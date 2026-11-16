package app.lazydex.ui.navigation.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lazydex.domain.model.MediaItem
import app.lazydex.domain.model.MediaType
import app.lazydex.domain.model.UserStatus
import app.lazydex.ui.components.color
import app.lazydex.ui.components.icon
import coil3.compose.AsyncImage
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddBottomSheet(
    onDismiss: () -> Unit,
    onQuickAdd: (
        title: String,
        type: MediaType,
        format: String,
        status: UserStatus,
        currentProgress: Int,
        totalItems: Int?,
        coverImagePath: String,
        coverImageUrl: String?
    ) -> Unit,
    onAddAndEdit: (
        title: String,
        type: MediaType,
        format: String,
        status: UserStatus,
        currentProgress: Int,
        totalItems: Int?,
        coverImagePath: String,
        coverImageUrl: String?
    ) -> Unit,
    onImportUrl: (url: String) -> Unit,
    onNavigateToBrowse: (query: String) -> Unit,
    onNavigateToExisting: (id: String) -> Unit,
    existingItems: List<MediaItem>,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }

    var title by rememberSaveable { mutableStateOf("") }
    var selectedType by rememberSaveable { mutableStateOf(MediaType.NOVEL) }
    var selectedFormat by rememberSaveable { mutableStateOf("") }
    var selectedStatus by rememberSaveable { mutableStateOf(UserStatus.CURRENT) }
    var currentProgressText by rememberSaveable { mutableStateOf("") }
    var totalUnitsText by rememberSaveable { mutableStateOf("") }
    var coverUriOrUrl by rememberSaveable { mutableStateOf("") }
    var showCoverDialog by rememberSaveable { mutableStateOf(false) }
    var isClipboardDismissed by rememberSaveable { mutableStateOf(false) }

    // Device Image Picker with safe caching to internal app storage
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val tempFile = File(context.cacheDir, "quick_add_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }
                coverUriOrUrl = tempFile.absolutePath
            }
            showCoverDialog = false
        }
    }

    // Smart clipboard URL detection
    val clipboardText = remember {
        clipboardManager.getText()?.text?.toString()?.trim()
    }
    val clipboardUrl = remember(clipboardText) {
        if (!clipboardText.isNullOrBlank() &&
            (clipboardText.startsWith("http://", ignoreCase = true) || clipboardText.startsWith("https://", ignoreCase = true))
        ) {
            clipboardText
        } else null
    }
    val clipboardDomain = remember(clipboardUrl) {
        if (clipboardUrl == null) ""
        else try {
            Uri.parse(clipboardUrl).host?.removePrefix("www.") ?: clipboardUrl
        } catch (_: Exception) {
            clipboardUrl
        }
    }

    // Duplicate detection against current library
    val matchingItem = remember(title, existingItems) {
        val trimmed = title.trim()
        if (trimmed.length >= 2) {
            existingItems.firstOrNull { it.title.equals(trimmed, ignoreCase = true) }
        } else null
    }

    // Dynamic formats and unit labels aligned with TypeConfig schema
    val formats = remember(selectedType) { getFormatsForMediaType(selectedType) }
    val unitLabel = remember(selectedType) { getUnitLabel(selectedType) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentWindowInsets = { WindowInsets.ime.union(WindowInsets.navigationBars) },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 20.dp)
        ) {
            // Header (No close X button, consistent with Komikku & LazyDex bottom sheets)
            Text(
                text = "Quick Add",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            // Smart Clipboard Detected Card (with dismiss X button)
            if (clipboardUrl != null && !isClipboardDismissed) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Link in clipboard",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                            Text(
                                text = clipboardDomain,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        FilledTonalButton(
                            onClick = { onImportUrl(clipboardUrl) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text("Import")
                        }
                        IconButton(
                            onClick = { isClipboardDismissed = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss clipboard suggestion",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            // 1. Hero Block: Cover Thumbnail + Title Input & Formats
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Cover Thumbnail (56x80dp standard 2:3 ratio)
                Box(
                    modifier = Modifier
                        .size(width = 56.dp, height = 80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { showCoverDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    if (coverUriOrUrl.isNotBlank()) {
                        AsyncImage(
                            model = coverUriOrUrl,
                            contentDescription = "Cover Image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = "Add Cover",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Cover",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(Modifier.width(12.dp))

                // Title Input + Clear & Paste Buttons
                Column(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        placeholder = { Text("Enter title to add...") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (title.isNotEmpty()) {
                                    IconButton(onClick = { title = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear title",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        val clip = clipboardManager.getText()?.text?.toString()?.trim()
                                        if (!clip.isNullOrBlank()) {
                                            title = clip
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = "Paste from clipboard",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )

                    // Format Sub-Chips (Dynamic based on selected MediaType from TypeConfig schema)
                    if (formats.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            formats.forEach { (formatKey, formatLabel) ->
                                val isSelected = selectedFormat == formatKey
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedFormat = if (isSelected) "" else formatKey
                                    },
                                    label = {
                                        Text(
                                            text = formatLabel,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer
                                    ),
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 2. Media Type Selection
            Text(
                text = "Media Type",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MediaType.entries.forEach { type ->
                    val isSelected = selectedType == type
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedType = type
                            selectedFormat = ""
                        },
                        label = { Text(type.displayName) },
                        leadingIcon = {
                            Icon(
                                imageVector = type.icon(),
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else type.color(),
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }

            // 3. Status Selector (Aligned with UserStatus.displayName)
            Text(
                text = "Status",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
            )
            val statuses = remember {
                listOf(
                    UserStatus.CURRENT,
                    UserStatus.PLANNING,
                    UserStatus.COMPLETED,
                    UserStatus.PAUSED
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                statuses.forEach { status ->
                    val isSelected = selectedStatus == status
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedStatus = status },
                        label = { Text(status.displayName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }

            // 4. Progress & Units (Clean, single-label inputs)
            Text(
                text = "Progress",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = currentProgressText,
                    onValueChange = { currentProgressText = it.filter { c -> c.isDigit() } },
                    label = { Text(unitLabel) },
                    placeholder = { Text("0") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = totalUnitsText,
                    onValueChange = { totalUnitsText = it.filter { c -> c.isDigit() } },
                    label = { Text("Total $unitLabel") },
                    placeholder = { Text("Optional") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            // 5. Duplicate Detection Inline Warning Card
            if (matchingItem != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Already in your Dex",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = "${matchingItem.title} (${matchingItem.type}) • ${matchingItem.userStatus.displayName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(onClick = { onNavigateToExisting(matchingItem.id) }) {
                            Text("View")
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // 6. Action Row: Primary Add vs Save & Edit (Dispatches normalized local vs remote cover)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            val isRemote = coverUriOrUrl.startsWith("http://", ignoreCase = true) || coverUriOrUrl.startsWith("https://", ignoreCase = true)
                            val finalCoverImagePath = if (!isRemote && coverUriOrUrl.isNotBlank()) coverUriOrUrl else ""
                            val finalCoverImageUrl = if (isRemote && coverUriOrUrl.isNotBlank()) coverUriOrUrl else null

                            onQuickAdd(
                                title.trim(),
                                selectedType,
                                selectedFormat,
                                selectedStatus,
                                currentProgressText.toIntOrNull() ?: 0,
                                totalUnitsText.toIntOrNull(),
                                finalCoverImagePath,
                                finalCoverImageUrl
                            )
                        }
                    },
                    enabled = title.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Add to Dex")
                }

                OutlinedButton(
                    onClick = {
                        if (title.isNotBlank()) {
                            val isRemote = coverUriOrUrl.startsWith("http://", ignoreCase = true) || coverUriOrUrl.startsWith("https://", ignoreCase = true)
                            val finalCoverImagePath = if (!isRemote && coverUriOrUrl.isNotBlank()) coverUriOrUrl else ""
                            val finalCoverImageUrl = if (isRemote && coverUriOrUrl.isNotBlank()) coverUriOrUrl else null

                            onAddAndEdit(
                                title.trim(),
                                selectedType,
                                selectedFormat,
                                selectedStatus,
                                currentProgressText.toIntOrNull() ?: 0,
                                totalUnitsText.toIntOrNull(),
                                finalCoverImagePath,
                                finalCoverImageUrl
                            )
                        }
                    },
                    enabled = title.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Save & Edit")
                }
            }

            // 7. Browse Hand-Off Row
            TextButton(
                onClick = { onNavigateToBrowse(title.trim()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text(
                    text = if (title.isNotBlank()) "Search \"${title.trim()}\" in online sources ➔" else "Search online sources in Browse ➔",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    // Cover Image Picker / URL Input Dialog
    if (showCoverDialog) {
        var tempCoverUrl by remember { mutableStateOf(coverUriOrUrl) }

        AlertDialog(
            onDismissRequest = { showCoverDialog = false },
            title = { Text("Set Cover Image") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Pick image from device")
                    }

                    OutlinedTextField(
                        value = tempCoverUrl,
                        onValueChange = { tempCoverUrl = it },
                        label = { Text("Cover Image URL") },
                        placeholder = { Text("https://example.com/cover.jpg") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coverUriOrUrl = tempCoverUrl.trim()
                        showCoverDialog = false
                    }
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                Row {
                    if (coverUriOrUrl.isNotBlank()) {
                        TextButton(
                            onClick = {
                                coverUriOrUrl = ""
                                showCoverDialog = false
                            }
                        ) {
                            Text("Clear")
                        }
                    }
                    TextButton(onClick = { showCoverDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}

/**
 * Maps MediaType to valid TypeConfig format keys and labels in LazyDex schema.
 */
private fun getFormatsForMediaType(type: MediaType): List<Pair<String, String>> = when (type) {
    MediaType.NOVEL -> listOf(
        "LIGHT_NOVEL" to "Light Novel",
        "WEB_NOVEL" to "Web Novel"
    )
    MediaType.MANGA -> listOf(
        "MANGA" to "Manga",
        "MANHWA" to "Manhwa",
        "MANHUA" to "Manhua",
        "ONE_SHOT" to "One Shot"
    )
    MediaType.ANIME -> listOf(
        "TV" to "TV Series",
        "MOVIE" to "Movie",
        "OVA" to "OVA"
    )
    MediaType.GAME -> listOf(
        "PC" to "PC",
        "ANDROID" to "Android",
        "CONSOLE" to "Console",
        "WEB" to "Web",
        "CROSS_PLATFORM" to "Cross Platform"
    )
    MediaType.MOVIE -> emptyList()
    MediaType.TV -> emptyList()
}

/**
 * Returns unit label for the given MediaType matching TypeConfig definitions.
 */
private fun getUnitLabel(type: MediaType): String = when (type) {
    MediaType.NOVEL, MediaType.MANGA -> "Chapters"
    MediaType.ANIME, MediaType.TV -> "Episodes"
    MediaType.GAME -> "Hours"
    MediaType.MOVIE -> "Minutes"
}
