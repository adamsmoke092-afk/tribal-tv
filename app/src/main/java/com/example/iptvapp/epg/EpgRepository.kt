package com.example.iptvapp.epg

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * Fetches XMLTV guides, parses them (scoped to the given channel ids and a
 * time window), and caches the merged result in Room. All network/parse work
 * runs on Dispatchers.IO.
 *
 * Multiple guide URLs (comma-separated in Settings) are supported so mixed
 * playlists (e.g. iptv-org categories, which span countries) still get
 * coverage: EVERY guide is fetched and parsed BEFORE the cache is replaced
 * once atomically — a later guide never wipes an earlier one's data.
 *
 * Per-guide failure tolerance: a dead/garbage URL among several is skipped
 * so one bad link can't decay the coverage the others provide. The channel
 * filter bounds storage regardless of how many guides are merged. Only if
 * every guide yields nothing for the playlist's channels does refresh()
 * throw, leaving a previously-good cache untouched (same contract as before).
 */
class EpgRepository(private val epgDao: EpgDao) {

    companion object {
        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val READ_TIMEOUT_MS = 20_000
        // How much guide to keep around the current moment.
        private const val WINDOW_PAST_MS = 60 * 60 * 1000L          // 1h back
        private const val WINDOW_FUTURE_MS = 48 * 60 * 60 * 1000L   // 48h ahead
    }

    /**
     * Downloads + parses + replaces the cache with the union of all guides.
     * [channelIds] is the set of tvg-ids in the active playlist; programmes
     * for other channels are dropped. The (channelId, startMs) primary key
     * de-dupes a channel that appears in more than one merged guide.
     * Returns the number of programmes cached; throws if no guide produced
     * any programmes for the playlist.
     */
    suspend fun refresh(epgUrls: List<String>, channelIds: Set<String>): Int = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val windowStart = now - WINDOW_PAST_MS
        val windowEnd = now + WINDOW_FUTURE_MS

        val merged = ArrayList<EpgProgramme>()
        for (url in epgUrls) {
            runCatching {
                openStream(url).use { stream ->
                    XmltvParser.parse(stream, channelIds, windowStart, windowEnd)
                }
            }.getOrNull()?.let { merged += it }
        }
        // Don't wipe a good cache when no guide produced anything usable.
        if (merged.isEmpty()) {
            throw IllegalStateException("No EPG programmes found for your channels in those guides")
        }
        epgDao.replaceAll(merged)
        merged.size
    }

    /** Current + next programme for a channel's tvg-id, or null/empty when unknown. */
    suspend fun nowNext(tvgId: String?): NowNext {
        if (tvgId.isNullOrBlank()) return NowNext(null, null)
        val now = System.currentTimeMillis()
        val upcoming = epgDao.upcomingForChannel(tvgId, now)
        return selectNowNext(upcoming, now)
    }

    suspend fun hasData(): Boolean = withContext(Dispatchers.IO) { epgDao.count() > 0 }

    /**
     * Opens the guide stream, transparently gunzipping when the server says
     * gzip or the URL ends in .gz (XMLTV guides are very commonly gzipped).
     */
    private fun openStream(urlString: String): InputStream {
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
        return if (gzipped) GZIPInputStream(raw) else raw
    }
}
