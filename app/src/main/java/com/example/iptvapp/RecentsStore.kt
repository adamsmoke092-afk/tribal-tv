package com.example.iptvapp

import android.content.Context
import android.content.SharedPreferences
import com.example.iptvapp.playlist.M3uChannel

/** Name of the synthetic recents group in the channel list. */
const val RECENTS_GROUP = "Recently watched"

/** How many recently-watched URLs are kept. */
const val RECENTS_MAX = 20

/**
 * Moves newUrl to the front of the recency list, dropping any earlier
 * occurrence, and bounds the list. Pure — unit-testable.
 */
fun updatedRecents(current: List<String>, newUrl: String, max: Int = RECENTS_MAX): List<String> =
    (listOf(newUrl) + current.filter { it != newUrl }).take(max)

/**
 * Recently-watched stream URLs, most recent first, in SharedPreferences —
 * user data must outlive the Room cache (same reasoning as favourites).
 * Stored as one newline-joined string because SharedPreferences sets lose
 * order; stream URLs never contain newlines.
 */
class RecentsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("tribal_tv_recents", Context.MODE_PRIVATE)

    fun recents(): List<String> =
        prefs.getString(KEY_RECENTS, null)
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    fun add(streamUrl: String) {
        prefs.edit()
            .putString(KEY_RECENTS, updatedRecents(recents(), streamUrl).joinToString("\n"))
            .apply()
    }

    companion object {
        private const val KEY_RECENTS = "recent_urls"
    }
}

/**
 * Inserts a synthetic "Recently watched" group holding the currently
 * visible channels in recency order. It sits just under "Favourites" when
 * that group is present, otherwise at the top. Returns the input
 * unchanged when there's nothing to show.
 */
fun withRecentsGroup(
    groups: List<Pair<String, List<M3uChannel>>>,
    recents: List<String>
): List<Pair<String, List<M3uChannel>>> {
    if (recents.isEmpty()) return groups
    val byUrl = groups.flatMap { it.second }.associateBy { it.streamUrl }
    val recentChannels = recents.mapNotNull { byUrl[it] }
    if (recentChannels.isEmpty()) return groups
    val insertAt = if (groups.firstOrNull()?.first == FAVOURITES_GROUP) 1 else 0
    return groups.subList(0, insertAt) +
        listOf(RECENTS_GROUP to recentChannels) +
        groups.subList(insertAt, groups.size)
}