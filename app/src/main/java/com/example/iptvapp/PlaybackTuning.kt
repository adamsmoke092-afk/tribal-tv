package com.example.iptvapp

import androidx.media3.common.C
import androidx.media3.common.Player

/**
 * Centralised tuning constants for Tribal TV's player and retry behaviour.
 *
 * Why here, and why conservative: this app is tuned for a ~370 Kbps lossy
 * VPN tunnel on a low-memory device. Every buffer value is a trade-off:
 *
 *   - Startup time vs. how often playback stalls for refills.
 *   - Jitter tolerance after a transient network drop.
 *   - Memory footprint on a ~2–3 GB phone.
 *   - How far behind the live edge the stream sits (larger = more latency
 *     but more jitter room for live channels).
 *
 * I am deliberately *not* increasing all the values. Bigger buffers reduce
 * stalls but slow startup and waste memory; smaller buffers start faster
 * but stall more often on a flaky link. The values below are the same ones
 * the app was shipped with after the §2.3 tuning pass — they have shown
 * acceptable behaviour in CI and on-device tests. Revisit only if a
 * measured symptom (e.g. "startup takes 8s", "stalls every 40s") appears.
 *
 * The retry policy has its own bounded budget (a fixed number of retries with
 * capped exponential backoff) plus one application-level recovery attempt, so
 * a genuinely broken stream fails fast instead of holding the player in a
 * loading loop.
 */

/** Default audio attributes for all channels. */
val AUDIO_ATTRIBUTES = androidx.media3.common.AudioAttributes.DEFAULT

/**
 * Default HTTP socket timeouts. The ~8s platform default is tuned for normal
 * networks; a lossy VPN is often just slow, not dead, so these give it more
 * room. If these stay green for a release cycle they can be lowered.
 */
const val CONNECT_TIMEOUT_MS: Int = 25_000
const val READ_TIMEOUT_MS: Int = 25_000

/**
 * Load-control buffer durations (milliseconds). Rationale per field:
 *   MIN                = minimum buffer at start and after a refill (cushion
 *                        so playback does not begin/resume on a thin buffer).
 *   MAX                = hard ceiling ExoPlayer may grow to.
 *   FOR_PLAYBACK       = target buffer after first-start buffering.
 *   FOR_AFTER_REBUFFER = target buffer after a refill mid-playback.
 */
const val MIN_BUFFER_MS: Int = 20_000
const val MAX_BUFFER_MS: Int = 60_000
const val BUFFER_FOR_PLAYBACK_MS: Int = 5_000
const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS: Int = 10_000

/** How far behind the live edge HLS stays (ms). Larger = more jitter room,
 * more latency for live channels. */
const val LIVE_TARGET_OFFSET_MS: Long = 8_000L

/** Retry-After header cap (ms): we never wait longer than this for a provider
 * that asks us to back off. */
const val RETRY_AFTER_MAX_MS: Long = 60_000L

/**
 * Bounded internal retry budget for a single load task (Media3 1.4.1:
 * LoadErrorHandlingPolicy.getMinimumLoadableRetryCount). Only transient
 * failures are retried (see BoundedLoadErrorHandlingPolicy); permanent ones
 * (other 4xx, parse/format errors) are not retried at all, so this is a hard
 * ceiling, not a target. Three retries is enough to ride out a brief drop
 * without delaying a genuine failure by much.
 */
const val MAX_LOADABLE_RETRY_COUNT: Int = 3

/** Base for the exponential retry backoff (ms): 1s, 2s, 4s for errorCount 1,2,3. */
const val RETRY_BASE_DELAY_MS: Long = 1_000L

/** Cap on a single retry delay (ms), so no individual wait is excessive. */
const val RETRY_MAX_DELAY_MS: Long = 8_000L

/**
 * Bounded application-level recovery: after ExoPlayer's internal retries
 * are exhausted, one manual re-prepare attempt is allowed per playback
 * episode (reset after playback reaches READY). The "Retry" button in the
 * UI covers an explicit user-initiated attempt. Zero or more than one would
 * either under-recover or open the door to a retry storm.
 */
const val MAX_APP_RECOVERY_ATTEMPTS: Int = 1

/**
 * How long playback may sit in BUFFERING before PlayerScreen shows a
 * "still buffering" warning. The warning does NOT mark the channel dead —
 * only a genuine player error does. Long enough to ride out a flaky link,
 * short enough to tell the user something is wrong rather than spin forever.
 */
const val BUFFERING_WARNING_AFTER_MS: Long = 25_000L

/**
 * Default video bitrate cap for Data Saver (bits/s): an adaptive HLS stream
 * is pulled down to a low rung; a fixed-bitrate stream above the cap still
 * plays because track-selection's exceedVideoConstraintsIfNecessary defaults
 * to true — a constraint no track satisfies is relaxed instead of failing
 * playback. Only which rung an adaptive stream picks is affected.
 */
const val DATA_SAVER_VIDEO_BITRATE = 500_000

/**
 * Applies the playback-related settings to the player's track selection.
 * Called once at service startup (PlaybackService) and after every
 * settings save. See the constants above for the rationale behind the values.
 */
fun applyPlaybackPreferences(player: Player, dataSaver: Boolean, audioOnly: Boolean) {
    player.trackSelectionParameters = player.trackSelectionParameters
        .buildUpon()
        .setMaxVideoBitrate(if (dataSaver) DATA_SAVER_VIDEO_BITRATE else Int.MAX_VALUE)
        .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, audioOnly)
        .build()
}
