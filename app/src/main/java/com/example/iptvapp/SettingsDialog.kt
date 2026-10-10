package com.example.iptvapp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
    val audioOnly: Boolean
)

/**
 * Settings dialog, strict black/gold. Surface background with 20dp
 * corners, a divider under the title and above the action row, a
 * scrollable body with real content padding, and toggles that read
 * clearly on and off (gold track when on; card track with a disabled
 * border and thumb when off) with a one-line gray description each.
 *
 * Playlist rows carry the URL on two lines; delete asks for confirmation
 * and is refused for the last remaining playlist. Any change that swaps
 * the active playlist makes the app fetch and parse it BEFORE anything is
 * persisted or the cache is replaced — a bad URL or empty playlist leaves
 * everything exactly as it was, with the error shown inline.
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
    onDismiss: () -> Unit,
    onSave: suspend (SettingsDraft) -> String?
) {
    var selectedPlaylistId by remember { mutableStateOf(initialActivePlaylistId) }
    var addingNew by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newUrl by remember { mutableStateOf("") }
    var removedPlaylistIds by remember { mutableStateOf(emptyList<Long>()) }
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }
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
    var errorText by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val visiblePlaylists = playlists.filter { it.id !in removedPlaylistIds }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        containerColor = PaletteSurface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Column {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleMedium,
                    color = PaletteTextPrimary
                )
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = PaletteOutline, thickness = 1.dp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = "Playlists",
                    style = MaterialTheme.typography.labelMedium,
                    color = PaletteGold
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
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = PaletteGold,
                                unselectedColor = PaletteTextSecondary
                            )
                        )
                        if (renamingPlaylistId == playlist.id) {
                            OutlinedTextField(
                                value = renameText,
                                onValueChange = { renameText = it },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                label = {
                                    Text(text = "Name", color = PaletteTextSecondary)
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = PaletteTextPrimary,
                                    unfocusedTextColor = PaletteTextPrimary,
                                    focusedBorderColor = PaletteGold,
                                    unfocusedBorderColor = PaletteOutline,
                                    cursorColor = PaletteGold
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
                                Text("OK", color = PaletteGold)
                            }
                            TextButton(
                                onClick = {
                                    renamingPlaylistId = null
                                    errorText = null
                                }
                            ) {
                                Text("Cancel", color = PaletteTextSecondary)
                            }
                        } else {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = renamedPlaylists[playlist.id] ?: playlist.name,
                                    fontSize = 14.sp,
                                    color = PaletteTextPrimary
                                )
                                Text(
                                    text = playlist.url,
                                    fontSize = 11.sp,
                                    color = PaletteTextSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TextButton(onClick = {
                                renamingPlaylistId = playlist.id
                                renameText = renamedPlaylists[playlist.id] ?: playlist.name
                            }) {
                                Text(text = "Rename", color = PaletteGold)
                            }
                            // Deletion is confirmed and refused for the
                            // active playlist and for the last remaining
                            // one — the registry must never end up empty.
                            IconButton(
                                onClick = { pendingDeleteId = playlist.id },
                                enabled = playlist.id != selectedPlaylistId && visiblePlaylists.size > 1
                            ) {
                                Icon(
                                    imageVector = TribalIcons.Close,
                                    contentDescription = "Delete playlist",
                                    tint = PaletteTextSecondary
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
                            Text(text = "Name", color = PaletteTextSecondary)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = PaletteTextPrimary,
                            unfocusedTextColor = PaletteTextPrimary,
                            focusedBorderColor = PaletteGold,
                            unfocusedBorderColor = PaletteOutline,
                            cursorColor = PaletteGold
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
                            Text(text = "Playlist URL", color = PaletteTextSecondary)
                        },
                        isError = errorText != null,
                        supportingText = errorText?.let { message ->
                            {
                                Text(text = message, color = PaletteOffline)
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = PaletteTextPrimary,
                            unfocusedTextColor = PaletteTextPrimary,
                            focusedBorderColor = PaletteGold,
                            unfocusedBorderColor = PaletteOutline,
                            cursorColor = PaletteGold
                        )
                    )
                    TextButton(onClick = { newUrl = SettingsStore.DEFAULT_PLAYLIST_URL }) {
                        Text(text = "Use default playlist URL", color = PaletteGold)
                    }
                    TextButton(
                        onClick = {
                            addingNew = false
                            newName = ""
                            newUrl = ""
                            errorText = null
                        }
                    ) {
                        Text(text = "Cancel adding", color = PaletteTextSecondary)
                    }
                } else {
                    TextButton(onClick = { addingNew = true }) {
                        Text(text = "+ Add playlist", color = PaletteGold)
                    }
                    // A failed switch of an existing playlist has no text
                    // field to attach to — surface the error here.
                    errorText?.let { message ->
                        Text(
                            text = message,
                            fontSize = 13.sp,
                            color = PaletteOffline
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                SettingSwitch(
                    label = "Resume last channel on launch",
                    description = "Reopen the last channel when the app starts.",
                    checked = resumeOnLaunch,
                    onCheckedChange = { resumeOnLaunch = it }
                )
                SettingSwitch(
                    label = "Data saver",
                    description = "Caps quality. Only helps channels that offer multiple qualities.",
                    checked = dataSaver,
                    onCheckedChange = { dataSaver = it }
                )
                SettingSwitch(
                    label = "Audio only",
                    description = "Plays sound with no video, for music and radio.",
                    checked = audioOnly,
                    onCheckedChange = { audioOnly = it }
                )
                SettingSwitch(
                    label = "Show channel logos",
                    description = "Loads channel artwork from the playlist.",
                    checked = showLogos,
                    onCheckedChange = { showLogos = it }
                )
                SettingSwitch(
                    label = "Hide geo-blocked",
                    description = "Based on playlist tags, not a live check.",
                    checked = hideGeoBlocked,
                    onCheckedChange = { hideGeoBlocked = it }
                )
                SettingSwitch(
                    label = "Hide Not 24/7",
                    description = "Based on playlist tags, not a live check.",
                    checked = hideNot24x7,
                    onCheckedChange = { hideNot24x7 = it }
                )
                SettingSwitch(
                    label = "HD only (720p+)",
                    description = "Keeps 720p and above; unlisted-quality channels stay.",
                    checked = hdOnly,
                    onCheckedChange = { hdOnly = it }
                )
                if (isSaving) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = PaletteGold
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Loading playlist…",
                            fontSize = 13.sp,
                            color = PaletteTextSecondary
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = PaletteOutline, thickness = 1.dp)
            }
        },
        confirmButton = {
            Button(
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
                                    audioOnly = audioOnly
                                )
                            )
                            isSaving = false
                            if (error == null) onDismiss() else errorText = error
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PaletteGold,
                    contentColor = PaletteBackground
                )
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isSaving,
                onClick = onDismiss
            ) {
                Text("Cancel", color = PaletteTextSecondary)
            }
        }
    )

    // Deletion confirmation — its own compact dialog over the settings one.
    if (pendingDeleteId != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            containerColor = PaletteSurface,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "Delete this playlist?",
                    style = MaterialTheme.typography.titleMedium,
                    color = PaletteTextPrimary
                )
            },
            text = {
                Text(
                    text = "It will be removed from the list. Favorites and history are kept.",
                    color = PaletteTextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteId?.let { removedPlaylistIds = removedPlaylistIds + it }
                    pendingDeleteId = null
                }) {
                    Text("Delete", color = PaletteOffline)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) {
                    Text("Cancel", color = PaletteTextSecondary)
                }
            }
        )
    }
}

@Composable
private fun SettingSwitch(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 14.sp,
                color = PaletteTextPrimary
            )
            Text(
                text = description,
                fontSize = 11.sp,
                color = PaletteTextSecondary
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = PaletteBackground,
                checkedTrackColor = PaletteGold,
                checkedBorderColor = PaletteGold,
                uncheckedThumbColor = PaletteDisabled,
                uncheckedTrackColor = PaletteCard,
                uncheckedBorderColor = PaletteDisabled
            )
        )
    }
}