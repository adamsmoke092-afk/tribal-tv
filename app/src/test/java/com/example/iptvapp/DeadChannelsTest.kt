package com.example.iptvapp

import org.junit.Assert.assertEquals
import org.junit.Test

class DeadChannelsTest {

    private val now = 1_000_000_000_000L

    @Test
    fun `marking adds the url with a timestamp`() {
        assertEquals(mapOf("http://a" to now), markDead(emptyMap(), "http://a", now))
        assertEquals(
            mapOf("http://a" to now, "http://b" to now),
            markDead(mapOf("http://a" to now), "http://b", now)
        )
    }

    @Test
    fun `unmarking removes the url`() {
        assertEquals(emptyMap<String, Long>(), unmarkDead(mapOf("http://a" to now), "http://a"))
    }

    @Test
    fun `unmarking an absent url is a no-op`() {
        assertEquals(mapOf("http://a" to now), unmarkDead(mapOf("http://a" to now), "http://missing"))
    }

    @Test
    fun `failures older than the expiry drop out`() {
        val failures = mapOf(
            "http://fresh" to now - 1_000_000,
            "http://old" to now - DEAD_EXPIRY_MS - 1
        )
        assertEquals(setOf("http://fresh"), deadUrls(failures, now))
    }

    @Test
    fun `a failure exactly at the expiry boundary still counts`() {
        val failures = mapOf("http://edge" to now - DEAD_EXPIRY_MS)
        assertEquals(setOf("http://edge"), deadUrls(failures, now))
    }

    @Test
    fun `encode and decode round-trip`() {
        val failures = mapOf("http://a" to 123L, "http://b" to 456L)
        assertEquals(failures, decodeDead(encodeDead(failures)))
    }

    @Test
    fun `decode handles null empty and junk`() {
        assertEquals(emptyMap<String, Long>(), decodeDead(null))
        assertEquals(emptyMap<String, Long>(), decodeDead(""))
        assertEquals(emptyMap<String, Long>(), decodeDead("junk-line"))
        assertEquals(mapOf("http://a" to 7L), decodeDead("http://a\u0000junk\nhttp://a\u00007"))
    }
}
