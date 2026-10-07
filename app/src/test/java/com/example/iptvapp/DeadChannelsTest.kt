package com.example.iptvapp

import org.junit.Assert.assertEquals
import org.junit.Test

class DeadChannelsTest {

    @Test
    fun `marking adds the url`() {
        assertEquals(setOf("http://a"), markDead(emptySet(), "http://a"))
        assertEquals(setOf("http://a", "http://b"), markDead(setOf("http://a"), "http://b"))
    }

    @Test
    fun `unmarking removes the url`() {
        assertEquals(emptySet<String>(), unmarkDead(setOf("http://a"), "http://a"))
    }

    @Test
    fun `unmarking an absent url is a no-op`() {
        assertEquals(setOf("http://a"), unmarkDead(setOf("http://a"), "http://missing"))
    }
}