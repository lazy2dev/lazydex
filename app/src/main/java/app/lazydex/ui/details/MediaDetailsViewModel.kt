package app.lazydex.ui.details

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lazydex.data.cache.CoverCache
import app.lazydex.data.local.ThemePreferences
import app.lazydex.data.local.dao.MediaLinkDao
import app.lazydex.data.local.entity.MediaLinkEntity
import app.lazydex.data.tracker.anilist.AnilistApi
import app.lazydex.data.tracker.anilist.AnilistTokenStore
import app.lazydex.data.tracker.anilist.dto.AniListMedia
import app.lazydex.data.tracker.sync.AnilistSyncEngine
import app.lazydex.data.tracker.sync.SyncSnapshot
import app.lazydex.domain.model.MediaItem
import app.lazydex.domain.model.MediaType
import app.lazydex.domain.model.ResolvedTypeConfig
import app.lazydex.domain.model.TypeConfig
import app.lazydex.domain.model.UserStatus
import app.lazydex.domain.model.toTopLevelId
import app.lazydex.domain.repository.DuplicateUrlException
import app.lazydex.domain.repository.MediaRepository
import app.lazydex.scraper.MetadataScraper
import app.lazydex.ui.components.formatProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
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
    val mediaType: MediaType = MediaType.NOVEL,
    val formatId: String? = null,
    val availableTypes: List<TypeConfig> = emptyList(),
    val availableFormats: List<TypeConfig> = emptyList(),
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
    val resolvedConfig: ResolvedTypeConfig? = null,
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
    val scrapeVersion: Int = 0,
    val isMetadataSheetOpen: Boolean = false,
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
    val isAdult: Boolean = false,
    val blurAdultCovers: Boolean = false,
    val seedColor: Color? = null,
    val coverTheming: Boolean = true,
) {
    val isObscured: Boolean get() = isAdult && blurAdultCovers
    val parsedSecondaryProgress: Double? get() = secondaryProgress.trim().toDoubleOrNull()
    val parsedSecondaryTotal: Double? get() = secondaryProgressTotal.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()

    val hasSecondaryProgress: Boolean get() {
        if (mediaType == MediaType.ANIME || mediaType == MediaType.MOVIE || mediaType == MediaType.GAME || mediaType == MediaType.TV) {
            return false
        }
        return (resolvedConfig?.hasSecondaryProgress == true) ||
            ((parsedSecondaryProgress ?: 0.0) > 0.0) ||
            ((parsedSecondaryTotal ?: 0.0) > 0.0) ||
            formatId in setOf(
                "LIGHT_NOVEL", "MANGA",
                "typ_novel_ln", "typ_manga_jp", "typ_manga", "typ_manhwa", "typ_manhua"
            )
    }

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
    private val client: AnilistApi? = null,
    private val themePreferences: ThemePreferences? = null,
    private val coverCache: CoverCache? = null,
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
            repository.observeTopLevelTypes().collect { types ->
                _uiState.update { it.copy(availableTypes = types) }
            }
        }
        if (themePreferences != null) {
            viewModelScope.launch {
                themePreferences.blurAdultCovers.collect { blur ->
                    _uiState.update { it.copy(blurAdultCovers = blur) }
                }
            }
            viewModelScope.launch {
                themePreferences.coverTheming.collect { enabled ->
                    _uiState.update { it.copy(coverTheming = enabled) }
                    if (enabled) {
                        lastExtractedSource = null
                        extractSeedColor(_uiState.value.coverImagePath, _uiState.value.coverImageUrl)
                    }
                }
            }
        }
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

        lastExtractedSource = null
        _uiState.value = _uiState.value.copy(
            isNew = effectiveId == null,
            isLoading = effectiveId != null,
            isAnilistLoggedIn = tokenStore?.isLoggedIn == true,
            seedColor = null,
        )

        effectiveUrl?.let { url ->
            updateSourceUrl(url)
            scrapeUrl(url)
        }

        observeTypeFormats(_uiState.value.mediaType.name, _uiState.value.formatId)

        effectiveId?.let { targetId ->
            observeItem(targetId)
        }
    }

    fun observeFormatsForType(typeId: String): Flow<List<TypeConfig>> = repository.observeFormats(typeId)

    fun setMetadataSheetOpen(open: Boolean) {
        _uiState.update { it.copy(isMetadataSheetOpen = open) }
    }

    fun clearScrapeError() {
        _uiState.update { it.copy(scrapeError = null, errorMsg = null) }
    }

    private fun observeTypeFormats(typeId: String, formatId: String? = null) {
        formatObservationJob?.cancel()
        formatObservationJob = viewModelScope.launch {
            val config = repository.getResolvedConfig(formatId ?: typeId)
            _uiState.value = _uiState.value.copy(resolvedConfig = config)
            repository.observeFormats(typeId).collect { formats ->
                _uiState.value = _uiState.value.copy(availableFormats = formats)
            }
        }
    }

    private fun resolveFormatId(item: MediaItem): String? = when {
        item.type.equals("MANGA", ignoreCase = true) && item.countryOfOrigin.equals("KR", ignoreCase = true) -> "MANHWA"
        item.type.equals("MANGA", ignoreCase = true) && (item.countryOfOrigin.equals("CN", ignoreCase = true) || item.countryOfOrigin.equals("TW", ignoreCase = true)) -> "MANHUA"
        item.format.isNotBlank() -> item.format.uppercase()
        else -> null
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
                if (item != null && !_uiState.value.isSaving && !_uiState.value.isScraping && !_uiState.value.isMetadataSheetOpen) {
                    originalItem = item
                    val resolvedFormatId = resolveFormatId(item)
                    val config = repository.getResolvedConfig(resolvedFormatId ?: item.mediaType.name)
                    val timeSpent = repository.getTimeSpentMinutes(item.id)
                    val typeChanged = item.mediaType != _uiState.value.mediaType || _uiState.value.availableFormats.isEmpty()
                    val romaji = item.alternativeTitles.getOrNull(0) ?: ""
                    val native = item.alternativeTitles.getOrNull(2) ?: ""
                    val banner = item.getExtra("banner_image")
                    _uiState.value = _uiState.value.copy(
                        id = item.id,
                        mediaType = item.mediaType,
                        formatId = resolvedFormatId,
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
                        isAdult = item.isAdult,
                        isNew = false,
                        isLoading = false,
                    )
                    if (typeChanged) {
                        observeTypeFormats(item.mediaType.name, resolvedFormatId)
                    }
                    extractSeedColor(item.coverImagePath, item.coverImageUrl ?: "")
                } else if (item == null) {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            }
        }
    }

    fun updateTitle(title: String) {
        _uiState.value = _uiState.value.copy(title = title, errorMsg = null)
    }

    fun updateMediaType(mediaType: MediaType) {
        _uiState.value = _uiState.value.copy(mediaType = mediaType, formatId = null)
        observeTypeFormats(mediaType.name)
    }

    fun updateFormatId(formatId: String?) {
        viewModelScope.launch {
            val typeId = formatId ?: _uiState.value.mediaType.name
            val config = repository.getResolvedConfig(typeId)
            _uiState.value = _uiState.value.copy(formatId = formatId, resolvedConfig = config)
        }
    }

    fun updateBannerImage(url: String?) {
        _uiState.value = _uiState.value.copy(bannerImage = url)
    }

    fun updateSourceUrl(url: String) {
        _uiState.value = _uiState.value.copy(sourceUrl = url, errorMsg = null, scrapeError = null)
    }

    private var lastExtractedSource: String? = null

    fun updateCoverImagePath(path: String) {
        _uiState.value = _uiState.value.copy(coverImagePath = path)
        lastExtractedSource = null
        extractSeedColor(path, _uiState.value.coverImageUrl)
    }

    fun setSeedColor(color: Color) {
        _uiState.update { it.copy(seedColor = color) }
    }

    fun extractSeedColorFromPath(path: String) {
        extractSeedColor(path, "")
    }

    fun extractSeedColor(path: String, url: String = "") {
        if (!_uiState.value.coverTheming) return
        val trimmedPath = path.trim()
        val trimmedUrl = url.trim()
        val sourceKey = trimmedPath.ifBlank { trimmedUrl }
        if (sourceKey.isBlank() || sourceKey == lastExtractedSource) return
        lastExtractedSource = sourceKey

        viewModelScope.launch(Dispatchers.IO) {
            val color = if (trimmedPath.isNotBlank()) {
                val localColor = app.lazydex.ui.theme.CoverColorExtractor.extractSeedColor(trimmedPath)
                if (localColor != null) {
                    localColor
                } else if (trimmedUrl.isNotBlank()) {
                    extractFromUrlOrCache(trimmedUrl)
                } else {
                    null
                }
            } else {
                extractFromUrlOrCache(trimmedUrl)
            }
            if (color != null && _uiState.value.seedColor != color) {
                _uiState.update { it.copy(seedColor = color) }
            }
        }
    }

    private suspend fun extractFromUrlOrCache(url: String): androidx.compose.ui.graphics.Color? {
        val cachedFile = coverCache?.getCoverFile(url)
        if (cachedFile != null && cachedFile.exists()) {
            val localColor = app.lazydex.ui.theme.CoverColorExtractor.extractSeedColor(cachedFile.absolutePath)
            if (localColor != null) return localColor
        }
        return app.lazydex.ui.theme.CoverColorExtractor.extractSeedColorFromUrl(url, okHttpClient)
    }

    fun updateCoverImageUrl(url: String) {
        _uiState.value = _uiState.value.copy(coverImageUrl = url)
        lastExtractedSource = null
        extractSeedColor(_uiState.value.coverImagePath, url)
    }

    fun updateArtwork(coverUrl: String, bannerUrl: String?) {
        val trimmedCover = coverUrl.trim()
        val trimmedBanner = bannerUrl?.trim()?.ifEmpty { null }
        val current = _uiState.value
        if (trimmedCover == current.coverImageUrl.trim() && trimmedBanner == current.bannerImage?.trim()?.ifEmpty { null }) {
            return
        }
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
                lastExtractedSource = null
                extractSeedColor(newLocalPath, trimmedCover)
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
            autoSave()
        }
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

                    val targetType = metadata.type ?: guessTypeFromUrl(trimmed) ?: _uiState.value.mediaType
                    val typeChanged = targetType != _uiState.value.mediaType
                    _uiState.value = _uiState.value.copy(
                        mediaType = targetType,
                        formatId = if (typeChanged) null else _uiState.value.formatId,
                        title = metadata.title.takeIf { it.isNotBlank() } ?: _uiState.value.title,
                        description = metadata.description.takeIf { it.isNotBlank() } ?: _uiState.value.description,
                        author = metadata.author.takeIf { it.isNotBlank() } ?: _uiState.value.author,
                        coverImageUrl = metadata.imageUrl ?: _uiState.value.coverImageUrl,
                        coverImagePath = downloadedCover?.absolutePath ?: _uiState.value.coverImagePath,
                        genres = if (metadata.genres.isNotEmpty()) metadata.genres else _uiState.value.genres,
                        tags = if (metadata.tags.isNotEmpty()) metadata.tags else _uiState.value.tags,
                        isScraping = false,
                        scrapeError = null,
                        errorMsg = null,
                        scrapeVersion = _uiState.value.scrapeVersion + 1,
                    )
                    if (typeChanged) {
                        observeTypeFormats(targetType.name)
                    }
                }.onFailure { exception ->
                    setScrapeError(exception.message ?: "Failed to scrape metadata")
                }
            } catch (e: Exception) {
                setScrapeError(e.message ?: "Failed to scrape metadata")
            }
        }
    }

    private fun setScrapeError(message: String) {
        _uiState.value = _uiState.value.copy(
            isScraping = false,
            scrapeError = message,
        )
    }

    private fun validateMetadata(state: MediaDetailsUiState): String? = when {
        state.isTitleBlank -> "Title is required"
        state.isProgressInvalid -> "Progress cannot exceed total items"
        state.isTotalInvalid -> "Total items cannot be negative"
        state.isUrlInvalid -> "Source URL must start with http:// or https://"
        else -> null
    }

    fun commitMetadataEdits(
        sourceUrl: String,
        mediaType: MediaType,
        formatId: String?,
        title: String,
        author: String,
        totalItems: String,
        description: String,
    ): Boolean {
        val current = _uiState.value
        if (current.isScraping || current.isSaving) return false

        val unchanged = current.sourceUrl.trim() == sourceUrl.trim() &&
            current.mediaType == mediaType &&
            current.formatId == formatId &&
            current.title.trim() == title.trim() &&
            current.author.trim() == author.trim() &&
            current.totalItems.trim() == totalItems.trim() &&
            current.description.trim() == description.trim()
        if (unchanged) return true

        val candidate = current.copy(
            sourceUrl = sourceUrl,
            mediaType = mediaType,
            formatId = formatId,
            title = title,
            author = author,
            totalItems = totalItems,
            description = description,
            errorMsg = null,
        )
        val validationError = validateMetadata(candidate)
        if (validationError != null) {
            _uiState.value = candidate.copy(errorMsg = validationError)
            return false
        }

        _uiState.value = candidate
        if (mediaType != current.mediaType || formatId != current.formatId) {
            observeTypeFormats(mediaType.name, formatId)
        }
        if (!candidate.isNew) {
            save(exitOnSuccess = false)
        }
        return true
    }

    private fun formatIdToDomainFormat(formatId: String?): String = when (formatId?.lowercase()) {
        null, "" -> ""
        "typ_novel_ln" -> "LIGHT_NOVEL"
        "typ_novel_kr", "typ_novel_cn" -> "WEB_NOVEL"
        "typ_manga", "typ_manga_jp" -> "MANGA"
        "typ_manhwa" -> "MANHWA"
        "typ_manhua" -> "MANHUA"
        "typ_tv", "typ_anime_series" -> "TV"
        "typ_anime_movie" -> "MOVIE"
        "typ_anime_ova" -> "OVA"
        else -> if (formatId.startsWith("typ_", ignoreCase = true)) "" else formatId.uppercase()
    }

    fun save(exitOnSuccess: Boolean = true) {
        notesFlow.value = null
        val state = _uiState.value
        val validationError = validateMetadata(state)
        if (validationError != null) {
            _uiState.value = _uiState.value.copy(errorMsg = validationError)
            return
        }
        if (state.isSaving || state.isScraping) return

        _uiState.value = _uiState.value.copy(isSaving = true, errorMsg = null)

        viewModelScope.launch {
            val totalVal = state.parsedTotal
            val progressVal = state.parsedProgress ?: 0

            val existing = originalItem
            val resolvedFormat = formatIdToDomainFormat(state.formatId).ifBlank {
                existing?.format?.takeIf { state.mediaType == existing.mediaType }.orEmpty()
            }
            val resolvedCountry = when {
                state.mediaType != MediaType.MANGA -> existing?.countryOfOrigin
                resolvedFormat == "MANHWA" -> "KR"
                resolvedFormat == "MANHUA" -> existing?.countryOfOrigin?.takeIf {
                    it.equals("CN", ignoreCase = true) || it.equals("TW", ignoreCase = true)
                } ?: "CN"
                resolvedFormat == "MANGA" && existing?.countryOfOrigin?.uppercase() in setOf("KR", "CN", "TW") -> null
                else -> existing?.countryOfOrigin
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

            val now = System.currentTimeMillis()
            var item = if (existing != null) {
                val candidate = existing.copy(
                    type = state.mediaType.name,
                    format = resolvedFormat,
                    countryOfOrigin = resolvedCountry,
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
                    isAdult = state.isAdult,
                )
                val bannerChanged = (state.bannerImage?.trim()?.ifEmpty { null }) != existing.getExtra("banner_image")
                val candidateWithBanner = if (!state.bannerImage.isNullOrBlank()) {
                    candidate.withExtra("banner_image", state.bannerImage!!)
                } else {
                    candidate
                }

                val userdataChanged = candidateWithBanner.hasUserdataChangedFrom(existing)
                val itemDataChanged = candidateWithBanner.hasItemDataChangedFrom(existing) || bannerChanged

                if (!userdataChanged && !itemDataChanged) {
                    if (exitOnSuccess) {
                        _uiState.value = _uiState.value.copy(id = existing.id, isDone = true, isSaving = false)
                    } else {
                        _uiState.value = _uiState.value.copy(id = existing.id, isNew = false, isSaving = false)
                    }
                    return@launch
                }

                val progressIncreased = candidateWithBanner.hasProgressIncreasedFrom(existing)
                val newProgressIncreasedAt = if (progressIncreased) now else existing.progressIncreasedAt
                val newLastUpdated = if (userdataChanged) now else existing.lastUpdated

                candidateWithBanner.copy(
                    lastUpdated = newLastUpdated,
                    progressIncreasedAt = newProgressIncreasedAt
                )
            } else {
                val newItem = MediaItem(
                    id = state.id.ifBlank { UUID.randomUUID().toString() },
                    type = state.mediaType.name,
                    format = resolvedFormat,
                    countryOfOrigin = resolvedCountry,
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
                    lastUpdated = now,
                    dateAdded = now,
                    isAdult = state.isAdult,
                    progressIncreasedAt = if (progressVal > 0 || ((state.parsedSecondaryProgress ?: 0.0) > 0.0)) now else null
                )
                if (!state.bannerImage.isNullOrBlank()) {
                    newItem.withExtra("banner_image", state.bannerImage!!)
                } else {
                    newItem
                }
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
                if (wasNew && tokenStore?.isLoggedIn == true && tokenStore.trackOnAdd && savedItem.title.isNotBlank()) {
                    autoTrackMedia(savedItem.id, savedItem.title, savedItem.mediaType)
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
        val mediaType = if (_uiState.value.mediaType == MediaType.ANIME || _uiState.value.mediaType == MediaType.TV || _uiState.value.mediaType == MediaType.MOVIE) "ANIME" else "MANGA"

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
            val type = if (_uiState.value.mediaType == MediaType.ANIME || _uiState.value.mediaType == MediaType.TV || _uiState.value.mediaType == MediaType.MOVIE) "anime" else "manga"

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

    private fun autoTrackMedia(mediaId: String, title: String, mediaType: MediaType) {
        val anilistClient = client ?: return
        val linkDao = mediaLinkDao ?: return
        viewModelScope.launch {
            try {
                val anilistType = if (mediaType == MediaType.ANIME || mediaType == MediaType.TV || mediaType == MediaType.MOVIE) "ANIME" else "MANGA"
                val results = withContext(Dispatchers.IO) {
                    anilistClient.searchMedia(title.trim(), anilistType, perPage = 1)
                }
                val match = results.firstOrNull() ?: return@launch
                val now = System.currentTimeMillis()
                val type = if (anilistType == "ANIME") "anime" else "manga"

                val existing = linkDao.getByMediaId(mediaId)
                if (existing.none { it.providerId == "anilist" }) {
                    val newLink = MediaLinkEntity(
                        id = UUID.randomUUID().toString(),
                        mediaId = mediaId,
                        kind = "TRACKER",
                        providerId = "anilist",
                        remoteId = match.id.toString(),
                        remoteMediaListId = null,
                        url = "https://anilist.co/$type/${match.id}",
                        state = "LINKED",
                        syncStatus = "DIRTY_LOCAL",
                        lastSyncedAt = null,
                        syncedSnapshot = "{}",
                        createdAt = now,
                        updatedAt = now,
                    )
                    linkDao.upsert(newLink)
                    syncEngine?.pushDirtyItem(mediaId)
                }
            } catch (_: Exception) {
                // Silently ignore background auto-track failure
            }
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
            state.mediaType != orig.mediaType ||
            state.alternativeTitles != orig.alternativeTitles ||
            state.titleRomaji != (orig.alternativeTitles.getOrNull(0) ?: "") ||
            state.titleNative != (orig.alternativeTitles.getOrNull(2) ?: "") ||
            state.formatId != resolveFormatId(orig) ||
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

    private fun guessTypeFromUrl(url: String): MediaType? {
        val lower = url.lowercase()
        return when {
            lower.contains("novelupdates.com") || lower.contains("royalroad.com") || lower.contains("wtr-lab.com") -> MediaType.NOVEL
            lower.contains("mangadex.org") || lower.contains("manganato.com") -> MediaType.MANGA
            lower.contains("myanimelist.net/anime") || lower.contains("anilist.co/anime") -> MediaType.ANIME
            lower.contains("myanimelist.net/manga") || lower.contains("anilist.co/manga") -> MediaType.MANGA
            lower.contains("steamcharts.com") || lower.contains("steampowered.com") || lower.contains("gog.com") -> MediaType.GAME
            else -> null
        }
    }
}

