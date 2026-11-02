package app.lazydex.ui.details.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun MediaActionRow(
    inLibrary: Boolean,
    onAddToLibraryClicked: () -> Unit,
    onCategoryClicked: () -> Unit,
    onTrackingClicked: () -> Unit,
    onWebViewClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val defaultInactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    Row(modifier = modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp)) {
        MediaActionButton(
            title = if (inLibrary) "In library" else "Add to library",
            icon = if (inLibrary) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            color = if (inLibrary) MaterialTheme.colorScheme.primary else defaultInactiveColor,
            onClick = onAddToLibraryClicked,
        )
        MediaActionButton(
            title = "Category",
            icon = Icons.Default.Folder,
            color = MaterialTheme.colorScheme.primary,
            onClick = onCategoryClicked,
        )
        MediaActionButton(
            title = "Tracking",
            icon = Icons.Outlined.Sync,
            color = if (inLibrary) MaterialTheme.colorScheme.primary else defaultInactiveColor,
            onClick = onTrackingClicked,
        )
        MediaActionButton(
            title = "WebView",
            icon = Icons.Outlined.Public,
            color = MaterialTheme.colorScheme.primary,
            onClick = onWebViewClicked,
        )
    }
}

@Composable
private fun RowScope.MediaActionButton(
    title: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.weight(1f),
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                color = color,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

