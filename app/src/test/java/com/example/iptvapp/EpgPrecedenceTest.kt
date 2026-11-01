package com.example.iptvapp

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The precedence rules for per-playlist EPG configuration, and the legacy
 * migration's *decision logic*. The preference-backed half of the migration is
 * covered by PlaylistStoreEpgTest (Robolectric); this file pins the rules
 * themselves so they stay honest without a device.
 */
class EpgPrecedenceTest {

    // ---- precedence: manual wins, never combined ----

    @Test
    fun `a manual override wins over the playlist header`() {
        assertEquals(
            listOf("https://manual.example/g.xml.gz"),
            effectiveEpgUrlsFor("https://manual.example/g.xml.gz", "https://header.example/g.xml.gz")
        )
        assertEquals(
            EpgSourceOrigin.MANUAL,
            epgSourceOrigin("https://manual.example/g.xml.gz", "https://header.example/g.xml.gz")
        )
    }

    @Test
    fun `the playlist header is used when no manual override exists`() {
        assertEquals(
            listOf("https://header.example/g.xml.gz"),
            effectiveEpgUrlsFor("", "https://header.example/g.xml.gz")
        )
        assertEquals(
            EpgSourceOrigin.PLAYLIST_HEADER,
            epgSourceOrigin("", "https://header.example/g.xml.gz")
        )
    }

    @Test
    fun `a blank manual override falls through to the header`() {
        assertEquals(
            listOf("https://header.example/g.xml.gz"),
            effectiveEpgUrlsFor("   ", "https://header.example/g.xml.gz")
        )
    }

    @Test
    fun `no override and no header means EPG is off`() {
        assertEquals(emptyList<String>(), effectiveEpgUrlsFor("", ""))
        assertEquals(EpgSourceOrigin.NONE, epgSourceOrigin(null, null))
    }

    @Test
    fun `manual overrides support several comma separated urls`() {
        assertEquals(
            listOf("https://a.example/g.xml", "https://b.example/g.xml"),
            effectiveEpgUrlsFor("https://a.example/g.xml, https://b.example/g.xml", "https://header.example/g.xml")
        )
    }

    @Test
    fun `the two sources are never merged`() {
        // A manual override pointing at a BROKEN source must not silently be
        // padded out with the playlist's own guide: the user chose the source.
        val urls = effectiveEpgUrlsFor("https://broken.example/g.xml", "https://header.example/g.xml")
        assertEquals(listOf("https://broken.example/g.xml"), urls)
        assertEquals(1, urls.size)
    }

    @Test
    fun `a whitespace-only override with no header is still no guide`() {
        assertEquals(emptyList<String>(), effectiveEpgUrlsFor("   ", ""))
    }

    @Test
    fun `origin distinguishes the two configured sources`() {
        assertEquals(EpgSourceOrigin.MANUAL, epgSourceOrigin("https://m", null))
        assertEquals(EpgSourceOrigin.PLAYLIST_HEADER, epgSourceOrigin(null, "https://h"))
        assertEquals(EpgSourceOrigin.NONE, epgSourceOrigin("", ""))
    }
}
