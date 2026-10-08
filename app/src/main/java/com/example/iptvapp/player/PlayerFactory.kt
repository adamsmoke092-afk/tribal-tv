package com.example.iptvapp.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

/**
 * Builds an ExoPlayer instance tuned for a slow/lossy VPN tunnel, per SPEC §2.3.
 * These are not generic "good defaults" — every value is chosen against the
 * measured ~370 Kbps baseline in §2.3.1. Revisit if that baseline changes.
 */
object PlayerFactory {

    // loadChannel() registers a fresh error-retry listener per channel and
    // must remove the previous one first — otherwise every channel switch
    // stacks another listener, and one network error would trigger N
    // simultaneous re-prepares (N = channels visited this session).
    private var retryListener: Player.Listener? = null

    // Buffer sizing — bigger cushion so playback doesn't start/resume on a thin buffer.
    private const val MIN_BUFFER_MS = 20_000
    private const val MAX_BUFFER_MS = 60_000
    private const val BUFFER_FOR_PLAYBACK_MS = 5_000
    private const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 10_000

    // HTTP timeouts — default (~8s) is tuned for normal networks; a lossy
    // tunnel is often just slow, not dead, so give it more room.
    private const val CONNECT_TIMEOUT_MS = 25_000
    private const val READ_TIMEOUT_MS = 25_000

    // Stay further behind the live edge for more jitter tolerance.
    private const val LIVE_TARGET_OFFSET_MS = 8_000L

    fun create(context: Context): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                MIN_BUFFER_MS,
                MAX_BUFFER_MS,
                BUFFER_FOR_PLAYBACK_MS,
                BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            )
            .build()

        val baseHttpFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
            .setReadTimeoutMs(READ_TIMEOUT_MS)
            .setAllowCrossProtocolRedirects(true)

        val retryingFactory = RetryingDataSource.Factory(
            delegateFactory = baseHttpFactory,
            maxRetries = 3,
            initialDelayMs = 1000L
        )

        return ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            // Public playlists mix HLS with progressive and other stream
            // types; DefaultMediaSourceFactory picks the right source per
            // MediaItem instead of forcing every URL through HLS. Everything
            // still flows through the retrying data source.
            .setMediaSourceFactory(DefaultMediaSourceFactory(retryingFactory))
            .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus= */ true)
            .build()
    }

    /**
     * Loads a channel and wires up recoverable-error handling: on a network
     * error, re-prepare instead of surfacing a hard failure (SPEC §2.3).
     */
    fun loadChannel(player: Player, streamUrl: String) {
        // mediaId doubles as the stream URL so the UI can resync to
        // whatever the session is already playing after a reconnect.
        val mediaItem = MediaItem.Builder()
            .setMediaId(streamUrl)
            .setUri(streamUrl)
            .setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(LIVE_TARGET_OFFSET_MS)
                    .build()
            )
            .build()

        retryListener?.let { player.removeListener(it) }
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (isRecoverable(error)) {
                    player.prepare()
                }
            }
        }
        retryListener = listener
        player.addListener(listener)

        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
    }

    private fun isRecoverable(error: PlaybackException): Boolean =
        error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED
}
