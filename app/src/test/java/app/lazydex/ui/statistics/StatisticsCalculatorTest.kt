package app.lazydex.ui.statistics

import app.lazydex.domain.model.ActivityDatePoint
import app.lazydex.domain.model.MediaType
import app.lazydex.domain.model.MediaItem
import app.lazydex.domain.model.UserStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class StatisticsCalculatorTest {

    private val dhakaZone = ZoneId.of("Asia/Dhaka") // UTC+6, non-UTC timezone test
    private val nyZone = ZoneId.of("America/New_York")

    @Test
    fun `resolveDateRange handles leap year Feb 29 safely for 1 year preset`() {
        val leapDay = LocalDate.of(2024, 2, 29)
        val resolved = StatisticsCalculator.resolveDateRange(
            preset = StatsDateRangePreset.LAST_1_YEAR,
            today = leapDay,
            zoneId = dhakaZone
        )

        assertEquals(LocalDate.of(2023, 2, 28), resolved.startDate)
        assertEquals(LocalDate.of(2024, 2, 29), resolved.endDate)
        assertEquals("Last 1y", resolved.label)
    }

    @Test
    fun `resolveDateRange custom converts UTC picker millis cleanly even in negative timezone`() {
        val startUtcMs = LocalDate.of(2026, 3, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val endUtcMs = LocalDate.of(2026, 3, 15).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        val startLocal = StatisticsCalculator.pickerUtcMillisToLocalDate(startUtcMs)
        val endLocal = StatisticsCalculator.pickerUtcMillisToLocalDate(endUtcMs)
        val resolved = StatisticsCalculator.resolveDateRange(
            preset = StatsDateRangePreset.CUSTOM,
            customStartDate = startLocal,
            customEndDate = endLocal,
            today = LocalDate.of(2026, 3, 20),
            zoneId = nyZone
        )

        assertEquals(LocalDate.of(2026, 3, 1), resolved.startDate)
        assertEquals(LocalDate.of(2026, 3, 15), resolved.endDate)
        assertEquals("Mar 1 – Mar 15", resolved.label)
    }

    @Test
    fun `filterItemsByRange enforces strict Yamtrack overlap and excludes undated items in bounded mode`() {
        val range = StatisticsCalculator.resolveDateRange(
            preset = StatsDateRangePreset.LAST_7_DAYS,
            today = LocalDate.of(2026, 5, 10),
            zoneId = ZoneOffset.UTC
        )
        val startEpoch = { d: LocalDate -> d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }

        val undated = MediaItem(id = "undated", title = "Undated", mediaType = MediaType.NOVEL, userStatus = UserStatus.CURRENT, startDate = null, endDate = null)
        val outside = MediaItem(id = "outside", title = "Old", mediaType = MediaType.NOVEL, userStatus = UserStatus.COMPLETED, startDate = startEpoch(LocalDate.of(2026, 4, 1)), endDate = startEpoch(LocalDate.of(2026, 5, 3)))
        val overlap = MediaItem(id = "overlap", title = "Span", mediaType = MediaType.MANGA, userStatus = UserStatus.CURRENT, startDate = startEpoch(LocalDate.of(2026, 4, 20)), endDate = startEpoch(LocalDate.of(2026, 5, 5)))
        val startOnlyInside = MediaItem(id = "startOnly", title = "Started", mediaType = MediaType.ANIME, userStatus = UserStatus.CURRENT, startDate = startEpoch(LocalDate.of(2026, 5, 8)), endDate = null)
        val startOnlyBefore = MediaItem(id = "startOld", title = "Old Start", mediaType = MediaType.ANIME, userStatus = UserStatus.CURRENT, startDate = startEpoch(LocalDate.of(2026, 5, 1)), endDate = null)

        val filteredAll = StatisticsCalculator.filterItemsByRange(listOf(undated, outside, overlap, startOnlyInside, startOnlyBefore), null, null)
        assertEquals(5, filteredAll.size)

        val filteredBounded = StatisticsCalculator.filterItemsByRange(
            listOf(undated, outside, overlap, startOnlyInside, startOnlyBefore),
            range.startMs,
            range.endMs
        )
        assertEquals(listOf("overlap", "startOnly"), filteredBounded.map { it.id })
    }

    @Test
    fun `computeConsumptionStats splits per-type units and distinguishes anime movies without dividing by 60`() {
        val items = listOf(
            MediaItem(id = "n1", type = "NOVEL", format = "NOVEL", title = "Novel 1", userStatus = UserStatus.CURRENT, currentProgress = 120, progress = 120.0, lastUpdated = 1L, dateAdded = 1L),
            MediaItem(id = "m1", type = "MANGA", format = "MANGA", title = "Manga 1", userStatus = UserStatus.CURRENT, currentProgress = 45, progress = 45.0, lastUpdated = 1L, dateAdded = 1L),
            MediaItem(id = "a1", type = "ANIME", format = "TV", title = "Anime 1", userStatus = UserStatus.CURRENT, currentProgress = 24, progress = 24.0, lastUpdated = 1L, dateAdded = 1L),
            MediaItem(id = "a2", type = "ANIME", format = "MOVIE", title = "Anime Movie", userStatus = UserStatus.COMPLETED, currentProgress = 1, progress = 1.0, lastUpdated = 1L, dateAdded = 1L),
            MediaItem(id = "g1", type = "GAME", format = "GAME", title = "Game 1", userStatus = UserStatus.CURRENT, currentProgress = 35, progress = 35.5, lastUpdated = 1L, dateAdded = 1L)
        )

        val stats = StatisticsCalculator.computeConsumptionStats(items).associateBy { it.key }
        assertEquals("120", stats["novel_chapters"]?.valueText)
        assertEquals("45", stats["manga_chapters"]?.valueText)
        assertEquals("24", stats["anime_episodes"]?.valueText)
        assertEquals("1", stats["anime_movies"]?.valueText)
        assertEquals("35.5", stats["game_hours"]?.valueText)
        assertNull(stats["movies_watched"])
        assertNull(stats["tv_episodes"])
    }

    @Test
    fun `computeActivityHeatmap respects timezone day boundaries and strict anchorDate today streak`() {
        val today = LocalDate.of(2026, 5, 10)
        // Two timestamps straddling 23:59:59 -> 00:00:01 in Asia/Dhaka (+06:00)
        val day9Late = LocalDateTime.of(2026, 5, 9, 23, 59, 59).atZone(dhakaZone).toInstant().toEpochMilli()
        val day10Early = LocalDateTime.of(2026, 5, 10, 0, 0, 1).atZone(dhakaZone).toInstant().toEpochMilli()
        val day8 = LocalDateTime.of(2026, 5, 8, 12, 0, 0).atZone(dhakaZone).toInstant().toEpochMilli()
        // Ignored STATUS_CHANGE on day 7
        val day7Status = LocalDateTime.of(2026, 5, 7, 12, 0, 0).atZone(dhakaZone).toInstant().toEpochMilli()

        val points = listOf(
            ActivityDatePoint("1", "NOVEL", "NOVEL", "PROGRESS_UPDATE", 1.0, day8),
            ActivityDatePoint("1", "NOVEL", "NOVEL", "PROGRESS_UPDATE", 1.0, day9Late),
            ActivityDatePoint("1", "NOVEL", "NOVEL", "PROGRESS_UPDATE", 1.0, day10Early),
            ActivityDatePoint("1", "NOVEL", "NOVEL", "STATUS_CHANGE", null, day7Status)
        )

        val activeTodayState = StatisticsCalculator.computeActivityHeatmap(
            points = points,
            selectedType = null,
            rangeStartDate = today.minusDays(6),
            rangeEndDate = today,
            today = today,
            zoneId = dhakaZone
        )
        assertTrue(activeTodayState.hasActivityData)
        assertEquals(3, activeTodayState.totalActivities)
        assertEquals(3, activeTodayState.currentStreak)
        assertEquals(3, activeTodayState.longestStreak)

        // Anchor on May 11 where May 11 has 0 events -> currentStreak resets strictly to 0, longest stays 3
        val inactiveTodayState = StatisticsCalculator.computeActivityHeatmap(
            points = points,
            selectedType = null,
            rangeStartDate = today.minusDays(6),
            rangeEndDate = LocalDate.of(2026, 5, 11),
            today = LocalDate.of(2026, 5, 11),
            zoneId = dhakaZone
        )
        assertEquals(0, inactiveTodayState.currentStreak)
        assertEquals(3, inactiveTodayState.longestStreak)
    }

    @Test
    fun `computeActivityHeatmap intensity levels match Yamtrack scale and empty state`() {
        val empty = StatisticsCalculator.computeActivityHeatmap(emptyList(), null, null, null)
        assertFalse(empty.hasActivityData)

        val today = LocalDate.of(2026, 5, 10)
        val ts = { d: LocalDate -> LocalDateTime.of(d, java.time.LocalTime.NOON).atZone(ZoneOffset.UTC).toInstant().toEpochMilli() }
        val points = buildList {
            // 1 event on day-3 -> level 1
            add(ActivityDatePoint("1", "MANGA", "MANGA", "PROGRESS_UPDATE", 1.0, ts(today.minusDays(3))))
            // 4 events on day-2 -> level 2
            repeat(4) { add(ActivityDatePoint("1", "MANGA", "MANGA", "PROGRESS_UPDATE", 1.0, ts(today.minusDays(2)))) }
            // 7 events on day-1 -> level 3
            repeat(7) { add(ActivityDatePoint("1", "MANGA", "MANGA", "PROGRESS_UPDATE", 1.0, ts(today.minusDays(1)))) }
            // 10 events on today -> level 4
            repeat(10) { add(ActivityDatePoint("1", "MANGA", "MANGA", "PROGRESS_UPDATE", 1.0, ts(today))) }
        }

        val heatmap = StatisticsCalculator.computeActivityHeatmap(
            points = points,
            selectedType = null,
            rangeStartDate = today.minusDays(6),
            rangeEndDate = today,
            today = today,
            zoneId = ZoneOffset.UTC
        )
        val dayMap = heatmap.weeks.flatten().filterNotNull().associateBy { LocalDate.ofEpochDay(it.epochDay) }
        assertEquals(0, dayMap[today.minusDays(4)]?.level)
        assertEquals(1, dayMap[today.minusDays(3)]?.level)
        assertEquals(2, dayMap[today.minusDays(2)]?.level)
        assertEquals(3, dayMap[today.minusDays(1)]?.level)
        assertEquals(4, dayMap[today]?.level)
    }

    @Test
    fun `computeStackedRatings bins fractional ratings into 5 buckets with type slices on All tab`() {
        val items = listOf(
            MediaItem(id = "1", title = "T1", mediaType = MediaType.NOVEL, userStatus = UserStatus.COMPLETED, rating = 5.0),
            MediaItem(id = "2", title = "T2", mediaType = MediaType.MANGA, userStatus = UserStatus.COMPLETED, rating = 4.5),
            MediaItem(id = "3", title = "T3", mediaType = MediaType.ANIME, userStatus = UserStatus.COMPLETED, rating = 4.0),
            MediaItem(id = "4", title = "T4", mediaType = MediaType.GAME, userStatus = UserStatus.COMPLETED, rating = 2.7),
            MediaItem(id = "5", title = "T5", mediaType = MediaType.NOVEL, userStatus = UserStatus.COMPLETED, rating = 1.6),
            MediaItem(id = "6", title = "T6", mediaType = MediaType.MANGA, userStatus = UserStatus.COMPLETED, rating = 0.8),
            MediaItem(id = "7", title = "T7", mediaType = MediaType.ANIME, userStatus = UserStatus.CURRENT, rating = null)
        )

        val buckets = StatisticsCalculator.computeStackedRatings(items, selectedType = null)
        assertEquals(5, buckets.size)
        assertEquals(listOf("4.5–5★", "3.5–4★", "2.5–3★", "1.5–2★", "0.5–1★"), buckets.map { it.bucketLabel })
        assertEquals(listOf(2, 1, 1, 1, 1), buckets.map { it.totalCount })
        assertEquals(1.0f, buckets[0].barFraction, 0.001f)
        assertEquals(0.5f, buckets[1].barFraction, 0.001f)
        assertEquals(listOf(MediaType.NOVEL, MediaType.MANGA), buckets[0].typeSlices.map { it.mediaType })

        val novelItems = items.filter { it.mediaType == MediaType.NOVEL }
        val singleTypeBuckets = StatisticsCalculator.computeStackedRatings(novelItems, selectedType = MediaType.NOVEL)
        assertTrue(singleTypeBuckets.all { it.typeSlices.isEmpty() })
        assertEquals(listOf(1, 0, 0, 1, 0), singleTypeBuckets.map { it.totalCount })
    }

    @Test
    fun `computeLoggedMinutes follows AniList minutesWatched, Novel 2m per chapter, Manga 3m per chapter, and repeat`() {
        val novel = MediaItem(
            id = "n1",
            mediaType = MediaType.NOVEL,
            title = "Novel",
            currentProgress = 100,
            totalItems = 200,
            userStatus = UserStatus.COMPLETED,
            repeat = 1 // 100 + 1*200 = 300 ch -> 600 min
        )
        val manga = MediaItem(
            id = "m1",
            mediaType = MediaType.MANGA,
            title = "Manga",
            currentProgress = 20,
            userStatus = UserStatus.CURRENT // 20 ch * 3m = 60 min
        )
        val animeTvSynced = MediaItem(
            id = "a1",
            type = "ANIME",
            format = "TV",
            title = "TV",
            currentProgress = 12,
            totalItems = 12,
            userStatus = UserStatus.COMPLETED,
            lastUpdated = 1L,
            dateAdded = 1L,
            repeat = 1, // 12 + 12 = 24 ep * 24m = 576 min
            durationMinutes = 24
        )
        val animeShort = MediaItem(
            id = "a2",
            type = "ANIME",
            format = "TV_SHORT",
            title = "Short",
            currentProgress = 10,
            userStatus = UserStatus.COMPLETED,
            lastUpdated = 1L,
            dateAdded = 1L,
            durationMinutes = 5 // 10 * 5m = 50 min
        )
        val animeUnlinked = MediaItem(
            id = "a3",
            type = "ANIME",
            format = "OVA",
            title = "Unlinked OVA",
            currentProgress = 2,
            userStatus = UserStatus.COMPLETED,
            lastUpdated = 1L,
            dateAdded = 1L,
            durationMinutes = null // 2 * 24m = 48 min
        )
        val animeMovieSynced = MediaItem(
            id = "a4",
            type = "ANIME",
            format = "MOVIE",
            title = "Synced Movie",
            currentProgress = 1,
            userStatus = UserStatus.COMPLETED,
            lastUpdated = 1L,
            dateAdded = 1L,
            durationMinutes = 110 // 1 * 110m = 110 min
        )
        val animeMovieManualMinutes = MediaItem(
            id = "a5",
            type = "ANIME",
            format = "MOVIE",
            title = "Manual Movie",
            currentProgress = 120,
            userStatus = UserStatus.COMPLETED,
            lastUpdated = 1L,
            dateAdded = 1L,
            durationMinutes = null // >= 20 -> 120m raw
        )
        val game = MediaItem(
            id = "g1",
            type = "GAME",
            format = "PC",
            title = "Game",
            currentProgress = 2,
            progress = 2.5,
            userStatus = UserStatus.CURRENT,
            lastUpdated = 1L,
            dateAdded = 1L // 2.5 * 60 = 150 min
        )

        assertEquals(600.0, StatisticsCalculator.computeLoggedMinutes(listOf(novel)), 0.001)
        assertEquals(60.0, StatisticsCalculator.computeLoggedMinutes(listOf(manga)), 0.001)
        assertEquals(576.0, StatisticsCalculator.computeLoggedMinutes(listOf(animeTvSynced)), 0.001)
        assertEquals(50.0, StatisticsCalculator.computeLoggedMinutes(listOf(animeShort)), 0.001)
        assertEquals(48.0, StatisticsCalculator.computeLoggedMinutes(listOf(animeUnlinked)), 0.001)
        assertEquals(110.0, StatisticsCalculator.computeLoggedMinutes(listOf(animeMovieSynced)), 0.001)
        assertEquals(120.0, StatisticsCalculator.computeLoggedMinutes(listOf(animeMovieManualMinutes)), 0.001)
        assertEquals(150.0, StatisticsCalculator.computeLoggedMinutes(listOf(game)), 0.001)
        assertEquals(1714.0, StatisticsCalculator.computeLoggedMinutes(listOf(novel, manga, animeTvSynced, animeShort, animeUnlinked, animeMovieSynced, animeMovieManualMinutes, game)), 0.001)
    }

    @Test
    fun `formatLoggedMinutes formats exact days hours minutes cleanly`() {
        assertEquals("—", StatisticsCalculator.formatLoggedMinutes(0.0))
        assertEquals("< 1m", StatisticsCalculator.formatLoggedMinutes(0.4))
        assertEquals("45m", StatisticsCalculator.formatLoggedMinutes(45.0))
        assertEquals("8h", StatisticsCalculator.formatLoggedMinutes(480.0))
        assertEquals("3h 20m", StatisticsCalculator.formatLoggedMinutes(200.0))
        assertEquals("1d 1h", StatisticsCalculator.formatLoggedMinutes(1500.0))
        assertEquals("2d", StatisticsCalculator.formatLoggedMinutes(2880.0))
    }

    @Test
    fun `computeActivityHeatmap preserves Dec 1 on late-Nov week 0 and adds year suffixes on multi-year span`() {
        val today = LocalDate.of(2026, 2, 10)
        val ts = { d: LocalDate -> LocalDateTime.of(d, java.time.LocalTime.NOON).atZone(ZoneOffset.UTC).toInstant().toEpochMilli() }
        val points = listOf(
            ActivityDatePoint("1", "NOVEL", "NOVEL", "PROGRESS_UPDATE", 1.0, ts(LocalDate.of(2025, 11, 28))),
            ActivityDatePoint("1", "NOVEL", "NOVEL", "PROGRESS_UPDATE", 1.0, ts(LocalDate.of(2026, 2, 10)))
        )

        val heatmap = StatisticsCalculator.computeActivityHeatmap(
            points = points,
            selectedType = null,
            rangeStartDate = LocalDate.of(2025, 11, 28), // Friday; Dec 1 falls in week 0 (next week Mon? Dec 1 2025 is Mon, wait: Nov 28 2025 is Fri -> week 0 ends Sun Nov 30! Try Nov 27 2024: Nov 25 is Mon -> Dec 1 2024 is Sun inside week 0!)
            rangeEndDate = today,
            today = today,
            zoneId = ZoneOffset.UTC
        )
        assertTrue(heatmap.monthSpans.any { it.label.startsWith("Jan '26") })

        // Verify week-0 boundary where Dec 1 falls inside week 0 itself (Nov 27 2024..Feb 1 2025)
        val nov2024Points = listOf(
            ActivityDatePoint("1", "NOVEL", "NOVEL", "PROGRESS_UPDATE", 1.0, ts(LocalDate.of(2024, 11, 27)))
        )
        val boundaryHeatmap = StatisticsCalculator.computeActivityHeatmap(
            points = nov2024Points,
            selectedType = null,
            rangeStartDate = LocalDate.of(2024, 11, 27), // Wednesday -> Dec 1 2024 is Sunday of week 0
            rangeEndDate = LocalDate.of(2025, 2, 1),
            today = LocalDate.of(2025, 2, 1),
            zoneId = ZoneOffset.UTC
        )
        assertTrue("Dec 1 in week 0 must not be swallowed", boundaryHeatmap.monthSpans.any { it.label.startsWith("Dec '24") })
        assertTrue(boundaryHeatmap.monthSpans.any { it.label == "Jan '25" })
    }
}
