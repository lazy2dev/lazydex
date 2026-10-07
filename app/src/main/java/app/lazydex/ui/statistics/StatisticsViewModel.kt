package app.lazydex.ui.statistics

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lazydex.domain.model.MediaCategory
import app.lazydex.domain.model.MediaItem
import app.lazydex.domain.model.MediaStats
import app.lazydex.domain.model.StatusFilter
import app.lazydex.domain.model.UserStatus
import app.lazydex.domain.model.statusLabel
import app.lazydex.domain.repository.MediaRepository
import app.lazydex.ui.components.color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.TimeUnit

class StatisticsViewModel(
    private val repository: MediaRepository
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow<MediaCategory?>(null)

    // Retained for backward compatibility
    val statsState: StateFlow<MediaStats?> = repository.observeStats()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val uiState: StateFlow<StatisticsUiState> = combine(
        repository.observeAll(),
        repository.observeCategoryCounts(),
        _selectedCategory
    ) { allItems, categoryCountsMap, selectedCategory ->
        computeUiState(
            allItems = allItems,
            categoryCountsMap = categoryCountsMap,
            selectedCategory = selectedCategory
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StatisticsUiState(isLoading = true)
    )

    fun selectCategory(category: MediaCategory?) {
        _selectedCategory.value = category
    }

    private fun computeUiState(
        allItems: List<MediaItem>,
        categoryCountsMap: Map<MediaCategory, Int>,
        selectedCategory: MediaCategory?
    ): StatisticsUiState {
        // Tab item counts: null represents "All"
        val totalCount = allItems.size
        val tabCounts = mutableMapOf<MediaCategory?, Int>()
        tabCounts[null] = totalCount
        MediaCategory.entries.forEach { cat ->
            tabCounts[cat] = categoryCountsMap[cat] ?: allItems.count { it.category == cat }
        }

        // Active items filtered by selected category
        val activeItems = if (selectedCategory == null) {
            allItems
        } else {
            allItems.filter { it.category == selectedCategory }
        }
        val activeTotal = activeItems.size

        // KPI metrics
        val completedCount = activeItems.count { it.userStatus == UserStatus.COMPLETED }
        val inProgressCount = activeItems.count {
            it.userStatus in setOf(UserStatus.READING, UserStatus.WATCHING, UserStatus.PLAYING)
        }
        val planToCount = activeItems.count { it.userStatus == UserStatus.PLAN_TO }
        val onHoldCount = activeItems.count { it.userStatus == UserStatus.ON_HOLD }
        val droppedCount = activeItems.count { it.userStatus == UserStatus.DROPPED }
        val rereadingCount = activeItems.count { it.getExtra("is_rereading") == "true" }
        val totalProgressUnits = activeItems.sumOf { it.currentProgress }

        val ratedItems = activeItems.mapNotNull { it.rating }
        val ratedCount = ratedItems.size
        val unratedCount = activeTotal - ratedCount
        val meanRating = if (ratedItems.isNotEmpty()) ratedItems.average() else null

        // Duration computation
        val now = System.currentTimeMillis()
        val totalDurationMillis = activeItems.sumOf { item ->
            val start = item.startDate
            if (start != null && start > 0) {
                val end = item.endDate ?: now
                if (end >= start) end - start else 0L
            } else {
                0L
            }
        }

        val durationText = if (totalDurationMillis > 0) {
            val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(totalDurationMillis)
            val days = totalMinutes / (24 * 60)
            val hours = (totalMinutes % (24 * 60)) / 60
            val mins = totalMinutes % 60
            when {
                days > 0 && hours > 0 -> "${days}d ${hours}h"
                days > 0 -> "${days}d"
                hours > 0 -> "${hours}h"
                mins > 0 -> "${mins}m"
                else -> "< 1m"
            }
        } else {
            "—"
        }

        val durationLabel = when (selectedCategory) {
            MediaCategory.NOVEL, MediaCategory.MANGA -> "Read duration"
            MediaCategory.ANIME, MediaCategory.MOVIE, MediaCategory.TV -> "Watch duration"
            MediaCategory.GAME -> "Play duration"
            null -> "Duration"
        }

        val progressUnitLabel = when (selectedCategory) {
            MediaCategory.NOVEL, MediaCategory.MANGA -> "Chapters read"
            MediaCategory.ANIME, MediaCategory.TV -> "Episodes watched"
            MediaCategory.GAME -> "Games played"
            MediaCategory.MOVIE -> "Movies watched"
            null -> "Total progress"
        }

        val inProgressLabel = when (selectedCategory) {
            MediaCategory.NOVEL, MediaCategory.MANGA -> "Reading"
            MediaCategory.ANIME, MediaCategory.MOVIE, MediaCategory.TV -> "Watching"
            MediaCategory.GAME -> "Playing"
            null -> "In progress"
        }

        val planToLabel = when (selectedCategory) {
            MediaCategory.NOVEL, MediaCategory.MANGA -> "Plan to Read"
            MediaCategory.ANIME, MediaCategory.MOVIE, MediaCategory.TV -> "Plan to Watch"
            MediaCategory.GAME -> "Plan to Play"
            null -> "Plan to"
        }

        val rereadingLabel = when (selectedCategory) {
            MediaCategory.NOVEL, MediaCategory.MANGA -> "Rereading"
            MediaCategory.ANIME, MediaCategory.MOVIE, MediaCategory.TV -> "Rewatching"
            MediaCategory.GAME -> "Replaying"
            null -> "Rereading"
        }

        val overview = StatsOverviewData(
            totalCount = activeTotal,
            totalProgressUnits = totalProgressUnits,
            progressUnitLabel = progressUnitLabel,
            durationText = durationText,
            durationLabel = durationLabel,
            inProgressCount = inProgressCount,
            inProgressLabel = inProgressLabel,
            planToCount = planToCount,
            planToLabel = planToLabel,
            completedCount = completedCount,
            onHoldCount = onHoldCount,
            droppedCount = droppedCount,
            rereadingCount = rereadingCount,
            rereadingLabel = rereadingLabel,
            meanRating = meanRating,
            ratedCount = ratedCount,
            unratedCount = unratedCount
        )

        // Status breakdown segments
        val statusCounts = UserStatus.entries.associateWith { status ->
            activeItems.count { it.userStatus == status }
        }
        val statusSegments = UserStatus.entries.mapNotNull { status ->
            val count = statusCounts[status] ?: 0
            if (count > 0 || activeTotal == 0) {
                val percentage = if (activeTotal > 0) (count.toFloat() / activeTotal) * 100f else 0f
                val label = when (status) {
                    UserStatus.READING, UserStatus.WATCHING, UserStatus.PLAYING ->
                        selectedCategory.statusLabel(StatusFilter.IN_PROGRESS)
                    UserStatus.PLAN_TO ->
                        selectedCategory.statusLabel(StatusFilter.PLAN_TO)
                    UserStatus.COMPLETED -> "Completed"
                    UserStatus.ON_HOLD -> "On Hold"
                    UserStatus.DROPPED -> "Dropped"
                }
                StatusSegmentData(
                    status = status,
                    label = label,
                    count = count,
                    percentage = percentage,
                    color = status.color()
                )
            } else null
        }

        val barColor = selectedCategory?.color()

        val genreBars = computeBarItems(
            items = activeItems.flatMap { it.genres },
            totalScopeCount = activeTotal,
            barColor = barColor
        )

        val tagBars = computeBarItems(
            items = activeItems.flatMap { it.tags },
            totalScopeCount = activeTotal,
            barColor = barColor
        )

        return StatisticsUiState(
            isLoading = false,
            selectedCategory = selectedCategory,
            categoryCounts = tabCounts,
            overview = overview,
            statusBreakdown = statusSegments,
            genreBars = genreBars,
            tagBars = tagBars
        )
    }

    private fun computeBarItems(
        items: List<String>,
        totalScopeCount: Int,
        barColor: Color?
    ): List<StatBarItem> {
        val freqMap = items
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()

        if (freqMap.isEmpty()) return emptyList()

        val sortedEntries = freqMap.entries.sortedWith(
            compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.lowercase() }
        )

        val maxCount = freqMap.values.maxOrNull() ?: 1

        return sortedEntries.map { entry ->
            val count = entry.value
            val fraction = if (maxCount > 0) (count.toFloat() / maxCount).coerceIn(0f, 1f) else 0f
            val percentage = if (totalScopeCount > 0) (count.toFloat() / totalScopeCount) * 100f else 0f

            StatBarItem(
                name = entry.key,
                count = count,
                percentage = percentage,
                barFraction = fraction,
                color = barColor
            )
        }
    }
}
