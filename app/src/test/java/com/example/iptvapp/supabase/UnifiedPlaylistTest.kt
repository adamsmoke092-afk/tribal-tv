package com.example.iptvapp.supabase

import com.example.iptvapp.SavedPlaylist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedPlaylistTest {

    @Test
    fun testMergePlaylists() {
        val curated = listOf(
            CuratedPlaylist(
                id = "curated-1",
                name = "SA Sports",
                description = "Sports channels",
                m3uUrl = "https://example.com/sa-sports.m3u",
                logoUrl = "https://example.com/sa-sports.png",
                category = CuratedPlaylist.PlaylistCategory.SPORTS,
                countryCode = "ZA",
                isActive = true,
                sortOrder = 0,
                metadata = emptyMap(),
                createdAt = "",
                updatedAt = ""
            ),
            CuratedPlaylist(
                id = "curated-2",
                name = "FAST Channels",
                description = "Free ad-supported TV",
                m3uUrl = "https://example.com/fast.m3u",
                logoUrl = null,
                category = CuratedPlaylist.PlaylistCategory.FAST,
                countryCode = null,
                isActive = true,
                sortOrder = 1,
                metadata = emptyMap(),
                createdAt = "",
                updatedAt = ""
            )
        )

        val local = listOf(
            SavedPlaylist(1L, "My Playlist", "https://my.example.com/playlist.m3u"),
            SavedPlaylist(2L, "Another Local", "https://another.example.com/list.m3u")
        )

        val unified = mergePlaylists(curated, local)

        assertEquals(4, unified.size)

        // Curated playlists come first, in sort_order
        assertEquals("curated:curated-1", unified[0].id)
        assertEquals("SA Sports", unified[0].name)
        assertTrue(unified[0].isCurated)
        assertEquals(CuratedPlaylist.PlaylistCategory.SPORTS, unified[0].category)
        assertEquals("ZA", unified[0].countryCode)
        assertEquals(0, unified[0].sortOrder)

        assertEquals("curated:curated-2", unified[1].id)
        assertEquals("FAST Channels", unified[1].name)
        assertTrue(unified[1].isCurated)
        assertEquals(CuratedPlaylist.PlaylistCategory.FAST, unified[1].category)
        assertNull(unified[1].countryCode)
        assertEquals(1, unified[1].sortOrder)

        // Local playlists come after
        assertEquals("local:1", unified[2].id)
        assertEquals("My Playlist", unified[2].name)
        assertFalse(unified[2].isCurated)
        assertEquals(CuratedPlaylist.PlaylistCategory.OTHER, unified[2].category)

        assertEquals("local:2", unified[3].id)
        assertEquals("Another Local", unified[3].name)
        assertFalse(unified[3].isCurated)
    }

    @Test
    fun testExtractCuratedId() {
        assertEquals("uuid-123", extractCuratedId("curated:uuid-123"))
        assertNull(extractCuratedId("local:1"))
        assertNull(extractCuratedId("invalid"))
    }

    @Test
    fun testExtractLocalId() {
        assertEquals(42L, extractLocalId("local:42"))
        assertNull(extractLocalId("curated:uuid"))
        assertNull(extractLocalId("invalid"))
    }

    @Test
    fun testFromLocal() {
        val local = SavedPlaylist(99L, "Test Local", "https://test.com/list.m3u")
        val unified = UnifiedPlaylist.fromLocal(local)

        assertEquals("local:99", unified.id)
        assertEquals("Test Local", unified.name)
        assertEquals("https://test.com/list.m3u", unified.url)
        assertNull(unified.logoUrl)
        assertEquals(CuratedPlaylist.PlaylistCategory.OTHER, unified.category)
        assertNull(unified.countryCode)
        assertFalse(unified.isCurated)
        assertEquals(0, unified.sortOrder)
    }
}