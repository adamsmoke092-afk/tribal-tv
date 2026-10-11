package com.example.iptvapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoEpgUrlTest {

    @Test
    fun `za country playlist pairs with the za guide`() {
        assertEquals(
            "https://iptv-org.github.io/epg/guides/za.xml.gz",
            autoEpgUrlForPlaylist("https://iptv-org.github.io/iptv/countries/za.m3u")
        )
    }

    @Test
    fun `any country code pairs with its own guide`() {
        assertEquals(
            "https://iptv-org.github.io/epg/guides/uk.xml.gz",
            autoEpgUrlForPlaylist("https://iptv-org.github.io/iptv/countries/uk.m3u")
        )
        assertEquals(
            "https://iptv-org.github.io/epg/guides/us.xml.gz",
            autoEpgUrlForPlaylist("https://iptv-org.github.io/iptv/countries/us.m3u")
        )
    }

    @Test
    fun `trailing query string is ignored`() {
        assertEquals(
            "https://iptv-org.github.io/epg/guides/za.xml.gz",
            autoEpgUrlForPlaylist("https://iptv-org.github.io/iptv/countries/za.m3u?v=2")
        )
    }

    @Test
    fun `non-iptv-org playlist gets no auto guide`() {
        assertNull(autoEpgUrlForPlaylist("http://myprovider.example.com/list.m3u"))
        assertNull(autoEpgUrlForPlaylist("https://example.com/iptv/countries/uk.m3u"))
    }

    @Test
    fun `iptv-org non-country playlist gets no auto guide`() {
        // Categories/languages don't pair with a per-country guide.
        assertNull(autoEpgUrlForPlaylist("https://iptv-org.github.io/iptv/categories/movies.m3u"))
        assertNull(autoEpgUrlForPlaylist("https://iptv-org.github.io/iptv/index.country.m3u"))
    }

    @Test
    fun `junk path segments cannot be spliced into the guide URL`() {
        assertNull(autoEpgUrlForPlaylist("https://iptv-org.github.io/iptv/countries/../secret.m3u"))
        assertNull(autoEpgUrlForPlaylist("https://iptv-org.github.io/iptv/countries/.m3u"))
        assertNull(autoEpgUrlForPlaylist("https://iptv-org.github.io/iptv/countries/za.m3u.bak"))
    }
}
