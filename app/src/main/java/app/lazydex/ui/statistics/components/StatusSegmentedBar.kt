package app.lazydex.ui.statistics.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lazydex.ui.statistics.StatusSegmentData

@Composable
fun StatusSegmentedBar(
    segments: List<StatusSegmentData>,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    segmentGap: Dp = 2.dp
) {
    val activeSegments = remember(segments) {
        segments.filter { it.count > 0 }
    }
    val totalCount = remember(activeSegments) {
        activeSegments.sumOf { it.count }
    }

    if (totalCount > 0) {
        val cornerRadius = height / 2
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(cornerRadius))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(segmentGap)
            ) {
                activeSegments.forEach { segment ->
                    val weight = segment.count.toFloat() / totalCount
                    if (weight > 0f) {
                        Box(
                            modifier = Modifier
                                .weight(weight)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.dp))
                                .background(segment.color)
                        )
                    }
                }
            }
        }
    }
}
