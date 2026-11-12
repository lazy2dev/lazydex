package app.lazydex.ui.statistics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lazydex.domain.model.MediaType
import app.lazydex.ui.components.EmptyState
import app.lazydex.ui.components.Pill
import app.lazydex.ui.components.color
import app.lazydex.ui.components.icon
import app.lazydex.ui.statistics.components.ActivityHeatmapBlock
import app.lazydex.ui.statistics.components.TypeStackedRatings
import app.lazydex.ui.statistics.components.ConsumptionBlock
import app.lazydex.ui.statistics.components.StatsChipCloud
import app.lazydex.ui.statistics.components.StatusRevampBlock
import app.lazydex.ui.statistics.components.TopRatedGallery
import app.lazydex.ui.theme.getRatingColor
import org.koin.androidx.compose.koinViewModel
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    modifier: Modifier = Modifier,
    onNavigateToItem: (String) -> Unit = {},
    viewModel: StatisticsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDateSheet by remember { mutableStateOf(false) }
    var showCustomDateDialog by remember { mutableStateOf(false) }
    var selectedHeatmapDay by remember(
        uiState.activityHeatmap,
        uiState.selectedType,
        uiState.dateRange
    ) {
        mutableStateOf<ActivityHeatmapDay?>(null)
    }

    val mediaTypes = remember {
        listOf(
            null to "All",
            MediaType.NOVEL to "Novels",
            MediaType.MANGA to "Manga",
            MediaType.ANIME to "Anime",
            MediaType.GAME to "Games",
            MediaType.MOVIE to "Movies",
            MediaType.TV to "TV"
        )
    }

    val selectedTabIndex = remember(uiState.selectedType) {
        val idx = mediaTypes.indexOfFirst { it.first == uiState.selectedType }
        if (idx >= 0) idx else 0
    }

    val accentColor = uiState.selectedType?.color() ?: MaterialTheme.colorScheme.primary

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Statistics",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                actions = {
                    DateRangeHeaderPill(
                        label = uiState.dateRange.displayLabel,
                        isFiltered = uiState.dateRange.preset != StatsDateRangePreset.ALL_TIME,
                        accentColor = accentColor,
                        onClick = { showDateSheet = true },
                        modifier = Modifier.padding(end = 12.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 0.dp,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onSurface,
                divider = {},
                indicator = { tabPositions ->
                    if (selectedTabIndex < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = accentColor
                        )
                    }
                }
            ) {
                mediaTypes.forEachIndexed { index, (mediaType, label) ->
                    val isSelected = selectedTabIndex == index
                    val count = uiState.typeCounts[mediaType] ?: 0
                    Tab(
                        selected = isSelected,
                        onClick = { viewModel.selectType(mediaType) },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = label,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (count > 0) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Pill(
                                        text = "$count",
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    )
                }
            }
            HorizontalDivider()

            val overview = uiState.overview
            val hasBoundedFilter = uiState.dateRange.preset != StatsDateRangePreset.ALL_TIME

            if (overview.totalCount == 0 && !uiState.isLoading) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 64.dp, start = 24.dp, end = 24.dp)
                ) {
                    EmptyState(
                        message = when {
                            hasBoundedFilter && uiState.selectedType != null ->
                                "No ${uiState.selectedType?.displayName?.lowercase()} matched ${uiState.dateRange.displayLabel}"
                            hasBoundedFilter ->
                                "No dated entries matched ${uiState.dateRange.displayLabel}"
                            uiState.selectedType != null ->
                                "No ${uiState.selectedType?.displayName?.lowercase()} in library yet"
                            else ->
                                "No media in library yet"
                        }
                    )
                    if (hasBoundedFilter) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { viewModel.selectDateRangePreset(StatsDateRangePreset.ALL_TIME) }) {
                            Text("Show all time")
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // 1. Komikku Overview Tonal Block
                    item(key = "overview") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SectionHeader(
                                title = "Overview",
                                accentColor = accentColor,
                                trailing = if (overview.durationText != "—") {
                                    "${overview.durationText} logged"
                                } else null
                            )
                            KomikkuOverviewBlock(
                                overview = overview,
                                selectedType = uiState.selectedType,
                                accentColor = accentColor
                            )
                        }
                    }

                    // 2. Per-type Consumption (All tab only)
                    if (uiState.selectedType == null && uiState.consumptionStats.isNotEmpty()) {
                        item(key = "consumption") {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                SectionHeader(
                                    title = "Consumption",
                                    accentColor = accentColor,
                                    trailing = "By unit"
                                )
                                ConsumptionBlock(items = uiState.consumptionStats)
                            }
                        }
                    }

                    // 3. Activity Heatmap & Streaks
                    item(key = "activity_heatmap") {
                        val activityTrailing = selectedHeatmapDay?.let { day ->
                            "${day.dateLabel} (${day.count})"
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SectionHeader(
                                title = "Activity",
                                accentColor = accentColor,
                                trailing = activityTrailing
                            )
                            ActivityHeatmapBlock(
                                heatmap = uiState.activityHeatmap,
                                accentColor = accentColor,
                                selectedDay = selectedHeatmapDay,
                                onSelectDay = { selectedHeatmapDay = it }
                            )
                        }
                    }

                    // 4. Status Breakdown & Type Stacks
                    item(key = "status_breakdown") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SectionHeader(
                                title = "Status",
                                accentColor = accentColor,
                                trailing = "${overview.totalCount} ${if (overview.totalCount == 1) "entry" else "entries"}"
                            )
                            StatusRevampBlock(
                                segments = uiState.statusBreakdown,
                                typeStacks = uiState.typeStatusStacks,
                                onSelectType = { viewModel.selectType(it) }
                            )
                        }
                    }

                    // 5. Stacked Rating Distribution
                    item(key = "ratings") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SectionHeader(
                                title = "Ratings",
                                accentColor = accentColor,
                                trailing = "${overview.ratedCount} / ${overview.totalCount} rated"
                            )
                            TypeStackedRatings(
                                overview = overview,
                                buckets = uiState.stackedRatingBuckets,
                                selectedType = uiState.selectedType,
                                accentColor = accentColor
                            )
                        }
                    }

                    // 6. Top Rated Gallery
                    if (uiState.topRatedItems.isNotEmpty()) {
                        item(key = "top_rated") {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                SectionHeader(
                                    title = "Top rated",
                                    accentColor = accentColor,
                                    trailing = "Top ${uiState.topRatedItems.size}"
                                )
                                TopRatedGallery(
                                    items = uiState.topRatedItems,
                                    onItemClick = onNavigateToItem
                                )
                            }
                        }
                    }

                    // 7. Genres & Tags Chip Clouds
                    item(key = "genre_cloud") {
                        StatsChipCloud(
                            title = "Top genres",
                            items = uiState.genreBars,
                            accentColor = accentColor
                        )
                    }

                    item(key = "tag_cloud") {
                        StatsChipCloud(
                            title = "Top tags",
                            items = uiState.tagBars,
                            accentColor = accentColor
                        )
                    }
                }
            }
        }
    }

    if (showDateSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showDateSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            DateRangePresetSheetContent(
                selectedPreset = uiState.dateRange.preset,
                onSelectPreset = { preset ->
                    viewModel.selectDateRangePreset(preset)
                    showDateSheet = false
                },
                onOpenCustom = {
                    showDateSheet = false
                    showCustomDateDialog = true
                }
            )
        }
    }

    if (showCustomDateDialog) {
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = uiState.dateRange.startMs,
            initialSelectedEndDateMillis = uiState.dateRange.endMs
        )
        DatePickerDialog(
            onDismissRequest = { showCustomDateDialog = false },
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedStartDateMillis != null || pickerState.selectedEndDateMillis != null,
                    onClick = {
                        viewModel.setCustomDateRange(
                            startUtcMillis = pickerState.selectedStartDateMillis,
                            endUtcMillis = pickerState.selectedEndDateMillis
                        )
                        showCustomDateDialog = false
                    }
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDateDialog = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DateRangePicker(
                state = pickerState,
                title = {
                    Text(
                        text = "Select date range",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp)
                    )
                },
                showModeToggle = false,
                modifier = Modifier.height(460.dp)
            )
        }
    }
}

@Composable
private fun DateRangeHeaderPill(
    label: String,
    isFiltered: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (isFiltered) accentColor.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (isFiltered) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.CalendarToday,
                contentDescription = "Date range",
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Icon(
                imageVector = Icons.Rounded.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DateRangePresetSheetContent(
    selectedPreset: StatsDateRangePreset,
    onSelectPreset: (StatsDateRangePreset) -> Unit,
    onOpenCustom: () -> Unit
) {
    val presets = remember {
        listOf(
            StatsDateRangePreset.ALL_TIME,
            StatsDateRangePreset.LAST_7_DAYS,
            StatsDateRangePreset.LAST_30_DAYS,
            StatsDateRangePreset.LAST_1_YEAR
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Date range",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Activity heatmap uses event timestamps; library totals match entries with start or end dates overlapping the range.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            presets.forEach { preset ->
                FilterChip(
                    selected = selectedPreset == preset,
                    onClick = { onSelectPreset(preset) },
                    label = { Text(preset.shortLabel) }
                )
            }
            FilterChip(
                selected = selectedPreset == StatsDateRangePreset.CUSTOM,
                onClick = onOpenCustom,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.DateRange,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = { Text("Custom range") }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun SectionHeader(
    title: String,
    accentColor: Color,
    trailing: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = accentColor
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun KomikkuOverviewBlock(
    overview: StatsOverviewData,
    selectedType: MediaType?,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val completedPct = if (overview.totalCount > 0) {
        (overview.completedCount.toFloat() / overview.totalCount * 100f).roundToInt()
    } else 0
    val meanRatingText = overview.meanRating?.let { String.format(Locale.US, "%.2f", it) } ?: "—"
    val ratingTint = overview.meanRating?.let { getRatingColor(it) } ?: accentColor

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KomikkuStatColumn(
                value = "${overview.totalCount}",
                label = "In library",
                sublabel = if (selectedType != null && overview.totalProgressUnits > 0) {
                    "${overview.totalProgressUnits} ${overview.progressUnitLabel.lowercase()}"
                } else {
                    null
                },
                icon = selectedType?.icon() ?: Icons.AutoMirrored.Rounded.LibraryBooks,
                iconTint = accentColor,
                modifier = Modifier.weight(1f)
            )
            KomikkuStatColumn(
                value = "${overview.completedCount}",
                label = "Completed",
                sublabel = if (overview.totalCount > 0) "$completedPct% finished" else null,
                icon = Icons.Rounded.CheckCircle,
                iconTint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            KomikkuStatColumn(
                value = meanRatingText,
                label = "Mean rating",
                sublabel = "${overview.ratedCount} rated",
                icon = Icons.Rounded.Star,
                iconTint = ratingTint,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun KomikkuStatColumn(
    value: String,
    label: String,
    sublabel: String?,
    icon: ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (sublabel != null) {
            Text(
                text = sublabel,
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
