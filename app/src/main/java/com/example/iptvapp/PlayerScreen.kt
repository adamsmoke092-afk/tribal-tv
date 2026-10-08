package com.example.iptvapp

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.util.Rational
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 *
 * UI: dark cinema (redesign step 3) + feature batch: the manifest's
 * configChanges keeps the activity alive across rotation, so playback and
 * the selected channel survive; the header bar hides in landscape so the
 * video fills the screen; the error card gained a Retry button. Picture-
 * in-picture: a header (or landscape overlay) button enters a floating
 * window; all chrome hides while in it.
 * Playback listeners, BackHandler and the buffering timer keep their
 * behavior; the listeners now also report success/failure so channels
 * that fail get remembered and dimmed in the list.
 */
@Composable
fun PlayerScreen(
    player: ExoPlayer,
    channelName: String,
    isInPip: Boolean,
    onPlaybackFailed: () -> Unit,
    onPlaybackSucceeded: () -> Unit,
    onBack: () -> Unit
) {
    var isBuffering by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val display = remember(channelName) { parseDisplay(channelName) }

    // Landscape = video fills the screen; the PlayerView controller and
    // the system back gesture still work without the header bar.
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // The PiP window takes the video's aspect ratio when the stream has a
    // sane one (the system accepts 2.39:1–1:2.39); anything freak or
    // unknown falls back to 16:9.
    val activity = LocalContext.current as? Activity
    val enterPip: () -> Unit = {
        val videoSize = player.videoSize
        val aspect = if (
            videoSize.width > 0 && videoSize.height > 0 &&
            videoSize.width.toFloat() / videoSize.height in 0.5f..2.0f
        ) Rational(videoSize.width, videoSize.height) else Rational(16, 9)
        activity?.enterPictureInPictureMode(
            PictureInPictureParams.Builder().setAspectRatio(aspect).build()
        )
    }

    // Without this, the system/gesture back button exits the whole app
    // instead of returning to the channel list — there's no navigation
    // library or back stack to intercept it otherwise (SPEC §4 audit).
    BackHandler(onBack = onBack)

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    statusMessage = null
                    // A "dead" channel that plays again is forgiven — the
                    // dead-channel memory self-heals.
                    onPlaybackSucceeded()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // Anything reaching here that PlayerFactory didn't already
                // recover from (e.g. a genuinely dead or geo-blocked stream,
                // or an unsupported format) gets a visible message instead
                // of a silently stuck screen.
                statusMessage = "This channel isn't playable right now (${error.errorCodeName})."
                onPlaybackFailed()
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

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (!isLandscape && !isInPip) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = TribalIcons.Back,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Column {
                    Text(
                        text = display.cleanName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip("Live", accent = true)
                        display.quality?.let { Chip(it, accent = false) }
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = enterPip) {
                    Text("PiP", color = MaterialTheme.colorScheme.onBackground)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
        }

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

            // Landscape hides the header, so the PiP trigger lives as a
            // small overlay on the video itself.
            if (isLandscape && !isInPip) {
                TextButton(
                    onClick = enterPip,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text("PiP", color = MaterialTheme.colorScheme.onBackground)
                }
            }

            // Any error while in PiP just shows a quiet black window; the
            // card is only useful once the user is back in the app.
            if (!isInPip) statusMessage?.let { message ->
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp)
                ) {
                    Text(message, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onBack,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Back to channels")
                        }
                        Button(
                            onClick = {
                                statusMessage = null
                                player.prepare()
                                player.playWhenReady = true
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, accent: Boolean) {
    Text(
        text = text,
        fontSize = 11.sp,
        color = if (accent) TribalAccentTintText else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (accent) TribalAccentTint else TribalChipBackground)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
