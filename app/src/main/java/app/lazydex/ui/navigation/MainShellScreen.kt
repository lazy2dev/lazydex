@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.lazydex.ui.navigation

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    var showCategoryPickerSheet by rememberSaveable { mutableStateOf(false) }
    var showCategoryCreatorSheet by rememberSaveable { mutableStateOf(false) }

    val isAnyPinnedSelected = pinnedCategories.any { it.id == uiState.selectedCategoryId }
    val isLibrarySelected = !isAnyPinnedSelected

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
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

                // 5. Library ▾ Item (Tap opens picker sheet; long press also works; arrow hint on icon)
                NavigationBarItem(
                    selected = isLibrarySelected,
                    onClick = { showCategoryPickerSheet = true },
                    icon = {
                        Box(
                            contentAlignment = Alignment.BottomEnd,
                            modifier = Modifier.combinedClickable(
                                onClick = { showCategoryPickerSheet = true },
                                onLongClick = { showCategoryPickerSheet = true }
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bookmarks,
                                contentDescription = "Library",
                                modifier = Modifier.padding(end = 4.dp, bottom = 2.dp)
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(14.dp)
                                    .offset(x = 6.dp, y = 4.dp)
                            )
                        }
                    },
                    label = { Text("Library ▾", maxLines = 1) }
                )
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
