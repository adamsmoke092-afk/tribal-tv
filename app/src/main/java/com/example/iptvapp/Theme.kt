package com.example.iptvapp

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Strict black/gold/gray palette (professional redesign). Always dark —
 * the system theme is ignored. These are the ONLY colors in the app;
 * every screen pulls from here or from the colorScheme below.
 *
 * Gold is an accent only: selected states, group headers, active toggles,
 * primary buttons, the playing channel. Everything else is black or gray.
 */
val PaletteBackground = Color(0xFF0A0A0A)
val PaletteSurface = Color(0xFF141414)
val PaletteCard = Color(0xFF1C1C1E)
val PaletteOutline = Color(0xFF2E2E31)
val PaletteGold = Color(0xFFD4AF37)
val PaletteDarkGold = Color(0xFF8A6D1D)
val PaletteTextPrimary = Color(0xFFF2F2F2)
val PaletteTextSecondary = Color(0xFFA0A0A6)
val PaletteDisabled = Color(0xFF5A5A5F)
val PaletteOffline = Color(0xFFB3554B)
val PaletteLogoBacking = Color(0xFFE8E8E8)

private val TribalColorScheme = darkColorScheme(
    primary = PaletteGold,
    onPrimary = PaletteBackground,
    background = PaletteBackground,
    surface = PaletteSurface,
    surfaceVariant = PaletteCard,
    outline = PaletteOutline,
    onBackground = PaletteTextPrimary,
    onSurface = PaletteTextPrimary,
    onSurfaceVariant = PaletteTextSecondary,
    error = PaletteOffline,
    onError = PaletteBackground
)

// Title hierarchy: semibold titles; group headers ride labelMedium with
// wide letter-spacing (their gold color is set where they're rendered).
private val TribalTypography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.8.sp
    )
)

@Composable
fun TribalTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TribalColorScheme,
        typography = TribalTypography,
        content = content
    )
}
