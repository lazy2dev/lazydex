package app.lazydex.ui.statistics.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lazydex.ui.components.Pill
import app.lazydex.ui.statistics.StatBarItem

@Composable
fun StatsBarChart(
    title: String,
    items: List<StatBarItem>,
    modifier: Modifier = Modifier,
    accentColor: Color? = null
) {
    var isExpanded by remember(items) { mutableStateOf(false) }
    val maxPreview = 8
    val visibleItems = if (isExpanded) items else items.take(maxPreview)
    val resolvedColor = accentColor ?: MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        // Flat section header matching Dex/Appearance screen
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "No ${title.lowercase()} recorded",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                visibleItems.forEach { item ->
                    StatBarRow(
                        item = item,
                        defaultColor = resolvedColor
                    )
                }
            }

            if (items.size > maxPreview) {
                Spacer(modifier = Modifier.height(6.dp))
                TextButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (isExpanded) "Show less" else "Show all",
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

@Composable
fun StatBarRow(
    item: StatBarItem,
    defaultColor: Color,
    modifier: Modifier = Modifier
) {
    val barColor = item.color ?: defaultColor
    val animatedFraction by animateFloatAsState(
        targetValue = item.barFraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "barFill_${item.name}"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${item.count}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            if (animatedFraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedFraction)
                        .fillMaxHeight()
                        .background(barColor)
                )
            }
        }
    }
}
