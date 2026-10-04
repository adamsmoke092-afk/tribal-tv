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
 */
@Composable
fun IptvApp(
    playlistUrl: String,
    repository: PlaylistRepository,
    player: ExoPlayer
) {
    var channels by remember { mutableStateOf<List<M3uChannel>>(emptyList()) }
    var selectedChannel by remember { mutableStateOf<M3uChannel?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
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
            }
        )
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
