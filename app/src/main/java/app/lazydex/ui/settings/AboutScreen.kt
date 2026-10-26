@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.lazydex.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lazydex.BuildConfig
import app.lazydex.R
import app.lazydex.ui.settings.components.TextPreferenceWidget

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var showLicensesDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    val diagnostics = """
        App: LazyDex Beta v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})
        Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
        Device: ${Build.MANUFACTURER} ${Build.MODEL}
        Database: v3
    """.trimIndent()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "About",
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
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // ==================== APP LOGO & TITLE ====================
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = "LazyDex Logo",
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "LazyDex",
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Beta v${BuildConfig.VERSION_NAME}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ==================== PREFERENCE LINKS ====================
            TextPreferenceWidget(
                title = "Version",
                subtitle = "Beta v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · Tap to copy diagnostics",
                icon = Icons.Default.Info,
                onClick = {
                    clipboardManager.setText(AnnotatedString(diagnostics))
                    Toast.makeText(context, "Diagnostics copied to clipboard", Toast.LENGTH_SHORT).show()
                }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )

            TextPreferenceWidget(
                title = "Check for updates",
                subtitle = "GitHub releases",
                icon = Icons.Default.NewReleases,
                onClick = {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/lazy2dev/lazydex/releases"))
                    context.startActivity(browserIntent)
                }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )

            TextPreferenceWidget(
                title = "What's new",
                subtitle = "Recent commits & changelog",
                icon = Icons.Default.History,
                onClick = {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/lazy2dev/lazydex/commits"))
                    context.startActivity(browserIntent)
                }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )

            TextPreferenceWidget(
                title = "Open source licenses",
                subtitle = "Third-party libraries used in LazyDex",
                icon = Icons.Default.Description,
                onClick = {
                    showLicensesDialog = true
                }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )

            TextPreferenceWidget(
                title = "Privacy policy",
                subtitle = "Local-only storage & license statement",
                icon = Icons.Default.Security,
                onClick = {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/lazy2dev/lazydex/blob/dev/LICENSE"))
                    context.startActivity(browserIntent)
                }
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ==================== FOOTER SOCIAL ICONS ====================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/lazy2dev/lazydex"))
                    context.startActivity(browserIntent)
                }) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = "Website",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(onClick = {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.gg"))
                    context.startActivity(browserIntent)
                }) {
                    Icon(
                        imageVector = Icons.Default.Forum,
                        contentDescription = "Discord",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(onClick = {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/lazy2dev/lazydex"))
                    context.startActivity(browserIntent)
                }) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = "GitHub",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
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
