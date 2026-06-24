package cn.qinxiandiqi.photochecker.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// ============================================================
// Brand color schemes — derived from the app icon's palette
// (lavender-purple primary, pale-sky-blue surface, cyan/mint accents).
// Only used when dynamic color is off or unavailable (Android < 12);
// on Android 12+ the theme follows the system wallpaper by default.
// ============================================================

private val BrandLight = lightColorScheme(
    primary = Color(0xFF7C5CFC),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE9D8FD),
    onPrimaryContainer = Color(0xFF2D1B6B),
    secondary = Color(0xFF4EC5D8),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC8F0F5),
    onSecondaryContainer = Color(0xFF003740),
    tertiary = Color(0xFF7CB342),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE6F7D4),
    onTertiaryContainer = Color(0xFF1F3700),
    background = Color(0xFFF0F6FE),
    onBackground = Color(0xFF1A1B22),
    surface = Color(0xFFF0F6FE),
    onSurface = Color(0xFF1A1B22),
    surfaceVariant = Color(0xFFE4E1F0),
    onSurfaceVariant = Color(0xFF474552),
    error = Color(0xFFE53935),
    onError = Color(0xFFFFFFFF),
)

private val BrandDark = darkColorScheme(
    primary = Color(0xFFB794F4),
    onPrimary = Color(0xFF3A1E8E),
    primaryContainer = Color(0xFF5539C4),
    onPrimaryContainer = Color(0xFFE9D8FD),
    secondary = Color(0xFF90E0EF),
    onSecondary = Color(0xFF003744),
    secondaryContainer = Color(0xFF004F5E),
    onSecondaryContainer = Color(0xFFC8F0F5),
    tertiary = Color(0xFFA5D677),
    onTertiary = Color(0xFF1E3700),
    tertiaryContainer = Color(0xFF2F4E08),
    onTertiaryContainer = Color(0xFFE6F7D4),
    background = Color(0xFF121318),
    onBackground = Color(0xFFE4E2EC),
    surface = Color(0xFF121318),
    onSurface = Color(0xFFE4E2EC),
    surfaceVariant = Color(0xFF474552),
    onSurfaceVariant = Color(0xFFC9C5D4),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

@Composable
fun PhotoCheckerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> BrandDark
        else -> BrandLight
    }

    val appColors = if (darkTheme) DarkAppColors else LightAppColors

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
