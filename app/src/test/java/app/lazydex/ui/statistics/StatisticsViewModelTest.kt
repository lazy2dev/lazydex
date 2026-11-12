package app.lazydex.ui.statistics

import app.lazydex.data.local.DisplayPreferences
import app.lazydex.data.local.ThemePreferences
import app.lazydex.domain.model.ActivityDatePoint
import app.lazydex.domain.model.MediaType
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private class FakeRepository(
        val items: List<MediaItem>,
        val activityPoints: List<ActivityDatePoint> = emptyList()
    ) : MediaRepository {
        override fun observeAll(): Flow<List<MediaItem>> = flowOf(items)
        override fun observeByType(mediaType: MediaType): Flow<List<MediaItem>> =
            flowOf(items.filter { it.mediaType == mediaType })
        override fun observeFiltered(mediaType: MediaType?, statusFilter: StatusFilter): Flow<List<MediaItem>> =
            flowOf(items)
        override fun observeById(id: String): Flow<MediaItem?> = flowOf(items.find { it.id == id })
        override fun observeCount(): Flow<Int> = flowOf(items.size)
        override fun observeStats(): Flow<MediaStats> = flowOf(MediaStats(0, 0, 0, null, 0, 0, 0, 0, 0))
        override fun observeTypeCounts(): Flow<Map<MediaType, Int>> = flowOf(
            MediaType.entries.associateWith { type -> items.count { it.mediaType == type } }
        )
        override fun observeStatusCounts(mediaType: MediaType?): Flow<Map<StatusFilter, Int>> = flowOf(emptyMap())
        override fun observeDistinctGenres(mediaType: MediaType?): Flow<List<String>> = flowOf(emptyList())
        override fun observeDistinctTags(mediaType: MediaType?): Flow<List<String>> = flowOf(emptyList())
        override fun observeActivityPoints(startMs: Long?, endMs: Long?): Flow<List<ActivityDatePoint>> = flowOf(
            activityPoints.filter { p ->
                (startMs == null || p.timestamp >= startMs) &&
                    (endMs == null || p.timestamp <= endMs)
            }
        )

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
                mediaType = MediaType.NOVEL,
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
                mediaType = MediaType.NOVEL,
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
                mediaType = MediaType.MANGA,
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
                mediaType = MediaType.ANIME,
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
        val viewModel = StatisticsViewModel(repo, computeDispatcher = testDispatcher)

        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        val state = viewModel.uiState.value
        assertEquals(4, state.overview.totalCount)
        assertEquals(2, state.overview.completedCount)
        assertEquals(1, state.overview.inProgressCount)
        assertEquals(175, state.overview.totalProgressUnits)
        assertEquals("8h", state.overview.durationText)
        assertEquals("Duration", state.overview.durationLabel)
        assertEquals(4.0, state.overview.meanRating!!, 0.001)

        // Tab counts
        assertEquals(4, state.typeCounts[null])
        assertEquals(2, state.typeCounts[MediaType.NOVEL])
        assertEquals(1, state.typeCounts[MediaType.MANGA])
        assertEquals(1, state.typeCounts[MediaType.ANIME])

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
    fun testTypeSelectionFiltersToSpecificType() = runTest {
        val repo = FakeRepository(sampleMediaItems())
        val viewModel = StatisticsViewModel(repo, computeDispatcher = testDispatcher)

        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.selectType(MediaType.NOVEL)

        val state = viewModel.uiState.value
        assertEquals(MediaType.NOVEL, state.selectedType)
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
        val viewModel = StatisticsViewModel(repo, computeDispatcher = testDispatcher)

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

        // Status breakdown verification
        val breakdown = state.statusBreakdown
        assertEquals(3, breakdown.size) // inProgress (1), completed (2), dropped (1)
        assertEquals(1, breakdown[0].count)
        assertEquals(25.0f, breakdown[0].percentage, 0.01f)
        assertEquals(2, breakdown[1].count)
        assertEquals(50.0f, breakdown[1].percentage, 0.01f)
        assertEquals(1, breakdown[2].count)
        assertEquals(25.0f, breakdown[2].percentage, 0.01f)

        collectJob.cancel()
    }

    @Test
    fun testEmptyLibrarySafeZeroDivision() = runTest {
        val repo = FakeRepository(emptyList())
        val viewModel = StatisticsViewModel(repo, computeDispatcher = testDispatcher)

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

    @Test
    fun testDurationComputationFollowsAniListAndNovelMangaFormula() = runTest {
        val novelItem = MediaItem(
            id = "novel1",
            mediaType = MediaType.NOVEL,
            title = "Long Novel",
            currentProgress = 100,
            totalItems = 100,
            userStatus = UserStatus.COMPLETED,
            repeat = 1 // 100 + 100 = 200 ch * 2m = 400m -> 6h 40m
        )
        val animeItem = MediaItem(
            id = "anime1",
            type = "ANIME",
            format = "TV",
            title = "Long Anime",
            currentProgress = 50,
            totalItems = 50,
            userStatus = UserStatus.COMPLETED,
            lastUpdated = 1000L,
            dateAdded = 1000L,
            durationMinutes = 30 // 50 * 30m = 1500m -> 1d 1h
        )

        val novelRepo = FakeRepository(listOf(novelItem))
        val novelVm = StatisticsViewModel(novelRepo, computeDispatcher = testDispatcher)
        val job1 = launch(UnconfinedTestDispatcher(testScheduler)) { novelVm.uiState.collect {} }
        assertEquals("6h 40m", novelVm.uiState.value.overview.durationText)
        job1.cancel()

        val animeRepo = FakeRepository(listOf(animeItem))
        val animeVm = StatisticsViewModel(animeRepo, computeDispatcher = testDispatcher)
        val job2 = launch(UnconfinedTestDispatcher(testScheduler)) { animeVm.uiState.collect {} }
        assertEquals("1d 1h", animeVm.uiState.value.overview.durationText)
        job2.cancel()
    }

    @Test
    fun testDateRangeFilteringAndActivityHeatmapIntegration() = runTest {
        val fixedToday = LocalDate.of(2026, 5, 10)
        val ts = { d: LocalDate -> d.atStartOfDay(ZoneOffset.UTC).plusHours(12).toInstant().toEpochMilli() }

        val datedRecent = MediaItem(
            id = "recent",
            mediaType = MediaType.NOVEL,
            title = "Recent Novel",
            currentProgress = 80,
            userStatus = UserStatus.READING,
            rating = 4.8,
            startDate = ts(fixedToday.minusDays(2)),
            endDate = ts(fixedToday)
        )
        val datedOld = MediaItem(
            id = "old",
            mediaType = MediaType.MANGA,
            title = "Old Manga",
            currentProgress = 40,
            userStatus = UserStatus.COMPLETED,
            rating = 3.5,
            startDate = ts(fixedToday.minusDays(40)),
            endDate = ts(fixedToday.minusDays(20))
        )
        val undated = MediaItem(
            id = "undated",
            mediaType = MediaType.GAME,
            title = "Undated Game",
            currentProgress = 12,
            userStatus = UserStatus.PLAYING,
            rating = 4.0
        )

        val points = listOf(
            ActivityDatePoint("recent", "NOVEL", "NOVEL", "PROGRESS_UPDATE", 2.0, ts(fixedToday)),
            ActivityDatePoint("recent", "NOVEL", "NOVEL", "PROGRESS_UPDATE", 1.0, ts(fixedToday.minusDays(1))),
            ActivityDatePoint("old", "MANGA", "MANGA", "PROGRESS_UPDATE", 5.0, ts(fixedToday.minusDays(25)))
        )

        val repo = FakeRepository(listOf(datedRecent, datedOld, undated), points)
        val vm = StatisticsViewModel(
            repository = repo,
            computeDispatcher = testDispatcher,
            todayProvider = { fixedToday },
            zoneId = ZoneOffset.UTC
        )
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        // Default: All Time includes all 3 entries + 3 activities + 2d streak
        val allState = vm.uiState.value
        assertEquals(StatsDateRangePreset.ALL_TIME, allState.dateRange.preset)
        assertEquals(3, allState.overview.totalCount)
        assertEquals(3, allState.consumptionStats.size)
        assertEquals(3, allState.activityHeatmap.totalActivities)
        assertEquals(2, allState.activityHeatmap.currentStreak)
        assertTrue(allState.typeStatusStacks.size >= 2)

        // Bounded preset: Last 7d excludes undated and old items
        vm.selectDateRangePreset(StatsDateRangePreset.LAST_7_DAYS)
        val weekState = vm.uiState.value
        assertEquals(StatsDateRangePreset.LAST_7_DAYS, weekState.dateRange.preset)
        assertEquals(1, weekState.overview.totalCount)
        assertEquals(1, weekState.typeCounts[MediaType.NOVEL])
        assertEquals(0, weekState.typeCounts[MediaType.MANGA])
        assertEquals(2, weekState.activityHeatmap.totalActivities)
        assertEquals(2, weekState.activityHeatmap.currentStreak)

        job.cancel()
    }

    @Test
    fun testTopRatedRespectsLimitAndBlurAdultCovers() = runTest {
        val fakeThemePrefs = object : ThemePreferences(null) {
            override val displayPreferences: Flow<DisplayPreferences> = flowOf(
                DisplayPreferences(blurAdultCovers = true)
            )
        }
        val items = (1..15).map { idx: Int ->
            MediaItem(
                id = "id-$idx",
                mediaType = MediaType.MANGA,
                title = "Title $idx",
                userStatus = UserStatus.COMPLETED,
                rating = 3.0 + (idx * 0.1),
                isAdult = idx == 15
            )
        }
        val vm = StatisticsViewModel(
            repository = FakeRepository(items),
            themePreferences = fakeThemePrefs,
            computeDispatcher = testDispatcher
        )
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        val topRated = vm.uiState.value.topRatedItems
        assertEquals(12, topRated.size)
        assertEquals("id-15", topRated.first().id)
        assertTrue(topRated.first().isObscured)
        assertFalse(topRated[1].isObscured)

        job.cancel()
    }
}
