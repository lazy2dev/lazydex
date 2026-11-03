package app.lazydex.ui.details.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun formatRelativeTime(millis: Long): String {
    if (millis <= 0) return "Never"
    val now = System.currentTimeMillis()
    val diff = (now - millis).coerceAtLeast(0L)
    val minutes = diff / (1000 * 60)
    val hours = minutes / 60
    val days = hours / 24
    val weeks = days / 7
    val months = days / 30

    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days == 1L -> "Yesterday"
        days < 7 -> "${days}d ago"
        weeks < 5 -> "${weeks}w ago"
        months < 12 -> "${months}mo ago"
        else -> "${days / 365}y ago"
    }
}

@Composable
fun MediaActionRow(
    inLibrary: Boolean,
    onLibraryClick: () -> Unit,
    lastUpdatedMillis: Long,
    onUpdatedClick: () -> Unit,
    isSynced: Boolean,
    onSyncClick: () -> Unit,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val defaultInactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    Row(modifier = modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp)) {
        MediaActionButton(
            title = if (inLibrary) "In library" else "Add to library",
            icon = if (inLibrary) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            color = if (inLibrary) MaterialTheme.colorScheme.primary else defaultInactiveColor,
            onClick = onLibraryClick,
        )
        MediaActionButton(
            title = formatRelativeTime(lastUpdatedMillis),
            icon = Icons.Default.HourglassEmpty,
            color = MaterialTheme.colorScheme.primary,
            onClick = onUpdatedClick,
        )
        MediaActionButton(
            title = if (isSynced) "Synced" else "Sync",
            icon = if (isSynced) Icons.Outlined.Done else Icons.Outlined.Sync,
            color = if (inLibrary) {
                if (isSynced) MaterialTheme.colorScheme.primary else defaultInactiveColor
            } else defaultInactiveColor.copy(alpha = 0.25f),
            enabled = inLibrary,
            onClick = onSyncClick,
        )
        MediaActionButton(
            title = "Edit",
            icon = Icons.Default.Edit,
            color = MaterialTheme.colorScheme.primary,
            onClick = onEditClick,
        )
    }
}

@Composable
private fun RowScope.MediaActionButton(
    title: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .weight(1f)
            .clip(MaterialTheme.shapes.small)
            .clickable(
                enabled = enabled,
                onClick = onClick,
            )
            .padding(vertical = 6.dp, horizontal = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                color = color,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
