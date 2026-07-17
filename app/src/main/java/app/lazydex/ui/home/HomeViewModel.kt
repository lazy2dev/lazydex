package app.lazydex.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lazydex.domain.model.MediaCategory
import app.lazydex.domain.model.MediaItem
import app.lazydex.domain.model.SortDirection
import app.lazydex.domain.model.SortField
import app.lazydex.domain.model.StatusFilter
import app.lazydex.domain.repository.MediaRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val items: List<MediaItem> = emptyList(),
    val selectedCategory: MediaCategory? = null,
    val selectedStatus: StatusFilter = StatusFilter.ALL,
    val sortField: SortField = SortField.DATE_ADDED,
    val sortDirection: SortDirection = SortDirection.DESCENDING,
    val isLoading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repository: MediaRepository
) : ViewModel() {

    private val selectedCategory = MutableStateFlow<MediaCategory?>(null)
    private val selectedStatus = MutableStateFlow<StatusFilter>(StatusFilter.ALL)
    private val sortField = MutableStateFlow<SortField>(SortField.DATE_ADDED)
    private val sortDirection = MutableStateFlow<SortDirection>(SortDirection.DESCENDING)
    private val isLoading = MutableStateFlow(true)

    val uiState: StateFlow<HomeUiState> = combine(
        selectedCategory,
        selectedStatus,
        sortField,
        sortDirection,
        selectedCategory.flatMapLatest { cat ->
            selectedStatus.flatMapLatest { stat ->
                repository.observeFiltered(cat, stat)
            }
        }
    ) { category, status, field, direction, items ->
        val sorted = sortItems(items, field, direction)
        HomeUiState(
            items = sorted,
            selectedCategory = category,
            selectedStatus = status,
            sortField = field,
            sortDirection = direction,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun selectCategory(category: MediaCategory?) {
        selectedCategory.value = category
    }

    fun selectStatus(status: StatusFilter) {
        selectedStatus.value = status
    }

    fun selectSortField(field: SortField) {
        sortField.value = field
    }

    fun selectSortDirection(direction: SortDirection) {
        sortDirection.value = direction
    }

    fun clearFilters() {
        selectedCategory.value = null
        selectedStatus.value = StatusFilter.ALL
    }

    private fun sortItems(items: List<MediaItem>, field: SortField, direction: SortDirection): List<MediaItem> {
        val sorted = when (field) {
            SortField.DATE_ADDED -> items.sortedBy { it.dateAdded }
            SortField.LAST_ACTIVE -> items.sortedBy { it.lastUpdated }
            SortField.TITLE -> items.sortedBy { it.title.lowercase() }
            SortField.PROGRESS -> items.sortedBy { 
                val total = it.totalItems ?: 0
                if (total <= 0) 0.0 else it.currentProgress.toDouble() / total.toDouble()
            }
        }
        return if (direction == SortDirection.ASCENDING) sorted else sorted.reversed()
    }
}
