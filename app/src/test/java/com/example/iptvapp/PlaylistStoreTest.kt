package com.example.iptvapp

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistStoreTest {

    @Test
    fun `parseIds roundtrips through formatIds`() {
        val ids = listOf(1L, 2L, 9L)
        assertEquals(ids, parseIds(formatIds(ids)))
    }

    @Test
    fun `parseIds handles null and empty`() {
        assertEquals(emptyList<Long>(), parseIds(null))
        assertEquals(emptyList<Long>(), parseIds(""))
    }

    @Test
    fun `parseIds drops junk and blanks keeping valid longs`() {
        assertEquals(listOf(3L, 5L), parseIds("3,x,,5"))
    }

    @Test
    fun `seed name is South Africa for the default URL and generic otherwise`() {
        assertEquals("South Africa", seedPlaylistName(SettingsStore.DEFAULT_PLAYLIST_URL))
        assertEquals("My playlist", seedPlaylistName("https://example.com/tv.m3u"))
    }
}
