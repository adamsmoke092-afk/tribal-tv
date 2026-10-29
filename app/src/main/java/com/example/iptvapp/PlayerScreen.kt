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
import com.example.iptvapp.player.PlayerFactory
import com.example.iptvapp.player.RecoveryBudget
import com.example.iptvapp.player.bufferingWarningDue
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
    selectedChannel: com.example.iptvapp.playlist.M3uChannel,
    isInPip: Boolean,
    dataSaver: Boolean,
    audioOnly: Boolean,
    nowNext: com.example.iptvapp.epg.NowNext?,
    onToggleDataSaver: () -> Unit,
    onToggleAudioOnly: () -> Unit,
    onPrevChannel: () -> Unit,
    onNextChannel: () -> Unit,
    onPlaybackFailed: () -> Unit,
    onPlaybackSucceeded: () -> Unit,
    onBack: () -> Unit
) {
    val channelName = selectedChannel.name
    var isBuffering by remember { mutableStateOf(false) }
    // Seeded from the player so an error that struck while the UI was gone
    // (background play) still shows its card on return.
    var statusMessage by remember {
        mutableStateOf(player.playerError?.let { playbackErrorMessage(it) })
    }
    var bufferingWarning by remember { mutableStateOf<String?>(null) }
    // A per-load attempt identifier. It is bumped every time a channel starts
    // loading, becomes ready, or hits an error, so a buffering timer or
    // warning belonging to an old (switched-away) channel can never mark the
    // current channel dead or leave a stale message behind.
    var playbackAttemptId by remember { mutableStateOf(0) }
    // Shared by the player listener and the channel-load effect below. The
    // lambda only touches the state object, so it stays valid across
    // recompositions.
    val startNewAttempt: () -> Unit = {
        playbackAttemptId = (playbackAttemptId + 1).takeUnless { it == Int.MIN_VALUE } ?: 0
    }
    var videoInfo by remember { mutableStateOf(videoInfoText(player)) }
    val display = remember(channelName) { parseDisplay(channelName) }

    // Load the selected channel onto the player. Skipped when the player is
    // already on this MediaItem (e.g. reopening the app while it plays, or
    // attaching to a background session) so we don't restart playback.
    // A single Player.Listener below owns UI updates AND the bounded
    // application-level recovery, so two listeners never race (one marking a
    // channel dead while another re-prepares it).
    val recoveryBudget = remember { RecoveryBudget() }
    LaunchedEffect(selectedChannel.streamUrl) {
        if (player.currentMediaItem?.mediaId == selectedChannel.streamUrl) return@LaunchedEffect
        startNewAttempt()
        statusMessage = null
        bufferingWarning = null
        // A new channel starts a fresh recovery episode.
        recoveryBudget.reset()
        PlayerFactory.loadChannel(
            player,
            selectedChannel.streamUrl,
            selectedChannel.headers
        )
    }

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

    DisposableEffect(player, selectedChannel.streamUrl) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    // A fresh attempt and a fresh recovery episode when
                    // playback becomes healthy for this channel — this also
                    // cancels any buffering timer waiting from an earlier
                    // state.
                    startNewAttempt()
                    recoveryBudget.reset()
                    statusMessage = null
                    bufferingWarning = null
                    videoInfo = videoInfoText(player)
                    // A "dead" channel that plays again is forgiven — the
                    // dead-channel memory self-heals.
                    onPlaybackSucceeded()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // ExoPlayer's load-error policy already retried transient
                // failures internally. This is the single bounded
                // application-level safety net: one re-prepare per playback
                // episode, and only while the failed stream is still the
                // selected one. Recovering is treated as transient, so we do
                // NOT flash a dead-channel card or poison the dead cache for a
                // stream that may come back on the retry.
                if (recoveryBudget.tryConsume(player.currentMediaItem?.mediaId, selectedChannel.streamUrl)) {
                    startNewAttempt()
                    statusMessage = null
                    bufferingWarning = null
                    player.prepare()
                    player.playWhenReady = true
                    return
                }
                // Budget spent (or the player moved on): surface the failure
                // and record it. Buffering alone never marks a channel dead —
                // only this genuine error path does.
                startNewAttempt()
                statusMessage = playbackErrorMessage(error)
                bufferingWarning = null
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

    // Buffered for longer than this with no recovery, warn the user that the
    // connection is slow or the stream may be dead/geo-blocked/too-high-
    // bitrate. Crucially this does NOT mark the channel dead: a fixed
    // buffering timer is exactly how slow-but-legitimate streams get
    // misclassified. The timer captures the attempt it started for and the
    // pure bufferingWarningDue() guard cancels it when buffering ends or the
    // attempt changes (channel switch), so a stale timer can never warn — or
    // kill — a newly selected channel.
    LaunchedEffect(isBuffering, playbackAttemptId) {
        if (isBuffering) {
            val timerAttemptId = playbackAttemptId
            delay(BUFFERING_WARNING_AFTER_MS)
            if (bufferingWarningDue(isBuffering, timerAttemptId, playbackAttemptId)) {
                bufferingWarning = "Still buffering after " +
                    "${BUFFERING_WARNING_AFTER_MS / 1000}s — the connection may be slow, " +
                    "or this channel could be geo-blocked or too high-bitrate for your network."
            }
        } else {
            bufferingWarning = null
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
                NowNextLine(nowNext)
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
            if (!isInPip) {
                val cardMessage = bufferingWarning ?: statusMessage
                cardMessage?.let { message ->
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
                                    // User-initiated retry starts a fresh attempt:
                                    // clears any warning, bumps the attempt so the
                                    // buffering timer restarts, and lets the player
                                    // try again. If this is the *only* error that
                                    // ever occurred (no genuine dead stream), the
                                    // retry succeeds and the channel is never
                                    // marked dead.
                                    bufferingWarning = null
                                    statusMessage = null
                                    startNewAttempt()
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
}

/**
 * Resolution + bitrate line for the currently selected video track;
 * "audio only" when there is no video track (video disabled, or a
 * pure-audio stream).
 *
 * Everything goes through reflection: the Player.videoFormat convenience
 * getter does not resolve against this project's Media3 (CI-verified,
 * 2026-10-10), and reflection has no compile-time API surface to get
 * wrong. Any failure degrades to "audio only" instead of showing wrong
 * numbers.
 */
private fun videoInfoText(player: Player): String {
    val format = runCatching {
        player.javaClass.getMethod("getVideoFormat").invoke(player)
    }.getOrNull() ?: return "audio only"
    val height = runCatching {
        format.javaClass.getField("height").getInt(format)
    }.getOrDefault(0)
    val bitrate = runCatching {
        format.javaClass.getField("bitrate").getInt(format)
    }.getOrDefault(0)
    return when {
        height <= 0 -> "audio only"
        bitrate > 0 -> "${height}p · ${bitrate / 1000} Kbps"
        else -> "${height}p"
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

/**
 * "Now / Next" line from the EPG. Renders nothing when there's no guide
 * data (EPG off, or the channel isn't in the guide), so the player is
 * unchanged for anyone not using EPG.
 */
@Composable
private fun NowNextLine(nowNext: com.example.iptvapp.epg.NowNext?) {
    val current = nowNext?.current ?: return
    val next = nowNext.next
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
    ) {
        Text(
            text = "NOW  ${current.title}",
            fontSize = 12.sp,
            color = PaletteTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (next != null) {
            Text(
                text = "NEXT  ${next.title}",
                fontSize = 11.sp,
                color = PaletteTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}