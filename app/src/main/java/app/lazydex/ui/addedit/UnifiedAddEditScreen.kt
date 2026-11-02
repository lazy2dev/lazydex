package app.lazydex.ui.addedit

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lazydex.domain.model.MediaCategory
import app.lazydex.domain.model.UserStatus
import app.lazydex.ui.components.AltTitleEditor
import app.lazydex.ui.components.CategoryBadge
import app.lazydex.ui.components.CoverImage
import app.lazydex.ui.components.Pill
import app.lazydex.ui.components.color
import app.lazydex.ui.components.formatProgress
import app.lazydex.ui.components.icon
import app.lazydex.util.formatDate
import org.koin.androidx.compose.koinViewModel
import java.util.Calendar
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UnifiedAddEditScreen(
    itemId: String?,
    initialUrl: String? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UnifiedAddEditViewModel = koinViewModel()
) {
    val state by viewModel.formState.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    LaunchedEffect(itemId, initialUrl) {
        viewModel.initialize(itemId, initialUrl)
    }

    LaunchedEffect(state.isDone) {
        if (state.isDone) {
            onBack()
        }
    }

    BackHandler {
        if (viewModel.checkBackPressAllowed()) {
            onBack()
        }
    }

    // Modal & Picker States
    var showEditMetadataSheet by rememberSaveable { mutableStateOf(false) }
    var showCoverDialog by rememberSaveable { mutableStateOf(false) }
    var isSynopsisExpanded by rememberSaveable { mutableStateOf(false) }

    var showStatusDialog by rememberSaveable { mutableStateOf(false) }
    var showChapterDialog by rememberSaveable { mutableStateOf(false) }
    var showScoreDialog by rememberSaveable { mutableStateOf(false) }
    var showTopOverflowMenu by remember { mutableStateOf(false) }

    fun openDatePicker(currentMillis: Long?, onSelected: (Long) -> Unit) {
        val cal = Calendar.getInstance()
        if (currentMillis != null && currentMillis > 0) {
            cal.timeInMillis = currentMillis
        }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val selected = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                onSelected(selected.timeInMillis)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    fun stepSecondaryProgress(delta: Double) {
        val current = state.parsedSecondaryProgress ?: 0.0
        val total = state.parsedSecondaryTotal
        val next = (current + delta).coerceAtLeast(0.0)
        val capped = if (total != null && total > 0) next.coerceAtMost(total) else next
        viewModel.updateSecondaryProgress(formatProgress(capped))
        viewModel.autoSave()
    }

    // Komikku Dynamic Top Bar scroll alpha calculations
    val isFirstItemVisible by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    val isFirstItemScrolled by remember { derivedStateOf { listState.firstVisibleItemScrollOffset > 0 } }

    val titleAlpha by animateFloatAsState(
        targetValue = if (!isFirstItemVisible) 1f else 0f,
        label = "TopBarTitleAlpha"
    )
    val backgroundAlpha by animateFloatAsState(
        targetValue = if (!isFirstItemVisible || isFirstItemScrolled) 1f else 0f,
        label = "TopBarBackgroundAlpha"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.isNew) "Add to Library" else state.title.ifBlank { "Media Details" },
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.alpha(if (state.isNew) 1f else titleAlpha)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (viewModel.checkBackPressAllowed()) {
                                onBack()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (state.sourceUrl.isNotBlank()) {
                        IconButton(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(state.sourceUrl))
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Public,
                                contentDescription = "WebView"
                            )
                        }

                        IconButton(
                            onClick = { viewModel.scrapeUrl() },
                            enabled = !state.isScraping
                        ) {
                            if (state.isScraping) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                            }
                        }
                    }

                    IconButton(onClick = { showTopOverflowMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }

                    DropdownMenu(
                        expanded = showTopOverflowMenu,
                        onDismissRequest = { showTopOverflowMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit info") },
                            onClick = {
                                showTopOverflowMenu = false
                                showEditMetadataSheet = true
                            }
                        )
                        if (!state.isNew) {
                            DropdownMenuItem(
                                text = { Text("Delete entry", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showTopOverflowMenu = false
                                    viewModel.showDeleteConfirm(true)
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme
                        .surfaceColorAtElevation(3.dp)
                        .copy(alpha = if (state.isNew) 1f else backgroundAlpha)
                )
            )
        },
        floatingActionButton = {
            // Komikku Floating Resume FAB (In-library) or Add to Library FAB (New entry)
            if (!state.isNew) {
                val currentProg = state.parsedProgress ?: 0
                val isReading = currentProg > 0
                ExtendedFloatingActionButton(
                    text = {
                        Text(
                            text = if (isReading) "Resume Ch. ${currentProg + 1}" else "Start",
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                    onClick = {
                        val next = currentProg + 1
                        val total = state.parsedTotal
                        val capped = if (total != null && total > 0) next.coerceAtMost(total) else next
                        viewModel.updateCurrentProgress(capped.toString())
                        viewModel.autoSave()
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                ExtendedFloatingActionButton(
                    text = { Text("Add to Library", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    onClick = {
                        if (state.isTitleBlank) {
                            viewModel.setError("Title is required")
                            showEditMetadataSheet = true
                        } else {
                            viewModel.save(exitOnSuccess = false)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
            state = listState,
            contentPadding = PaddingValues(bottom = 88.dp) // Space for Komikku FAB
        ) {
            // Error Message Banner
            state.errorMsg?.let { err ->
                item(key = "error_banner") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = err,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.setError(null) }) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            // Quick Scraper URL Box for New Entries
            if (state.isNew) {
                item(key = "scraper_box") {
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = state.sourceUrl,
                                onValueChange = { viewModel.updateSourceUrl(it) },
                                placeholder = { Text("Paste URL to auto-fill metadata", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = { viewModel.scrapeUrl() },
                                enabled = state.sourceUrl.isNotBlank() && !state.isScraping,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                if (state.isScraping) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Fetch")
                                }
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // KOMIKKU ITEM 1: MANGA INFO BOX
            // =========================================================================
            item(key = "info_box") {
                MangaInfoBox(
                    title = state.title.ifBlank { "Untitled" },
                    author = state.author,
                    statusText = state.userStatus.displayName,
                    category = state.category,
                    formatName = state.availableFormats.find { it.id == state.formatId }?.name ?: state.formatId,
                    coverImagePath = state.coverImagePath,
                    coverImageUrl = state.coverImageUrl,
                    bannerUrl = state.bannerImage,
                    onCoverClick = { showCoverDialog = true }
                )
            }

            // =========================================================================
            // KOMIKKU ITEM 2: MANGA ACTION ROW
            // =========================================================================
            item(key = "action_row") {
                MangaActionRow(
                    favorite = !state.isNew,
                    onAddToLibraryClicked = {
                        if (state.isNew) {
                            viewModel.save(exitOnSuccess = false)
                        } else {
                            showStatusDialog = true
                        }
                    },
                    onCategoryClicked = { showEditMetadataSheet = true },
                    onTrackingClicked = {
                        if (!state.isNew) {
                            showChapterDialog = true
                        }
                    },
                    onWebViewClicked = {
                        if (state.sourceUrl.isNotBlank()) {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(state.sourceUrl))
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        } else {
                            showEditMetadataSheet = true
                        }
                    }
                )
            }

            // =========================================================================
            // KOMIKKU ITEM 3: EXPANDABLE DESCRIPTION WITH TAGS
            // =========================================================================
            item(key = "description_with_tags") {
                ExpandableMangaDescription(
                    description = state.description,
                    genres = state.genres,
                    tags = state.tags,
                    isExpanded = isSynopsisExpanded,
                    onToggleExpand = { isSynopsisExpanded = !isSynopsisExpanded },
                    onEditInfo = { showEditMetadataSheet = true }
                )
            }

            // =========================================================================
            // KOMIKKU ITEM 4: CHAPTER HEADER / SECTION HEADER
            // =========================================================================
            if (!state.isNew) {
                item(key = "chapter_header") {
                    ChapterHeader(
                        title = "Tracking",
                        chapterCountText = "${state.parsedProgress ?: 0}".let {
                            val total = state.parsedTotal
                            if (total != null && total > 0) "$it / $total chapters" else "$it chapters"
                        },
                        onClick = { showChapterDialog = true }
                    )
                }

                // =========================================================================
                // KOMIKKU ITEM 5: ANILIST SYNC TRACKING CARD (REPLACES CHAPTER LIST)
                // =========================================================================
                item(key = "track_info_card") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                    ) {
                        Column {
                            // Row 1: Status | Chapters | Score
                            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                                TrackDetailsItem(
                                    modifier = Modifier.weight(1f),
                                    text = state.userStatus.displayName,
                                    onClick = { showStatusDialog = true }
                                )
                                VerticalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                TrackDetailsItem(
                                    modifier = Modifier.weight(1f),
                                    text = "${state.parsedProgress ?: 0}".let {
                                        val total = state.parsedTotal
                                        if (total != null && total > 0) "$it / $total" else it
                                    },
                                    onClick = { showChapterDialog = true }
                                )
                                VerticalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                TrackDetailsItem(
                                    modifier = Modifier.weight(1f),
                                    text = state.rating?.let { String.format("%.1f ★", it) },
                                    placeholder = "Score",
                                    onClick = { showScoreDialog = true }
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                            // Row 2: Started reading date | Finished reading date
                            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                                TrackDetailsItem(
                                    modifier = Modifier.weight(1f),
                                    text = state.startDate?.let { formatDate(it) },
                                    placeholder = "Started reading date",
                                    onClick = {
                                        openDatePicker(state.startDate) {
                                            viewModel.updateStartDate(it)
                                            viewModel.autoSave()
                                        }
                                    }
                                )
                                VerticalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                TrackDetailsItem(
                                    modifier = Modifier.weight(1f),
                                    text = state.endDate?.let { formatDate(it) },
                                    placeholder = "Finished reading date",
                                    onClick = {
                                        openDatePicker(state.endDate) {
                                            viewModel.updateEndDate(it)
                                            viewModel.autoSave()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // Volume / Secondary Progress Stepper (if present)
                if (state.hasSecondaryProgress) {
                    item(key = "secondary_progress") {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Volume Progress",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilledTonalIconButton(
                                        onClick = { stepSecondaryProgress(-1.0) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "Decrement", modifier = Modifier.size(16.dp))
                                    }

                                    Text(
                                        text = "${state.secondaryProgress.ifEmpty { "0" }} / ${state.secondaryProgressTotal.ifEmpty { "—" }}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    FilledTonalIconButton(
                                        onClick = { stepSecondaryProgress(1.0) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Increment", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // Komikku MangaNotesDisplay Box
                item(key = "notes_display") {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Notes",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            OutlinedTextField(
                                value = state.notes,
                                onValueChange = {
                                    viewModel.updateNotes(it)
                                    viewModel.autoSave()
                                },
                                placeholder = { Text("Write personal notes or comments...") },
                                minLines = 2,
                                maxLines = 6,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // MODAL DIALOGS
    // =========================================================================

    // 1. Status Picker Dialog
    if (showStatusDialog) {
        AlertDialog(
            onDismissRequest = { showStatusDialog = false },
            title = { Text("Update Status") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    UserStatus.entries.forEach { status ->
                        val isSelected = state.userStatus == status
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updateUserStatus(status)
                                    viewModel.autoSave()
                                    showStatusDialog = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(status.icon(), contentDescription = null, tint = status.color())
                                Text(
                                    text = status.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showStatusDialog = false }) { Text("Cancel") }
            }
        )
    }

    // 2. Chapter Progress Dialog
    if (showChapterDialog) {
        var inputProgress by remember { mutableStateOf(state.currentProgress) }
        var inputTotal by remember { mutableStateOf(state.totalItems) }

        AlertDialog(
            onDismissRequest = { showChapterDialog = false },
            title = { Text("Update Progress") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = inputProgress,
                        onValueChange = { inputProgress = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Current Chapter") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = inputTotal,
                        onValueChange = { inputTotal = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Total Chapters") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val cur = inputProgress.toIntOrNull() ?: 0
                        AssistChip(onClick = { inputProgress = (cur + 1).toString() }, label = { Text("+1") })
                        AssistChip(onClick = { inputProgress = (cur + 5).toString() }, label = { Text("+5") })
                        AssistChip(onClick = { inputProgress = (cur + 10).toString() }, label = { Text("+10") })
                        inputTotal.toIntOrNull()?.let { tot ->
                            AssistChip(onClick = { inputProgress = tot.toString() }, label = { Text("All") })
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateCurrentProgress(inputProgress)
                        viewModel.updateTotalItems(inputTotal)
                        viewModel.autoSave()
                        showChapterDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showChapterDialog = false }) { Text("Cancel") }
            }
        )
    }

    // 3. AniList 10-Point Score Picker Dialog
    if (showScoreDialog) {
        var currentScore by remember { mutableDoubleStateOf(state.rating ?: 0.0) }

        AlertDialog(
            onDismissRequest = { showScoreDialog = false },
            title = { Text("AniList Score (0 – 10)") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (currentScore > 0.0) String.format("%.1f / 10.0", currentScore) else "Unrated",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Slider(
                        value = currentScore.toFloat(),
                        onValueChange = { currentScore = (it * 10).roundToInt() / 10.0 },
                        valueRange = 0f..10f,
                        steps = 99,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf(6.0, 7.0, 8.0, 9.0, 10.0).forEach { preset ->
                            AssistChip(
                                onClick = { currentScore = preset },
                                label = { Text(preset.toInt().toString()) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateRating(if (currentScore > 0.0) currentScore else null)
                        viewModel.autoSave()
                        showScoreDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                Row {
                    if (state.rating != null) {
                        TextButton(
                            onClick = {
                                viewModel.updateRating(null)
                                viewModel.autoSave()
                                showScoreDialog = false
                            }
                        ) {
                            Text("Clear", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = { showScoreDialog = false }) { Text("Cancel") }
                }
            }
        )
    }

    // 4. Cover Enlarge / Edit Dialog
    if (showCoverDialog) {
        var tempCoverUrl by remember { mutableStateOf(state.coverImageUrl) }
        var tempBannerUrl by remember { mutableStateOf(state.bannerImage ?: "") }

        AlertDialog(
            onDismissRequest = { showCoverDialog = false },
            title = { Text("Cover Image") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CoverImage(
                            coverImagePath = state.coverImagePath,
                            title = state.title,
                            coverImageUrl = tempCoverUrl,
                            modifier = Modifier
                                .width(120.dp)
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }

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
                        label = { Text("Banner Image URL") },
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
                        showCoverDialog = false
                    }
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCoverDialog = false }) { Text("Cancel") }
            }
        )
    }

    // 5. Delete Confirm Dialog
    if (state.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.showDeleteConfirm(false) },
            title = { Text("Delete Entry") },
            text = {
                Text("Are you sure you want to remove \"${state.title}\" from your library? This cannot be undone.")
            },
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

    // 6. Discard Confirm Dialog
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

    // 7. Edit Metadata Bottom Sheet
    if (showEditMetadataSheet) {
        ModalBottomSheet(
            onDismissRequest = { showEditMetadataSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            EditMediaMetadataSheetContent(
                state = state,
                viewModel = viewModel,
                onClose = {
                    viewModel.autoSave()
                    showEditMetadataSheet = false
                }
            )
        }
    }
}

// =============================================================================
// KOMIKKU EXACT COMPONENTS (1:1 IMPLEMENTATION)
// =============================================================================

/**
 * Komikku MangaInfoBox: Backdrop blur gradient + Cover 2:3 + Title + Author + Status + Source
 */
@Composable
private fun MangaInfoBox(
    title: String,
    author: String,
    statusText: String,
    category: MediaCategory,
    formatName: String?,
    coverImagePath: String,
    coverImageUrl: String?,
    bannerUrl: String?,
    onCoverClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        // Backdrop
        val backdropGradientColors = listOf(
            Color.Transparent,
            MaterialTheme.colorScheme.background,
        )
        val imageModel = bannerUrl?.takeIf { it.isNotBlank() } ?: coverImageUrl?.takeIf { it.isNotBlank() }

        if (imageModel != null) {
            CoverImage(
                coverImagePath = coverImagePath,
                title = title,
                coverImageUrl = imageModel,
                shape = RectangleShape,
                modifier = Modifier
                    .matchParentSize()
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = backdropGradientColors,
                                startY = size.height / 2,
                            ),
                        )
                    }
                    .background(MaterialTheme.colorScheme.surfaceTint.copy(alpha = 0.4f))
                    .blur(7.dp)
                    .alpha(0.2f),
            )
        }

        // Manga & source info
        CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Book Cover (Aspect ratio 2:3, max width 100.dp)
                Box(
                    modifier = Modifier
                        .sizeIn(maxWidth = 100.dp)
                        .aspectRatio(2f / 3f)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .clickable(onClick = onCoverClick)
                ) {
                    CoverImage(
                        coverImagePath = coverImagePath,
                        title = title,
                        coverImageUrl = coverImageUrl,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    if (author.isNotBlank()) {
                        Row(
                            modifier = Modifier.alpha(0.78f),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PersonOutline,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = author,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        modifier = Modifier.alpha(0.78f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp).size(16.dp),
                        )
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                            Text(
                                text = statusText,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                            )
                            DotSeparatorText()
                            CategoryBadge(category = category)
                            if (formatName != null) {
                                DotSeparatorText()
                                Pill(text = formatName, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Komikku MangaActionRow
 */
@Composable
private fun MangaActionRow(
    favorite: Boolean,
    onAddToLibraryClicked: () -> Unit,
    onCategoryClicked: () -> Unit,
    onTrackingClicked: () -> Unit,
    onWebViewClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val defaultActionButtonColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    Row(modifier = modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp)) {
        MangaActionButton(
            title = if (favorite) "In library" else "Add to library",
            icon = if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            color = if (favorite) MaterialTheme.colorScheme.primary else defaultActionButtonColor,
            onClick = onAddToLibraryClicked,
        )
        MangaActionButton(
            title = "Category",
            icon = Icons.Default.Folder,
            color = MaterialTheme.colorScheme.primary,
            onClick = onCategoryClicked,
        )
        MangaActionButton(
            title = "Tracking",
            icon = Icons.Outlined.Sync,
            color = if (favorite) MaterialTheme.colorScheme.primary else defaultActionButtonColor,
            onClick = onTrackingClicked,
        )
        MangaActionButton(
            title = "WebView",
            icon = Icons.Outlined.Public,
            color = MaterialTheme.colorScheme.primary,
            onClick = onWebViewClicked,
        )
    }
}

/**
 * Komikku MangaActionButton item
 */
@Composable
private fun RowScope.MangaActionButton(
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
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Komikku ExpandableMangaDescription
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpandableMangaDescription(
    description: String,
    genres: List<String>,
    tags: List<String>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onEditInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.animateContentSize()) {
        val descText = description.ifBlank {
            "No description available. Tap to edit info or fetch from web."
        }

        Text(
            text = descText,
            maxLines = if (isExpanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(top = 8.dp)
                .padding(horizontal = 16.dp)
                .clickable { onToggleExpand() }
        )

        val allTags = genres + tags
        if (allTags.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .padding(vertical = 4.dp)
                    .fillMaxWidth(),
            ) {
                if (isExpanded) {
                    FlowRow(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        allTags.forEach { tag ->
                            TagsChip(text = tag, onClick = onEditInfo)
                        }
                    }
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(allTags) { tag ->
                            TagsChip(text = tag, onClick = onEditInfo)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Komikku TagsChip
 */
@Composable
private fun TagsChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(32.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/**
 * Komikku ChapterHeader
 */
@Composable
private fun ChapterHeader(
    title: String,
    chapterCountText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = chapterCountText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Komikku TrackDetailsItem (Exact 1:1 replica from TrackInfoDialogHome.kt)
 */
@Composable
private fun TrackDetailsItem(
    text: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
) {
    Box(
        modifier = modifier
            .clickable(onClick = onClick)
            .fillMaxHeight()
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text?.takeIf { it.isNotBlank() } ?: placeholder,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (text.isNullOrBlank()) 0.5f else 1f),
        )
    }
}

/**
 * Komikku DotSeparatorText
 */
@Composable
private fun DotSeparatorText(modifier: Modifier = Modifier) {
    Text(
        text = " • ",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

/**
 * Metadata Editor Sheet
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun EditMediaMetadataSheetContent(
    state: AddEditFormState,
    viewModel: UnifiedAddEditViewModel,
    onClose: () -> Unit
) {
    val scrollState = rememberScrollState()
    var newGenreInput by remember { mutableStateOf("") }
    var newTagInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
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
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        // Web Scraper Row
        OutlinedTextField(
            value = state.sourceUrl,
            onValueChange = { viewModel.updateSourceUrl(it) },
            label = { Text("Source URL") },
            placeholder = { Text("https://...") },
            singleLine = true,
            trailingIcon = {
                if (state.sourceUrl.isNotBlank()) {
                    IconButton(
                        onClick = { viewModel.scrapeUrl() },
                        enabled = !state.isScraping
                    ) {
                        if (state.isScraping) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.CloudDownload, contentDescription = "Scrape")
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        // Main Title
        OutlinedTextField(
            value = state.title,
            onValueChange = { viewModel.updateTitle(it) },
            label = { Text("Title *") },
            singleLine = true,
            isError = state.isTitleBlank,
            modifier = Modifier.fillMaxWidth()
        )

        // Romaji & Native Titles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = state.titleRomaji,
                onValueChange = { viewModel.updateTitleRomaji(it) },
                label = { Text("Romaji Title") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )

            OutlinedTextField(
                value = state.titleNative,
                onValueChange = { viewModel.updateTitleNative(it) },
                label = { Text("Native Title") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        // Author / Creator
        OutlinedTextField(
            value = state.author,
            onValueChange = { viewModel.updateAuthor(it) },
            label = { Text("Author / Creator") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // Media Category Dropdown
        var categoryExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = categoryExpanded,
            onExpandedChange = { categoryExpanded = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = state.category.name.lowercase().replaceFirstChar { it.uppercase() },
                onValueChange = {},
                readOnly = true,
                label = { Text("Media Category") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = categoryExpanded,
                onDismissRequest = { categoryExpanded = false }
            ) {
                MediaCategory.entries.forEach { cat ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(cat.icon(), contentDescription = null, tint = cat.color())
                                Text(cat.name.lowercase().replaceFirstChar { it.uppercase() })
                            }
                        },
                        onClick = {
                            viewModel.updateCategory(cat)
                            categoryExpanded = false
                        }
                    )
                }
            }
        }

        // Format / Subcategory
        if (state.availableFormats.isNotEmpty()) {
            var formatExpanded by remember { mutableStateOf(false) }
            val currentFormatName = state.availableFormats.find { it.id == state.formatId }?.name ?: "Default"

            ExposedDropdownMenuBox(
                expanded = formatExpanded,
                onExpandedChange = { formatExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = currentFormatName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Format / Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = formatExpanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = formatExpanded,
                    onDismissRequest = { formatExpanded = false }
                ) {
                    state.availableFormats.forEach { fmt ->
                        DropdownMenuItem(
                            text = { Text(fmt.name) },
                            onClick = {
                                viewModel.updateFormatId(fmt.id)
                                formatExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // Total Items
        OutlinedTextField(
            value = state.totalItems,
            onValueChange = { viewModel.updateTotalItems(it.filter { ch -> ch.isDigit() }) },
            label = { Text("Total Chapters / Episodes") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // Description / Synopsis
        OutlinedTextField(
            value = state.description,
            onValueChange = { viewModel.updateDescription(it) },
            label = { Text("Synopsis / Description") },
            minLines = 3,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth()
        )

        // Genres Section
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Genres", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                state.genres.forEach { genre ->
                    InputChip(
                        selected = true,
                        onClick = { viewModel.removeGenre(genre) },
                        label = { Text(genre) },
                        trailingIcon = {
                            Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp))
                        }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newGenreInput,
                    onValueChange = { newGenreInput = it },
                    placeholder = { Text("Add genre") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        if (newGenreInput.isNotBlank()) {
                            viewModel.addGenre(newGenreInput.trim())
                            newGenreInput = ""
                        }
                    },
                    enabled = newGenreInput.isNotBlank()
                ) {
                    Text("Add")
                }
            }
        }

        // Tags Section
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Tags", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                state.tags.forEach { tag ->
                    InputChip(
                        selected = true,
                        onClick = { viewModel.removeTag(tag) },
                        label = { Text("#$tag") },
                        trailingIcon = {
                            Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp))
                        }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newTagInput,
                    onValueChange = { newTagInput = it },
                    placeholder = { Text("Add tag") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        if (newTagInput.isNotBlank()) {
                            viewModel.addTag(newTagInput.trim().removePrefix("#"))
                            newTagInput = ""
                        }
                    },
                    enabled = newTagInput.isNotBlank()
                ) {
                    Text("Add")
                }
            }
        }

        // Alternative Titles
        AltTitleEditor(
            mainTitle = state.title,
            alternativeTitles = state.alternativeTitles,
            onMainTitleChanged = { viewModel.updateTitle(it) },
            onAltTitlesChanged = { viewModel.updateAlternativeTitles(it) }
        )

        Button(
            onClick = onClose,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Done")
        }
    }
}
