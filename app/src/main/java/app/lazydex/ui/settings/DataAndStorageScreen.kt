@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.lazydex.ui.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lazydex.ui.settings.components.PreferenceGroupHeader
import app.lazydex.ui.settings.components.SwitchPreferenceWidget
import app.lazydex.ui.settings.components.TextPreferenceWidget
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

@Composable
fun DataAndStorageScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var exportIncludeCovers by rememberSaveable { mutableStateOf(true) }
    var exportExcludeAniList by rememberSaveable { mutableStateOf(false) }
    var exportSelectedTypes by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var showRestoringProgressBanner by rememberSaveable { mutableStateOf(true) }

    // Launcher for exporting database (SAF CreateDocument)
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        onResult = { uri ->
            if (uri != null) {
                viewModel.exportBackup(
                    context = context,
                    uri = uri,
                    options = BackupExportOptions(
                        includeCovers = exportIncludeCovers,
                        excludeAniList = exportExcludeAniList,
                        selectedTypes = exportSelectedTypes.toSet()
                    )
                )
            }
        }
    )

    // Launcher for importing database (SAF OpenDocument)
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.importBackup(context, uri)
            }
        }
    )

    // Handle ViewModel success / error toast notifications
    LaunchedEffect(state.successMsg, state.errorMsg) {
        state.successMsg?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearMessages()
        }
        state.errorMsg?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Data and storage",
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

            // ==================== STORAGE SECTION ====================
            PreferenceGroupHeader(title = "Storage space")

            val usedBytes = (state.storageStats.totalBytes - state.storageStats.availableBytes).coerceAtLeast(0L)
            val progress = if (state.storageStats.totalBytes > 0) {
                (usedBytes.toFloat() / state.storageStats.totalBytes.toFloat()).coerceIn(0f, 1f)
            } else 0f

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Available: ${formatBytes(state.storageStats.availableBytes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Total: ${formatBytes(state.storageStats.totalBytes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            }

            TextPreferenceWidget(
                title = "Clear temporary cache",
                subtitle = "Free space from transient image & network cache (${formatBytes(state.storageStats.cacheBytes)})",
                icon = Icons.Default.CleaningServices,
                onClick = { viewModel.clearTemporaryCache() }
            )

            TextPreferenceWidget(
                title = "Clean orphaned covers",
                subtitle = "Delete cached covers no longer linked to any library item (${formatBytes(state.storageStats.coversBytes)} cover storage)",
                icon = Icons.Default.DeleteSweep,
                onClick = { viewModel.cleanOrphanCovers() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ==================== BACKUP & RESTORE ====================
            PreferenceGroupHeader(title = "Backup and restore")

            Text(
                text = "Export your local media tracker data and covers, or import backups using Android SAF.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.showExportCoversDialog(true) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Create backup", fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Restore backup", fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Info",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "You should keep copies of backups in other places as well. Backups may contain sensitive data; be careful if sharing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SwitchPreferenceWidget(
                title = "Show restoring progress banner",
                subtitle = "Display notification banner while restoring database",
                checked = showRestoringProgressBanner,
                onCheckedChange = { showRestoringProgressBanner = it }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Export Options Dialog
    if (state.showExportCoversDialog) {
        val availableTypes = listOf("NOVEL", "MANGA", "ANIME", "GAME", "MOVIE", "TV")
        AlertDialog(
            onDismissRequest = { viewModel.showExportCoversDialog(false) },
            title = { Text("Backup Options") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "Filter Media Types:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = exportSelectedTypes.isEmpty(),
                            onClick = { exportSelectedTypes = emptyList() },
                            label = { Text("All") }
                        )
                        availableTypes.forEach { type ->
                            val isSelected = exportSelectedTypes.contains(type)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    exportSelectedTypes = if (isSelected) {
                                        exportSelectedTypes - type
                                    } else {
                                        exportSelectedTypes + type
                                    }
                                },
                                label = { Text(type.lowercase().replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { exportExcludeAniList = !exportExcludeAniList },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = exportExcludeAniList,
                            onCheckedChange = { exportExcludeAniList = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Exclude AniList synced items",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Keeps only pure local media (e.g. novels)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { exportIncludeCovers = !exportIncludeCovers },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = exportIncludeCovers,
                            onCheckedChange = { exportIncludeCovers = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Include local cover images",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Packages covers into backup file",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.showExportCoversDialog(false)
                        exportLauncher.launch("backup_${System.currentTimeMillis()}.lazydex")
                    }
                ) {
                    Text("Export", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showExportCoversDialog(false) }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Restore Dialog
    state.importedBackup?.let { backup ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelImport() },
            icon = {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "Restore backup?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Found ${backup.items.size} media items in backup.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Clean Overwrite: Completely replaces current library with this backup.\n• Merge: Combines backup with current library, protecting newer progress.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { viewModel.executeOverwrite() },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Clean Overwrite", fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        onClick = { viewModel.restoreBackup() }
                    ) {
                        Text("Merge", fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.cancelImport() }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
