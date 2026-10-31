@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.lazydex.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lazydex.ui.components.CategoryCreatorSheet
import app.lazydex.ui.components.CategoryPickerSheet
import app.lazydex.ui.components.getCategoryIcon
import app.lazydex.ui.dex.DexScreen
import app.lazydex.ui.dex.DexViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun MainShellScreen(
    onNavigateToAppearance: () -> Unit,
    onNavigateToExtensions: () -> Unit,
    onNavigateToDataAndStorage: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToAddItem: (String?) -> Unit,
    onNavigateToEditItem: (String) -> Unit,
    onNavigateToBrowseSources: () -> Unit,
    onNavigateToStatistics: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    dexViewModel: DexViewModel = koinViewModel()
) {
    val uiState by dexViewModel.uiState.collectAsState()
    val pinnedCategories by dexViewModel.pinnedCategories.collectAsState()
    val topLevelCategories by dexViewModel.topLevelCategories.collectAsState()

    var isScrollableNavMode by rememberSaveable { mutableStateOf(false) }
    var showCategoryPickerSheet by rememberSaveable { mutableStateOf(false) }
    var showCategoryCreatorSheet by rememberSaveable { mutableStateOf(false) }

    val isAnyPinnedSelected = pinnedCategories.any { it.id == uiState.selectedCategoryId }
    val isLibrarySelected = !isAnyPinnedSelected

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                AnimatedContent(
                    targetState = isScrollableNavMode,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "BottomNavModeTransition"
                ) { inScrollableMode ->
                    if (inScrollableMode) {
                        // SCROLLABLE BY BOX: Displays ALL categories in the same bottom nav
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .windowInsetsPadding(NavigationBarDefaults.windowInsets)
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. Collapse back to 4 pinned + Library
                            ScrollableCategoryBox(
                                name = "Pinned",
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                isSelected = false,
                                onClick = { isScrollableNavMode = false }
                            )

                            // 2. All Library Box
                            ScrollableCategoryBox(
                                name = "All",
                                icon = Icons.Outlined.CollectionsBookmark,
                                isSelected = uiState.selectedCategoryId == null,
                                onClick = {
                                    dexViewModel.selectCategoryId(null)
                                }
                            )

                            // 3. Every Category Box
                            topLevelCategories.forEach { category ->
                                val isSelected = uiState.selectedCategoryId == category.id
                                ScrollableCategoryBox(
                                    name = category.name,
                                    icon = getCategoryIcon(category),
                                    isSelected = isSelected,
                                    onClick = {
                                        dexViewModel.selectCategoryId(category.id)
                                    }
                                )
                            }

                            // 4. + New Category Box
                            ScrollableCategoryBox(
                                name = "+ New",
                                icon = Icons.Default.Add,
                                isSelected = false,
                                onClick = {
                                    showCategoryCreatorSheet = true
                                }
                            )
                        }
                    } else {
                        // PINNED MODE: 4 Pinned Categories + Library ▾ (Material 3 5-item max)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .windowInsetsPadding(NavigationBarDefaults.windowInsets),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1-4. The 4 Pinned Categories
                            pinnedCategories.take(4).forEach { category ->
                                val isSelected = uiState.selectedCategoryId == category.id
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = { dexViewModel.selectCategoryId(category.id) },
                                    icon = {
                                        Icon(
                                            imageVector = getCategoryIcon(category),
                                            contentDescription = category.name
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = category.name,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                )
                            }

                            // 5. Library ▾: Tap OR Hold-Click toggles scrollable all-categories bottom nav
                            LibraryNavItem(
                                isSelected = isLibrarySelected,
                                onTap = {
                                    isScrollableNavMode = true
                                },
                                onHoldClick = {
                                    isScrollableNavMode = true
                                }
                            )
                        }
                    }
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
            DexScreen(
                onNavigateToAddItem = { onNavigateToAddItem(null) },
                onNavigateToEditItem = onNavigateToEditItem,
                onNavigateToBrowseSources = onNavigateToBrowseSources,
                onNavigateToStatistics = onNavigateToStatistics,
                onNavigateToHistory = onNavigateToHistory,
                onNavigateToSettings = onNavigateToSettings,
                viewModel = dexViewModel,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    if (showCategoryPickerSheet) {
        CategoryPickerSheet(
            categories = topLevelCategories,
            selectedCategoryId = uiState.selectedCategoryId,
            totalItemCount = uiState.totalCount,
            categoryCounts = emptyMap(),
            onSelectCategory = { catId ->
                dexViewModel.selectCategoryId(catId)
            },
            onCreateNewCategory = {
                showCategoryCreatorSheet = true
            },
            onDismiss = {
                showCategoryPickerSheet = false
            }
        )
    }

    if (showCategoryCreatorSheet) {
        CategoryCreatorSheet(
            parentCategory = null,
            onDismiss = { showCategoryCreatorSheet = false },
            onSaveCategory = { cat ->
                dexViewModel.createCategory(cat)
                showCategoryCreatorSheet = false
            }
        )
    }
}

@Composable
private fun ScrollableCategoryBox(
    name: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = CircleShape,
        modifier = modifier
            .width(68.dp)
            .fillMaxHeight()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width(56.dp)
                    .height(32.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = name,
                    tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LibraryNavItem(
    isSelected: Boolean,
    onTap: () -> Unit,
    onHoldClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(72.dp)
            .fillMaxHeight()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 32.dp),
                onClick = onTap,
                onLongClick = onHoldClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width(64.dp)
                    .height(32.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Icon(
                        imageVector = Icons.Default.Bookmarks,
                        contentDescription = "Library",
                        tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(24.dp)
                            .padding(end = 2.dp, bottom = 1.dp)
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(14.dp)
                            .offset(x = 5.dp, y = 3.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Library ▾",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
