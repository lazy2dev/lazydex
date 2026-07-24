package app.lazydex.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lazydex.data.anilist.ALMedia
import app.lazydex.domain.model.UserStatus
import coil3.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerBottomSheet(
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    isBound: Boolean,
    anilistListEntryId: Long?,
    title: String,
    currentProgress: String,
    totalItems: String,
    progressVolumes: String,
    userStatus: UserStatus,
    rating: Int?,
    isPrivate: Boolean,
    sourceUrl: String?,
    isSearching: Boolean,
    searchQuery: String,
    searchResults: List<ALMedia>,
    onSearchQueryChange: (String) -> Unit,
    onPerformSearch: () -> Unit,
    onBindMedia: (ALMedia) -> Unit,
    onUnbindMedia: () -> Unit,
    onStatusChange: (UserStatus) -> Unit,
    onProgressChange: (String) -> Unit,
    onProgressVolumesChange: (String) -> Unit,
    onPrivateChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showStatusPicker by remember { mutableStateOf(false) }
    var showProgressPicker by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row (Exact Komikku TrackInfoItem style)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF02A9FF)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "AL",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = title.ifEmpty { "AniList Tracker" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (isBound) {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            if (!sourceUrl.isNullOrBlank()) {
                                DropdownMenuItem(
                                    text = { Text("Open in AniList") },
                                    leadingIcon = { Icon(Icons.Default.OpenInNew, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(sourceUrl))
                                            context.startActivity(intent)
                                        } catch (_: Exception) {}
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(if (isPrivate) "Make Public" else "Make Private") },
                                onClick = {
                                    showMenu = false
                                    onPrivateChange(!isPrivate)
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Unbind Tracker", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    onUnbindMedia()
                                }
                            )
                        }
                    }
                }

                IconButton(onClick = onDismissRequest) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            if (!isBound) {
                // UNBOUND STATE: Search & Bind
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Search AniList to bind this entry:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search title...") },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = onPerformSearch) {
                                Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
                            }
                        }
                    )

                    if (isSearching) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        }
                    } else if (searchResults.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(searchResults, key = { it.id }) { media ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onBindMedia(media) },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        media.coverImage?.large?.let { coverUrl ->
                                            AsyncImage(
                                                model = coverUrl,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(44.dp, 60.dp)
                                                    .clip(RoundedCornerShape(6.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = media.title?.userPreferred ?: media.title?.romaji ?: "Unknown",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "${media.format ?: "Unknown"} • ${media.chapters ?: media.volumes ?: "?"} items",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Button(
                                            onClick = { onBindMedia(media) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF02A9FF)
                                            )
                                        ) {
                                            Text("Bind")
                                        }
                                    }
                                }
                            }
                        }
                    } else if (searchQuery.isNotBlank()) {
                        Text(
                            text = "No matching AniList entries found.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                }
            } else {
                // BOUND STATE: Komikku 2-Grid Interactive Card Container (TrackDetailsItem)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(6.dp)
                ) {
                    Column {
                        // Grid Row 1: Status | Chapters | Rating
                        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                            // Cell 1: Status
                            Box(modifier = Modifier.weight(1f)) {
                                TrackGridCell(
                                    text = userStatus.displayName,
                                    label = "Status",
                                    onClick = { showStatusPicker = true }
                                )
                                DropdownMenu(
                                    expanded = showStatusPicker,
                                    onDismissRequest = { showStatusPicker = false }
                                ) {
                                    UserStatus.entries.forEach { status ->
                                        DropdownMenuItem(
                                            text = { Text(status.displayName) },
                                            onClick = {
                                                onStatusChange(status)
                                                showStatusPicker = false
                                            }
                                        )
                                    }
                                }
                            }

                            VerticalDivider()

                            // Cell 2: Chapters Progress
                            TrackGridCell(
                                modifier = Modifier.weight(1f),
                                text = "Ch. $currentProgress / ${totalItems.ifEmpty { "?" }}",
                                label = "Progress",
                                onClick = { showProgressPicker = true }
                            )

                            VerticalDivider()

                            // Cell 3: Score / Rating
                            TrackGridCell(
                                modifier = Modifier.weight(1f),
                                text = rating?.let { "${it / 20.0} / 5.0" } ?: "Unrated",
                                label = "Score",
                                onClick = {}
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        // Grid Row 2: Volume Progress & Options
                        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                            TrackGridCell(
                                modifier = Modifier.weight(1f),
                                text = if (progressVolumes.isNotBlank()) "Vol. $progressVolumes" else "Vol. 0",
                                label = "Volumes",
                                onClick = { showProgressPicker = true }
                            )
                            VerticalDivider()
                            TrackGridCell(
                                modifier = Modifier.weight(1f),
                                text = if (isPrivate) "Private 🔒" else "Public 🌐",
                                label = "Visibility",
                                onClick = { onPrivateChange(!isPrivate) }
                            )
                        }
                    }
                }

                if (showProgressPicker) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Update Progress", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = currentProgress,
                                    onValueChange = onProgressChange,
                                    label = { Text("Chapter Progress", fontSize = 11.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = progressVolumes,
                                    onValueChange = onProgressVolumesChange,
                                    label = { Text("Volume Progress", fontSize = 11.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { showProgressPicker = false },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Done")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TrackGridCell(
    text: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clickable(onClick = onClick)
            .fillMaxHeight()
            .padding(vertical = 10.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}
