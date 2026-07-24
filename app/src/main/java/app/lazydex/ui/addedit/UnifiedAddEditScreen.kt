package app.lazydex.ui.addedit

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lazydex.ui.components.CollapsibleBox
import app.lazydex.ui.components.KomikkuMangaCoverDialog
import app.lazydex.ui.components.KomikkuMangaInfoHeader
import app.lazydex.ui.components.KomikkuMangaNotesSection
import app.lazydex.ui.components.KomikkuNamespaceTags
import app.lazydex.ui.components.TrackerBottomSheet
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UnifiedAddEditScreen(
    itemId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UnifiedAddEditViewModel = koinViewModel()
) {
    val state by viewModel.formState.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val density = LocalDensity.current

    val scrollThresholdPx = with(density) { 140.dp.toPx() }
    val scrollRatio = (scrollState.value / scrollThresholdPx).coerceIn(0f, 1f)

    var showOverflowMenu by remember { mutableStateOf(false) }
    var showCoverZoomDialog by remember { mutableStateOf(false) }

    // Dialog state for inline field editing
    var editDialogType by remember { mutableStateOf<String?>(null) } // "title", "author", "progress", "synopsis", "notes", "tags"
    var tempInput by remember { mutableStateOf("") }
    var tempProgressInput by remember { mutableStateOf("") }
    var tempTotalInput by remember { mutableStateOf("") }
    var newTagInput by remember { mutableStateOf("") }

    LaunchedEffect(state.isDone) {
        if (state.isDone) {
            onBack()
        }
    }

    BackHandler(enabled = state.isEditing) {
        if (viewModel.checkBackPressAllowed()) {
            viewModel.cancelEditing()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.alpha(scrollRatio)
                    )
                },
                navigationIcon = {
                    if (state.isEditing) {
                        TextButton(onClick = { viewModel.cancelEditing() }) {
                            Text("Cancel", color = MaterialTheme.colorScheme.primary, fontSize = 15.sp)
                        }
                    } else {
                        IconButton(onClick = {
                            if (viewModel.checkBackPressAllowed()) onBack()
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                actions = {
                    if (state.isEditing) {
                        TextButton(
                            onClick = { viewModel.save() },
                            enabled = state.canSave
                        ) {
                            Text("Save", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    } else {
                        IconButton(onClick = { viewModel.startEditing() }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Entry",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More Options"
                                )
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Quick Scrape Metadata") },
                                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        viewModel.scrapeUrl()
                                    }
                                )
                                if (state.sourceUrl.isNotBlank()) {
                                    DropdownMenuItem(
                                        text = { Text("Open Web URL") },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                                        onClick = {
                                            showOverflowMenu = false
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(state.sourceUrl))
                                                context.startActivity(intent)
                                            } catch (_: Exception) {}
                                        }
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Delete Media Item", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showOverflowMenu = false
                                        viewModel.showDeleteConfirm(true)
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(
                        alpha = if (state.isEditing) 1f else scrollRatio
                    )
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                // 1. Komikku Header (Clean Presentation)
                KomikkuMangaInfoHeader(
                    coverImagePath = state.coverImagePath,
                    title = state.title,
                    author = state.author,
                    category = state.category,
                    status = state.userStatus,
                    rating = state.rating,
                    currentProgress = state.currentProgress,
                    totalItems = state.totalItems,
                    startDate = state.startDate,
                    endDate = state.endDate,
                    isEditing = state.isEditing,
                    topPadding = innerPadding.calculateTopPadding(),
                    onTitleClick = {
                        tempInput = state.title
                        editDialogType = "title"
                    },
                    onAuthorClick = {
                        tempInput = state.author
                        editDialogType = "author"
                    },
                    onCategoryChange = { viewModel.updateCategory(it) },
                    onStatusChange = { viewModel.updateStatus(it) },
                    onRatingChange = { viewModel.updateRating(it) },
                    onProgressClick = {
                        tempProgressInput = state.currentProgress
                        tempTotalInput = state.totalItems
                        editDialogType = "progress"
                    },
                    onStartDateChange = { viewModel.updateStartDate(it) },
                    onEndDateChange = { viewModel.updateEndDate(it) },
                    onCoverClick = { showCoverZoomDialog = true },
                    onTrackerClick = { viewModel.showTrackerSheet(true) },
                    onWebviewClick = if (state.sourceUrl.isNotBlank()) {
                        {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(state.sourceUrl))
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }
                    } else null
                )

                // 2. Synopsis Section (Clean Komikku CollapsibleBox Style)
                CollapsibleBox(
                    heading = "Synopsis",
                    startExpanded = true
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .then(if (state.isEditing) Modifier.clickable {
                                tempInput = state.description
                                editDialogType = "synopsis"
                            } else Modifier)
                    ) {
                        Text(
                            text = state.description.ifBlank { "No synopsis available." },
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = if (state.description.isNotBlank()) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 3. Tags & Genres (Komikku NamespaceTags Style)
                val allTags = state.genres + state.tags
                Column(
                    modifier = Modifier.then(if (state.isEditing) Modifier.clickable {
                        editDialogType = "tags"
                    } else Modifier)
                ) {
                    KomikkuNamespaceTags(
                        tags = allTags.ifEmpty { listOf("No tags added") },
                        onClick = {
                            if (state.isEditing) {
                                editDialogType = "tags"
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 4. Personal Notes (Komikku MangaNotesSection Style)
                Column(
                    modifier = Modifier.then(if (state.isEditing) Modifier.clickable {
                        tempInput = state.notes
                        editDialogType = "notes"
                    } else Modifier)
                ) {
                    KomikkuMangaNotesSection(
                        notes = state.notes,
                        isEditing = false,
                        onNotesChange = {}
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Cover Zoom Dialog
    if (showCoverZoomDialog) {
        KomikkuMangaCoverDialog(
            coverImagePath = state.coverImagePath,
            title = state.title,
            onDismissRequest = { showCoverZoomDialog = false }
        )
    }

    // Field Edit Dialogs (Clean Popup Modals when tapping items in Edit Mode)
    when (editDialogType) {
        "title" -> {
            AlertDialog(
                onDismissRequest = { editDialogType = null },
                title = { Text("Edit Title") },
                text = {
                    OutlinedTextField(
                        value = tempInput,
                        onValueChange = { tempInput = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.updateTitle(tempInput)
                        editDialogType = null
                    }) {
                        Text("Apply")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editDialogType = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
        "author" -> {
            AlertDialog(
                onDismissRequest = { editDialogType = null },
                title = { Text("Edit Author / Studio") },
                text = {
                    OutlinedTextField(
                        value = tempInput,
                        onValueChange = { tempInput = it },
                        label = { Text("Author / Studio") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.updateAuthor(tempInput)
                        editDialogType = null
                    }) {
                        Text("Apply")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editDialogType = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
        "progress" -> {
            AlertDialog(
                onDismissRequest = { editDialogType = null },
                title = { Text("Update Progress") },
                text = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = tempProgressInput,
                            onValueChange = { tempProgressInput = it },
                            label = { Text("Progress") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = tempTotalInput,
                            onValueChange = { tempTotalInput = it },
                            label = { Text("Total") },
                            placeholder = { Text("Ongoing") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.updateProgress(tempProgressInput)
                        viewModel.updateTotal(tempTotalInput)
                        editDialogType = null
                    }) {
                        Text("Apply")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editDialogType = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
        "synopsis" -> {
            AlertDialog(
                onDismissRequest = { editDialogType = null },
                title = { Text("Edit Synopsis") },
                text = {
                    OutlinedTextField(
                        value = tempInput,
                        onValueChange = { tempInput = it },
                        label = { Text("Synopsis") },
                        minLines = 4,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.updateDescription(tempInput)
                        editDialogType = null
                    }) {
                        Text("Apply")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editDialogType = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
        "notes" -> {
            AlertDialog(
                onDismissRequest = { editDialogType = null },
                title = { Text("Edit Personal Notes") },
                text = {
                    OutlinedTextField(
                        value = tempInput,
                        onValueChange = { tempInput = it },
                        label = { Text("Notes (Markdown)") },
                        minLines = 4,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.updateNotes(tempInput)
                        editDialogType = null
                    }) {
                        Text("Apply")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editDialogType = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
        "tags" -> {
            val currentTags = state.genres + state.tags
            AlertDialog(
                onDismissRequest = { editDialogType = null },
                title = { Text("Manage Tags & Genres") },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            currentTags.forEach { tagStr ->
                                InputChip(
                                    selected = true,
                                    onClick = {
                                        viewModel.removeGenre(tagStr)
                                        viewModel.removeTag(tagStr)
                                    },
                                    label = { Text(tagStr, fontSize = 11.sp) },
                                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newTagInput,
                                onValueChange = { newTagInput = it },
                                placeholder = { Text("Add tag/genre...", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(onClick = {
                                if (newTagInput.isNotBlank()) {
                                    viewModel.addTag(newTagInput)
                                    newTagInput = ""
                                }
                            }) {
                                Icon(Icons.Default.Add, contentDescription = "Add", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { editDialogType = null }) {
                        Text("Done")
                    }
                }
            )
        }
    }

    // Confirmation & Tracker Sheets
    if (state.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.showDeleteConfirm(false) },
            title = { Text("Delete Media Item") },
            text = { Text("Are you sure you want to delete '${state.title}'? This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteItem() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showDeleteConfirm(false) }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (state.showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDiscardConfirm() },
            title = { Text("Discard Changes?") },
            text = { Text("You have unsaved changes. Are you sure you want to discard them?") },
            confirmButton = {
                TextButton(onClick = { viewModel.cancelEditing() }) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDiscardConfirm() }) {
                    Text("Keep Editing")
                }
            }
        )
    }

    if (state.showTrackerSheet) {
        val trackerSheetState = rememberModalBottomSheetState()
        TrackerBottomSheet(
            sheetState = trackerSheetState,
            onDismissRequest = { viewModel.showTrackerSheet(false) },
            isBound = state.anilistListEntryId != null,
            anilistListEntryId = state.anilistListEntryId,
            title = state.title,
            currentProgress = state.currentProgress,
            totalItems = state.totalItems,
            progressVolumes = state.progressVolumes,
            userStatus = state.userStatus,
            rating = state.rating,
            isPrivate = state.isPrivate,
            sourceUrl = state.sourceUrl,
            isSearching = state.isTrackerSearching,
            searchQuery = state.trackerSearchQuery,
            searchResults = state.trackerSearchResults,
            onSearchQueryChange = { viewModel.updateTrackerSearchQuery(it) },
            onPerformSearch = { viewModel.searchAniList() },
            onBindMedia = { viewModel.bindAniListMedia(it) },
            onUnbindMedia = { viewModel.unbindAniListMedia() },
            onStatusChange = { viewModel.updateStatus(it) },
            onProgressChange = { viewModel.updateProgress(it) },
            onProgressVolumesChange = { viewModel.updateProgressVolumes(it) },
            onPrivateChange = { viewModel.updateIsPrivate(it) }
        )
    }
}
