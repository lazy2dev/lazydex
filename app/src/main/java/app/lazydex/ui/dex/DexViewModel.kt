package app.lazydex.ui.dex

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lazydex.domain.model.MediaCategory
import app.lazydex.domain.model.MediaItem
import app.lazydex.domain.model.SortDirection
import app.lazydex.domain.model.SortField
import app.lazydex.domain.model.StatusFilter
import app.lazydex.domain.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class DexUiState(
    val items: List<MediaItem> = emptyList(),
    val totalCount: Int = 0,
    val selectedCategory: MediaCategory? = null,
    val selectedStatus: StatusFilter = StatusFilter.ALL,
    val sortField: SortField = SortField.DATE_ADDED,
    val sortDirection: SortDirection = SortDirection.DESCENDING,
    val selectedGenres: Set<String> = emptySet(),
    val availableGenres: List<String> = emptyList(),
    val selectedTags: Set<String> = emptySet(),
    val availableTags: List<String> = emptyList(),
    val authorQuery: String = "",
    val minRating: Double? = null,
    val maxRating: Double? = null,
    val dateRangeStart: Long? = null,
    val dateRangeEnd: Long? = null,
    val perCategoryCounts: Map<MediaCategory, Int> = emptyMap(),
    val perStatusCounts: Map<StatusFilter, Int> = emptyMap(),
    val isLoading: Boolean = true
)

private data class MutableFilterState(
    val category: MediaCategory? = null,
    val status: StatusFilter = StatusFilter.ALL,
    val genres: Set<String> = emptySet(),   // lowercase keys
    val tags: Set<String> = emptySet()      // lowercase keys
)

private data class FilterBundle(
    val category: MediaCategory?,
    val status: StatusFilter,
    val selectedGenres: Set<String>,
    val selectedTags: Set<String>,
    val perStatusCounts: Map<StatusFilter, Int> = emptyMap()
)

private data class AdvancedFilterBundle(
    val authorQuery: String,
    val minRating: Double?,
    val maxRating: Double?,
    val dateRangeStart: Long?,
    val dateRangeEnd: Long?
)

private data class SortBundle(
    val field: SortField,
    val direction: SortDirection
)

private data class MetadataBundle(
    val genres: List<String>,
    val tags: List<String>,
    val categoryCounts: Map<MediaCategory, Int>
)

@OptIn(ExperimentalCoroutinesApi::class)
class DexViewModel(
    private val repository: MediaRepository
) : ViewModel() {

    private val mutableFilterState = MutableStateFlow(MutableFilterState())
    private val sortField = MutableStateFlow(SortField.DATE_ADDED)
    private val sortDirection = MutableStateFlow(SortDirection.DESCENDING)

    private val authorQuery = MutableStateFlow("")
    private val minRating = MutableStateFlow<Double?>(null)
    private val maxRating = MutableStateFlow<Double?>(null)
    private val dateRangeStart = MutableStateFlow<Long?>(null)
    private val dateRangeEnd = MutableStateFlow<Long?>(null)

    private val selectedCategory: StateFlow<MediaCategory?> = mutableFilterState.map { it.category }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    private val selectedStatus: StateFlow<StatusFilter> = mutableFilterState.map { it.status }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatusFilter.ALL)

    private val availableGenres = selectedCategory.flatMapLatest { cat ->
        repository.observeDistinctGenres(cat)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val availableTags = selectedCategory.flatMapLatest { cat ->
        repository.observeDistinctTags(cat)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val dbFiltered = selectedCategory.flatMapLatest { cat ->
        selectedStatus.flatMapLatest { stat ->
            repository.observeFiltered(cat, stat)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val perStatusCounts = selectedCategory.flatMapLatest { cat ->
        repository.observeStatusCounts(cat)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val filterState = combine(
        mutableFilterState,
        perStatusCounts
    ) { mfs, sc ->
        FilterBundle(mfs.category, mfs.status, mfs.genres, mfs.tags, sc)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FilterBundle(null, StatusFilter.ALL, emptySet(), emptySet(), emptyMap()))

    private val advancedFilterState = combine(
        authorQuery, minRating, maxRating, dateRangeStart, dateRangeEnd
    ) { aq, min, max, ds, de -> AdvancedFilterBundle(aq, min, max, ds, de) }

    private val categoryCounts = repository.observeCategoryCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val metadataState = combine(
        availableGenres, availableTags, categoryCounts
    ) { genres, tags, cc -> MetadataBundle(genres, tags, cc) }

    val uiState: StateFlow<DexUiState> = combine(
        dbFiltered,
        filterState,
        advancedFilterState,
        combine(sortField, sortDirection) { f, d -> SortBundle(f, d) },
        metadataState
    ) { items, fs, afs, ss, meta ->
        val filtered = items.filter { item ->
            (fs.selectedGenres.isEmpty() || item.genres.any { it.lowercase() in fs.selectedGenres }) &&
            (fs.selectedTags.isEmpty() || item.tags.any { it.lowercase() in fs.selectedTags }) &&
            (afs.authorQuery.isBlank() || item.author.contains(afs.authorQuery, ignoreCase = true)) &&
            (afs.minRating == null || (item.rating ?: 0).toDouble() >= afs.minRating) &&
            (afs.maxRating == null || (item.rating ?: 0).toDouble() <= afs.maxRating) &&
            (afs.dateRangeStart == null || (item.startDate != null && item.startDate >= afs.dateRangeStart)) &&
            (afs.dateRangeEnd == null || (item.startDate != null && item.startDate <= afs.dateRangeEnd))
        }

        DexUiState(
            items = sortItems(filtered, ss.field, ss.direction),
            selectedCategory = fs.category,
            selectedStatus = fs.status,
            sortField = ss.field,
            sortDirection = ss.direction,
            selectedGenres = fs.selectedGenres,
            availableGenres = if (fs.category != null) meta.genres else emptyList(),
            selectedTags = fs.selectedTags,
            availableTags = if (fs.category != null) meta.tags else emptyList(),
            authorQuery = afs.authorQuery,
            minRating = afs.minRating,
            maxRating = afs.maxRating,
            dateRangeStart = afs.dateRangeStart,
            dateRangeEnd = afs.dateRangeEnd,
            perCategoryCounts = meta.categoryCounts,
            perStatusCounts = fs.perStatusCounts,
            isLoading = false
        )
    }.flowOn(Dispatchers.Default)
    .combine(repository.observeCount()) { state, count -> state.copy(totalCount = count) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DexUiState())

    fun selectCategory(category: MediaCategory?) {
        mutableFilterState.update { it.copy(category = category, genres = emptySet(), tags = emptySet()) }
    }

    fun selectStatus(status: StatusFilter) {
        mutableFilterState.update { it.copy(status = status) }
    }

    fun selectGenres(genres: Set<String>) {
        mutableFilterState.update { it.copy(genres = genres.map { g -> g.lowercase() }.toSet()) }
    }

    fun selectTags(tags: Set<String>) {
        mutableFilterState.update { it.copy(tags = tags.map { t -> t.lowercase() }.toSet()) }
    }

    fun toggleGenre(genre: String) {
        val lower = genre.lowercase()
        mutableFilterState.update { state ->
            val updated = if (lower in state.genres) state.genres - lower else state.genres + lower
            state.copy(genres = updated)
        }
    }

    fun toggleTag(tag: String) {
        val lower = tag.lowercase()
        mutableFilterState.update { state ->
            val updated = if (lower in state.tags) state.tags - lower else state.tags + lower
            state.copy(tags = updated)
        }
    }

    fun setAuthorQuery(query: String) {
        authorQuery.value = query
    }

    fun setRatingRange(min: Double?, max: Double?) {
        minRating.value = min
        maxRating.value = max
    }

    fun setDateRange(start: Long?, end: Long?) {
        dateRangeStart.value = start
        dateRangeEnd.value = end
    }

    fun selectSortField(field: SortField) {
        sortField.value = field
    }

    fun selectSortDirection(direction: SortDirection) {
        sortDirection.value = direction
    }

    fun clearFilters() {
        mutableFilterState.update { MutableFilterState() }
        authorQuery.value = ""
        minRating.value = null
        maxRating.value = null
        dateRangeStart.value = null
        dateRangeEnd.value = null
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
