package com.example.iptvapp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

/**
 * Playback screen (SPEC §2.4). Wraps Media3's PlayerView via AndroidView —
 * Compose has no native video-surface composable, so this is the standard
 * bridge for it.
 *
 * Also surfaces failure states PlayerFactory's retry logic doesn't cover
 * (SPEC §4, risks #4 and #5): a dead/geo-blocked channel would otherwise
 * leave the user staring at an infinite spinner with no signal that
 * something's actually wrong, or silently fail on any error code besides
 * the three network ones PlayerFactory retries.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    player: ExoPlayer,
    channelName: String,
    onBack: () -> Unit
) {
    var isBuffering by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    // Without this, the system/gesture back button exits the whole app
    // instead of returning to the channel list — there's no navigation
    // library or back stack to intercept it otherwise (SPEC §4 audit).
    BackHandler(onBack = onBack)

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) statusMessage = null
            }

            override fun onPlayerError(error: PlaybackException) {
                // Anything reaching here that PlayerFactory didn't already
                // recover from (e.g. a genuinely dead or geo-blocked stream,
                // or an unsupported format) gets a visible message instead
                // of a silently stuck screen.
                statusMessage = "This channel isn't playable right now (${error.errorCodeName})."
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // If buffering drags on this long with no recovery, it's more likely a
    // dead/geo-blocked/too-high-bitrate stream than a briefly slow one —
    // say so instead of spinning forever.
    LaunchedEffect(isBuffering) {
        if (isBuffering) {
            delay(20_000)
            if (isBuffering) {
                statusMessage = "Still buffering after 20s — this channel may be dead, " +
                    "geo-blocked, or too high-bitrate for your current connection."
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(channelName) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Text("←")
                }
            }
        )

        Box(Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    PlayerView(context).apply {
                        this.player = player
                        useController = true
                    }
                }
            )

            statusMessage?.let { message ->
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Text(message)
                    Button(onClick = onBack) {
                        Text("Back to channels")
                    }
                }
            }
        }
    }
}
