package com.example.iptvapp.epg

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The relevance test the XMLTV parser runs per programme, kept pure so it is
 * testable without the Android XmlPullParser.
 *
 * It is deliberately BROADER than the matcher: anything sharing a playlist
 * channel's case-sensitive base id is kept, including regional and time-shift
 * ids. Uniqueness of a quality match can only be judged from every candidate
 * the guide mentions, so the parser must not pre-empt that decision — the
 * matcher is what rejects "@East"/"@Plus1".
 */
class EpgCandidateIdsTest {

    private val playlist = listOf("nasaTV@HD", "superSport.za")

    @Test
    fun `an exact playlist id is a candidate`() {
        assertTrue(epgCandidateIds(playlist)("nasaTV@HD"))
        assertTrue(epgCandidateIds(playlist)("superSport.za"))
    }

    @Test
    fun `an id sharing a base id is a candidate`() {
        val isCandidate = epgCandidateIds(playlist)
        assertTrue(isCandidate("nasaTV"))          // bare base
        assertTrue(isCandidate("nasaTV@FHD"))      // other quality word
        assertTrue(isCandidate("nasaTV@East"))     // regional — matched or rejected later
        assertTrue(isCandidate("nasaTV@Plus1"))    // time-shift — same
    }

    @Test
    fun `an unrelated id is not a candidate`() {
        val isCandidate = epgCandidateIds(playlist)
        assertFalse(isCandidate("bbcOne"))
        assertFalse(isCandidate("nasaTV.za"))      // different base id entirely
        assertFalse(isCandidate("superSport"))     // ".za" is part of the base
    }

    @Test
    fun `blank ids are never candidates`() {
        val isCandidate = epgCandidateIds(playlist)
        assertFalse(isCandidate(""))
        assertFalse(isCandidate("   "))
    }

    @Test
    fun `an empty playlist has no candidates`() {
        assertFalse(epgCandidateIds(emptyList())("nasaTV"))
    }

    @Test
    fun `ids are trimmed before the base comparison`() {
        assertTrue(epgCandidateIds(playlist)("  nasaTV  "))
    }
}
