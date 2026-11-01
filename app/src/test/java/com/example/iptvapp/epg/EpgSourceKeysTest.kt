package com.example.iptvapp.epg

import com.example.iptvapp.epg.epgSourceKey
import com.example.iptvapp.epg.epgSourceKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Canonical URL representation for EPG source keys: trimmed, de-duplicated,
 * case-preserving. Whitespace-only differences must map to the same key so
 * they never create duplicate downloads or duplicate cache rows.
 */
class EpgSourceKeysTest {

    @Test
    fun `source key ignores surrounding whitespace`() {
        assertEquals(
            epgSourceKey("playlist-1", "https://a.example/guide.xml"),
            epgSourceKey("playlist-1", "  https://a.example/guide.xml  ")
        )
        assertEquals(
            epgSourceKey("playlist-1", "https://a.example/guide.xml"),
            epgSourceKey("playlist-1", "\thttps://a.example/guide.xml\n")
        )
    }

    @Test
    fun `whitespace-only variants produce the same source key`() {
        // The same guide URL with different surrounding whitespace must map
        // to one cache entry, not several.
        val urls = listOf(
            "https://a.example/guide.xml",
            "  https://a.example/guide.xml  ",
            " https://a.example/guide.xml "
        )
        val keys = urls.map { epgSourceKey("playlist-1", it) }
        assertEquals(1, keys.toSet().size)
    }

    @Test
    fun `source key preserves URL casing`() {
        // Paths and query values are case-sensitive, so different casing is
        // a different source and must force a refresh.
        assertNotEquals(
            epgSourceKey("playlist-1", "https://a.example/Guide.xml"),
            epgSourceKey("playlist-1", "https://a.example/guide.xml")
        )
        assertNotEquals(
            epgSourceKey("playlist-1", "https://a.example/g.xml?token=Abc123"),
            epgSourceKey("playlist-1", "https://a.example/g.xml?token=abc123")
        )
    }

    @Test
    fun `epgSourceKeys preserves order and maps one-to-one`() {
        val urls = listOf("https://b.example/g.xml", "https://a.example/g.xml")
        val keys = epgSourceKeys("playlist-1", urls)
        assertEquals(2, keys.size)
        assertEquals(epgSourceKey("playlist-1", "https://b.example/g.xml"), keys[0])
        assertEquals(epgSourceKey("playlist-1", "https://a.example/g.xml"), keys[1])
    }

    @Test
    fun `same playlist and guide always yield the same key`() {
        assertEquals(
            epgSourceKey("playlist-1", "https://a.example/g.xml"),
            epgSourceKey("playlist-1", "https://a.example/g.xml")
        )
    }

    @Test
    fun `different playlists using the same guide get different keys`() {
        assertNotEquals(
            epgSourceKey("playlist-1", "https://a.example/g.xml"),
            epgSourceKey("playlist-2", "https://a.example/g.xml")
        )
    }
}
