package app.lazydex.ui.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lazydex.data.local.dao.MediaLinkDao
import app.lazydex.data.local.entity.MediaLinkEntity
import app.lazydex.data.tracker.anilist.AnilistGraphQLClient
import app.lazydex.data.tracker.anilist.AnilistTokenStore
import app.lazydex.data.tracker.anilist.dto.AniListMedia
import app.lazydex.data.tracker.sync.AnilistSyncEngine
import app.lazydex.data.tracker.sync.SyncSnapshot
import app.lazydex.domain.model.Category
import app.lazydex.domain.model.MediaCategory
import app.lazydex.domain.model.MediaItem
import app.lazydex.domain.model.ResolvedCategoryConfig
import app.lazydex.domain.model.UserStatus
import app.lazydex.domain.repository.DuplicateUrlException
import app.lazydex.domain.repository.MediaRepository
import app.lazydex.scraper.MetadataScraper
import app.lazydex.ui.components.formatProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

data class SuggestedTracker(
    val linkId: String,
    val remoteId: Long,
    val title: String,
    val progress: Double = 0.0,
    val userStatus: String = "PLANNING"
)

data class MediaDetailsUiState(
    val id: String = "",
    val category: MediaCategory = MediaCategory.NOVEL,
    val formatId: String? = null,
    val availableFormats: List<Category> = emptyList(),
    val title: String = "",
    val titleRomaji: String = "",
    val titleNative: String = "",
    val alternativeTitles: List<String> = emptyList(),
    val sourceUrl: String = "",
    val coverImagePath: String = "",
    val coverImageUrl: String = "",
    val bannerImage: String? = null,
    val currentProgress: String = "0",
    val totalItems: String = "",
    val secondaryProgress: String = "0",
    val secondaryProgressTotal: String = "",
    val actionOverride: String? = null,
    val playbackSpeed: Double = 1.0,
    val avgChapterLength: String = "",
    val resolvedConfig: ResolvedCategoryConfig? = null,
    val timeStats: Pair<Double, Double> = Pair(0.0, 0.0),
    val userStatus: UserStatus = UserStatus.READING,
    val rating: Double? = null,
    val notes: String = "",
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val author: String = "",
    val description: String = "",
    val startDate: Long? = null,
    val endDate: Long? = null,
    val isScraping: Boolean = false,
    val isSaving: Boolean = false,
    val errorMsg: String? = null,
    val scrapeError: String? = null,
    val isDone: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    val showDiscardConfirm: Boolean = false,
    val isNew: Boolean = true,
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val isUpdatingArtwork: Boolean = false,
    val anilistMediaId: Long? = null,
    val anilistSyncStatus: String? = null,
    val anilistLastSynced: Long? = null,
    val isAnilistLoggedIn: Boolean = false,
    val suggestedTracker: SuggestedTracker? = null,
    val trackerSearchResults: List<AniListMedia> = emptyList(),
    val isSearchingTracker: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis(),
) {
    val parsedSecondaryProgress: Double? get() = secondaryProgress.trim().toDoubleOrNull()
    val parsedSecondaryTotal: Double? get() = secondaryProgressTotal.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()

    val hasSecondaryProgress: Boolean get() = (resolvedConfig?.hasSecondaryProgress == true) ||
            ((parsedSecondaryProgress ?: 0.0) > 0.0) ||
            ((parsedSecondaryTotal ?: 0.0) > 0.0) ||
            formatId in setOf("typ_novel_ln", "typ_manga_jp", "cat_tv", "typ_anime_series")

    val isTitleBlank: Boolean get() = title.trim().isBlank()
    val parsedProgress: Int? get() = currentProgress.trim().toIntOrNull()
    val parsedTotal: Int? get() = totalItems.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()

    val isProgressInvalid: Boolean get() {
        val p = parsedProgress ?: return true
        if (p < 0) return true
        val t = parsedTotal
        if (t != null && p > t) return true
        return false
    }

    val isTotalInvalid: Boolean get() {
        val t = totalItems.trim()
        if (t.isEmpty()) return false
        val parsed = t.toIntOrNull() ?: return true
        return parsed < 0
    }

    val isUrlInvalid: Boolean get() {
        val url = sourceUrl.trim()
        if (url.isEmpty()) return false
        return !url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)
    }

    val canSave: Boolean get() = !isTitleBlank && !isProgressInvalid && !isTotalInvalid && !isUrlInvalid && !isScraping && !isSaving
}

class MediaDetailsViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: MediaRepository,
    private val scraper: MetadataScraper,
    private val okHttpClient: OkHttpClient,
    private val cacheDir: File,
    private val mediaLinkDao: MediaLinkDao? = null,
    private val syncEngine: AnilistSyncEngine? = null,
    private val tokenStore: AnilistTokenStore? = null,
    private val client: AnilistGraphQLClient? = null,
) : ViewModel() {

    private val itemId: String? = savedStateHandle["itemId"]
    private val initialUrl: String? = savedStateHandle["initialUrl"]

    private val _uiState = MutableStateFlow(MediaDetailsUiState())
    val uiState: StateFlow<MediaDetailsUiState> = _uiState.asStateFlow()

    private var originalItem: MediaItem? = null
    private val notesFlow = MutableStateFlow<String?>(null)
    private var itemObservationJob: Job? = null
    private var linkObservationJob: Job? = null
    private var formatObservationJob: Job? = null

    init {
        initialize(itemId, initialUrl)
        viewModelScope.launch {
            @OptIn(FlowPreview::class)
            notesFlow
                .debounce(500L)
                .distinctUntilChanged()
                .collect { pendingNotes ->
                    if (pendingNotes != null) {
                        autoSave()
                    }
                }
        }
    }

    fun initialize(id: String?, initialUrl: String?) {
        val effectiveUrl = initialUrl?.takeIf { it.isNotBlank() }
        val effectiveId = id?.takeIf { it.isNotBlank() }

        _uiState.value = _uiState.value.copy(
            isNew = effectiveId == null,
            isLoading = effectiveId != null,
            isAnilistLoggedIn = tokenStore?.isLoggedIn == true,
        )

        effectiveUrl?.let { url ->
            updateSourceUrl(url)
            scrapeUrl(url)
        }

        val initialCatId = when (_uiState.value.category) {
            MediaCategory.NOVEL -> "cat_novel"
            MediaCategory.MANGA -> "cat_manga"
            MediaCategory.ANIME -> "cat_anime"
            MediaCategory.GAME -> "cat_game"
            MediaCategory.MOVIE -> "cat_movie"
            MediaCategory.TV -> "cat_tv"
        }
        observeCategoryFormats(initialCatId)

        effectiveId?.let { targetId ->
            observeItem(targetId)
        }
    }

    private fun observeCategoryFormats(catId: String) {
        formatObservationJob?.cancel()
        formatObservationJob = viewModelScope.launch {
            val config = repository.getResolvedConfig(catId)
            _uiState.value = _uiState.value.copy(resolvedConfig = config)
            repository.observeTypes(catId).collect { types ->
                _uiState.value = _uiState.value.copy(availableFormats = types)
            }
        }
    }

    private fun observeItem(targetId: String) {
        mediaLinkDao?.let { linkDao ->
            linkObservationJob?.cancel()
            linkObservationJob = viewModelScope.launch {
                linkDao.observeByMediaId(targetId).collect { links ->
                    val link = links.firstOrNull { it.providerId == "anilist" && it.state == "LINKED" }
                    val suggested = links.firstOrNull { it.providerId == "anilist" && it.state == "SUGGESTED" }
                    val suggestedTracker = suggested?.let { s ->
                        val snap = SyncSnapshot.fromJson(s.syncedSnapshot)
                        SuggestedTracker(
                            linkId = s.id,
                            remoteId = s.remoteId?.toLongOrNull() ?: 0L,
                            title = _uiState.value.title.ifBlank { "AniList Entry #${s.remoteId}" },
                            progress = snap?.progress ?: 0.0,
                            userStatus = snap?.status ?: "PLANNING"
                        )
                    }
                    _uiState.value = _uiState.value.copy(
                        anilistMediaId = link?.remoteId?.toLongOrNull(),
                        anilistSyncStatus = link?.syncStatus,
                        anilistLastSynced = link?.lastSyncedAt,
                        suggestedTracker = suggestedTracker
                    )
                }
            }
        }

        itemObservationJob?.cancel()
        itemObservationJob = viewModelScope.launch {
            repository.observeById(targetId).collect { item ->
                if (item != null && !_uiState.value.isSaving && !_uiState.value.isScraping) {
                    originalItem = item
                    val config = repository.getResolvedConfig(item.categoryId)
                    val timeSpent = repository.getTimeSpentMinutes(item.id)
                    val topCatId = when (item.category) {
                        MediaCategory.NOVEL -> "cat_novel"
                        MediaCategory.MANGA -> "cat_manga"
                        MediaCategory.ANIME -> "cat_anime"
                        MediaCategory.GAME -> "cat_game"
                        MediaCategory.MOVIE -> "cat_movie"
                        MediaCategory.TV -> "cat_tv"
                    }
                    val romaji = item.alternativeTitles.getOrNull(0) ?: ""
                    val native = item.alternativeTitles.getOrNull(2) ?: ""
                    val banner = item.getExtra("banner_image")
                    _uiState.value = _uiState.value.copy(
                        id = item.id,
                        category = item.category,
                        formatId = item.categoryId,
                        title = item.title,
                        titleRomaji = romaji,
                        titleNative = native,
                        bannerImage = banner,
                        alternativeTitles = item.alternativeTitles,
                        sourceUrl = item.sourceUrl ?: "",
                        coverImagePath = item.coverImagePath,
                        coverImageUrl = item.coverImageUrl ?: "",
                        currentProgress = item.currentProgress.toString(),
                        totalItems = item.totalItems?.toString() ?: "",
                        secondaryProgress = formatProgress(item.secondaryProgress),
                        secondaryProgressTotal = item.secondaryProgressTotal?.let { formatProgress(it) } ?: "",
                        actionOverride = item.actionOverride,
                        playbackSpeed = item.playbackSpeed,
                        avgChapterLength = item.avgChapterLength?.toString() ?: "",
                        resolvedConfig = config,
                        timeStats = timeSpent,
                        userStatus = item.userStatus,
                        rating = item.rating,
                        notes = item.notes,
                        genres = item.genres,
                        tags = item.tags,
                        author = item.author,
                        description = item.description,
                        startDate = item.startDate,
                        endDate = item.endDate,
                        lastUpdated = item.lastUpdated,
                        isNew = false,
                        isLoading = false,
                    )
                    if (item.category != _uiState.value.category || _uiState.value.availableFormats.isEmpty()) {
                        observeCategoryFormats(topCatId)
                    }
                } else if (item == null) {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            }
        }
    }

    fun updateTitle(title: String) {
        _uiState.value = _uiState.value.copy(title = title, errorMsg = null)
    }

    fun updateCategory(category: MediaCategory) {
        val catId = when (category) {
            MediaCategory.NOVEL -> "cat_novel"
            MediaCategory.MANGA -> "cat_manga"
            MediaCategory.ANIME -> "cat_anime"
            MediaCategory.GAME -> "cat_game"
            MediaCategory.MOVIE -> "cat_movie"
            MediaCategory.TV -> "cat_tv"
        }
        _uiState.value = _uiState.value.copy(category = category, formatId = null)
        observeCategoryFormats(catId)
    }

    fun updateFormatId(formatId: String?) {
        viewModelScope.launch {
            val catId = formatId ?: when (_uiState.value.category) {
                MediaCategory.NOVEL -> "cat_novel"
                MediaCategory.MANGA -> "cat_manga"
                MediaCategory.ANIME -> "cat_anime"
                MediaCategory.GAME -> "cat_game"
                MediaCategory.MOVIE -> "cat_movie"
                MediaCategory.TV -> "cat_tv"
            }
            val config = repository.getResolvedConfig(catId)
            _uiState.value = _uiState.value.copy(formatId = formatId, resolvedConfig = config)
        }
    }

    fun updateBannerImage(url: String?) {
        _uiState.value = _uiState.value.copy(bannerImage = url)
    }

    fun updateTitleRomaji(title: String) {
        _uiState.value = _uiState.value.copy(titleRomaji = title)
    }

    fun updateTitleNative(title: String) {
        _uiState.value = _uiState.value.copy(titleNative = title)
    }

    fun updateSourceUrl(url: String) {
        _uiState.value = _uiState.value.copy(sourceUrl = url, errorMsg = null)
    }

    fun updateCoverImagePath(path: String) {
        _uiState.value = _uiState.value.copy(coverImagePath = path)
    }

    fun updateCoverImageUrl(url: String) {
        _uiState.value = _uiState.value.copy(coverImageUrl = url)
    }

    fun updateArtwork(coverUrl: String, bannerUrl: String?) {
        val trimmedCover = coverUrl.trim()
        val trimmedBanner = bannerUrl?.trim()?.ifEmpty { null }
        _uiState.value = _uiState.value.copy(
            isUpdatingArtwork = true,
            bannerImage = trimmedBanner,
            coverImageUrl = trimmedCover,
        )
        viewModelScope.launch {
            try {
                val newLocalPath = if (trimmedCover.isNotBlank()) {
                    val downloaded = downloadImageToTemp(trimmedCover)
                    downloaded?.absolutePath ?: ""
                } else {
                    ""
                }
                _uiState.value = _uiState.value.copy(
                    coverImagePath = newLocalPath,
                    isUpdatingArtwork = false,
                )
                autoSave()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isUpdatingArtwork = false,
                    errorMsg = e.message ?: "Failed to update artwork",
                )
            }
        }
    }

    fun updateCurrentProgress(progress: String) {
        _uiState.value = _uiState.value.copy(currentProgress = progress, errorMsg = null)
    }

    fun updateTotalItems(total: String) {
        _uiState.value = _uiState.value.copy(totalItems = total, errorMsg = null)
    }

    fun updateSecondaryProgress(progress: String) {
        _uiState.value = _uiState.value.copy(secondaryProgress = progress)
    }

    fun updateSecondaryProgressTotal(total: String) {
        _uiState.value = _uiState.value.copy(secondaryProgressTotal = total)
    }

    fun updateActionOverride(action: String?) {
        _uiState.value = _uiState.value.copy(actionOverride = action)
    }

    fun updatePlaybackSpeed(speed: Double) {
        _uiState.value = _uiState.value.copy(playbackSpeed = speed)
    }

    fun updateAvgChapterLength(length: String) {
        _uiState.value = _uiState.value.copy(avgChapterLength = length)
    }

    fun updateUserStatus(status: UserStatus) {
        _uiState.value = _uiState.value.copy(userStatus = status)
    }

    fun updateRating(rating: Double?) {
        _uiState.value = _uiState.value.copy(rating = rating)
    }

    fun updateNotes(notes: String) {
        _uiState.value = _uiState.value.copy(notes = notes)
        notesFlow.value = notes
    }

    fun flushNotes() {
        val pending = notesFlow.value
        if (pending != null) {
            notesFlow.value = null
            originalItem = originalItem?.copy(notes = _uiState.value.notes)
            autoSave()
        }
    }

    fun addGenre(genre: String) {
        val trimmed = genre.trim()
        if (trimmed.isNotEmpty() && trimmed !in _uiState.value.genres) {
            _uiState.value = _uiState.value.copy(genres = _uiState.value.genres + trimmed)
        }
    }

    fun removeGenre(genre: String) {
        _uiState.value = _uiState.value.copy(genres = _uiState.value.genres - genre)
    }

    fun addTag(tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isNotEmpty() && trimmed !in _uiState.value.tags) {
            _uiState.value = _uiState.value.copy(tags = _uiState.value.tags + trimmed)
        }
    }

    fun removeTag(tag: String) {
        _uiState.value = _uiState.value.copy(tags = _uiState.value.tags - tag)
    }

    fun updateAuthor(author: String) {
        _uiState.value = _uiState.value.copy(author = author)
    }

    fun updateDescription(description: String) {
        _uiState.value = _uiState.value.copy(description = description)
    }

    fun updateStartDate(date: Long?) {
        _uiState.value = _uiState.value.copy(startDate = date)
    }

    fun updateEndDate(date: Long?) {
        _uiState.value = _uiState.value.copy(endDate = date)
    }

    fun setError(message: String?) {
        _uiState.value = _uiState.value.copy(errorMsg = message)
    }

    fun scrapeUrl(urlOverride: String? = null) {
        val rawUrl = urlOverride ?: _uiState.value.sourceUrl
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return

        _uiState.value = _uiState.value.copy(isScraping = true, scrapeError = null)

        viewModelScope.launch {
            try {
                val result = scraper.scrape(trimmed)
                result.onSuccess { metadata ->
                    val downloadedCover = metadata.imageUrl?.let { coverUrl ->
                        downloadImageToTemp(coverUrl)
                    }

                    _uiState.value = _uiState.value.copy(
                        title = metadata.title.takeIf { it.isNotBlank() } ?: _uiState.value.title,
                        description = metadata.description.takeIf { it.isNotBlank() } ?: _uiState.value.description,
                        author = metadata.author.takeIf { it.isNotBlank() } ?: _uiState.value.author,
                        coverImageUrl = metadata.imageUrl ?: _uiState.value.coverImageUrl,
                        coverImagePath = downloadedCover?.absolutePath ?: _uiState.value.coverImagePath,
                        genres = if (metadata.genres.isNotEmpty()) metadata.genres else _uiState.value.genres,
                        tags = if (metadata.tags.isNotEmpty()) metadata.tags else _uiState.value.tags,
                        isScraping = false,
                    )

                    metadata.category?.let { cat ->
                        updateCategory(cat)
                    } ?: run {
                        guessCategoryFromUrl(trimmed)?.let { guessed ->
                            updateCategory(guessed)
                        }
                    }
                }.onFailure { exception ->
                    val msg = exception.message ?: "Failed to scrape metadata"
                    _uiState.value = _uiState.value.copy(
                        isScraping = false,
                        scrapeError = msg,
                        errorMsg = msg,
                    )
                }
            } catch (e: Exception) {
                val msg = e.message ?: "Failed to scrape metadata"
                _uiState.value = _uiState.value.copy(
                    isScraping = false,
                    scrapeError = msg,
                    errorMsg = msg,
                )
            }
        }
    }

    fun save(exitOnSuccess: Boolean = true) {
        flushNotes()
        val state = _uiState.value
        if (state.isTitleBlank) {
            _uiState.value = _uiState.value.copy(errorMsg = "Title is required")
            return
        }
        if (state.isProgressInvalid) {
            _uiState.value = _uiState.value.copy(errorMsg = "Progress cannot exceed total items")
            return
        }
        if (state.isTotalInvalid) {
            _uiState.value = _uiState.value.copy(errorMsg = "Total items cannot be negative")
            return
        }
        if (state.isUrlInvalid) {
            _uiState.value = _uiState.value.copy(errorMsg = "Source URL must start with http:// or https://")
            return
        }
        if (state.isSaving || state.isScraping) return

        _uiState.value = _uiState.value.copy(isSaving = true, errorMsg = null)

        viewModelScope.launch {
            val totalVal = state.parsedTotal
            val progressVal = state.parsedProgress ?: 0

            val catId = state.formatId ?: when (state.category) {
                MediaCategory.NOVEL -> "cat_novel"
                MediaCategory.MANGA -> "cat_manga"
                MediaCategory.ANIME -> "cat_anime"
                MediaCategory.GAME -> "cat_game"
                MediaCategory.MOVIE -> "cat_movie"
                MediaCategory.TV -> "cat_tv"
            }

            val romaji = state.titleRomaji.trim().ifEmpty { state.alternativeTitles.getOrNull(0) ?: "" }
            val english = state.alternativeTitles.getOrNull(1) ?: ""
            val native = state.titleNative.trim().ifEmpty { state.alternativeTitles.getOrNull(2) ?: "" }
            val rest = if (state.alternativeTitles.size > 3) state.alternativeTitles.subList(3, state.alternativeTitles.size) else emptyList()

            val altTitles = if (romaji.isNotBlank() || english.isNotBlank() || native.isNotBlank() || rest.isNotEmpty()) {
                listOf(romaji, english, native) + rest
            } else {
                emptyList()
            }

            var item = MediaItem(
                id = state.id.ifBlank { UUID.randomUUID().toString() },
                categoryId = catId,
                category = state.category,
                title = state.title.trim(),
                alternativeTitles = altTitles,
                sourceUrl = state.sourceUrl.trim().ifEmpty { null },
                coverImagePath = state.coverImagePath,
                coverImageUrl = state.coverImageUrl.ifEmpty { null },
                currentProgress = progressVal,
                totalItems = totalVal,
                progress = progressVal.toDouble(),
                progressTotal = totalVal?.toDouble(),
                secondaryProgress = state.parsedSecondaryProgress ?: 0.0,
                secondaryProgressTotal = state.parsedSecondaryTotal,
                actionOverride = state.actionOverride,
                playbackSpeed = state.playbackSpeed,
                avgChapterLength = state.avgChapterLength.toIntOrNull(),
                userStatus = state.userStatus,
                rating = state.rating,
                notes = state.notes,
                genres = state.genres,
                tags = state.tags,
                author = state.author.trim(),
                description = state.description.trim(),
                startDate = state.startDate,
                endDate = state.endDate,
                lastUpdated = System.currentTimeMillis(),
                dateAdded = originalItem?.dateAdded ?: System.currentTimeMillis(),
            )

            if (!state.bannerImage.isNullOrBlank()) {
                item = item.withExtra("banner_image", state.bannerImage!!)
            }

            try {
                val wasNew = state.isNew
                val savedItem = if (state.isNew) {
                    repository.add(item)
                } else {
                    repository.update(item)
                    item
                }
                originalItem = savedItem
                if (exitOnSuccess) {
                    _uiState.value = _uiState.value.copy(id = savedItem.id, isDone = true, isSaving = false)
                } else {
                    _uiState.value = _uiState.value.copy(id = savedItem.id, isNew = false, isSaving = false)
                    if (wasNew) {
                        observeItem(savedItem.id)
                    }
                }
            } catch (e: DuplicateUrlException) {
                _uiState.value = _uiState.value.copy(
                    errorMsg = "A media tracker with this URL already exists.",
                    isSaving = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMsg = e.message ?: "Failed to save media item.",
                    isSaving = false,
                )
            }
        }
    }

    fun autoSave() {
        val state = _uiState.value
        if (state.isNew || state.isTitleBlank || state.id.isBlank()) return
        save(exitOnSuccess = false)
    }

    fun syncWithAniList() {
        val mediaId = _uiState.value.id
        if (mediaId.isBlank() || _uiState.value.isSyncing) return
        val engine = syncEngine ?: return

        _uiState.value = _uiState.value.copy(isSyncing = true)
        viewModelScope.launch {
            try {
                val success = engine.pushDirtyItem(mediaId)
                if (!success) {
                    _uiState.value = _uiState.value.copy(errorMsg = "AniList sync failed.")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMsg = e.message ?: "AniList sync error.")
            } finally {
                _uiState.value = _uiState.value.copy(isSyncing = false)
            }
        }
    }

    fun acceptTrackerSuggestion() {
        val suggestion = _uiState.value.suggestedTracker ?: return
        val targetId = _uiState.value.id
        if (targetId.isBlank()) return
        val linkDao = mediaLinkDao ?: return

        viewModelScope.launch {
            val links = linkDao.getByMediaId(targetId)
            val suggestedLink = links.firstOrNull { it.id == suggestion.linkId } ?: return@launch

            // Fast forward progress if remote progress is higher (monotonic max)
            val localProgress = _uiState.value.currentProgress.toDoubleOrNull() ?: 0.0
            if (suggestion.progress > localProgress) {
                _uiState.value = _uiState.value.copy(currentProgress = suggestion.progress.toInt().toString())
                save(exitOnSuccess = false)
            }

            linkDao.upsert(
                suggestedLink.copy(
                    state = "LINKED",
                    syncStatus = "DIRTY_LOCAL",
                    updatedAt = System.currentTimeMillis()
                )
            )
            _uiState.value = _uiState.value.copy(suggestedTracker = null)
            syncWithAniList()
        }
    }

    fun dismissTrackerSuggestion() {
        val suggestion = _uiState.value.suggestedTracker ?: return
        val linkDao = mediaLinkDao ?: return
        viewModelScope.launch {
            linkDao.delete(suggestion.linkId)
            _uiState.value = _uiState.value.copy(suggestedTracker = null)
        }
    }

    fun searchAniListTracker(query: String) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            _uiState.value = _uiState.value.copy(trackerSearchResults = emptyList())
            return
        }
        val anilistClient = client ?: return
        val mediaType = if (_uiState.value.category == MediaCategory.ANIME || _uiState.value.category == MediaCategory.TV || _uiState.value.category == MediaCategory.MOVIE) "ANIME" else "MANGA"

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearchingTracker = true)
            try {
                val results = withContext(Dispatchers.IO) {
                    anilistClient.searchMedia(cleanQuery, mediaType, perPage = 8)
                }
                _uiState.value = _uiState.value.copy(trackerSearchResults = results, isSearchingTracker = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    trackerSearchResults = emptyList(),
                    isSearchingTracker = false,
                    errorMsg = "Search failed: ${e.message}"
                )
            }
        }
    }

    fun clearTrackerSearchResults() {
        _uiState.value = _uiState.value.copy(trackerSearchResults = emptyList())
    }

    fun linkAniListId(remoteId: Long) {
        val targetId = _uiState.value.id
        if (targetId.isBlank() || _uiState.value.isNew) {
            _uiState.value = _uiState.value.copy(errorMsg = "Add media to library before linking tracker")
            return
        }
        val linkDao = mediaLinkDao ?: return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val type = if (_uiState.value.category == MediaCategory.ANIME || _uiState.value.category == MediaCategory.TV || _uiState.value.category == MediaCategory.MOVIE) "anime" else "manga"

            // Remove any pending suggestion or old links for anilist on this item
            val existingLinks = linkDao.getByMediaId(targetId)
            existingLinks.filter { it.providerId == "anilist" }.forEach { linkDao.delete(it.id) }

            val newLink = MediaLinkEntity(
                id = UUID.randomUUID().toString(),
                mediaId = targetId,
                kind = "TRACKER",
                providerId = "anilist",
                remoteId = remoteId.toString(),
                remoteMediaListId = null,
                url = "https://anilist.co/$type/$remoteId",
                state = "LINKED",
                syncStatus = "DIRTY_LOCAL",
                lastSyncedAt = null,
                syncedSnapshot = "{}",
                createdAt = now,
                updatedAt = now,
            )
            linkDao.upsert(newLink)
            _uiState.value = _uiState.value.copy(suggestedTracker = null, trackerSearchResults = emptyList())
            syncWithAniList()
        }
    }

    fun unlinkAniList() {
        val targetId = _uiState.value.id
        if (targetId.isBlank()) return
        val linkDao = mediaLinkDao ?: return
        viewModelScope.launch {
            val links = linkDao.getByMediaId(targetId)
            val link = links.firstOrNull { it.providerId == "anilist" && it.state == "LINKED" }
            if (link != null) {
                linkDao.delete(link.id)
                _uiState.value = _uiState.value.copy(
                    anilistMediaId = null,
                    anilistSyncStatus = null,
                    anilistLastSynced = null,
                )
            }
        }
    }

    fun deleteItem() {
        val id = _uiState.value.id
        if (id.isEmpty()) return

        _uiState.value = _uiState.value.copy(isSaving = true, showDeleteConfirm = false)
        viewModelScope.launch {
            try {
                repository.delete(id)
                _uiState.value = _uiState.value.copy(isDone = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMsg = e.message ?: "Failed to delete item",
                    isSaving = false,
                )
            }
        }
    }

    fun checkBackPressAllowed(): Boolean {
        if (_uiState.value.isDone) return true

        val state = _uiState.value
        val isDirty = if (state.isNew) {
            state.title.isNotBlank() || state.sourceUrl.isNotBlank() || state.notes.isNotBlank()
        } else {
            val orig = originalItem ?: return true
            state.title != orig.title ||
            state.category != orig.category ||
            state.alternativeTitles != orig.alternativeTitles ||
            state.titleRomaji != (orig.alternativeTitles.getOrNull(0) ?: "") ||
            state.titleNative != (orig.alternativeTitles.getOrNull(2) ?: "") ||
            state.formatId != orig.categoryId ||
            state.bannerImage != orig.getExtra("banner_image") ||
            state.secondaryProgress != formatProgress(orig.secondaryProgress) ||
            state.secondaryProgressTotal != (orig.secondaryProgressTotal?.let { formatProgress(it) } ?: "") ||
            state.sourceUrl != (orig.sourceUrl ?: "") ||
            state.coverImageUrl != (orig.coverImageUrl ?: "") ||
            state.currentProgress != orig.currentProgress.toString() ||
            state.totalItems != (orig.totalItems?.toString() ?: "") ||
            state.userStatus != orig.userStatus ||
            state.rating != orig.rating ||
            state.notes != orig.notes ||
            state.author != orig.author ||
            state.description != orig.description ||
            state.genres != orig.genres ||
            state.tags != orig.tags ||
            state.startDate != orig.startDate ||
            state.endDate != orig.endDate
        }

        if (isDirty) {
            _uiState.value = _uiState.value.copy(showDiscardConfirm = true)
            return false
        }
        return true
    }

    fun onBackPress(): Boolean {
        flushNotes()
        return checkBackPressAllowed()
    }

    fun dismissDiscardConfirm() {
        _uiState.value = _uiState.value.copy(showDiscardConfirm = false)
    }

    fun showDeleteConfirm(show: Boolean) {
        _uiState.value = _uiState.value.copy(showDeleteConfirm = show)
    }

    private suspend fun downloadImageToTemp(url: String): File? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body ?: return@withContext null
                val tempFile = File(cacheDir, "temp_cover_${UUID.randomUUID()}.jpg")
                FileOutputStream(tempFile).use { output ->
                    body.byteStream().copyTo(output)
                }
                tempFile
            }
        } catch (e: IOException) {
            null
        }
    }

    private fun guessCategoryFromUrl(url: String): MediaCategory? {
        val lower = url.lowercase()
        return when {
            lower.contains("novelupdates.com") || lower.contains("royalroad.com") || lower.contains("wtr-lab.com") -> MediaCategory.NOVEL
            lower.contains("mangadex.org") || lower.contains("manganato.com") -> MediaCategory.MANGA
            lower.contains("myanimelist.net/anime") || lower.contains("anilist.co/anime") -> MediaCategory.ANIME
            lower.contains("myanimelist.net/manga") || lower.contains("anilist.co/manga") -> MediaCategory.MANGA
            lower.contains("steamcharts.com") || lower.contains("steampowered.com") || lower.contains("gog.com") -> MediaCategory.GAME
            else -> null
        }
    }
}

