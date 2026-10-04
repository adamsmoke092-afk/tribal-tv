package com.example.iptvapp.playlist

/**
 * A single channel parsed out of an M3U/M3U8 playlist (SPEC §2.1).
 * `streamUrl` may be an HLS (.m3u8) URL or a direct stream URL — the
 * player layer decides how to handle it, this layer just extracts it.
 */
data class M3uChannel(
    val name: String,
    val streamUrl: String,
    val logoUrl: String? = null,
    val groupTitle: String? = null,
    val tvgId: String? = null
)
