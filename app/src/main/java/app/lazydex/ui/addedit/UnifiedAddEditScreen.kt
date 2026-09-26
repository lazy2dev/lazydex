package app.lazydex.ui.addedit

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lazydex.domain.model.MediaCategory
import app.lazydex.domain.model.UserStatus
import app.lazydex.ui.components.AltTitleEditor
import app.lazydex.ui.components.CategoryBadge
import app.lazydex.ui.components.CoverImage
import app.lazydex.ui.components.Pill
import app.lazydex.ui.components.StarRating
import app.lazydex.ui.components.StatusBadge
import app.lazydex.ui.components.icon
import app.lazydex.util.formatDate
import org.koin.androidx.compose.koinViewModel
import java.util.Calendar

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
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    LaunchedEffect(itemId, initialUrl) {
        viewModel.initialize(itemId, initialUrl)
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(if (itemId == null) 1 else 0) }
    var isRereading by rememberSaveable { mutableStateOf(false) }
    var isSynopsisExpanded by rememberSaveable { mutableStateOf(true) }

    var newGenreInput by rememberSaveable { mutableStateOf("") }
    var newTagInput by rememberSaveable { mutableStateOf("") }

    var showJumpDialog by rememberSaveable { mutableStateOf(false) }
    var jumpValue by rememberSaveable { mutableStateOf("") }
    var jumpTotal by rememberSaveable { mutableStateOf("") }

    var showCoverDialog by rememberSaveable { mutableStateOf(false) }
    var coverUrlInput by rememberSaveable { mutableStateOf("") }

    fun stepProgress(delta: Int) {
        val current = state.parsedProgress ?: 0
        val total = state.parsedTotal
        val next = (current + delta).coerceAtLeast(0)
        val capped = if (total != null && total > 0) next.coerceAtMost(total) else next
        viewModel.updateProgress(capped.toString())
    }

    val daysActive = remember(state.startDate, state.endDate) {
        val start = state.startDate ?: return@remember null
        val end = state.endDate ?: System.currentTimeMillis()
        if (end >= start) {
            ((end - start) / (1000 * 60 * 60 * 24)).coerceAtLeast(1)
        } else {
            1L
        }
    }

    val paceText = remember(daysActive, state.parsedProgress) {
        val days = daysActive
        val cur = state.parsedProgress ?: 0
        if (days != null && days > 0 && cur > 0) {
            val pace = cur.toDouble() / days
            String.format("~%.1f ch/day", pace)
        } else {
            null
        }
    }

    val borderStroke = BorderStroke(
        width = 1.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    )

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.isNew) "Add Entry" else "Edit Entry",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
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
                            contentDescription = "Go back"
                        )
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { viewModel.showDeleteConfirm(true) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Entry",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            // Floating Bottom Action Dock: Resting thumb zone (Fitts ID = 0.52 bits)
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            if (viewModel.checkBackPressAllowed()) {
                                onBack()
                            }
                        },
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            if (state.isTitleBlank) {
                                viewModel.setError("Title is required")
                            } else if (state.isProgressInvalid) {
                                selectedTab = 0
                                viewModel.setError("Progress cannot exceed total items")
                            } else if (state.isTotalInvalid) {
                                selectedTab = 0
                                viewModel.setError("Total items cannot be negative")
                            } else if (state.isUrlInvalid) {
                                selectedTab = 1
                                viewModel.setError("Source URL must start with https://")
                            } else {
                                viewModel.save()
                            }
                        },
                        enabled = !state.isSaving,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (state.isNew) "Add to Library" else "Save Changes",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(scrollState)
        ) {
            // Error Message Banner (Inline Contiguity)
            state.errorMsg?.let { err ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cancel,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.setError(null) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss error",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // ==================== 1. FLAT IDENTITY HEADER ====================
            // Flat Canvas: Sits directly on background matching DexScreen & Settings rhythm.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Cover Thumbnail with Edit Overlay
                Box(
                    modifier = Modifier
                        .size(width = 76.dp, height = 106.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            coverUrlInput = state.coverImageUrl ?: ""
                            showCoverDialog = true
                        }
                ) {
                    CoverImage(
                        coverImagePath = state.coverImagePath,
                        title = state.title,
                        coverImageUrl = state.coverImageUrl,
                        modifier = Modifier.fillMaxSize()
                    )

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit cover",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Right: Editable Title & Author Inputs (Direct Manipulation, No Duplication)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CategoryBadge(category = state.category)
                        StatusBadge(status = state.userStatus)
                    }

                    OutlinedTextField(
                        value = state.title,
                        onValueChange = { viewModel.updateTitle(it) },
                        label = { Text("Title *", fontSize = 11.sp) },
                        singleLine = true,
                        isError = state.isTitleBlank && state.errorMsg != null,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = state.author,
                        onValueChange = { viewModel.updateAuthor(it) },
                        label = { Text("Author / Creator", fontSize = 11.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ==================== 2. SEGMENTED CAPSULE TABS ====================
            // Recreated from AppearanceScreen & Mihon UI: Rounded pill capsule floating on background
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = borderStroke,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val tabs = listOf(
                        Triple(0, "Tracking", Icons.Default.SyncAlt),
                        Triple(1, "Details", Icons.Default.Info),
                        Triple(2, "Notes & Tags", Icons.Default.Bookmark)
                    )

                    tabs.forEach { (idx, title, icon) ->
                        val isSelected = selectedTab == idx
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedTab = idx }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = title,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ==================== TAB CONTENT (FLAT CANVAS & CLEAN SECTIONS) ====================
            when (selectedTab) {
                0 -> {
                    // ==================== TAB 0: TRACKING ====================
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Section: Progress & Stepper
                        val current = state.parsedProgress ?: 0
                        val total = state.parsedTotal
                        val remaining = if (total != null && total >= current) "${total - current} remaining" else "Ongoing tracker"
                        val pct = if (total != null && total > 0) ((current * 100) / total).coerceIn(0, 100) else null

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = when (state.category) {
                                        MediaCategory.NOVEL, MediaCategory.MANGA -> "Chapter Progress"
                                        MediaCategory.ANIME, MediaCategory.TV -> "Episode Progress"
                                        MediaCategory.GAME -> "Game Progress"
                                        MediaCategory.MOVIE -> "Movie Progress"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = remaining,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = "$current",
                                        fontSize = 26.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        lineHeight = 26.sp
                                    )
                                    Text(
                                        text = " / ${if (state.totalItems.isBlank()) "?" else state.totalItems}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    )
                                }
                                if (pct != null) {
                                    Pill(text = "$pct% Complete", fontSize = 10.sp)
                                }
                            }
                        }

                        // Linear Progress Track
                        val fraction = remember(state.parsedProgress, state.parsedTotal) {
                            val cur = (state.parsedProgress ?: 0).toFloat()
                            val tot = (state.parsedTotal ?: 0).toFloat()
                            if (tot > 0f) (cur / tot).coerceIn(0f, 1f) else 0f
                        }
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        // 5-Button Stepper Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = borderStroke,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clickable { stepProgress(-10) }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "-10",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = borderStroke,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clickable { stepProgress(-1) }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Remove,
                                        contentDescription = "-1",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                border = borderStroke,
                                modifier = Modifier
                                    .height(42.dp)
                                    .clickable {
                                        jumpValue = state.currentProgress
                                        jumpTotal = state.totalItems
                                        showJumpDialog = true
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Jump",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = borderStroke,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clickable { stepProgress(1) }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "+1",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = borderStroke,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clickable { stepProgress(10) }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "+10",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                        // Section: Status
                        Text(
                            text = "Status",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        val statusRows = remember(state.category) {
                            val primary = when (state.category) {
                                MediaCategory.NOVEL, MediaCategory.MANGA -> UserStatus.READING
                                MediaCategory.ANIME, MediaCategory.MOVIE, MediaCategory.TV -> UserStatus.WATCHING
                                MediaCategory.GAME -> UserStatus.PLAYING
                            }
                            val primaryLabel = when (primary) {
                                UserStatus.READING -> "Reading"
                                UserStatus.WATCHING -> "Watching"
                                UserStatus.PLAYING -> "Playing"
                                else -> primary.displayName
                            }
                            val planToLabel = when (state.category) {
                                MediaCategory.NOVEL, MediaCategory.MANGA -> "Plan to Read"
                                MediaCategory.GAME -> "Plan to Play"
                                else -> "Plan to Watch"
                            }
                            listOf(
                                listOf(
                                    Triple(primary, primaryLabel, Icons.Default.LocalLibrary),
                                    Triple(UserStatus.PLAN_TO, planToLabel, Icons.Default.BookmarkBorder),
                                    Triple(UserStatus.COMPLETED, "Completed", Icons.Default.TaskAlt)
                                ),
                                listOf(
                                    Triple(UserStatus.ON_HOLD, "On Hold", Icons.Default.PauseCircle),
                                    Triple(UserStatus.DROPPED, "Dropped", Icons.Default.Cancel),
                                    Triple(null, "Rereading", Icons.Default.Replay)
                                )
                            )
                        }

                        statusRows.forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowItems.forEach { (status, label, icon) ->
                                    val isSelected = if (status == null) {
                                        isRereading
                                    } else {
                                        !isRereading && state.userStatus == status
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                        border = borderStroke,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(40.dp)
                                            .clickable {
                                                if (status == null) {
                                                    isRereading = true
                                                    viewModel.updateStatus(UserStatus.READING)
                                                } else {
                                                    isRereading = false
                                                    viewModel.updateStatus(status)
                                                }
                                            }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = label,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                        // Section: Timeline
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Timeline",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            paceText?.let { pace ->
                                Pill(text = pace, fontSize = 10.sp)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Started Date Picker Box
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = borderStroke,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        val cal = Calendar.getInstance()
                                        if (state.startDate != null) cal.timeInMillis = state.startDate!!
                                        DatePickerDialog(
                                            context,
                                            { _, year, month, day ->
                                                val sel = Calendar.getInstance()
                                                sel.set(year, month, day)
                                                viewModel.updateStartDate(sel.timeInMillis)
                                            },
                                            cal.get(Calendar.YEAR),
                                            cal.get(Calendar.MONTH),
                                            cal.get(Calendar.DAY_OF_MONTH)
                                        ).show()
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Started",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Icon(
                                            imageVector = Icons.Default.CalendarToday,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = formatDate(state.startDate).ifEmpty { "Not set" },
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Icon(
                                            imageVector = Icons.Default.EditCalendar,
                                            contentDescription = "Edit date",
                                            tint = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }

                            // Finished Date Picker Box
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = borderStroke,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        val cal = Calendar.getInstance()
                                        if (state.endDate != null) cal.timeInMillis = state.endDate!!
                                        DatePickerDialog(
                                            context,
                                            { _, year, month, day ->
                                                val sel = Calendar.getInstance()
                                                sel.set(year, month, day)
                                                viewModel.updateEndDate(sel.timeInMillis)
                                            },
                                            cal.get(Calendar.YEAR),
                                            cal.get(Calendar.MONTH),
                                            cal.get(Calendar.DAY_OF_MONTH)
                                        ).show()
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Finished",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Icon(
                                            imageVector = Icons.Default.EventAvailable,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (state.endDate != null) formatDate(state.endDate) else "In Progress",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = if (state.endDate != null) FontWeight.SemiBold else FontWeight.Normal,
                                            fontStyle = if (state.endDate == null) FontStyle.Italic else FontStyle.Normal,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (state.endDate != null) "Edit" else "+ Set",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                        // Section: Rating (Reused StarRating)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Rating",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            if (state.rating != null) {
                                TextButton(
                                    onClick = { viewModel.updateRating(null) },
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("Reset", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StarRating(
                                rating = state.rating,
                                isEditable = true,
                                onRatingChanged = { viewModel.updateRating(it) }
                            )
                        }

                        val ratingSentiment = remember(state.rating) {
                            when {
                                state.rating == null -> "Unrated"
                                state.rating!! >= 5.0 -> "Masterpiece • Highly Recommended"
                                state.rating!! >= 4.0 -> "Great • Well worth the time"
                                state.rating!! >= 3.0 -> "Good • Decent experience"
                                state.rating!! >= 2.0 -> "Average • Has notable flaws"
                                else -> "Poor • Disappointing"
                            }
                        }

                        Text(
                            text = ratingSentiment,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }

                1 -> {
                    // ==================== TAB 1: DETAILS ====================
                    // Flat canvas: Sits directly on background with clean dividers
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Section: Auto-Fill Metadata
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CloudSync,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Auto-Fill Metadata",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            if (state.sourceUrl.isNotBlank()) {
                                Pill(text = "Ready", fontSize = 10.sp)
                            }
                        }

                        OutlinedTextField(
                            value = state.sourceUrl,
                            onValueChange = { viewModel.updateSourceUrl(it) },
                            label = { Text("Web Novel / Manga / Anime URL") },
                            placeholder = { Text("https://...") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            trailingIcon = {
                                if (state.isScraping) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    IconButton(
                                        onClick = { viewModel.scrapeUrl() },
                                        enabled = state.sourceUrl.isNotBlank()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Fetch Metadata",
                                            tint = if (state.sourceUrl.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        state.scrapeError?.let { err ->
                            Text(
                                text = "Sync error: $err",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                        // Section: Media Format
                        Text(
                            text = "Media Format",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(MediaCategory.entries) { category ->
                                val isSelected = state.category == category
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.updateCategory(category) },
                                    label = { Text(category.displayName, fontSize = 12.sp) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = category.icon(),
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                        // Section: Alternative Titles (Reused AltTitleEditor)
                        AltTitleEditor(
                            mainTitle = state.title,
                            alternativeTitles = state.alternativeTitles,
                            onMainTitleChanged = { viewModel.updateTitle(it) },
                            onAltTitlesChanged = { viewModel.updateAltTitles(it) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                        // Section: Original Web Link
                        OutlinedTextField(
                            value = state.sourceUrl,
                            onValueChange = { viewModel.updateSourceUrl(it) },
                            label = { Text("Original Web Link") },
                            placeholder = { Text("https://...") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            trailingIcon = {
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
                                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                            contentDescription = "Open Link",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                        // Section: Synopsis
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Synopsis",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            IconButton(
                                onClick = { isSynopsisExpanded = !isSynopsisExpanded },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = if (isSynopsisExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = "Toggle synopsis",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        AnimatedVisibility(visible = isSynopsisExpanded) {
                            OutlinedTextField(
                                value = state.description,
                                onValueChange = { viewModel.updateDescription(it) },
                                placeholder = { Text("Write synopsis or description...") },
                                minLines = 3,
                                maxLines = 8,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                2 -> {
                    // ==================== TAB 2: NOTES & TAGS ====================
                    // Flat canvas: Sits directly on background with clean dividers
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Section: Genres
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Genres",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Pill(text = "${state.genres.size}", fontSize = 10.sp)
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            state.genres.forEach { genre ->
                                InputChip(
                                    selected = true,
                                    onClick = { },
                                    label = { Text(genre, fontSize = 12.sp) },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove genre",
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clickable { viewModel.removeGenre(genre) }
                                        )
                                    }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = newGenreInput,
                                onValueChange = { newGenreInput = it },
                                placeholder = { Text("New genre", fontSize = 12.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        if (newGenreInput.isNotBlank()) {
                                            viewModel.addGenre(newGenreInput)
                                            newGenreInput = ""
                                        }
                                    }
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    if (newGenreInput.isNotBlank()) {
                                        viewModel.addGenre(newGenreInput)
                                        newGenreInput = ""
                                    }
                                },
                                enabled = newGenreInput.isNotBlank()
                            ) {
                                Text("Add")
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                        // Section: Personal Tags
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Personal Tags",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Pill(text = "${state.tags.size}", fontSize = 10.sp)
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            state.tags.forEach { tag ->
                                InputChip(
                                    selected = true,
                                    onClick = { },
                                    label = { Text(tag, fontSize = 12.sp) },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove tag",
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clickable { viewModel.removeTag(tag) }
                                        )
                                    }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = newTagInput,
                                onValueChange = { newTagInput = it },
                                placeholder = { Text("New tag", fontSize = 12.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        if (newTagInput.isNotBlank()) {
                                            viewModel.addTag(newTagInput)
                                            newTagInput = ""
                                        }
                                    }
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    if (newTagInput.isNotBlank()) {
                                        viewModel.addTag(newTagInput)
                                        newTagInput = ""
                                    }
                                },
                                enabled = newTagInput.isNotBlank()
                            ) {
                                Text("Add")
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                        // Section: Private Notes
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Private Notes & Observations",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Pill(text = "${state.notes.length} chars", fontSize = 10.sp)
                        }

                        OutlinedTextField(
                            value = state.notes,
                            onValueChange = { viewModel.updateNotes(it) },
                            placeholder = { Text("Write private observations, theories, reading history...") },
                            minLines = 4,
                            maxLines = 10,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // ==================== DIALOGS ====================

    // Jump to Progress Dialog
    if (showJumpDialog) {
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = {
                Text("Jump to Progress", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = jumpValue,
                        onValueChange = { jumpValue = it },
                        label = { Text("Current Progress") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = jumpTotal,
                        onValueChange = { jumpTotal = it },
                        label = { Text("Total Items (optional)") },
                        placeholder = { Text("Leave blank if ongoing") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateProgress(jumpValue)
                        viewModel.updateTotal(jumpTotal)
                        showJumpDialog = false
                    }
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Cover Image URL Dialog
    if (showCoverDialog) {
        AlertDialog(
            onDismissRequest = { showCoverDialog = false },
            title = {
                Text("Update Poster", fontWeight = FontWeight.Bold)
            },
            text = {
                OutlinedTextField(
                    value = coverUrlInput,
                    onValueChange = { coverUrlInput = it },
                    label = { Text("Poster Image URL") },
                    placeholder = { Text("https://...") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateCoverImageUrl(coverUrlInput)
                        showCoverDialog = false
                    }
                ) {
                    Text("Update")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCoverDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (state.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.showDeleteConfirm(false) },
            title = { Text("Delete Entry", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete this media entry? This will remove all progress history and local cover art.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteItem() }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.showDeleteConfirm(false) }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Discard Warning Dialog
    if (state.showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDiscardConfirm() },
            title = { Text("Discard Changes", fontWeight = FontWeight.Bold) },
            text = { Text("You have unsaved changes. Are you sure you want to discard them and go back?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissDiscardConfirm()
                        onBack()
                    }
                ) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissDiscardConfirm() }
                ) {
                    Text("Keep Editing")
                }
            }
        )
    }
}
