package app.lazydex.ui.dex

import app.lazydex.data.local.DisplayPreferences
import app.lazydex.data.local.ThemePreferences
import app.lazydex.domain.model.Category
import app.lazydex.domain.model.MediaCategory
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DexViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeThemePreferences : ThemePreferences(null) {
        override val sortField: Flow<SortField> = flowOf(SortField.DATE_ADDED)
        override val sortDirection: Flow<SortDirection> = flowOf(SortDirection.DESCENDING)
        override val displayPreferences: Flow<DisplayPreferences> = flowOf(DisplayPreferences())
    }

    private class FakeMediaRepository(
        val itemsFlow: MutableStateFlow<List<MediaItem>> = MutableStateFlow(emptyList())
    ) : MediaRepository {
        override fun observeFilteredByCategoryId(categoryId: String?, statusFilter: StatusFilter): Flow<List<MediaItem>> = itemsFlow
        override fun observeAll(): Flow<List<MediaItem>> = itemsFlow
        override fun observeByCategory(category: MediaCategory): Flow<List<MediaItem>> = itemsFlow
        override fun observeFiltered(category: MediaCategory?, statusFilter: StatusFilter): Flow<List<MediaItem>> = itemsFlow
        override fun observeById(id: String): Flow<MediaItem?> = flowOf(itemsFlow.value.find { it.id == id })
        override fun observeCount(): Flow<Int> = flowOf(itemsFlow.value.size)
        override fun observeStats(): Flow<MediaStats> = flowOf(MediaStats(0, 0, 0, null, 0, 0, 0, 0, 0))
        override fun observeCategoryCounts(): Flow<Map<MediaCategory, Int>> = flowOf(emptyMap())
        override fun observeStatusCounts(category: MediaCategory?): Flow<Map<StatusFilter, Int>> = flowOf(emptyMap())
        override fun observeDistinctGenres(category: MediaCategory?): Flow<List<String>> = flowOf(emptyList())
        override fun observeDistinctTags(category: MediaCategory?): Flow<List<String>> = flowOf(emptyList())
        override fun observeTopLevelCategories(): Flow<List<Category>> = flowOf(emptyList())
        override fun observeTypes(parentId: String): Flow<List<Category>> = flowOf(emptyList())

        override suspend fun getById(id: String): MediaItem? = itemsFlow.value.find { it.id == id }
        override suspend fun getAll(): List<MediaItem> = itemsFlow.value
        override suspend fun add(item: MediaItem): MediaItem = item
        override suspend fun update(item: MediaItem) {}
        override suspend fun delete(id: String) {}
        override suspend fun replaceAll(items: List<MediaItem>) {}
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
            userStatus = UserStatus.READING,
            lastUpdated = 1000L,
            dateAdded = 1000L
        ),
        MediaItem(
            id = "2",
            title = "Omniscient Reader",
            author = "Sing Shong",
            alternativeTitles = listOf("ORV"),
            userStatus = UserStatus.PLAN_TO,
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
}

