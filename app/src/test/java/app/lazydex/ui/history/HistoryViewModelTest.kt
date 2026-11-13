package app.lazydex.ui.history

import app.lazydex.data.local.ThemePreferences
import app.lazydex.domain.model.MediaItem
import app.lazydex.domain.model.MediaStats
import app.lazydex.domain.model.MediaType
import app.lazydex.domain.model.StatusFilter
import app.lazydex.domain.model.UserStatus
import app.lazydex.domain.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class HistoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepo: FakeMediaRepository
    private lateinit var fakePrefs: FakeThemePreferences
    private lateinit var viewModel: HistoryViewModel

    private class FakeThemePreferences : ThemePreferences(null) {
        val blurFlow = MutableStateFlow(false)
        override val blurAdultCovers: Flow<Boolean> = blurFlow
    }

    private class FakeMediaRepository : MediaRepository {
        val historyFlow = MutableStateFlow<List<MediaItem>>(emptyList())

        override fun observeHistory(): Flow<List<MediaItem>> = historyFlow
        override fun observeAll(): Flow<List<MediaItem>> = flowOf(emptyList())
        override fun observeByType(mediaType: MediaType): Flow<List<MediaItem>> = flowOf(emptyList())
        override fun observeFiltered(mediaType: MediaType?, statusFilter: StatusFilter): Flow<List<MediaItem>> = flowOf(emptyList())
        override fun observeById(id: String): Flow<MediaItem?> = flowOf(null)
        override fun observeCount(): Flow<Int> = flowOf(0)
        override fun observeStats(): Flow<MediaStats> = flowOf(MediaStats(0, 0, 0, null, 0, 0, 0, 0, 0))
        override fun observeTypeCounts(): Flow<Map<MediaType, Int>> = flowOf(emptyMap())
        override fun observeStatusCounts(mediaType: MediaType?): Flow<Map<StatusFilter, Int>> = flowOf(emptyMap())
        override fun observeDistinctGenres(mediaType: MediaType?): Flow<List<String>> = flowOf(emptyList())
        override fun observeDistinctTags(mediaType: MediaType?): Flow<List<String>> = flowOf(emptyList())
        override suspend fun getById(id: String): MediaItem? = null
        override suspend fun getAll(): List<MediaItem> = emptyList()
        override suspend fun add(item: MediaItem): MediaItem = item
        override suspend fun update(item: MediaItem) {}
        override suspend fun delete(id: String) {}
        override suspend fun replaceAll(items: List<MediaItem>) {}
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeMediaRepository()
        fakePrefs = FakeThemePreferences()
        viewModel = HistoryViewModel(fakeRepo, fakePrefs)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `emits history items and filters by title and author`() = runTest(testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        val item1 = MediaItem(
            id = "1",
            title = "Solo Leveling",
            author = "Chugong",
            userStatus = UserStatus.CURRENT,
            lastUpdated = 2000L,
            dateAdded = 1000L,
            progressIncreasedAt = 2000L
        )
        val item2 = MediaItem(
            id = "2",
            title = "Omniscient Reader",
            author = "Sing Shong",
            userStatus = UserStatus.CURRENT,
            lastUpdated = 3000L,
            dateAdded = 1000L,
            progressIncreasedAt = 3000L
        )
        fakeRepo.historyFlow.value = listOf(item2, item1)
        advanceUntilIdle()

        var state = viewModel.uiState.value
        assertEquals(2, state.items.size)
        assertEquals("Omniscient Reader", state.items[0].title)

        // Filter by title
        viewModel.onSearchQueryChange("solo")
        advanceUntilIdle()
        state = viewModel.uiState.value
        assertEquals(1, state.items.size)
        assertEquals("Solo Leveling", state.items[0].title)

        // Filter by author
        viewModel.onSearchQueryChange("Sing")
        advanceUntilIdle()
        state = viewModel.uiState.value
        assertEquals(1, state.items.size)
        assertEquals("Omniscient Reader", state.items[0].title)

        // Clear search
        viewModel.clearSearchQuery()
        advanceUntilIdle()
        state = viewModel.uiState.value
        assertEquals(2, state.items.size)
    }

    @Test
    fun `emits blurAdultCovers state change when preference updates`() = runTest(testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.blurAdultCovers)

        fakePrefs.blurFlow.value = true
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.blurAdultCovers)

        fakePrefs.blurFlow.value = false
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.blurAdultCovers)
    }

    @Test
    fun `onSearchingToggle manages search active flag and clears query on close`() = runTest(testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.onSearchingToggle(true)
        viewModel.onSearchQueryChange("frieren")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSearching)
        assertEquals("frieren", viewModel.uiState.value.searchQuery)

        viewModel.onSearchingToggle(false)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSearching)
        assertEquals("", viewModel.uiState.value.searchQuery)
    }
}
