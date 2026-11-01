package com.example.iptvapp.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class EpgSourceKeysTest {

    @Test
    fun `the key is playlist id plus guide url`() {
        assertEquals("3|https://g.example/x.xml.gz", epgSourceKey("3", "https://g.example/x.xml.gz"))
    }

    @Test
    fun `two playlists sharing a guide url get different keys`() {
        assertEquals(
            epgSourceKey("1", "https://g.example/x.xml.gz"),
            epgSourceKey("1", "https://g.example/x.xml.gz")
        )
        assertFalse(
            epgSourceKey("1", "https://g.example/x.xml.gz") ==
                epgSourceKey("2", "https://g.example/x.xml.gz")
        )
    }

    @Test
    fun `keys are stable across restarts for the same playlist`() {
        assertEquals(
            epgSourceKey("7", "https://g.example/x.xml.gz"),
            epgSourceKey("7", "https://g.example/x.xml.gz")
        )
    }

    @Test
    fun `keys are trimmed on both parts`() {
        assertEquals(
            epgSourceKey("3", "https://g.example/x.xml.gz"),
            epgSourceKey(" 3 ", " https://g.example/x.xml.gz ")
        )
    }

    @Test
    fun `multiple guides keep their input order`() {
        assertEquals(
            listOf("1|a", "1|b"),
            epgSourceKeys("1", listOf("a", "b"))
        )
    }

    @Test
    fun `stale-key pruning only touches the same playlist`() {
        assertTrue(belongsToPlaylist("4|https://g.example/a.xml", "4"))
        assertFalse(belongsToPlaylist("14|https://g.example/a.xml", "4"))
        assertFalse(belongsToPlaylist("9|https://g.example/a.xml", "4"))
    }
}

class EpgUrlRedactionTest {

    @Test
    fun `query strings are never shown`() {
        val redacted = redactEpgUrl("https://g.example/za.xml.gz?token=SECRET123&k=v")
        assertFalse(redacted.contains("SECRET123"))
        assertFalse(redacted.contains("token"))
        assertEquals("https://g.example/za.xml.gz", redacted)
    }

    @Test
    fun `userinfo credentials are never shown`() {
        val redacted = redactEpgUrl("https://user:pass@g.example/za.xml.gz")
        assertFalse(redacted.contains("user:pass"))
        assertFalse(redacted.contains("pass"))
        assertEquals("https://g.example/za.xml.gz", redacted)
    }

    @Test
    fun `fragments are dropped`() {
        assertEquals(
            "https://g.example/za.xml.gz",
            redactEpgUrl("https://g.example/za.xml.gz#section")
        )
    }

    @Test
    fun `very long paths are truncated`() {
        val longPath = "/" + "a".repeat(200)
        val redacted = redactEpgUrl("https://g.example$longPath")
        assertTrue(redacted.length < 80)
        assertTrue(redacted.startsWith("https://g.example/aaa"))
    }

    @Test
    fun `unparsable urls degrade to a truncated literal without their query`() {
        val redacted = redactEpgUrl("not a url at all?token=SECRET")
        assertFalse(redacted.contains("SECRET"))
        assertTrue(redacted.startsWith("not a url at all"))
    }
}

class LimitedInputStreamTest {

    private fun streamOf(bytes: Int, maxBytes: Long) =
        LimitedInputStream(ByteArrayInputStream(ByteArray(bytes)), maxBytes)

    @Test
    fun `reading under the limit works`() {
        val input = streamOf(10, maxBytes = 100)
        val buffer = ByteArray(10)
        assertEquals(10, input.read(buffer, 0, 10))
        input.close()
    }

    @Test
    fun `reading past the limit throws instead of exhausting memory`() {
        val input = streamOf(1024, maxBytes = 100)
        try {
            val buffer = ByteArray(256)
            repeat(4) { input.read(buffer, 0, 256) }
            throw AssertionError("expected EpgLimitExceededException")
        } catch (expected: EpgLimitExceededException) {
            // The exception is an IOException, so a repository's per-source
            // handler reports it like any other network failure.
            assertTrue(expected is java.io.IOException)
        }
    }

    @Test
    fun `single byte reads are counted too`() {
        val input = streamOf(5, maxBytes = 3)
        input.read()
        input.read()
        input.read()
        try {
            input.read()
            throw AssertionError("expected EpgLimitExceededException")
        } catch (expected: EpgLimitExceededException) {
            // expected
        }
    }
}
