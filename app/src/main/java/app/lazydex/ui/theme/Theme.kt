package app.lazydex.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.ColorScheme
import app.lazydex.data.local.ThemePreset

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    primaryContainer = DarkPrimaryContainer,
    secondary = DarkSecondary,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = DarkOnBackground,
    onSurface = DarkOnSurface,
    outline = DarkOutline
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    background = LightBackground,
    surface = LightSurface,
    onBackground = LightOnBackground,
    onSurface = LightOnSurface,
    outline = LightOutline
)

private val MangaDarkColorScheme = darkColorScheme(
    primary = Color(0xFF22C55E),
    primaryContainer = Color(0xFF052E16),
    secondary = Color(0xFF15803D),
    background = Color(0xFF0F172A),
    surface = Color(0xFF131F37),
    surfaceVariant = Color(0xFF1E293B),
    onBackground = DarkOnBackground,
    onSurface = DarkOnSurface,
    outline = DarkOutline
)

private val MangaLightColorScheme = lightColorScheme(
    primary = Color(0xFF16A34A),
    background = LightBackground,
    surface = LightSurface,
    onBackground = LightOnBackground,
    onSurface = LightOnSurface,
    outline = LightOutline
)

private val GameDarkColorScheme = darkColorScheme(
    primary = Color(0xFFA855F7),
    primaryContainer = Color(0xFF3B0764),
    secondary = Color(0xFF7E22CE),
    background = Color(0xFF15102A),
    surface = Color(0xFF1D173B),
    surfaceVariant = Color(0xFF281E52),
    onBackground = DarkOnBackground,
    onSurface = DarkOnSurface,
    outline = DarkOutline
)

private val GameLightColorScheme = lightColorScheme(
    primary = Color(0xFF9333EA),
    background = LightBackground,
    surface = LightSurface,
    onBackground = LightOnBackground,
    onSurface = LightOnSurface,
    outline = LightOutline
)

private val MovieDarkColorScheme = darkColorScheme(
    primary = Color(0xFFF97316),
    primaryContainer = Color(0xFF431407),
    secondary = Color(0xFFC2410C),
    background = Color(0xFF1C1310),
    surface = Color(0xFF241A16),
    surfaceVariant = Color(0xFF32231E),
    onBackground = DarkOnBackground,
    onSurface = DarkOnSurface,
    outline = DarkOutline
)

private val MovieLightColorScheme = lightColorScheme(
    primary = Color(0xFFEA580C),
    background = LightBackground,
    surface = LightSurface,
    onBackground = LightOnBackground,
    onSurface = LightOnSurface,
    outline = LightOutline
)

@Composable
fun LazyDexTheme(
    darkTheme: Boolean = true, // Default to Dark mode
    dynamicColor: Boolean = true,
    themePreset: ThemePreset = ThemePreset.DEFAULT,
    amoledMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var colorScheme = when {
        themePreset == ThemePreset.DYNAMIC && dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        themePreset == ThemePreset.MANGA -> {
            if (darkTheme) MangaDarkColorScheme else MangaLightColorScheme
        }
        themePreset == ThemePreset.GAME -> {
            if (darkTheme) GameDarkColorScheme else GameLightColorScheme
        }
        themePreset == ThemePreset.MOVIE -> {
            if (darkTheme) MovieDarkColorScheme else MovieLightColorScheme
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    if (darkTheme && amoledMode) {
        colorScheme = colorScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(0xFF111111) // Muted black variant for popups/variant surfaces
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

/**
 * Derives a dynamic ColorScheme for the Media Details screen based on the cover's vibrant seed color.
 */
fun coverColorScheme(
    seedColor: Color,
    isDark: Boolean,
    base: ColorScheme
): ColorScheme {
    val seedLum = seedColor.luminance()
    val adjustedPrimary = if (!isDark && seedLum > 0.45f) {
        // Darken for sufficient contrast on light theme backgrounds
        Color(
            red = seedColor.red * 0.65f,
            green = seedColor.green * 0.65f,
            blue = seedColor.blue * 0.65f,
            alpha = 1f
        )
    } else if (isDark && seedLum < 0.15f) {
        // Brighten for sufficient contrast on dark theme backgrounds
        Color(
            red = (seedColor.red + 0.35f).coerceAtMost(1f),
            green = (seedColor.green + 0.35f).coerceAtMost(1f),
            blue = (seedColor.blue + 0.35f).coerceAtMost(1f),
            alpha = 1f
        )
    } else {
        seedColor
    }

    val onPrimaryColor = if (adjustedPrimary.luminance() > 0.5f) Color.Black else Color.White
    val containerAlpha = if (isDark) 0.22f else 0.14f

    return base.copy(
        primary = adjustedPrimary,
        onPrimary = onPrimaryColor,
        primaryContainer = adjustedPrimary.copy(alpha = containerAlpha),
        onPrimaryContainer = adjustedPrimary,
        surfaceTint = adjustedPrimary,
        secondary = adjustedPrimary.copy(alpha = 0.85f),
        secondaryContainer = adjustedPrimary.copy(alpha = containerAlpha * 0.8f),
        onSecondaryContainer = adjustedPrimary,
        outlineVariant = adjustedPrimary.copy(alpha = 0.35f),
    )
}
