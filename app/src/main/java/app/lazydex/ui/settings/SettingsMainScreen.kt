@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.lazydex.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lazydex.BuildConfig
import app.lazydex.ui.settings.components.TextPreferenceWidget

@Composable
fun SettingsMainScreen(
    onNavigateToAppearance: () -> Unit,
    onNavigateToExtensions: () -> Unit,
    onNavigateToDataAndStorage: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToCategories: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            TextPreferenceWidget(
                title = "Categories",
                subtitle = "Manage categories, subtypes & pinned bar items",
                icon = Icons.Default.Category,
                onClick = onNavigateToCategories
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            TextPreferenceWidget(
                title = "Appearance",
                subtitle = "Theme mode, color preset & pure black",
                icon = Icons.Default.Palette,
                onClick = onNavigateToAppearance
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            TextPreferenceWidget(
                title = "Extensions",
                subtitle = "Repositories, scrapers & multi-runtime store",
                icon = Icons.Default.Extension,
                onClick = onNavigateToExtensions
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            TextPreferenceWidget(
                title = "Data and storage",
                subtitle = "Storage telemetry, backups, cache cleaning",
                icon = Icons.Default.Storage,
                onClick = onNavigateToDataAndStorage
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            TextPreferenceWidget(
                title = "About",
                subtitle = "LazyDex Beta v${BuildConfig.VERSION_NAME}",
                icon = Icons.Default.Info,
                onClick = onNavigateToAbout
            )
        }
    }
}
