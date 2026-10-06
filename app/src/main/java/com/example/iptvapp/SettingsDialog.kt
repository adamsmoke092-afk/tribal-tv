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
 * The whole settings payload in one object, so the dialog can hand the
 * save handler a single value instead of five loose parameters.
 */
data class SettingsDraft(
    val playlistUrl: String,
    val showLogos: Boolean,
    val hideGeoBlocked: Boolean,
    val hideNot24x7: Boolean,
    val hdOnly: Boolean
)

/**
 * Settings dialog: playlist URL, logo switch, and the list-visibility
 * filters. Saving a changed URL fetches and parses the new playlist BEFORE
 * the persisted URL or the Room cache is touched — a bad URL or an empty
 * playlist leaves everything exactly as it was, with the error shown
 * inline under the field.
 */
@OptIn(ExperimentalMaterial3Api::class) // harmless if this AlertDialog overload is stable in our version
@Composable
fun SettingsDialog(
    currentUrl: String,
    initialShowLogos: Boolean,
    initialHideGeoBlocked: Boolean,
    initialHideNot24x7: Boolean,
    initialHdOnly: Boolean,
    onDismiss: () -> Unit,
    onSave: suspend (SettingsDraft) -> String?
) {
    var urlText by remember { mutableStateOf(currentUrl) }
    var showLogos by remember { mutableStateOf(initialShowLogos) }
    var hideGeoBlocked by remember { mutableStateOf(initialHideGeoBlocked) }
    var hideNot24x7 by remember { mutableStateOf(initialHideNot24x7) }
    var hdOnly by remember { mutableStateOf(initialHdOnly) }
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
                SettingSwitch("Show channel logos", showLogos) { showLogos = it }
                SettingSwitch("Hide geo-blocked", hideGeoBlocked) { hideGeoBlocked = it }
                SettingSwitch("Hide Not 24/7", hideNot24x7) { hideNot24x7 = it }
                SettingSwitch("HD only (720p+)", hdOnly) { hdOnly = it }
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
                            val error = onSave(
                                SettingsDraft(
                                    playlistUrl = trimmed,
                                    showLogos = showLogos,
                                    hideGeoBlocked = hideGeoBlocked,
                                    hideNot24x7 = hideNot24x7,
                                    hdOnly = hdOnly
                                )
                            )
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

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary
            )
        )
    }
}
