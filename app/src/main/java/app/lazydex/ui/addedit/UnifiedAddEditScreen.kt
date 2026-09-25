package app.lazydex.ui.addedit

import android.content.Intent
import android.net.Uri
import android.text.Html
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.lazydex.domain.model.MediaCategory
import app.lazydex.domain.model.UserStatus
import app.lazydex.ui.components.CoverImage
import app.lazydex.util.formatDate
import org.koin.androidx.compose.koinViewModel

import androidx.compose.material3.MaterialTheme
import app.lazydex.ui.theme.CategoryTvColor
import app.lazydex.ui.theme.DarkInputBorder
import app.lazydex.ui.theme.LightBackground
import app.lazydex.ui.theme.StatusCompleted

private data class ScreenPalette(
    val Background: Color,
    val Surface: Color,
    val SurfaceContainerLowest: Color,
    val SurfaceContainerLow: Color,
    val SurfaceContainer: Color,
    val SurfaceContainerHigh: Color,
    val SurfaceContainerHighest: Color,
    val SurfaceBright: Color,
    val Primary: Color,
    val PrimaryContainer: Color,
    val OnPrimary: Color,
    val OnPrimaryContainer: Color,
    val Secondary: Color,
    val SecondaryContainer: Color,
    val OnSecondaryContainer: Color,
    val Tertiary: Color,
    val TertiaryContainer: Color,
    val OnTertiaryContainer: Color,
    val OnSurface: Color,
    val OnSurfaceVariant: Color,
    val Outline: Color,
    val OutlineVariant: Color,
    val Error: Color,
    val ErrorContainer: Color
)

@Composable
private fun rememberScreenPalette(): ScreenPalette {
    val cs = MaterialTheme.colorScheme
    return remember(cs) {
        val isAmoled = cs.background == Color.Black
        val isDark = cs.background != LightBackground
        ScreenPalette(
            Background = cs.background,
            Surface = cs.surface,
            SurfaceContainerLowest = if (isAmoled) Color.Black else if (isDark) Color(0xFF14161B) else Color(0xFFE5E7EB),
            SurfaceContainerLow = if (isAmoled) Color(0xFF0D0E11) else cs.surface,
            SurfaceContainer = cs.surfaceVariant,
            SurfaceContainerHigh = if (isDark) Color(0xFF2C323B) else Color(0xFFE2E8F0),
            SurfaceContainerHighest = if (isDark) Color(0xFF38404B) else Color(0xFFCBD5E1),
            SurfaceBright = if (isDark) Color(0xFF444E5B) else Color(0xFF94A3B8),
            Primary = cs.primary,
            PrimaryContainer = cs.primaryContainer,
            OnPrimary = Color.White,
            OnPrimaryContainer = if (isDark) Color(0xFFD4E4F7) else cs.primary,
            Secondary = CategoryTvColor,
            SecondaryContainer = if (isDark) Color(0xFF163E3A) else Color(0xFFCCFBF1),
            OnSecondaryContainer = if (isDark) Color(0xFF99F6E4) else Color(0xFF115E59),
            Tertiary = StatusCompleted,
            TertiaryContainer = if (isDark) Color(0xFF143E23) else Color(0xFFDCFCE7),
            OnTertiaryContainer = if (isDark) Color(0xFF86EFAC) else Color(0xFF166534),
            OnSurface = cs.onSurface,
            OnSurfaceVariant = cs.onBackground,
            Outline = cs.onBackground,
            OutlineVariant = if (isDark) DarkInputBorder else Color(0xFFCBD5E1),
            Error = Color(0xFFEF4444),
            ErrorContainer = if (isDark) Color(0xFF451111) else Color(0xFFFEE2E2)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UnifiedAddEditScreen(
    itemId: String?,
    initialUrl: String? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UnifiedAddEditViewModel = koinViewModel()
) {
    val Palette = rememberScreenPalette()
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
    var newAliasInput by rememberSaveable { mutableStateOf("") }
    var showAddAliasDialog by rememberSaveable { mutableStateOf(false) }

    var showJumpDialog by rememberSaveable { mutableStateOf(false) }
    var jumpValue by rememberSaveable { mutableStateOf("") }
    var jumpTotal by rememberSaveable { mutableStateOf("") }

    var showCoverDialog by rememberSaveable { mutableStateOf(false) }
    var coverUrlInput by rememberSaveable { mutableStateOf("") }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

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
            String.format("~%.1f ch/day pace", pace)
        } else {
            null
        }
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

    Scaffold(
        topBar = {
            // Header Top App Bar
            Surface(
                color = Palette.Surface.copy(alpha = 0.95f),
                border = BorderStroke(1.dp, Palette.SurfaceContainerHigh.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(56.dp)
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (viewModel.checkBackPressAllowed()) {
                                    onBack()
                                }
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Go back",
                                tint = Palette.OnSurface,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column {
                            Text(
                                text = if (state.isNew) "Add Entry" else "Edit Entry",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = Palette.OnSurface,
                                letterSpacing = (-0.2).sp
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Palette.Tertiary, CircleShape)
                                )
                                Text(
                                    text = "Offline Database",
                                    fontSize = 11.sp,
                                    color = Palette.Outline
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (!state.isNew) {
                            IconButton(
                                onClick = { viewModel.showDeleteConfirm(true) },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Entry",
                                    tint = Palette.Outline,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = { },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options",
                                tint = Palette.Outline,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            // Floating Bottom Action Dock
            Surface(
                color = Palette.SurfaceContainer.copy(alpha = 0.95f),
                border = BorderStroke(1.dp, Palette.SurfaceContainerHigh.copy(alpha = 0.5f)),
                shadowElevation = 16.dp,
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
                    // Cancel / Discard button
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Palette.SurfaceContainerHigh,
                        modifier = Modifier
                            .height(48.dp)
                            .clickable {
                                if (viewModel.checkBackPressAllowed()) {
                                    onBack()
                                }
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 18.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                tint = Palette.OnSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Cancel",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Palette.OnSurfaceVariant
                            )
                        }
                    }

                    // Primary Save Action Button (Gradient)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Palette.PrimaryContainer,
                                        Palette.Primary,
                                        Palette.Secondary
                                    )
                                )
                            )
                            .clickable(enabled = !state.isSaving) {
                                if (state.isTitleBlank) {
                                    selectedTab = 1
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
                        contentAlignment = Alignment.Center
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Palette.OnPrimary
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Palette.OnPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (state.isNew) "Add Entry" else "Save Changes",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Palette.OnPrimary
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = Palette.Background,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
        ) {
            // Error Message Banner
            state.errorMsg?.let { err ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Palette.ErrorContainer,
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
                            tint = Palette.Error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = err,
                            color = Palette.Error,
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
                                tint = Palette.Error,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
            // ==================== 1. FOCUSED HERO CARD ====================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Surface(
                    color = Palette.SurfaceContainer,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Palette.SurfaceContainerHigh.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        // Ambient glow blurs
                        Box(
                            modifier = Modifier
                                .size(160.dp)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(Palette.Primary.copy(alpha = 0.18f), Color.Transparent)
                                    )
                                )
                        )
                        Box(
                            modifier = Modifier
                                .size(150.dp)
                                .align(Alignment.BottomEnd)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(Palette.Secondary.copy(alpha = 0.14f), Color.Transparent)
                                    )
                                )
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Cover Container
                            Box(
                                modifier = Modifier
                                    .size(width = 96.dp, height = 128.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Palette.SurfaceContainerLowest)
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
                                    color = Palette.SurfaceContainerLowest.copy(alpha = 0.85f),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                        .size(24.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit cover",
                                            tint = Palette.Primary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            // Metadata Column
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedTab = 1 },
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Palette.SurfaceContainerHigh
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Icon(
                                                imageVector = when (state.category) {
                                                    MediaCategory.NOVEL -> Icons.Default.AutoStories
                                                    MediaCategory.MANGA -> Icons.AutoMirrored.Filled.MenuBook
                                                    MediaCategory.ANIME -> Icons.Default.SmartDisplay
                                                    MediaCategory.GAME -> Icons.Default.SportsEsports
                                                    MediaCategory.MOVIE -> Icons.Default.Movie
                                                    MediaCategory.TV -> Icons.Default.Tv
                                                },
                                                contentDescription = null,
                                                tint = Palette.Secondary,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = state.category.displayName,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Palette.Secondary
                                            )
                                        }
                                    }

                                    if (state.id.isNotBlank()) {
                                        Text(
                                            text = "ID #${state.id.take(4).uppercase()}",
                                            fontSize = 10.sp,
                                            color = Palette.Outline,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }

                                Text(
                                    text = state.title.ifEmpty { "Untitled Entry" },
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Palette.OnSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Text(
                                    text = state.author.ifEmpty { "Unknown Author" },
                                    fontSize = 13.sp,
                                    color = Palette.OnSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDone,
                                        contentDescription = null,
                                        tint = Palette.Tertiary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Offline Stored",
                                        fontSize = 11.sp,
                                        color = Palette.Outline
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ==================== 2. SEGMENTED NAVIGATION TABS ====================
            Surface(
                color = Palette.Surface.copy(alpha = 0.95f),
                border = BorderStroke(1.dp, Palette.SurfaceContainerHigh.copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Palette.SurfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val tabsData = listOf(
                            Triple(0, "Tracking", Icons.Default.SyncAlt),
                            Triple(1, "Details", Icons.Default.Info),
                            Triple(2, "Notes & Tags", Icons.Default.Bookmark)
                        )

                        tabsData.forEach { (idx, title, icon) ->
                            val isSelected = selectedTab == idx
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) Palette.PrimaryContainer else Color.Transparent,
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
                                        tint = if (isSelected) Palette.OnPrimaryContainer else Palette.Outline,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = title,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Palette.OnPrimaryContainer else Palette.Outline
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ==================== TAB PANELS CONTAINER ====================
            when (selectedTab) {
                0 -> {
                    // ==================== TAB 1: TRACKING ====================
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Card 1: Reading Timeline
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Palette.SurfaceContainer,
                            border = BorderStroke(1.dp, Palette.SurfaceContainerHigh.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.DateRange,
                                            contentDescription = null,
                                            tint = Palette.Secondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "READING TIMELINE",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Palette.Outline,
                                            letterSpacing = 1.sp
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Palette.TertiaryContainer.copy(alpha = 0.2f)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .alpha(pulseAlpha)
                                                    .background(Palette.Tertiary, CircleShape)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (state.endDate != null) "Completed" else "Active",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Palette.Tertiary
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Started Box
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Palette.SurfaceContainerHigh.copy(alpha = 0.6f),
                                        border = BorderStroke(1.dp, Palette.SurfaceContainerHigh),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                val cal = java.util.Calendar.getInstance()
                                                if (state.startDate != null) cal.timeInMillis = state.startDate!!
                                                android.app.DatePickerDialog(
                                                    context,
                                                    { _, year, month, day ->
                                                        val sel = java.util.Calendar.getInstance()
                                                        sel.set(year, month, day)
                                                        viewModel.updateStartDate(sel.timeInMillis)
                                                    },
                                                    cal.get(java.util.Calendar.YEAR),
                                                    cal.get(java.util.Calendar.MONTH),
                                                    cal.get(java.util.Calendar.DAY_OF_MONTH)
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
                                                    fontSize = 11.sp,
                                                    color = Palette.Outline
                                                )
                                                Icon(
                                                    imageVector = Icons.Default.CalendarToday,
                                                    contentDescription = null,
                                                    tint = Palette.Primary,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = formatDate(state.startDate).ifEmpty { "Not set" },
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Palette.OnSurface
                                                )
                                                Icon(
                                                    imageVector = Icons.Default.EditCalendar,
                                                    contentDescription = "Edit date",
                                                    tint = Palette.Outline,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Finished Box
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Palette.SurfaceContainerHigh.copy(alpha = 0.6f),
                                        border = BorderStroke(1.dp, Palette.SurfaceContainerHigh),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                val cal = java.util.Calendar.getInstance()
                                                if (state.endDate != null) cal.timeInMillis = state.endDate!!
                                                android.app.DatePickerDialog(
                                                    context,
                                                    { _, year, month, day ->
                                                        val sel = java.util.Calendar.getInstance()
                                                        sel.set(year, month, day)
                                                        viewModel.updateEndDate(sel.timeInMillis)
                                                    },
                                                    cal.get(java.util.Calendar.YEAR),
                                                    cal.get(java.util.Calendar.MONTH),
                                                    cal.get(java.util.Calendar.DAY_OF_MONTH)
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
                                                    fontSize = 11.sp,
                                                    color = Palette.Outline
                                                )
                                                Icon(
                                                    imageVector = Icons.Default.EventAvailable,
                                                    contentDescription = null,
                                                    tint = Palette.Outline,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = if (state.endDate != null) formatDate(state.endDate) else "In Progress",
                                                    fontSize = 12.sp,
                                                    fontWeight = if (state.endDate != null) FontWeight.SemiBold else FontWeight.Medium,
                                                    fontStyle = if (state.endDate == null) FontStyle.Italic else FontStyle.Normal,
                                                    color = if (state.endDate != null) Palette.OnSurface else Palette.OutlineVariant
                                                )
                                                Text(
                                                    text = if (state.endDate != null) "Edit" else "+ Set",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Palette.Primary
                                                )
                                            }
                                        }
                                    }
                                }

                                // Bottom Pace Strip
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Palette.SurfaceContainerLowest.copy(alpha = 0.8f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Speed,
                                                contentDescription = null,
                                                tint = Palette.Secondary,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Text(
                                                text = if (daysActive != null) "Active for $daysActive days" else "Active recently",
                                                fontSize = 12.sp,
                                                color = Palette.OnSurfaceVariant
                                            )
                                        }

                                        paceText?.let { pace ->
                                            Text(
                                                text = pace,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Palette.Secondary,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Card 2: Chapter Progress Card
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Palette.SurfaceContainer,
                            border = BorderStroke(1.dp, Palette.SurfaceContainerHigh.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                val current = state.parsedProgress ?: 0
                                val total = state.parsedTotal
                                val remaining = if (total != null && total >= current) "${total - current} remaining" else "Ongoing tracker"
                                val pct = if (total != null && total > 0) ((current * 100) / total).coerceIn(0, 100) else null

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = Palette.Primary.copy(alpha = 0.15f),
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                                    contentDescription = null,
                                                    tint = Palette.Primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        Column {
                                            Text(
                                                text = when (state.category) {
                                                    MediaCategory.NOVEL, MediaCategory.MANGA -> "Chapter Progress"
                                                    MediaCategory.ANIME, MediaCategory.TV -> "Episode Progress"
                                                    MediaCategory.GAME -> "Game Progress"
                                                    MediaCategory.MOVIE -> "Movie Progress"
                                                },
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Palette.OnSurface
                                            )
                                            Text(
                                                text = remaining,
                                                fontSize = 11.sp,
                                                color = Palette.Outline
                                            )
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Row(verticalAlignment = Alignment.Bottom) {
                                            Text(
                                                text = "$current",
                                                fontSize = 24.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Palette.Primary,
                                                lineHeight = 24.sp
                                            )
                                            Text(
                                                text = " / ${if (state.totalItems.isBlank()) "?" else state.totalItems}",
                                                fontSize = 12.sp,
                                                color = Palette.Outline,
                                                modifier = Modifier.padding(bottom = 2.dp)
                                            )
                                        }
                                        if (pct != null) {
                                            Text(
                                                text = "$pct% Complete",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Palette.Secondary,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }

                                // Gradient Progress Bar
                                val fraction = remember(state.parsedProgress, state.parsedTotal) {
                                    val cur = (state.parsedProgress ?: 0).toFloat()
                                    val tot = (state.parsedTotal ?: 0).toFloat()
                                    if (tot > 0f) (cur / tot).coerceIn(0f, 1f) else 0f
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(CircleShape)
                                        .background(Palette.SurfaceContainerLowest)
                                        .padding(1.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(fraction)
                                            .fillMaxHeight()
                                            .clip(CircleShape)
                                            .background(
                                                Brush.horizontalGradient(
                                                    listOf(Palette.Primary, Palette.Secondary)
                                                )
                                            )
                                    )
                                }

                                // Stepper Row
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Palette.SurfaceContainerHigh.copy(alpha = 0.7f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // -10
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Palette.SurfaceContainer,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(42.dp)
                                                .clickable { stepProgress(-10) }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "-10",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Palette.OnSurface
                                                )
                                            }
                                        }

                                        // -1
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Palette.SurfaceContainer,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(42.dp)
                                                .clickable { stepProgress(-1) }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Remove,
                                                    contentDescription = "-1",
                                                    tint = Palette.OnSurface,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }

                                        // Jump
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Palette.SurfaceContainer,
                                            modifier = Modifier
                                                .height(42.dp)
                                                .padding(horizontal = 2.dp)
                                                .clickable {
                                                    jumpValue = state.currentProgress
                                                    jumpTotal = state.totalItems
                                                    showJumpDialog = true
                                                }
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center,
                                                modifier = Modifier.padding(horizontal = 12.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = null,
                                                    tint = Palette.Primary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Jump",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Palette.Primary
                                                )
                                            }
                                        }

                                        // +1
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Palette.SurfaceContainer,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(42.dp)
                                                .clickable { stepProgress(1) }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = "+1",
                                                    tint = Palette.OnSurface,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }

                                        // +10
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Palette.SurfaceContainer,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(42.dp)
                                                .clickable { stepProgress(10) }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "+10",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Palette.OnSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Card 3: Tracking Status (3x2 Grid)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Palette.SurfaceContainer,
                            border = BorderStroke(1.dp, Palette.SurfaceContainerHigh.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "TRACKING STATUS",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Palette.Outline,
                                        letterSpacing = 1.sp
                                    )
                                    Text(
                                        text = "Select lifecycle state",
                                        fontSize = 11.sp,
                                        color = Palette.Outline
                                    )
                                }

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
                                                shape = RoundedCornerShape(12.dp),
                                                color = if (isSelected) Palette.Primary else Palette.SurfaceContainerHigh,
                                                shadowElevation = if (isSelected) 2.dp else 0.dp,
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
                                                        tint = if (isSelected) Palette.OnPrimary else Palette.OnSurfaceVariant,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = label,
                                                        fontSize = 11.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (isSelected) Palette.OnPrimary else Palette.OnSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Card 4: Your Rating
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Palette.SurfaceContainer,
                            border = BorderStroke(1.dp, Palette.SurfaceContainerHigh.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "YOUR RATING",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Palette.Outline,
                                        letterSpacing = 1.sp
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = if (state.rating != null) String.format("%.1f", state.rating) else "0.0",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Palette.Secondary
                                        )
                                        Text(
                                            text = "/ 5.0",
                                            fontSize = 12.sp,
                                            color = Palette.Outline
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Palette.SurfaceContainerHigh,
                                            modifier = Modifier.clickable { viewModel.updateRating(null) }
                                        ) {
                                            Text(
                                                text = "Reset",
                                                fontSize = 11.sp,
                                                color = Palette.Outline,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Palette.SurfaceContainerHigh.copy(alpha = 0.6f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val currentRating = state.rating ?: 0.0
                                        for (starIdx in 1..5) {
                                            val isFilled = currentRating >= starIdx
                                            IconButton(
                                                onClick = { viewModel.updateRating(starIdx.toDouble()) },
                                                modifier = Modifier.size(44.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isFilled) Icons.Default.Star else Icons.Default.StarBorder,
                                                    contentDescription = "Star $starIdx",
                                                    tint = if (isFilled) Palette.Secondary else Palette.Outline,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                        }
                                    }
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
                                    fontSize = 11.sp,
                                    color = Palette.Outline,
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                )
                            }
                        }
                    }
                }
                1 -> {
                    // ==================== TAB 2: DETAILS (METADATA & SCRAPER) ====================
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Compact Metadata Auto-sync Card
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Palette.SurfaceContainerLow,
                            border = BorderStroke(1.dp, Palette.SurfaceContainerHigh.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CloudSync,
                                            contentDescription = null,
                                            tint = Palette.Secondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Metadata Auto-Sync",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Palette.OnSurface
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Palette.TertiaryContainer.copy(alpha = 0.2f)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = Palette.Tertiary,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (state.sourceUrl.isNotBlank()) "Available" else "Ready",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Palette.Tertiary
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Palette.SurfaceContainer,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 10.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Link,
                                                contentDescription = null,
                                                tint = Palette.Outline,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            BasicTextField(
                                                value = state.sourceUrl,
                                                onValueChange = { viewModel.updateSourceUrl(it) },
                                                singleLine = true,
                                                textStyle = TextStyle(
                                                    color = Palette.OnSurfaceVariant,
                                                    fontSize = 12.sp,
                                                    fontFamily = FontFamily.Monospace
                                                ),
                                                cursorBrush = SolidColor(Palette.Primary),
                                                decorationBox = { innerTextField ->
                                                    Box(contentAlignment = Alignment.CenterStart) {
                                                        if (state.sourceUrl.isEmpty()) {
                                                            Text("https://...", fontSize = 12.sp, color = Palette.OutlineVariant)
                                                        }
                                                        innerTextField()
                                                    }
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Palette.SecondaryContainer,
                                        modifier = Modifier
                                            .height(38.dp)
                                            .clickable(enabled = state.sourceUrl.isNotBlank() && !state.isScraping) {
                                                viewModel.scrapeUrl()
                                            }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center,
                                            modifier = Modifier.padding(horizontal = 12.dp)
                                        ) {
                                            if (state.isScraping) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp,
                                                    color = Palette.OnSecondaryContainer
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.Refresh,
                                                    contentDescription = null,
                                                    tint = Palette.OnSecondaryContainer,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Fetch",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Palette.OnSecondaryContainer
                                                )
                                            }
                                        }
                                    }
                                }

                                state.scrapeError?.let { err ->
                                    Text(
                                        text = "Sync error: $err",
                                        color = Palette.Error,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        // Primary Form Fields Card
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Palette.SurfaceContainer,
                            border = BorderStroke(1.dp, Palette.SurfaceContainerHigh.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // Primary Title
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "PRIMARY TITLE",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Palette.Outline,
                                        letterSpacing = 1.sp
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Palette.SurfaceContainerHigh,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 14.dp)
                                        ) {
                                            BasicTextField(
                                                value = state.title,
                                                onValueChange = { viewModel.updateTitle(it) },
                                                singleLine = true,
                                                textStyle = TextStyle(
                                                    color = Palette.OnSurface,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Medium
                                                ),
                                                cursorBrush = SolidColor(Palette.Primary),
                                                decorationBox = { inner ->
                                                    Box(contentAlignment = Alignment.CenterStart) {
                                                        if (state.title.isEmpty()) {
                                                            Text("Enter title *", fontSize = 14.sp, color = Palette.Outline)
                                                        }
                                                        inner()
                                                    }
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                            Icon(
                                                imageVector = Icons.Default.Translate,
                                                contentDescription = null,
                                                tint = Palette.Outline,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                // Creator / Author
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "CREATOR / AUTHOR",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Palette.Outline,
                                        letterSpacing = 1.sp
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Palette.SurfaceContainerHigh,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.CenterStart,
                                            modifier = Modifier.padding(horizontal = 14.dp)
                                        ) {
                                            BasicTextField(
                                                value = state.author,
                                                onValueChange = { viewModel.updateAuthor(it) },
                                                singleLine = true,
                                                textStyle = TextStyle(
                                                    color = Palette.OnSurface,
                                                    fontSize = 14.sp
                                                ),
                                                cursorBrush = SolidColor(Palette.Primary),
                                                decorationBox = { inner ->
                                                    Box(contentAlignment = Alignment.CenterStart) {
                                                        if (state.author.isEmpty()) {
                                                            Text("Author name", fontSize = 14.sp, color = Palette.Outline)
                                                        }
                                                        inner()
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }

                                // Media Format Chips
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "MEDIA FORMAT",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Palette.Outline,
                                        letterSpacing = 1.sp
                                    )
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(MediaCategory.entries) { category ->
                                            val isSelected = state.category == category
                                            val icon = when (category) {
                                                MediaCategory.NOVEL -> Icons.Default.AutoStories
                                                MediaCategory.MANGA -> Icons.AutoMirrored.Filled.MenuBook
                                                MediaCategory.ANIME -> Icons.Default.SmartDisplay
                                                MediaCategory.GAME -> Icons.Default.SportsEsports
                                                MediaCategory.MOVIE -> Icons.Default.Movie
                                                MediaCategory.TV -> Icons.Default.Tv
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isSelected) Palette.TertiaryContainer else Palette.SurfaceContainerHigh,
                                                modifier = Modifier
                                                    .height(32.dp)
                                                    .clickable { viewModel.updateCategory(category) }
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 12.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = icon,
                                                        contentDescription = null,
                                                        tint = if (isSelected) Palette.OnTertiaryContainer else Palette.OnSurfaceVariant,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = category.displayName,
                                                        fontSize = 12.sp,
                                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                                        color = if (isSelected) Palette.OnTertiaryContainer else Palette.OnSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Alternative Titles / Aliases
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "ALTERNATIVE TITLES",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Palette.Outline,
                                        letterSpacing = 1.sp
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        state.alternativeTitles.forEach { alt ->
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Palette.SurfaceContainerHigh
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                ) {
                                                    Text(
                                                        text = alt,
                                                        fontSize = 12.sp,
                                                        color = Palette.OnSurface
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Remove alias",
                                                        tint = Palette.Outline,
                                                        modifier = Modifier
                                                            .size(14.dp)
                                                            .clickable {
                                                                viewModel.updateAltTitles(state.alternativeTitles.filter { it != alt })
                                                            }
                                                    )
                                                }
                                            }
                                        }

                                        // Add Alias Button
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            border = BorderStroke(1.dp, Palette.Outline.copy(alpha = 0.4f)),
                                            color = Color.Transparent,
                                            modifier = Modifier.clickable { showAddAliasDialog = true }
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = null,
                                                    tint = Palette.Primary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Add Alias",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Palette.Primary
                                                )
                                            }
                                        }
                                    }
                                }

                                // Original Web Link
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "ORIGINAL WEB LINK",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Palette.Outline,
                                        letterSpacing = 1.sp
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Palette.SurfaceContainerHigh,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(40.dp)
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.CenterStart,
                                                modifier = Modifier.padding(horizontal = 12.dp)
                                            ) {
                                                BasicTextField(
                                                    value = state.sourceUrl,
                                                    onValueChange = { viewModel.updateSourceUrl(it) },
                                                    singleLine = true,
                                                    textStyle = TextStyle(
                                                        color = Palette.Outline,
                                                        fontSize = 12.sp,
                                                        fontFamily = FontFamily.Monospace
                                                    ),
                                                    cursorBrush = SolidColor(Palette.Primary),
                                                    decorationBox = { inner ->
                                                        Box(contentAlignment = Alignment.CenterStart) {
                                                            if (state.sourceUrl.isEmpty()) {
                                                                Text("https://webnovel.com/...", fontSize = 12.sp, color = Palette.OutlineVariant)
                                                            }
                                                            inner()
                                                        }
                                                    },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Palette.SurfaceContainerHigh,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clickable(enabled = state.sourceUrl.isNotBlank()) {
                                                    try {
                                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(state.sourceUrl))
                                                        context.startActivity(intent)
                                                    } catch (_: Exception) {}
                                                }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                                    contentDescription = "Open Link",
                                                    tint = Palette.Primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Expandable Synopsis Card
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Palette.SurfaceContainer,
                            border = BorderStroke(1.dp, Palette.SurfaceContainerHigh.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Description,
                                            contentDescription = null,
                                            tint = Palette.Outline,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "SYNOPSIS & SUMMARY",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Palette.Outline,
                                            letterSpacing = 1.sp
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable { isSynopsisExpanded = !isSynopsisExpanded }
                                    ) {
                                        Text(
                                            text = if (isSynopsisExpanded) "Collapse" else "Expand",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Palette.Primary
                                        )
                                        Icon(
                                            imageVector = if (isSynopsisExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = null,
                                            tint = Palette.Primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                AnimatedVisibility(visible = isSynopsisExpanded) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Palette.SurfaceContainerHigh.copy(alpha = 0.5f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            BasicTextField(
                                                value = state.description,
                                                onValueChange = { viewModel.updateDescription(it) },
                                                textStyle = TextStyle(
                                                    color = Palette.OnSurfaceVariant,
                                                    fontSize = 12.sp,
                                                    lineHeight = 18.sp
                                                ),
                                                cursorBrush = SolidColor(Palette.Primary),
                                                decorationBox = { inner ->
                                                    Box(contentAlignment = Alignment.TopStart) {
                                                        if (state.description.isEmpty()) {
                                                            Text("Write synopsis or description (HTML supported)...", fontSize = 12.sp, color = Palette.Outline)
                                                        }
                                                        inner()
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // ==================== TAB 3: NOTES & TAGS ====================
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Genres Card
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Palette.SurfaceContainer,
                            border = BorderStroke(1.dp, Palette.SurfaceContainerHigh.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Category,
                                        contentDescription = null,
                                        tint = Palette.Primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "GENRES (${state.genres.size})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Palette.Outline,
                                        letterSpacing = 1.sp
                                    )
                                }

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    state.genres.forEach { genre ->
                                        Surface(
                                            shape = CircleShape,
                                            color = Palette.Primary.copy(alpha = 0.15f)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = genre,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Palette.Primary
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Remove genre",
                                                    tint = Palette.Primary,
                                                    modifier = Modifier
                                                        .size(13.dp)
                                                        .clickable { viewModel.removeGenre(genre) }
                                                )
                                            }
                                        }
                                    }

                                    // Add Genre Inline
                                    Surface(
                                        shape = CircleShape,
                                        color = Palette.SurfaceContainerHigh
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            BasicTextField(
                                                value = newGenreInput,
                                                onValueChange = { newGenreInput = it },
                                                singleLine = true,
                                                textStyle = TextStyle(color = Palette.OnSurface, fontSize = 12.sp),
                                                cursorBrush = SolidColor(Palette.Primary),
                                                decorationBox = { inner ->
                                                    Box(contentAlignment = Alignment.CenterStart) {
                                                        if (newGenreInput.isEmpty()) {
                                                            Text("+ Add", fontSize = 12.sp, color = Palette.OnSurfaceVariant)
                                                        }
                                                        inner()
                                                    }
                                                },
                                                modifier = Modifier.width(55.dp)
                                            )
                                            if (newGenreInput.isNotBlank()) {
                                                IconButton(
                                                    onClick = {
                                                        viewModel.addGenre(newGenreInput)
                                                        newGenreInput = ""
                                                    },
                                                    modifier = Modifier.size(18.dp)
                                                ) {
                                                    Icon(Icons.Default.Add, null, tint = Palette.Primary, modifier = Modifier.size(14.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Personal Tags Card
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Palette.SurfaceContainer,
                            border = BorderStroke(1.dp, Palette.SurfaceContainerHigh.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Label,
                                        contentDescription = null,
                                        tint = Palette.Secondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "PERSONAL TAGS",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Palette.Outline,
                                        letterSpacing = 1.sp
                                    )
                                }

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    state.tags.forEach { tag ->
                                        Surface(
                                            shape = CircleShape,
                                            color = Palette.SecondaryContainer
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = tag,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Palette.OnSecondaryContainer
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Remove tag",
                                                    tint = Palette.OnSecondaryContainer,
                                                    modifier = Modifier
                                                        .size(13.dp)
                                                        .clickable { viewModel.removeTag(tag) }
                                                )
                                            }
                                        }
                                    }

                                    // Add Tag Inline
                                    Surface(
                                        shape = CircleShape,
                                        color = Palette.SurfaceContainerHigh
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            BasicTextField(
                                                value = newTagInput,
                                                onValueChange = { newTagInput = it },
                                                singleLine = true,
                                                textStyle = TextStyle(color = Palette.OnSurface, fontSize = 12.sp),
                                                cursorBrush = SolidColor(Palette.Secondary),
                                                decorationBox = { inner ->
                                                    Box(contentAlignment = Alignment.CenterStart) {
                                                        if (newTagInput.isEmpty()) {
                                                            Text("+ Add Tag", fontSize = 12.sp, color = Palette.OnSurfaceVariant)
                                                        }
                                                        inner()
                                                    }
                                                },
                                                modifier = Modifier.width(65.dp)
                                            )
                                            if (newTagInput.isNotBlank()) {
                                                IconButton(
                                                    onClick = {
                                                        viewModel.addTag(newTagInput)
                                                        newTagInput = ""
                                                    },
                                                    modifier = Modifier.size(18.dp)
                                                ) {
                                                    Icon(Icons.Default.Add, null, tint = Palette.Secondary, modifier = Modifier.size(14.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Sleek Private Notes Card
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Palette.SurfaceContainer,
                            border = BorderStroke(1.dp, Palette.SurfaceContainerHigh.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = Palette.Tertiary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "PRIVATE THEORY LOG & NOTES",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Palette.Outline,
                                            letterSpacing = 1.sp
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Security,
                                            contentDescription = null,
                                            tint = Palette.Outline,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = "Encrypted",
                                            fontSize = 10.sp,
                                            color = Palette.Outline
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Palette.SurfaceContainerHigh.copy(alpha = 0.6f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        BasicTextField(
                                            value = state.notes,
                                            onValueChange = { viewModel.updateNotes(it) },
                                            textStyle = TextStyle(
                                                color = Palette.OnSurface,
                                                fontSize = 12.sp,
                                                lineHeight = 18.sp
                                            ),
                                            cursorBrush = SolidColor(Palette.Tertiary),
                                            decorationBox = { inner ->
                                                Box(contentAlignment = Alignment.TopStart) {
                                                    if (state.notes.isEmpty()) {
                                                        Text(
                                                            text = "Write private observations, theories, and notes...",
                                                            fontSize = 12.sp,
                                                            color = Palette.Outline
                                                        )
                                                    }
                                                    inner()
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(90.dp)
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Markdown supported",
                                        fontSize = 11.sp,
                                        color = Palette.Outline
                                    )
                                    Text(
                                        text = "${state.notes.length} characters",
                                        fontSize = 11.sp,
                                        color = Palette.Outline
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }

    // ==================== DIALOGS ====================

    // Jump to Progress Dialog
    if (showJumpDialog) {
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            containerColor = Palette.SurfaceContainer,
            title = {
                Text("Jump to Progress", color = Palette.OnSurface, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextField(
                        value = jumpValue,
                        onValueChange = { jumpValue = it },
                        label = { Text("Current Progress", color = Palette.Outline) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Palette.SurfaceContainerHigh,
                            unfocusedContainerColor = Palette.SurfaceContainerHigh,
                            focusedTextColor = Palette.OnSurface,
                            unfocusedTextColor = Palette.OnSurface,
                            focusedIndicatorColor = Palette.Primary,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextField(
                        value = jumpTotal,
                        onValueChange = { jumpTotal = it },
                        label = { Text("Total Items (optional)", color = Palette.Outline) },
                        placeholder = { Text("Leave blank if ongoing", color = Palette.OutlineVariant) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Palette.SurfaceContainerHigh,
                            unfocusedContainerColor = Palette.SurfaceContainerHigh,
                            focusedTextColor = Palette.OnSurface,
                            unfocusedTextColor = Palette.OnSurface,
                            focusedIndicatorColor = Palette.Primary,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
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
                    Text("Cancel", color = Palette.Outline)
                }
            }
        )
    }

    // Cover Image URL Dialog
    if (showCoverDialog) {
        AlertDialog(
            onDismissRequest = { showCoverDialog = false },
            containerColor = Palette.SurfaceContainer,
            title = {
                Text("Update Poster", color = Palette.OnSurface, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextField(
                        value = coverUrlInput,
                        onValueChange = { coverUrlInput = it },
                        label = { Text("Poster Image URL", color = Palette.Outline) },
                        placeholder = { Text("https://...", color = Palette.OutlineVariant) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Palette.SurfaceContainerHigh,
                            unfocusedContainerColor = Palette.SurfaceContainerHigh,
                            focusedTextColor = Palette.OnSurface,
                            unfocusedTextColor = Palette.OnSurface,
                            focusedIndicatorColor = Palette.Primary,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
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
                    Text("Cancel", color = Palette.Outline)
                }
            }
        )
    }

    // Add Alias Dialog
    if (showAddAliasDialog) {
        AlertDialog(
            onDismissRequest = { showAddAliasDialog = false },
            containerColor = Palette.SurfaceContainer,
            title = {
                Text("Add Alternative Title", color = Palette.OnSurface, fontWeight = FontWeight.Bold)
            },
            text = {
                TextField(
                    value = newAliasInput,
                    onValueChange = { newAliasInput = it },
                    label = { Text("Alias / Alt Title", color = Palette.Outline) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Palette.SurfaceContainerHigh,
                        unfocusedContainerColor = Palette.SurfaceContainerHigh,
                        focusedTextColor = Palette.OnSurface,
                        unfocusedTextColor = Palette.OnSurface,
                        focusedIndicatorColor = Palette.Primary,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newAliasInput.isNotBlank()) {
                            viewModel.updateAltTitles(state.alternativeTitles + newAliasInput.trim())
                            newAliasInput = ""
                        }
                        showAddAliasDialog = false
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddAliasDialog = false }) {
                    Text("Cancel", color = Palette.Outline)
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (state.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.showDeleteConfirm(false) },
            containerColor = Palette.SurfaceContainer,
            title = { Text("Delete Entry", color = Palette.OnSurface, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete this media entry? This will remove all progress history and local cover art.", color = Palette.OnSurfaceVariant) },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteItem() }
                ) {
                    Text("Delete", color = Palette.Error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.showDeleteConfirm(false) }
                ) {
                    Text("Cancel", color = Palette.Outline)
                }
            }
        )
    }

    // Discard Warning Dialog
    if (state.showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDiscardConfirm() },
            containerColor = Palette.SurfaceContainer,
            title = { Text("Discard Changes", color = Palette.OnSurface, fontWeight = FontWeight.Bold) },
            text = { Text("You have unsaved changes. Are you sure you want to discard them and go back?", color = Palette.OnSurfaceVariant) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissDiscardConfirm()
                        onBack()
                    }
                ) {
                    Text("Discard", color = Palette.Error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissDiscardConfirm() }
                ) {
                    Text("Keep Editing", color = Palette.Outline)
                }
            }
        )
    }
}
