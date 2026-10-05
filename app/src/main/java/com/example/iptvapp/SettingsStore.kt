package com.example.iptvapp

import android.content.Context
import android.content.SharedPreferences
import java.net.URI

/**
 * App settings over SharedPreferences (spec: no DataStore, no new deps).
 * Two keys: playlist_url (falls back to DEFAULT_PLAYLIST_URL) and
 * show_logos (default true; consumed by the channel-logo feature).
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("tribal_tv_settings", Context.MODE_PRIVATE)

    fun playlistUrl(): String =
        prefs.getString(KEY_PLAYLIST_URL, DEFAULT_PLAYLIST_URL) ?: DEFAULT_PLAYLIST_URL

    fun setPlaylistUrl(url: String) {
        prefs.edit().putString(KEY_PLAYLIST_URL, url).apply()
    }

    fun showLogos(): Boolean = prefs.getBoolean(KEY_SHOW_LOGOS, true)

    fun setShowLogos(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_LOGOS, show).apply()
    }

    companion object {
        const val DEFAULT_PLAYLIST_URL = "https://iptv-org.github.io/iptv/countries/za.m3u"

        private const val KEY_PLAYLIST_URL = "playlist_url"
        private const val KEY_SHOW_LOGOS = "show_logos"
    }
}

/**
 * Minimal playlist-URL sanity check for the settings dialog: http(s)
 * scheme and a non-blank host. Pure java.net, so it's JVM unit-testable.
 * Deliberately shallow — the real proof is fetching the playlist on Save,
 * which happens before anything is persisted.
 */
fun isValidPlaylistUrl(url: String): Boolean {
    val trimmed = url.trim()
    if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return false
    return try {
        val host = URI(trimmed).host
        host != null && host.isNotBlank()
    } catch (e: Exception) {
        false
    }
}
