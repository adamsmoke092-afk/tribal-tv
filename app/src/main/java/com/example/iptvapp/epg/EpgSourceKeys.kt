package com.example.iptvapp.epg

import java.security.MessageDigest

/**
 * Stable, PLAYLIST-SPECIFIC, non-reversible identifiers for cached EPG rows.
 *
 * M3U tvg-ids are a per-playlist namespace, not a global one: two playlists
 * can both say tvg-id="Channel.1" and mean different channels. A cached row
 * therefore must never be identified by its guide URL alone — two playlists
 * using the same guide would silently exchange channel mappings.
 *
 * The key is `SHA-256("<playlistId>\n<guideUrl>")` as lowercase hex. The
 * newline matters: it is the unambiguous boundary between the two parts (a URL
 * cannot contain a raw newline, and a playlist id is numeric), so
 * ("1", "23|x") and ("12", "3|x") cannot collide.
 *
 * The digest is deliberately irreversible. A guide URL can carry credentials
 * or tokens, so it is never stored as, or recoverable from, the identifier;
 * diagnostics always display the REDACTED url (see redactEpgUrl) instead.
 *
 * Consequences worth knowing:
 *  - Same playlist + same guide -> same key, so a refresh replaces exactly its
 *    own rows and nothing else's.
 *  - Different playlist, same guide -> different key; rows are never shared.
 *  - Deleting a playlist and re-adding it gets a new registry id, so its old
 *    rows are orphaned. They are reclaimed by the 48h expiry pass and are
 *    unreadable meanwhile, because every query is scoped to the keys configured
 *    for the active playlist.
 */

/**
 * Key under which one guide's rows are cached for one playlist: the SHA-256
 * digest of the playlist id and the full guide URL.
 */
fun epgSourceKey(playlistKey: String, guideUrl: String): String =
    sha256Hex("${playlistKey.trim()}\n${guideUrl.trim()}")

/** Keys for every guide configured on one playlist, in the same order. */
fun epgSourceKeys(playlistKey: String, guideUrls: Collection<String>): List<String> =
    guideUrls.map { epgSourceKey(playlistKey, it) }

/** Lowercase hex SHA-256 of the UTF-8 bytes of [input]. */
fun sha256Hex(input: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
    return buildString(bytes.size * 2) {
        for (byte in bytes) {
            val value = byte.toInt() and 0xFF
            append(HEX[value ushr 4])
            append(HEX[value and 0x0F])
        }
    }
}

private const val HEX = "0123456789abcdef"