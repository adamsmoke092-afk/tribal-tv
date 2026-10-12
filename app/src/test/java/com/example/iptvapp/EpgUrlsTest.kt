package com.example.iptvapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpgUrlsTest {

    @Test
    fun `single URL parses to a one-element list`() {
        assertEquals(
            listOf("https://iptv-org.github.io/epg/guides/za.xml.gz"),
            parseEpgUrls("https://iptv-org.github.io/epg/guides/za.xml.gz")
        )
    }

    @Test
    fun `comma-separated URLs split and trim`() {
        assertEquals(
            listOf(
                "https://a.example.com/guide1.xml.gz",
                "https://b.example.com/guide2.xml"
            ),
            parseEpgUrls(" https://a.example.com/guide1.xml.gz , https://b.example.com/guide2.xml ")
        )
    }

    @Test
    fun `blank segments are dropped`() {
        assertEquals(
            listOf("https://a.example.com/g.xml"),
            parseEpgUrls("https://a.example.com/g.xml,, , ")
        )
    }

    @Test
    fun `empty input yields an empty list`() {
        assertTrue(parseEpgUrls("").isEmpty())
        assertTrue(parseEpgUrls("   ").isEmpty())
        assertTrue(parseEpgUrls(",,").isEmpty())
    }

    @Test
    fun `a single invalid segment makes the whole field invalid for the dialog check`() {
        // Mirrors the dialog validation: parseEpgUrls(...).any { !isValidPlaylistUrl(it) }
        val field = "https://good.example.com/a.xml.gz, notaurl"
        val anyInvalid = parseEpgUrls(field).any { !isValidPlaylistUrl(it) }
        assertTrue(anyInvalid)
    }
}
