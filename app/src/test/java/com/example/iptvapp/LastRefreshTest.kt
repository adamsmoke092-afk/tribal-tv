package com.example.iptvapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LastRefreshTest {

    private val now = 1_000_000_000_000L

    @Test
    fun `never refreshed renders nothing`() {
        assertNull(formatLastRefreshText(now, 0))
    }

    @Test
    fun `under a minute is just now`() {
        assertEquals("Updated just now", formatLastRefreshText(now, now - 30_000))
    }

    @Test
    fun `minutes render as m`() {
        assertEquals("Updated 5m ago", formatLastRefreshText(now, now - 5 * 60_000))
    }

    @Test
    fun `minutes round down`() {
        assertEquals("Updated 5m ago", formatLastRefreshText(now, now - (5 * 60_000 + 59_999)))
    }

    @Test
    fun `hours render as h`() {
        assertEquals("Updated 3h ago", formatLastRefreshText(now, now - (3 * 60 + 20) * 60_000))
    }

    @Test
    fun `days render as d`() {
        assertEquals("Updated 2d ago", formatLastRefreshText(now, now - (2 * 24 + 3) * 60 * 60_000))
    }

    @Test
    fun `future timestamp (clock skew) is just now`() {
        assertEquals("Updated just now", formatLastRefreshText(now, now + 60_000))
    }
}
