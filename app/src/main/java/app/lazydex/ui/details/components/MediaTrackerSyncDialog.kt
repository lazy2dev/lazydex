package app.lazydex.ui.details.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import app.lazydex.data.tracker.anilist.dto.AniListMedia
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lazydex.domain.model.MediaCategory
import app.lazydex.domain.model.UserStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaTrackerSyncDialog(
    isLoggedIn: Boolean,
    anilistMediaId: Long?,
    anilistSyncStatus: String?,
    anilistLastSynced: Long?,
    isSyncing: Boolean,
    category: MediaCategory,
    title: String,
    currentProgress: String,
    totalItems: String,
    userStatus: UserStatus,
    rating: Double?,
    startDate: Long?,
    endDate: Long?,
    onSyncNow: () -> Unit,
    onLinkMediaId: (Long) -> Unit,
    onUnlink: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissRequest: () -> Unit,
    searchResults: List<AniListMedia> = emptyList(),
    isSearching: Boolean = false,
    onSearch: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    inLibrary: Boolean = true,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    val mediaType = if (category == MediaCategory.ANIME || category == MediaCategory.TV || category == MediaCategory.MOVIE) "anime" else "manga"
    val anilistUrl = anilistMediaId?.let { "https://anilist.co/$mediaType/$it" }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // AniList brand badge
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF02A9FF)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "AL",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AniList",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = when {
                            !isLoggedIn -> "Not connected"
                            anilistMediaId != null -> title.ifBlank { "Linked entry #$anilistMediaId" }
                            else -> "Not linked"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (isLoggedIn && anilistMediaId != null) {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Tracker Options")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            if (anilistUrl != null) {
                                DropdownMenuItem(
                                    text = { Text("Open in browser") },
                                    leadingIcon = { Icon(Icons.Default.OpenInBrowser, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        try {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(anilistUrl)))
                                        } catch (_: Exception) {}
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Copy link") },
                                    leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        clipboard?.setPrimaryClip(ClipData.newPlainText("AniList Link", anilistUrl))
                                        Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Unlink", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    menuExpanded = false
                                    onUnlink()
                                },
                            )
                        }
                    }
                }
            }

            // Body States
            when {
                !inLibrary -> {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "Add this media to your library first to track and synchronize with AniList.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Button(
                                onClick = onDismissRequest,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("OK")
                            }
                        }
                    }
                }

                !isLoggedIn -> {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "AniList account is not connected. Sign in via Settings to synchronize your reading progress, status, and scores.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Button(
                                onClick = {
                                    onDismissRequest()
                                    onOpenSettings()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Open Tracking Settings")
                            }
                        }
                    }
                }

                anilistMediaId == null -> {
                    var searchQuery by remember(title) { mutableStateOf(title) }
                    var showManualId by remember { mutableStateOf(false) }
                    var manualIdText by remember { mutableStateOf("") }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = "Link to AniList",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )

                            // Search bar
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    label = { Text("Search title") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                                Button(
                                    onClick = { onSearch(searchQuery) },
                                    enabled = searchQuery.isNotBlank() && !isSearching,
                                ) {
                                    if (isSearching) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                        )
                                    } else {
                                        Icon(Icons.Default.Search, contentDescription = "Search")
                                    }
                                }
                            }

                            // Search Results
                            if (searchResults.isNotEmpty()) {
                                Text(
                                    text = "Select matching entry:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    searchResults.take(5).forEach { result ->
                                        Surface(
                                            color = MaterialTheme.colorScheme.surface,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onLinkMediaId(result.id.toLong()) },
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            ) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(LocalContext.current)
                                                        .data(result.coverImage?.best)
                                                        .crossfade(true)
                                                        .build(),
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .size(40.dp, 56.dp)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = result.title?.best ?: "Untitled",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                    val meta = listOfNotNull(
                                                        result.format,
                                                        result.startDate?.year?.toString(),
                                                        (result.chapters ?: result.episodes)?.let { "$it eps/ch" }
                                                    ).joinToString(" • ")
                                                    if (meta.isNotBlank()) {
                                                        Text(
                                                            text = meta,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                    }
                                                }
                                                Button(
                                                    onClick = { onLinkMediaId(result.id.toLong()) },
                                                    modifier = Modifier.height(36.dp),
                                                ) {
                                                    Text("Link")
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            // Manual ID option toggle
                            if (!showManualId) {
                                OutlinedButton(
                                    onClick = { showManualId = true },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Enter AniList ID manually")
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = manualIdText,
                                        onValueChange = { manualIdText = it.filter { char -> char.isDigit() } },
                                        label = { Text("AniList Media ID") },
                                        placeholder = { Text("e.g. 140101") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Button(
                                        onClick = {
                                            val id = manualIdText.toLongOrNull()
                                            if (id != null) onLinkMediaId(id)
                                        },
                                        enabled = manualIdText.isNotBlank(),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Icon(Icons.Default.CloudSync, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Link by ID")
                                    }
                                }
                            }
                        }
                    }
                }

                else -> {
                    // Linked State: Komikku 1:1 Tracking Matrix
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column {
                            // Row 1: Status, Progress, Score
                            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                                MatrixCell(
                                    label = "STATUS",
                                    value = userStatus.displayName,
                                    modifier = Modifier.weight(1f),
                                )
                                VerticalDivider()
                                MatrixCell(
                                    label = "PROGRESS",
                                    value = "$currentProgress / ${totalItems.ifBlank { "—" }}",
                                    modifier = Modifier.weight(1f),
                                )
                                VerticalDivider()
                                MatrixCell(
                                    label = "SCORE",
                                    value = if (rating != null) "$rating ★" else "—",
                                    modifier = Modifier.weight(1f),
                                )
                            }

                            HorizontalDivider()

                            // Row 2: Started, Finished
                            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                                MatrixCell(
                                    label = "STARTED",
                                    value = if (startDate != null) formatSyncDate(startDate) else "—",
                                    modifier = Modifier.weight(1f),
                                )
                                VerticalDivider()
                                MatrixCell(
                                    label = "FINISHED",
                                    value = if (endDate != null) formatSyncDate(endDate) else "—",
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }

                    // Sync status and Last Synced row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Sync: ${anilistSyncStatus ?: "SYNCED"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (anilistSyncStatus == "DIRTY_LOCAL") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = anilistLastSynced?.let { formatRelativeTime(it) } ?: "Never synced",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Push Sync Button
                    Button(
                        onClick = onSyncNow,
                        enabled = !isSyncing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Syncing...")
                        } else {
                            Icon(Icons.Default.CloudSync, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sync Now")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MatrixCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(vertical = 12.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun formatSyncDate(millis: Long): String {
    return SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(millis))
}

