package com.example.iptvapp

import com.example.iptvapp.playlist.M3uPlaylistParser
import com.example.iptvapp.playlist.splitGuideUrls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Playlist-level EPG metadata: url-tvg / x-tvg-url from the #EXTM3U header.
 * These pin the parsing rules agreed for the per-playlist EPG work.
 */
class M3uHeaderTest {

    @Test
    fun `url-tvg is read from the header`() {
        val playlist = M3uPlaylistParser.parsePlaylist(
            "#EXTM3U url-tvg=\"https://g.example/za.xml.gz\"\n" +
                "#EXTINF:-1 tvg-id=\"sabc.1\",SABC 1\n" +
                "http://stream.example/sabc1"
        )
        assertEquals(listOf("https://g.example/za.xml.gz"), playlist.epgUrls)
        assertEquals(1, playlist.channels.size)
        assertEquals("sabc.1", playlist.channels[0].tvgId)
    }

    @Test
    fun `x-tvg-url is read as well`() {
        val playlist = M3uPlaylistParser.parsePlaylist(
            "#EXTM3U x-tvg-url=\"https://g.example/za.xml.gz\"\n" +
                "#EXTINF:-1 tvg-id=\"sabc.1\",SABC 1\n" +
                "http://stream.example/sabc1"
        )
        assertEquals(listOf("https://g.example/za.xml.gz"), playlist.epgUrls)
    }

    @Test
    fun `comma separated guide urls split and are trimmed`() {
        val playlist = M3uPlaylistParser.parsePlaylist(
            "#EXTM3U url-tvg=\"https://a.example/g.xml.gz , https://b.example/g.xml\"\n" +
                "#EXTINF:-1 tvg-id=\"c\",C\nhttp://s"
        )
        assertEquals(
            listOf("https://a.example/g.xml.gz", "https://b.example/g.xml"),
            playlist.epgUrls
        )
    }

    @Test
    fun `when both attributes are present url-tvg comes first then x-tvg-url`() {
        val playlist = M3uPlaylistParser.parsePlaylist(
            "#EXTM3U x-tvg-url=\"https://x.example/g.xml\" url-tvg=\"https://u.example/g.xml\"\n" +
                "#EXTINF:-1 tvg-id=\"c\",C\nhttp://s"
        )
        assertEquals(
            listOf("https://u.example/g.xml", "https://x.example/g.xml"),
            playlist.epgUrls
        )
    }

    @Test
    fun `a duplicate url across the two attributes is not stored twice`() {
        val playlist = M3uPlaylistParser.parsePlaylist(
            "#EXTM3U url-tvg=\"https://same.example/g.xml\" x-tvg-url=\"https://same.example/g.xml\"\n" +
                "#EXTINF:-1 tvg-id=\"c\",C\nhttp://s"
        )
        assertEquals(listOf("https://same.example/g.xml"), playlist.epgUrls)
    }

    @Test
    fun `a utf-8 bom before the header is tolerated`() {
        val playlist = M3uPlaylistParser.parsePlaylist(
            "\uFEFF#EXTM3U url-tvg=\"https://g.example/za.xml.gz\"\n" +
                "#EXTINF:-1 tvg-id=\"sabc.1\",SABC 1\n" +
                "http://stream.example/sabc1"
        )
        assertEquals(listOf("https://g.example/za.xml.gz"), playlist.epgUrls)
        assertEquals(1, playlist.channels.size)
    }

    @Test
    fun `unquoted attribute values are supported`() {
        val playlist = M3uPlaylistParser.parsePlaylist(
            "#EXTM3U url-tvg=https://g.example/za.xml.gz\n" +
                "#EXTINF:-1 tvg-id=\"c\",C\nhttp://s"
        )
        assertEquals(listOf("https://g.example/za.xml.gz"), playlist.epgUrls)
    }

    @Test
    fun `whitespace around the equals sign is tolerated`() {
        val playlist = M3uPlaylistParser.parsePlaylist(
            "#EXTM3U url-tvg = \"https://g.example/za.xml.gz\"\n" +
                "#EXTINF:-1 tvg-id=\"c\",C\nhttp://s"
        )
        assertEquals(listOf("https://g.example/za.xml.gz"), playlist.epgUrls)
    }

    @Test
    fun `the same attribute on a channel line is never playlist metadata`() {
        // An #EXTINF that carries url-tvg must not leak into the playlist's
        // EPG sources; and it must not disturb the channel's own parsing.
        val playlist = M3uPlaylistParser.parsePlaylist(
            "#EXTM3U\n" +
                "#EXTINF:-1 tvg-id=\"c\" url-tvg=\"https://evil.example/g.xml\",C\nhttp://s"
        )
        assertTrue(playlist.epgUrls.isEmpty())
        assertEquals(1, playlist.channels.size)
        assertEquals("c", playlist.channels[0].tvgId)
    }

    @Test
    fun `a second header line adds nothing`() {
        val playlist = M3uPlaylistParser.parsePlaylist(
            "#EXTM3U url-tvg=\"https://first.example/g.xml\"\n" +
                "#EXTM3U url-tvg=\"https://second.example/g.xml\"\n" +
                "#EXTINF:-1 tvg-id=\"c\",C\nhttp://s"
        )
        assertEquals(listOf("https://first.example/g.xml"), playlist.epgUrls)
    }

    @Test
    fun `a playlist without epg metadata parses with an empty list`() {
        val playlist = M3uPlaylistParser.parsePlaylist(
            "#EXTM3U\n#EXTINF:-1 tvg-id=\"c\",C\nhttp://s"
        )
        assertTrue(playlist.epgUrls.isEmpty())
        assertEquals(1, playlist.channels.size)
    }

    @Test
    fun `attribute reader prefers the quoted value and trims it`() {
        assertEquals(
            "https://g.example/g.xml",
            M3uPlaylistParser.playlistAttributeValue("#EXTM3U url-tvg=\" https://g.example/g.xml \"", "url-tvg")
        )
        assertNull(M3uPlaylistParser.playlistAttributeValue("#EXTM3U", "url-tvg"))
    }

    @Test
    fun `guide url splitting drops blanks`() {
        assertEquals(listOf("a", "b"), splitGuideUrls(" a ,, b, "))
        assertTrue(splitGuideUrls(null).isEmpty())
        assertTrue(splitGuideUrls("  , , ").isEmpty())
    }
}
