package app.lazydex.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.lazydex.data.anilist.model.ScoreFormat
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.initiateAnilistAuth(context)
    }

    LaunchedEffect(uiState.errorMsg, uiState.successMsg) {
        uiState.errorMsg?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
        uiState.successMsg?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tracking Services") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Services",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            // AniList Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // AniList Icon / Badge
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF02A9FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "AL",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "AniList",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (uiState.isAnilistLoggedIn) {
                                    "Connected as ${uiState.anilistUsername ?: "User"}"
                                } else {
                                    "Not connected"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (uiState.isAnilistLoggedIn) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (uiState.isAnilistLoggedIn) {
                            OutlinedButton(
                                onClick = { viewModel.logoutAnilist() },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Logout")
                            }
                        } else {
                            Button(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        viewModel.initiateAnilistAuth(context)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF02A9FF)
                                )
                            ) {
                                Text("Connect")
                            }
                        }
                    }

                    if (uiState.isAnilistLoggedIn) {
                        HorizontalDivider()

                        // Score Format Picker
                        var scoreDropdownExpanded by remember { mutableStateOf(false) }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Score Format",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = uiState.scoreFormat.displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Box {
                                OutlinedButton(onClick = { scoreDropdownExpanded = true }) {
                                    Text("Change")
                                }
                                DropdownMenu(
                                    expanded = scoreDropdownExpanded,
                                    onDismissRequest = { scoreDropdownExpanded = false }
                                ) {
                                    ScoreFormat.entries.forEach { format ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    if (format == uiState.scoreFormat) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp),
                                                            tint = MaterialTheme.colorScheme.primary
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                    }
                                                    Text(format.displayName)
                                                }
                                            },
                                            onClick = {
                                                viewModel.setScoreFormat(format)
                                                scoreDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider()

                        // Manual Sync Card / Progress Dashboard
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Sync Library",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "Two-way pull & push with AniList",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Button(
                                    onClick = { viewModel.performManualSync() },
                                    enabled = !uiState.isSyncing
                                ) {
                                    if (uiState.isSyncing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Syncing...")
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Sync,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Sync Now")
                                    }
                                }
                            }

                            // MALSync-style Real-time Progress Display
                            when (val state = uiState.syncProgressState) {
                                is app.lazydex.data.anilist.SyncProgressState.Fetching -> {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Fetching ${state.type} (Page ${state.page})...",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            if (state.itemsFetched > 0) {
                                                Text(
                                                    text = "${state.itemsFetched} remote items retrieved",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(top = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                is app.lazydex.data.anilist.SyncProgressState.Analyzing -> {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Analyzing ${state.totalRemoteItems} remote items against database...",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                                is app.lazydex.data.anilist.SyncProgressState.Syncing -> {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
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
                                                    text = "${state.current} / ${state.total}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            androidx.compose.material3.LinearProgressIndicator(
                                                progress = { if (state.total > 0) state.current.toFloat() / state.total.toFloat() else 0f },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Text(
                                                text = state.currentItemTitle,
                                                style = MaterialTheme.typography.labelMedium,
                                                maxLines = 1,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                is app.lazydex.data.anilist.SyncProgressState.Completed -> {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF1B5E20).copy(alpha = 0.2f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Sync complete: ${state.report.importedCount} new items imported, ${state.report.updatedCount} items updated",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF4CAF50),
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                                is app.lazydex.data.anilist.SyncProgressState.Error -> {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Sync Error: ${state.message}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                                else -> {}
                            }

                            // MALSync Persistent Report Card
                            uiState.syncReport?.let { report ->
                                Spacer(modifier = Modifier.height(8.dp))
                                app.lazydex.ui.components.SyncReportCard(
                                    report = report,
                                    onDismiss = { viewModel.dismissSyncReport() }
                                )
                            }
                        }
                    }
                }
            }

            // Remote Deletion Reconciliation Section
            if (uiState.pendingResolutionItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Remote Deletions Pending Resolution",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )

                uiState.pendingResolutionItems.forEach { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "This item was deleted on AniList.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.resolveRemoteDeletion(item.id, deleteLocally = false) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LinkOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Unlink (Keep Local)")
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Button(
                                    onClick = { viewModel.resolveRemoteDeletion(item.id, deleteLocally = true) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Delete Locally")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
