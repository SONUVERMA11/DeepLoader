package com.sonuverma.deeploader.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * DeepLoader Theme System
 * Three themes: Light, Dark, AMOLED (Dark+)
 * Seamless transitions between themes with live toggle.
 * Developer: Sonu Verma
 */

// ─── Theme Mode Enum ───
enum class ThemeMode {
    LIGHT,
    DARK,
    AMOLED,
    SYSTEM  // Follow system dark/light setting
}

// ─── Light Color Scheme ───
private val LightColorScheme = lightColorScheme(
    primary = DeepLoaderColors.PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = DeepLoaderColors.PrimaryBlue.copy(alpha = 0.12f),
    onPrimaryContainer = DeepLoaderColors.PrimaryBlue,
    secondary = DeepLoaderColors.AccentCyan,
    onSecondary = Color.White,
    secondaryContainer = DeepLoaderColors.AccentCyan.copy(alpha = 0.12f),
    onSecondaryContainer = DeepLoaderColors.AccentCyan,
    tertiary = DeepLoaderColors.AccentPurple,
    onTertiary = Color.White,
    error = DeepLoaderColors.AccentRed,
    onError = Color.White,
    errorContainer = DeepLoaderColors.AccentRed.copy(alpha = 0.12f),
    onErrorContainer = DeepLoaderColors.AccentRed,
    background = DeepLoaderColors.LightBackground,
    onBackground = DeepLoaderColors.LightOnBackground,
    surface = DeepLoaderColors.LightSurface,
    onSurface = DeepLoaderColors.LightOnSurface,
    surfaceVariant = DeepLoaderColors.LightSurfaceVariant,
    onSurfaceVariant = DeepLoaderColors.LightOnSurfaceVariant,
    outline = DeepLoaderColors.LightSeparator,
    outlineVariant = DeepLoaderColors.LightTertiaryText,
    inverseSurface = DeepLoaderColors.DarkSurface,
    inverseOnSurface = DeepLoaderColors.DarkOnSurface,
    inversePrimary = DeepLoaderColors.PrimaryBlueDark,
    surfaceTint = DeepLoaderColors.PrimaryBlue,
)

// ─── Dark Color Scheme ───
private val DarkColorScheme = darkColorScheme(
    primary = DeepLoaderColors.PrimaryBlueDark,
    onPrimary = Color.White,
    primaryContainer = DeepLoaderColors.PrimaryBlueDark.copy(alpha = 0.20f),
    onPrimaryContainer = DeepLoaderColors.PrimaryBlueDark,
    secondary = DeepLoaderColors.AccentCyan,
    onSecondary = Color.Black,
    secondaryContainer = DeepLoaderColors.AccentCyan.copy(alpha = 0.20f),
    onSecondaryContainer = DeepLoaderColors.AccentCyan,
    tertiary = DeepLoaderColors.AccentPurple,
    onTertiary = Color.White,
    error = DeepLoaderColors.AccentRed,
    onError = Color.White,
    errorContainer = DeepLoaderColors.AccentRed.copy(alpha = 0.20f),
    onErrorContainer = DeepLoaderColors.AccentRed,
    background = DeepLoaderColors.DarkBackground,
    onBackground = DeepLoaderColors.DarkOnBackground,
    surface = DeepLoaderColors.DarkSurface,
    onSurface = DeepLoaderColors.DarkOnSurface,
    surfaceVariant = DeepLoaderColors.DarkSurfaceVariant,
    onSurfaceVariant = DeepLoaderColors.DarkOnSurfaceVariant,
    outline = DeepLoaderColors.DarkSeparator,
    outlineVariant = DeepLoaderColors.DarkTertiaryText,
    inverseSurface = DeepLoaderColors.LightSurface,
    inverseOnSurface = DeepLoaderColors.LightOnSurface,
    inversePrimary = DeepLoaderColors.PrimaryBlue,
    surfaceTint = DeepLoaderColors.PrimaryBlueDark,
)

// ─── AMOLED Color Scheme (pure black backgrounds) ───
private val AmoledColorScheme = darkColorScheme(
    primary = DeepLoaderColors.PrimaryBlueDark,
    onPrimary = Color.White,
    primaryContainer = DeepLoaderColors.PrimaryBlueDark.copy(alpha = 0.20f),
    onPrimaryContainer = DeepLoaderColors.PrimaryBlueDark,
    secondary = DeepLoaderColors.AccentCyan,
    onSecondary = Color.Black,
    secondaryContainer = DeepLoaderColors.AccentCyan.copy(alpha = 0.20f),
    onSecondaryContainer = DeepLoaderColors.AccentCyan,
    tertiary = DeepLoaderColors.AccentPurple,
    onTertiary = Color.White,
    error = DeepLoaderColors.AccentRed,
    onError = Color.White,
    errorContainer = DeepLoaderColors.AccentRed.copy(alpha = 0.20f),
    onErrorContainer = DeepLoaderColors.AccentRed,
    background = DeepLoaderColors.AmoledBackground,
    onBackground = DeepLoaderColors.DarkOnBackground,
    surface = DeepLoaderColors.AmoledSurface,
    onSurface = DeepLoaderColors.DarkOnSurface,
    surfaceVariant = DeepLoaderColors.AmoledSurfaceVariant,
    onSurfaceVariant = DeepLoaderColors.DarkOnSurfaceVariant,
    outline = DeepLoaderColors.AmoledSeparator,
    outlineVariant = DeepLoaderColors.DarkTertiaryText,
    inverseSurface = DeepLoaderColors.LightSurface,
    inverseOnSurface = DeepLoaderColors.LightOnSurface,
    inversePrimary = DeepLoaderColors.PrimaryBlue,
    surfaceTint = DeepLoaderColors.PrimaryBlueDark,
)

@Composable
fun DeepLoaderTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()

    val colorScheme = when (themeMode) {
        ThemeMode.LIGHT -> LightColorScheme
        ThemeMode.DARK -> DarkColorScheme
        ThemeMode.AMOLED -> AmoledColorScheme
        ThemeMode.SYSTEM -> if (systemDark) DarkColorScheme else LightColorScheme
    }

    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.SYSTEM -> systemDark
    }

    // Update system bar colors to match theme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DeepLoaderTypography,
        content = content
    )
}
