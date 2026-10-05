package com.example.iptvapp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsUrlTest {

    @Test
    fun `valid http url`() {
        assertTrue(isValidPlaylistUrl("http://example.com/playlist.m3u"))
    }

    @Test
    fun `valid https url with surrounding whitespace`() {
        assertTrue(isValidPlaylistUrl("  https://iptv-org.github.io/iptv/countries/za.m3u  "))
    }

    @Test
    fun `blank url is invalid`() {
        assertFalse(isValidPlaylistUrl(""))
        assertFalse(isValidPlaylistUrl("   "))
    }

    @Test
    fun `non-http scheme is invalid`() {
        assertFalse(isValidPlaylistUrl("ftp://x"))
    }

    @Test
    fun `missing scheme is invalid`() {
        assertFalse(isValidPlaylistUrl("iptv-org.github.io/iptv/index.m3u"))
    }

    @Test
    fun `spaces inside the url are invalid`() {
        assertFalse(isValidPlaylistUrl("http://exa mple.com/list.m3u"))
    }

    @Test
    fun `missing host is invalid`() {
        assertFalse(isValidPlaylistUrl("http:///just/a/path"))
    }
}
