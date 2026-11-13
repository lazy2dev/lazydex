package app.lazydex.ui.dex

import app.lazydex.data.local.DisplayPreferences
import app.lazydex.data.local.LibraryGroupMode
import app.lazydex.data.local.ThemePreferences
import app.lazydex.domain.model.TypeConfig
import app.lazydex.domain.model.MediaType
import app.lazydex.domain.model.MediaItem
import app.lazydex.domain.model.MediaStats
import app.lazydex.domain.model.SortDirection
import app.lazydex.domain.model.SortField
import app.lazydex.domain.model.StatusFilter
import app.lazydex.domain.model.UserStatus
import app.lazydex.domain.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DexViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeThemePreferences : ThemePreferences(null) {
        val sortFieldFlow = MutableStateFlow(SortField.DATE_ADDED)
        val sortDirectionFlow = MutableStateFlow(SortDirection.DESCENDING)
        val displayPrefsFlow = MutableStateFlow(DisplayPreferences())
        override val sortField: Flow<SortField> = sortFieldFlow
        override val sortDirection: Flow<SortDirection> = sortDirectionFlow
        override val displayPreferences: Flow<DisplayPreferences> = displayPrefsFlow

        override suspend fun setSortField(field: SortField) {
            sortFieldFlow.value = field
        }

        override suspend fun setSortDirection(direction: SortDirection) {
            sortDirectionFlow.value = direction
        }

        override suspend fun setGroupMode(mode: LibraryGroupMode) {
            displayPrefsFlow.value = displayPrefsFlow.value.copy(groupMode = mode)
        }
    }

    private class FakeMediaRepository(
        val itemsFlow: MutableStateFlow<List<MediaItem>> = MutableStateFlow(emptyList())
    ) : MediaRepository {
        override fun observeFilteredByTypeId(typeId: String?, statusFilter: StatusFilter): Flow<List<MediaItem>> = itemsFlow
        override fun observeAll(): Flow<List<MediaItem>> = itemsFlow
        override fun observeHistory(): Flow<List<MediaItem>> = itemsFlow
        override fun observeByType(mediaType: MediaType): Flow<List<MediaItem>> = itemsFlow
        override fun observeFiltered(mediaType: MediaType?, statusFilter: StatusFilter): Flow<List<MediaItem>> = itemsFlow
        override fun observeById(id: String): Flow<MediaItem?> = flowOf(itemsFlow.value.find { it.id == id })
        override fun observeCount(): Flow<Int> = flowOf(itemsFlow.value.size)
        override fun observeStats(): Flow<MediaStats> = flowOf(MediaStats(0, 0, 0, null, 0, 0, 0, 0, 0))
        override fun observeTypeCounts(): Flow<Map<MediaType, Int>> = flowOf(emptyMap())
        override fun observeStatusCounts(mediaType: MediaType?): Flow<Map<StatusFilter, Int>> = flowOf(emptyMap())
        override fun observeDistinctGenres(mediaType: MediaType?): Flow<List<String>> = flowOf(emptyList())
        override fun observeDistinctTags(mediaType: MediaType?): Flow<List<String>> = flowOf(emptyList())
        override fun observeTopLevelTypes(): Flow<List<TypeConfig>> = flowOf(emptyList())
        override fun observeFormats(parentId: String): Flow<List<TypeConfig>> = flowOf(emptyList())

        override suspend fun getById(id: String): MediaItem? = itemsFlow.value.find { it.id == id }
        override suspend fun getAll(): List<MediaItem> = itemsFlow.value
        override suspend fun add(item: MediaItem): MediaItem = item
        override suspend fun update(item: MediaItem) {}
        override suspend fun delete(id: String) {}
        override suspend fun replaceAll(items: List<MediaItem>) {}
        override suspend fun updateStatus(ids: Collection<String>, status: UserStatus) {
            itemsFlow.value = itemsFlow.value.map { item ->
                if (item.id in ids) item.copy(userStatus = status) else item
            }
        }
        override suspend fun deleteItems(ids: Collection<String>) {
            itemsFlow.value = itemsFlow.value.filterNot { it.id in ids }
        }
    }

    private lateinit var repository: FakeMediaRepository
    private lateinit var themePreferences: FakeThemePreferences
    private lateinit var viewModel: DexViewModel

    private val testItems = listOf(
        MediaItem(
            id = "1",
            title = "Solo Leveling",
            author = "Chugong",
            alternativeTitles = listOf("Only I Level Up"),
            userStatus = UserStatus.CURRENT,
            lastUpdated = 1000L,
            dateAdded = 1000L
        ),
        MediaItem(
            id = "2",
            title = "Omniscient Reader",
            author = "Sing Shong",
            alternativeTitles = listOf("ORV"),
            userStatus = UserStatus.PLANNING,
            lastUpdated = 2000L,
            dateAdded = 2000L
        ),
        MediaItem(
            id = "3",
            title = "The Beginning After The End",
            author = "TurtleMe",
            alternativeTitles = listOf("TBATE"),
            userStatus = UserStatus.COMPLETED,
            lastUpdated = 3000L,
            dateAdded = 3000L
        )
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeMediaRepository(MutableStateFlow(testItems))
        themePreferences = FakeThemePreferences()
        viewModel = DexViewModel(repository, themePreferences, testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `empty query returns all items`() = runTest(testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.items.size)

        viewModel.setSearchQuery("")
        advanceTimeBy(300)
        advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.items.size)

        viewModel.setSearchQuery(null)
        advanceTimeBy(300)
        advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.items.size)
    }

    @Test
    fun `query matching title filters correctly`() = runTest(testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setSearchQuery("Solo")
        advanceTimeBy(300)
        advanceUntilIdle()

        val items = viewModel.uiState.value.items
        assertEquals(1, items.size)
        assertEquals("Solo Leveling", items[0].title)
    }

    @Test
    fun `query matching author filters correctly`() = runTest(testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setSearchQuery("TurtleMe")
        advanceTimeBy(300)
        advanceUntilIdle()

        val items = viewModel.uiState.value.items
        assertEquals(1, items.size)
        assertEquals("The Beginning After The End", items[0].title)
    }

    @Test
    fun `whitespace trimming and case-insensitive matching`() = runTest(testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setSearchQuery("   sInG sHoNg   ")
        advanceTimeBy(300)
        advanceUntilIdle()

        val items = viewModel.uiState.value.items
        assertEquals(1, items.size)
        assertEquals("Omniscient Reader", items[0].title)
    }

    @Test
    fun `query matching alternative title or titleRomaji filters correctly`() = runTest(testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setSearchQuery("ORV")
        advanceTimeBy(300)
        advanceUntilIdle()

        val items = viewModel.uiState.value.items
        assertEquals(1, items.size)
        assertEquals("Omniscient Reader", items[0].title)
    }

    @Test
    fun `default display mode is LIST and selectTypeId resolves manhwa type`() = runTest(testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(app.lazydex.data.local.LibraryDisplayMode.LIST, viewModel.uiState.value.displayMode)

        viewModel.selectTypeId("typ_manhwa")
        advanceUntilIdle()
        assertEquals(MediaType.MANGA, viewModel.uiState.value.selectedType)
    }

    @Test
    fun `alphabetical sorting sorts case-insensitively ascending and descending`() = runTest(testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.selectSortField(SortField.ALPHABETICAL)
        viewModel.selectSortDirection(SortDirection.ASCENDING)
        advanceUntilIdle()

        val ascItems = viewModel.uiState.value.items
        assertEquals("Omniscient Reader", ascItems[0].title)
        assertEquals("Solo Leveling", ascItems[1].title)
        assertEquals("The Beginning After The End", ascItems[2].title)

        viewModel.selectSortDirection(SortDirection.DESCENDING)
        advanceUntilIdle()

        val descItems = viewModel.uiState.value.items
        assertEquals("The Beginning After The End", descItems[0].title)
        assertEquals("Solo Leveling", descItems[1].title)
        assertEquals("Omniscient Reader", descItems[2].title)
    }

    @Test
    fun `toggleSelection enters selection mode and toggles item id`() = runTest(testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSelectionMode)
        assertTrue(viewModel.uiState.value.selectedIds.isEmpty())

        viewModel.toggleSelection("1")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isSelectionMode)
        assertEquals(setOf("1"), viewModel.uiState.value.selectedIds)

        viewModel.toggleSelection("2")
        advanceUntilIdle()
        assertEquals(setOf("1", "2"), viewModel.uiState.value.selectedIds)

        viewModel.toggleSelection("1")
        viewModel.toggleSelection("2")
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSelectionMode)
        assertTrue(viewModel.uiState.value.selectedIds.isEmpty())
    }

    @Test
    fun `selectAll selects all items in current list and invertSelection flips selection`() = runTest(testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.toggleSelection("1")
        advanceUntilIdle()
        assertEquals(setOf("1"), viewModel.uiState.value.selectedIds)

        viewModel.selectAll()
        advanceUntilIdle()
        assertEquals(setOf("1", "2", "3"), viewModel.uiState.value.selectedIds)

        viewModel.invertSelection()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.selectedIds.isEmpty())
        assertFalse(viewModel.uiState.value.isSelectionMode)

        viewModel.toggleSelection("1")
        advanceUntilIdle()
        viewModel.invertSelection()
        advanceUntilIdle()
        assertEquals(setOf("2", "3"), viewModel.uiState.value.selectedIds)
    }

    @Test
    fun `toggleRangeSelection selects range between last selected and target`() = runTest(testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.toggleSelection("1")
        advanceUntilIdle()
        viewModel.toggleRangeSelection("3")
        advanceUntilIdle()

        assertEquals(setOf("1", "2", "3"), viewModel.uiState.value.selectedIds)
    }

    @Test
    fun `updateStatusForSelected updates repository and clears selection`() = runTest(testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.toggleSelection("1")
        viewModel.toggleSelection("2")
        advanceUntilIdle()

        viewModel.updateStatusForSelected(UserStatus.COMPLETED)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSelectionMode)
        assertTrue(viewModel.uiState.value.selectedIds.isEmpty())

        val updatedItems = repository.itemsFlow.value
        assertEquals(UserStatus.COMPLETED, updatedItems.find { it.id == "1" }?.userStatus)
        assertEquals(UserStatus.COMPLETED, updatedItems.find { it.id == "2" }?.userStatus)
    }

    @Test
    fun `selectStatus re-clicking active status toggles back to ALL`() = runTest(testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(StatusFilter.ALL, viewModel.uiState.value.selectedStatus)

        viewModel.selectStatus(StatusFilter.CURRENT)
        advanceUntilIdle()
        assertEquals(StatusFilter.CURRENT, viewModel.uiState.value.selectedStatus)

        // Clicking same status again toggles back to ALL
        viewModel.selectStatus(StatusFilter.CURRENT)
        advanceUntilIdle()
        assertEquals(StatusFilter.ALL, viewModel.uiState.value.selectedStatus)
    }

    @Test
    fun `setGroupMode updates preferences and uiState`() = runTest(testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(LibraryGroupMode.BY_DEFAULT, viewModel.uiState.value.groupMode)

        viewModel.setGroupMode(LibraryGroupMode.BY_STATUS)
        advanceUntilIdle()
        assertEquals(LibraryGroupMode.BY_STATUS, viewModel.uiState.value.groupMode)
    }
}

