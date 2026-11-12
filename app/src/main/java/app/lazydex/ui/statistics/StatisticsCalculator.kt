package app.lazydex.ui.statistics

import app.lazydex.domain.model.ActivityDatePoint
import app.lazydex.domain.model.MediaType
import app.lazydex.domain.model.MediaItem
import app.lazydex.domain.model.StatusFilter
import app.lazydex.domain.model.UserStatus
import app.lazydex.domain.model.resolveMediaType
import app.lazydex.domain.model.statusLabel
import app.lazydex.ui.components.color
import app.lazydex.ui.theme.getRatingColor
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt

data class ResolvedDateRange(
    val preset: StatsDateRangePreset,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val startMs: Long?,
    val endMs: Long?,
    val label: String
) {
    fun toUiState(): DateRangeUiState = DateRangeUiState(
        preset = preset,
        startMs = startMs,
        endMs = endMs,
        displayLabel = label
    )
}

object StatisticsCalculator {

    private const val NOVEL_MINUTES_PER_CHAPTER = 2.0
    private const val MANGA_MINUTES_PER_CHAPTER = 3.0
    private const val ANIME_DEFAULT_EPISODE_MINUTES = 24.0
    private const val ANIME_MOVIE_DEFAULT_MINUTES = 90.0
    private const val ANIME_MOVIE_RAW_MINUTE_THRESHOLD = 20.0
    private const val MINUTES_PER_HOUR = 60.0

    private data class RatingBucketSpec(
        val label: String,
        val representativeScore: Double,
        val matches: (Double) -> Boolean
    )

    private val ratingBuckets = listOf(
        RatingBucketSpec("4.5–5★", 4.75) { it >= 4.5 },
        RatingBucketSpec("3.5–4★", 3.75) { it >= 3.5 && it < 4.5 },
        RatingBucketSpec("2.5–3★", 2.75) { it >= 2.5 && it < 3.5 },
        RatingBucketSpec("1.5–2★", 1.75) { it >= 1.5 && it < 2.5 },
        RatingBucketSpec("0.5–1★", 1.0) { it < 1.5 }
    )

    private val sameYearDateFmt = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
    private val crossYearDateFmt = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
    private val tooltipDateFmt = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)

    fun pickerUtcMillisToLocalDate(utcMillis: Long): LocalDate =
        Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()

    fun resolveDateRange(
        preset: StatsDateRangePreset,
        customStartDate: LocalDate? = null,
        customEndDate: LocalDate? = null,
        today: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ResolvedDateRange {
        if (preset == StatsDateRangePreset.ALL_TIME) {
            return ResolvedDateRange(
                preset = preset,
                startDate = null,
                endDate = null,
                startMs = null,
                endMs = null,
                label = preset.shortLabel
            )
        }

        val (rawStart, rawEnd) = when (preset) {
            StatsDateRangePreset.LAST_7_DAYS -> today.minusDays(6) to today
            StatsDateRangePreset.LAST_30_DAYS -> today.minusDays(29) to today
            StatsDateRangePreset.LAST_1_YEAR -> today.minusYears(1) to today
            StatsDateRangePreset.CUSTOM -> {
                val start = customStartDate ?: customEndDate ?: today
                val end = customEndDate ?: customStartDate ?: today
                if (start <= end) start to end else end to start
            }
            StatsDateRangePreset.ALL_TIME -> null to null
        }

        if (rawStart == null || rawEnd == null) {
            return ResolvedDateRange(StatsDateRangePreset.ALL_TIME, null, null, null, null, StatsDateRangePreset.ALL_TIME.shortLabel)
        }

        val startMs = rawStart.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endMs = rawEnd.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1L
        val label = if (preset == StatsDateRangePreset.CUSTOM) {
            formatRangeLabel(rawStart, rawEnd, today)
        } else {
            preset.shortLabel
        }

        return ResolvedDateRange(
            preset = preset,
            startDate = rawStart,
            endDate = rawEnd,
            startMs = startMs,
            endMs = endMs,
            label = label
        )
    }

    private fun formatRangeLabel(start: LocalDate, end: LocalDate, today: LocalDate): String {
        val useFullYear = start.year != end.year || end.year != today.year
        val fmt = if (useFullYear) crossYearDateFmt else sameYearDateFmt
        return if (start == end) start.format(fmt) else "${start.format(fmt)} – ${end.format(fmt)}"
    }

    fun filterItemsByRange(
        items: List<MediaItem>,
        startMs: Long?,
        endMs: Long?
    ): List<MediaItem> {
        if (startMs == null && endMs == null) return items
        val minBound = startMs ?: Long.MIN_VALUE
        val maxBound = endMs ?: Long.MAX_VALUE
        return items.filter { item ->
            val lo = item.startDate ?: item.endDate ?: return@filter false
            val hi = item.endDate ?: item.startDate ?: return@filter false
            val itemStart = minOf(lo, hi)
            val itemEnd = maxOf(lo, hi)
            itemEnd >= minBound && itemStart <= maxBound
        }
    }

    fun computeConsumptionStats(items: List<MediaItem>): List<ConsumptionStatItem> {
        if (items.isEmpty()) return emptyList()

        val novelChapters = items
            .filter { it.mediaType == MediaType.NOVEL && !it.type.equals("MOVIE", true) && !it.type.equals("TV", true) }
            .sumOf { it.effectiveProgress() }

        val mangaChapters = items
            .filter { it.mediaType == MediaType.MANGA }
            .sumOf { it.effectiveProgress() }

        val animeEpisodes = items
            .filter { it.mediaType == MediaType.ANIME && !it.format.equals("MOVIE", ignoreCase = true) }
            .sumOf { it.effectiveProgress() }

        val animeMovies = items
            .filter {
                it.mediaType == MediaType.ANIME &&
                    it.format.equals("MOVIE", ignoreCase = true) &&
                    (it.userStatus == UserStatus.COMPLETED || it.effectiveProgress() > 0.0)
            }
            .size
            .toDouble()

        val gameHours = items
            .filter { it.mediaType == MediaType.GAME }
            .sumOf { it.effectiveProgress() }

        val liveActionMovies = items
            .filter { it.type.equals("MOVIE", ignoreCase = true) && (it.userStatus == UserStatus.COMPLETED || it.effectiveProgress() > 0.0) }
            .size
            .toDouble()

        val tvEpisodes = items
            .filter { it.type.equals("TV", ignoreCase = true) }
            .sumOf { it.effectiveProgress() }

        return listOfNotNull(
            buildConsumptionItem("novel_chapters", MediaType.NOVEL, novelChapters, "Novel chapters read"),
            buildConsumptionItem("manga_chapters", MediaType.MANGA, mangaChapters, "Manga chapters read"),
            buildConsumptionItem("anime_episodes", MediaType.ANIME, animeEpisodes, "Anime episodes watched"),
            buildConsumptionItem("anime_movies", MediaType.ANIME, animeMovies, "Anime movies watched"),
            buildConsumptionItem("game_hours", MediaType.GAME, gameHours, "Game hours played"),
            buildConsumptionItem("movies_watched", MediaType.MOVIE, liveActionMovies, "Movies watched"),
            buildConsumptionItem("tv_episodes", MediaType.TV, tvEpisodes, "TV episodes watched")
        )
    }

    private fun buildConsumptionItem(
        key: String,
        mediaType: MediaType,
        value: Double,
        label: String
    ): ConsumptionStatItem? {
        if (value <= 0.0) return null
        return ConsumptionStatItem(
            key = key,
            mediaType = mediaType,
            valueText = formatConsumptionValue(value),
            label = label,
            rawValue = value,
            accentColor = mediaType.color()
        )
    }

    private fun formatConsumptionValue(value: Double): String {
        val roundedInt = kotlin.math.round(value)
        return if (kotlin.math.abs(value - roundedInt) < 0.05) {
            NumberFormat.getIntegerInstance(Locale.US).format(roundedInt.toLong())
        } else {
            val nf = NumberFormat.getNumberInstance(Locale.US).apply {
                maximumFractionDigits = 1
                minimumFractionDigits = 1
            }
            nf.format(value)
        }
    }

    private fun MediaItem.effectiveProgress(): Double =
        maxOf(progress, currentProgress.toDouble()).coerceAtLeast(0.0)

    private fun MediaItem.effectiveTotal(): Double? =
        progressTotal?.takeIf { it > 0.0 } ?: totalItems?.takeIf { it > 0 }?.toDouble()

    private fun MediaItem.effectiveTotalUnits(): Double {
        val base = effectiveProgress()
        val repeats = repeat.coerceAtLeast(0)
        if (repeats == 0) return base
        val cycleLength = effectiveTotal() ?: base
        return base + (repeats * cycleLength)
    }

    /**
     * Computes total consumption time in minutes using:
     * - Anime: AniList `minutesWatched` = (progress + repeat * total) * episodeDuration (24m fallback;
     *   MOVIE without `durationMinutes` treats values >= 20 as raw minutes else 90m/movie)
     * - Novel: 2m per chapter
     * - Manga: 3m per chapter
     * - Game: progress already stored in hours -> * 60m
     */
    fun computeLoggedMinutes(items: List<MediaItem>): Double {
        return items.sumOf { item ->
            val totalUnits = item.effectiveTotalUnits()
            if (totalUnits <= 0.0) return@sumOf 0.0
            when (item.mediaType) {
                MediaType.NOVEL -> totalUnits * NOVEL_MINUTES_PER_CHAPTER
                MediaType.MANGA -> totalUnits * MANGA_MINUTES_PER_CHAPTER
                MediaType.ANIME, MediaType.MOVIE, MediaType.TV -> resolveAnimeMinutes(item, totalUnits)
                MediaType.GAME -> totalUnits * MINUTES_PER_HOUR
            }
        }
    }

    private fun resolveAnimeMinutes(item: MediaItem, totalUnits: Double): Double {
        val duration = item.durationMinutes?.takeIf { it > 0 }?.toDouble()
        return if (item.format.equals("MOVIE", ignoreCase = true) && duration == null) {
            if (totalUnits >= ANIME_MOVIE_RAW_MINUTE_THRESHOLD) totalUnits else totalUnits * ANIME_MOVIE_DEFAULT_MINUTES
        } else {
            totalUnits * (duration ?: ANIME_DEFAULT_EPISODE_MINUTES)
        }
    }

    fun formatLoggedMinutes(totalMinutes: Double): String {
        if (totalMinutes <= 0.0) return "—"
        if (totalMinutes < 1.0) return "< 1m"
        val roundedMinutes = kotlin.math.round(totalMinutes).toLong()
        val days = roundedMinutes / (24L * 60L)
        val hours = (roundedMinutes % (24L * 60L)) / 60L
        val mins = roundedMinutes % 60L
        return when {
            days > 0L && hours > 0L -> "${days}d ${hours}h"
            days > 0L -> "${days}d"
            hours > 0L && mins > 0L -> "${hours}h ${mins}m"
            hours > 0L -> "${hours}h"
            else -> "${mins}m"
        }
    }

    fun computeActivityHeatmap(
        points: List<ActivityDatePoint>,
        selectedType: MediaType?,
        rangeStartDate: LocalDate?,
        rangeEndDate: LocalDate?,
        today: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ActivityHeatmapState {
        val scopedPoints = points.filter { point ->
            point.type == "PROGRESS_UPDATE" &&
                (selectedType == null || resolveMediaType(point.mediaType) == selectedType)
        }

        if (scopedPoints.isEmpty()) {
            return ActivityHeatmapState(hasActivityData = false)
        }

        val dailyCounts = mutableMapOf<LocalDate, Int>()
        val weekdayCounts = IntArray(7) // index 0 = MONDAY .. 6 = SUNDAY

        scopedPoints.forEach { point ->
            val date = Instant.ofEpochMilli(point.timestamp).atZone(zoneId).toLocalDate()
            dailyCounts[date] = (dailyCounts[date] ?: 0) + 1
            weekdayCounts[date.dayOfWeek.value - 1]++
        }

        val totalActivities = scopedPoints.size
        val anchorEnd = rangeEndDate ?: today
        val minActivityDate = dailyCounts.keys.min()
        val rawStart = rangeStartDate ?: minOf(minActivityDate, anchorEnd.minusWeeks(25))
        val effectiveStart = if (rawStart <= anchorEnd) rawStart else anchorEnd

        val gridStart = effectiveStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val gridEnd = anchorEnd.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        val includeYearLabels = effectiveStart.year != anchorEnd.year

        val weeks = mutableListOf<List<ActivityHeatmapDay?>>()
        val monthSpans = mutableListOf<ActivityMonthSpan>()
        var lastLabeledMonthKey: Int? = null
        var lastLabelWeek = -4

        var currMonday = gridStart
        var weekIndex = 0
        while (!currMonday.isAfter(gridEnd)) {
            val weekDays = ArrayList<ActivityHeatmapDay?>(7)
            var monthStartDay: LocalDate? = null
            var firstInRangeDay: LocalDate? = null

            for (offset in 0..6) {
                val date = currMonday.plusDays(offset.toLong())
                val inRange = !date.isBefore(effectiveStart) && !date.isAfter(anchorEnd)
                if (!inRange) {
                    weekDays.add(null)
                } else {
                    val count = dailyCounts[date] ?: 0
                    val level = when {
                        count <= 0 -> 0
                        count <= 3 -> 1
                        count <= 6 -> 2
                        count <= 9 -> 3
                        else -> 4
                    }
                    weekDays.add(
                        ActivityHeatmapDay(
                            epochDay = date.toEpochDay(),
                            dateLabel = date.format(tooltipDateFmt),
                            count = count,
                            level = level,
                            isInRange = true
                        )
                    )
                    if (monthStartDay == null && date.dayOfMonth == 1) {
                        monthStartDay = date
                    }
                    if (firstInRangeDay == null) {
                        firstInRangeDay = date
                    }
                }
            }

            val monthCandidate = monthStartDay ?: if (weekIndex == 0) firstInRangeDay else null
            if (monthCandidate != null) {
                val monthKey = monthCandidate.year * 12 + monthCandidate.monthValue
                val showYear = includeYearLabels && (monthCandidate.monthValue == 1 || monthSpans.isEmpty())
                val minWeekGap = if (showYear) 3 else 2
                if (monthKey != lastLabeledMonthKey && (monthSpans.isEmpty() || weekIndex - lastLabelWeek >= minWeekGap)) {
                    val shortMonth = monthCandidate.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                    val label = if (showYear) {
                        "$shortMonth '${String.format(Locale.ENGLISH, "%02d", monthCandidate.year % 100)}"
                    } else {
                        shortMonth
                    }
                    monthSpans.add(
                        ActivityMonthSpan(
                            weekIndex = weekIndex,
                            label = label
                        )
                    )
                    lastLabeledMonthKey = monthKey
                    lastLabelWeek = weekIndex
                }
            }

            weeks.add(weekDays)
            currMonday = currMonday.plusWeeks(1)
            weekIndex++
        }

        // Strict Yamtrack streak: anchored directly on anchorEnd (today / custom endDate)
        var currentStreak = 0
        var cursor = anchorEnd
        while ((dailyCounts[cursor] ?: 0) > 0) {
            currentStreak++
            cursor = cursor.minusDays(1)
        }

        var longestStreak = 0
        var running = 0
        var prevDate: LocalDate? = null
        dailyCounts.keys.sorted().forEach { date ->
            running = if (prevDate != null && date == prevDate!!.plusDays(1)) running + 1 else 1
            if (running > longestStreak) longestStreak = running
            prevDate = date
        }

        var peakDayIndex = 0
        var peakCount = -1
        for (i in weekdayCounts.indices) {
            if (weekdayCounts[i] > peakCount) {
                peakCount = weekdayCounts[i]
                peakDayIndex = i
            }
        }
        val peakWeekdayName = if (peakCount > 0) {
            DayOfWeek.of(peakDayIndex + 1).getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
        } else null
        val peakPct = if (totalActivities > 0 && peakCount > 0) {
            ((peakCount * 100.0) / totalActivities).roundToInt()
        } else 0

        return ActivityHeatmapState(
            totalActivities = totalActivities,
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            peakWeekdayName = peakWeekdayName,
            peakWeekdayPercentage = peakPct,
            weeks = weeks,
            monthSpans = monthSpans,
            hasActivityData = true
        )
    }

    fun computeStatusBreakdown(
        items: List<MediaItem>,
        selectedType: MediaType?
    ): List<StatusSegmentData> {
        val activeTotal = items.size
        val inProgressCount = items.count {
            it.userStatus in setOf(UserStatus.READING, UserStatus.WATCHING, UserStatus.PLAYING)
        }
        val planToCount = items.count { it.userStatus == UserStatus.PLAN_TO }
        val completedCount = items.count { it.userStatus == UserStatus.COMPLETED }
        val onHoldCount = items.count { it.userStatus == UserStatus.ON_HOLD }
        val droppedCount = items.count { it.userStatus == UserStatus.DROPPED }

        val activeUserStatus = when (selectedType) {
            MediaType.NOVEL, MediaType.MANGA -> UserStatus.READING
            MediaType.ANIME, MediaType.MOVIE, MediaType.TV -> UserStatus.WATCHING
            MediaType.GAME -> UserStatus.PLAYING
            null -> UserStatus.READING
        }

        val ordered = listOf(
            Triple(StatusFilter.IN_PROGRESS, inProgressCount, activeUserStatus),
            Triple(StatusFilter.PLAN_TO, planToCount, UserStatus.PLAN_TO),
            Triple(StatusFilter.COMPLETED, completedCount, UserStatus.COMPLETED),
            Triple(StatusFilter.ON_HOLD, onHoldCount, UserStatus.ON_HOLD),
            Triple(StatusFilter.DROPPED, droppedCount, UserStatus.DROPPED)
        )

        return ordered.mapNotNull { (filter, count, status) ->
            if (count > 0 || activeTotal == 0) {
                val percentage = if (activeTotal > 0) (count.toFloat() / activeTotal) * 100f else 0f
                StatusSegmentData(
                    status = status,
                    label = selectedType.statusLabel(filter),
                    count = count,
                    percentage = percentage,
                    color = status.color()
                )
            } else {
                null
            }
        }
    }

    fun computeTypeStatusStacks(items: List<MediaItem>): List<TypeStatusStack> {
        val grouped = MediaType.entries.mapNotNull { mediaType ->
            val typeItems = items.filter { it.mediaType == mediaType }
            if (typeItems.isEmpty()) return@mapNotNull null
            TypeStatusStack(
                mediaType = mediaType,
                totalCount = typeItems.size,
                segments = computeStatusBreakdown(typeItems, mediaType)
            )
        }
        return if (grouped.size >= 2) grouped else emptyList()
    }

    fun computeStackedRatings(
        items: List<MediaItem>,
        selectedType: MediaType?
    ): List<RatingBucketRow> {
        val ratedItems = items.filter { it.rating != null }
        val countsPerBucket = ratingBuckets.map { spec ->
            ratedItems.filter { spec.matches(it.rating!!) }
        }
        val maxBucketCount = countsPerBucket.maxOfOrNull { it.size } ?: 0

        return ratingBuckets.mapIndexed { index, spec ->
            val bucketItems = countsPerBucket[index]
            val count = bucketItems.size
            val fraction = if (maxBucketCount > 0) (count.toFloat() / maxBucketCount).coerceIn(0f, 1f) else 0f
            val slices = if (selectedType == null && count > 0) {
                MediaType.entries.mapNotNull { mediaType ->
                    val typeCount = bucketItems.count { it.mediaType == mediaType }
                    if (typeCount > 0) {
                        RatingTypeSlice(
                            mediaType = mediaType,
                            count = typeCount,
                            color = mediaType.color()
                        )
                    } else null
                }
            } else {
                emptyList()
            }

            RatingBucketRow(
                bucketLabel = spec.label,
                totalCount = count,
                barFraction = fraction,
                color = getRatingColor(spec.representativeScore),
                typeSlices = slices
            )
        }
    }

    fun computeTopRated(
        items: List<MediaItem>,
        blurAdultCovers: Boolean,
        limit: Int = 12
    ): List<TopRatedMediaCardData> {
        return items
            .filter { it.rating != null }
            .sortedWith(compareByDescending<MediaItem> { it.rating }.thenByDescending { it.lastUpdated })
            .take(limit)
            .map { item ->
                TopRatedMediaCardData(
                    id = item.id,
                    title = item.title,
                    rating = item.rating!!,
                    coverImagePath = item.coverImagePath,
                    coverImageUrl = item.coverImageUrl,
                    mediaType = item.mediaType,
                    isObscured = blurAdultCovers && item.isAdult
                )
            }
    }
}
