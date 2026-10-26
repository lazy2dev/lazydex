package app.lazydex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import app.lazydex.data.local.ThemePreferences
import app.lazydex.ui.navigation.LazyDexNavGraph
import app.lazydex.ui.theme.LazyDexTheme
import org.koin.android.ext.android.inject

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import app.lazydex.ui.navigation.ExtensionsRoute

class MainActivity : ComponentActivity() {

    private val themePreferences: ThemePreferences by inject()
    private val deepLinkRepoUrl = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        deepLinkRepoUrl.value = extractRepoUrl(intent?.data?.toString())

        enableEdgeToEdge()
        setContent {
            val themeMode by themePreferences.themeMode.collectAsState(initial = "DARK")
            val themePreset by themePreferences.themePreset.collectAsState(initial = app.lazydex.data.local.ThemePreset.DEFAULT)
            val amoledMode by themePreferences.amoledMode.collectAsState(initial = false)

            val isDark = when (themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> isSystemInDarkTheme()
            }

            LazyDexTheme(
                darkTheme = isDark,
                themePreset = themePreset,
                amoledMode = amoledMode
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    LaunchedEffect(deepLinkRepoUrl.value) {
                        val repoUrl = deepLinkRepoUrl.value ?: return@LaunchedEffect
                        while (true) {
                            val navigated = runCatching {
                                navController.navigate(ExtensionsRoute(initialAddRepoUrl = repoUrl))
                            }.isSuccess
                            if (navigated) {
                                deepLinkRepoUrl.value = null
                                break
                            }
                            kotlinx.coroutines.delay(50)
                        }
                    }

                    LazyDexNavGraph(navController = navController)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        deepLinkRepoUrl.value = extractRepoUrl(intent.data?.toString())
    }

    companion object {
        fun extractRepoUrl(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            if (!raw.startsWith("lazydex://add-repo", ignoreCase = true)) return null
            val urlPart = raw.substringAfter("url=", "")
            if (urlPart.isBlank()) return null
            return if (urlPart.startsWith("http%3A", ignoreCase = true) ||
                urlPart.startsWith("https%3A", ignoreCase = true) ||
                (!urlPart.startsWith("http://", ignoreCase = true) && !urlPart.startsWith("https://", ignoreCase = true) && urlPart.contains("%"))
            ) {
                try {
                    java.net.URLDecoder.decode(urlPart, "UTF-8")
                } catch (_: Exception) {
                    urlPart
                }
            } else {
                urlPart
            }
        }
    }
}
