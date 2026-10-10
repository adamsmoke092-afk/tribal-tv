package com.example.iptvapp

import com.example.iptvapp.playlist.M3uChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelDisplayTest {

    @Test
    fun `plain name stays as-is`() {
        val d = parseDisplay("SABC 1")
        assertEquals("SABC 1", d.cleanName)
        assertNull(d.quality)
        assertEquals(emptyList<String>(), d.tags)
    }

    @Test
    fun `quality token only`() {
        val d = parseDisplay("SABC 1 (576p)")
        assertEquals("SABC 1", d.cleanName)
        assertEquals("576p", d.quality)
        assertEquals(emptyList<String>(), d.tags)
    }

    @Test
    fun `quality plus tag`() {
        val d = parseDisplay("eMovies (1080p) [Geo-blocked]")
        assertEquals("eMovies", d.cleanName)
        assertEquals("1080p", d.quality)
        assertEquals(listOf("Geo-blocked"), d.tags)
    }

    @Test
    fun `multiple tags all extracted in order`() {
        val d = parseDisplay("BBC News [Not 24/7] [Geo-blocked]")
        assertEquals("BBC News", d.cleanName)
        assertNull(d.quality)
        assertEquals(listOf("Not 24/7", "Geo-blocked"), d.tags)
    }

    @Test
    fun `parentheses that are not a quality token stay in the name`() {
        val d = parseDisplay("News (DStv 403)")
        assertEquals("News (DStv 403)", d.cleanName)
        assertNull(d.quality)
    }

    @Test
    fun `odd spacing around tokens is collapsed`() {
        val d = parseDisplay("  SABC 2   (576p)   [Geo-blocked]  ")
        assertEquals("SABC 2", d.cleanName)
        assertEquals("576p", d.quality)
        assertEquals(listOf("Geo-blocked"), d.tags)
    }

    @Test
    fun `name that is only tokens falls back to placeholder`() {
        val d = parseDisplay(" (576p) [Geo-blocked] ")
        assertEquals("Unknown channel", d.cleanName)
    }

    @Test
    fun `group title splits on semicolon with trimming`() {
        assertEquals(
            listOf("Entertainment", "Family", "General"),
            categoriesForGroupTitle("Entertainment;Family;General")
        )
        assertEquals(listOf("News"), categoriesForGroupTitle(" News "))
        assertEquals(listOf("Sport"), categoriesForGroupTitle("Sport;; "))
    }

    @Test
    fun `null or empty group title is other`() {
        assertEquals(listOf("Other"), categoriesForGroupTitle(null))
        assertEquals(listOf("Other"), categoriesForGroupTitle(""))
        assertEquals(listOf("Other"), categoriesForGroupTitle(" ; "))
    }

    @Test
    fun `grouped channels sorted alphabetically with other last`() {
        val channels = listOf(
            M3uChannel("A", "http://a", groupTitle = "Zulu"),
            M3uChannel("B", "http://b", groupTitle = "News"),
            M3uChannel("C", "http://c", groupTitle = null),
            M3uChannel("D", "http://d", groupTitle = "News;Sport")
        )
        val grouped = groupChannels(channels)
        assertEquals(listOf("News", "Sport", "Zulu", "Other"), grouped.map { it.first })
        // D appears under both of its categories.
        assertEquals(2, grouped.first { it.first == "News" }.second.size)
        assertEquals(listOf("D"), grouped.first { it.first == "Sport" }.second.map { it.name })
    }

    @Test
    fun `blank query returns everything`() {
        val channels = listOf(
            M3uChannel("SABC 1", "http://a", groupTitle = "News"),
            M3uChannel("eTV", "http://b", groupTitle = "Entertainment")
        )
        assertEquals(channels, filterChannels("", channels))
        assertEquals(channels, filterChannels("   ", channels))
    }

    @Test
    fun `search is case-insensitive`() {
        val channels = listOf(
            M3uChannel("SABC 1 (576p)", "http://a", groupTitle = "News")
        )
        assertEquals(1, filterChannels("sabc", channels).size)
        assertEquals(1, filterChannels("SABC", channels).size)
    }

    @Test
    fun `no match returns empty`() {
        val channels = listOf(M3uChannel("SABC 1", "http://a", groupTitle = "News"))
        assertTrue(filterChannels("xyzzy", channels).isEmpty())
    }

    @Test
    fun `query matches on group name`() {
        val channels = listOf(
            M3uChannel("Totally different name", "http://a", groupTitle = "News")
        )
        assertEquals(1, filterChannels("news", channels).size)
    }

    @Test
    fun `query is trimmed and matches the clean name`() {
        val channels = listOf(M3uChannel("SABC 2 (576p)", "http://a", groupTitle = "News"))
        assertEquals(1, filterChannels("  sabc 2  ", channels).size)
    }

    @Test
    fun `isHd is true only at 720 and above`() {
        assertTrue(isHd("720p"))
        assertTrue(isHd("1080p"))
        assertFalse(isHd("576p"))
        assertFalse(isHd(null))
    }

    @Test
    fun `hasTag ignores case`() {
        val d = parseDisplay("X [geo-BLOCKED]")
        assertTrue(hasTag(d, "Geo-blocked"))
        assertFalse(hasTag(d, "Not 24/7"))
    }

    @Test
    fun `all filters off returns the same list instance`() {
        val channels = listOf(M3uChannel("A", "http://a"))
        assertSame(channels, applyFilters(channels, hideGeoBlocked = false, hideNot24x7 = false, hdOnly = false))
    }

    @Test
    fun `hideGeoBlocked drops only geo-blocked channels`() {
        val channels = listOf(
            M3uChannel("A [Geo-blocked]", "http://a"),
            M3uChannel("B", "http://b")
        )
        val result = applyFilters(channels, hideGeoBlocked = true, hideNot24x7 = false, hdOnly = false)
        assertEquals(listOf("B"), result.map { it.name })
    }

    @Test
    fun `hideNot24x7 drops only not-24-7 channels`() {
        val channels = listOf(
            M3uChannel("A [Not 24/7]", "http://a"),
            M3uChannel("B", "http://b")
        )
        val result = applyFilters(channels, hideGeoBlocked = false, hideNot24x7 = true, hdOnly = false)
        assertEquals(listOf("B"), result.map { it.name })
    }

    @Test
    fun `hdOnly drops sub-720 but keeps 720+, and keeps unknown quality`() {
        val channels = listOf(
            M3uChannel("SD (576p)", "http://a"),
            M3uChannel("HD (720p)", "http://b"),
            M3uChannel("FHD (1080p)", "http://c"),
            M3uChannel("NoToken", "http://d")
        )
        val result = applyFilters(channels, hideGeoBlocked = false, hideNot24x7 = false, hdOnly = true)
        assertEquals(listOf("HD (720p)", "FHD (1080p)", "NoToken"), result.map { it.name })
    }

    @Test
    fun `filters combine`() {
        val channels = listOf(
            M3uChannel("A (576p) [Geo-blocked]", "http://a"),
            M3uChannel("B (1080p) [Geo-blocked]", "http://b"),
            M3uChannel("C (576p)", "http://c"),
            M3uChannel("D (1080p)", "http://d")
        )
        val result = applyFilters(channels, hideGeoBlocked = true, hideNot24x7 = false, hdOnly = true)
        assertEquals(listOf("D (1080p)"), result.map { it.name })
    }

    @Test
    fun `chip filter All keeps everything`() {
        val channels = listOf(M3uChannel("A", "http://a", groupTitle = "News"))
        assertEquals(channels, applyChipFilter(CHIP_ALL, channels, emptySet()))
    }

    @Test
    fun `chip filter Favorites keeps only favourites`() {
        val channels = listOf(
            M3uChannel("A", "http://a"),
            M3uChannel("B", "http://b")
        )
        assertEquals(
            listOf("A"),
            applyChipFilter(CHIP_FAVOURITES, channels, setOf("http://a")).map { it.name }
        )
    }

    @Test
    fun `chip filter by group name matches membership`() {
        val channels = listOf(
            M3uChannel("A", "http://a", groupTitle = "News;Sport"),
            M3uChannel("B", "http://b", groupTitle = "Music")
        )
        assertEquals(listOf("A"), applyChipFilter("Sport", channels, emptySet()).map { it.name })
    }

    @Test
    fun `offline channels sort to the end keeping relative order`() {
        val channels = listOf(
            M3uChannel("A", "http://a"),
            M3uChannel("B", "http://b"),
            M3uChannel("C", "http://c")
        )
        val result = sortOfflineLast(channels, setOf("http://c", "http://a"))
        assertEquals(listOf("B", "A", "C"), result.map { it.name })
    }
}