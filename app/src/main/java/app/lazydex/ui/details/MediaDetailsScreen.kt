package app.lazydex.ui.details

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
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
    data object Sync : ActiveDialog
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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

    var isEditing by rememberSaveable { mutableStateOf(state.isNew) }
    LaunchedEffect(state.isNew) {
        if (state.isNew) {
            isEditing = true
        }
    }

    DisposableEffect(isEditing) {
        viewModel.setMetadataSheetOpen(isEditing)
        onDispose {
            viewModel.setMetadataSheetOpen(false)
        }
    }

    var draftTitle by rememberSaveable { mutableStateOf(state.title) }
    var draftAuthor by rememberSaveable { mutableStateOf(state.author) }
    var draftDescription by rememberSaveable { mutableStateOf(state.description) }
    var draftFormatId by rememberSaveable { mutableStateOf(state.formatId) }
    var draftTotalItems by rememberSaveable { mutableStateOf(state.totalItems) }
    var draftSecondaryProgressTotal by rememberSaveable { mutableStateOf(state.secondaryProgressTotal) }
    var draftVolumesEnabled by rememberSaveable { mutableStateOf(state.hasSecondaryProgress) }
    var draftIsAdult by rememberSaveable { mutableStateOf(state.isAdult) }
    var draftSourceUrl by rememberSaveable { mutableStateOf(state.sourceUrl) }
    var draftMediaType by rememberSaveable { mutableStateOf(state.mediaType) }

    var hasInitializedDrafts by remember { mutableStateOf(false) }
    LaunchedEffect(state.isLoading, state.title) {
        if (!state.isLoading && !hasInitializedDrafts) {
            hasInitializedDrafts = true
            draftTitle = state.title
            draftAuthor = state.author
            draftDescription = state.description
            draftFormatId = state.formatId
            draftTotalItems = state.totalItems
            draftSecondaryProgressTotal = state.secondaryProgressTotal
            draftVolumesEnabled = state.hasSecondaryProgress
            draftIsAdult = state.isAdult
            draftSourceUrl = state.sourceUrl
            draftMediaType = state.mediaType
        }
    }

    var handledScrapeVersion by remember { mutableIntStateOf(state.scrapeVersion) }
    LaunchedEffect(state.scrapeVersion) {
        if (state.scrapeVersion != handledScrapeVersion) {
            handledScrapeVersion = state.scrapeVersion
            draftMediaType = state.mediaType
            draftFormatId = state.formatId
            draftTitle = state.title
            draftAuthor = state.author
            draftSecondaryProgressTotal = state.secondaryProgressTotal
            draftVolumesEnabled = state.hasSecondaryProgress
            draftDescription = state.description
            draftIsAdult = state.isAdult
            if (state.sourceUrl.isNotBlank()) {
                draftSourceUrl = state.sourceUrl
            }
        }
    }

    LaunchedEffect(state.scrapeError) {
        state.scrapeError?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    fun enterEditMode() {
        draftTitle = state.title
        draftAuthor = state.author
        draftDescription = state.description
        draftFormatId = state.formatId
        draftTotalItems = state.totalItems
        draftSecondaryProgressTotal = state.secondaryProgressTotal
        draftVolumesEnabled = state.hasSecondaryProgress
        draftIsAdult = state.isAdult
        draftSourceUrl = state.sourceUrl
        draftMediaType = state.mediaType
        isEditing = true
    }

    val isDirty by remember(
        isEditing, draftTitle, draftAuthor, draftDescription, draftFormatId,
        draftTotalItems, draftSecondaryProgressTotal, draftVolumesEnabled,
        draftIsAdult, draftSourceUrl, draftMediaType, state
    ) {
        derivedStateOf {
            if (!isEditing) false
            else {
                draftTitle != state.title ||
                    draftAuthor != state.author ||
                    draftDescription != state.description ||
                    draftFormatId != state.formatId ||
                    draftTotalItems != state.totalItems ||
                    draftSecondaryProgressTotal != state.secondaryProgressTotal ||
                    draftVolumesEnabled != state.hasSecondaryProgress ||
                    draftIsAdult != state.isAdult ||
                    draftSourceUrl != state.sourceUrl ||
                    draftMediaType != state.mediaType
            }
        }
    }

    var showInlineDiscardDialog by remember { mutableStateOf(false) }

    fun handleCancelEdit() {
        if (isDirty) {
            showInlineDiscardDialog = true
        } else {
            if (state.isNew) {
                onBack()
            } else {
                isEditing = false
            }
        }
    }

    val trimmedUrl = draftSourceUrl.trim()
    val urlInvalid = trimmedUrl.isNotEmpty() &&
        !trimmedUrl.startsWith("http://", ignoreCase = true) &&
        !trimmedUrl.startsWith("https://", ignoreCase = true)
    val titleBlank = draftTitle.trim().isBlank()
    val parsedDraftTotal = draftTotalItems.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
    val progressNum = state.parsedProgress
    val totalInvalid = (draftTotalItems.trim().isNotEmpty() && (parsedDraftTotal == null || parsedDraftTotal < 0)) ||
        (progressNum != null && parsedDraftTotal != null && progressNum > parsedDraftTotal)

    fun tryCommit(): Boolean {
        if (state.isScraping) {
            scope.launch { snackbarHostState.showSnackbar("Wait for metadata fetch to finish") }
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
            scope.launch { snackbarHostState.showSnackbar(err) }
            return false
        }
        val ok = viewModel.commitMetadataEdits(
            sourceUrl = draftSourceUrl,
            mediaType = draftMediaType,
            formatId = draftFormatId,
            title = draftTitle,
            author = draftAuthor,
            totalItems = draftTotalItems,
            description = draftDescription,
            isAdult = draftIsAdult,
            secondaryProgressTotal = if (draftVolumesEnabled) draftSecondaryProgressTotal else "",
        )
        if (!ok) {
            scope.launch { snackbarHostState.showSnackbar(viewModel.uiState.value.errorMsg ?: "Unable to save changes") }
            return false
        }
        viewModel.setVolumesEnabled(draftVolumesEnabled)
        if (state.isNew) {
            viewModel.save(exitOnSuccess = true)
        } else {
            isEditing = false
        }
        return true
    }

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
        if (isEditing) {
            handleCancelEdit()
        } else if (viewModel.onBackPress()) {
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
                        if (isEditing) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val clipboardManager = LocalClipboardManager.current
                                OutlinedTextField(
                                    value = draftSourceUrl,
                                    onValueChange = {
                                        draftSourceUrl = it
                                        viewModel.clearScrapeError()
                                    },
                                    placeholder = { Text("Source URL...", style = MaterialTheme.typography.bodySmall) },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodySmall,
                                    trailingIcon = {
                                        IconButton(
                                            onClick = {
                                                val clip = clipboardManager.getText()?.text?.trim()
                                                if (!clip.isNullOrBlank()) {
                                                    draftSourceUrl = clip
                                                    viewModel.clearScrapeError()
                                                    viewModel.updateSourceUrl(clip)
                                                    viewModel.scrapeUrl(clip)
                                                }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentPaste,
                                                contentDescription = "Paste",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp),
                                    shape = MaterialTheme.shapes.small,
                                )
                                IconButton(
                                    onClick = {
                                        viewModel.clearScrapeError()
                                        viewModel.updateSourceUrl(draftSourceUrl)
                                        viewModel.scrapeUrl(draftSourceUrl)
                                    },
                                    enabled = !state.isScraping && draftSourceUrl.isNotBlank() && !urlInvalid,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    if (state.isScraping) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = "Fetch info",
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = state.title.ifBlank { "Media Details" },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.alpha(titleAlpha)
                            )
                        }
                    },
                    navigationIcon = {
                        if (isEditing) {
                            IconButton(onClick = { handleCancelEdit() }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel edit"
                                )
                            }
                        } else {
                            IconButton(onClick = {
                                if (viewModel.onBackPress()) onBack()
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        }
                    },
                    actions = {
                        if (isEditing) {
                            IconButton(
                                onClick = { tryCommit() },
                                enabled = !state.isScraping && !state.isSaving
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Save changes",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            var menuExpanded by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { menuExpanded = true }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Options"
                                    )
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false }
                                ) {
                                    if (state.sourceUrl.isNotBlank()) {
                                        DropdownMenuItem(
                                            text = { Text("Open source URL") },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.OpenInBrowser,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            },
                                            onClick = {
                                                menuExpanded = false
                                                try {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(state.sourceUrl))
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    scope.launch { snackbarHostState.showSnackbar("Cannot open URL") }
                                                }
                                            }
                                        )
                                    }
                                    if (!state.isNew) {
                                        DropdownMenuItem(
                                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            },
                                            onClick = {
                                                menuExpanded = false
                                                viewModel.showDeleteConfirm(true)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp).copy(alpha = if (isEditing) 1f else backgroundAlpha)
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
                        title = if (isEditing) draftTitle else state.title,
                        author = if (isEditing) draftAuthor else state.author,
                        mediaType = if (isEditing) draftMediaType else state.mediaType,
                        formatName = state.availableFormats.find { it.id == (if (isEditing) draftFormatId else state.formatId) }?.name
                            ?: (if (isEditing) draftFormatId else state.formatId)?.takeIf { !it.startsWith("typ_") && !it.equals((if (isEditing) draftMediaType else state.mediaType).name, ignoreCase = true) },
                        sourceUrl = if (isEditing) draftSourceUrl else state.sourceUrl,
                        totalItems = if (isEditing) draftTotalItems.toIntOrNull() else state.parsedTotal,
                        coverImagePath = state.coverImagePath,
                        coverImageUrl = state.coverImageUrl,
                        bannerUrl = state.bannerImage,
                        onCoverClick = { if (!state.isObscured) activeDialog = ActiveDialog.Cover },
                        isUpdatingCover = state.isUpdatingArtwork,
                        isObscured = state.isObscured,
                        topPadding = innerPadding.calculateTopPadding(),
                        isEditing = isEditing,
                        onTitleChange = { draftTitle = it },
                        onAuthorChange = { draftAuthor = it },
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
                            enterEditMode()
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
                        description = if (isEditing) draftDescription else state.description,
                        genres = state.genres,
                        tags = state.tags,
                        isExpanded = isSynopsisExpanded,
                        onToggleExpand = { isSynopsisExpanded = !isSynopsisExpanded },
                        isEditing = isEditing,
                        onDescriptionChange = { draftDescription = it },
                    )

                    // 4. Tracking Matrix
                    MediaTrackingSection(
                        mediaType = if (isEditing) draftMediaType else state.mediaType,
                        userStatus = state.userStatus,
                        currentProgress = state.parsedProgress ?: 0,
                        totalItems = if (isEditing) draftTotalItems.toIntOrNull() else state.parsedTotal,
                        rating = state.rating,
                        startDate = state.startDate,
                        endDate = state.endDate,
                        hasSecondaryProgress = if (isEditing) draftVolumesEnabled else state.hasSecondaryProgress,
                        secondaryUnitName = state.resolvedConfig?.secondaryUnitName,
                        secondaryProgress = state.secondaryProgress,
                        secondaryProgressTotal = if (isEditing) draftSecondaryProgressTotal else state.secondaryProgressTotal,
                        notes = state.notes,
                        repeatCount = state.repeat,
                        onStatusClick = { activeDialog = ActiveDialog.Status },
                        onProgressClick = { activeDialog = ActiveDialog.Progress },
                        onScoreClick = { activeDialog = ActiveDialog.Score },
                        onStartDateClick = { activeDialog = ActiveDialog.StartDate },
                        onEndDateClick = { activeDialog = ActiveDialog.FinishDate },
                        onNotesChange = {
                            viewModel.updateNotes(it)
                        }
                    )

                    // 5. Extra Metadata Fields (Only shown in Edit Mode, right after tracking)
                    if (isEditing) {
                        val dynamicFormats by remember(draftMediaType) {
                            viewModel.observeFormatsForType(draftMediaType.name)
                        }.collectAsState(initial = if (draftMediaType == state.mediaType) state.availableFormats else emptyList())

                        var showDisableConfirmDialog by remember { mutableStateOf(false) }

                        if (showDisableConfirmDialog) {
                            AlertDialog(
                                onDismissRequest = { showDisableConfirmDialog = false },
                                title = { Text("Disable Volume Tracking?") },
                                text = { Text("Disabling volume tracking will reset your volume progress to 0 and remove the volume counter.") },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            draftVolumesEnabled = false
                                            draftSecondaryProgressTotal = ""
                                            viewModel.setVolumesEnabled(false, resetIfDisabled = true)
                                            showDisableConfirmDialog = false
                                        }
                                    ) {
                                        Text("Disable & Reset", color = MaterialTheme.colorScheme.error)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDisableConfirmDialog = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text(
                                    text = "METADATA & DETAILS",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )

                                // Type Selection (if isNew)
                                if (state.isNew) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "Media Type",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            listOf(MediaType.NOVEL, MediaType.MANGA, MediaType.ANIME, MediaType.GAME).forEach { mType ->
                                                FilterChip(
                                                    selected = draftMediaType == mType,
                                                    onClick = {
                                                        if (draftMediaType != mType) {
                                                            draftMediaType = mType
                                                            draftFormatId = null
                                                        }
                                                    },
                                                    label = { Text(mType.displayName) }
                                                )
                                            }
                                        }
                                    }
                                }

                                // Format Chips
                                if (dynamicFormats.isNotEmpty()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "Format",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            dynamicFormats.forEach { format ->
                                                val isSelected = format.id.equals(draftFormatId, ignoreCase = true)
                                                FilterChip(
                                                    selected = isSelected,
                                                    onClick = {
                                                        draftFormatId = if (isSelected) null else format.id
                                                    },
                                                    label = { Text(format.name) }
                                                )
                                            }
                                        }
                                    }
                                }

                                // Total Units
                                val unitsLabel = when (draftMediaType) {
                                    MediaType.NOVEL, MediaType.MANGA -> "Total Chapters"
                                    MediaType.ANIME, MediaType.TV -> "Total Episodes"
                                    MediaType.GAME -> "Total Hours"
                                    MediaType.MOVIE -> "Total Parts"
                                }
                                OutlinedTextField(
                                    value = draftTotalItems,
                                    onValueChange = {
                                        draftTotalItems = it.filter { ch -> ch.isDigit() }
                                    },
                                    label = { Text(unitsLabel) },
                                    placeholder = { Text("Unknown / Ongoing") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // Volumes (for MANGA or NOVEL)
                                if (draftMediaType == MediaType.MANGA || draftMediaType == MediaType.NOVEL) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Track Volumes",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = "Enable volume progress and volume total tracking",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = draftVolumesEnabled,
                                            onCheckedChange = { checked ->
                                                if (!checked) {
                                                    val currentVols = state.parsedSecondaryProgress ?: 0.0
                                                    if (currentVols > 0.0) {
                                                        showDisableConfirmDialog = true
                                                    } else {
                                                        draftVolumesEnabled = false
                                                        draftSecondaryProgressTotal = ""
                                                        viewModel.setVolumesEnabled(false, resetIfDisabled = true)
                                                    }
                                                } else {
                                                    draftVolumesEnabled = true
                                                    viewModel.setVolumesEnabled(true)
                                                }
                                            }
                                        )
                                    }

                                    if (draftVolumesEnabled) {
                                        OutlinedTextField(
                                            value = draftSecondaryProgressTotal,
                                            onValueChange = {
                                                draftSecondaryProgressTotal = it.filter { ch -> ch.isDigit() || ch == '.' }
                                            },
                                            label = { Text("Total Volumes") },
                                            placeholder = { Text("—") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }

                                // 18+ Adult Content Switch
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "18+ Adult Content",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "Obscures cover with blur when blur 18+ setting is enabled",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = draftIsAdult,
                                        onCheckedChange = { draftIsAdult = it }
                                    )
                                }
                            }
                        }
                    }
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
                val repeatLabel = when (state.mediaType) {
                    MediaType.ANIME, MediaType.TV, MediaType.MOVIE -> "Rewatches"
                    MediaType.GAME -> "Replays"
                    else -> "Rereads"
                }
                TrackProgressSelector(
                    title = when (state.mediaType) {
                        MediaType.MANGA, MediaType.NOVEL -> "Chapters Read"
                        MediaType.ANIME, MediaType.TV -> "Episodes Watched"
                        MediaType.GAME -> "Hours Played"
                        MediaType.MOVIE -> "Progress"
                    },
                    currentProgress = state.parsedProgress ?: 0,
                    total = state.parsedTotal,
                    hasSecondaryProgress = state.hasSecondaryProgress,
                    secondaryUnitName = state.resolvedConfig?.secondaryUnitName,
                    currentVolume = state.parsedSecondaryProgress ?: 0.0,
                    totalVolumes = state.parsedSecondaryTotal,
                    repeatCount = state.repeat,
                    repeatLabel = repeatLabel,
                    onConfirmed = { progress, total, vol, totalVol, repeat ->
                        var changed = false
                        if (progress != (state.parsedProgress ?: 0)) {
                            viewModel.updateCurrentProgress(progress.toString())
                            changed = true
                        }
                        val currentTotalStr = state.totalItems.trim()
                        val newTotalStr = total?.toString() ?: ""
                        if (newTotalStr != currentTotalStr) {
                            viewModel.updateTotalItems(newTotalStr)
                            changed = true
                            if (isEditing) draftTotalItems = newTotalStr
                        }
                        if (state.hasSecondaryProgress) {
                            val curVol = state.parsedSecondaryProgress ?: 0.0
                            if (kotlin.math.abs(vol - curVol) > 1e-6) {
                                viewModel.updateSecondaryProgress(if (vol % 1.0 == 0.0) vol.toInt().toString() else vol.toString())
                                changed = true
                            }
                            val curTotalVol = state.secondaryProgressTotal.trim()
                            val newTotalVol = totalVol?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: ""
                            if (newTotalVol != curTotalVol) {
                                viewModel.updateSecondaryProgressTotal(newTotalVol)
                                changed = true
                                if (isEditing) draftSecondaryProgressTotal = newTotalVol
                            }
                        }
                        if (repeat != state.repeat) {
                            viewModel.updateRepeat(repeat)
                            changed = true
                        }
                        if (changed) {
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
                    title = "Started Date",
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
                    title = "Finished Date",
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

        // Discard Confirmation (from ViewModel)
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

        // Discard Confirmation (from Inline Edit Mode)
        if (showInlineDiscardDialog) {
            AlertDialog(
                onDismissRequest = { showInlineDiscardDialog = false },
                title = { Text("Discard Changes?") },
                text = { Text("You have unsaved changes. Leaving edit mode will discard all edits.") },
                confirmButton = {
                    Button(
                        onClick = {
                            showInlineDiscardDialog = false
                            if (state.isNew) {
                                onBack()
                            } else {
                                draftTitle = state.title
                                draftAuthor = state.author
                                draftDescription = state.description
                                draftFormatId = state.formatId
                                draftTotalItems = state.totalItems
                                draftSecondaryProgressTotal = state.secondaryProgressTotal
                                draftVolumesEnabled = state.hasSecondaryProgress
                                draftIsAdult = state.isAdult
                                draftSourceUrl = state.sourceUrl
                                draftMediaType = state.mediaType
                                isEditing = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Discard")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showInlineDiscardDialog = false }) { Text("Keep Editing") }
                }
            )
        }
    }
}

