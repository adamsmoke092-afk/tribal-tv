package com.example.iptvapp.epg

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * Fetches XMLTV guides, maps their channel ids onto the playlist's tvg-ids and
 * caches the result in Room, ONE SOURCE AT A TIME. All network/parse work runs
 * on Dispatchers.IO.
 *
 * Cache integrity rules this class exists to enforce:
 *  - Rows are keyed by a per-playlist source digest (epgSourceKey), so two
 *    playlists using the same guide URL never share or erase each other's data.
 *  - A source's cached rows are replaced ONLY after that source parsed
 *    successfully AND produced usable rows. Failure, an empty answer or a
 *    truncated guide always leaves the previous rows in place.
 *  - A truncated guide additionally disables quality-alias matching (its
 *    candidate id set is incomplete, so uniqueness cannot be established) and
 *    its exact matches are MERGED rather than replacing anything.
 *  - Cancellation is rethrown, never recorded as a source failure.
 *  - Nothing here logs or returns a raw URL: failures carry a sanitized message
 *    and results carry only a redacted URL.
 */
class EpgRepository(private val epgDao: EpgDao) {

    companion object {
        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val READ_TIMEOUT_MS = 20_000
        // How much guide to keep around the current moment.
        private const val WINDOW_PAST_MS = 60 * 60 * 1000L          // 1h back
        private const val WINDOW_FUTURE_MS = 48 * 60 * 60 * 1000L   // 48h ahead

        /** Decompressed byte budget per guide. Bounds memory for one source. */
        internal const val MAX_GUIDE_BYTES = 32L * 1024 * 1024

        /** Diagnostic caps, so the settings dialog stays readable on a phone. */
        internal const val MAX_UNMATCHED_SHOWN = 5
        internal const val MAX_SUGGESTIONS_SHOWN = 3
    }

    /**
     * Refreshes every configured guide for one playlist. Never throws for a
     * per-source reason: each source gets its own [EpgSourceResult] and the
     * caller renders them as diagnostics. [CancellationException] propagates.
     *
     * @param playlistKey the active playlist's registry id, as a string
     * @param guideUrls   the sources to fetch, in display order
     * @param playlistIds the playlist's non-blank tvg-ids
     * @param looseMatching when false, only exact ids match
     */
    suspend fun refresh(
        playlistKey: String,
        guideUrls: List<String>,
        playlistIds: Set<String>,
        looseMatching: Boolean
    ): EpgRefreshResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val windowStart = now - WINDOW_PAST_MS
        val windowEnd = now + WINDOW_FUTURE_MS
        // Canonicalise once: trim, drop blanks, dedupe (order preserved). Every
        // consumer — source keys, HTTP requests, cache lookups — uses this
        // list, so whitespace-only differences never create duplicate downloads
        // or duplicate cache rows.
        val canonicalUrls = guideUrls.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val sourceKeys = epgSourceKeys(playlistKey, canonicalUrls)
        val isCandidate = epgCandidateIds(playlistIds)

        val results = ArrayList<EpgSourceResult>(canonicalUrls.size)
        for ((index, url) in canonicalUrls.withIndex()) {
            results += fetchAndStore(
                sourceKey = sourceKeys[index],
                guideUrl = url,
                playlistIds = playlistIds,
                looseMatching = looseMatching,
                isCandidate = isCandidate,
                windowStartMs = windowStart,
                windowEndMs = windowEnd
            )
        }

        // The bounded window is enforced whether or not anything was fetched.
        epgDao.deleteExpired(now)

        val keys = sourceKeys.distinct()
        EpgRefreshResult(
            sources = results,
            matchedChannels = if (keys.isEmpty()) 0 else epgDao.countDistinctChannelsFor(keys),
            cachedProgrammes = if (keys.isEmpty()) 0 else epgDao.countBySources(keys)
        )
    }

    /**
     * Fetches one guide and hands the parse to [storeParsedSource]. A fetch or
     * parse problem becomes a FAILED result carrying a sanitized message; the
     * source's existing rows are untouched because nothing was written.
     */
    private suspend fun fetchAndStore(
        sourceKey: String,
        guideUrl: String,
        playlistIds: Set<String>,
        looseMatching: Boolean,
        isCandidate: (String) -> Boolean,
        windowStartMs: Long,
        windowEndMs: Long
    ): EpgSourceResult {
        val redacted = redactEpgUrl(guideUrl)
        val parsed = try {
            openGuide(guideUrl).use { stream ->
                XmltvParser.parse(stream, isCandidate, windowStartMs, windowEndMs)
            }
        } catch (ce: CancellationException) {
            throw ce // cancellation is not a source failure — never swallow it
        } catch (e: Exception) {
            return EpgSourceResult(
                sourceKey = sourceKey,
                redactedUrl = redacted,
                status = EpgSourceStatus.FAILED,
                errorMessage = sanitizeErrorMessage(e.message ?: e.javaClass.simpleName)
            )
        }
        return storeParsedSource(sourceKey, redacted, parsed, playlistIds, looseMatching)
    }

    /**
     * Maps one guide's programmes onto playlist channels and persists them.
     *
     * Internal rather than private so the cache-integrity behaviour can be
     * unit-tested without a network: a test supplies the GuideParse directly.
     *
     * @param looseMatching quality aliases are ALSO suppressed when the parse
     *        was truncated, regardless of this flag.
     */
    internal suspend fun storeParsedSource(
        sourceKey: String,
        redactedUrl: String,
        parse: GuideParse,
        playlistIds: Set<String>,
        looseMatching: Boolean
    ): EpgSourceResult {
        val allowAliases = looseMatching && !parse.truncated
        val report = matchPlaylistIds(
            playlistIds = playlistIds,
            guideIds = parse.programmes.map { it.guideChannelId },
            allowAliases = allowAliases
        )
        val rows = parse.programmes.mapNotNull { programme ->
            val playlistId = report.playlistIdFor(programme.guideChannelId) ?: return@mapNotNull null
            EpgProgramme(
                source = sourceKey,
                channelId = playlistId,
                startMs = programme.startMs,
                stopMs = programme.stopMs,
                title = programme.title,
                description = programme.description
            )
        }

        val unmatched = report.unmatched.take(MAX_UNMATCHED_SHOWN).map { it.playlistId }
        val suggestions = report.ambiguous
            .take(MAX_UNMATCHED_SHOWN)
            .associate { resolution ->
                resolution.playlistId to resolution.suggestions.take(MAX_SUGGESTIONS_SHOWN)
            }

        if (parse.truncated) {
            // Incomplete candidate set: merge what we DID match (exact only)
            // and keep everything already cached for this source. insertAll
            // replaces per-row via the primary key; it never deletes.
            if (rows.isNotEmpty()) epgDao.insertAll(rows)
            return EpgSourceResult(
                sourceKey = sourceKey,
                redactedUrl = redactedUrl,
                status = EpgSourceStatus.TRUNCATED,
                programmeCount = rows.size,
                errorMessage = "Guide exceeded the ${XmltvParser.MAX_GUIDE_PROGRAMMES}-programme limit; " +
                    "previous data kept, quality matching disabled for this source",
                unmatchedIds = unmatched,
                suggestions = suggestions
            )
        }

        if (rows.isEmpty()) {
            // Fetched and parsed cleanly but yielded nothing for these
            // channels. Distinct from a failure, and NOT a reason to wipe the
            // rows this source cached before.
            return EpgSourceResult(
                sourceKey = sourceKey,
                redactedUrl = redactedUrl,
                status = EpgSourceStatus.EMPTY,
                unmatchedIds = unmatched,
                suggestions = suggestions
            )
        }

        epgDao.replaceSource(sourceKey, rows)
        return EpgSourceResult(
            sourceKey = sourceKey,
            redactedUrl = redactedUrl,
            status = EpgSourceStatus.LOADED,
            programmeCount = rows.size,
            unmatchedIds = unmatched,
            suggestions = suggestions
        )
    }

    /** Current + next programme for a channel, merged across THIS playlist's
     * configured sources. Empty [sourceKeys] means no guide is configured. */
    suspend fun nowNext(tvgId: String?, sourceKeys: List<String>): NowNext {
        val wanted = tvgId?.let { normalizeTvgId(it) }
        if (wanted.isNullOrEmpty() || sourceKeys.isEmpty()) return NowNext(null, null)
        return withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val upcoming = epgDao.upcomingForChannel(wanted, sourceKeys, now)
            selectNowNext(upcoming, now)
        }
    }

    /** True when this playlist still has any cached schedule. */
    suspend fun hasData(sourceKeys: List<String>): Boolean =
        sourceKeys.isNotEmpty() && withContext(Dispatchers.IO) {
            epgDao.countBySources(sourceKeys) > 0
        }

    /** Rows currently cached for this playlist's configured sources. */
    suspend fun countCached(sourceKeys: List<String>): Int =
        if (sourceKeys.isEmpty()) 0 else withContext(Dispatchers.IO) {
            epgDao.countBySources(sourceKeys)
        }

    /**
     * Opens the guide stream, transparently gunzipping when the server says
     * gzip or the URL ends in .gz (XMLTV guides are very commonly gzipped),
     * then wraps it in a byte budget so an oversized guide fails this source
     * instead of exhausting memory. The wrapped stream is still consumed by the
     * streaming pull parser — nothing is buffered whole.
     */
    private fun openGuide(urlString: String): InputStream {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            // HttpURLConnection's own transparent gzip only triggers when we
            // don't set Accept-Encoding; for .gz files the body is gzip
            // regardless, so we handle decompression explicitly below.
            setRequestProperty("Accept-Encoding", "gzip")
        }
        val raw = connection.inputStream
        val encoding = connection.contentEncoding
        val gzipped = encoding?.equals("gzip", ignoreCase = true) == true ||
            urlString.substringBefore('?').endsWith(".gz", ignoreCase = true)
        return LimitedInputStream(if (gzipped) GZIPInputStream(raw) else raw, MAX_GUIDE_BYTES)
    }
}

/** Everything the diagnostics need about one refresh of one playlist. */
data class EpgRefreshResult(
    val sources: List<EpgSourceResult>,
    val matchedChannels: Int,
    val cachedProgrammes: Int
) {
    val summary: EpgDiagnosticsSummary get() = epgDiagnosticsSummary(sources, matchedChannels)

    fun state(isLoading: Boolean): EpgRefreshState =
        epgRefreshState(sources, cachedProgrammes, isLoading)
}
