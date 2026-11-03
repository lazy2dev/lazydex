@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.lazydex.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lazydex.R
import app.lazydex.ui.settings.components.PreferenceGroupHeader
import app.lazydex.ui.settings.components.SwitchPreferenceWidget
import app.lazydex.ui.settings.components.TrackingPreferenceWidget
import org.koin.androidx.compose.koinViewModel

private const val ANILIST_CLIENT_ID = "46821"
private const val ANILIST_AUTH_URL =
    "https://anilist.co/api/v2/oauth/authorize?client_id=$ANILIST_CLIENT_ID&response_type=token"

@Composable
fun SettingsTrackingScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val tracking = uiState.tracking

    var showLoginDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showLoginDialog) {
        AniListLoginDialog(
            isLoggingIn = tracking.isSyncing,
            errorMessage = tracking.syncError,
            onDismissRequest = {
                showLoginDialog = false
                viewModel.clearSyncError()
            },
            onConfirmLogin = { token ->
                viewModel.loginWithToken(token)
            }
        )
    }

    if (showLogoutDialog) {
        AniListLogoutDialog(
            username = tracking.username,
            onDismissRequest = { showLogoutDialog = false },
            onConfirmLogout = {
                viewModel.logoutAniList()
                showLogoutDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Tracking Settings",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
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
            // ==================== 1. SERVICES ====================
            PreferenceGroupHeader(title = "SERVICES")

            TrackingPreferenceWidget(
                title = "AniList",
                subtitle = if (tracking.isLoggedIn) {
                    "Logged in as: ${tracking.username ?: "Unknown"}"
                } else {
                    "Tap to connect AniList account"
                },
                checked = tracking.isLoggedIn,
                logoResId = R.drawable.ic_anilist,
                onClick = {
                    if (tracking.isLoggedIn) {
                        showLogoutDialog = true
                    } else {
                        showLoginDialog = true
                    }
                }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // ==================== 2. SYNC & REFRESH ====================
            PreferenceGroupHeader(title = "SYNC & REFRESH")

            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Sync Library with AniList",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "2-way state synchronization for Anime, Manga, and Novels.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Button(
                            onClick = { viewModel.syncAniList() },
                            enabled = tracking.isLoggedIn && !tracking.isSyncing
                        ) {
                            if (tracking.isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "Syncing")
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Sync Now")
                            }
                        }
                    }

                    // MAL-Sync style live progress bar
                    if (tracking.isSyncing || tracking.syncTotal > 0) {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(12.dp))

                        val percent = if (tracking.syncTotal > 0) {
                            ((tracking.syncCurrent.toFloat() / tracking.syncTotal) * 100).toInt().coerceIn(0, 100)
                        } else {
                            0
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (tracking.isSyncing) {
                                    "Syncing items: ${tracking.syncCurrent} / ${tracking.syncTotal} items"
                                } else {
                                    "Last sync completed: ${tracking.syncCurrent} / ${tracking.syncTotal} items"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$percent%",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (tracking.isSyncing && tracking.syncTotal == 0) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                        } else {
                            val progressFloat = if (tracking.syncTotal > 0) {
                                (tracking.syncCurrent.toFloat() / tracking.syncTotal).coerceIn(0f, 1f)
                            } else {
                                1f
                            }
                            LinearProgressIndicator(
                                progress = { progressFloat },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                        }
                    }

                    // Error banner with retry affordance
                    if (!tracking.syncError.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = tracking.syncError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.syncAniList() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Retry",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // ==================== 3. AUTOMATION ====================
            PreferenceGroupHeader(title = "AUTOMATION")

            SwitchPreferenceWidget(
                title = "Background Sync",
                subtitle = "Daily automatic synchronization on Wi-Fi",
                checked = tracking.backgroundSyncEnabled,
                onCheckedChange = { viewModel.setBackgroundSyncEnabled(it) }
            )

            SwitchPreferenceWidget(
                title = "Strict Adult Content Filter",
                subtitle = "Exclude 18+ adult titles from library sync",
                checked = tracking.strictAdultContent,
                onCheckedChange = { viewModel.setStrictAdultContent(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "AniList 2-way sync keeps local reading/watching progress, volumes, scores, and status synchronized across devices without any cloud account needed for LazyDex.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AniListLoginDialog(
    isLoggingIn: Boolean,
    errorMessage: String?,
    onDismissRequest: () -> Unit,
    onConfirmLogin: (String) -> Unit
) {
    val context = LocalContext.current
    var token by remember { mutableStateOf("") }
    var hideToken by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Log In with AniList",
                    style = MaterialTheme.typography.titleLarge
                )
                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close"
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Authorize LazyDex on AniList in your browser, then copy and paste the generated access token below:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ANILIST_AUTH_URL))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInBrowser,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Open AniList Login in Browser")
                }

                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("AniList Access Token") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (hideToken) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    trailingIcon = {
                        IconButton(onClick = { hideToken = !hideToken }) {
                            Icon(
                                imageVector = if (hideToken) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (hideToken) "Show token" else "Hide token"
                            )
                        }
                    },
                    isError = !errorMessage.isNullOrBlank()
                )

                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmLogin(token) },
                enabled = !isLoggingIn && token.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoggingIn) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Logging In...")
                } else {
                    Text("Log In")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun AniListLogoutDialog(
    username: String?,
    onDismissRequest: () -> Unit,
    onConfirmLogout: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = "Log out from AniList?",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Text(
                text = "Logged in as ${username ?: "AniList user"}. Local media items and tracking history will not be deleted.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onDismissRequest
                ) {
                    Text(text = "Cancel")
                }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onConfirmLogout,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(text = "Log Out")
                }
            }
        }
    )
}

