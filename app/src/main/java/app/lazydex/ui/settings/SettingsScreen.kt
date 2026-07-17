package app.lazydex.ui.settings

import android.content.Context
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    var includeCoversExport by remember { mutableStateOf(false) }
    var showLicensesDialog by remember { mutableStateOf(false) }

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
                        text = "Settings",
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
                .verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ==================== DATA SECTION ====================
            Text(
                text = "Data Operations",
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
                        text = "Backups",
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
                        Button(
                            onClick = { viewModel.showExportCoversDialog(true) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Export Backup", fontSize = 13.sp)
                        }
                        Button(
                            onClick = { importLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Import Backup", fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ==================== THEME SECTION ====================
            Text(
                text = "Appearance",
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
                        text = "Theme Mode",
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("SYSTEM", "DARK", "LIGHT").forEach { mode ->
                            FilterChip(
                                selected = state.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                label = { Text(mode.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 12.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "AMOLED Mode",
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Use pure black (#000000) backgrounds in dark mode.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = state.amoledMode,
                            onCheckedChange = { viewModel.setAmoledMode(it) },
                            enabled = state.themeMode != "LIGHT"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ==================== ABOUT SECTION ====================
            Text(
                text = "About",
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
                        text = "LazyDex",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Version 0.0.1-beta",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Built with Kotlin & Jetpack Compose. A lightweight, privacy-first, local-only Android media consumption tracker.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { showLicensesDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Open Source Licenses", fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
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
                    // Styled warning button that responds to pointer hold
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

    // Licenses Dialog
    if (showLicensesDialog) {
        AlertDialog(
            onDismissRequest = { showLicensesDialog = false },
            title = { Text("Open Source Licenses") },
            text = {
                Column(
                    modifier = Modifier
                        .height(300.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Jetpack Compose & Material3\nApache License 2.0\n\n" +
                               "Room Database\nApache License 2.0\n\n" +
                               "Koin DI\nApache License 2.0\n\n" +
                               "OkHttp 4.x\nApache License 2.0\n\n" +
                               "Jsoup\nMIT License\n\n" +
                               "Coil v3 Image Loader\nApache License 2.0\n\n" +
                               "Kotlinx Serialization\nApache License 2.0\n\n" +
                               "DataStore Preferences\nApache License 2.0",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicensesDialog = false }) {
                    Text("Dismiss")
                }
            }
        )
    }
}
