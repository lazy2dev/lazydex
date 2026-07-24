package app.lazydex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lazydex.data.anilist.AnilistSyncReport
import app.lazydex.data.anilist.SyncReportItem

@Composable
fun SyncReportCard(
    report: AnilistSyncReport,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "MALSync Report Summary",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close report"
                    )
                }
            }

            // Overview Metric Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricChip(
                    label = "Scanned",
                    value = report.totalScanned.toString(),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.weight(1f)
                )
                MetricChip(
                    label = "Imported",
                    value = report.importedCount.toString(),
                    color = Color(0xFF1B5E20).copy(alpha = 0.3f),
                    modifier = Modifier.weight(1f)
                )
                MetricChip(
                    label = "Updated",
                    value = report.updatedCount.toString(),
                    color = Color(0xFF0D47A1).copy(alpha = 0.3f),
                    modifier = Modifier.weight(1f)
                )
            }

            if (report.items.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    text = "Sync Changes Breakdown (${report.items.size} items)",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(report.items) { item ->
                        ReportItemRow(item = item)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricChip(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                text = label,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReportItemRow(item: SyncReportItem) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CoverImage(
                coverImagePath = "",
                coverImageUrl = item.coverUrl,
                title = item.title,
                modifier = Modifier.size(width = 40.dp, height = 55.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                if (item.isNewImport) {
                    Text(
                        text = "New Import • Status: ${item.newStatus ?: "PLAN_TO"} • Progress: ${item.newProgress ?: 0}",
                        fontSize = 10.sp,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (item.oldProgress != item.newProgress) {
                            Text(
                                text = "Progress: ${item.oldProgress ?: 0} → ${item.newProgress ?: 0}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        if (item.oldStatus != item.newStatus) {
                            Text(
                                text = "Status: ${item.oldStatus} → ${item.newStatus}",
                                fontSize = 10.sp,
                                color = Color(0xFFFFA726),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
