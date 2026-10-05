package com.example.iptvapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.media3.exoplayer.ExoPlayer
import com.example.iptvapp.player.PlayerFactory
import com.example.iptvapp.playlist.M3uChannel
import com.example.iptvapp.playlist.PlaylistRepository
import kotlinx.coroutines.launch

/**
 * Top-level screen toggle: shows the channel list until something is
 * tapped, then hands the stream URL to PlayerFactory.loadChannel and
 * switches to the player screen (SPEC §2.4/§5 — "wire it together").
 *
 * Feature batch: owns the playlist URL as state, seeded from
 * SettingsStore by MainActivity. It only changes through the settings
 * dialog's save path, which fetches and parses the new playlist before
 * anything is persisted or cached.
 */
@Composable
fun IptvApp(
    initialPlaylistUrl: String,
    settings: SettingsStore,
    repository: PlaylistRepository,
    player: ExoPlayer
) {
    var channels by remember { mutableStateOf<List<M3uChannel>>(emptyList()) }
    var selectedChannel by remember { mutableStateOf<M3uChannel?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var playlistUrl by remember { mutableStateOf(initialPlaylistUrl) }
    var showLogos by remember { mutableStateOf(settings.showLogos()) }
    var showSettings by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            channels = repository.getChannels(playlistUrl)
        } catch (e: Exception) {
            loadError = "Failed to load playlist: ${e.message}"
        }
    }

    val current = selectedChannel
    if (current == null) {
        ChannelListScreen(
            channels = channels,
            error = loadError,
            onChannelSelected = { channel ->
                PlayerFactory.loadChannel(player, channel.streamUrl)
                selectedChannel = channel
            },
            onRefresh = {
                scope.launch {
                    try {
                        channels = repository.refreshChannels(playlistUrl)
                        loadError = null
                    } catch (e: Exception) {
                        loadError = "Refresh failed: ${e.message}"
                    }
                }
            },
            onOpenSettings = { showSettings = true }
        )

        if (showSettings) {
            SettingsDialog(
                currentUrl = playlistUrl,
                initialShowLogos = showLogos,
                onDismiss = { showSettings = false },
                onSave = { newUrl, newShowLogos ->
                    try {
                        if (newUrl != playlistUrl) {
                            // Throws before the Room cache is replaced if the
                            // fetch fails or the playlist parses to nothing.
                            val loaded = repository.refreshChannels(newUrl)
                            channels = loaded
                            playlistUrl = newUrl
                            settings.setPlaylistUrl(newUrl)
                        }
                        if (newShowLogos != showLogos) {
                            showLogos = newShowLogos
                            settings.setShowLogos(newShowLogos)
                        }
                        null
                    } catch (e: Exception) {
                        e.message ?: "Failed to load that playlist"
                    }
                }
            )
        }
    } else {
        PlayerScreen(
            player = player,
            channelName = current.name,
            onBack = {
                player.stop()
                selectedChannel = null
            }
        )
    }
}
