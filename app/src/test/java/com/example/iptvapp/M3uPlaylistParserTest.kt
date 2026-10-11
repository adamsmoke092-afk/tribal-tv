package com.example.iptvapp

import com.example.iptvapp.playlist.M3uPlaylistParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uPlaylistParserTest {

    @Test
    fun `parses EXTVLCOPT user-agent and referrer into canonical headers`() {
        val text = """
            #EXTM3U
            #EXTINF:-1 tvg-id="c1",Channel One
            #EXTVLCOPT:http-user-agent=Mozilla/5.0 (SmartTV)
            #EXTVLCOPT:http-referrer=http://ref.example.com/
            http://host.example.com/stream1
        """.trimIndent()

        val channels = M3uPlaylistParser.parse(text)

        assertEquals(1, channels.size)
        val headers = channels[0].headers
        assertEquals("Mozilla/5.0 (SmartTV)", headers["User-Agent"])
        assertEquals("http://ref.example.com/", headers["Referer"])
    }

    @Test
    fun `strips surrounding quotes from EXTVLCOPT value`() {
        val text = """
            #EXTINF:-1,Quoted UA
            #EXTVLCOPT:http-user-agent="Agent/1.0"
            http://host/stream
        """.trimIndent()

        val channels = M3uPlaylistParser.parse(text)

        assertEquals("Agent/1.0", channels[0].headers["User-Agent"])
    }

    @Test
    fun `value may contain equals signs`() {
        val text = """
            #EXTINF:-1,Token UA
            #EXTVLCOPT:http-user-agent=player?token=a=b=c
            http://host/stream
        """.trimIndent()

        val channels = M3uPlaylistParser.parse(text)

        assertEquals("player?token=a=b=c", channels[0].headers["User-Agent"])
    }

    @Test
    fun `headers do not leak to the next channel`() {
        val text = """
            #EXTINF:-1,With UA
            #EXTVLCOPT:http-user-agent=Special/1.0
            http://host/with
            #EXTINF:-1,Without UA
            http://host/without
        """.trimIndent()

        val channels = M3uPlaylistParser.parse(text)

        assertEquals(2, channels.size)
        assertEquals("Special/1.0", channels[0].headers["User-Agent"])
        assertTrue(channels[1].headers.isEmpty())
    }

    @Test
    fun `unmapped EXTVLCOPT options are ignored`() {
        val text = """
            #EXTINF:-1,Deinterlace
            #EXTVLCOPT:deinterlace=1
            http://host/stream
        """.trimIndent()

        val channels = M3uPlaylistParser.parse(text)

        assertTrue(channels[0].headers.isEmpty())
    }

    @Test
    fun `channel without EXTINF is still skipped`() {
        val text = """
            #EXTVLCOPT:http-user-agent=Orphan/1.0
            http://host/orphan
        """.trimIndent()

        val channels = M3uPlaylistParser.parse(text)

        assertTrue(channels.isEmpty())
    }
}
