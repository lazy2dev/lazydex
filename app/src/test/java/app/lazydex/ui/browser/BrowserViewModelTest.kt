package app.lazydex.ui.browser

import app.lazydex.data.local.ThemePreferences
import app.lazydex.domain.model.MediaCategory
import app.lazydex.domain.model.MediaItem
import app.lazydex.domain.model.MediaStats
import app.lazydex.domain.model.StatusFilter
import app.lazydex.domain.model.UserStatus
import app.lazydex.domain.repository.MediaRepository
import app.lazydex.scraper.source.ScrapedMetadata
import app.lazydex.scraper.source.SearchResult
import app.lazydex.scraper.source.Source
import app.lazydex.scraper.source.SourceRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeSource(
        override val id: String,
        override val name: String,
        override val baseUrl: String,
        override val urlPattern: Regex,
        override val isSearchable: Boolean,
        override val categoryHint: MediaCategory?,
        var resultsToReturn: List<SearchResult> = emptyList()
    ) : Source {
        override suspend fun scrape(url: String): ScrapedMetadata? = null
        override suspend fun search(query: String): List<SearchResult> = resultsToReturn
    }

    private class FakeThemePreferences : ThemePreferences(null) {
        val pinnedFlow = MutableStateFlow(setOf("manga1", "novel1"))
        override val pinnedSources: Flow<Set<String>> = pinnedFlow

        override suspend fun togglePinnedSource(sourceId: String) {
            val cur = pinnedFlow.value
            pinnedFlow.value = if (cur.contains(sourceId)) cur - sourceId else cur + sourceId
        }
    }

    private class FakeMediaRepository : MediaRepository {
        val itemsFlow = MutableStateFlow<List<MediaItem>>(emptyList())
        override fun observeAll(): Flow<List<MediaItem>> = itemsFlow
        override fun observeByCategory(category: MediaCategory): Flow<List<MediaItem>> = flowOf(emptyList())
        override fun observeFiltered(category: MediaCategory?, statusFilter: StatusFilter): Flow<List<MediaItem>> = flowOf(emptyList())
        override fun observeById(id: String): Flow<MediaItem?> = flowOf(null)
        override fun observeCount(): Flow<Int> = flowOf(0)
        override fun observeStats(): Flow<MediaStats> = flowOf(MediaStats(0, 0, 0, null, 0, 0, 0, 0, 0))
        override fun observeCategoryCounts(): Flow<Map<MediaCategory, Int>> = flowOf(emptyMap())
        override fun observeStatusCounts(category: MediaCategory?): Flow<Map<StatusFilter, Int>> = flowOf(emptyMap())
        override fun observeDistinctGenres(category: MediaCategory?): Flow<List<String>> = flowOf(emptyList())
        override fun observeDistinctTags(category: MediaCategory?): Flow<List<String>> = flowOf(emptyList())
        override suspend fun getById(id: String): MediaItem? = null
        override suspend fun getAll(): List<MediaItem> = emptyList()
        override suspend fun add(item: MediaItem): MediaItem = item
        override suspend fun update(item: MediaItem) {}
        override suspend fun delete(id: String) {}
        override suspend fun replaceAll(items: List<MediaItem>) {}
    }

    private lateinit var source1: FakeSource
    private lateinit var source2: FakeSource
    private lateinit var fakeThemePreferences: FakeThemePreferences
    private lateinit var fakeMediaRepository: FakeMediaRepository
    private lateinit var viewModel: BrowserViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        source1 = FakeSource(
            id = "manga1",
            name = "Manga Source",
            baseUrl = "https://manga.example.com",
            urlPattern = Regex("""https://manga\.example\.com/title/\d+"""),
            isSearchable = true,
            categoryHint = MediaCategory.MANGA,
            resultsToReturn = listOf(
                SearchResult("One Piece", "https://manga.example.com/title/123", "https://img.example.com/1.jpg")
            )
        )
        source2 = FakeSource(
            id = "novel1",
            name = "Novel Source",
            baseUrl = "https://novel.example.com",
            urlPattern = Regex("""https://novel\.example\.com/novel/\d+"""),
            isSearchable = true,
            categoryHint = MediaCategory.NOVEL,
            resultsToReturn = listOf(
                SearchResult("Shadow Slave", "https://novel.example.com/novel/456", "https://img.example.com/2.jpg")
            )
        )

        val registry = SourceRegistry(listOf(source1, source2))
        fakeThemePreferences = FakeThemePreferences()
        fakeMediaRepository = FakeMediaRepository()

        viewModel = BrowserViewModel(registry, fakeThemePreferences, fakeMediaRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialState() = runTest {
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(2, state.sources.size)
        assertTrue(state.pinnedSourceIds.contains("manga1"))
        assertTrue(state.pinnedSourceIds.contains("novel1"))
    }

    @Test
    fun testTogglePin() = runTest {
        advanceUntilIdle()
        viewModel.togglePin("manga1")
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.pinnedSourceIds.contains("manga1"))

        viewModel.togglePin("manga1")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.pinnedSourceIds.contains("manga1"))
    }

    @Test
    fun testUrlDetection() = runTest {
        viewModel.updateSearchQuery("https://manga.example.com/title/123")
        assertNotNull(viewModel.uiState.value.detectedSourceForUrl)
        assertEquals("manga1", viewModel.uiState.value.detectedSourceForUrl?.id)

        viewModel.updateSearchQuery("Solo Leveling")
        assertNull(viewModel.uiState.value.detectedSourceForUrl)
    }

    @Test
    fun testPerformSearchSingleSource() = runTest {
        viewModel.selectSource(source1)
        viewModel.updateSearchQuery("One")
        viewModel.performSearch()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.searchResults.size)
        assertEquals("One Piece", state.searchResults.first().title)
        assertTrue(state.hasSearched)
        assertFalse(state.isSearching)
    }

    @Test
    fun testPerformSearchGlobalFilteredByCategory() = runTest {
        viewModel.setCategory(MediaCategory.NOVEL)
        viewModel.updateSearchQuery("Shadow")
        viewModel.performSearch()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.searchResults.size)
        assertEquals("Shadow Slave", state.searchResults.first().title)
        assertTrue(state.hasSearched)
    }

    @Test
    fun testTrackedUrlsDetection() = runTest {
        advanceUntilIdle()
        fakeMediaRepository.itemsFlow.value = listOf(
            MediaItem(
                id = "id-1",
                category = MediaCategory.MANGA,
                title = "One Piece",
                sourceUrl = "https://manga.example.com/title/123",
                currentProgress = 100,
                totalItems = 1100,
                userStatus = UserStatus.READING,
                lastUpdated = 1000L,
                dateAdded = 1000L
            )
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val result = SearchResult("One Piece", "https://manga.example.com/title/123", null)
        assertTrue(state.isTracked(result))

        val untrackedResult = SearchResult("Untracked", "https://manga.example.com/title/999", null)
        assertFalse(state.isTracked(untrackedResult))
    }

    @Test
    fun testEmptySearchFallback() = runTest {
        source1.resultsToReturn = emptyList()
        source2.resultsToReturn = emptyList()

        viewModel.updateSearchQuery("NonExistent")
        viewModel.performSearch()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.hasSearched)
        assertTrue(state.searchResults.isEmpty())
        assertFalse(state.isSearching)
    }
}

