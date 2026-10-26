@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.lazydex.ui.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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

    var includeCoversExport by remember { mutableStateOf(false) }
    var showRestoringProgressBanner by remember { mutableStateOf(true) }

    // Launcher for exporting database (SAF CreateDocument)
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        onResult = { uri ->
            if (uri != null) {
                viewModel.exportBackup(context, uri, includeCoversExport)
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

    // Export Covers Dialog
    if (state.showExportCoversDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showExportCoversDialog(false) },
            title = { Text("Export Options") },
            text = { Text("Do you want to package local cover images in your backup? Including covers increases backup file size.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        includeCoversExport = true
                        exportLauncher.launch("backup_${System.currentTimeMillis()}.lazydex")
                    }
                ) {
                    Text("Metadata + Covers")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        includeCoversExport = false
                        exportLauncher.launch("backup_${System.currentTimeMillis()}.lazydex")
                    }
                ) {
                    Text("Metadata Only")
                }
            }
        )
    }

    // Import merge / overwrite dialog
    state.importedBackup?.let { backup ->
        var holdActive by remember { mutableStateOf(false) }
        var holdProgress by remember { mutableStateOf(0f) }

        // Core press-and-hold trigger coroutine loop
        LaunchedEffect(holdActive) {
            if (holdActive) {
                val startTime = System.currentTimeMillis()
                val duration = 5000f // 5 seconds
                while (holdActive && holdProgress < 1f) {
                    val elapsed = System.currentTimeMillis() - startTime
                    holdProgress = (elapsed / duration).coerceIn(0f, 1f)
                    if (holdProgress >= 1f) {
                        viewModel.executeOverwrite()
                        holdActive = false
                    }
                    delay(30)
                }
            } else {
                holdProgress = 0f
            }
        }

        AlertDialog(
            onDismissRequest = { viewModel.cancelImport() },
            title = { Text("Import Options") },
            text = {
                Column {
                    Text("Found ${backup.items.size} media items in the backup file.")
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Merge: Adds new items and overwrites conflicts where the import file contains a newer timestamp.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Overwrite: Wipes all your local database trackers and covers, replacing them completely with this file.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    if (holdProgress > 0f) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Restoring... Keep holding down the Overwrite button.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { holdProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.executeMerge() },
                    enabled = holdProgress == 0f
                ) {
                    Text("Merge")
                }
            },
            dismissButton = {
                Box(
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                holdActive = true
                                try {
                                    awaitRelease()
                                } finally {
                                    holdActive = false
                                }
                            }
                        )
                    }
                ) {
                    TextButton(
                        onClick = {},
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(
                            text = if (holdActive) {
                                "Holding Overwrite..."
                            } else {
                                "Overwrite (Hold 5s)"
                            }
                        )
                    }
                }
            }
        )
    }
}
