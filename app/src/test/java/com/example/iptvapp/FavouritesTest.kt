package com.example.iptvapp

import com.example.iptvapp.playlist.M3uChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class FavouritesTest {

    private fun groups() = listOf(
        "News" to listOf(
            M3uChannel("SABC News", "http://a"),
            M3uChannel("eNCA", "http://b")
        ),
        "Sport" to listOf(
            M3uChannel("SuperSport", "http://c")
        )
    )

    @Test
    fun `no favourites returns the input unchanged`() {
        val g = groups()
        assertSame(g, withFavouritesGroupFirst(g, emptySet()))
    }

    @Test
    fun `favourited channel is pinned first and stays in its own group`() {
        val result = withFavouritesGroupFirst(groups(), setOf("http://b"))
        assertEquals(FAVOURITES_GROUP, result.first().first)
        assertEquals(listOf("eNCA"), result.first().second.map { it.name })
        // Still present below under News, unchanged.
        assertEquals(3, result.size)
        assertEquals(listOf("SABC News", "eNCA"), result[1].second.map { it.name })
    }

    @Test
    fun `a favourite in several categories is pinned only once`() {
        val g = listOf(
            "News" to listOf(M3uChannel("Shared", "http://x")),
            "Sport" to listOf(M3uChannel("Shared", "http://x"))
        )
        val result = withFavouritesGroupFirst(g, setOf("http://x"))
        assertEquals(listOf("Shared"), result.first().second.map { it.name })
    }

    @Test
    fun `favourites not present in the current view pin nothing`() {
        val g = groups()
        // The favourited URL was filtered out / not in this playlist.
        assertSame(g, withFavouritesGroupFirst(g, setOf("http://missing")))
    }
}