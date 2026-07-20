package app.lazydex.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lazydex.BuildConfig
import app.lazydex.ui.dex.DexScreen
import app.lazydex.ui.statistics.StatisticsScreen

enum class ShellTab {
    DEX, STATISTICS, SETTINGS
}

@Composable
fun MainShellScreen(
    onNavigateToAppearance: () -> Unit,
    onNavigateToDataAndStorage: () -> Unit,
    onNavigateToAbout: () -> Unit,
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
                    selected = currentTab == ShellTab.SETTINGS,
                    onClick = { currentTab = ShellTab.SETTINGS },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    },
                    label = { Text("Settings") }
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
                        modifier = Modifier.fillMaxSize()
                    )
                }
                ShellTab.STATISTICS -> {
                    StatisticsScreen(
                        modifier = Modifier.fillMaxSize()
                    )
                }
                ShellTab.SETTINGS -> {
                    SettingsTabContent(
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

@Composable
private fun SettingsTabContent(
    onNavigateToAppearance: () -> Unit,
    onNavigateToDataAndStorage: () -> Unit,
    onNavigateToAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "L/D",
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        SettingsCategoryItem(
            title = "Appearance",
            subtitle = "Theme, date & time format",
            icon = Icons.Default.Palette,
            onClick = onNavigateToAppearance
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        SettingsCategoryItem(
            title = "Data and storage",
            subtitle = "Manual & automatic backups, storage space",
            icon = Icons.Default.Storage,
            onClick = onNavigateToDataAndStorage
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        SettingsCategoryItem(
            title = "About",
            subtitle = "LazyDex Stable ${BuildConfig.VERSION_NAME}",
            icon = Icons.Default.Info,
            onClick = onNavigateToAbout
        )
    }
}

@Composable
private fun SettingsCategoryItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
    }
}
