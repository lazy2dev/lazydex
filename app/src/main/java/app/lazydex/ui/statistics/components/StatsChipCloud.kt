package app.lazydex.ui.statistics.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lazydex.ui.components.Pill
import app.lazydex.ui.statistics.StatBarItem

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatsChipCloud(
    title: String,
    items: List<StatBarItem>,
    modifier: Modifier = Modifier,
    accentColor: Color? = null
) {
    var isExpanded by remember(items) { mutableStateOf(false) }
    val maxPreview = 12
    val visibleItems = if (isExpanded) items else items.take(maxPreview)
    val resolvedColor = accentColor ?: MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = resolvedColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (items.isNotEmpty()) {
                Pill(
                    text = "${items.size}",
                    fontSize = 10.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (items.isEmpty()) {
            Text(
                text = "No ${title.lowercase()} recorded",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                visibleItems.forEach { item ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Pill(
                                text = "${item.count}",
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            if (items.size > maxPreview) {
                Spacer(modifier = Modifier.height(6.dp))
                TextButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .defaultMinSize(minHeight = 44.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (isExpanded) "Show less" else "Show all (${items.size})",
                            style = MaterialTheme.typography.labelLarge,
                            color = resolvedColor
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = resolvedColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

