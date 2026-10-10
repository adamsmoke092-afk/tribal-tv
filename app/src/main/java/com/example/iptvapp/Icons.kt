package com.example.iptvapp

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The app's icon set. Core icons come from material-icons-core (no
 * extended pack: huge APK for a 3 GB RAM phone), centralized so screens
 * reference one place.
 *
 * material-icons-core has NO outlined star and no prev/next chevrons
 * (StarBorder's absence is CI-verified from an earlier batch), so those
 * are drawn here as custom ImageVectors using the standard Material
 * path data — the spec's documented fallback, no new dependency. All are
 * drawn white; Icon tint recolors them per call site. Every IconButton
 * sets a contentDescription, and Material3's IconButton enforces a 48dp
 * minimum touch target.
 */
object TribalIcons {
    val Back = Icons.AutoMirrored.Filled.ArrowBack
    val Search = Icons.Filled.Search
    val Close = Icons.Filled.Close
    val Settings = Icons.Filled.Settings
    val Refresh = Icons.Filled.Refresh
    val Star = Icons.Filled.Star

    /** Material navigate_before — a solid left chevron. */
    val Prev: ImageVector = ImageVector.Builder(
        name = "Prev",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(15.41f, 7.41f)
            lineTo(14f, 6f)
            lineTo(8f, 12f)
            lineTo(14f, 18f)
            lineTo(15.41f, 16.59f)
            lineTo(10.83f, 12f)
            close()
        }
    }.build()

    /** Material navigate_next — a solid right chevron. */
    val Next: ImageVector = ImageVector.Builder(
        name = "Next",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(10f, 6f)
            lineTo(8.59f, 7.41f)
            lineTo(13.17f, 12f)
            lineTo(8.59f, 16.59f)
            lineTo(10f, 18f)
            lineTo(16f, 12f)
            close()
        }
    }.build()

    /** The Material star as a pure outline (stroke, no fill) — the favourites off-state. */
    val StarOutlined: ImageVector = ImageVector.Builder(
        name = "StarOutlined",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.8f
        ) {
            moveTo(12f, 17.27f)
            lineTo(18.18f, 21f)
            lineTo(16.54f, 13.97f)
            lineTo(22f, 9.24f)
            lineTo(14.81f, 8.63f)
            lineTo(12f, 2f)
            lineTo(9.19f, 8.63f)
            lineTo(2f, 9.24f)
            lineTo(7.46f, 13.97f)
            lineTo(5.82f, 21f)
            close()
        }
    }.build()
}