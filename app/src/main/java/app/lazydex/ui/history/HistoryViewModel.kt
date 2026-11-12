package app.lazydex.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lazydex.data.local.ThemePreferences
import app.lazydex.domain.model.MediaItem
import app.lazydex.domain.repository.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HistoryUiState(
    val items: List<MediaItem> = emptyList(),
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val blurAdultCovers: Boolean = false,
    val isLoading: Boolean = true
)

class HistoryViewModel(
    private val repository: MediaRepository,
    private val themePreferences: ThemePreferences
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _isSearching = MutableStateFlow(false)

    val uiState: StateFlow<HistoryUiState> = combine(
        repository.observeHistory(),
        themePreferences.blurAdultCovers,
        _searchQuery,
        _isSearching
    ) { items, blur, query, isSearching ->
        val trimmed = query.trim()
        val filtered = if (trimmed.isEmpty()) {
            items
        } else {
            items.filter { item ->
                item.title.contains(trimmed, ignoreCase = true) ||
                    item.author.contains(trimmed, ignoreCase = true)
            }
        }
        HistoryUiState(
            items = filtered,
            searchQuery = query,
            isSearching = isSearching,
            blurAdultCovers = blur,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HistoryUiState()
    )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onSearchingToggle(searching: Boolean) {
        _isSearching.value = searching
        if (!searching) {
            _searchQuery.value = ""
        }
    }

    fun clearSearchQuery() {
        _searchQuery.value = ""
    }
}
