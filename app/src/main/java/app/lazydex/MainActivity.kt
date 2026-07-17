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

class MainActivity : ComponentActivity() {

    private val themePreferences: ThemePreferences by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            val themeMode by themePreferences.themeMode.collectAsState(initial = "DARK")
            val amoledMode by themePreferences.amoledMode.collectAsState(initial = false)

            val isDark = when (themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> isSystemInDarkTheme()
            }

            LazyDexTheme(
                darkTheme = isDark,
                amoledMode = amoledMode
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    LazyDexNavGraph(navController = navController)
                }
            }
        }
    }
}
