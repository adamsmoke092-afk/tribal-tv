package com.example.iptvapp

import android.content.Context
import android.content.SharedPreferences
import java.net.URI

/**
 * App settings over SharedPreferences (spec: no DataStore, no new deps).
 * Keys: playlist_url (legacy single-playlist URL — read exactly once to
 * seed PlaylistStore on first run, then unused), show_logos, the three
 * list-visibility filters, resume_on_launch, data_saver, audio_only and
 * grid_layout — all default off, so behaviour is unchanged until the
 * user opts in.
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("tribal_tv_settings", Context.MODE_PRIVATE)

    fun playlistUrl(): String =
        prefs.getString(KEY_PLAYLIST_URL, DEFAULT_PLAYLIST_URL) ?: DEFAULT_PLAYLIST_URL

    fun showLogos(): Boolean = prefs.getBoolean(KEY_SHOW_LOGOS, true)

    fun setShowLogos(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_LOGOS, show).apply()
    }

    fun hideGeoBlocked(): Boolean = prefs.getBoolean(KEY_HIDE_GEO_BLOCKED, false)

    fun setHideGeoBlocked(hide: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_GEO_BLOCKED, hide).apply()
    }

    fun hideNot24x7(): Boolean = prefs.getBoolean(KEY_HIDE_NOT_24X7, false)

    fun setHideNot24x7(hide: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_NOT_24X7, hide).apply()
    }

    fun hdOnly(): Boolean = prefs.getBoolean(KEY_HD_ONLY, false)

    fun setHdOnly(hdOnly: Boolean) {
        prefs.edit().putBoolean(KEY_HD_ONLY, hdOnly).apply()
    }

    fun resumeOnLaunch(): Boolean = prefs.getBoolean(KEY_RESUME_ON_LAUNCH, false)

    fun setResumeOnLaunch(resume: Boolean) {
        prefs.edit().putBoolean(KEY_RESUME_ON_LAUNCH, resume).apply()
    }

    fun dataSaver(): Boolean = prefs.getBoolean(KEY_DATA_SAVER, false)

    fun setDataSaver(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DATA_SAVER, enabled).apply()
    }

    fun audioOnly(): Boolean = prefs.getBoolean(KEY_AUDIO_ONLY, false)

    fun setAudioOnly(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUDIO_ONLY, enabled).apply()
    }

    fun gridLayout(): Boolean = prefs.getBoolean(KEY_GRID_LAYOUT, false)

    fun setGridLayout(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GRID_LAYOUT, enabled).apply()
    }

    /** Wall-clock ms of the last successful playlist fetch; 0 = never. */
    fun lastRefreshMs(): Long = prefs.getLong(KEY_LAST_REFRESH_MS, 0L)

    fun setLastRefreshMs(ms: Long) {
        prefs.edit().putLong(KEY_LAST_REFRESH_MS, ms).apply()
    }

    /**
     * XMLTV EPG guide URL. Empty (the default) means EPG is off — no guide
     * is fetched and the player shows no now/next. Opt-in so the common
     * case downloads nothing extra.
     */
    fun epgUrl(): String = prefs.getString(KEY_EPG_URL, "") ?: ""

    fun setEpgUrl(url: String) {
        prefs.edit().putString(KEY_EPG_URL, url).apply()
    }

    /** Wall-clock ms of the last successful EPG fetch; 0 = never. */
    fun lastEpgRefreshMs(): Long = prefs.getLong(KEY_LAST_EPG_REFRESH_MS, 0L)

    fun setLastEpgRefreshMs(ms: Long) {
        prefs.edit().putLong(KEY_LAST_EPG_REFRESH_MS, ms).apply()
    }

    companion object {
        const val DEFAULT_PLAYLIST_URL = "https://iptv-org.github.io/iptv/countries/za.m3u"

        private const val KEY_PLAYLIST_URL = "playlist_url"
        private const val KEY_SHOW_LOGOS = "show_logos"
        private const val KEY_HIDE_GEO_BLOCKED = "hide_geo_blocked"
        private const val KEY_HIDE_NOT_24X7 = "hide_not_24x7"
        private const val KEY_HD_ONLY = "hd_only"
        private const val KEY_RESUME_ON_LAUNCH = "resume_on_launch"
        private const val KEY_DATA_SAVER = "data_saver"
        private const val KEY_AUDIO_ONLY = "audio_only"
        private const val KEY_GRID_LAYOUT = "grid_layout"
        private const val KEY_LAST_REFRESH_MS = "last_refresh_ms"
        private const val KEY_EPG_URL = "epg_url"
        private const val KEY_LAST_EPG_REFRESH_MS = "last_epg_refresh_ms"
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

/**
 * Auto-pairs an iptv-org COUNTRY playlist with its matching per-country
 * XMLTV guide (.../iptv/countries/uk.m3u -> .../epg/guides/uk.xml.gz) —
 * the one pairing where playlist tvg-ids and guide channel ids are
 * guaranteed to share a namespace. Any other URL returns null (no
 * auto-guide); the user's manual EPG setting always overrides this.
 *
 * The country code is validated as plain ASCII letters so junk paths can
 * never be spliced into the guide URL. Pure java.lang — JVM unit-testable.
 */
fun autoEpgUrlForPlaylist(playlistUrl: String): String? {
    val trimmed = playlistUrl.trim()
    if (!trimmed.startsWith(IPTV_ORG_PLAYLIST_PREFIX)) return null
    val file = trimmed.removePrefix(IPTV_ORG_PLAYLIST_PREFIX).substringBefore('?')
    if (!file.endsWith(".m3u")) return null
    val code = file.removeSuffix(".m3u")
    if (code.isEmpty() || !code.all { it.isLetter() && it.code < 128 }) return null
    return "https://iptv-org.github.io/epg/guides/${code.lowercase()}.xml.gz"
}

private const val IPTV_ORG_PLAYLIST_PREFIX = "https://iptv-org.github.io/iptv/countries/"