package com.example.iptvapp

import com.example.iptvapp.playlist.M3uChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class RecentsTest {

    @Test
    fun `new url moves to front`() {
        assertEquals(
            listOf("http://new", "http://a", "http://b"),
            updatedRecents(listOf("http://a", "http://b"), "http://new")
        )
    }

    @Test
    fun `re-watching an existing url moves it to front without duplicating`() {
        assertEquals(
            listOf("http://b", "http://a"),
            updatedRecents(listOf("http://a", "http://b"), "http://b")
        )
    }

    @Test
    fun `list is bounded`() {
        val current = (1..30).map { "http://$it" }
        val result = updatedRecents(current, "http://new")
        assertEquals(RECENTS_MAX, result.size)
        assertEquals("http://new", result.first())
    }

    private fun groups() = listOf(
        FAVOURITES_GROUP to listOf(M3uChannel("Fav", "http://fav")),
        "News" to listOf(M3uChannel("News1", "http://n1"), M3uChannel("News2", "http://n2"))
    )

    @Test
    fun `no recents returns the input unchanged`() {
        val g = groups()
        assertSame(g, withRecentsGroup(g, emptyList()))
    }

    @Test
    fun `recents group sits under favourites in recency order`() {
        val result = withRecentsGroup(groups(), listOf("http://n2", "http://n1"))
        assertEquals(FAVOURITES_GROUP, result[0].first)
        assertEquals(RECENTS_GROUP, result[1].first)
        assertEquals(listOf("News2", "News1"), result[1].second.map { it.name })
        // Real groups still below, untouched.
        assertEquals("News", result[2].first)
        assertEquals(2, result[2].second.size)
    }

    @Test
    fun `without favourites the recents group goes first`() {
        val g = listOf("News" to listOf(M3uChannel("News1", "http://n1")))
        val result = withRecentsGroup(g, listOf("http://n1"))
        assertEquals(RECENTS_GROUP, result[0].first)
    }

    @Test
    fun `recents not in the current view pin nothing`() {
        val g = groups()
        assertSame(g, withRecentsGroup(g, listOf("http://missing")))
    }
}