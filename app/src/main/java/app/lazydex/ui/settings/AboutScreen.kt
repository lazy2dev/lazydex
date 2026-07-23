package app.lazydex.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lazydex.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showLicensesDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

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
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // ==================== APP LOGO CANVAS ====================
            val primaryColor = MaterialTheme.colorScheme.primary
            Canvas(modifier = Modifier.size(80.dp)) {
                // Drawing a stylized Komikku glasses/infinity-C logo using vector arcs
                val strokeWidth = 8.dp.toPx()
                // Left ring of glasses
                drawCircle(
                    color = primaryColor,
                    radius = size.width / 4.5f,
                    center = center.copy(x = center.x - size.width / 4f),
                    style = Stroke(width = strokeWidth)
                )
                // Right ring of glasses
                drawCircle(
                    color = primaryColor,
                    radius = size.width / 4.5f,
                    center = center.copy(x = center.x + size.width / 4f),
                    style = Stroke(width = strokeWidth)
                )
                // Bridge arc between them
                drawArc(
                    color = primaryColor,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = center.copy(
                        x = center.x - size.width / 6f,
                        y = center.y - size.width / 6f
                    ),
                    size = size.copy(width = size.width / 3f, height = size.height / 5f),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "LazyDex",
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Stable v${BuildConfig.VERSION_NAME}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ==================== SETTINGS LINKS ====================
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    AboutLinkItem(title = "Check for updates") {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/lazy2dev/lazydex/releases"))
                        context.startActivity(browserIntent)
                    }
                    AboutLinkItem(title = "What's new") {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/lazy2dev/lazydex/commits"))
                        context.startActivity(browserIntent)
                    }
                    AboutLinkItem(title = "Open source licenses") {
                        showLicensesDialog = true
                    }
                    AboutLinkItem(title = "Privacy policy") {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/lazy2dev/lazydex/blob/dev/LICENSE"))
                        context.startActivity(browserIntent)
                    }
                }
            }

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

@Composable
fun AboutLinkItem(
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
