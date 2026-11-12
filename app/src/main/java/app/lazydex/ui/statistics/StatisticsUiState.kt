package app.lazydex.ui.statistics

import androidx.compose.ui.graphics.Color
import app.lazydex.domain.model.MediaType
import app.lazydex.domain.model.UserStatus

enum class StatsDateRangePreset(val shortLabel: String) {
    LAST_7_DAYS("Last 7d"),
    LAST_30_DAYS("Last 30d"),
    LAST_1_YEAR("Last 1y"),
    ALL_TIME("All Time"),
    CUSTOM("Custom")
}

data class DateRangeUiState(
    val preset: StatsDateRangePreset = StatsDateRangePreset.ALL_TIME,
    val startMs: Long? = null,
    val endMs: Long? = null,
    val displayLabel: String = StatsDateRangePreset.ALL_TIME.shortLabel
)

data class ConsumptionStatItem(
    val key: String,
    val mediaType: MediaType,
    val valueText: String,
    val label: String,
    val rawValue: Double,
    val accentColor: Color
)

data class ActivityHeatmapDay(
    val epochDay: Long,
    val dateLabel: String,
    val count: Int,
    val level: Int,
    val isInRange: Boolean
)

data class ActivityMonthSpan(
    val weekIndex: Int,
    val label: String
)

data class ActivityHeatmapState(
    val totalActivities: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val peakWeekdayName: String? = null,
    val peakWeekdayPercentage: Int = 0,
    val weeks: List<List<ActivityHeatmapDay?>> = emptyList(),
    val monthSpans: List<ActivityMonthSpan> = emptyList(),
    val hasActivityData: Boolean = false
)

data class TypeStatusStack(
    val mediaType: MediaType,
    val totalCount: Int,
    val segments: List<StatusSegmentData>
)

data class RatingTypeSlice(
    val mediaType: MediaType,
    val count: Int,
    val color: Color
)

data class RatingBucketRow(
    val bucketLabel: String,
    val totalCount: Int,
    val barFraction: Float,
    val color: Color,
    val typeSlices: List<RatingTypeSlice> = emptyList()
)

data class TopRatedMediaCardData(
    val id: String,
    val title: String,
    val rating: Double,
    val coverImagePath: String,
    val coverImageUrl: String?,
    val mediaType: MediaType,
    val isObscured: Boolean
)

data class StatsOverviewData(
    val totalCount: Int = 0,
    val totalProgressUnits: Int = 0,
    val progressUnitLabel: String = "Total progress",
    val durationText: String = "—",
    val durationLabel: String = "Duration",
    val inProgressCount: Int = 0,
    val inProgressLabel: String = "In progress",
    val planToCount: Int = 0,
    val planToLabel: String = "Plan to",
    val completedCount: Int = 0,
    val onHoldCount: Int = 0,
    val droppedCount: Int = 0,
    val rereadingCount: Int = 0,
    val rereadingLabel: String = "Rereading",
    val meanRating: Double? = null,
    val ratedCount: Int = 0,
    val unratedCount: Int = 0
)

data class StatusSegmentData(
    val status: UserStatus,
    val label: String,
    val count: Int,
    val percentage: Float,
    val color: Color
)

data class StatBarItem(
    val name: String,
    val count: Int,
    val percentage: Float,
    val barFraction: Float,
    val color: Color? = null
)

data class StatisticsUiState(
    val isLoading: Boolean = false,
    val selectedType: MediaType? = null,
    val typeCounts: Map<MediaType?, Int> = emptyMap(),
    val dateRange: DateRangeUiState = DateRangeUiState(),
    val overview: StatsOverviewData = StatsOverviewData(),
    val consumptionStats: List<ConsumptionStatItem> = emptyList(),
    val activityHeatmap: ActivityHeatmapState = ActivityHeatmapState(),
    val statusBreakdown: List<StatusSegmentData> = emptyList(),
    val typeStatusStacks: List<TypeStatusStack> = emptyList(),
    val ratingDistribution: Map<Int, Int> = emptyMap(),
    val stackedRatingBuckets: List<RatingBucketRow> = emptyList(),
    val topRatedItems: List<TopRatedMediaCardData> = emptyList(),
    val genreBars: List<StatBarItem> = emptyList(),
    val tagBars: List<StatBarItem> = emptyList()
)
