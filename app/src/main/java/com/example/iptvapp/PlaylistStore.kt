package com.example.iptvapp

import android.content.Context
import android.content.SharedPreferences

/** One saved playlist: a user-given name plus its M3U URL. */
data class SavedPlaylist(
    val id: Long,
    val name: String,
    val url: String
)

/**
 * Sentinel the settings draft uses for "the active playlist after save is
 * the one being added" — never a real registry id.
 */
const val NEW_PLAYLIST_ID = -1L

/**
 * The playlist registry. Like favourites/recents/dead, deliberately in
 * SharedPreferences, NOT in the Room cache — the channel table is a
 * re-fetchable network cache with fallbackToDestructiveMigration and must
 * never hold user data. The Room cache only ever holds the ACTIVE
 * playlist's channels; switching playlists re-fetches and swaps it.
 *
 * Storage shape: a comma-joined list of ids under one key, with each
 * playlist's name/url under per-id keys — names and URLs never need
 * delimiter escaping.
 */
class PlaylistStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("tribal_tv_playlists", Context.MODE_PRIVATE)

    /**
     * First-run migration from the old single-URL world: seeds the
     * registry with the URL that used to live alone in SettingsStore.
     * Idempotent — a no-op once any playlist exists. Returns the active
     * playlist either way.
     */
    fun ensureSeeded(fallbackUrl: String): SavedPlaylist {
        if (playlists().isEmpty()) {
            val seeded = add(seedPlaylistName(fallbackUrl), fallbackUrl)
            prefs.edit().putLong(KEY_ACTIVE, seeded.id).apply()
        }
        return activePlaylist()
    }

    fun playlists(): List<SavedPlaylist> =
        parseIds(prefs.getString(KEY_IDS, null)).mapNotNull { id ->
            val name = prefs.getString(nameKey(id), null) ?: return@mapNotNull null
            val url = prefs.getString(urlKey(id), null) ?: return@mapNotNull null
            SavedPlaylist(id, name, url)
        }

    fun add(name: String, url: String): SavedPlaylist {
        val id = (ids().maxOrNull() ?: 0L) + 1L
        prefs.edit()
            .putString(KEY_IDS, formatIds(ids() + id))
            .putString(nameKey(id), name)
            .putString(urlKey(id), url)
            .apply()
        return SavedPlaylist(id, name, url)
    }

    /**
     * Removing the active playlist is refused by the settings dialog
     * before a draft can ever reach here.
     */
    fun remove(id: Long) {
        prefs.edit()
            .putString(KEY_IDS, formatIds(ids().filterNot { it == id }))
            .remove(nameKey(id))
            .remove(urlKey(id))
            .apply()
    }

    /** Renaming never touches the URL or the active flag — no fetch needed. */
    fun rename(id: Long, name: String) {
        prefs.edit().putString(nameKey(id), name).apply()
    }

    fun activePlaylist(): SavedPlaylist {
        val activeId = prefs.getLong(KEY_ACTIVE, NEW_PLAYLIST_ID)
        val all = playlists()
        return all.firstOrNull { it.id == activeId }
            ?: all.firstOrNull()
            // Unreachable while ensureSeeded is called at startup; keeps
            // the app loadable even with an empty registry.
            ?: SavedPlaylist(NEW_PLAYLIST_ID, "South Africa", SettingsStore.DEFAULT_PLAYLIST_URL)
    }

    fun setActive(id: Long) {
        prefs.edit().putLong(KEY_ACTIVE, id).apply()
    }

    private fun ids(): List<Long> = parseIds(prefs.getString(KEY_IDS, null))

    private fun nameKey(id: Long) = "${KEY_PREFIX_NAME}$id"

    private fun urlKey(id: Long) = "${KEY_PREFIX_URL}$id"

    companion object {
        private const val KEY_IDS = "playlist_ids"
        private const val KEY_ACTIVE = "active_playlist_id"
        private const val KEY_PREFIX_NAME = "playlist_name_"
        private const val KEY_PREFIX_URL = "playlist_url_"
    }
}

// Pure helpers below — JVM unit-testable (PlaylistStoreTest).

fun parseIds(joined: String?): List<Long> =
    joined?.split(",")?.mapNotNull { it.toLongOrNull() } ?: emptyList()

fun formatIds(ids: List<Long>): String = ids.joinToString(",")

/** Seed-name for the migrated legacy URL: the default playlist is the SA one. */
fun seedPlaylistName(url: String): String =
    if (url == SettingsStore.DEFAULT_PLAYLIST_URL) "South Africa" else "My playlist"
