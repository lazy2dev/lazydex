package app.lazydex.ui.statistics

import androidx.compose.ui.graphics.Color
import app.lazydex.domain.model.MediaCategory
import app.lazydex.domain.model.UserStatus

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
    val selectedCategory: MediaCategory? = null,
    val categoryCounts: Map<MediaCategory?, Int> = emptyMap(),
    val overview: StatsOverviewData = StatsOverviewData(),
    val statusBreakdown: List<StatusSegmentData> = emptyList(),
    val genreBars: List<StatBarItem> = emptyList(),
    val tagBars: List<StatBarItem> = emptyList()
)
