package app.lazydex.ui.statistics.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lazydex.ui.statistics.ActivityHeatmapDay
import app.lazydex.ui.statistics.ActivityHeatmapState

@Composable
fun ActivityHeatmapBlock(
    heatmap: ActivityHeatmapState,
    accentColor: Color,
    selectedDay: ActivityHeatmapDay?,
    onSelectDay: (ActivityHeatmapDay?) -> Unit,
    modifier: Modifier = Modifier
) {
    val hairlineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
    val monthLabelByWeek = remember(heatmap.monthSpans) {
        heatmap.monthSpans.associate { it.weekIndex to it.label }
    }

    val cellSizeDp = 12.dp
    val cellGapDp = 3.dp
    val cellStepDp = cellSizeDp + cellGapDp
    val gridHeightDp = cellSizeDp * 7 + cellGapDp * 6

    val emptyCellColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val levelColors = remember(accentColor, emptyCellColor) {
        listOf(
            emptyCellColor,
            accentColor.copy(alpha = 0.25f),
            accentColor.copy(alpha = 0.50f),
            accentColor.copy(alpha = 0.75f),
            accentColor
        )
    }
    val selectedBorderColor = MaterialTheme.colorScheme.onSurface

    val listState = rememberLazyListState()
    LaunchedEffect(heatmap.weeks.size) {
        if (heatmap.weeks.isNotEmpty()) {
            listState.scrollToItem((heatmap.weeks.size - 1).coerceAtLeast(0))
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 3-column Komikku summary row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HeatmapStatCell(
                    value = "${heatmap.totalActivities}",
                    label = "Updates",
                    sublabel = null,
                    modifier = Modifier.weight(1f)
                )
                HeatmapStatCell(
                    value = "${heatmap.currentStreak}d",
                    label = "Current streak",
                    sublabel = if (heatmap.longestStreak > 0) "Longest: ${heatmap.longestStreak}d" else null,
                    modifier = Modifier.weight(1f)
                )
                HeatmapStatCell(
                    value = heatmap.peakWeekdayName ?: "—",
                    label = "Peak day",
                    sublabel = if (heatmap.peakWeekdayName != null) "${heatmap.peakWeekdayPercentage}% of updates" else null,
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(color = hairlineColor)

            if (!heatmap.hasActivityData || heatmap.weeks.isEmpty()) {
                Text(
                    text = "No logged progress updates in this period",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    textAlign = TextAlign.Center
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val density = LocalDensity.current
                    val stepPx = with(density) { cellStepDp.toPx() }
                    val sizePx = with(density) { cellSizeDp.toPx() }
                    val cornerPx = with(density) { 3.dp.toPx() }

                    Row(modifier = Modifier.fillMaxWidth()) {
                        // Sticky left weekday axis (Mon, Wed, Fri at rows 0, 2, 4)
                        Column(
                            modifier = Modifier.width(20.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Spacer(modifier = Modifier.height(18.dp)) // matches month header height
                            Box(modifier = Modifier.height(gridHeightDp)) {
                                listOf(0 to "M", 2 to "W", 4 to "F").forEach { (rowIdx, label) ->
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = cellStepDp * rowIdx)
                                    )
                                }
                            }
                        }

                        LazyRow(
                            state = listState,
                            horizontalArrangement = Arrangement.spacedBy(cellGapDp),
                            modifier = Modifier.weight(1f)
                        ) {
                            itemsIndexed(heatmap.weeks) { weekIdx, weekDays ->
                                Column(
                                    horizontalAlignment = Alignment.Start,
                                    modifier = Modifier.width(cellSizeDp)
                                ) {
                                    Box(modifier = Modifier.height(18.dp)) {
                                        val monthText = monthLabelByWeek[weekIdx]
                                        if (monthText != null) {
                                            Text(
                                                text = monthText,
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Visible,
                                                modifier = Modifier.wrapContentWidth(
                                                    align = Alignment.Start,
                                                    unbounded = true
                                                )
                                            )
                                        }
                                    }

                                    Canvas(
                                        modifier = Modifier
                                            .width(cellSizeDp)
                                            .height(gridHeightDp)
                                            .pointerInput(weekDays, selectedDay) {
                                                detectTapGestures { offset ->
                                                    val dayIdx = (offset.y / stepPx).toInt().coerceIn(0, 6)
                                                    val tapped = weekDays.getOrNull(dayIdx)
                                                    if (tapped != null) {
                                                        onSelectDay(if (selectedDay?.epochDay == tapped.epochDay) null else tapped)
                                                    }
                                                }
                                            }
                                    ) {
                                        weekDays.forEachIndexed { dayIdx, day ->
                                            if (day != null) {
                                                val top = dayIdx * stepPx
                                                val color = levelColors[day.level.coerceIn(0, 4)]
                                                drawRoundRect(
                                                    color = color,
                                                    topLeft = Offset(0f, top),
                                                    size = Size(sizePx, sizePx),
                                                    cornerRadius = CornerRadius(cornerPx, cornerPx)
                                                )
                                                if (selectedDay?.epochDay == day.epochDay) {
                                                    drawRoundRect(
                                                        color = selectedBorderColor,
                                                        topLeft = Offset(0f, top),
                                                        size = Size(sizePx, sizePx),
                                                        cornerRadius = CornerRadius(cornerPx, cornerPx),
                                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Legend footer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val selectedSummary = if (selectedDay != null) {
                            "${selectedDay.dateLabel}: ${selectedDay.count} ${if (selectedDay.count == 1) "update" else "updates"}"
                        } else {
                            "Tap a day to inspect"
                        }
                        Text(
                            text = selectedSummary,
                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = "Less",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            levelColors.forEach { shade ->
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(shade)
                                )
                            }
                            Text(
                                text = "More",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeatmapStatCell(
    value: String,
    label: String,
    sublabel: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
