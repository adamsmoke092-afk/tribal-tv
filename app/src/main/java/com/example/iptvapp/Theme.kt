package com.example.iptvapp

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Dark cinema palette (UI redesign spec). Always dark — the system theme
 * is ignored. The scheme covers Material defaults; the three Tribal* vals
 * are spec colors with no colorScheme slot (chips, accent tint) exposed
 * for the screens to use directly.
 */
private val DarkBackground = Color(0xFF0B0D12)
private val DarkSurface = Color(0xFF151922)
private val DarkDivider = Color(0xFF232938)
private val DarkText = Color(0xFFF2F4F8)
private val DarkSecondary = Color(0xFF9AA3B5)
private val DarkAccent = Color(0xFFFF5A36)
private val DarkOnAccent = Color(0xFF0B0D12)

val TribalChipBackground = Color(0xFF1E2431)
val TribalAccentTint = DarkAccent.copy(alpha = 0.15f)
val TribalAccentTintText = Color(0xFFFF8A6B)

private val TribalColorScheme = darkColorScheme(
    background = DarkBackground,
    surface = DarkSurface,
    onBackground = DarkText,
    onSurface = DarkText,
    primary = DarkAccent,
    onPrimary = DarkOnAccent,
    outline = DarkDivider,
    onSurfaceVariant = DarkSecondary
)

@Composable
fun TribalTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TribalColorScheme, content = content)
}
