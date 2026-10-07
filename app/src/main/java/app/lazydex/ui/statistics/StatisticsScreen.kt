package app.lazydex.ui.statistics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.StarRate
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lazydex.domain.model.MediaCategory
import app.lazydex.ui.components.Pill
import app.lazydex.ui.components.color
import app.lazydex.ui.statistics.components.StatsBarChart
import org.koin.androidx.compose.koinViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    modifier: Modifier = Modifier,
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Statistics",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
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
            val accentColor = uiState.selectedCategory?.color() ?: MaterialTheme.colorScheme.primary

            // Category Tab Row matching Komikku/DexScreen
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Pill(
                                    text = "$count",
                                    fontSize = 11.sp
                                )
                            }
                        }
                    )
                }
            }

            // Scrollable Content formatted into Komikku's card sections
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                val overview = uiState.overview

                // Section 1: Overview
                item(key = "overview") {
                    KomikkuSectionCard(
                        title = "Overview",
                        titleColor = accentColor
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            KomikkuStatCell(
                                value = "${overview.totalCount}",
                                label = "In library",
                                icon = Icons.AutoMirrored.Filled.LibraryBooks,
                                iconTint = accentColor,
                                modifier = Modifier.weight(1f)
                            )
                            KomikkuStatCell(
                                value = "${overview.totalProgressUnits}",
                                label = overview.progressUnitLabel,
                                icon = Icons.AutoMirrored.Filled.TrendingUp,
                                iconTint = accentColor,
                                modifier = Modifier.weight(1f)
                            )
                            KomikkuStatCell(
                                value = overview.durationText,
                                label = overview.durationLabel,
                                icon = Icons.Default.DateRange,
                                iconTint = accentColor,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Section 2: Entries / Statuses (matches Edit Entry status grid)
                item(key = "entries") {
                    KomikkuSectionCard(
                        title = "Entries",
                        titleColor = accentColor
                    ) {
                        val inProgressIcon = when (uiState.selectedCategory) {
                            MediaCategory.NOVEL, MediaCategory.MANGA -> Icons.Default.LocalLibrary
                            MediaCategory.ANIME, MediaCategory.MOVIE, MediaCategory.TV -> Icons.Default.SmartDisplay
                            MediaCategory.GAME -> Icons.Default.SportsEsports
                            null -> Icons.Default.HourglassTop
                        }

                        // Row 1: In Progress, Plan To, Completed
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            KomikkuStatCell(
                                value = "${overview.inProgressCount}",
                                label = overview.inProgressLabel,
                                icon = inProgressIcon,
                                iconTint = accentColor,
                                modifier = Modifier.weight(1f)
                            )
                            KomikkuStatCell(
                                value = "${overview.planToCount}",
                                label = overview.planToLabel,
                                icon = Icons.Default.BookmarkBorder,
                                iconTint = accentColor,
                                modifier = Modifier.weight(1f)
                            )
                            KomikkuStatCell(
                                value = "${overview.completedCount}",
                                label = "Completed",
                                icon = Icons.Default.TaskAlt,
                                iconTint = accentColor,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Row 2: On Hold, Dropped, Rereading
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            KomikkuStatCell(
                                value = "${overview.onHoldCount}",
                                label = "On Hold",
                                icon = Icons.Default.PauseCircle,
                                iconTint = accentColor,
                                modifier = Modifier.weight(1f)
                            )
                            KomikkuStatCell(
                                value = "${overview.droppedCount}",
                                label = "Dropped",
                                icon = Icons.Default.Cancel,
                                iconTint = accentColor,
                                modifier = Modifier.weight(1f)
                            )
                            KomikkuStatCell(
                                value = "${overview.rereadingCount}",
                                label = overview.rereadingLabel,
                                icon = Icons.Default.Replay,
                                iconTint = accentColor,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Section 3: Ratings
                item(key = "ratings") {
                    KomikkuSectionCard(
                        title = "Ratings",
                        titleColor = accentColor
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            KomikkuStatCell(
                                value = "${overview.ratedCount}",
                                label = "Rated",
                                icon = Icons.Default.Star,
                                iconTint = accentColor,
                                modifier = Modifier.weight(1f)
                            )
                            val scoreText = if (overview.meanRating != null && overview.ratedCount > 0) {
                                String.format(Locale.getDefault(), "%.2f", overview.meanRating)
                            } else {
                                "—"
                            }
                            KomikkuStatCell(
                                value = scoreText,
                                label = "Mean score",
                                icon = Icons.Default.StarRate,
                                iconTint = accentColor,
                                modifier = Modifier.weight(1f)
                            )
                            KomikkuStatCell(
                                value = "${overview.unratedCount}",
                                label = "Unrated",
                                icon = Icons.Default.StarOutline,
                                iconTint = accentColor,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Section 4: Genres Graph
                item(key = "genre_graph") {
                    StatsBarChart(
                        title = "Genres",
                        items = uiState.genreBars,
                        accentColor = accentColor
                    )
                }

                // Section 5: Tags Graph
                item(key = "tag_graph") {
                    StatsBarChart(
                        title = "Tags",
                        items = uiState.tagBars,
                        accentColor = accentColor
                    )
                }
            }
        }
    }
}

@Composable
private fun KomikkuSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    titleColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = titleColor,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                content = content
            )
        }
    }
}

@Composable
private fun KomikkuStatCell(
    value: String,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.primary
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(6.dp))
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconTint,
            modifier = Modifier.size(20.dp)
        )
    }
}
