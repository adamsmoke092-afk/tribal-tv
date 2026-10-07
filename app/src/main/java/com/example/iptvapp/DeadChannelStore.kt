package com.example.iptvapp

import android.content.Context
import android.content.SharedPreferences

/** Pure: adds a URL to the dead set. */
fun markDead(current: Set<String>, streamUrl: String): Set<String> = current + streamUrl

/** Pure: removes a URL — a channel that plays again is forgiven. */
fun unmarkDead(current: Set<String>, streamUrl: String): Set<String> = current - streamUrl

/**
 * Stream URLs that failed to play, in SharedPreferences — user data must
 * outlive the Room cache (same reasoning as favourites/recents). A channel
 * that later plays successfully is un-marked, so the memory self-heals
 * when a "dead" link comes back.
 */
class DeadChannelStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("tribal_tv_dead", Context.MODE_PRIVATE)

    fun dead(): Set<String> =
        prefs.getStringSet(KEY_DEAD, emptySet())?.toSet() ?: emptySet()

    fun mark(streamUrl: String): Set<String> {
        val updated = markDead(dead(), streamUrl)
        prefs.edit().putStringSet(KEY_DEAD, updated).apply()
        return updated
    }

    fun clear(streamUrl: String): Set<String> {
        val updated = unmarkDead(dead(), streamUrl)
        prefs.edit().putStringSet(KEY_DEAD, updated).apply()
        return updated
    }

    companion object {
        private const val KEY_DEAD = "dead_urls"
    }
}