package com.example.iptvapp

import android.content.Context
import android.content.SharedPreferences
import com.example.iptvapp.playlist.M3uChannel

/** Name of the synthetic group pinned to the top of the list. */
const val FAVOURITES_GROUP = "Favourites"

/**
 * Favourites, stored as a set of stream URLs in SharedPreferences —
 * deliberately NOT in the Room cache. The channel table is a re-fetchable
 * network cache with fallbackToDestructiveMigration, so a schema bump or a
 * playlist refresh could wipe it; user data must outlive that.
 */
class FavouritesStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("tribal_tv_favourites", Context.MODE_PRIVATE)

    fun favourites(): Set<String> =
        prefs.getStringSet(KEY_FAVOURITES, emptySet())?.toSet() ?: emptySet()

    fun isFavourite(streamUrl: String): Boolean = streamUrl in favourites()

    /** Flips one URL and returns the resulting set, for direct UI state use. */
    fun toggle(streamUrl: String): Set<String> {
        val current = favourites().toMutableSet()
        if (!current.add(streamUrl)) current.remove(streamUrl) // add() false => was already present
        prefs.edit().putStringSet(KEY_FAVOURITES, current).apply()
        return current
    }

    companion object {
        private const val KEY_FAVOURITES = "favourite_urls"
    }
}

/**
 * Prepends a synthetic "Favourites" group holding every favourited channel
 * currently visible (filtered/search results), de-duplicated by stream URL.
 * The real groups are returned untouched below it, so a favourite also
 * still appears under its own category.
 *
 * Returns the input unchanged when there is nothing to pin.
 */
fun withFavouritesGroupFirst(
    groups: List<Pair<String, List<M3uChannel>>>,
    favourites: Set<String>
): List<Pair<String, List<M3uChannel>>> {
    if (favourites.isEmpty()) return groups
    val pinned = groups
        .flatMap { it.second }
        .filter { it.streamUrl in favourites }
        .distinctBy { it.streamUrl }
    if (pinned.isEmpty()) return groups
    return listOf(FAVOURITES_GROUP to pinned) + groups
}