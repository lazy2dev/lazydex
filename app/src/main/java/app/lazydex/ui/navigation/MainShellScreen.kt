package app.lazydex.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lazydex.ui.browser.BrowserScreen
import app.lazydex.ui.browser.BrowserViewModel
import app.lazydex.ui.dex.DexScreen
import app.lazydex.ui.dex.DexViewModel
import app.lazydex.ui.settings.SettingsMainScreen
import app.lazydex.ui.statistics.StatisticsScreen
import org.koin.androidx.compose.koinViewModel

enum class ShellTab {
    DEX, BROWSE, STATISTICS, SETTINGS
}

@Composable
fun MainShellScreen(
    onNavigateToAppearance: () -> Unit,
    onNavigateToTracking: () -> Unit,
    onNavigateToExtensions: () -> Unit,
    onNavigateToDataAndStorage: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToAddItem: (String?) -> Unit,
    onNavigateToEditItem: (String) -> Unit,
    modifier: Modifier = Modifier,
    dexViewModel: DexViewModel = koinViewModel(),
    browserViewModel: BrowserViewModel = koinViewModel()
) {
    var currentTab by rememberSaveable { mutableStateOf(ShellTab.DEX) }
    val dexUiState by dexViewModel.uiState.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (!dexUiState.isSelectionMode || currentTab != ShellTab.DEX) {
                NavigationBar {
                    // 1. Dex Tab
                    NavigationBarItem(
                        selected = currentTab == ShellTab.DEX,
                        onClick = { currentTab = ShellTab.DEX },
                        icon = { Icon(imageVector = Icons.Default.Bookmark, contentDescription = "Dex") },
                        label = { Text("Dex") }
                    )

                    // 2. Browse Tab
                    NavigationBarItem(
                        selected = currentTab == ShellTab.BROWSE,
                        onClick = { currentTab = ShellTab.BROWSE },
                        icon = { Icon(imageVector = Icons.Default.Explore, contentDescription = "Browse") },
                        label = { Text("Browse") }
                    )

                    // 3. Central Add Action
                    NavigationBarItem(
                        selected = false,
                        onClick = { onNavigateToAddItem(null) },
                        icon = {
                            Box(
                                modifier = Modifier
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
                        label = { Text("Add") }
                    )

                    // 4. Statistics Tab
                    NavigationBarItem(
                        selected = currentTab == ShellTab.STATISTICS,
                        onClick = { currentTab = ShellTab.STATISTICS },
                        icon = { Icon(imageVector = Icons.Default.BarChart, contentDescription = "Statistics") },
                        label = { Text("Stats") }
                    )

                    // 5. Settings Tab
                    NavigationBarItem(
                        selected = currentTab == ShellTab.SETTINGS,
                        onClick = { currentTab = ShellTab.SETTINGS },
                        icon = { Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") }
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
            when (currentTab) {
                ShellTab.DEX -> {
                    DexScreen(
                        onNavigateToAddItem = { onNavigateToAddItem(null) },
                        onNavigateToEditItem = onNavigateToEditItem,
                        viewModel = dexViewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                ShellTab.BROWSE -> {
                    BrowserScreen(
                        onNavigateToCreateFromUrl = { url -> onNavigateToAddItem(url) },
                        onNavigateToManualAdd = { onNavigateToAddItem(null) },
                        viewModel = browserViewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                ShellTab.STATISTICS -> {
                    StatisticsScreen(
                        onNavigateToItem = onNavigateToEditItem,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                ShellTab.SETTINGS -> {
                    SettingsMainScreen(
                        onNavigateToAppearance = onNavigateToAppearance,
                        onNavigateToTracking = onNavigateToTracking,
                        onNavigateToExtensions = onNavigateToExtensions,
                        onNavigateToDataAndStorage = onNavigateToDataAndStorage,
                        onNavigateToAbout = onNavigateToAbout,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
