package com.example.mantiskeyboard.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.mantiskeyboard.data.repository.ThemeUtils

private val DarkColorScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF2C2C2E),
    onPrimaryContainer = Color.White,
    secondary = Color.White,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF2C2C2E),
    onSecondaryContainer = Color.White,
    tertiary = Color.White,
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF2C2C2E),
    onTertiaryContainer = Color.White,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFFAAAAAA),
    surfaceContainer = Color(0xFF1C1C1E),
    surfaceContainerLow = Color(0xFF161618),
    surfaceContainerLowest = Color(0xFF0F0F10),
    surfaceContainerHigh = Color(0xFF242426),
    surfaceContainerHighest = Color(0xFF2C2C2E),
    surfaceBright = Color(0xFF38383A),
    surfaceDim = Color(0xFF141416),
    error = Color(0xFFFF6B6B),
    onError = Color.Black,
    outline = Color(0xFF444446),
    outlineVariant = Color(0xFF2C2C2E)
)

private val LightColorScheme = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEEEEEE),
    onPrimaryContainer = Color.Black,
    secondary = Color.Black,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEEEEEE),
    onSecondaryContainer = Color.Black,
    tertiary = Color.Black,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEEEEEE),
    onTertiaryContainer = Color.Black,
    background = Color(0xFFFAFAFC),
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFF2F2F4),
    onSurfaceVariant = Color.Black,
    surfaceContainer = Color(0xFFF2F2F5),
    surfaceContainerLow = Color(0xFFFAFAFC),
    surfaceContainerLowest = Color.White,
    surfaceContainerHigh = Color(0xFFEAEAEA),
    surfaceContainerHighest = Color(0xFFE0E0E2),
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFE0E0E2),
    error = Color.Gray,
    onError = Color.White,
    outline = Color(0xFFCCCCCC),
    outlineVariant = Color(0xFFDDDDDD)
)

@Composable
fun MantisKeyboardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val initialAppTheme = remember(context) { ThemeUtils.getAppThemeSync(context) }
    val appTheme by ThemeUtils.getAppThemeFlow(context).collectAsState(initial = initialAppTheme)

    val isDark = when (appTheme) {
        "light" -> false
        "dark" -> true
        else -> darkTheme
    }

    val useDynamicColor = dynamicColor || appTheme == "m3_dynamic" || appTheme == "dynamic"

    val colorScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !isDark
                insetsController.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = getTypography(),
        content = content
    )
}