package com.example.iptvapp.supabase

import com.example.iptvapp.SavedPlaylist
import com.example.iptvapp.NEW_PLAYLIST_ID

/**
 * Unified playlist combining curated (from Supabase) and local (user-added) playlists.
 */
data class UnifiedPlaylist(
    val id: String,
    val name: String,
    val url: String,
    val logoUrl: String?,
    val category: CuratedPlaylist.PlaylistCategory,
    val countryCode: String?,
    val isCurated: Boolean,
    val sortOrder: Int,
    val metadata: Map<String, Any>
) {
    /**
     * Creates a UnifiedPlaylist from a local SavedPlaylist.
     */
    companion object {
        fun fromLocal(playlist: SavedPlaylist): UnifiedPlaylist {
            return UnifiedPlaylist(
                id = "local:${playlist.id}",
                name = playlist.name,
                url = playlist.url,
                logoUrl = null,
                category = CuratedPlaylist.PlaylistCategory.OTHER,
                countryCode = null,
                isCurated = false,
                sortOrder = 0,
                metadata = emptyMap()
            )
        }
    }
}

/**
 * Merges curated and local playlists into a single sorted list.
 * Curated playlists come first (by sort_order), then local playlists.
 */
fun mergePlaylists(
    curated: List<CuratedPlaylist>,
    local: List<SavedPlaylist>
): List<UnifiedPlaylist> {
    val unified = mutableListOf<UnifiedPlaylist>()

    // Add curated playlists (already sorted by sort_order from Supabase)
    for (playlist in curated) {
        unified.add(UnifiedPlaylist(
            id = "curated:${playlist.id}",
            name = playlist.name,
            url = playlist.m3uUrl,
            logoUrl = playlist.logoUrl,
            category = playlist.category,
            countryCode = playlist.countryCode,
            isCurated = true,
            sortOrder = playlist.sortOrder,
            metadata = playlist.metadata
        ))
    }

    // Add local playlists
    for (playlist in local) {
        unified.add(UnifiedPlaylist.fromLocal(playlist))
    }

    return unified
}

/**
 * Extracts the Supabase UUID from a unified playlist ID.
 * Returns null for local playlists.
 */
fun extractCuratedId(unifiedId: String): String? {
    return if (unifiedId.startsWith("curated:")) {
        unifiedId.substringAfter("curated:")
    } else null
}

/**
 * Extracts the local ID from a unified playlist ID.
 * Returns null for curated playlists.
 */
fun extractLocalId(unifiedId: String): Long? {
    return if (unifiedId.startsWith("local:")) {
        unifiedId.substringAfter("local:").toLongOrNull()
    } else null
}