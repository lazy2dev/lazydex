package app.lazydex.ui.statistics.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StarHalf
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.lazydex.domain.model.MediaType
import app.lazydex.ui.statistics.RatingBucketRow
import app.lazydex.ui.statistics.StatsOverviewData
import app.lazydex.ui.theme.getRatingColor
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TypeStackedRatings(
    overview: StatsOverviewData,
    buckets: List<RatingBucketRow>,
    selectedType: MediaType?,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val meanRating = overview.meanRating
    val starTint = if (meanRating != null) getRatingColor(meanRating) else accentColor
    val hairlineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)

    val legendTypes = remember(buckets, selectedType) {
        if (selectedType != null) {
            emptyList()
        } else {
            buckets.flatMap { it.typeSlices }
                .distinctBy { it.mediaType }
                .sortedBy { it.mediaType.ordinal }
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (overview.ratedCount == 0) {
                Text(
                    text = "Rate entries to see your score distribution",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Left column: mean score & stars
                    Column(
                        modifier = Modifier.width(84.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (meanRating != null) String.format(Locale.US, "%.2f", meanRating) else "—",
                            style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                            val ratingVal = meanRating ?: 0.0
                            for (i in 1..5) {
                                val icon = when {
                                    ratingVal >= i - 0.25 -> Icons.Filled.Star
                                    ratingVal >= i - 0.75 -> Icons.AutoMirrored.Filled.StarHalf
                                    else -> Icons.Filled.StarBorder
                                }
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (ratingVal >= i - 0.75) starTint else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Text(
                            text = "out of 5",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Right column: 5 stacked histogram rows
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        buckets.forEach { bucket ->
                            RatingHistogramRow(row = bucket)
                        }
                    }
                }

                if (legendTypes.isNotEmpty()) {
                    HorizontalDivider(color = hairlineColor)
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        legendTypes.forEach { slice ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(slice.color)
                                )
                                Text(
                                    text = slice.mediaType.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RatingHistogramRow(row: RatingBucketRow) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = row.bucketLabel,
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(46.dp)
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            if (row.totalCount > 0 && row.barFraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(row.barFraction.coerceIn(0.03f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                ) {
                    if (row.typeSlices.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            row.typeSlices.forEach { slice ->
                                val sliceWeight = slice.count.toFloat() / row.totalCount
                                if (sliceWeight > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .weight(sliceWeight)
                                            .fillMaxHeight()
                                            .background(slice.color)
                                    )
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(row.color)
                        )
                    }
                }
            }
        }

        Text(
            text = "${row.totalCount}",
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(24.dp)
        )
    }
}
