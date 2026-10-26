package app.lazydex.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lazydex.ui.settings.SettingsMainScreen
import app.lazydex.ui.browser.BrowserScreen
import app.lazydex.ui.browser.BrowserViewModel
import app.lazydex.ui.dex.DexScreen
import app.lazydex.ui.dex.DexViewModel
import app.lazydex.ui.history.HistoryScreen
import app.lazydex.ui.statistics.StatisticsScreen
import org.koin.androidx.compose.koinViewModel

enum class ShellTab {
    DEX, STATISTICS, BROWSE, HISTORY, SETTINGS
}

@Composable
fun MainShellScreen(
    onNavigateToAppearance: () -> Unit,
    onNavigateToDataAndStorage: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToAddItem: (String?) -> Unit,
    onNavigateToEditItem: (String) -> Unit,
    modifier: Modifier = Modifier,
    dexViewModel: DexViewModel = koinViewModel(),
    browserViewModel: BrowserViewModel = koinViewModel()
) {
    var currentTab by rememberSaveable { mutableStateOf(ShellTab.DEX) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                // 1. Dex Tab
                NavigationBarItem(
                    selected = currentTab == ShellTab.DEX,
                    onClick = { currentTab = ShellTab.DEX },
                    icon = { Icon(imageVector = Icons.Default.Bookmark, contentDescription = "Dex") },
                    label = { Text("Dex") }
                )

                // 2. Statistics Tab
                NavigationBarItem(
                    selected = currentTab == ShellTab.STATISTICS,
                    onClick = { currentTab = ShellTab.STATISTICS },
                    icon = { Icon(imageVector = Icons.Default.BarChart, contentDescription = "Statistics") },
                    label = { Text("Stats") }
                )

                // 3. Add (+) Button — Center Hub
                NavigationBarItem(
                    selected = currentTab == ShellTab.BROWSE,
                    onClick = {
                        if (currentTab == ShellTab.BROWSE) {
                            browserViewModel.selectSource(null)
                        } else {
                            currentTab = ShellTab.BROWSE
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add",
                            tint = if (currentTab == ShellTab.BROWSE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                    },
                    label = { Text("Add") }
                )

                // 4. History Tab
                NavigationBarItem(
                    selected = currentTab == ShellTab.HISTORY,
                    onClick = { currentTab = ShellTab.HISTORY },
                    icon = { Icon(imageVector = Icons.Default.History, contentDescription = "History") },
                    label = { Text("History") }
                )

                // 5. Settings Tab
                NavigationBarItem(
                    selected = currentTab == ShellTab.SETTINGS,
                    onClick = { currentTab = ShellTab.SETTINGS },
                    icon = { Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") }
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
            when (currentTab) {
                ShellTab.DEX -> {
                    DexScreen(
                        onNavigateToAddItem = { onNavigateToAddItem(null) },
                        onNavigateToEditItem = onNavigateToEditItem,
                        viewModel = dexViewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                ShellTab.STATISTICS -> {
                    StatisticsScreen(
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
                ShellTab.HISTORY -> {
                    HistoryScreen(
                        onNavigateToEditItem = onNavigateToEditItem,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                ShellTab.SETTINGS -> {
                    SettingsMainScreen(
                        onNavigateToAppearance = onNavigateToAppearance,
                        onNavigateToDataAndStorage = onNavigateToDataAndStorage,
                        onNavigateToAbout = onNavigateToAbout,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
