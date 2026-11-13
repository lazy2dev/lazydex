package app.lazydex.ui.statistics

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lazydex.data.local.ThemePreferences
import app.lazydex.domain.model.ActivityDatePoint
import app.lazydex.domain.model.MediaType
import app.lazydex.domain.model.MediaItem
import app.lazydex.domain.model.MediaStats
import app.lazydex.domain.model.UserStatus
import app.lazydex.domain.repository.MediaRepository
import app.lazydex.ui.components.color
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

class StatisticsViewModel(
    private val repository: MediaRepository,
    private val themePreferences: ThemePreferences? = null,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    private data class DateRangeSelection(
        val preset: StatsDateRangePreset = StatsDateRangePreset.ALL_TIME,
        val customStartDate: LocalDate? = null,
        val customEndDate: LocalDate? = null
    )

    private val _selectedType = MutableStateFlow<MediaType?>(null)
    private val _dateRangeSelection = MutableStateFlow(DateRangeSelection())

    private val blurAdultCoversFlow: Flow<Boolean> =
        themePreferences?.displayPreferences?.map { it.blurAdultCovers }?.distinctUntilChanged()
            ?: flowOf(false)

    // Retained for backward compatibility
    val statsState: StateFlow<MediaStats?> = repository.observeStats()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<StatisticsUiState> = _dateRangeSelection.flatMapLatest { selection ->
        val resolvedRange = StatisticsCalculator.resolveDateRange(
            preset = selection.preset,
            customStartDate = selection.customStartDate,
            customEndDate = selection.customEndDate,
            today = todayProvider(),
            zoneId = zoneId
        )

        combine(
            repository.observeAll(),
            repository.observeTypeCounts(),
            repository.observeActivityPoints(resolvedRange.startMs, resolvedRange.endMs),
            _selectedType,
            blurAdultCoversFlow
        ) { allItems, typeCountsMap, activityPoints, selectedType, blurAdultCovers ->
            computeUiState(
                allItems = allItems,
                typeCountsMap = typeCountsMap,
                activityPoints = activityPoints,
                selectedType = selectedType,
                resolvedRange = resolvedRange,
                blurAdultCovers = blurAdultCovers
            )
        }.flowOn(computeDispatcher)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StatisticsUiState(isLoading = true)
    )

    fun selectType(mediaType: MediaType?) {
        _selectedType.value = mediaType
    }

    fun selectDateRangePreset(preset: StatsDateRangePreset) {
        if (preset == StatsDateRangePreset.CUSTOM) return
        _dateRangeSelection.value = DateRangeSelection(preset = preset)
    }

    fun setCustomDateRange(startUtcMillis: Long?, endUtcMillis: Long?) {
        if (startUtcMillis == null && endUtcMillis == null) return
        val startDate = startUtcMillis?.let { StatisticsCalculator.pickerUtcMillisToLocalDate(it) }
        val endDate = endUtcMillis?.let { StatisticsCalculator.pickerUtcMillisToLocalDate(it) }
        _dateRangeSelection.value = DateRangeSelection(
            preset = StatsDateRangePreset.CUSTOM,
            customStartDate = startDate ?: endDate,
            customEndDate = endDate ?: startDate
        )
    }

    private fun computeUiState(
        allItems: List<MediaItem>,
        typeCountsMap: Map<MediaType, Int>,
        activityPoints: List<ActivityDatePoint>,
        selectedType: MediaType?,
        resolvedRange: ResolvedDateRange,
        blurAdultCovers: Boolean
    ): StatisticsUiState {
        val isAllTime = resolvedRange.preset == StatsDateRangePreset.ALL_TIME
        val rangeItems = StatisticsCalculator.filterItemsByRange(
            items = allItems,
            startMs = resolvedRange.startMs,
            endMs = resolvedRange.endMs
        )

        val tabCounts = buildMap<MediaType?, Int> {
            put(null, rangeItems.size)
            MediaType.entries.forEach { type ->
                val count = if (isAllTime) {
                    typeCountsMap[type] ?: rangeItems.count { it.mediaType == type }
                } else {
                    rangeItems.count { it.mediaType == type }
                }
                put(type, count)
            }
        }

        val activeItems = if (selectedType == null) {
            rangeItems
        } else {
            rangeItems.filter { it.mediaType == selectedType }
        }
        val activeTotal = activeItems.size

        val completedCount = activeItems.count { it.userStatus == UserStatus.COMPLETED }
        val inProgressCount = activeItems.count { it.userStatus == UserStatus.CURRENT || it.userStatus == UserStatus.REPEATING }
        val planToCount = activeItems.count { it.userStatus == UserStatus.PLANNING }
        val onHoldCount = activeItems.count { it.userStatus == UserStatus.PAUSED }
        val droppedCount = activeItems.count { it.userStatus == UserStatus.DROPPED }
        val totalProgressUnits = activeItems.sumOf {
            maxOf(it.progress, it.currentProgress.toDouble()).roundToInt()
        }

        val ratedItems = activeItems.mapNotNull { it.rating }
        val ratedCount = ratedItems.size
        val unratedCount = activeTotal - ratedCount
        val meanRating = if (ratedItems.isNotEmpty()) ratedItems.average() else null
        val ratingDistribution = (1..5).associateWith { star ->
            ratedItems.count { rating ->
                kotlin.math.round(rating).toInt().coerceIn(1, 5) == star
            }
        }

        val overview = buildOverview(
            activeItems = activeItems,
            selectedType = selectedType,
            activeTotal = activeTotal,
            totalProgressUnits = totalProgressUnits,
            inProgressCount = inProgressCount,
            planToCount = planToCount,
            completedCount = completedCount,
            onHoldCount = onHoldCount,
            droppedCount = droppedCount,
            meanRating = meanRating,
            ratedCount = ratedCount,
            unratedCount = unratedCount
        )

        val consumptionStats = if (selectedType == null) {
            StatisticsCalculator.computeConsumptionStats(rangeItems)
        } else {
            emptyList()
        }

        val today = todayProvider()
        val activityHeatmap = StatisticsCalculator.computeActivityHeatmap(
            points = activityPoints,
            selectedType = selectedType,
            rangeStartDate = resolvedRange.startDate,
            rangeEndDate = resolvedRange.endDate,
            today = today,
            zoneId = zoneId
        )

        val statusSegments = StatisticsCalculator.computeStatusBreakdown(activeItems, selectedType)
        val typeStatusStacks = if (selectedType == null) {
            StatisticsCalculator.computeTypeStatusStacks(rangeItems)
        } else {
            emptyList()
        }
        val stackedRatingBuckets = StatisticsCalculator.computeStackedRatings(activeItems, selectedType)
        val topRatedItems = StatisticsCalculator.computeTopRated(activeItems, blurAdultCovers, limit = 12)

        val barColor = selectedType?.color()
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
            selectedType = selectedType,
            typeCounts = tabCounts,
            dateRange = resolvedRange.toUiState(),
            overview = overview,
            consumptionStats = consumptionStats,
            activityHeatmap = activityHeatmap,
            statusBreakdown = statusSegments,
            typeStatusStacks = typeStatusStacks,
            ratingDistribution = ratingDistribution,
            stackedRatingBuckets = stackedRatingBuckets,
            topRatedItems = topRatedItems,
            genreBars = genreBars,
            tagBars = tagBars
        )
    }

    private fun buildOverview(
        activeItems: List<MediaItem>,
        selectedType: MediaType?,
        activeTotal: Int,
        totalProgressUnits: Int,
        inProgressCount: Int,
        planToCount: Int,
        completedCount: Int,
        onHoldCount: Int,
        droppedCount: Int,
        meanRating: Double?,
        ratedCount: Int,
        unratedCount: Int
    ): StatsOverviewData {
        val loggedMinutes = StatisticsCalculator.computeLoggedMinutes(activeItems)
        val durationText = StatisticsCalculator.formatLoggedMinutes(loggedMinutes)
        val durationLabel = when (selectedType) {
            MediaType.NOVEL, MediaType.MANGA -> "Read duration"
            MediaType.ANIME, MediaType.MOVIE, MediaType.TV -> "Watch duration"
            MediaType.GAME -> "Play duration"
            null -> "Duration"
        }
        val progressUnitLabel = when (selectedType) {
            MediaType.NOVEL, MediaType.MANGA -> "Chapters read"
            MediaType.ANIME, MediaType.TV -> "Episodes watched"
            MediaType.GAME -> "Hours played"
            MediaType.MOVIE -> "Movies watched"
            null -> "Progress units"
        }
        val inProgressLabel = when (selectedType) {
            MediaType.NOVEL, MediaType.MANGA -> "Reading"
            MediaType.ANIME, MediaType.MOVIE, MediaType.TV -> "Watching"
            MediaType.GAME -> "Playing"
            null -> "In progress"
        }
        val planToLabel = when (selectedType) {
            MediaType.NOVEL, MediaType.MANGA -> "Plan to Read"
            MediaType.ANIME, MediaType.MOVIE, MediaType.TV -> "Plan to Watch"
            MediaType.GAME -> "Plan to Play"
            null -> "Plan to"
        }
        val rereadingLabel = when (selectedType) {
            MediaType.NOVEL, MediaType.MANGA -> "Rereading"
            MediaType.ANIME, MediaType.MOVIE, MediaType.TV -> "Rewatching"
            MediaType.GAME -> "Replaying"
            null -> "Rereading"
        }

        return StatsOverviewData(
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
            rereadingCount = 0,
            rereadingLabel = rereadingLabel,
            meanRating = meanRating,
            ratedCount = ratedCount,
            unratedCount = unratedCount
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
