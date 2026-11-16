package app.lazydex.ui.dex

import java.io.File
import java.util.UUID
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lazydex.data.cache.CoverCache
import app.lazydex.data.local.LibraryDisplayMode
import app.lazydex.data.local.LibraryGroupMode
import app.lazydex.data.local.ThemePreferences
import app.lazydex.data.tracker.sync.TrackerSyncEngine
import app.lazydex.domain.model.MediaItem
import app.lazydex.domain.model.MediaType
import app.lazydex.domain.model.SortDirection
import app.lazydex.domain.model.SortField
import app.lazydex.domain.model.StatusFilter
import app.lazydex.domain.model.TypeConfig
import app.lazydex.domain.model.UserStatus
import app.lazydex.domain.model.resolveMediaType
import app.lazydex.domain.model.toTopLevelId
import app.lazydex.domain.repository.MediaRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DexUiState(
    val items: List<MediaItem> = emptyList(),
    val totalCount: Int = 0,
    val selectedType: MediaType? = null,
    val selectedStatus: StatusFilter = StatusFilter.ALL,
    val topLevelTypes: List<TypeConfig> = emptyList(),
    val selectedTypeId: String? = null,
    val formatsForSelectedType: List<TypeConfig> = emptyList(),
    val selectedFormatId: String? = null,
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
    val perTypeCounts: Map<MediaType, Int> = emptyMap(),
    val perStatusCounts: Map<StatusFilter, Int> = emptyMap(),
    val displayMode: LibraryDisplayMode = LibraryDisplayMode.LIST,
    val gridColumns: Int = 0,
    val showTypeTabs: Boolean = true,
    val showProgressBadge: Boolean = true,
    val showStatusBadge: Boolean = true,
    val showScoreBadge: Boolean = true,
    val showItemCount: Boolean = false,
    val blurAdultCovers: Boolean = false,
    val groupMode: LibraryGroupMode = LibraryGroupMode.BY_STATUS,
    val selectedIds: Set<String> = emptySet(),
    val isSelectionMode: Boolean = selectedIds.isNotEmpty(),
    val isLoading: Boolean = true
)

private data class MutableFilterState(
    val mediaType: MediaType? = null,
    val status: StatusFilter = StatusFilter.ALL,
    val genres: Set<String> = emptySet(),
    val tags: Set<String> = emptySet()
)

private data class FilterBundle(
    val mediaType: MediaType?,
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
    val direction: SortDirection,
    val randomSeed: Long
)

private data class MetadataBundle(
    val genres: List<String>,
    val tags: List<String>,
    val typeCounts: Map<MediaType, Int>
)

@OptIn(ExperimentalCoroutinesApi::class)
class DexViewModel(
    private val repository: MediaRepository,
    private val themePreferences: ThemePreferences,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val syncEngine: TrackerSyncEngine? = null,
    private val coverCache: CoverCache? = null,
) : ViewModel() {

    @OptIn(FlowPreview::class)
    private fun <T> Flow<T>.throttleDuringSync(): Flow<T> {
        val engine = syncEngine ?: return this
        return combine(engine.syncProgress.map { it.isSyncing }.distinctUntilChanged()) { data, isSyncing ->
            data to isSyncing
        }.debounce { (_, isSyncing) ->
            if (isSyncing) 300L else 0L
        }.map { (data, _) -> data }
    }

    private val mutableFilterState = MutableStateFlow(MutableFilterState())
    val searchQuery = MutableStateFlow<String?>(null)
    val selectedIds = MutableStateFlow<Set<String>>(emptySet())
    private var lastSelectedId: String? = null

    fun setSearchQuery(query: String?) {
        searchQuery.value = query
    }

    @OptIn(FlowPreview::class)
    private val debouncedSearchQuery: Flow<String?> = searchQuery
        .debounce { if (it == null) 0L else 250L }
        .distinctUntilChanged()
        .flowOn(defaultDispatcher)

    private val selectedTypeId = MutableStateFlow<String?>(null)
    private val selectedFormatId = MutableStateFlow<String?>(null)

    private val typeCounts = repository.observeTypeCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val topLevelTypes: StateFlow<List<TypeConfig>> = combine(
        repository.observeTopLevelTypes(),
        typeCounts
    ) { types, counts ->
        types.filter { type ->
            val mediaType = when (type.id.uppercase()) {
                "MANGA" -> MediaType.MANGA
                "ANIME" -> MediaType.ANIME
                "GAME" -> MediaType.GAME
                else -> MediaType.NOVEL
            }
            (counts[mediaType] ?: 0) > 0
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val formatsForSelectedType: StateFlow<List<TypeConfig>> = selectedTypeId.flatMapLatest { typeId ->
        if (typeId != null) repository.observeFormats(typeId) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val sortField = themePreferences.sortField
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SortField.DATE_ADDED)
    private val sortDirection = themePreferences.sortDirection
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SortDirection.DESCENDING)
    private val randomSeed = MutableStateFlow(System.currentTimeMillis())

    private val authorQuery = MutableStateFlow("")
    private val minRating = MutableStateFlow<Double?>(null)
    private val maxRating = MutableStateFlow<Double?>(null)
    private val dateRangeStart = MutableStateFlow<Long?>(null)
    private val dateRangeEnd = MutableStateFlow<Long?>(null)

    private val tabStatusFilters = MutableStateFlow<Map<String, StatusFilter>>(emptyMap())

    private val selectedType: StateFlow<MediaType?> = selectedTypeId.map { id ->
        id?.let { resolveMediaType(it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val selectedStatus: StateFlow<StatusFilter> = combine(
        selectedTypeId,
        tabStatusFilters
    ) { typeId, filters ->
        val key = typeId ?: "ALL"
        filters[key] ?: StatusFilter.ALL
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatusFilter.ALL)

    private val availableGenres = selectedType.flatMapLatest { type ->
        repository.observeDistinctGenres(type)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val availableTags = selectedType.flatMapLatest { type ->
        repository.observeDistinctTags(type)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val activeTargetTypeId = combine(selectedTypeId, selectedFormatId) { typeId, formatId ->
        formatId ?: typeId
    }

    private val dbFiltered = repository.observeFilteredByTypeId(null, StatusFilter.ALL)
        .throttleDuringSync()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val perStatusCounts = selectedType.flatMapLatest { type ->
        repository.observeStatusCounts(type)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val filterState = combine(
        selectedType,
        selectedStatus,
        perStatusCounts
    ) { type, stat, sc ->
        FilterBundle(type, stat, emptySet(), emptySet(), sc)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FilterBundle(null, StatusFilter.ALL, emptySet(), emptySet(), emptyMap()))

    private val advancedFilterState = combine(
        authorQuery, minRating, maxRating, dateRangeStart, dateRangeEnd
    ) { aq, min, max, ds, de -> AdvancedFilterBundle(aq, min, max, ds, de) }

    private val metadataState = combine(
        availableGenres, availableTags, typeCounts
    ) { genres, tags, tc -> MetadataBundle(genres, tags, tc) }

    private val sortedItems: Flow<List<MediaItem>> = combine(
        dbFiltered,
        tabStatusFilters,
        selectedFormatId,
        mutableFilterState.map { it.genres to it.tags }.distinctUntilChanged(),
        advancedFilterState,
        debouncedSearchQuery,
        combine(sortField, sortDirection, randomSeed) { f, d, r -> SortBundle(f, d, r) }
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val items = args[0] as List<MediaItem>
        @Suppress("UNCHECKED_CAST")
        val statusFilters = args[1] as Map<String, StatusFilter>
        val selFormatId = args[2] as String?
        @Suppress("UNCHECKED_CAST")
        val genresAndTags = args[3] as Pair<Set<String>, Set<String>>
        val afs = args[4] as AdvancedFilterBundle
        val search = args[5] as String?
        val ss = args[6] as SortBundle

        val selectedGenres = genresAndTags.first
        val selectedTags = genresAndTags.second
        val q = search?.trim()
        val filtered = items.filter { item ->
            val itemTabId = item.mediaType.name.uppercase()
            val targetStatus = statusFilters[itemTabId] ?: StatusFilter.ALL
            val expectedUserStatus = targetStatus.toUserStatus()
            if (expectedUserStatus != null && item.userStatus != expectedUserStatus) return@filter false

            (selFormatId == null || item.format.equals(selFormatId, ignoreCase = true) || item.typeId.equals(selFormatId, ignoreCase = true)) &&
            (selectedGenres.isEmpty() || item.genres.any { it.lowercase() in selectedGenres }) &&
            (selectedTags.isEmpty() || item.tags.any { it.lowercase() in selectedTags }) &&
            (afs.authorQuery.isBlank() || item.author.contains(afs.authorQuery, ignoreCase = true)) &&
            (afs.minRating == null || (item.rating ?: 0.0) >= afs.minRating) &&
            (afs.maxRating == null || (item.rating ?: 0.0) <= afs.maxRating) &&
            (afs.dateRangeStart == null || (item.startDate != null && item.startDate >= afs.dateRangeStart)) &&
            (afs.dateRangeEnd == null || (item.startDate != null && item.startDate <= afs.dateRangeEnd)) &&
            (q.isNullOrEmpty() || (
                item.title.contains(q, ignoreCase = true) ||
                item.author.contains(q, ignoreCase = true) ||
                item.alternativeTitles.any { it.contains(q, ignoreCase = true) } ||
                item.getExtra("titleEnglish")?.contains(q, ignoreCase = true) == true ||
                item.getExtra("titleRomaji")?.contains(q, ignoreCase = true) == true
            ))
        }

        sortItems(filtered, ss.field, ss.direction, ss.randomSeed)
    }.flowOn(defaultDispatcher)

    val uiState: StateFlow<DexUiState> = combine(
        sortedItems,
        filterState,
        advancedFilterState,
        combine(sortField, sortDirection) { f, d -> f to d },
        metadataState,
        topLevelTypes,
        formatsForSelectedType,
        selectedTypeId,
        selectedFormatId
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val items = args[0] as List<MediaItem>
        val fs = args[1] as FilterBundle
        val afs = args[2] as AdvancedFilterBundle
        @Suppress("UNCHECKED_CAST")
        val sortPair = args[3] as Pair<SortField, SortDirection>
        val meta = args[4] as MetadataBundle
        @Suppress("UNCHECKED_CAST")
        val topTypes = args[5] as List<TypeConfig>
        @Suppress("UNCHECKED_CAST")
        val formatConfigs = args[6] as List<TypeConfig>
        val selTypeId = args[7] as String?
        val selFormatId = args[8] as String?

        DexUiState(
            items = items,
            selectedType = fs.mediaType,
            selectedStatus = fs.status,
            topLevelTypes = topTypes,
            selectedTypeId = selTypeId,
            formatsForSelectedType = formatConfigs,
            selectedFormatId = selFormatId,
            sortField = sortPair.first,
            sortDirection = sortPair.second,
            selectedGenres = fs.selectedGenres,
            availableGenres = meta.genres,
            selectedTags = fs.selectedTags,
            availableTags = meta.tags,
            authorQuery = afs.authorQuery,
            minRating = afs.minRating,
            maxRating = afs.maxRating,
            dateRangeStart = afs.dateRangeStart,
            dateRangeEnd = afs.dateRangeEnd,
            perTypeCounts = meta.typeCounts,
            perStatusCounts = fs.perStatusCounts,
            isLoading = false
        )
    }.flowOn(defaultDispatcher).combine(repository.observeCount()) { state, count -> state.copy(totalCount = count) }
    .combine(themePreferences.displayPreferences) { state, dp ->
        state.copy(
            displayMode = dp.displayMode,
            gridColumns = dp.gridColumns,
            showTypeTabs = dp.showTypeTabs,
            showProgressBadge = dp.showProgressBadge,
            showStatusBadge = dp.showStatusBadge,
            showScoreBadge = dp.showScoreBadge,
            showItemCount = dp.showItemCount,
            blurAdultCovers = dp.blurAdultCovers,
            groupMode = dp.groupMode
        )
    }
    .combine(selectedIds) { state, ids ->
        state.copy(
            selectedIds = ids,
            isSelectionMode = ids.isNotEmpty()
        )
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DexUiState())

    fun selectTypeId(typeId: String?) {
        selectedTypeId.value = typeId
        selectedFormatId.value = null
        val resolvedType = typeId?.let { resolveMediaType(it) }
        mutableFilterState.update { it.copy(mediaType = resolvedType, genres = emptySet(), tags = emptySet()) }
    }

    fun selectFormatId(formatId: String?) {
        selectedFormatId.value = formatId
    }

    fun incrementProgress(id: String, delta: Double = 1.0, mode: String? = null, speed: Double = 1.0) {
        viewModelScope.launch {
            repository.incrementProgress(id, delta, mode, speed)
        }
    }

    fun setProgress(id: String, progress: Double) {
        viewModelScope.launch {
            val item = repository.getById(id) ?: return@launch
            if (kotlin.math.abs(item.progress - progress) > 1e-6) {
                val delta = progress - item.progress
                repository.incrementProgress(id, delta)
            }
        }
    }

    fun updateProgressDetailed(
        id: String,
        newProgress: Int,
        newTotal: Int?,
        newVolume: Double,
        newTotalVolumes: Double?,
        newRepeat: Int
    ) {
        viewModelScope.launch {
            val item = repository.getById(id) ?: return@launch
            val updated = item.copy(
                currentProgress = newProgress,
                progress = newProgress.toDouble(),
                totalItems = newTotal,
                progressTotal = newTotal?.toDouble(),
                secondaryProgress = newVolume,
                secondaryProgressTotal = newTotalVolumes,
                repeat = newRepeat
            )
            repository.update(updated)
        }
    }

    fun setSecondaryProgress(id: String, secondaryProgress: Double) {
        viewModelScope.launch {
            val item = repository.getById(id) ?: return@launch
            if (kotlin.math.abs(item.secondaryProgress - secondaryProgress) > 1e-6) {
                repository.update(item.copy(secondaryProgress = secondaryProgress))
            }
        }
    }

    fun setShowItemCount(enabled: Boolean) {
        viewModelScope.launch {
            themePreferences.setShowItemCount(enabled)
        }
    }

    fun setDisplayMode(mode: LibraryDisplayMode) {
        viewModelScope.launch {
            themePreferences.setDisplayMode(mode)
        }
    }

    fun setGridColumns(columns: Int) {
        viewModelScope.launch {
            themePreferences.setGridColumns(columns)
        }
    }

    fun quickAdd(
        title: String,
        mediaType: MediaType,
        format: String = "",
        status: UserStatus = UserStatus.CURRENT,
        currentProgress: Int = 0,
        totalItems: Int? = null,
        coverImagePath: String = "",
        coverImageUrl: String? = null,
        onCreated: (MediaItem) -> Unit = {}
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val id = UUID.randomUUID().toString()
            var finalImagePath = coverImagePath.trim()

            if (finalImagePath.isNotBlank()) {
                val srcFile = File(finalImagePath)
                if (srcFile.exists()) {
                    coverCache?.let { cache ->
                        val destFile = cache.getCustomCoverFile(id)
                        try {
                            srcFile.copyTo(destFile, overwrite = true)
                            finalImagePath = destFile.absolutePath
                        } catch (_: Exception) {}
                    }
                }
            }

            val newItem = MediaItem(
                id = id,
                title = title.trim(),
                type = mediaType.name,
                format = format.trim(),
                userStatus = status,
                currentProgress = currentProgress,
                progress = currentProgress.toDouble(),
                totalItems = totalItems,
                progressTotal = totalItems?.toDouble(),
                coverImagePath = finalImagePath,
                coverImageUrl = coverImageUrl?.trim()?.ifBlank { null },
                lastUpdated = now,
                dateAdded = now,
                progressIncreasedAt = if (status == UserStatus.CURRENT) now else null
            )
            val added = repository.add(newItem)
            onCreated(added)
        }
    }

    fun setShowTypeTabs(enabled: Boolean) {
        viewModelScope.launch {
            themePreferences.setShowTypeTabs(enabled)
        }
    }

    fun setShowProgressBadge(enabled: Boolean) {
        viewModelScope.launch {
            themePreferences.setShowProgressBadge(enabled)
        }
    }

    fun setShowStatusBadge(enabled: Boolean) {
        viewModelScope.launch {
            themePreferences.setShowStatusBadge(enabled)
        }
    }

    fun setShowScoreBadge(enabled: Boolean) {
        viewModelScope.launch {
            themePreferences.setShowScoreBadge(enabled)
        }
    }

    fun selectType(mediaType: MediaType?) {
        selectTypeId(mediaType?.toTopLevelId())
    }

    fun selectStatus(status: StatusFilter) {
        val currentTab = selectedTypeId.value ?: "ALL"
        tabStatusFilters.update { current ->
            val existing = current[currentTab] ?: StatusFilter.ALL
            val newStatus = if (existing == status) StatusFilter.ALL else status
            current + (currentTab to newStatus)
        }
    }

    fun toggleSelection(id: String) {
        selectedIds.update { current ->
            if (id in current) current - id else current + id
        }
        lastSelectedId = id
    }

    fun toggleRangeSelection(id: String) {
        val currentItems = getItemsForType(uiState.value.items, selectedTypeId.value)
        val lastId = lastSelectedId
        if (lastId == null || currentItems.none { it.id == lastId }) {
            toggleSelection(id)
            return
        }

        val lastIndex = currentItems.indexOfFirst { it.id == lastId }
        val currentIndex = currentItems.indexOfFirst { it.id == id }

        if (lastIndex == -1 || currentIndex == -1) {
            toggleSelection(id)
            return
        }

        val range = if (lastIndex <= currentIndex) lastIndex..currentIndex else currentIndex..lastIndex
        val rangeIds = range.map { currentItems[it].id }.toSet()

        selectedIds.update { it + rangeIds }
        lastSelectedId = id
    }

    fun selectAll() {
        val currentTabItems = getItemsForType(uiState.value.items, selectedTypeId.value)
        val allIds = currentTabItems.map { it.id }.toSet()
        selectedIds.value = allIds
    }

    fun invertSelection() {
        val currentTabItems = getItemsForType(uiState.value.items, selectedTypeId.value)
        val tabIds = currentTabItems.map { it.id }.toSet()
        selectedIds.update { current ->
            tabIds - current
        }
    }

    fun clearSelection() {
        selectedIds.value = emptySet()
        lastSelectedId = null
    }

    fun updateStatusForSelected(status: UserStatus) {
        val idsToUpdate = selectedIds.value
        if (idsToUpdate.isEmpty()) return
        clearSelection()
        viewModelScope.launch {
            repository.updateStatus(idsToUpdate, status)
        }
    }

    fun deleteSelected() {
        val idsToDelete = selectedIds.value
        if (idsToDelete.isEmpty()) return
        clearSelection()
        viewModelScope.launch {
            repository.deleteItems(idsToDelete)
        }
    }

    fun setGroupMode(mode: LibraryGroupMode) {
        viewModelScope.launch {
            themePreferences.setGroupMode(mode)
        }
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
        if (field == SortField.RANDOM) {
            randomSeed.value = System.currentTimeMillis()
        }
        viewModelScope.launch {
            themePreferences.setSortField(field)
        }
    }

    fun selectSortDirection(direction: SortDirection) {
        viewModelScope.launch {
            themePreferences.setSortDirection(direction)
        }
    }

    fun clearFilters() {
        val currentType = selectedTypeId.value?.let { resolveMediaType(it) }
        mutableFilterState.update { it.copy(mediaType = currentType, genres = emptySet(), tags = emptySet()) }
        tabStatusFilters.value = emptyMap()
        selectedFormatId.value = null
        authorQuery.value = ""
        minRating.value = null
        maxRating.value = null
        dateRangeStart.value = null
        dateRangeEnd.value = null
        clearSelection()
    }

    private fun sortItems(
        items: List<MediaItem>,
        field: SortField,
        direction: SortDirection,
        randomSeed: Long
    ): List<MediaItem> {
        if (field == SortField.RANDOM) {
            return items.shuffled(kotlin.random.Random(randomSeed))
        }

        val comparator: Comparator<MediaItem> = when (field) {
            SortField.ALPHABETICAL -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            SortField.TOTAL_COUNT -> compareBy { it.totalItems ?: 0 }
            SortField.LAST_READ -> compareBy<MediaItem> { it.progressIncreasedAt != null }
                .thenBy { it.progressIncreasedAt ?: it.dateAdded }
            SortField.UNREAD_COUNT -> compareBy { (it.totalItems ?: 0) - it.currentProgress }
            SortField.CURRENT_PROGRESS -> compareBy { it.currentProgress }
            SortField.DATE_ADDED -> compareBy { it.dateAdded }
            SortField.RATING -> compareBy { it.rating ?: 0.0 }
            SortField.RANDOM -> return items
        }

        return if (direction == SortDirection.DESCENDING) {
            items.sortedWith(comparator.reversed())
        } else {
            items.sortedWith(comparator)
        }
    }

    companion object {
        fun getItemsForType(items: List<MediaItem>, typeId: String?): List<MediaItem> {
            if (typeId == null) return items
            val targetType = resolveMediaType(typeId)
            return items.filter { item ->
                item.type.equals(typeId, ignoreCase = true) ||
                item.typeId.equals(typeId, ignoreCase = true) ||
                item.mediaType == targetType
            }
        }
    }
}
