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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.statusBarsPadding
import app.lazydex.BuildConfig
import app.lazydex.domain.model.MediaCategory
import app.lazydex.ui.browser.BrowserScreen
import app.lazydex.ui.components.CategoryDropdown
import app.lazydex.ui.dex.DexScreen
import app.lazydex.ui.dex.DexViewModel
import app.lazydex.ui.statistics.StatisticsScreen
import org.koin.androidx.compose.koinViewModel

enum class ShellTab {
    DEX, STATISTICS, BROWSE, SETTINGS
}

@Composable
fun MainShellScreen(
    onNavigateToAppearance: () -> Unit,
    onNavigateToDataAndStorage: () -> Unit,
    onNavigateToTracking: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToAddItem: (String?) -> Unit,
    onNavigateToEditItem: (String) -> Unit,
    modifier: Modifier = Modifier,
    dexViewModel: DexViewModel = koinViewModel()
) {
    var currentTab by rememberSaveable { mutableStateOf(ShellTab.DEX) }
    var showCategoryDropdown by rememberSaveable { mutableStateOf(false) }

    val dexUiState by dexViewModel.uiState.collectAsState()

    Scaffold(
        bottomBar = {
            NavigationBar {
                // 1. Dex Tab
                val dexIcon = when (dexUiState.selectedCategory) {
                    MediaCategory.NOVEL -> Icons.Default.Book
                    MediaCategory.MANGA -> Icons.Default.MenuBook
                    MediaCategory.ANIME -> Icons.Default.Casino
                    MediaCategory.GAME -> Icons.Default.SportsEsports
                    MediaCategory.MOVIE -> Icons.Default.Movie
                    MediaCategory.TV -> Icons.Default.Tv
                    null -> Icons.Default.Bookmark
                }
                val dexLabel = dexUiState.selectedCategory?.displayName ?: "Dex"

                NavigationBarItem(
                    selected = currentTab == ShellTab.DEX,
                    onClick = {
                        if (currentTab == ShellTab.DEX) {
                            showCategoryDropdown = true
                        } else {
                            currentTab = ShellTab.DEX
                        }
                    },
                    icon = {
                        Box {
                            Icon(imageVector = dexIcon, contentDescription = dexLabel)
                            CategoryDropdown(
                                expanded = showCategoryDropdown,
                                onDismissRequest = { showCategoryDropdown = false },
                                selectedCategory = dexUiState.selectedCategory,
                                perCategoryCounts = dexUiState.perCategoryCounts,
                                onSelectCategory = { cat -> dexViewModel.selectCategory(cat) }
                            )
                        }
                    },
                    label = { Text("$dexLabel ▾") }
                )

                // 2. Statistics Tab
                NavigationBarItem(
                    selected = currentTab == ShellTab.STATISTICS,
                    onClick = { currentTab = ShellTab.STATISTICS },
                    icon = { Icon(imageVector = Icons.Default.BarChart, contentDescription = "Statistics") },
                    label = { Text("Stats") }
                )

                // 3. Add (+) Button — Center Item
                NavigationBarItem(
                    selected = false,
                    onClick = { onNavigateToAddItem(null) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    },
                    label = { Text("Add") }
                )

                // 4. Browse Tab
                NavigationBarItem(
                    selected = currentTab == ShellTab.BROWSE,
                    onClick = { currentTab = ShellTab.BROWSE },
                    icon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Browse") },
                    label = { Text("Browse") }
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
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                        modifier = Modifier.fillMaxSize()
                    )
                }
                ShellTab.SETTINGS -> {
                    SettingsTabContent(
                        onNavigateToAppearance = onNavigateToAppearance,
                        onNavigateToDataAndStorage = onNavigateToDataAndStorage,
                        onNavigateToTracking = onNavigateToTracking,
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
    onNavigateToTracking: () -> Unit,
    onNavigateToAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(16.dp))

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
            title = "Tracking",
            subtitle = "Sync with tracking services like AniList",
            icon = Icons.Default.Sync,
            onClick = onNavigateToTracking
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
            subtitle = "LazyDex Beta v${BuildConfig.VERSION_NAME}",
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
