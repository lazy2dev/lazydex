package app.lazydex.ui.details

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lazydex.domain.model.MediaType
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
import app.lazydex.ui.theme.coverColorScheme
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

    var headerHeightPx by remember { mutableIntStateOf(0) }
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val fallbackThresholdPx = remember(density) { with(density) { 180.dp.roundToPx() } }

    val isHeaderScrolledOut by remember {
        derivedStateOf {
            val threshold = if (headerHeightPx > 0 && topBarHeightPx > 0) {
                (headerHeightPx - topBarHeightPx).coerceAtLeast(1)
            } else {
                fallbackThresholdPx
            }
            scrollState.value >= threshold
        }
    }

    val titleAlpha by animateFloatAsState(
        targetValue = if (isHeaderScrolledOut) 1f else 0f,
        label = "TopAppBarTitleAlpha"
    )
    val backgroundAlpha by animateFloatAsState(
        targetValue = if (scrollState.value > 0) 1f else 0f,
        label = "TopAppBarBackgroundAlpha"
    )

    val baseColorScheme = MaterialTheme.colorScheme
    val effectiveColorScheme = remember(state.coverTheming, state.seedColor, baseColorScheme) {
        if (state.coverTheming && state.seedColor != null) {
            coverColorScheme(
                seedColor = state.seedColor!!,
                isDark = baseColorScheme.background.luminance() < 0.5f,
                base = baseColorScheme
            )
        } else {
            baseColorScheme
        }
    }

    MaterialTheme(colorScheme = effectiveColorScheme) {
        Scaffold(
            topBar = {
                TopAppBar(
                    modifier = Modifier.onGloballyPositioned { topBarHeightPx = it.size.height },
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
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp).copy(alpha = backgroundAlpha)
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
                    // 1. Media Info Header (Backdrop + Cover + Title + Metadata)
                    MediaInfoHeader(
                        modifier = Modifier.onGloballyPositioned { headerHeightPx = it.size.height },
                        title = state.title,
                        author = state.author,
                        mediaType = state.mediaType,
                        formatName = state.availableFormats.find { it.id == state.formatId }?.name
                            ?: state.formatId?.takeIf { !it.startsWith("typ_") && !it.equals(state.mediaType.name, ignoreCase = true) },
                        sourceUrl = state.sourceUrl,
                        totalItems = state.parsedTotal,
                        coverImagePath = state.coverImagePath,
                        coverImageUrl = state.coverImageUrl,
                        bannerUrl = state.bannerImage,
                        onCoverClick = { if (!state.isObscured) activeDialog = ActiveDialog.Cover },
                        isUpdatingCover = state.isUpdatingArtwork,
                        isObscured = state.isObscured,
                        topPadding = innerPadding.calculateTopPadding(),
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

                // Suggested AniList Tracker Match Banner
                state.suggestedTracker?.let { suggestion ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CloudSync,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Suggested AniList Match",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Text(
                                text = "Found matching AniList entry \"${suggestion.title}\"${if (suggestion.progress > 0) " at progress ${suggestion.progress.toInt()}" else ""}. Link tracker?",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = { viewModel.dismissTrackerSuggestion() }) {
                                    Text("Dismiss")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = { viewModel.acceptTrackerSuggestion() }) {
                                    Text("Link Tracker")
                                }
                            }
                        }
                    }
                }

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
                    mediaType = state.mediaType,
                    userStatus = state.userStatus,
                    currentProgress = state.parsedProgress ?: 0,
                    totalItems = state.parsedTotal,
                    rating = state.rating,
                    startDate = state.startDate,
                    endDate = state.endDate,
                    hasSecondaryProgress = state.hasSecondaryProgress,
                    secondaryUnitName = state.resolvedConfig?.secondaryUnitName,
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
                        if (kotlin.math.abs(next - cur) > 1e-6) {
                            viewModel.updateSecondaryProgress(next.toString())
                            viewModel.autoSave()
                        }
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
                mediaType = state.mediaType,
                onStatusSelected = { status ->
                    if (status != state.userStatus) {
                        viewModel.updateUserStatus(status)
                        viewModel.autoSave()
                    }
                },
                onDismissRequest = { activeDialog = null }
            )
        }
        ActiveDialog.Progress -> {
            TrackProgressSelector(
                title = when (state.mediaType) {
                    MediaType.MANGA, MediaType.NOVEL -> "Chapters Read"
                    MediaType.ANIME, MediaType.TV -> "Episodes Watched"
                    MediaType.GAME -> "Hours Played"
                    MediaType.MOVIE -> "Progress"
                },
                currentProgress = state.parsedProgress ?: 0,
                total = state.parsedTotal,
                onProgressConfirmed = { progress ->
                    if (progress != (state.parsedProgress ?: 0)) {
                        viewModel.updateCurrentProgress(progress.toString())
                        viewModel.autoSave()
                    }
                },
                onDismissRequest = { activeDialog = null }
            )
        }
        ActiveDialog.Score -> {
            TrackScoreSelector(
                currentScore = state.rating,
                onScoreConfirmed = { score ->
                    if (score != state.rating) {
                        viewModel.updateRating(score)
                        viewModel.autoSave()
                    }
                },
                onDismissRequest = { activeDialog = null }
            )
        }
        ActiveDialog.StartDate -> {
            TrackDateSelector(
                title = "Started Reading Date",
                initialDateMillis = state.startDate,
                onDateConfirmed = { date ->
                    if (date != state.startDate) {
                        viewModel.updateStartDate(date)
                        viewModel.autoSave()
                    }
                },
                onDismissRequest = { activeDialog = null }
            )
        }
        ActiveDialog.FinishDate -> {
            TrackDateSelector(
                title = "Finished Reading Date",
                initialDateMillis = state.endDate,
                onDateConfirmed = { date ->
                    if (date != state.endDate) {
                        viewModel.updateEndDate(date)
                        viewModel.autoSave()
                    }
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
                            viewModel.updateArtwork(tempCoverUrl, tempBannerUrl)
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
            EditMetadataModalSheet(
                state = state,
                viewModel = viewModel,
                onDismiss = { activeDialog = null }
            )
        }
        ActiveDialog.Sync -> {
            MediaTrackerSyncDialog(
                inLibrary = !state.isNew,
                isLoggedIn = state.isAnilistLoggedIn,
                anilistMediaId = state.anilistMediaId,
                anilistSyncStatus = state.anilistSyncStatus,
                anilistLastSynced = state.anilistLastSynced,
                isSyncing = state.isSyncing,
                mediaType = state.mediaType,
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
                searchResults = state.trackerSearchResults,
                isSearching = state.isSearchingTracker,
                onSearch = { query -> viewModel.searchAniListTracker(query) },
                onDismissRequest = {
                    viewModel.clearTrackerSearchResults()
                    activeDialog = null
                }
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditMetadataModalSheet(
    state: MediaDetailsUiState,
    viewModel: MediaDetailsViewModel,
    onDismiss: () -> Unit,
) {
    DisposableEffect(Unit) {
        viewModel.setMetadataSheetOpen(true)
        onDispose { viewModel.setMetadataSheetOpen(false) }
    }

    var sourceUrl by remember { mutableStateOf(state.sourceUrl) }
    var selectedType by remember { mutableStateOf(state.mediaType) }
    var selectedFormatId by remember { mutableStateOf(state.formatId) }
    var title by remember { mutableStateOf(state.title) }
    var author by remember { mutableStateOf(state.author) }
    var totalItems by remember { mutableStateOf(state.totalItems) }
    var description by remember { mutableStateOf(state.description) }
    var localError by remember { mutableStateOf<String?>(null) }
    var handledScrapeVersion by remember { mutableIntStateOf(state.scrapeVersion) }
    var committed by remember { mutableStateOf(false) }

    LaunchedEffect(state.scrapeVersion) {
        if (state.scrapeVersion != handledScrapeVersion) {
            handledScrapeVersion = state.scrapeVersion
            selectedType = state.mediaType
            selectedFormatId = state.formatId
            title = state.title
            author = state.author
            description = state.description
            localError = null
        }
    }

    val scope = rememberCoroutineScope()

    val trimmedUrl = sourceUrl.trim()
    val urlInvalid = trimmedUrl.isNotEmpty() &&
        !trimmedUrl.startsWith("http://", ignoreCase = true) &&
        !trimmedUrl.startsWith("https://", ignoreCase = true)
    val titleBlank = title.trim().isBlank()
    val parsedDraftTotal = totalItems.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
    val progressNum = state.parsedProgress
    val totalInvalid = (totalItems.trim().isNotEmpty() && (parsedDraftTotal == null || parsedDraftTotal < 0)) ||
        (progressNum != null && parsedDraftTotal != null && progressNum > parsedDraftTotal)

    fun tryCommit(): Boolean {
        if (state.isScraping) {
            localError = "Wait for metadata fetch to finish"
            return false
        }
        val err = when {
            titleBlank -> "Title is required"
            totalInvalid -> if (progressNum != null && parsedDraftTotal != null && progressNum > parsedDraftTotal) {
                "Progress cannot exceed total items"
            } else {
                "Total items cannot be negative"
            }
            urlInvalid -> "Source URL must start with http:// or https://"
            else -> null
        }
        if (err != null) {
            localError = err
            return false
        }
        val ok = viewModel.commitMetadataEdits(
            sourceUrl = sourceUrl,
            mediaType = selectedType,
            formatId = selectedFormatId,
            title = title,
            author = author,
            totalItems = totalItems,
            description = description,
        )
        if (!ok) {
            localError = viewModel.uiState.value.errorMsg ?: "Unable to save changes"
            return false
        }
        committed = true
        localError = null
        return true
    }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target ->
            if (target == SheetValue.Hidden && !committed) {
                tryCommit()
            } else {
                true
            }
        }
    )

    val dynamicFormats by remember(selectedType) {
        viewModel.observeFormatsForType(selectedType.name)
    }.collectAsState(initial = if (selectedType == state.mediaType) state.availableFormats else emptyList())

    val typeOptions = remember(state.availableTypes) {
        val mapped = state.availableTypes.mapNotNull { cfg ->
            runCatching { MediaType.valueOf(cfg.id.uppercase()) }.getOrNull()?.let { it to cfg.name }
        }
        mapped.ifEmpty {
            listOf(
                MediaType.NOVEL to MediaType.NOVEL.displayName,
                MediaType.MANGA to MediaType.MANGA.displayName,
                MediaType.ANIME to MediaType.ANIME.displayName,
                MediaType.GAME to MediaType.GAME.displayName,
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (committed || tryCommit()) {
                onDismiss()
            } else {
                scope.launch { sheetState.show() }
            }
        },
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
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
                Button(
                    onClick = {
                        if (tryCommit()) {
                            scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                        }
                    },
                    enabled = !state.isScraping && !state.isSaving
                ) {
                    Text("Done")
                }
            }

            if (localError != null) {
                Text(
                    text = localError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            // Source URL Fetcher
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                            value = sourceUrl,
                            onValueChange = {
                                sourceUrl = it
                                localError = null
                                viewModel.clearScrapeError()
                            },
                            label = { Text("Source URL") },
                            placeholder = { Text("Paste URL to auto-fetch info") },
                            isError = urlInvalid && localError != null,
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalButton(
                            onClick = {
                                localError = null
                                viewModel.clearScrapeError()
                                viewModel.updateSourceUrl(sourceUrl)
                                viewModel.scrapeUrl(sourceUrl)
                            },
                            enabled = !state.isScraping && sourceUrl.isNotBlank() && !urlInvalid
                        ) {
                            if (state.isScraping) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Fetch", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                if (state.scrapeError != null) {
                    Text(
                        text = state.scrapeError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            // Type & Format Picker
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                var typeExpanded by remember { mutableStateOf(false) }
                val selectedTypeLabel = typeOptions.find { it.first == selectedType }?.second ?: selectedType.displayName
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = it },
                    modifier = Modifier.weight(1f),
                ) {
                    OutlinedTextField(
                        value = selectedTypeLabel,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text("Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false },
                    ) {
                        typeOptions.forEach { (mediaType, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    if (mediaType != selectedType) {
                                        selectedType = mediaType
                                        selectedFormatId = null
                                    }
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }

                if (dynamicFormats.isNotEmpty()) {
                    var formatExpanded by remember { mutableStateOf(false) }
                    val currentFormatName = dynamicFormats.find {
                        it.id.equals(selectedFormatId, ignoreCase = true)
                    }?.name ?: "Standard"
                    ExposedDropdownMenuBox(
                        expanded = formatExpanded,
                        onExpandedChange = { formatExpanded = it },
                        modifier = Modifier.weight(1f),
                    ) {
                        OutlinedTextField(
                            value = currentFormatName,
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            label = { Text("Format") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = formatExpanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        )
                        ExposedDropdownMenu(
                            expanded = formatExpanded,
                            onDismissRequest = { formatExpanded = false },
                        ) {
                            dynamicFormats.forEach { format ->
                                DropdownMenuItem(
                                    text = { Text(format.name) },
                                    onClick = {
                                        selectedFormatId = format.id
                                        formatExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    localError = null
                },
                label = { Text("Title *") },
                isError = titleBlank && localError != null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = author,
                onValueChange = { author = it },
                label = { Text("Author / Artist / Studio") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = totalItems,
                onValueChange = {
                    totalItems = it.filter { ch -> ch.isDigit() }
                    localError = null
                },
                label = { Text("Total Units (Chapters/Episodes)") },
                isError = totalInvalid && localError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Synopsis / Description") },
                minLines = 3,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

