package app.lazydex.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lazydex.data.local.ThemePreferences
import app.lazydex.domain.model.MediaCategory
import app.lazydex.domain.repository.MediaRepository
import app.lazydex.scraper.source.GenericSource
import app.lazydex.scraper.source.SearchResult
import app.lazydex.scraper.source.Source
import app.lazydex.scraper.source.SourceRegistry
import app.lazydex.util.UrlNormalizer
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BrowserUiState(
    val sources: List<Source> = emptyList(),
    val pinnedSourceIds: Set<String> = emptySet(),
    val selectedCategory: MediaCategory? = null,
    val selectedSource: Source? = null,
    val searchQuery: String = "",
    val detectedSourceForUrl: Source? = null,
    val searchResults: List<SearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val hasSearched: Boolean = false,
    val trackedUrls: Set<String> = emptySet()
) {
    fun isTracked(result: SearchResult): Boolean {
        if (trackedUrls.isEmpty()) return false
        val normalized = try {
            UrlNormalizer.normalize(result.url)
        } catch (_: Exception) {
            result.url
        }
        return trackedUrls.contains(result.url) || trackedUrls.contains(normalized)
    }
}

class BrowserViewModel(
    private val sourceRegistry: SourceRegistry,
    private val themePreferences: ThemePreferences,
    private val mediaRepository: MediaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    init {
        _uiState.update {
            it.copy(sources = sourceRegistry.getSearchableSources())
        }

        viewModelScope.launch {
            themePreferences.pinnedSources.collect { pinned ->
                _uiState.update { it.copy(pinnedSourceIds = pinned) }
            }
        }

        viewModelScope.launch {
            mediaRepository.observeAll().collect { items ->
                val urls = items.mapNotNull { it.sourceUrl?.takeIf { u -> u.isNotBlank() } }.toSet()
                _uiState.update { it.copy(trackedUrls = urls) }
            }
        }
    }

    fun selectSource(source: Source?) {
        _uiState.update {
            it.copy(
                selectedSource = source,
                searchQuery = "",
                searchResults = emptyList(),
                searchError = null,
                hasSearched = false,
                detectedSourceForUrl = null
            )
        }
    }

    fun setCategory(category: MediaCategory?) {
        _uiState.update {
            it.copy(
                selectedCategory = category,
                searchResults = emptyList(),
                searchError = null,
                hasSearched = false
            )
        }
    }

    fun updateSearchQuery(query: String) {
        val trimmed = query.trim()
        val detected = if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            sourceRegistry.sources.firstOrNull { it !is GenericSource && it.urlPattern.matches(trimmed) }
                ?: sourceRegistry.sources.firstOrNull { it is GenericSource }
        } else {
            null
        }

        _uiState.update {
            it.copy(
                searchQuery = query,
                detectedSourceForUrl = detected,
                hasSearched = if (query.isBlank()) false else it.hasSearched
            )
        }
    }

    fun togglePin(sourceId: String) {
        viewModelScope.launch {
            themePreferences.togglePinnedSource(sourceId)
        }
    }

    fun clearSearch() {
        _uiState.update {
            it.copy(
                searchQuery = "",
                searchResults = emptyList(),
                hasSearched = false,
                detectedSourceForUrl = null,
                searchError = null
            )
        }
    }

    fun performSearch() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isBlank()) return

        val selectedSource = _uiState.value.selectedSource
        if (selectedSource != null) {
            // Search single source
            _uiState.update { it.copy(isSearching = true, searchError = null, hasSearched = true) }
            viewModelScope.launch {
                try {
                    val results = selectedSource.search(query)
                    _uiState.update {
                        it.copy(
                            searchResults = results,
                            isSearching = false
                        )
                    }
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            searchError = e.message ?: "Search failed",
                            isSearching = false
                        )
                    }
                }
            }
            return
        }

        // Global search across sources matching category
        val category = _uiState.value.selectedCategory
        val candidateSources = if (category != null) {
            sourceRegistry.getSearchableSources().filter {
                it.categoryHint == category || it.categoryHint == null
            }
        } else {
            sourceRegistry.getSearchableSources()
        }

        if (candidateSources.isEmpty()) {
            _uiState.update {
                it.copy(
                    searchResults = emptyList(),
                    isSearching = false,
                    hasSearched = true,
                    searchError = null
                )
            }
            return
        }

        _uiState.update { it.copy(isSearching = true, searchError = null, hasSearched = true) }

        viewModelScope.launch {
            try {
                val deferreds = candidateSources.map { source ->
                    async {
                        runCatching { source.search(query) }.getOrDefault(emptyList())
                    }
                }
                val results = deferreds.awaitAll().flatten()
                _uiState.update {
                    it.copy(
                        searchResults = results,
                        isSearching = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        searchError = e.message ?: "Search failed",
                        isSearching = false
                    )
                }
            }
        }
    }
}
