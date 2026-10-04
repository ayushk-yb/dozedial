package com.dozedial.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

object DozeDialColors {
    val MidnightBlack = Color(0xFF090D16)
    val SurfaceDark = Color(0xFF111827)
    val CardBackground = Color(0xFF182234)
    val CardBorder = Color(0xFF2E3A52).copy(alpha = 0.6f)

    val NeonIndigo = Color(0xFF6366F1)
    val CelestialCyan = Color(0xFF38BDF8)
    val EmeraldSleep = Color(0xFF10B981)
    val SunriseCoral = Color(0xFFF43F5E)
    val DawnGold = Color(0xFFF59E0B)
    val RadiantViolet = Color(0xFFA855F7)

    val TextPrimary = Color(0xFFF9FAFB)
    val TextSecondary = Color(0xFF9CA3AF)
    val TextTertiary = Color(0xFF6B7280)

    val DeepSleep = NeonIndigo
    val LightSleep = CelestialCyan
    val RemSleep = RadiantViolet
    val WakeArousal = SunriseCoral

    val DarkColorScheme = darkColorScheme(
        primary = NeonIndigo,
        onPrimary = Color.White,
        primaryContainer = Color(0xFF312E81),
        onPrimaryContainer = Color(0xFFE0E7FF),
        secondary = CelestialCyan,
        onSecondary = Color(0xFF082F49),
        secondaryContainer = Color(0xFF075985),
        onSecondaryContainer = Color(0xFFE0F2FE),
        tertiary = DawnGold,
        background = MidnightBlack,
        onBackground = TextPrimary,
        surface = SurfaceDark,
        onSurface = TextPrimary,
        surfaceVariant = CardBackground,
        onSurfaceVariant = TextSecondary,
        outline = CardBorder,
        error = SunriseCoral,
        onError = Color.White
    )
}
