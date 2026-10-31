package app.lazydex.ui.statistics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.StarHalf
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lazydex.domain.model.MediaCategory
import app.lazydex.ui.components.EmptyState
import app.lazydex.ui.components.Pill
import app.lazydex.ui.components.color
import app.lazydex.ui.statistics.components.StatsChipCloud
import app.lazydex.ui.statistics.components.StatusSegmentedBar
import app.lazydex.ui.theme.StatusCompleted
import app.lazydex.ui.theme.StatusDropped
import app.lazydex.ui.theme.StatusInProgress
import app.lazydex.ui.theme.StatusOnHold
import app.lazydex.ui.theme.StatusPlanTo
import app.lazydex.ui.theme.getRatingColor
import org.koin.androidx.compose.koinViewModel
import java.util.Locale
import kotlin.math.roundToInt

private val StarGold = Color(0xFFFFB800)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    viewModel: StatisticsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val categories = remember {
        listOf(
            null to "All",
            MediaCategory.NOVEL to "Novels",
            MediaCategory.MANGA to "Manga",
            MediaCategory.ANIME to "Anime",
            MediaCategory.GAME to "Games",
            MediaCategory.MOVIE to "Movies",
            MediaCategory.TV to "TV"
        )
    }

    val selectedTabIndex = remember(uiState.selectedCategory) {
        val idx = categories.indexOfFirst { it.first == uiState.selectedCategory }
        if (idx >= 0) idx else 0
    }

    val accentColor = uiState.selectedCategory?.color() ?: MaterialTheme.colorScheme.primary

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
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
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
            // Category Tab Row matching DexScreen
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
                categories.forEachIndexed { index, (category, label) ->
                    val isSelected = selectedTabIndex == index
                    val count = uiState.categoryCounts[category] ?: 0
                    Tab(
                        selected = isSelected,
                        onClick = { viewModel.selectCategory(category) },
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

            if (overview.totalCount == 0 && !uiState.isLoading) {
                EmptyState(
                    message = if (uiState.selectedCategory != null) {
                        "No ${uiState.selectedCategory?.displayName?.lowercase()} in library yet"
                    } else {
                        "No media in library yet"
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 64.dp)
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // 1. KPI Overview (3-metric row flat on canvas)
                    item(key = "hero_overview") {
                        HeroOverviewSection(
                            overview = overview,
                            accentColor = accentColor
                        )
                    }

                    item(key = "divider_1") {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    }

                    // 2. Status Breakdown (Continuous ribbon + horizontal wrapping capsules)
                    item(key = "status_breakdown") {
                        StatusBreakdownSection(
                            overview = overview,
                            statusBreakdown = uiState.statusBreakdown,
                            accentColor = accentColor
                        )
                    }

                    item(key = "divider_2") {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    }

                    // 3. Ratings (Score distribution histogram)
                    item(key = "ratings") {
                        RatingsSection(
                            overview = overview,
                            ratingDistribution = uiState.ratingDistribution,
                            accentColor = accentColor
                        )
                    }

                    item(key = "divider_3") {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    }

                    // 4. Genres (Weighted chip cloud with expand/collapse)
                    item(key = "genre_cloud") {
                        StatsChipCloud(
                            title = "Top genres",
                            items = uiState.genreBars,
                            accentColor = accentColor
                        )
                    }

                    item(key = "divider_4") {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    }

                    // 5. Tags (Weighted chip cloud with expand/collapse)
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
}

@Composable
private fun HeroOverviewSection(
    overview: StatsOverviewData,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        // Metric 1: In Library
        KpiMetric(
            title = "In library",
            value = "${overview.totalCount}",
            subtitle = if (overview.totalProgressUnits > 0) {
                "${overview.totalProgressUnits} ${overview.progressUnitLabel.lowercase()}"
            } else null,
            modifier = Modifier.weight(1f)
        )

        // Metric 2: Duration Logged
        KpiMetric(
            title = "Logged",
            value = overview.durationText,
            subtitle = if (overview.durationText != "—") overview.durationLabel else null,
            modifier = Modifier.weight(1f)
        )

        // Metric 3: Completed
        val completedPct = if (overview.totalCount > 0) {
            (overview.completedCount.toFloat() / overview.totalCount * 100f).roundToInt()
        } else 0
        KpiMetric(
            title = "Completed",
            value = "${overview.completedCount}",
            subtitle = if (overview.totalCount > 0) "$completedPct% finished" else null,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun KpiMetric(
    title: String,
    value: String,
    subtitle: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            letterSpacing = 0.5.sp
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusBreakdownSection(
    overview: StatsOverviewData,
    statusBreakdown: List<StatusSegmentData>,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Status",
            style = MaterialTheme.typography.titleMedium,
            color = accentColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )

        StatusSegmentedBar(segments = statusBreakdown)

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val statuses = listOf(
                Triple(overview.inProgressLabel, overview.inProgressCount, StatusInProgress),
                Triple(overview.planToLabel, overview.planToCount, StatusPlanTo),
                Triple("Completed", overview.completedCount, StatusCompleted),
                Triple("On Hold", overview.onHoldCount, StatusOnHold),
                Triple("Dropped", overview.droppedCount, StatusDropped)
            )

            statuses.forEach { (label, count, color) ->
                StatusCapsule(
                    label = label,
                    count = count,
                    totalCount = overview.totalCount,
                    color = color
                )
            }

            if (overview.rereadingCount > 0) {
                StatusCapsule(
                    label = overview.rereadingLabel,
                    count = overview.rereadingCount,
                    totalCount = overview.totalCount,
                    color = accentColor,
                    icon = Icons.Default.Replay
                )
            }
        }
    }
}

@Composable
private fun StatusCapsule(
    label: String,
    count: Int,
    totalCount: Int,
    color: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val isZero = count == 0
    val pct = if (totalCount > 0 && count > 0) {
        (count.toFloat() / totalCount * 100f).roundToInt()
    } else null

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(
            alpha = if (isZero) 0.2f else 0.45f
        ),
        border = BorderStroke(
            1.dp,
            if (isZero) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
            else color.copy(alpha = 0.4f)
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isZero) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else color,
                    modifier = Modifier.size(12.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (isZero) color.copy(alpha = 0.3f) else color)
                )
            }

            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = if (isZero) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                       else MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = if (pct != null && pct > 0) "$count ($pct%)" else "$count",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isZero) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                       else color
            )
        }
    }
}

@Composable
private fun RatingsSection(
    overview: StatsOverviewData,
    ratingDistribution: Map<Int, Int>,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Ratings",
                style = MaterialTheme.typography.titleMedium,
                color = accentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (overview.ratedCount > 0) {
                Text(
                    text = "${overview.ratedCount} / ${overview.totalCount} rated",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        val hasRating = overview.meanRating != null && overview.ratedCount > 0
        if (!hasRating) {
            Text(
                text = "No ratings recorded",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left Column: Big Mean Rating Hero
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.width(84.dp)
                ) {
                    Text(
                        text = String.format(Locale.getDefault(), "%.2f", overview.meanRating),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        val mean = overview.meanRating
                        for (i in 1..5) {
                            val icon = when {
                                mean >= i -> Icons.Default.Star
                                mean >= i - 0.5 -> Icons.AutoMirrored.Filled.StarHalf
                                else -> Icons.Default.StarBorder
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = StarGold,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                    Text(
                        text = "out of 5",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // Right Column: 5★ down to 1★ histogram bars
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val maxBucketCount = ratingDistribution.values.maxOrNull()?.coerceAtLeast(1) ?: 1
                    for (star in 5 downTo 1) {
                        val count = ratingDistribution[star] ?: 0
                        val barFraction = count.toFloat() / maxBucketCount

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "$star★",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(20.dp)
                            )

                            // Linear bar
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                if (barFraction > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(fraction = barFraction)
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(getRatingColor(star.toDouble()))
                                    )
                                }
                            }

                            Text(
                                text = "$count",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (count > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.width(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}