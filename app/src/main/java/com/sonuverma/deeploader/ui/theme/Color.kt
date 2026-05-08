package com.sonuverma.deeploader.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * DeepLoader Color System
 * Three theme variants: Light, Dark, AMOLED (Dark+)
 * Inspired by iOS design language for premium feel.
 * Developer: Sonu Verma
 */
object DeepLoaderColors {

    // ─── Brand Colors ───
    val PrimaryBlue = Color(0xFF007AFF)        // iOS-style blue
    val PrimaryBlueDark = Color(0xFF0A84FF)    // iOS dark mode blue
    val AccentCyan = Color(0xFF5AC8FA)         // Secondary accent
    val AccentGreen = Color(0xFF34C759)        // Success state
    val AccentOrange = Color(0xFFFF9F0A)       // Warning state
    val AccentRed = Color(0xFFFF3B30)          // Error/destructive
    val AccentPurple = Color(0xFFAF52DE)       // Torrent indicator
    val AccentPink = Color(0xFFFF2D55)         // Live/streaming

    // ─── Light Theme ───
    val LightBackground = Color(0xFFF2F2F7)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceVariant = Color(0xFFE5E5EA)
    val LightOnBackground = Color(0xFF000000)
    val LightOnSurface = Color(0xFF000000)
    val LightOnSurfaceVariant = Color(0xFF3C3C43)
    val LightSecondaryText = Color(0xFF8E8E93)
    val LightTertiaryText = Color(0xFFC7C7CC)
    val LightSeparator = Color(0xFFC6C6C8)
    val LightCardShadow = Color(0x1A000000)

    // ─── Dark Theme ───
    val DarkBackground = Color(0xFF1C1C1E)
    val DarkSurface = Color(0xFF2C2C2E)
    val DarkSurfaceVariant = Color(0xFF3A3A3C)
    val DarkOnBackground = Color(0xFFFFFFFF)
    val DarkOnSurface = Color(0xFFFFFFFF)
    val DarkOnSurfaceVariant = Color(0xFFEBEBF5)
    val DarkSecondaryText = Color(0xFF98989D)
    val DarkTertiaryText = Color(0xFF48484A)
    val DarkSeparator = Color(0xFF38383A)
    val DarkCardShadow = Color(0x33000000)

    // ─── AMOLED Theme (Dark+) ───
    val AmoledBackground = Color(0xFF000000)
    val AmoledSurface = Color(0xFF0D0D0D)
    val AmoledSurfaceVariant = Color(0xFF1A1A1A)
    val AmoledSeparator = Color(0xFF262626)

    // ─── Gradient Colors ───
    val GradientBlueStart = Color(0xFF007AFF)
    val GradientBlueEnd = Color(0xFF5856D6)
    val GradientGreenStart = Color(0xFF34C759)
    val GradientGreenEnd = Color(0xFF30D158)
    val GradientPurpleStart = Color(0xFFAF52DE)
    val GradientPurpleEnd = Color(0xFF5856D6)

    // ─── Speed indicator colors ───
    val SpeedSlow = Color(0xFFFF9F0A)       // < 1 MB/s
    val SpeedMedium = Color(0xFF34C759)     // 1-10 MB/s
    val SpeedFast = Color(0xFF007AFF)       // > 10 MB/s
    val SpeedLudicrous = Color(0xFFAF52DE)  // > 50 MB/s

    // ─── Platform brand colors ───
    val YouTube = Color(0xFFFF0000)
    val Instagram = Color(0xFFE4405F)
    val Twitter = Color(0xFF1DA1F2)
    val TikTok = Color(0xFF010101)
    val Reddit = Color(0xFFFF4500)
    val Facebook = Color(0xFF1877F2)
    val SoundCloud = Color(0xFFFF5500)
    val Vimeo = Color(0xFF1AB7EA)
    val Dailymotion = Color(0xFF00B2FF)
    val Torrent = AccentPurple
}
