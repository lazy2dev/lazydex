package app.lazydex.ui.settings

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lazydex.data.anilist.model.ScoreFormat
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
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
    var scoreFormatDropdownExpanded by remember { mutableStateOf(false) }

    // Android 13+ Notification permission launcher
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            viewModel.initiateAnilistAuth(context)
        }
    )

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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ==================== ANILIST SYNC SECTION ====================
            Text(
                text = "Tracking services",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "AniList Sync",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (state.isAnilistLoggedIn) "Connected as ${state.anilistUsername ?: "User"}" else "Not connected",
                                fontSize = 12.sp,
                                color = if (state.isAnilistLoggedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        if (state.isSyncing) {
                            LinearProgressIndicator(modifier = Modifier.width(60.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (state.isAnilistLoggedIn) {
                        // Rating format preference
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Rating Scale",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Box {
                                OutlinedButton(onClick = { scoreFormatDropdownExpanded = true }) {
                                    Text(state.scoreFormat.displayName, fontSize = 12.sp)
                                }
                                DropdownMenu(
                                    expanded = scoreFormatDropdownExpanded,
                                    onDismissRequest = { scoreFormatDropdownExpanded = false }
                                ) {
                                    ScoreFormat.entries.forEach { format ->
                                        DropdownMenuItem(
                                            text = { Text(format.displayName) },
                                            onClick = {
                                                viewModel.setScoreFormat(format)
                                                scoreFormatDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { viewModel.performManualSync() },
                                modifier = Modifier.weight(1f),
                                enabled = !state.isSyncing
                            ) {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Sync", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Sync Now", fontSize = 13.sp)
                            }
                            OutlinedButton(
                                onClick = { viewModel.logoutAnilist() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Disconnect", fontSize = 13.sp)
                            }
                        }

                        // MALSync-style Real-time Progress Display
                        when (val syncState = state.syncProgressState) {
                            is app.lazydex.data.anilist.SyncProgressState.Fetching -> {
                                Spacer(modifier = Modifier.height(10.dp))
                                androidx.compose.material3.Surface(
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            androidx.compose.material3.CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Fetching ${syncState.type} (Page ${syncState.page})...",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        if (syncState.itemsFetched > 0) {
                                            Text(
                                                text = "${syncState.itemsFetched} remote items retrieved",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            is app.lazydex.data.anilist.SyncProgressState.Analyzing -> {
                                Spacer(modifier = Modifier.height(10.dp))
                                androidx.compose.material3.Surface(
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        androidx.compose.material3.CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Analyzing ${syncState.totalRemoteItems} remote items against database...",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                            is app.lazydex.data.anilist.SyncProgressState.Syncing -> {
                                Spacer(modifier = Modifier.height(10.dp))
                                androidx.compose.material3.Surface(
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Syncing items...",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "${syncState.current} / ${syncState.total}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        LinearProgressIndicator(
                                            progress = { if (syncState.total > 0) syncState.current.toFloat() / syncState.total.toFloat() else 0f },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Text(
                                            text = syncState.currentItemTitle,
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            is app.lazydex.data.anilist.SyncProgressState.Completed -> {
                                Spacer(modifier = Modifier.height(10.dp))
                                androidx.compose.material3.Surface(
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                    color = androidx.compose.ui.graphics.Color(0xFF1B5E20).copy(alpha = 0.2f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Sync complete: ${syncState.report.importedCount} new items imported, ${syncState.report.updatedCount} items updated",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = androidx.compose.ui.graphics.Color(0xFF4CAF50),
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                            is app.lazydex.data.anilist.SyncProgressState.Error -> {
                                Spacer(modifier = Modifier.height(10.dp))
                                androidx.compose.material3.Surface(
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Sync Error: ${syncState.message}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                            else -> {}
                        }

                        // MALSync Persistent Report Card
                        state.syncReport?.let { report ->
                            Spacer(modifier = Modifier.height(10.dp))
                            app.lazydex.ui.components.SyncReportCard(
                                report = report,
                                onDismiss = { viewModel.dismissSyncReport() }
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    viewModel.initiateAnilistAuth(context)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Connect to AniList", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Sync Resolution Warning Banner
            if (state.pendingResolutionItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Sync Warning",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Sync Conflict: Remote Deletions (${state.pendingResolutionItems.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "The following items were deleted on AniList web. Choose whether to keep them as local-only entries or delete them from LazyDex:",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        state.pendingResolutionItems.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { viewModel.resolveRemoteDeletion(item.id, deleteLocally = false) }) {
                                    Text("Unlink", fontSize = 11.sp)
                                }
                                TextButton(
                                    onClick = { viewModel.resolveRemoteDeletion(item.id, deleteLocally = true) },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Delete", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ==================== STORAGE SECTION ====================
            Text(
                text = "Backup and restore",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Local Backups",
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Export your local media tracker data and covers, or import backups using Android SAF.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Information box
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Info",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "You should keep copies of backups in other places as well. Backups may contain sensitive data; be careful if sharing.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // RESTORING PROGRESS SWITCH
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Show restoring progress banner",
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Switch(
                    checked = showRestoringProgressBanner,
                    onCheckedChange = { showRestoringProgressBanner = it }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
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

        LaunchedEffect(holdActive) {
            if (holdActive) {
                val startTime = System.currentTimeMillis()
                val duration = 5000f
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
                            progress = holdProgress,
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
