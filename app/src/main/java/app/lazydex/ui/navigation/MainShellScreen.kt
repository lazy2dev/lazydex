package app.lazydex.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.lazydex.ui.dex.DexScreen
import app.lazydex.ui.statistics.StatisticsScreen

enum class ShellTab {
    DEX, STATISTICS, MORE
}

@Composable
fun MainShellScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToAddItem: () -> Unit,
    onNavigateToEditItem: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentTab by rememberSaveable { mutableStateOf(ShellTab.DEX) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentTab == ShellTab.DEX,
                    onClick = { currentTab = ShellTab.DEX },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Book,
                            contentDescription = "Dex"
                        )
                    },
                    label = { Text("Dex") }
                )
                NavigationBarItem(
                    selected = currentTab == ShellTab.STATISTICS,
                    onClick = { currentTab = ShellTab.STATISTICS },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = "Statistics"
                        )
                    },
                    label = { Text("Statistics") }
                )
                NavigationBarItem(
                    selected = currentTab == ShellTab.MORE,
                    onClick = { currentTab = ShellTab.MORE },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.MoreHoriz,
                            contentDescription = "More"
                        )
                    },
                    label = { Text("More") }
                )
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                ShellTab.DEX -> {
                    DexScreen(
                        onNavigateToAddItem = onNavigateToAddItem,
                        onNavigateToEditItem = onNavigateToEditItem,
                        onNavigateToSettings = onNavigateToSettings,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                ShellTab.STATISTICS -> {
                    StatisticsScreen(
                        modifier = Modifier.fillMaxSize()
                    )
                }
                ShellTab.MORE -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "More Screen Placeholder")
                    }
                }
            }
        }
    }
}
