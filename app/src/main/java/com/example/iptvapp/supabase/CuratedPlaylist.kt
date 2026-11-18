package com.example.iptvapp.supabase

/**
 * Curated playlist model matching the Supabase table.
 *
 * Deliberately not @Serializable: nothing encodes or decodes this type, and the
 * annotation would reject `metadata: Map<String, Any>` -- kotlinx.serialization
 * has no serializer for Any. Rows are parsed by hand in
 * CuratedPlaylistRepository, which is also where the snake_case column names
 * are mapped.
 */
data class CuratedPlaylist(
    val id: String,
    val name: String,
    val description: String?,
    val m3uUrl: String,
    val logoUrl: String?,
    val category: PlaylistCategory,
    val countryCode: String?,
    val isActive: Boolean,
    val sortOrder: Int,
    val metadata: Map<String, Any>,
    val createdAt: String,
    val updatedAt: String
) {
    /**
     * Category for curated playlists.
     *
     * Nested rather than top-level: callers already write
     * `CuratedPlaylist.PlaylistCategory`, and the category only means anything
     * in the context of a playlist.
     */
    enum class PlaylistCategory {
        COUNTRY, FAST, SPORTS, NEWS, ENTERTAINMENT, KIDS, MUSIC, OTHER
    }

    companion object {
        // Sample playlist for first launch if Supabase is unavailable
        val DEFAULT = CuratedPlaylist(
            id = "default-sa",
            name = "South Africa (iptv-org)",
            description = "Official iptv-org South Africa playlist",
            m3uUrl = "https://iptv-org.github.io/iptv/countries/za.m3u",
            logoUrl = null,
            category = PlaylistCategory.COUNTRY,
            countryCode = "ZA",
            isActive = true,
            sortOrder = 0,
            metadata = emptyMap(),
            createdAt = "",
            updatedAt = ""
        )
    }
}
