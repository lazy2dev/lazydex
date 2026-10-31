package app.lazydex.ui.addedit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lazydex.domain.model.MediaCategory
import app.lazydex.domain.model.MediaItem
import app.lazydex.domain.model.ResolvedCategoryConfig
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
    val isNew: Boolean = true
) {
    // Form level validation checks
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
    val isFormValid: Boolean get() = canSave
}

class UnifiedAddEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: MediaRepository,
    private val scraper: MetadataScraper,
    private val okHttpClient: OkHttpClient,
    private val cacheDir: File
) : ViewModel() {

    private val itemId: String? = savedStateHandle["itemId"]
    private val initialUrl: String? = savedStateHandle["initialUrl"]

    private val _formState = MutableStateFlow(AddEditFormState())
    val formState: StateFlow<AddEditFormState> = _formState.asStateFlow()

    private var originalItem: MediaItem? = null

    init {
        initialize(itemId, initialUrl)
    }

    fun initialize(id: String?, initialUrl: String?) {
        val effectiveUrl = initialUrl?.takeIf { it.isNotBlank() }
        val effectiveId = id?.takeIf { it.isNotBlank() }

        _formState.value = _formState.value.copy(isNew = effectiveId == null)

        effectiveUrl?.let { url ->
            updateSourceUrl(url)
            scrapeUrl(url)
        }

        viewModelScope.launch {
            val initialCatId = when (_formState.value.category) {
                MediaCategory.NOVEL -> "cat_novel"
                MediaCategory.MANGA -> "cat_manga"
                MediaCategory.ANIME -> "cat_anime"
                MediaCategory.GAME -> "cat_game"
                MediaCategory.MOVIE -> "cat_movie"
                MediaCategory.TV -> "cat_tv"
            }
            val config = repository.getResolvedConfig(initialCatId)
            _formState.value = _formState.value.copy(resolvedConfig = config)
        }

        effectiveId?.let { targetId ->
            viewModelScope.launch {
                repository.observeById(targetId).collect { item ->
                    if (item != null && !_formState.value.isSaving && !_formState.value.isScraping) {
                        originalItem = item
                        val config = repository.getResolvedConfig(item.categoryId)
                        val timeSpent = repository.getTimeSpentMinutes(item.id)
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
                            isNew = false
                        )
                    }
                }
            }
        }
    }

    fun updateTitle(title: String) {
        _formState.value = _formState.value.copy(title = title, errorMsg = null)
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
        viewModelScope.launch {
            val config = repository.getResolvedConfig(catId)
            _formState.value = _formState.value.copy(category = category, resolvedConfig = config)
        }
    }

    fun updateActionOverride(mode: String?) {
        _formState.value = _formState.value.copy(actionOverride = mode)
    }

    fun updatePlaybackSpeed(speed: Double) {
        _formState.value = _formState.value.copy(playbackSpeed = speed)
    }

    fun updateAvgChapterLength(length: String) {
        _formState.value = _formState.value.copy(avgChapterLength = length)
    }

    fun updateAlternativeTitles(titles: List<String>) {
        _formState.value = _formState.value.copy(alternativeTitles = titles)
    }

    fun updateAltTitles(titles: List<String>) = updateAlternativeTitles(titles)

    fun updateSourceUrl(url: String) {
        _formState.value = _formState.value.copy(sourceUrl = url, errorMsg = null)
    }

    fun updateCoverImagePath(path: String) {
        _formState.value = _formState.value.copy(coverImagePath = path)
    }

    fun updateCoverImageUrl(url: String) {
        _formState.value = _formState.value.copy(coverImageUrl = url)
    }

    fun updateCurrentProgress(progress: String) {
        _formState.value = _formState.value.copy(currentProgress = progress)
    }

    fun updateProgress(progress: String) = updateCurrentProgress(progress)

    fun updateTotalItems(total: String) {
        _formState.value = _formState.value.copy(totalItems = total)
    }

    fun updateTotal(total: String) = updateTotalItems(total)

    fun updateUserStatus(status: UserStatus) {
        _formState.value = _formState.value.copy(userStatus = status)
    }

    fun updateStatus(status: UserStatus) = updateUserStatus(status)

    fun updateRating(rating: Double?) {
        _formState.value = _formState.value.copy(rating = rating)
    }

    fun updateNotes(notes: String) {
        _formState.value = _formState.value.copy(notes = notes)
    }

    fun updateGenres(genres: List<String>) {
        _formState.value = _formState.value.copy(genres = genres)
    }

    fun addGenre(genre: String) {
        val trimmed = genre.trim()
        if (trimmed.isNotBlank() && !_formState.value.genres.contains(trimmed)) {
            _formState.value = _formState.value.copy(genres = _formState.value.genres + trimmed)
        }
    }

    fun removeGenre(genre: String) {
        _formState.value = _formState.value.copy(genres = _formState.value.genres - genre)
    }

    fun updateTags(tags: List<String>) {
        _formState.value = _formState.value.copy(tags = tags)
    }

    fun addTag(tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isNotBlank() && !_formState.value.tags.contains(trimmed)) {
            _formState.value = _formState.value.copy(tags = _formState.value.tags + trimmed)
        }
    }

    fun removeTag(tag: String) {
        _formState.value = _formState.value.copy(tags = _formState.value.tags - tag)
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

    fun setError(message: String?) {
        _formState.value = _formState.value.copy(errorMsg = message)
    }

    fun scrapeUrl(urlOverride: String? = null) {
        val rawUrl = urlOverride ?: _formState.value.sourceUrl
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return

        _formState.value = _formState.value.copy(isScraping = true, scrapeError = null)

        viewModelScope.launch {
            try {
                val result = scraper.scrape(trimmed)
                result.onSuccess { metadata ->
                    val downloadedCover = metadata.imageUrl?.let { coverUrl ->
                        downloadImageToTemp(coverUrl)
                    }

                    _formState.value = _formState.value.copy(
                        title = metadata.title.takeIf { it.isNotBlank() } ?: _formState.value.title,
                        description = metadata.description.takeIf { it.isNotBlank() } ?: _formState.value.description,
                        author = metadata.author.takeIf { it.isNotBlank() } ?: _formState.value.author,
                        coverImageUrl = metadata.imageUrl ?: _formState.value.coverImageUrl,
                        coverImagePath = downloadedCover?.absolutePath ?: _formState.value.coverImagePath,
                        genres = if (metadata.genres.isNotEmpty()) metadata.genres else _formState.value.genres,
                        tags = if (metadata.tags.isNotEmpty()) metadata.tags else _formState.value.tags,
                        isScraping = false
                    )

                    metadata.category?.let { cat ->
                        updateCategory(cat)
                    } ?: run {
                        guessCategoryFromUrl(trimmed)?.let { guessed ->
                            updateCategory(guessed)
                        }
                    }
                }.onFailure { exception ->
                    _formState.value = _formState.value.copy(
                        isScraping = false,
                        scrapeError = exception.message ?: "Failed to scrape metadata"
                    )
                }
            } catch (e: Exception) {
                _formState.value = _formState.value.copy(
                    isScraping = false,
                    scrapeError = e.message ?: "Failed to scrape metadata"
                )
            }
        }
    }

    fun save() {
        val state = _formState.value
        if (state.isTitleBlank) {
            _formState.value = _formState.value.copy(errorMsg = "Title is required")
            return
        }
        if (state.isProgressInvalid) {
            _formState.value = _formState.value.copy(errorMsg = "Progress cannot exceed total items")
            return
        }
        if (state.isTotalInvalid) {
            _formState.value = _formState.value.copy(errorMsg = "Total items cannot be negative")
            return
        }
        if (state.isUrlInvalid) {
            _formState.value = _formState.value.copy(errorMsg = "Source URL must start with http:// or https://")
            return
        }
        if (state.isSaving || state.isScraping) return

        _formState.value = _formState.value.copy(isSaving = true, errorMsg = null)

        viewModelScope.launch {
            val totalVal = state.parsedTotal
            val progressVal = state.parsedProgress ?: 0

            val catId = when (state.category) {
                MediaCategory.NOVEL -> "cat_novel"
                MediaCategory.MANGA -> "cat_manga"
                MediaCategory.ANIME -> "cat_anime"
                MediaCategory.GAME -> "cat_game"
                MediaCategory.MOVIE -> "cat_movie"
                MediaCategory.TV -> "cat_tv"
            }

            val item = MediaItem(
                id = state.id,
                categoryId = catId,
                title = state.title,
                alternativeTitles = state.alternativeTitles,
                sourceUrl = state.sourceUrl.trim().ifEmpty { null },
                coverImagePath = state.coverImagePath,
                coverImageUrl = state.coverImageUrl.trim().ifEmpty { null },
                currentProgress = progressVal,
                totalItems = totalVal,
                progress = progressVal.toDouble(),
                progressTotal = totalVal?.toDouble(),
                actionOverride = state.actionOverride,
                playbackSpeed = state.playbackSpeed,
                avgChapterLength = state.avgChapterLength.toIntOrNull(),
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
                dateAdded = originalItem?.dateAdded ?: System.currentTimeMillis()
            )

            try {
                if (state.isNew) {
                    repository.add(item)
                } else {
                    repository.update(item)
                }
                _formState.value = _formState.value.copy(isDone = true)
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
                    errorMsg = e.message ?: "Failed to delete item",
                    isSaving = false
                )
            }
        }
    }

    fun checkBackPressAllowed(): Boolean {
        if (_formState.value.isDone) return true
        
        val state = _formState.value
        val isDirty = if (state.isNew) {
            state.title.isNotBlank() || state.sourceUrl.isNotBlank() || state.notes.isNotBlank()
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

    fun onBackPress(): Boolean = checkBackPressAllowed()

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
