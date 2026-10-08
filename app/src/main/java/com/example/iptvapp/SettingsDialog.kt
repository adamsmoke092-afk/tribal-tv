package com.example.iptvapp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * The whole settings payload in one object, so the dialog can hand the
 * save handler a single value instead of loose parameters. The playlist
 * fields describe the registry as of Save: which existing playlist is
 * active, an optional new playlist to add (adding always activates it),
 * and the ids of playlists to remove. Everything else is a plain switch.
 */
data class SettingsDraft(
    val selectedPlaylistId: Long,
    val newPlaylistName: String?,
    val newPlaylistUrl: String?,
    val removedPlaylistIds: List<Long>,
    val renamedPlaylists: Map<Long, String>,
    val showLogos: Boolean,
    val hideGeoBlocked: Boolean,
    val hideNot24x7: Boolean,
    val hdOnly: Boolean,
    val resumeOnLaunch: Boolean,
    val dataSaver: Boolean,
    val audioOnly: Boolean,
    val gridLayout: Boolean
)

/**
 * Settings dialog: the playlist registry (tap a row to make it active on
 * save, + to add one, ✕ to remove a non-active one), then the switches.
 * Any change that swaps the active playlist makes the app fetch and parse
 * it BEFORE anything is persisted or the Room cache is replaced — a bad
 * URL or an empty playlist leaves everything exactly as it was, with the
 * error shown inline.
 */
@OptIn(ExperimentalMaterial3Api::class) // harmless if this AlertDialog overload is stable in our version
@Composable
fun SettingsDialog(
    playlists: List<SavedPlaylist>,
    initialActivePlaylistId: Long,
    initialShowLogos: Boolean,
    initialHideGeoBlocked: Boolean,
    initialHideNot24x7: Boolean,
    initialHdOnly: Boolean,
    initialResumeOnLaunch: Boolean,
    initialDataSaver: Boolean,
    initialAudioOnly: Boolean,
    initialGridLayout: Boolean,
    onDismiss: () -> Unit,
    onSave: suspend (SettingsDraft) -> String?
) {
    var selectedPlaylistId by remember { mutableStateOf(initialActivePlaylistId) }
    var addingNew by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newUrl by remember { mutableStateOf("") }
    var removedPlaylistIds by remember { mutableStateOf(emptyList<Long>()) }
    // Rename mode: the row being edited and its text buffer. Confirmed
    // renames collect in the map and ride to Save with everything else.
    var renamingPlaylistId by remember { mutableStateOf<Long?>(null) }
    var renameText by remember { mutableStateOf("") }
    var renamedPlaylists by remember { mutableStateOf(mapOf<Long, String>()) }
    var showLogos by remember { mutableStateOf(initialShowLogos) }
    var hideGeoBlocked by remember { mutableStateOf(initialHideGeoBlocked) }
    var hideNot24x7 by remember { mutableStateOf(initialHideNot24x7) }
    var hdOnly by remember { mutableStateOf(initialHdOnly) }
    var resumeOnLaunch by remember { mutableStateOf(initialResumeOnLaunch) }
    var dataSaver by remember { mutableStateOf(initialDataSaver) }
    var audioOnly by remember { mutableStateOf(initialAudioOnly) }
    var gridLayout by remember { mutableStateOf(initialGridLayout) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val visiblePlaylists = playlists.filter { it.id !in removedPlaylistIds }

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
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Playlists",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                visiblePlaylists.forEach { playlist ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = playlist.id == selectedPlaylistId,
                            onClick = {
                                selectedPlaylistId = playlist.id
                                errorText = null
                            }
                        )
                        if (renamingPlaylistId == playlist.id) {
                            OutlinedTextField(
                                value = renameText,
                                onValueChange = { renameText = it },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                label = {
                                    Text(
                                        text = "Name",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    cursorColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            TextButton(
                                onClick = {
                                    val trimmed = renameText.trim()
                                    if (trimmed.isEmpty()) {
                                        errorText = "Give the playlist a name."
                                    } else {
                                        renamedPlaylists = renamedPlaylists + (playlist.id to trimmed)
                                        renamingPlaylistId = null
                                        errorText = null
                                    }
                                }
                            ) {
                                Text("OK", color = MaterialTheme.colorScheme.primary)
                            }
                            TextButton(
                                onClick = {
                                    renamingPlaylistId = null
                                    errorText = null
                                }
                            ) {
                                Text(
                                    "Cancel",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = renamedPlaylists[playlist.id] ?: playlist.name,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = playlist.url,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TextButton(onClick = {
                                renamingPlaylistId = playlist.id
                                renameText = renamedPlaylists[playlist.id] ?: playlist.name
                            }) {
                                Text(
                                    text = "Rename",
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            // The playlist that will be active can't be
                            // removed, and the registry must never end up
                            // empty — both enforced right here.
                            IconButton(
                                onClick = { removedPlaylistIds = removedPlaylistIds + playlist.id },
                                enabled = playlist.id != selectedPlaylistId && visiblePlaylists.size > 1
                            ) {
                                Icon(
                                    imageVector = TribalIcons.Close,
                                    contentDescription = "Remove playlist",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                if (addingNew) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = {
                            newName = it
                            errorText = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = {
                            Text(
                                text = "Name",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            cursorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    OutlinedTextField(
                        value = newUrl,
                        onValueChange = {
                            newUrl = it
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
                    TextButton(onClick = { newUrl = SettingsStore.DEFAULT_PLAYLIST_URL }) {
                        Text(
                            text = "Use default playlist URL",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    TextButton(
                        onClick = {
                            addingNew = false
                            newName = ""
                            newUrl = ""
                            errorText = null
                        }
                    ) {
                        Text(
                            text = "Cancel adding",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    TextButton(onClick = { addingNew = true }) {
                        Text(
                            text = "+ Add playlist",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    // A failed switch of an existing playlist has no text
                    // field to attach to — surface the error here.
                    errorText?.let { message ->
                        Text(
                            text = message,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                SettingSwitch("Resume last channel on launch", resumeOnLaunch) { resumeOnLaunch = it }
                SettingSwitch("Data saver (cap video quality)", dataSaver) { dataSaver = it }
                SettingSwitch("Audio only (play sound, no video)", audioOnly) { audioOnly = it }
                SettingSwitch("Show channel logos", showLogos) { showLogos = it }
                SettingSwitch("Grid layout (logo tiles)", gridLayout) { gridLayout = it }
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
                    val name = newName.trim()
                    val url = newUrl.trim()
                    if (addingNew && name.isEmpty()) {
                        errorText = "Give the playlist a name."
                    } else if (addingNew && !isValidPlaylistUrl(url)) {
                        errorText = "Enter a valid http(s) playlist URL."
                    } else {
                        // Fold an unconfirmed rename into the save, so
                        // typing a name and hitting Save works without
                        // pressing OK first.
                        val renaming = renamingPlaylistId
                        val draftRenames = if (renaming != null && renameText.isNotBlank()) {
                            renamedPlaylists + (renaming to renameText.trim())
                        } else {
                            renamedPlaylists
                        }
                        scope.launch {
                            isSaving = true
                            val error = onSave(
                                SettingsDraft(
                                    selectedPlaylistId = if (addingNew) NEW_PLAYLIST_ID else selectedPlaylistId,
                                    newPlaylistName = if (addingNew) name else null,
                                    newPlaylistUrl = if (addingNew) url else null,
                                    removedPlaylistIds = removedPlaylistIds,
                                    renamedPlaylists = draftRenames,
                                    showLogos = showLogos,
                                    hideGeoBlocked = hideGeoBlocked,
                                    hideNot24x7 = hideNot24x7,
                                    hdOnly = hdOnly,
                                    resumeOnLaunch = resumeOnLaunch,
                                    dataSaver = dataSaver,
                                    audioOnly = audioOnly,
                                    gridLayout = gridLayout
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
