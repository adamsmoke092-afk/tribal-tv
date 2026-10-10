package com.example.iptvapp

import android.content.Context
import android.content.SharedPreferences

/** How long a recorded failure sticks before the channel is retried. */
const val DEAD_EXPIRY_MS: Long = 24 * 60 * 60 * 1000L

/** Pure: records a failure with the given wall-clock timestamp. */
fun markDead(
    current: Map<String, Long>,
    streamUrl: String,
    nowMs: Long
): Map<String, Long> = current + (streamUrl to nowMs)

/** Pure: removes a URL — a channel that plays again is forgiven. */
fun unmarkDead(current: Map<String, Long>, streamUrl: String): Map<String, Long> =
    current - streamUrl

/** Pure: the still-valid dead set — failures older than the expiry drop out. */
fun deadUrls(failures: Map<String, Long>, nowMs: Long, maxAgeMs: Long = DEAD_EXPIRY_MS): Set<String> =
    failures.filterValues { it in (nowMs - maxAgeMs)..nowMs }.keys

/** Pure: url\u0000timestamp lines — separators that never occur in URLs. */
fun encodeDead(failures: Map<String, Long>): String =
    failures.entries.joinToString("\n") { "${it.key}\u0000${it.value}" }

/** Pure inverse of encodeDead; junk lines drop out. */
fun decodeDead(encoded: String?): Map<String, Long> =
    encoded?.split('\n')
        ?.mapNotNull { line ->
            val idx = line.indexOf('\u0000')
            if (idx <= 0) return@mapNotNull null
            val ts = line.substring(idx + 1).toLongOrNull() ?: return@mapNotNull null
            line.substring(0, idx) to ts
        }
        ?.toMap()
        ?: emptyMap()

/**
 * Stream URLs that failed to play, with timestamps, in SharedPreferences —
 * user data must outlive the Room cache (same reasoning as
 * favourites/recents). A failure expires after 24h so dead-looking
 * channels are retried eventually; a channel that plays successfully is
 * un-marked, so the memory self-heals when a link comes back. The
 * pre-expiry format (a plain set with no timestamps) is deliberately not
 * migrated — its entries carry no age, so they would expire instantly
 * anyway.
 */
class DeadChannelStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("tribal_tv_dead", Context.MODE_PRIVATE)

    private fun failures(): Map<String, Long> =
        decodeDead(prefs.getString(KEY_DEAD, null))

    fun dead(): Set<String> = deadUrls(failures(), System.currentTimeMillis())

    fun mark(streamUrl: String): Set<String> {
        val updated = markDead(failures(), streamUrl, System.currentTimeMillis())
        prefs.edit().putString(KEY_DEAD, encodeDead(updated)).apply()
        return deadUrls(updated, System.currentTimeMillis())
    }

    fun clear(streamUrl: String): Set<String> {
        val updated = unmarkDead(failures(), streamUrl)
        prefs.edit().putString(KEY_DEAD, encodeDead(updated)).apply()
        return deadUrls(updated, System.currentTimeMillis())
    }

    companion object {
        private const val KEY_DEAD = "dead_failures_v2"
    }
}
