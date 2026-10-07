package app.lazydex.ui.statistics

import app.lazydex.domain.model.MediaCategory
import app.lazydex.domain.model.MediaItem
import app.lazydex.domain.model.MediaStats
import app.lazydex.domain.model.StatusFilter
import app.lazydex.domain.model.UserStatus
import app.lazydex.domain.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private class FakeRepository(
        val items: List<MediaItem>
    ) : MediaRepository {
        override fun observeAll(): Flow<List<MediaItem>> = flowOf(items)
        override fun observeByCategory(category: MediaCategory): Flow<List<MediaItem>> =
            flowOf(items.filter { it.category == category })
        override fun observeFiltered(category: MediaCategory?, statusFilter: StatusFilter): Flow<List<MediaItem>> =
            flowOf(items)
        override fun observeById(id: String): Flow<MediaItem?> = flowOf(items.find { it.id == id })
        override fun observeCount(): Flow<Int> = flowOf(items.size)
        override fun observeStats(): Flow<MediaStats> = flowOf(MediaStats(0, 0, 0, null, 0, 0, 0, 0, 0))
        override fun observeCategoryCounts(): Flow<Map<MediaCategory, Int>> = flowOf(
            MediaCategory.entries.associateWith { cat -> items.count { it.category == cat } }
        )
        override fun observeStatusCounts(category: MediaCategory?): Flow<Map<StatusFilter, Int>> = flowOf(emptyMap())
        override fun observeDistinctGenres(category: MediaCategory?): Flow<List<String>> = flowOf(emptyList())
        override fun observeDistinctTags(category: MediaCategory?): Flow<List<String>> = flowOf(emptyList())

        override suspend fun getById(id: String): MediaItem? = items.find { it.id == id }
        override suspend fun getAll(): List<MediaItem> = items
        override suspend fun add(item: MediaItem): MediaItem = item
        override suspend fun update(item: MediaItem) {}
        override suspend fun delete(id: String) {}
        override suspend fun replaceAll(items: List<MediaItem>) {}
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun sampleMediaItems(): List<MediaItem> {
        return listOf(
            MediaItem(
                id = "1",
                category = MediaCategory.NOVEL,
                title = "Lord of the Mysteries",
                currentProgress = 100,
                totalItems = 1432,
                userStatus = UserStatus.COMPLETED,
                rating = 5.0,
                genres = listOf("Fantasy", "Action"),
                tags = listOf("Magic", "Isekai"),
                startDate = 1000L,
                endDate = 1000L + (86400000L * 2) + (3600000L * 5),
                lastUpdated = 1000L,
                dateAdded = 1000L
            ),
            MediaItem(
                id = "2",
                category = MediaCategory.NOVEL,
                title = "Shadow Slave",
                currentProgress = 50,
                totalItems = 1000,
                userStatus = UserStatus.READING,
                rating = 4.0,
                genres = listOf("Fantasy"),
                tags = listOf("Magic"),
                lastUpdated = 2000L,
                dateAdded = 2000L
            ),
            MediaItem(
                id = "3",
                category = MediaCategory.MANGA,
                title = "Solo Leveling",
                currentProgress = 20,
                totalItems = 200,
                userStatus = UserStatus.COMPLETED,
                rating = 3.0,
                genres = listOf("Action"),
                tags = listOf("Martial Arts"),
                lastUpdated = 3000L,
                dateAdded = 3000L
            ),
            MediaItem(
                id = "4",
                category = MediaCategory.ANIME,
                title = "Steins;Gate",
                currentProgress = 5,
                totalItems = 24,
                userStatus = UserStatus.DROPPED,
                rating = null,
                genres = listOf("Sci-Fi"),
                tags = listOf("Time Travel"),
                lastUpdated = 4000L,
                dateAdded = 4000L
            )
        )
    }

    @Test
    fun testInitialStateAggregatesAllItems() = runTest {
        val repo = FakeRepository(sampleMediaItems())
        val viewModel = StatisticsViewModel(repo)

        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        val state = viewModel.uiState.value
        assertEquals(4, state.overview.totalCount)
        assertEquals(2, state.overview.completedCount)
        assertEquals(1, state.overview.inProgressCount)
        assertEquals(175, state.overview.totalProgressUnits)
        assertEquals("2d 5h", state.overview.durationText)
        assertEquals("Duration", state.overview.durationLabel)
        assertEquals(4.0, state.overview.meanRating!!, 0.001)

        // Tab counts
        assertEquals(4, state.categoryCounts[null])
        assertEquals(2, state.categoryCounts[MediaCategory.NOVEL])
        assertEquals(1, state.categoryCounts[MediaCategory.MANGA])
        assertEquals(1, state.categoryCounts[MediaCategory.ANIME])

        // Genres
        val fantasyGenre = state.genreBars.find { it.name == "Fantasy" }
        assertEquals(2, fantasyGenre?.count)
        assertEquals(50.0f, fantasyGenre?.percentage!!, 0.01f)

        // Tags
        val magicTag = state.tagBars.find { it.name == "Magic" }
        assertEquals(2, magicTag?.count)
        assertEquals(50.0f, magicTag?.percentage!!, 0.01f)

        collectJob.cancel()
    }

    @Test
    fun testCategorySelectionFiltersToSpecificCategory() = runTest {
        val repo = FakeRepository(sampleMediaItems())
        val viewModel = StatisticsViewModel(repo)

        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.selectCategory(MediaCategory.NOVEL)

        val state = viewModel.uiState.value
        assertEquals(MediaCategory.NOVEL, state.selectedCategory)
        assertEquals(2, state.overview.totalCount)
        assertEquals(1, state.overview.completedCount)
        assertEquals(1, state.overview.inProgressCount)
        assertEquals("Reading", state.overview.inProgressLabel)
        assertEquals("Plan to Read", state.overview.planToLabel)
        assertEquals("Rereading", state.overview.rereadingLabel)
        assertEquals("Read duration", state.overview.durationLabel)
        assertEquals(150, state.overview.totalProgressUnits)
        assertEquals("Chapters read", state.overview.progressUnitLabel)

        // Novel genres only
        assertEquals(2, state.genreBars.size)
        val fantasy = state.genreBars.find { it.name == "Fantasy" }
        assertEquals(2, fantasy?.count)

        collectJob.cancel()
    }

    @Test
    fun testRatingsAndStatusMetricsCalculatedCorrectly() = runTest {
        val repo = FakeRepository(sampleMediaItems())
        val viewModel = StatisticsViewModel(repo)

        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        val state = viewModel.uiState.value
        // 3 items have rating, 1 does not
        assertEquals(3, state.overview.ratedCount)
        assertEquals(1, state.overview.unratedCount)
        assertEquals(4.0, state.overview.meanRating!!, 0.001)

        // 1 inProgress, 0 planTo, 0 onHold, 1 dropped
        assertEquals(1, state.overview.inProgressCount)
        assertEquals(0, state.overview.planToCount)
        assertEquals(0, state.overview.onHoldCount)
        assertEquals(1, state.overview.droppedCount)

        collectJob.cancel()
    }

    @Test
    fun testEmptyLibrarySafeZeroDivision() = runTest {
        val repo = FakeRepository(emptyList())
        val viewModel = StatisticsViewModel(repo)

        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        val state = viewModel.uiState.value
        assertEquals(0, state.overview.totalCount)
        assertEquals(0, state.overview.completedCount)
        assertEquals("—", state.overview.durationText)
        assertEquals(0, state.overview.ratedCount)
        assertEquals(0, state.overview.unratedCount)
        assertNull(state.overview.meanRating)
        assertTrue(state.genreBars.isEmpty())
        assertTrue(state.tagBars.isEmpty())

        collectJob.cancel()
    }
}
