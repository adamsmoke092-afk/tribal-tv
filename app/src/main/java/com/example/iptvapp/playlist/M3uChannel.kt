package com.example.iptvapp.playlist

/**
 * A single channel parsed out of an M3U/M3U8 playlist (SPEC §2.1).
 * `streamUrl` may be an HLS (.m3u8) URL or a direct stream URL — the
 * player layer decides how to handle it, this layer just extracts it.
 *
 * `headers` are HTTP request headers the stream requires (parsed from
 * #EXTVLCOPT lines, e.g. http-user-agent -> User-Agent, http-referrer ->
 * Referer). Empty for the common case; when present they must be applied
 * to every request for this stream (playlist AND segments) or the server
 * rejects playback. Keyed by canonical HTTP header name.
 */
data class M3uChannel(
    val name: String,
    val streamUrl: String,
    val logoUrl: String? = null,
    val groupTitle: String? = null,
    val tvgId: String? = null,
    val headers: Map<String, String> = emptyMap()
)
