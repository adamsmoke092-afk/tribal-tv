package com.example.iptvapp.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Acceptance tests for loose ("quality alias") matching. These encode the
 * rules agreed on 2026-10-30: split at '@' only, allow-list SD/HD/FHD/UHD/4K,
 * exact first, unique-or-suggestions, and never a regional/time-shift match.
 */
class EpgQualityMatchTest {

    // ---- splitting ----

    @Test
    fun `splits at the first at-sign only`() {
        assertEquals(SplitId("nasaTV", "HD"), splitIdSuffix("nasaTV@HD"))
        assertEquals(SplitId("nasaTV.za", null), splitIdSuffix("nasaTV.za"))
        // A second '@' stays in the suffix, which then isn't a quality word.
        assertEquals(SplitId("a", "b@c"), splitIdSuffix("a@b@c"))
    }

    @Test
    fun `a country code is part of the base id, not a suffix`() {
        assertEquals(SplitId("superSport.za", null), splitIdSuffix("superSport.za"))
        assertEquals(SplitId("channel.za", "HD"), splitIdSuffix("channel.za@HD"))
    }

    @Test
    fun `only the five agreed suffix words count as quality`() {
        listOf("SD", "HD", "FHD", "UHD", "4K").forEach {
            assertTrue("$it should be a quality suffix", isQualitySuffix(it))
        }
        listOf("East", "West", "Plus1", "Africa", "za", "720p", "1080p", "8k", "hq", "lq")
            .forEach { assertFalse("$it must not be a quality suffix", isQualitySuffix(it)) }
    }

    @Test
    fun `bare suffixes are allowed and blank counts as bare`() {
        assertTrue(isQualitySuffix(null))
        assertTrue(isQualitySuffix(""))
        assertTrue(isQualitySuffix("   "))
    }

    // ---- resolve: exact always wins ----

    @Test
    fun `exact match wins even when aliases also exist`() {
        val r = resolveGuideId("nasaTV@HD", listOf("nasaTV", "nasaTV@HD", "nasaTV@FHD"))
        assertEquals("nasaTV@HD", r.matchedGuideId)
        assertTrue(r.exact)
        assertTrue(r.suggestions.isEmpty())
    }

    @Test
    fun `exact match is case sensitive`() {
        val r = resolveGuideId("NASA@HD", listOf("nasa@HD", "NASA@HD"))
        assertEquals("NASA@HD", r.matchedGuideId)
        assertTrue(r.exact)
    }

    // ---- resolve: unique alias ----

    @Test
    fun `playlist suffix resolves to the bare guide id`() {
        val r = resolveGuideId("nasaTV@HD", listOf("nasaTV"))
        assertEquals("nasaTV", r.matchedGuideId)
        assertFalse(r.exact)
        assertTrue(r.suggestions.isEmpty())
    }

    @Test
    fun `bare playlist id resolves to a suffixed guide id`() {
        val r = resolveGuideId("nasaTV", listOf("nasaTV@HD"))
        assertEquals("nasaTV@HD", r.matchedGuideId)
        assertFalse(r.exact)
    }

    @Test
    fun `two different quality suffixes are compatible`() {
        val r = resolveGuideId("nasaTV@HD", listOf("nasaTV@FHD"))
        assertEquals("nasaTV@FHD", r.matchedGuideId)
    }

    @Test
    fun `suffix comparison is case insensitive but the stored id keeps its case`() {
        val r = resolveGuideId("nasaTV@hd", listOf("nasaTV@HD"))
        // Both suffixes are quality words, bases match case-sensitively.
        assertEquals("nasaTV@HD", r.matchedGuideId)
        assertEquals("nasaTV@HD", r.matchedGuideId?.let { normalizeTvgId(it) })
    }

    // ---- resolve: ambiguity is reported, never guessed ----

    @Test
    fun `a guide with both a bare and a suffixed id is ambiguous for a different suffix`() {
        val r = resolveGuideId("nasaTV@SD", listOf("nasaTV", "nasaTV@HD"))
        assertNull("must not guess between two candidates", r.matchedGuideId)
        assertTrue(r.isAmbiguous)
        assertEquals(listOf("nasaTV", "nasaTV@HD"), r.suggestions)
    }

    @Test
    fun `suggestions are sorted for deterministic output`() {
        val r = resolveGuideId("x@SD", listOf("x@UHD", "x", "x@HD"))
        assertEquals(listOf("x", "x@HD", "x@UHD"), r.suggestions)
    }

    // ---- resolve: never a regional or time-shift match ----

    @Test
    fun `regional suffixes never match`() {
        assertNull(resolveGuideId("nasaTV@HD", listOf("nasaTV@East")).matchedGuideId)
        assertNull(resolveGuideId("nasaTV@HD", listOf("nasaTV@West")).matchedGuideId)
        assertTrue(resolveGuideId("nasaTV@HD", listOf("nasaTV@East")).isUnmatched)
    }

    @Test
    fun `time-shift suffixes never match`() {
        assertNull(resolveGuideId("nasaTV@Plus1", listOf("nasaTV")).matchedGuideId)
        assertNull(resolveGuideId("nasaTV", listOf("nasaTV@Plus1")).matchedGuideId)
    }

    @Test
    fun `country codes in the base id are never treated as quality`() {
        assertNull(resolveGuideId("nasaTV.za", listOf("nasaTV")).matchedGuideId)
        assertNull(resolveGuideId("nasaTV", listOf("nasaTV.za")).matchedGuideId)
    }

    @Test
    fun `base ids are compared case sensitively`() {
        assertNull(resolveGuideId("NASA@HD", listOf("nasa")).matchedGuideId)
        assertNull(resolveGuideId("nasa@HD", listOf("NASA")).matchedGuideId)
    }

    // ---- trimming ----

    @Test
    fun `ids are trimmed on both sides before comparing`() {
        val r = resolveGuideId("  nasaTV@HD  ", listOf(" nasaTV@HD "))
        assertEquals("nasaTV@HD", r.matchedGuideId)
        assertTrue(r.exact)

        val alias = resolveGuideId("  nasaTV@HD ", listOf(" nasaTV "))
        assertEquals("nasaTV", alias.matchedGuideId)
    }

    @Test
    fun `empty or blank guide ids are ignored`() {
        assertTrue(resolveGuideId("nasaTV", listOf("", "   ")).isUnmatched)
        assertTrue(resolveGuideId("nasaTV", emptyList()).isUnmatched)
    }

    @Test
    fun `normalizeTvgId is the single trimming used for storage and lookup`() {
        assertEquals("nasaTV@HD", normalizeTvgId("  nasaTV@HD "))
    }

    // ---- batch ----

    @Test
    fun `batch report separates matched, ambiguous and unmatched`() {
        val report = matchPlaylistIds(
            playlistIds = listOf("a", "b@HD", "c@SD"),
            guideIds = listOf("a", "b", "c", "c@HD")
        )
        assertEquals(listOf("a", "b@HD"), report.matched.map { it.playlistId })
        assertEquals(listOf("c@SD"), report.ambiguous.map { it.playlistId })
        assertTrue(report.unmatched.isEmpty())
    }

    @Test
    fun `programmes are stored under the playlist id, not the guide id`() {
        val report = matchPlaylistIds(
            playlistIds = listOf("nasaTV@HD"),
            guideIds = listOf("nasaTV")
        )
        // The guide declares "nasaTV", the playlist asks with "nasaTV@HD";
        // the row must be written under the playlist's id so lookups hit.
        assertEquals("nasaTV@HD", report.playlistIdFor("nasaTV"))
        assertNull("an unrelated guide id must be dropped", report.playlistIdFor("other"))
    }

    @Test
    fun `an ambiguous channel stores nothing`() {
        val report = matchPlaylistIds(
            playlistIds = listOf("nasaTV@SD"),
            guideIds = listOf("nasaTV", "nasaTV@HD")
        )
        assertNull(report.playlistIdFor("nasaTV"))
        assertNull(report.playlistIdFor("nasaTV@HD"))
    }
}
