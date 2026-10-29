package com.example.iptvapp.player

import com.example.iptvapp.MAX_APP_RECOVERY_ATTEMPTS

/**
 * Pure helpers for the *application-level* playback recovery, kept out of the
 * anonymous Player.Listener in PlayerFactory so the rules can be unit-tested.
 *
 * The internal Media3 load-error policy already retries transient failures.
 * This is the single extra safety net: it must be bounded (never an endless
 * re-prepare loop) and it must never act for a channel the user already
 * switched away from.
 */

/**
 * A per-episode recovery budget. "Episode" resets when playback reaches
 * READY, so a later, separate failure can still recover once — but two
 * re-prepares can never fire for the same failure.
 */
internal class RecoveryBudget(private val maxAttempts: Int = MAX_APP_RECOVERY_ATTEMPTS) {

    var attempts: Int = 0
        private set

    /**
     * @param currentMediaId the player's current MediaItem.mediaId (null when
     *        nothing is loaded)
     * @param loadedStreamUrl the stream URL this budget belongs to
     * @return true if a recovery re-prepare is allowed: the player is still on
     *         [loadedStreamUrl] AND the per-episode budget is not exhausted.
     */
    fun tryConsume(currentMediaId: String?, loadedStreamUrl: String): Boolean {
        if (currentMediaId != loadedStreamUrl) return false
        if (attempts >= maxAttempts) return false
        attempts++
        return true
    }

    /** Called when playback becomes READY — starts a fresh episode. */
    fun reset() {
        attempts = 0
    }
}

/**
 * Pure decision for the buffering warning timer: show the "still buffering"
 * warning only when buffering is still in progress AND the timer still belongs
 * to the current playback attempt. A channel switch bumps the attempt id, so a
 * stale timer from a previous channel is cancelled (returns false) and can
 * never raise a warning — let alone mark a channel dead — for the new one.
 */
internal fun bufferingWarningDue(
    stillBuffering: Boolean,
    timerAttemptId: Int,
    currentAttemptId: Int
): Boolean = stillBuffering && timerAttemptId == currentAttemptId
