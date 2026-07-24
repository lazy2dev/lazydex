package app.lazydex.ui.addedit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lazydex.domain.model.MediaCategory
import app.lazydex.domain.model.MediaItem
import app.lazydex.domain.model.UserStatus
import app.lazydex.domain.repository.DuplicateUrlException
import app.lazydex.domain.repository.MediaRepository
import app.lazydex.scraper.MetadataScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

import app.lazydex.data.anilist.ALMedia
import app.lazydex.data.anilist.AnilistApi
import app.lazydex.data.anilist.AnilistSyncManager
import app.lazydex.domain.model.MediaFormat

data class AddEditFormState(
    val id: String = "",
    val category: MediaCategory = MediaCategory.NOVEL,
    val title: String = "",
    val alternativeTitles: List<String> = emptyList(),
    val sourceUrl: String = "",
    val coverImagePath: String = "",
    val coverImageUrl: String = "",
    val currentProgress: String = "0",
    val totalItems: String = "",
    val userStatus: UserStatus = UserStatus.READING,
    val rating: Int? = null,
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
    val isEditing: Boolean = false,

    // Extended Metadata & Tracking
    val anilistListEntryId: Long? = null,
    val isPrivate: Boolean = false,
    val mediaFormat: String = "",
    val rawFormat: String = "",
    val publishingStatus: String = "",
    val season: String = "",
    val totalVolumes: String = "",
    val progressVolumes: String = "0",
    val durationMinutes: String = "",
    val sourceMaterial: String = "",
    val isAdult: Boolean = false,
    val isDoujin: Boolean = false,
    val syncPendingAction: String? = null,

    // Tracker Bottom Sheet UI state
    val showTrackerSheet: Boolean = false,
    val trackerSearchQuery: String = "",
    val isTrackerSearching: Boolean = false,
    val trackerSearchResults: List<ALMedia> = emptyList()
) {
    // Form level validation checks
    val isTitleBlank: Boolean get() = title.trim().isBlank()
    
    val parsedProgress: Int? get() = currentProgress.trim().toIntOrNull()
    val parsedTotal: Int? get() = totalItems.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
    val parsedTotalVolumes: Int? get() = totalVolumes.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
    val parsedProgressVolumes: Int get() = progressVolumes.trim().toIntOrNull() ?: 0
    val parsedDurationMinutes: Int? get() = durationMinutes.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()

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
        return !url.startsWith("https://", ignoreCase = true)
    }

    val canSave: Boolean get() = !isTitleBlank && !isProgressInvalid && !isTotalInvalid && !isUrlInvalid && !isScraping && !isSaving
}

class UnifiedAddEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: MediaRepository,
    private val scraper: MetadataScraper,
    private val okHttpClient: OkHttpClient,
    private val cacheDir: File,
    private val localCoversDir: File,
    private val anilistApi: AnilistApi,
    private val syncManager: AnilistSyncManager
) : ViewModel() {

    private val itemId: String? = savedStateHandle["itemId"]
    private val initialUrlParam: String? = savedStateHandle["initialUrl"]

    private val isNewItem: Boolean = itemId == null
    private val _formState = MutableStateFlow(AddEditFormState(isNew = isNewItem, isEditing = isNewItem))
    val formState: StateFlow<AddEditFormState> = _formState.asStateFlow()

    private var originalItem: MediaItem? = null

    init {
        initialUrlParam?.let { url ->
            if (url.isNotBlank()) {
                updateSourceUrl(url)
                scrapeUrl()
            }
        }

        itemId?.let { id ->
            viewModelScope.launch {
                repository.observeById(id).collect { item ->
                    if (item != null && !_formState.value.isSaving && !_formState.value.isScraping) {
                        originalItem = item
                        if (!_formState.value.isEditing) {
                            populateFromItem(item)
                        }
                    }
                }
            }
        }
    }

    private fun populateFromItem(item: MediaItem) {
        _formState.value = _formState.value.copy(
            id = item.id,
            category = item.category,
            title = item.title,
            alternativeTitles = item.alternativeTitles,
            sourceUrl = item.sourceUrl ?: "",
            coverImagePath = item.coverImagePath,
            coverImageUrl = item.coverImageUrl ?: "",
            currentProgress = item.currentProgress.toString(),
            totalItems = item.totalItems?.toString() ?: "",
            userStatus = item.userStatus,
            rating = item.rating,
            notes = item.notes,
            genres = item.genres,
            tags = item.tags,
            author = item.author,
            description = item.description,
            startDate = item.startDate,
            endDate = item.endDate,
            isNew = false,
            anilistListEntryId = item.anilistListEntryId,
            isPrivate = item.isPrivate,
            mediaFormat = item.mediaFormat?.name ?: item.rawFormat ?: "",
            rawFormat = item.rawFormat ?: "",
            publishingStatus = item.publishingStatus ?: "",
            season = item.season ?: "",
            totalVolumes = item.totalVolumes?.toString() ?: "",
            progressVolumes = item.progressVolumes.toString(),
            durationMinutes = item.durationMinutes?.toString() ?: "",
            sourceMaterial = item.sourceMaterial ?: "",
            isAdult = item.isAdult,
            isDoujin = item.isDoujin,
            syncPendingAction = item.syncPendingAction
        )
    }

    fun startEditing() {
        _formState.value = _formState.value.copy(isEditing = true)
    }

    fun cancelEditing() {
        if (_formState.value.isNew) {
            _formState.value = _formState.value.copy(isDone = true)
        } else {
            originalItem?.let { populateFromItem(it) }
            _formState.value = _formState.value.copy(isEditing = false, showDiscardConfirm = false)
        }
    }

    fun updateTitle(title: String) {
        _formState.value = _formState.value.copy(title = title, errorMsg = null)
    }

    fun updateCategory(category: MediaCategory) {
        val currentStatus = _formState.value.userStatus
        val adaptiveStatus = when (category) {
            MediaCategory.NOVEL, MediaCategory.MANGA -> {
                if (currentStatus in listOf(UserStatus.WATCHING, UserStatus.PLAYING)) UserStatus.READING else currentStatus
            }
            MediaCategory.ANIME, MediaCategory.MOVIE, MediaCategory.TV -> {
                if (currentStatus in listOf(UserStatus.READING, UserStatus.PLAYING)) UserStatus.WATCHING else currentStatus
            }
            MediaCategory.GAME -> {
                if (currentStatus in listOf(UserStatus.READING, UserStatus.WATCHING)) UserStatus.PLAYING else currentStatus
            }
        }
        _formState.value = _formState.value.copy(category = category, userStatus = adaptiveStatus)
    }

    fun updateAltTitles(alternativeTitles: List<String>) {
        _formState.value = _formState.value.copy(alternativeTitles = alternativeTitles)
    }

    fun updateSourceUrl(url: String) {
        _formState.value = _formState.value.copy(sourceUrl = url)
    }

    fun updateCoverImageUrl(url: String) {
        _formState.value = _formState.value.copy(coverImageUrl = url)
        if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
            viewModelScope.launch {
                val tempFile = downloadImageToTemp(url)
                if (tempFile != null) {
                    _formState.value = _formState.value.copy(coverImagePath = tempFile.absolutePath)
                }
            }
        }
    }

    fun updateProgress(progress: String) {
        _formState.value = _formState.value.copy(currentProgress = progress)
    }

    fun updateTotal(total: String) {
        _formState.value = _formState.value.copy(totalItems = total)
    }

    fun updateStatus(status: UserStatus) {
        _formState.value = _formState.value.copy(userStatus = status)
    }

    fun updateRating(rating: Int?) {
        _formState.value = _formState.value.copy(rating = rating)
    }

    fun updateNotes(notes: String) {
        _formState.value = _formState.value.copy(notes = notes)
    }

    fun updateAuthor(author: String) {
        _formState.value = _formState.value.copy(author = author)
    }

    fun updateDescription(description: String) {
        _formState.value = _formState.value.copy(description = description)
    }

    fun updateStartDate(date: Long?) {
        _formState.value = _formState.value.copy(startDate = date)
    }

    fun updateEndDate(date: Long?) {
        _formState.value = _formState.value.copy(endDate = date)
    }

    fun addGenre(genre: String) {
        val trimmed = genre.trim()
        if (trimmed.isNotBlank() && _formState.value.genres.none { it.equals(trimmed, ignoreCase = true) }) {
            _formState.value = _formState.value.copy(genres = _formState.value.genres + trimmed)
        }
    }

    fun removeGenre(genre: String) {
        _formState.value = _formState.value.copy(genres = _formState.value.genres.filter { !it.equals(genre, ignoreCase = true) })
    }

    fun addTag(tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isNotBlank() && _formState.value.tags.none { it.equals(trimmed, ignoreCase = true) }) {
            _formState.value = _formState.value.copy(tags = _formState.value.tags + trimmed)
        }
    }

    fun removeTag(tag: String) {
        _formState.value = _formState.value.copy(tags = _formState.value.tags.filter { !it.equals(tag, ignoreCase = true) })
    }

    fun scrapeUrl() {
        val url = _formState.value.sourceUrl.trim()
        if (url.isEmpty()) return
        
        _formState.value = _formState.value.copy(isScraping = true, scrapeError = null)
        
        viewModelScope.launch {
            val result = scraper.scrape(url)
            result.onSuccess { metadata ->
                _formState.value = _formState.value.copy(
                    title = metadata.title.ifEmpty { _formState.value.title },
                    coverImageUrl = metadata.imageUrl.ifEmpty { _formState.value.coverImageUrl },
                    author = metadata.author.ifEmpty { _formState.value.author },
                    description = metadata.description.ifEmpty { _formState.value.description },
                    genres = if (metadata.genres.isNotEmpty()) metadata.genres else _formState.value.genres,
                    tags = if (metadata.tags.isNotEmpty()) metadata.tags else _formState.value.tags,
                    scrapeError = null
                )
                
                metadata.category?.let { cat ->
                    updateCategory(cat)
                } ?: guessCategoryFromUrl(url)?.let { guessed ->
                    updateCategory(guessed)
                }
                
                if (metadata.imageUrl.isNotEmpty()) {
                    val tempFile = downloadImageToTemp(metadata.imageUrl)
                    if (tempFile != null) {
                        _formState.value = _formState.value.copy(coverImagePath = tempFile.absolutePath)
                    }
                }
            }.onFailure { exception ->
                _formState.value = _formState.value.copy(scrapeError = exception.message ?: "Scraping failed")
            }
            _formState.value = _formState.value.copy(isScraping = false)
        }
    }

    fun showTrackerSheet(show: Boolean) {
        _formState.value = _formState.value.copy(
            showTrackerSheet = show,
            trackerSearchQuery = if (show && _formState.value.trackerSearchQuery.isBlank()) _formState.value.title else _formState.value.trackerSearchQuery
        )
    }

    fun updateTrackerSearchQuery(query: String) {
        _formState.value = _formState.value.copy(trackerSearchQuery = query)
    }

    fun searchAniList() {
        val query = _formState.value.trackerSearchQuery.trim()
        if (query.isBlank()) return
        _formState.value = _formState.value.copy(isTrackerSearching = true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val results = anilistApi.searchMedia(query)
                _formState.value = _formState.value.copy(
                    isTrackerSearching = false,
                    trackerSearchResults = results
                )
            } catch (e: Exception) {
                _formState.value = _formState.value.copy(
                    isTrackerSearching = false,
                    errorMsg = "Search failed: ${e.message}"
                )
            }
        }
    }

    fun bindAniListMedia(media: ALMedia) {
        val formatStr = media.format ?: ""
        val url = "https://anilist.co/${if (media.format in listOf("MANGA", "ONE_SHOT", "NOVEL")) "manga" else "anime"}/${media.id}"
        _formState.value = _formState.value.copy(
            anilistListEntryId = media.id,
            sourceUrl = if (_formState.value.sourceUrl.isBlank()) url else _formState.value.sourceUrl,
            rawFormat = formatStr,
            totalItems = (media.chapters ?: media.volumes)?.toString() ?: _formState.value.totalItems,
            showTrackerSheet = false,
            trackerSearchResults = emptyList()
        )
    }

    fun unbindAniListMedia() {
        _formState.value = _formState.value.copy(
            anilistListEntryId = null,
            syncPendingAction = null,
            showTrackerSheet = false
        )
    }

    fun updateProgressVolumes(volumes: String) {
        _formState.value = _formState.value.copy(progressVolumes = volumes)
    }

    fun updateIsPrivate(isPrivate: Boolean) {
        _formState.value = _formState.value.copy(isPrivate = isPrivate)
    }

    fun updateExtendedMetadata(
        mediaFormat: String? = null,
        publishingStatus: String? = null,
        season: String? = null,
        totalVolumes: String? = null,
        durationMinutes: String? = null,
        sourceMaterial: String? = null,
        isAdult: Boolean? = null,
        isDoujin: Boolean? = null
    ) {
        _formState.value = _formState.value.copy(
            mediaFormat = mediaFormat ?: _formState.value.mediaFormat,
            publishingStatus = publishingStatus ?: _formState.value.publishingStatus,
            season = season ?: _formState.value.season,
            totalVolumes = totalVolumes ?: _formState.value.totalVolumes,
            durationMinutes = durationMinutes ?: _formState.value.durationMinutes,
            sourceMaterial = sourceMaterial ?: _formState.value.sourceMaterial,
            isAdult = isAdult ?: _formState.value.isAdult,
            isDoujin = isDoujin ?: _formState.value.isDoujin
        )
    }

    fun save() {
        val state = _formState.value
        if (!state.canSave) return

        _formState.value = _formState.value.copy(isSaving = true)

        viewModelScope.launch {
            val totalVal = state.parsedTotal
            val progressVal = state.parsedProgress ?: 0

            val item = MediaItem(
                id = state.id,
                category = state.category,
                title = state.title,
                alternativeTitles = state.alternativeTitles,
                sourceUrl = state.sourceUrl.trim().ifEmpty { null },
                coverImagePath = state.coverImagePath,
                coverImageUrl = state.coverImageUrl.trim().ifEmpty { null },
                currentProgress = progressVal,
                totalItems = totalVal,
                userStatus = state.userStatus,
                rating = state.rating,
                notes = state.notes,
                genres = state.genres,
                tags = state.tags,
                author = state.author,
                description = state.description,
                startDate = state.startDate,
                endDate = state.endDate,
                lastUpdated = System.currentTimeMillis(),
                dateAdded = originalItem?.dateAdded ?: System.currentTimeMillis(),
                anilistListEntryId = state.anilistListEntryId,
                isPrivate = state.isPrivate,
                mediaFormat = MediaFormat.fromString(state.mediaFormat),
                rawFormat = state.rawFormat.ifEmpty { null },
                publishingStatus = state.publishingStatus.ifEmpty { null },
                season = state.season.ifEmpty { null },
                totalVolumes = state.parsedTotalVolumes,
                progressVolumes = state.parsedProgressVolumes,
                durationMinutes = state.parsedDurationMinutes,
                sourceMaterial = state.sourceMaterial.ifEmpty { null },
                isAdult = state.isAdult,
                isDoujin = state.isDoujin,
                syncPendingAction = state.syncPendingAction
            )

            try {
                if (state.isNew) {
                    repository.add(item)
                    _formState.value = _formState.value.copy(isDone = true, isSaving = false)
                } else {
                    repository.update(item)
                    originalItem = item
                    _formState.value = _formState.value.copy(isSaving = false, isEditing = false)
                }
            } catch (e: DuplicateUrlException) {
                _formState.value = _formState.value.copy(
                    errorMsg = "A media tracker with this URL already exists.",
                    isSaving = false
                )
            } catch (e: Exception) {
                _formState.value = _formState.value.copy(
                    errorMsg = e.message ?: "Failed to save media item.",
                    isSaving = false
                )
            }
        }
    }

    fun deleteItem() {
        val id = _formState.value.id
        if (id.isEmpty()) return
        
        _formState.value = _formState.value.copy(isSaving = true, showDeleteConfirm = false)
        viewModelScope.launch {
            try {
                repository.delete(id)
                _formState.value = _formState.value.copy(isDone = true)
            } catch (e: Exception) {
                _formState.value = _formState.value.copy(
                    errorMsg = e.message ?: "Failed to delete item.",
                    isSaving = false
                )
            }
        }
    }

    fun checkBackPressAllowed(): Boolean {
        if (_formState.value.isDone) return true
        
        val state = _formState.value
        val isDirty = if (state.isNew) {
            state.title.isNotEmpty() ||
            state.sourceUrl.isNotEmpty() ||
            state.notes.isNotEmpty() ||
            state.alternativeTitles.isNotEmpty() ||
            state.currentProgress != "0" ||
            state.author.isNotEmpty() ||
            state.description.isNotEmpty() ||
            state.genres.isNotEmpty() ||
            state.tags.isNotEmpty() ||
            state.startDate != null ||
            state.endDate != null
        } else {
            val orig = originalItem
            orig == null ||
            state.title != orig.title ||
            state.category != orig.category ||
            state.alternativeTitles != orig.alternativeTitles ||
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
            _formState.value = _formState.value.copy(showDiscardConfirm = true)
            return false
        }
        return true
    }

    fun dismissDiscardConfirm() {
        _formState.value = _formState.value.copy(showDiscardConfirm = false)
    }

    fun showDeleteConfirm(show: Boolean) {
        _formState.value = _formState.value.copy(showDeleteConfirm = show)
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
