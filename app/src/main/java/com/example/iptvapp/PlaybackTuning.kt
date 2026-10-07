package com.example.iptvapp

import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer

/**
 * Applies the playback-related settings to the player's track selection.
 * Called once at startup (MainActivity) and after every settings save.
 *
 * Data saver caps the video bitrate so an adaptive stream stays on a low
 * rung. A fixed-bitrate stream above the cap still plays — the selector's
 * exceedVideoConstraintsIfNecessary defaults to true (verified against the
 * Media3 1.4.1 source), so a constraint no track satisfies is relaxed
 * instead of failing playback. The cap only changes which rung an
 * adaptive stream picks.
 *
 * Audio only disables the video track and plays the stream's audio. A
 * stream with no audio track fails in this mode, so a failure while
 * audio-only is on must not mark the channel dead (see IptvApp).
 */
const val DATA_SAVER_VIDEO_BITRATE = 500_000

fun applyPlaybackPreferences(player: ExoPlayer, dataSaver: Boolean, audioOnly: Boolean) {
    player.trackSelectionParameters = player.trackSelectionParameters
        .buildUpon()
        .setMaxVideoBitrate(if (dataSaver) DATA_SAVER_VIDEO_BITRATE else Int.MAX_VALUE)
        .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, audioOnly)
        .build()
}
