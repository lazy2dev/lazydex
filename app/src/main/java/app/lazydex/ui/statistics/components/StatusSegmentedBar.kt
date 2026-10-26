package app.lazydex.ui.statistics.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import app.lazydex.ui.statistics.StatusSegmentData

@Composable
fun StatusSegmentedBar(
    segments: List<StatusSegmentData>,
    modifier: Modifier = Modifier
) {
    val activeSegments = remember(segments) {
        segments.filter { it.count > 0 }
    }
    val totalCount = remember(activeSegments) {
        activeSegments.sumOf { it.count }
    }

    if (totalCount > 0) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                activeSegments.forEach { segment ->
                    val weight = segment.count.toFloat() / totalCount
                    if (weight > 0f) {
                        Box(
                            modifier = Modifier
                                .weight(weight)
                                .fillMaxHeight()
                                .background(segment.color)
                        )
                    }
                }
            }
        }
    }
}

