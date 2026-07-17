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
    val rating: Double? = null,
    val notes: String = "",
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
    private val localCoversDir: File
) : ViewModel() {

    private val itemId: String? = savedStateHandle["itemId"]

    private val _formState = MutableStateFlow(AddEditFormState(isNew = itemId == null))
    val formState: StateFlow<AddEditFormState> = _formState.asStateFlow()

    private var originalItem: MediaItem? = null

    init {
        itemId?.let { id ->
            viewModelScope.launch {
                repository.observeById(id).collect { item ->
                    if (item != null && !_formState.value.isSaving && !_formState.value.isScraping) {
                        originalItem = item
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
        // Auto-adapt status if not compatible with new category
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

    fun updateRating(rating: Double?) {
        _formState.value = _formState.value.copy(rating = rating)
    }

    fun updateNotes(notes: String) {
        _formState.value = _formState.value.copy(notes = notes)
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
                    scrapeError = null
                )
                
                // Attempt to guess category based on URL domains
                val guessedCat = guessCategoryFromUrl(url)
                if (guessedCat != null) {
                    updateCategory(guessedCat)
                }
                
                // Trigger background cover download
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
                    errorMsg = e.message ?: "Failed to delete item.",
                    isSaving = false
                )
            }
        }
    }

    fun checkBackPressAllowed(): Boolean {
        if (_formState.value.isDone) return true
        
        // Form is dirty if values differ from original loaded item (or empty default for new)
        val state = _formState.value
        val isDirty = if (state.isNew) {
            state.title.isNotEmpty() || state.sourceUrl.isNotEmpty() || state.notes.isNotEmpty() || state.alternativeTitles.isNotEmpty() || state.currentProgress != "0"
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
            state.notes != orig.notes
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
            lower.contains("novelupdates.com") || lower.contains("royalroad.com") -> MediaCategory.NOVEL
            lower.contains("mangadex.org") || lower.contains("manganato.com") -> MediaCategory.MANGA
            lower.contains("myanimelist.net/anime") || lower.contains("anilist.co/anime") -> MediaCategory.ANIME
            lower.contains("myanimelist.net/manga") || lower.contains("anilist.co/manga") -> MediaCategory.MANGA
            lower.contains("steamcharts.com") || lower.contains("steampowered.com") || lower.contains("gog.com") -> MediaCategory.GAME
            else -> null
        }
    }
}
