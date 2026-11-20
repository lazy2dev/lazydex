package app.lazydex.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lazydex.domain.model.MediaType
import app.lazydex.domain.model.UserStatus
import app.lazydex.domain.model.TypeConfig
import app.lazydex.domain.model.resolveMediaType
import app.lazydex.ui.components.icon
import app.lazydex.ui.browser.BrowserScreen
import app.lazydex.ui.browser.BrowserViewModel
import app.lazydex.ui.dex.DexScreen
import app.lazydex.ui.dex.DexViewModel
import app.lazydex.ui.navigation.components.QuickAddBottomSheet
import app.lazydex.ui.settings.SettingsMainScreen
import app.lazydex.ui.statistics.StatisticsScreen
import app.lazydex.ui.statistics.StatisticsViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

enum class ShellTab {
    DEX, STATISTICS, BROWSE, SETTINGS
}

@Composable
fun MainShellScreen(
    onNavigateToAppearance: () -> Unit,
    onNavigateToTracking: () -> Unit,
    onNavigateToDataAndStorage: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToAddItem: (String?) -> Unit,
    onNavigateToAddItemWithTitle: (String) -> Unit = {},
    onNavigateToEditItem: (String) -> Unit,
    modifier: Modifier = Modifier,
    dexViewModel: DexViewModel = koinViewModel(),
    statisticsViewModel: StatisticsViewModel = koinViewModel(),
    browserViewModel: BrowserViewModel = koinViewModel()
) {
    var currentTab by rememberSaveable { mutableStateOf(ShellTab.DEX) }
    var showQuickAddSheet by rememberSaveable { mutableStateOf(false) }

    val dexLivePageIndex by dexViewModel.activePageIndex.collectAsState()
    val dexUiState by dexViewModel.uiState.collectAsState()
    val dexTabs = dexUiState.topLevelTypes
    val dexActiveTabIndex = remember(dexTabs, dexLivePageIndex, dexUiState.selectedTypeId) {
        if (dexLivePageIndex in 0..dexTabs.lastIndex) {
            dexLivePageIndex
        } else if (dexUiState.selectedTypeId != null) {
            dexTabs.indexOfFirst { it.id == dexUiState.selectedTypeId }.takeIf { it != -1 } ?: 0
        } else {
            0
        }
    }
    val dexActiveType = remember(dexTabs, dexActiveTabIndex) {
        dexTabs.getOrNull(dexActiveTabIndex)?.let { resolveMediaType(it.id) }
    }

    val statsLivePageIndex by statisticsViewModel.activePageIndex.collectAsState()
    val statsUiState by statisticsViewModel.uiState.collectAsState()
    val statsTabs = remember(statsUiState.topLevelTypes, statsUiState.typeCounts) {
        val baseTypes = if (statsUiState.topLevelTypes.isNotEmpty()) {
            statsUiState.topLevelTypes
        } else {
            listOf(
                TypeConfig(id = "NOVEL", name = "Novel", unitName = "Chapter"),
                TypeConfig(id = "MANGA", name = "Manga", unitName = "Chapter"),
                TypeConfig(id = "ANIME", name = "Anime", unitName = "Episode"),
                TypeConfig(id = "GAME", name = "Game", unitName = "Hour"),
                TypeConfig(id = "MOVIE", name = "Movie", unitName = "Movie"),
                TypeConfig(id = "TV", name = "TV", unitName = "Episode")
            )
        }
        baseTypes.filter { tab ->
            val mediaType = resolveMediaType(tab.id)
            (statsUiState.typeCounts[mediaType] ?: 0) > 0
        }
    }
    val statsActiveTabIndex = remember(statsTabs, statsLivePageIndex, statsUiState.selectedType) {
        if (statsLivePageIndex in 0..statsTabs.lastIndex) {
            statsLivePageIndex
        } else if (statsUiState.selectedType != null) {
            statsTabs.indexOfFirst { resolveMediaType(it.id) == statsUiState.selectedType }.takeIf { it != -1 } ?: 0
        } else {
            0
        }
    }
    val statsActiveType = remember(statsTabs, statsActiveTabIndex, statsUiState.selectedType) {
        statsTabs.getOrNull(statsActiveTabIndex)?.let { resolveMediaType(it.id) } ?: statsUiState.selectedType
    }

    val haptic = LocalHapticFeedback.current
    val saveableStateHolder = rememberSaveableStateHolder()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val addInteractionSource = remember { MutableInteractionSource() }
    val isAddPressed by addInteractionSource.collectIsPressedAsState()
    val addScale by animateFloatAsState(
        targetValue = if (isAddPressed) 0.88f else 1f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioMediumBouncy
        ),
        label = "addScale"
    )

    // Komikku Back Navigation: Return to Dex tab before exiting app
    BackHandler(enabled = currentTab != ShellTab.DEX && !dexUiState.isSelectionMode) {
        currentTab = ShellTab.DEX
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            AnimatedVisibility(
                visible = !dexUiState.isSelectionMode || currentTab != ShellTab.DEX,
                enter = expandVertically(animationSpec = tween(200, easing = FastOutSlowInEasing)),
                exit = shrinkVertically(animationSpec = tween(200, easing = FastOutSlowInEasing))
            ) {
                KomikkuNavigationBar {
                    // 1. Dex Tab
                    NavigationBarItem(
                        selected = currentTab == ShellTab.DEX,
                        onClick = { currentTab = ShellTab.DEX },
                        icon = {
                            ShellNavPillIcon(
                                isSelected = currentTab == ShellTab.DEX,
                                defaultIcon = Icons.Default.Bookmark,
                                activeType = dexActiveType,
                                totalTabs = dexTabs.size,
                                activeTabIndex = dexActiveTabIndex,
                                contentDescription = "Dex"
                            )
                        },
                        label = null,
                        alwaysShowLabel = false
                    )

                    // 2. Statistics Tab (Same side as Dex!)
                    NavigationBarItem(
                        selected = currentTab == ShellTab.STATISTICS,
                        onClick = { currentTab = ShellTab.STATISTICS },
                        icon = {
                            ShellNavPillIcon(
                                isSelected = currentTab == ShellTab.STATISTICS,
                                defaultIcon = Icons.Default.BarChart,
                                activeType = statsActiveType,
                                totalTabs = statsTabs.size,
                                activeTabIndex = statsActiveTabIndex,
                                contentDescription = "Statistics"
                            )
                        },
                        label = null,
                        alwaysShowLabel = false
                    )

                    // 3. Central Add Action (Quick Add Modal Sheet)
                    NavigationBarItem(
                        selected = false,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showQuickAddSheet = true
                        },
                        interactionSource = addInteractionSource,
                        icon = {
                            Box(
                                modifier = Modifier
                                    .graphicsLayer {
                                        scaleX = addScale
                                        scaleY = addScale
                                    }
                                    .size(width = 56.dp, height = 32.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(16.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        },
                        label = null,
                        alwaysShowLabel = false
                    )

                    // 4. Browse Tab (Same side as Settings!)
                    NavigationBarItem(
                        selected = currentTab == ShellTab.BROWSE,
                        onClick = { currentTab = ShellTab.BROWSE },
                        icon = { Icon(imageVector = Icons.Default.Explore, contentDescription = "Browse") },
                        label = null,
                        alwaysShowLabel = false
                    )

                    // 5. Settings Tab
                    NavigationBarItem(
                        selected = currentTab == ShellTab.SETTINGS,
                        onClick = { currentTab = ShellTab.SETTINGS },
                        icon = { Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings") },
                        label = null,
                        alwaysShowLabel = false
                    )
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
                .consumeWindowInsets(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(200, easing = LinearEasing))
                        .togetherWith(fadeOut(animationSpec = tween(150, easing = LinearEasing)))
                },
                label = "tabContent"
            ) { targetTab ->
                saveableStateHolder.SaveableStateProvider(key = targetTab) {
                    when (targetTab) {
                        ShellTab.DEX -> {
                            DexScreen(
                                onNavigateToAddItem = { showQuickAddSheet = true },
                                onNavigateToEditItem = onNavigateToEditItem,
                                viewModel = dexViewModel,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        ShellTab.STATISTICS -> {
                            StatisticsScreen(
                                viewModel = statisticsViewModel,
                                onNavigateToItem = onNavigateToEditItem,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        ShellTab.BROWSE -> {
                            BrowserScreen(
                                onNavigateToCreateFromUrl = { url -> onNavigateToAddItem(url) },
                                onNavigateToManualAdd = { showQuickAddSheet = true },
                                onNavigateToCreateManual = onNavigateToAddItemWithTitle,
                                viewModel = browserViewModel,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        ShellTab.SETTINGS -> {
                            SettingsMainScreen(
                                onNavigateToAppearance = onNavigateToAppearance,
                                onNavigateToTracking = onNavigateToTracking,
                                onNavigateToDataAndStorage = onNavigateToDataAndStorage,
                                onNavigateToAbout = onNavigateToAbout,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }

    if (showQuickAddSheet) {
        QuickAddBottomSheet(
            onDismiss = { showQuickAddSheet = false },
            onQuickAdd = { title, type, format, status, progress, total, coverPath, coverUrl ->
                showQuickAddSheet = false
                dexViewModel.quickAdd(title, type, format, status, progress, total, coverPath, coverUrl) { createdItem ->
                    coroutineScope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = "Added \"${createdItem.title}\" to Dex",
                            actionLabel = "View",
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            onNavigateToEditItem(createdItem.id)
                        }
                    }
                }
            },
            onAddAndEdit = { title, type, format, status, progress, total, coverPath, coverUrl ->
                showQuickAddSheet = false
                dexViewModel.quickAdd(title, type, format, status, progress, total, coverPath, coverUrl) { createdItem ->
                    onNavigateToEditItem(createdItem.id)
                }
            },
            onImportUrl = { url ->
                showQuickAddSheet = false
                onNavigateToAddItem(url)
            },
            onNavigateToBrowse = { query ->
                showQuickAddSheet = false
                currentTab = ShellTab.BROWSE
                if (query.isNotBlank()) {
                    browserViewModel.updateSearchQuery(query)
                    browserViewModel.performSearch()
                }
            },
            onNavigateToExisting = { id ->
                showQuickAddSheet = false
                onNavigateToEditItem(id)
            },
            existingItems = dexUiState.items
        )
    }
}

/**
 * 1:1 Komikku NavigationBar with no horizontal spacer.
 * Based on tachiyomi.presentation.core.components.material.NavigationBar.
 */
@Composable
private fun KomikkuNavigationBar(
    modifier: Modifier = Modifier,
    containerColor: Color = NavigationBarDefaults.containerColor,
    contentColor: Color = MaterialTheme.colorScheme.contentColorFor(containerColor),
    tonalElevation: Dp = NavigationBarDefaults.Elevation,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(windowInsets)
                .height(64.dp)
                .selectableGroup(),
            content = content
        )
    }
}

/**
 * Compact icon container reporting strictly 24dp size to NavigationBarItem,
 * guaranteeing the active indicator pill stays at exact 64dp x 32dp native geometry (Image 2).
 * Indicator dots for previous/next tabs are positioned cleanly at left/right inside the pill.
 */
@Composable
private fun ShellNavPillIcon(
    isSelected: Boolean,
    defaultIcon: ImageVector,
    activeType: MediaType?,
    totalTabs: Int,
    activeTabIndex: Int,
    contentDescription: String
) {
    val targetIcon = if (isSelected && activeType != null) {
        activeType.icon()
    } else {
        defaultIcon
    }

    Box(
        modifier = Modifier
            .size(24.dp)
            .wrapContentSize(align = Alignment.Center, unbounded = true),
        contentAlignment = Alignment.Center
    ) {
        // 1. Previous tab indicator dot (left)
        AnimatedVisibility(
            visible = isSelected && totalTabs > 1 && activeTabIndex > 0,
            enter = fadeIn(tween(200)) + scaleIn(tween(200)),
            exit = fadeOut(tween(200)) + scaleOut(tween(200))
        ) {
            Box(
                modifier = Modifier
                    .offset(x = (-20).dp)
                    .size(4.5.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.55f))
            )
        }

        // 2. Centered Icon with directional carousel slide transition
        AnimatedContent(
            targetState = targetIcon to activeTabIndex,
            transitionSpec = {
                val forward = targetState.second > initialState.second
                val slideDistance = 20
                if (forward) {
                    (slideInHorizontally(tween(250, easing = FastOutSlowInEasing)) { slideDistance } + fadeIn(tween(200)))
                        .togetherWith(slideOutHorizontally(tween(250, easing = FastOutSlowInEasing)) { -slideDistance } + fadeOut(tween(200)))
                } else {
                    (slideInHorizontally(tween(250, easing = FastOutSlowInEasing)) { -slideDistance } + fadeIn(tween(200)))
                        .togetherWith(slideOutHorizontally(tween(250, easing = FastOutSlowInEasing)) { slideDistance } + fadeOut(tween(200)))
                }
            },
            label = "shellNavIcon"
        ) { (icon, _) ->
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(24.dp)
            )
        }

        // 3. Next tab indicator dot (right)
        AnimatedVisibility(
            visible = isSelected && totalTabs > 1 && activeTabIndex < totalTabs - 1,
            enter = fadeIn(tween(200)) + scaleIn(tween(200)),
            exit = fadeOut(tween(200)) + scaleOut(tween(200))
        ) {
            Box(
                modifier = Modifier
                    .offset(x = 20.dp)
                    .size(4.5.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.55f))
            )
        }
    }
}
