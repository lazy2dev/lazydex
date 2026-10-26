@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.lazydex.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lazydex.data.local.ThemePreset
import app.lazydex.ui.settings.components.PreferenceGroupHeader
import app.lazydex.ui.settings.components.SwitchPreferenceWidget
import org.koin.androidx.compose.koinViewModel

private data class PresetPreviewInfo(
    val preset: ThemePreset,
    val title: String,
    val darkPrimary: Color,
    val darkBg: Color,
    val darkSurface: Color,
    val lightPrimary: Color,
    val lightBg: Color,
    val lightSurface: Color
)

private val PresetsList = listOf(
    PresetPreviewInfo(ThemePreset.DEFAULT, "Default", Color(0xFF5795D9), Color(0xFF1B1D23), Color(0xFF23272C), Color(0xFF2F6DB5), Color(0xFFF8F9FA), Color(0xFFE2E8F0)),
    PresetPreviewInfo(ThemePreset.DYNAMIC, "Dynamic", Color(0xFF8AB4F8), Color(0xFF1F1F1F), Color(0xFF2B2B2B), Color(0xFF1972E8), Color(0xFFF8F9FA), Color(0xFFE2E8F0)),
    PresetPreviewInfo(ThemePreset.MANGA, "Manga", Color(0xFF22C55E), Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF16A34A), Color(0xFFF8F9FA), Color(0xFFE2E8F0)),
    PresetPreviewInfo(ThemePreset.GAME, "Game", Color(0xFFA855F7), Color(0xFF15102A), Color(0xFF281E52), Color(0xFF9333EA), Color(0xFFF8F9FA), Color(0xFFE2E8F0)),
    PresetPreviewInfo(ThemePreset.MOVIE, "Movie", Color(0xFFF97316), Color(0xFF1C1310), Color(0xFF32231E), Color(0xFFEA580C), Color(0xFFF8F9FA), Color(0xFFE2E8F0))
)

@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isDark = when (state.themeMode) {
        "LIGHT" -> false
        "DARK" -> true
        else -> isSystemDark
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Appearance",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go back"
                        )
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

            // ==================== THEME MODE ====================
            PreferenceGroupHeader(title = "Theme mode")

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                val modes = listOf(
                    "SYSTEM" to "System",
                    "LIGHT" to "Light",
                    "DARK" to "Dark"
                )
                modes.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = state.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                        label = { Text(label, fontSize = 13.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ==================== COLOR SCHEME PRESETS ====================
            PreferenceGroupHeader(title = "Color preset")

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(PresetsList, key = { it.preset.name }) { info ->
                    AppThemePreviewItem(
                        info = info,
                        isSelected = state.themePreset == info.preset,
                        isDark = isDark,
                        onClick = { viewModel.setThemePreset(info.preset) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ==================== OPTIONS ====================
            PreferenceGroupHeader(title = "Options")

            SwitchPreferenceWidget(
                title = "Pure black dark mode",
                subtitle = "AMOLED black background in dark theme",
                checked = state.amoledMode,
                onCheckedChange = { viewModel.setAmoledMode(it) },
                enabled = state.themeMode != "LIGHT"
            )

            SwitchPreferenceWidget(
                title = "Theme based on cover",
                subtitle = "Extract accent colors dynamically from media cover art",
                checked = state.coverTheming,
                onCheckedChange = { viewModel.setCoverTheming(it) }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AppThemePreviewItem(
    info: PresetPreviewInfo,
    isSelected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = if (isDark) info.darkPrimary else info.lightPrimary
    val backgroundColor = if (isDark) info.darkBg else info.lightBg
    val surfaceColor = if (isDark) info.darkSurface else info.lightSurface

    Column(
        modifier = modifier
            .width(80.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(width = 76.dp, height = 120.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(backgroundColor)
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .background(primaryColor)
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Content mock lines
                Box(
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .fillMaxWidth(0.7f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(surfaceColor)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .fillMaxWidth()
                        .height(28.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(surfaceColor)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(primaryColor.copy(alpha = 0.6f))
                    )
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(surfaceColor)
                    )
                }
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = info.title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
