package com.example.iptvapp

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.util.Rational
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

/**
 * Playback screen, strict black/gold edition. Wraps Media3's PlayerView
 * via AndroidView (no native Compose video surface). The top bar carries
 * the channel name with live resolution/bitrate info, prev/next channel
 * zapping through the list the user came from, the PiP trigger, and
 * quick toggles for Data saver / Audio only that apply instantly and
 * share the persisted settings with the dialog. Failures the retry
 * logic can't cover surface in a card (Surface color, Offline-colored
 * message, gold "Back to channels"). Chrome hides in landscape and in
 * PiP — zapping and PiP stay as small overlays in landscape.
 */
@Composable
fun PlayerScreen(
    player: Player,
    channelName: String,
    isInPip: Boolean,
    dataSaver: Boolean,
    audioOnly: Boolean,
    onToggleDataSaver: () -> Unit,
    onToggleAudioOnly: () -> Unit,
    onPrevChannel: () -> Unit,
    onNextChannel: () -> Unit,
    onPlaybackFailed: () -> Unit,
    onPlaybackSucceeded: () -> Unit,
    onBack: () -> Unit
) {
    var isBuffering by remember { mutableStateOf(false) }
    // Seeded from the player so an error that struck while the UI was gone
    // (background play) still shows its card on return.
    var statusMessage by remember {
        mutableStateOf(
            player.playerError?.let {
                "This channel isn't playable right now (${it.errorCodeName})."
            }
        )
    }
    var videoInfo by remember { mutableStateOf(videoInfoText(player)) }
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
    // library or back stack to intercept it otherwise.
    BackHandler(onBack = onBack)

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    statusMessage = null
                    videoInfo = videoInfoText(player)
                    // A "dead" channel that plays again is forgiven — the
                    // dead-channel memory self-heals.
                    onPlaybackSucceeded()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // Anything reaching here that PlayerFactory didn't already
                // recover from (a genuinely dead or geo-blocked stream, or
                // an unsupported format) gets a visible message instead
                // of a silently stuck screen.
                statusMessage = "This channel isn't playable right now (${error.errorCodeName})."
                onPlaybackFailed()
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                videoInfo = videoInfoText(player)
            }

            override fun onTracksChanged(tracks: Tracks) {
                videoInfo = videoInfoText(player)
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
                    "geo-blocked, or too-high-bitrate for your current connection."
                // A stream that never came up counts as a failure for the
                // dead-channel memory too — the list dims it and skips it
                // in Recently watched until the failure expires.
                onPlaybackFailed()
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(PaletteBackground)
    ) {
        if (!isLandscape && !isInPip) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = TribalIcons.Back,
                            contentDescription = "Back",
                            tint = PaletteTextPrimary
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = display.cleanName,
                            style = MaterialTheme.typography.titleMedium,
                            color = PaletteTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = videoInfo,
                            fontSize = 11.sp,
                            color = PaletteTextSecondary
                        )
                    }
                    IconButton(onClick = onPrevChannel) {
                        Icon(
                            imageVector = TribalIcons.Prev,
                            contentDescription = "Previous channel",
                            tint = PaletteTextPrimary
                        )
                    }
                    IconButton(onClick = onNextChannel) {
                        Icon(
                            imageVector = TribalIcons.Next,
                            contentDescription = "Next channel",
                            tint = PaletteTextPrimary
                        )
                    }
                    TextButton(onClick = enterPip) {
                        Text("PiP", color = PaletteTextPrimary)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickToggle(label = "Data saver", active = dataSaver, onToggle = onToggleDataSaver)
                    QuickToggle(label = "Audio only", active = audioOnly, onToggle = onToggleAudioOnly)
                }
                HorizontalDivider(color = PaletteOutline, thickness = 1.dp)
            }
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

            // Landscape hides the header, so zapping and the PiP trigger
            // live as a small overlay on the video itself.
            if (isLandscape && !isInPip) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    IconButton(onClick = onPrevChannel) {
                        Icon(
                            imageVector = TribalIcons.Prev,
                            contentDescription = "Previous channel",
                            tint = PaletteTextPrimary
                        )
                    }
                    IconButton(onClick = onNextChannel) {
                        Icon(
                            imageVector = TribalIcons.Next,
                            contentDescription = "Next channel",
                            tint = PaletteTextPrimary
                        )
                    }
                    TextButton(onClick = enterPip) {
                        Text("PiP", color = PaletteTextPrimary)
                    }
                }
            }

            // Any error while in PiP just shows a quiet black window; the
            // card is only useful once the user is back in the app.
            if (!isInPip) statusMessage?.let { message ->
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PaletteSurface)
                        .padding(16.dp)
                ) {
                    Text(message, color = PaletteOffline)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onBack,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PaletteGold,
                                contentColor = PaletteBackground
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Back to channels")
                        }
                        OutlinedButton(
                            onClick = {
                                statusMessage = null
                                player.prepare()
                                player.playWhenReady = true
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, PaletteOutline),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = PaletteTextPrimary
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

/**
 * Resolution + bitrate line from the currently selected video track;
 * "audio only" when there is no video track (video disabled, or a
 * pure-audio stream).
 */
private fun videoInfoText(player: Player): String {
    val format = player.videoFormat
    return when {
        format == null || format.height <= 0 -> "audio only"
        format.bitrate > 0 -> "${format.height}p · ${format.bitrate / 1000} Kbps"
        else -> "${format.height}p"
    }
}

/** Compact instant-apply toggle chip: gold when on, card + outline when off. */
@Composable
private fun QuickToggle(label: String, active: Boolean, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Text(
        text = label,
        fontSize = 11.sp,
        color = if (active) PaletteBackground else PaletteTextSecondary,
        modifier = Modifier
            .clip(shape)
            .background(if (active) PaletteGold else PaletteCard)
            .border(1.dp, if (active) PaletteGold else PaletteOutline, shape)
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}