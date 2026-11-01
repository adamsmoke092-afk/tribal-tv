package com.example.iptvapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The precedence rules for per-playlist EPG configuration, and the legacy
 * migration's *decision logic*. The preference-backed half of the migration is
 * covered by PlaylistStoreEpgTest (Robolectric); this file pins the rules
 * themselves so they stay honest without a device.
 */
class EpgPrecedenceTest {

    // ---- precedence: manual wins, never combined ----

    @Test
    fun `a manual override wins over the playlist header`() {
        assertEquals(
            listOf("https://manual.example/g.xml.gz"),
            effectiveEpgUrlsFor("https://manual.example/g.xml.gz", "https://header.example/g.xml.gz")
        )
        assertEquals(
            EpgSourceOrigin.MANUAL,
            epgSourceOrigin("https://manual.example/g.xml.gz", "https://header.example/g.xml.gz")
        )
    }

    @Test
    fun `the playlist header is used when no manual override exists`() {
        assertEquals(
            listOf("https://header.example/g.xml.gz"),
            effectiveEpgUrlsFor("", "https://header.example/g.xml.gz")
        )
        assertEquals(
            EpgSourceOrigin.PLAYLIST_HEADER,
            epgSourceOrigin("", "https://header.example/g.xml.gz")
        )
    }

    @Test
    fun `a blank manual override falls through to the header`() {
        assertEquals(
            listOf("https://header.example/g.xml.gz"),
            effectiveEpgUrlsFor("   ", "https://header.example/g.xml.gz")
        )
    }

    @Test
    fun `no override and no header means EPG is off`() {
        assertEquals(emptyList<String>(), effectiveEpgUrlsFor("", ""))
        assertEquals(EpgSourceOrigin.NONE, epgSourceOrigin(null, null))
    }

    @Test
    fun `manual overrides support several comma separated urls`() {
        assertEquals(
            listOf("https://a.example/g.xml", "https://b.example/g.xml"),
            effectiveEpgUrlsFor("https://a.example/g.xml, https://b.example/g.xml", "https://header.example/g.xml")
        )
    }

    @Test
    fun `the two sources are never merged`() {
        // A manual override pointing at a BROKEN source must not silently be
        // padded out with the playlist's own guide: the user chose the source.
        val urls = effectiveEpgUrlsFor("https://broken.example/g.xml", "https://header.example/g.xml")
        assertEquals(listOf("https://broken.example/g.xml"), urls)
        assertEquals(1, urls.size)
    }

    @Test
    fun `a whitespace-only override with no header is still no guide`() {
        assertEquals(emptyList<String>(), effectiveEpgUrlsFor("   ", ""))
    }

    @Test
    fun `origin distinguishes the two configured sources`() {
        assertEquals(EpgSourceOrigin.MANUAL, epgSourceOrigin("https://m", null))
        assertEquals(EpgSourceOrigin.PLAYLIST_HEADER, epgSourceOrigin(null, "https://h"))
        assertEquals(EpgSourceOrigin.NONE, epgSourceOrigin("", ""))
    }

    // ---- forcing a guide download on playlist refresh: decided on the
    // EFFECTIVE source, never on the header alone ----

    @Test
    fun `a manual override in force is not re-downloaded when the header changes`() {
        // Manual wins, so the effective source is identical before and after
        // even though the playlist's own header URL moved.
        val before = effectiveEpgUrlsFor("https://manual.example/g.xml", "https://old-header.example/g.xml")
        val after = effectiveEpgUrlsFor("https://manual.example/g.xml", "https://new-header.example/g.xml")

        assertEquals(listOf("https://manual.example/g.xml"), after)
        assertFalse(shouldForceEpgRefresh(before, after))
    }

    @Test
    fun `with no override a changed header forces a refresh of the new guide`() {
        val before = effectiveEpgUrlsFor("", "https://old-header.example/g.xml")
        val after = effectiveEpgUrlsFor("", "https://new-header.example/g.xml")

        assertTrue(shouldForceEpgRefresh(before, after))
    }

    @Test
    fun `a newly discovered header source forces a refresh`() {
        val before = effectiveEpgUrlsFor("", "")
        val after = effectiveEpgUrlsFor("", "https://g.example/new.xml")

        assertTrue(shouldForceEpgRefresh(before, after))
    }

    @Test
    fun `a header that disappears forces a refresh down to no source`() {
        val before = effectiveEpgUrlsFor("", "https://g.example/gone.xml")
        val after = effectiveEpgUrlsFor("", "")

        assertTrue(shouldForceEpgRefresh(before, after))
    }

    @Test
    fun `adding an override forces a refresh of the newly effective source`() {
        val before = effectiveEpgUrlsFor("", "https://header.example/g.xml")
        val after = effectiveEpgUrlsFor("https://manual.example/g.xml", "https://header.example/g.xml")

        assertTrue(shouldForceEpgRefresh(before, after))
    }

    @Test
    fun `removing an override forces a refresh onto the header source`() {
        val before = effectiveEpgUrlsFor("https://manual.example/g.xml", "https://header.example/g.xml")
        val after = effectiveEpgUrlsFor("", "https://header.example/g.xml")

        assertEquals(listOf("https://header.example/g.xml"), after)
        assertTrue(shouldForceEpgRefresh(before, after))
    }

    @Test
    fun `editing an override forces a refresh onto the new manual source`() {
        assertTrue(
            shouldForceEpgRefresh(
                effectiveEpgUrlsFor("https://a.example/g.xml", ""),
                effectiveEpgUrlsFor("https://b.example/g.xml", "")
            )
        )
    }

    @Test
    fun `an unchanged fresh source is not downloaded again`() {
        val same = effectiveEpgUrlsFor("", "https://header.example/g.xml")
        assertFalse(shouldForceEpgRefresh(same, same))
    }

    @Test
    fun `whitespace-only differences are not treated as a new source`() {
        val before = effectiveEpgUrlsFor("", "https://header.example/g.xml")
        val after = effectiveEpgUrlsFor("   ", " https://header.example/g.xml ")

        assertFalse(shouldForceEpgRefresh(before, after))
    }

    @Test
    fun `reordering the same urls does not force a refresh`() {
        // Every effective URL is fetched independently and merged, so order
        // has no semantic meaning for the force decision.
        val before = effectiveEpgUrlsFor(
            "https://a.example/g.xml, https://b.example/g.xml", ""
        )
        val after = effectiveEpgUrlsFor(
            "https://b.example/g.xml, https://a.example/g.xml", ""
        )
        assertEquals(before.sorted(), after.sorted()) // same set, different order
        assertFalse(shouldForceEpgRefresh(before, after))
    }

    @Test
    fun `adding a source url to the manual list forces a refresh`() {
        val before = effectiveEpgUrlsFor("https://a.example/g.xml", "")
        val after = effectiveEpgUrlsFor(
            "https://a.example/g.xml, https://b.example/g.xml", ""
        )
        assertTrue(shouldForceEpgRefresh(before, after))
    }

    @Test
    fun `removing a source url from the manual list forces a refresh`() {
        val before = effectiveEpgUrlsFor(
            "https://a.example/g.xml, https://b.example/g.xml", ""
        )
        val after = effectiveEpgUrlsFor("https://a.example/g.xml", "")
        assertTrue(shouldForceEpgRefresh(before, after))
    }

    @Test
    fun `changing a source url forces a refresh`() {
        val before = effectiveEpgUrlsFor("https://a.example/g.xml", "")
        val after = effectiveEpgUrlsFor("https://b.example/g.xml", "")
        assertTrue(shouldForceEpgRefresh(before, after))
    }

    @Test
    fun `changing path casing forces a refresh`() {
        // Paths are case-sensitive: /Guide.xml and /guide.xml are different
        // sources, so the change must force a refresh.
        val before = effectiveEpgUrlsFor("https://a.example/Guide.xml", "")
        val after = effectiveEpgUrlsFor("https://a.example/guide.xml", "")
        assertTrue(shouldForceEpgRefresh(before, after))
    }

    @Test
    fun `changing a case-sensitive query value forces a refresh`() {
        // Query values can be case-sensitive, so a token change must force a
        // refresh even though the scheme/host/path are identical.
        val before = effectiveEpgUrlsFor("https://a.example/g.xml?token=Abc123", "")
        val after = effectiveEpgUrlsFor("https://a.example/g.xml?token=abc123", "")
        assertTrue(shouldForceEpgRefresh(before, after))
    }

    @Test
    fun `reordering case-preserved urls does not force a refresh`() {
        val before = effectiveEpgUrlsFor(
            "https://a.example/Guide.xml, https://b.example/g.xml?token=Abc", ""
        )
        val after = effectiveEpgUrlsFor(
            "https://b.example/g.xml?token=Abc, https://a.example/Guide.xml", ""
        )
        assertEquals(before.sorted(), after.sorted())
        assertFalse(shouldForceEpgRefresh(before, after))
    }

    @Test
    fun `manual override still wins over the header after reorder`() {
        // The force decision reads the effective list; precedence is untouched,
        // so a header change behind an active override never forces a download.
        val before = effectiveEpgUrlsFor("https://manual.example/g.xml", "https://old.example/g.xml")
        val after = effectiveEpgUrlsFor("https://manual.example/g.xml", "https://new.example/g.xml")
        assertEquals(listOf("https://manual.example/g.xml"), after)
        assertFalse(shouldForceEpgRefresh(before, after))
    }

    @Test
    fun `loose matching is orthogonal to the effective source set`() {
        // Changing loose quality matching forces a rematcher in saveSettings,
        // but it does not change the effective guide URLs, so this comparison
        // reports no change and leaves the normal staleness window in charge.
        val same = effectiveEpgUrlsFor("", "https://header.example/g.xml")
        assertFalse(shouldForceEpgRefresh(same, same))
    }

    @Test
    fun `the force decision never alters precedence itself`() {
        // Forcing reads the effective list; it must not merge or reorder sources.
        val urls = effectiveEpgUrlsFor("https://manual.example/g.xml", "https://header.example/g.xml")
        assertEquals(1, urls.size)
        assertEquals("https://manual.example/g.xml", urls.first())
    }
}
