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
     * LEGACY global manual EPG URL. Read only by the one-time migration in
     * PlaylistStore.migrateLegacyEpgOverride, which copies it onto the active
     * playlist's own manual override. The value is never deleted here, so it
     * survives until a real active playlist exists to receive it.
     */
    fun epgUrl(): String = prefs.getString(KEY_EPG_URL, "") ?: ""

    /**
     * Loose quality-alias matching for EPG ids. Default OFF: only exact id
     * equality matches. The rules live in epg/EpgQualityMatch.kt.
     */
    fun epgLooseMatching(): Boolean = prefs.getBoolean(KEY_EPG_LOOSE_MATCHING, false)

    fun setEpgLooseMatching(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EPG_LOOSE_MATCHING, enabled).apply()
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
        private const val KEY_EPG_LOOSE_MATCHING = "epg_loose_matching"
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
 * REMOVED (2026-10-31): the iptv-org country auto-pair
 * (`.../iptv/countries/za.m3u` -> `.../epg/guides/za.xml.gz`) is gone by
 * decision of the user — its generated guide URLs proved unreliable, and
 * guessing a guide for a playlist from its URL shape is exactly the kind of
 * silent behaviour the per-playlist EPG work removes. A playlist now gets its
 * guide from its own `url-tvg`/`x-tvg-url` header, or from the user's manual
 * override for that playlist. Nothing else.
 */

/**
 * Splits the EPG setting into individual guide URLs: comma-separated,
 * trimmed, blanks dropped. The raw string stays stored as typed (one
 * preference value); only consumers split it. "" -> empty list (EPG off
 * via manual). Pure java.lang — JVM unit-testable.
 */
fun parseEpgUrls(raw: String): List<String> =
    raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }

/** Where the EPG URLs currently in force came from. */
enum class EpgSourceOrigin { MANUAL, PLAYLIST_HEADER, NONE }

/**
 * Which EPG source is in force for a playlist, by precedence:
 *  1. the playlist's manual override, when the user configured one,
 *  2. otherwise the guide URL the playlist declares in its own header,
 *  3. otherwise none — no guessing, no auto-pairing.
 */
fun epgSourceOrigin(manualOverride: String?, headerUrl: String?): EpgSourceOrigin = when {
    parseEpgUrls(manualOverride.orEmpty()).isNotEmpty() -> EpgSourceOrigin.MANUAL
    parseEpgUrls(headerUrl.orEmpty()).isNotEmpty() -> EpgSourceOrigin.PLAYLIST_HEADER
    else -> EpgSourceOrigin.NONE
}

/**
 * The guide URLs in force for a playlist, by the same precedence.
 *
 * The two sources are NEVER combined. That is deliberate: a manual override
 * that fails must not silently fall back to the playlist's own URL — it fails,
 * its cached rows are preserved, and diagnostics say so. The user decides
 * which source to use by editing the override.
 */
fun effectiveEpgUrlsFor(manualOverride: String?, headerUrl: String?): List<String> {
    val manual = parseEpgUrls(manualOverride.orEmpty())
    if (manual.isNotEmpty()) return manual
    return parseEpgUrls(headerUrl.orEmpty())
}

/**
 * Whether a playlist refresh must FORCE a fresh guide download, decided on the
 * source actually in force — not on the playlist's header alone.
 *
 * A manual override outranks the header, so:
 *  - override in force + header changed -> false. The effective source is
 *    still the same manual URL; re-downloading it would be pure waste.
 *  - no override + header changed -> true. The guide genuinely moved.
 *  - override added, cleared or edited -> true. The effective source changed.
 *  - nothing changed -> false, and the caller falls through to its normal
 *    staleness window rather than bypassing it.
 *
 * Order does not matter: every effective URL is fetched independently and the
 * results are merged, so reordering the same URLs must not trigger a fresh
 * download. Comparison is on normalized, distinct URL sets — adding, removing
 * or changing any source (a string difference) changes the set and forces a
 * refresh. URL casing is preserved: path segments and query values can be
 * case-sensitive, so changing them changes the set. Pure — JVM unit-testable.
 */
fun shouldForceEpgRefresh(
    effectiveBefore: List<String>,
    effectiveAfter: List<String>
): Boolean = normalizedUrlSet(effectiveBefore) != normalizedUrlSet(effectiveAfter)

/** Normalises effective guide URLs to a de-duplicated set so that reordering
 * the same URLs compares equal. Surrounding whitespace is trimmed and blanks
 * are dropped, but URL casing is preserved because path segments and query
 * values can be case-sensitive — changing them changes the set and forces a
 * refresh. Callers already pass trimmed, non-blank lists from
 * [effectiveEpgUrlsFor], but this pure helper tolerates anything. */
internal fun normalizedUrlSet(urls: List<String>): Set<String> =
    urls.map { it.trim() }.filter { it.isNotEmpty() }.toSet()