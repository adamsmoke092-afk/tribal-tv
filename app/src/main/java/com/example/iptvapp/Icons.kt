package com.example.iptvapp

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings

/**
 * The app's icon set — all from material-icons-core (no extended pack:
 * huge APK for a 3 GB RAM phone). Centralized so screens reference one
 * place. Every IconButton call site sets a contentDescription, and
 * Material3's IconButton enforces a 48dp minimum touch target.
 */
object TribalIcons {
    val Back = Icons.AutoMirrored.Filled.ArrowBack
    val Search = Icons.Filled.Search
    val Close = Icons.Filled.Close
    val Settings = Icons.Filled.Settings
    val Refresh = Icons.Filled.Refresh
}
