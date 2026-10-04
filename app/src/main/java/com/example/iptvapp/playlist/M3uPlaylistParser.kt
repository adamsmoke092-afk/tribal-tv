package com.example.iptvapp.playlist

/**
 * Parses standard extended M3U playlists (#EXTM3U / #EXTINF), per SPEC §2.1.
 * Pulls tvg-logo, group-title, and tvg-id attributes off the #EXTINF line;
 * the display name is everything after the last comma on that line; the
 * following non-comment line is the stream URL.
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

    fun parse(playlistText: String): List<M3uChannel> {
        val lines = playlistText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val channels = mutableListOf<M3uChannel>()

        var pendingLogo: String? = null
        var pendingGroup: String? = null
        var pendingTvgId: String? = null
        var pendingName: String? = null

        for (line in lines) {
            when {
                line.startsWith("#EXTINF") -> {
                    pendingLogo = TVG_LOGO_REGEX.find(line)?.groupValues?.get(1)
                    pendingGroup = GROUP_TITLE_REGEX.find(line)?.groupValues?.get(1)
                    pendingTvgId = TVG_ID_REGEX.find(line)?.groupValues?.get(1)
                    pendingName = line.substringAfterLast(',').trim().ifEmpty { "Unknown channel" }
                }

                line.startsWith("#") -> {
                    // Other directives (#EXTM3U, #EXTGRP, #EXTVLCOPT, etc.) — ignored for now.
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
                            tvgId = pendingTvgId
                        )
                    }
                    pendingLogo = null
                    pendingGroup = null
                    pendingTvgId = null
                    pendingName = null
                }
            }
        }

        return channels
    }
}
