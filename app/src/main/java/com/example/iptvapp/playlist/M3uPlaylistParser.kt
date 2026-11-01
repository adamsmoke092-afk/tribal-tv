package com.example.iptvapp.playlist

/**
 * Parses standard extended M3U playlists (#EXTM3U / #EXTINF), per SPEC §2.1.
 * Pulls tvg-logo, group-title, and tvg-id attributes off the #EXTINF line;
 * the display name is everything after the last comma on that line; the
 * following non-comment line is the stream URL.
 *
 * Playlist-level EPG metadata is read from the #EXTM3U header only, via
 * `url-tvg` and `x-tvg-url` — never from a channel line, so a playlist that
 * happens to put those names on an #EXTINF cannot poison the metadata.
 *
 * Deliberately tolerant of malformed entries (SPEC §4 notes playlist
 * metadata can't always be trusted) — a channel with no name still gets
 * a URL, a stray URL with no preceding #EXTINF is skipped rather than
 * crashing the parse.
 */
object M3uPlaylistParser {

    private val TVG_LOGO_REGEX = Regex("""tvg-logo="([^"]*)"""")
    private val GROUP_TITLE_REGEX = Regex("""group-title="([^"]*)"""")
    private val TVG_ID_REGEX = Regex("""tvg-id="([^"]*)"""")

    // Maps the #EXTVLCOPT option name (lowercase) to the canonical HTTP
    // request header the stream server actually expects. Only the options
    // that affect reachability are mapped; playback-only VLC options
    // (deinterlace, etc.) are irrelevant to an HTTP fetch and ignored.
    private val VLCOPT_HEADER_NAMES = mapOf(
        "http-user-agent" to "User-Agent",
        "http-referrer" to "Referer",
        "http-origin" to "Origin"
    )

    fun parse(playlistText: String): List<M3uChannel> = parsePlaylist(playlistText).channels

    /**
     * Parses a playlist into its channels plus its own declared EPG sources.
     *
     * Both `url-tvg` and `x-tvg-url` are honoured (different providers use
     * different names, some use both). When BOTH appear, the `url-tvg` entries
     * come first and the `x-tvg-url` entries follow — deterministic order, with
     * duplicates across the two removed. Neither is reordered against itself.
     */
    fun parsePlaylist(playlistText: String): M3uPlaylist {
        // A UTF-8 BOM precedes the first directive on some feeds and would
        // otherwise stop "#EXTM3U" from being recognised at all.
        val text = playlistText.removePrefix("\uFEFF")
        val lines = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val channels = mutableListOf<M3uChannel>()
        val headerUrls = LinkedHashSet<String>()
        var headerSeen = false

        var pendingLogo: String? = null
        var pendingGroup: String? = null
        var pendingTvgId: String? = null
        var pendingName: String? = null
        var pendingHeaders = mutableMapOf<String, String>()

        for (line in lines) {
            when {
                line.startsWith("#EXTM3U") -> {
                    // Only the FIRST header line carries playlist metadata;
                    // the same attribute names on a channel line are channel
                    // attributes and are never read here.
                    if (!headerSeen) {
                        headerSeen = true
                        headerUrls += splitGuideUrls(playlistAttributeValue(line, "url-tvg"))
                        headerUrls += splitGuideUrls(playlistAttributeValue(line, "x-tvg-url"))
                    }
                }

                line.startsWith("#EXTINF") -> {
                    pendingLogo = TVG_LOGO_REGEX.find(line)?.groupValues?.get(1)
                    pendingGroup = GROUP_TITLE_REGEX.find(line)?.groupValues?.get(1)
                    pendingTvgId = TVG_ID_REGEX.find(line)?.groupValues?.get(1)
                    pendingName = line.substringAfterLast(',').trim().ifEmpty { "Unknown channel" }
                }

                line.startsWith("#EXTVLCOPT") -> {
                    // Form: #EXTVLCOPT:http-user-agent=Mozilla/5.0 ...
                    // Only the first '=' splits option name from value; the
                    // value itself may contain '='. Surrounding quotes (some
                    // playlists wrap the UA) are stripped.
                    val opt = line.substringAfter(':', "").trim()
                    val name = opt.substringBefore('=', "").trim().lowercase()
                    val rawValue = opt.substringAfter('=', "").trim().trim('"')
                    val headerName = VLCOPT_HEADER_NAMES[name]
                    if (headerName != null && rawValue.isNotEmpty()) {
                        pendingHeaders[headerName] = rawValue
                    }
                }

                line.startsWith("#") -> {
                    // Other directives (#EXTGRP, comments, etc.) — ignored.
                }

                else -> {
                    // A non-comment line is a stream URL. Only attach it if it followed
                    // a real #EXTINF — a stray URL with no metadata is dropped, not
                    // silently merged into whatever channel came before it.
                    val name = pendingName
                    if (name != null) {
                        channels += M3uChannel(
                            name = name,
                            streamUrl = line,
                            logoUrl = pendingLogo,
                            groupTitle = pendingGroup,
                            tvgId = pendingTvgId,
                            headers = pendingHeaders.toMap()
                        )
                    }
                    pendingLogo = null
                    pendingGroup = null
                    pendingTvgId = null
                    pendingName = null
                    pendingHeaders = mutableMapOf()
                }
            }
        }

        return M3uPlaylist(channels = channels, epgUrls = headerUrls.toList())
    }

    /**
     * Reads one attribute off a directive line. Quoted form first (the normal
     * case); a bare token as the fallback, so `url-tvg=http://x` still parses.
     * Surrounding whitespace is trimmed on the value.
     */
    fun playlistAttributeValue(line: String, name: String): String? {
        val quoted = Regex("""$name\s*=\s*"([^"]*)"""").find(line)?.groupValues?.get(1)
        if (quoted != null) return quoted.trim()
        return Regex("""$name\s*=\s*([^\s"]+)""").find(line)?.groupValues?.get(1)?.trim()
    }
}

/**
 * A parsed playlist: its channels plus any EPG sources the playlist itself
 * declares in its header (url-tvg / x-tvg-url).
 */
data class M3uPlaylist(
    val channels: List<M3uChannel>,
    val epgUrls: List<String>
)

/**
 * Splits a header attribute value into guide URLs: comma-separated, trimmed,
 * blanks dropped. Null/blank input yields an empty list. Pure, unit-testable.
 */
fun splitGuideUrls(raw: String?): List<String> =
    raw?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
