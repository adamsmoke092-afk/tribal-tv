package com.example.iptvapp.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure logic for the three refresh safeguards: truncation handling, source
 * status semantics and overall state derivation. The Room-backed half lives in
 * EpgCacheIntegrityTest (Robolectric).
 */
class EpgRefreshDiagnosticsTest {

    private fun result(status: EpgSourceStatus, count: Int = 0) = EpgSourceResult(
        sourceKey = "key",
        redactedUrl = "https://g.example/g.xml.gz",
        status = status,
        programmeCount = count
    )

    // ---- truncation disables aliasing but not exact matching ----

    @Test
    fun `a truncated guide still matches exactly`() {
        val resolution = resolveGuideId("nasaTV@HD", listOf("nasaTV@HD"), allowAliases = false)
        assertEquals("nasaTV@HD", resolution.matchedGuideId)
        assertTrue(resolution.exact)
    }

    @Test
    fun `a truncated guide never takes a quality alias even when unique`() {
        // With a complete set this WOULD match: one compatible candidate.
        val complete = resolveGuideId("nasaTV@HD", listOf("nasaTV"), allowAliases = true)
        assertEquals("nasaTV", complete.matchedGuideId)

        // With an incomplete set uniqueness is unknowable, so nothing matches.
        val partial = resolveGuideId("nasaTV@HD", listOf("nasaTV"), allowAliases = false)
        assertFalse(partial.exact)
        assertEquals(null, partial.matchedGuideId)
        assertTrue(partial.suggestions.isEmpty())
    }

    @Test
    fun `a truncated guide reports no suggestions`() {
        val report = matchPlaylistIds(
            playlistIds = listOf("x@SD"),
            guideIds = listOf("x", "x@HD"),
            allowAliases = false
        )
        assertTrue(report.matched.isEmpty())
        assertTrue(report.ambiguous.isEmpty())
        assertEquals(listOf("x@SD"), report.unmatched.map { it.playlistId })
    }

    // ---- per-source status semantics ----

    @Test
    fun `only a loaded source replaces its previous cache`() {
        assertFalse(EpgSourceStatus.LOADED.keepsPreviousCache())
        assertTrue(EpgSourceStatus.FAILED.keepsPreviousCache())
        assertTrue(EpgSourceStatus.EMPTY.keepsPreviousCache())
        assertTrue(EpgSourceStatus.TRUNCATED.keepsPreviousCache())
    }

    // ---- overall state derivation ----

    @Test
    fun `no sources and no data means nothing configured`() {
        assertEquals(
            EpgRefreshState.NO_GUIDE_CONFIGURED,
            epgRefreshState(emptyList(), cachedProgrammeCount = 0, isLoading = false)
        )
    }

    @Test
    fun `no sources with cached rows reports cached data available`() {
        assertEquals(
            EpgRefreshState.CACHED_DATA_AVAILABLE,
            epgRefreshState(emptyList(), cachedProgrammeCount = 40, isLoading = false)
        )
    }

    @Test
    fun `loading over existing rows keeps showing the cached state`() {
        assertEquals(
            EpgRefreshState.CACHED_DATA_AVAILABLE,
            epgRefreshState(listOf(result(EpgSourceStatus.LOADED)), cachedProgrammeCount = 10, isLoading = true)
        )
        assertEquals(
            EpgRefreshState.LOADING,
            epgRefreshState(listOf(result(EpgSourceStatus.LOADED)), cachedProgrammeCount = 0, isLoading = true)
        )
    }

    @Test
    fun `all sources loaded is loaded, mixed is partial`() {
        assertEquals(
            EpgRefreshState.LOADED,
            epgRefreshState(
                listOf(result(EpgSourceStatus.LOADED, 5), result(EpgSourceStatus.LOADED, 7)),
                cachedProgrammeCount = 12,
                isLoading = false
            )
        )
        assertEquals(
            EpgRefreshState.PARTIALLY_LOADED,
            epgRefreshState(
                listOf(result(EpgSourceStatus.LOADED, 5), result(EpgSourceStatus.FAILED)),
                cachedProgrammeCount = 5,
                isLoading = false
            )
        )
    }

    @Test
    fun `every source failed falls back to cached rows when they exist`() {
        val allFailed = listOf(result(EpgSourceStatus.FAILED), result(EpgSourceStatus.FAILED))
        assertEquals(
            EpgRefreshState.CACHED_DATA_AVAILABLE,
            epgRefreshState(allFailed, cachedProgrammeCount = 30, isLoading = false)
        )
        assertEquals(
            EpgRefreshState.FAILED,
            epgRefreshState(allFailed, cachedProgrammeCount = 0, isLoading = false)
        )
    }

    @Test
    fun `summary counts each status separately`() {
        val summary = epgDiagnosticsSummary(
            sources = listOf(
                result(EpgSourceStatus.LOADED, 4),
                result(EpgSourceStatus.FAILED),
                result(EpgSourceStatus.TRUNCATED),
                result(EpgSourceStatus.EMPTY)
            ),
            matchedChannels = 9
        )
        assertEquals(1, summary.loaded)
        assertEquals(1, summary.failed)
        assertEquals(1, summary.truncated)
        assertEquals(1, summary.empty)
        assertEquals(9, summary.matchedChannels)
    }
}
