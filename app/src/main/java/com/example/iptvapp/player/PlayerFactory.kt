package com.example.iptvapp.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.iptvapp.AUDIO_ATTRIBUTES
import com.example.iptvapp.BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
import com.example.iptvapp.BUFFER_FOR_PLAYBACK_MS
import com.example.iptvapp.CONNECT_TIMEOUT_MS
import com.example.iptvapp.LIVE_TARGET_OFFSET_MS
import com.example.iptvapp.MAX_BUFFER_MS
import com.example.iptvapp.MIN_BUFFER_MS
import com.example.iptvapp.READ_TIMEOUT_MS

/**
 * Builds an ExoPlayer instance tuned for a slow/lossy VPN tunnel, per SPEC §2.3.
 * All tuning values live in [com.example.iptvapp.PlaybackTuning] (PlaybackTuning.kt);
 * PlayerFactory only composes them. Revisit the constants if the ~370 Kbps
 * baseline changes.
 */
object PlayerFactory {

    /**
     * Builds the player. The media source uses a bounded, classification-aware
     * retry policy (see [BoundedLoadErrorHandlingPolicy]) so transient network
     * failures retry internally and HTTP 4xx / parse failures fail fast.
     */
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

        // ResolvingDataSource injects the active stream's #EXTVLCOPT headers
        // (StreamHeaders) into EVERY request — the HLS playlist AND each
        // segment — so servers that gate on User-Agent/Referer accept playback.
        // The header factory runs per DataSpec, so headers come fresh for every
        // request and never stale across a channel switch.
        val resolvingFactory = ResolvingDataSource.Factory(baseHttpFactory) { dataSpec ->
            val headers = StreamHeaders.current
            if (headers.isEmpty()) dataSpec else dataSpec.withAdditionalHeaders(headers)
        }

        return ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            // Public playlists mix HLS with progressive and other stream
            // types; DefaultMediaSourceFactory picks the right source per
            // MediaItem instead of forcing every URL through HLS. Everything
            // still flows through the header-resolving data source + the
            // bounded retry policy below.
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(resolvingFactory)
                    .setLoadErrorHandlingPolicy(BoundedLoadErrorHandlingPolicy())
            )
            .setAudioAttributes(AUDIO_ATTRIBUTES, /* handleAudioFocus= */ true)
            .build()
    }

    /**
     * Loads a channel onto the player.
     *
     * Retry behaviour is delegated to the [BoundedLoadErrorHandlingPolicy]
     * attached to the media source factory: transient network failures and
     * appropriate server errors retry internally with bounded exponential
     * backoff (up to [com.example.iptvapp.MAX_LOADABLE_RETRY_COUNT] retries,
     * each delay capped at [com.example.iptvapp.RETRY_MAX_DELAY_MS]); HTTP 4xx
     * (auth/geoblock/missing) and parse/format errors fail fast. An
     * additional bounded application-level recovery attempt (ONE re-prepare
     * per playback episode, reset once playback becomes READY) is allowed,
     * and only ever fires for the channel that is still selected.
     *
     * @param player    the session player (owned by PlaybackService)
     * @param streamUrl the stream URL; also used as the MediaItem.mediaId
     * @param headers   stream-specific #EXTVLCOPT headers (User-Agent,
     *                  Referer, Origin) — applied to every request for this
     *                  channel
     */
    fun loadChannel(
        player: Player,
        streamUrl: String,
        headers: Map<String, String> = emptyMap()
    ) {
        // Activate this stream's required HTTP headers BEFORE prepare() so the
        // very first request (playlist fetch) already carries them. The
        // ResolvingDataSource reads StreamHeaders.current on every request.
        StreamHeaders.current = headers

        // mediaId doubles as the stream URL so the UI can resync to whatever
        // the session is already playing after a reconnect, and so the single
        // Player.Listener in PlayerScreen can tell whether a failure still
        // belongs to the selected channel before allowing a recovery attempt.
        val mediaItem = MediaItem.Builder()
            .setMediaId(streamUrl)
            .setUri(streamUrl)
            .setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(LIVE_TARGET_OFFSET_MS)
                    .build()
            )
            .build()

        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
    }
}
