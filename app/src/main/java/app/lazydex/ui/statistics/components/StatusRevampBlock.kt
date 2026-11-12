package app.lazydex.ui.statistics.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lazydex.domain.model.MediaType
import app.lazydex.ui.components.color
import app.lazydex.ui.components.icon
import app.lazydex.ui.statistics.TypeStatusStack
import app.lazydex.ui.statistics.StatusSegmentData
import kotlin.math.roundToInt

@Composable
fun StatusRevampBlock(
    segments: List<StatusSegmentData>,
    typeStacks: List<TypeStatusStack>,
    onSelectType: (MediaType) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeSegments = segments.filter { it.count > 0 }
    if (activeSegments.isEmpty()) return

    val hairlineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatusSegmentedBar(
                    segments = activeSegments,
                    height = 10.dp,
                    segmentGap = 2.dp
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    activeSegments.forEach { segment ->
                        StatusBreakdownRow(segment = segment)
                    }
                }
            }

            if (typeStacks.isNotEmpty()) {
                HorizontalDivider(color = hairlineColor)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 6.dp)
                ) {
                    Text(
                        text = "By type",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )

                    typeStacks.forEach { stack ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectType(stack.mediaType) }
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = stack.mediaType.icon(),
                                contentDescription = null,
                                tint = stack.mediaType.color(),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stack.mediaType.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.width(64.dp)
                            )
                            StatusSegmentedBar(
                                segments = stack.segments,
                                height = 6.dp,
                                segmentGap = 1.5.dp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${stack.totalCount}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(28.dp)
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = "Filter by ${stack.mediaType.displayName}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBreakdownRow(segment: StatusSegmentData) {
    val pct = segment.percentage.roundToInt()
    val fillFraction = (segment.percentage / 100f).coerceIn(0.02f, 1f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(segment.color)
        )
        Text(
            text = segment.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(104.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fillFraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(segment.color)
            )
        }
        Text(
            text = "${segment.count}",
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.width(28.dp)
        )
        Text(
            text = "$pct%",
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(34.dp)
        )
    }
}
