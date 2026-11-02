package app.lazydex.ui.details

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lazydex.domain.model.MediaCategory
import app.lazydex.domain.model.UserStatus
import app.lazydex.ui.details.components.MediaActionRow
import app.lazydex.ui.details.components.MediaDescriptionSection
import app.lazydex.ui.details.components.MediaDetailsSkeleton
import app.lazydex.ui.details.components.MediaInfoHeader
import app.lazydex.ui.details.components.MediaTrackerSyncDialog
import app.lazydex.ui.details.components.MediaTrackingSection
import app.lazydex.ui.details.components.TrackDateSelector
import app.lazydex.ui.details.components.TrackProgressSelector
import app.lazydex.ui.details.components.TrackScoreSelector
import app.lazydex.ui.details.components.TrackStatusSelector
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

private sealed interface ActiveDialog {
    data object Status : ActiveDialog
    data object Progress : ActiveDialog
    data object Score : ActiveDialog
    data object StartDate : ActiveDialog
    data object FinishDate : ActiveDialog
    data object Cover : ActiveDialog
    data object Metadata : ActiveDialog
    data object Sync : ActiveDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDetailsScreen(
    itemId: String? = null,
    initialUrl: String? = null,
    onBack: () -> Unit,
    onNavigateToTracking: (() -> Unit)? = null,
    viewModel: MediaDetailsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var activeDialog by remember { mutableStateOf<ActiveDialog?>(null) }
    var isSynopsisExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(itemId, initialUrl) {
        if (itemId != null || initialUrl != null) {
            viewModel.initialize(itemId, initialUrl)
        }
    }

    LaunchedEffect(state.isDone) {
        if (state.isDone) {
            onBack()
        }
    }

    LaunchedEffect(state.errorMsg) {
        state.errorMsg?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.setError(null)
        }
    }

    BackHandler {
        if (viewModel.onBackPress()) {
            onBack()
        }
    }

    val titleAlpha by animateFloatAsState(
        targetValue = if (scrollState.value > 120) 1f else 0f,
        label = "TopAppBarTitleAlpha"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.title.ifBlank { "Media Details" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.alpha(titleAlpha)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (viewModel.onBackPress()) onBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { viewModel.showDeleteConfirm(true) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete entry",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (scrollState.value > 60) {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    } else {
                        Color.Transparent
                    }
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        if (state.isLoading) {
            MediaDetailsSkeleton(modifier = Modifier.padding(innerPadding))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(bottom = 88.dp)
            ) {
                Spacer(modifier = Modifier.height(innerPadding.calculateTopPadding()))

                // 1. Media Info Header (Backdrop + Cover + Title + Metadata)
                MediaInfoHeader(
                    title = state.title,
                    author = state.author,
                    category = state.category,
                    formatName = state.availableFormats.find { it.id == state.formatId }?.name ?: state.formatId,
                    sourceUrl = state.sourceUrl,
                    totalItems = state.parsedTotal,
                    coverImagePath = state.coverImagePath,
                    coverImageUrl = state.coverImageUrl,
                    bannerUrl = state.bannerImage,
                    onCoverClick = { activeDialog = ActiveDialog.Cover }
                )

                // 2. Action Row (In Library, Updated X, Sync, Edit)
                MediaActionRow(
                    inLibrary = !state.isNew,
                    onLibraryClick = {
                        if (state.isNew) {
                            viewModel.save(exitOnSuccess = false)
                        } else {
                            viewModel.showDeleteConfirm(true)
                        }
                    },
                    onLibraryLongClick = {
                        activeDialog = ActiveDialog.Metadata
                    },
                    lastUpdatedMillis = state.lastUpdated,
                    onUpdatedClick = {
                        val dateStr = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date(state.lastUpdated))
                        scope.launch { snackbarHostState.showSnackbar("Last updated: $dateStr") }
                    },
                    isSynced = state.anilistSyncStatus == "SYNCED",
                    onSyncClick = {
                        activeDialog = ActiveDialog.Sync
                    },
                    onEditClick = {
                        activeDialog = ActiveDialog.Metadata
                    }
                )

                // 3. Expandable Description & Genre Chips
                MediaDescriptionSection(
                    description = state.description,
                    genres = state.genres,
                    tags = state.tags,
                    isExpanded = isSynopsisExpanded,
                    onToggleExpand = { isSynopsisExpanded = !isSynopsisExpanded },
                )

                // 4. Tracking Matrix
                MediaTrackingSection(
                    category = state.category,
                    userStatus = state.userStatus,
                    currentProgress = state.parsedProgress ?: 0,
                    totalItems = state.parsedTotal,
                    rating = state.rating,
                    startDate = state.startDate,
                    endDate = state.endDate,
                    hasSecondaryProgress = state.hasSecondaryProgress,
                    secondaryProgress = state.secondaryProgress,
                    secondaryProgressTotal = state.secondaryProgressTotal,
                    notes = state.notes,
                    onStatusClick = { activeDialog = ActiveDialog.Status },
                    onProgressClick = { activeDialog = ActiveDialog.Progress },
                    onScoreClick = { activeDialog = ActiveDialog.Score },
                    onStartDateClick = { activeDialog = ActiveDialog.StartDate },
                    onEndDateClick = { activeDialog = ActiveDialog.FinishDate },
                    onSecondaryStep = { step ->
                        val cur = state.parsedSecondaryProgress ?: 0.0
                        val next = (cur + step).coerceAtLeast(0.0)
                        viewModel.updateSecondaryProgress(next.toString())
                        viewModel.autoSave()
                    },
                    onNotesChange = {
                        viewModel.updateNotes(it)
                    }
                )
            }
        }
    }

    // =========================================================================
    // MODAL DIALOGS (1:1 KOMIKKU TRACK SELECTORS)
    // =========================================================================

    when (activeDialog) {
        ActiveDialog.Status -> {
            TrackStatusSelector(
                currentStatus = state.userStatus,
                category = state.category,
                onStatusSelected = { status ->
                    viewModel.updateUserStatus(status)
                    viewModel.autoSave()
                },
                onDismissRequest = { activeDialog = null }
            )
        }
        ActiveDialog.Progress -> {
            TrackProgressSelector(
                title = when (state.category) {
                    MediaCategory.MANGA, MediaCategory.NOVEL -> "Chapters Read"
                    MediaCategory.ANIME, MediaCategory.TV -> "Episodes Watched"
                    MediaCategory.GAME -> "Hours Played"
                    MediaCategory.MOVIE -> "Progress"
                },
                currentProgress = state.parsedProgress ?: 0,
                total = state.parsedTotal,
                onProgressConfirmed = { progress ->
                    viewModel.updateCurrentProgress(progress.toString())
                    viewModel.autoSave()
                },
                onDismissRequest = { activeDialog = null }
            )
        }
        ActiveDialog.Score -> {
            TrackScoreSelector(
                currentScore = state.rating,
                onScoreConfirmed = { score ->
                    viewModel.updateRating(score)
                    viewModel.autoSave()
                },
                onDismissRequest = { activeDialog = null }
            )
        }
        ActiveDialog.StartDate -> {
            TrackDateSelector(
                title = "Started Reading Date",
                initialDateMillis = state.startDate,
                onDateConfirmed = { date ->
                    viewModel.updateStartDate(date)
                    viewModel.autoSave()
                },
                onDismissRequest = { activeDialog = null }
            )
        }
        ActiveDialog.FinishDate -> {
            TrackDateSelector(
                title = "Finished Reading Date",
                initialDateMillis = state.endDate,
                onDateConfirmed = { date ->
                    viewModel.updateEndDate(date)
                    viewModel.autoSave()
                },
                onDismissRequest = { activeDialog = null }
            )
        }
        ActiveDialog.Cover -> {
            var tempCoverUrl by remember { mutableStateOf(state.coverImageUrl) }
            var tempBannerUrl by remember { mutableStateOf(state.bannerImage ?: "") }

            AlertDialog(
                onDismissRequest = { activeDialog = null },
                title = { Text("Edit Artwork") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = tempCoverUrl,
                            onValueChange = { tempCoverUrl = it },
                            label = { Text("Cover Image URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = tempBannerUrl,
                            onValueChange = { tempBannerUrl = it },
                            label = { Text("Backdrop Banner URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.updateCoverImageUrl(tempCoverUrl)
                            viewModel.updateBannerImage(tempBannerUrl.takeIf { it.isNotBlank() })
                            viewModel.autoSave()
                            activeDialog = null
                        }
                    ) {
                        Text("Apply")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { activeDialog = null }) { Text("Cancel") }
                }
            )
        }
        ActiveDialog.Metadata -> {
            ModalBottomSheet(
                onDismissRequest = { activeDialog = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                EditMetadataSheetContent(
                    state = state,
                    viewModel = viewModel,
                    onClose = {
                        viewModel.autoSave()
                        activeDialog = null
                    }
                )
            }
        }
        ActiveDialog.Sync -> {
            MediaTrackerSyncDialog(
                isLoggedIn = state.isAnilistLoggedIn,
                anilistMediaId = state.anilistMediaId,
                anilistSyncStatus = state.anilistSyncStatus,
                anilistLastSynced = state.anilistLastSynced,
                isSyncing = state.isSyncing,
                category = state.category,
                title = state.title,
                currentProgress = state.currentProgress,
                totalItems = state.totalItems,
                userStatus = state.userStatus,
                rating = state.rating,
                startDate = state.startDate,
                endDate = state.endDate,
                onSyncNow = { viewModel.syncWithAniList() },
                onLinkMediaId = { id -> viewModel.linkAniListId(id) },
                onUnlink = { viewModel.unlinkAniList() },
                onOpenSettings = {
                    onNavigateToTracking?.invoke()
                },
                onDismissRequest = { activeDialog = null }
            )
        }
        null -> {}
    }

    // Delete Confirmation
    if (state.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.showDeleteConfirm(false) },
            title = { Text("Delete Entry") },
            text = { Text("Are you sure you want to remove \"${state.title}\" from your library? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteItem() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showDeleteConfirm(false) }) { Text("Cancel") }
            }
        )
    }

    // Discard Confirmation
    if (state.showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDiscardConfirm() },
            title = { Text("Discard Changes?") },
            text = { Text("You have unsaved changes. Leaving now will discard all edits.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissDiscardConfirm()
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDiscardConfirm() }) { Text("Keep Editing") }
            }
        )
    }
}

@Composable
private fun EditMetadataSheetContent(
    state: MediaDetailsUiState,
    viewModel: MediaDetailsViewModel,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Edit Media Details",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Button(onClick = onClose) {
                Text("Done")
            }
        }

        // Relocated Scraper / Source URL Fetcher
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                OutlinedTextField(
                    value = state.sourceUrl,
                    onValueChange = { viewModel.updateSourceUrl(it) },
                    label = { Text("Source URL") },
                    placeholder = { Text("Paste URL to auto-fetch info") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                FilledTonalButton(
                    onClick = { viewModel.scrapeUrl() },
                    enabled = !state.isScraping && state.sourceUrl.isNotBlank()
                ) {
                    if (state.isScraping) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Fetch", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Category & Format Picker
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            var categoryExpanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = state.category.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    trailingIcon = {
                        IconButton(onClick = { categoryExpanded = true }) {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { categoryExpanded = true }
                )
                DropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false },
                ) {
                    MediaCategory.entries.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat.displayName) },
                            onClick = {
                                viewModel.updateCategory(cat)
                                categoryExpanded = false
                            }
                        )
                    }
                }
            }

            if (state.availableFormats.isNotEmpty()) {
                var formatExpanded by remember { mutableStateOf(false) }
                val currentFormatName = state.availableFormats.find { it.id == state.formatId }?.name
                    ?: state.availableFormats.firstOrNull()?.name ?: "Standard"
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = currentFormatName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Format") },
                        trailingIcon = {
                            IconButton(onClick = { formatExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { formatExpanded = true }
                    )
                    DropdownMenu(
                        expanded = formatExpanded,
                        onDismissRequest = { formatExpanded = false },
                    ) {
                        state.availableFormats.forEach { format ->
                            DropdownMenuItem(
                                text = { Text(format.name) },
                                onClick = {
                                    viewModel.updateFormatId(format.id)
                                    formatExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = state.title,
            onValueChange = { viewModel.updateTitle(it) },
            label = { Text("Title *") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = state.author,
            onValueChange = { viewModel.updateAuthor(it) },
            label = { Text("Author / Artist / Studio") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = state.totalItems,
            onValueChange = { viewModel.updateTotalItems(it.filter { ch -> ch.isDigit() }) },
            label = { Text("Total Units (Chapters/Episodes)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = state.description,
            onValueChange = { viewModel.updateDescription(it) },
            label = { Text("Synopsis / Description") },
            minLines = 3,
            maxLines = 8,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

