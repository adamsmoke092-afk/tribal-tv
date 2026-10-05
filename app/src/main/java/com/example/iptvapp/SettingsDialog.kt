package com.example.iptvapp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Settings dialog (feature batch step 4): playlist URL + logo switch.
 * Saving a changed URL fetches and parses the new playlist BEFORE the
 * persisted URL or the Room cache is touched — a bad URL or an empty
 * playlist leaves everything exactly as it was, with the error shown
 * inline under the field.
 */
@OptIn(ExperimentalMaterial3Api::class) // harmless if this AlertDialog overload is stable in our version
@Composable
fun SettingsDialog(
    currentUrl: String,
    initialShowLogos: Boolean,
    onDismiss: () -> Unit,
    onSave: suspend (newUrl: String, showLogos: Boolean) -> String?
) {
    var urlText by remember { mutableStateOf(currentUrl) }
    var showLogos by remember { mutableStateOf(initialShowLogos) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "Settings",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = urlText,
                    onValueChange = {
                        urlText = it
                        errorText = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = {
                        Text(
                            text = "Playlist URL",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    isError = errorText != null,
                    supportingText = errorText?.let { message ->
                        {
                            Text(
                                text = message,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )
                TextButton(onClick = { urlText = SettingsStore.DEFAULT_PLAYLIST_URL }) {
                    Text(
                        text = "Reset to default",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Show channel logos",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = showLogos,
                        onCheckedChange = { showLogos = it },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
                if (isSaving) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Loading playlist…",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSaving,
                onClick = {
                    val trimmed = urlText.trim()
                    if (!isValidPlaylistUrl(trimmed)) {
                        errorText = "Enter a valid http(s) playlist URL."
                    } else {
                        scope.launch {
                            isSaving = true
                            val error = onSave(trimmed, showLogos)
                            isSaving = false
                            if (error == null) onDismiss() else errorText = error
                        }
                    }
                }
            ) {
                Text("Save", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isSaving,
                onClick = onDismiss
            ) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}
