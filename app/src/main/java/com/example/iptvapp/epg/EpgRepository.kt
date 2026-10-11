package com.example.iptvapp.epg

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * Fetches an XMLTV guide, parses it (scoped to the given channel ids and a
 * time window), and caches the result in Room. All network/parse work runs
 * on Dispatchers.IO.
 *
 * EPG is opt-in: an empty URL means the feature is off, and nothing here
 * runs. A failed fetch/parse throws so the caller can surface it without
 * wiping a previously-good cache (replaceAll only runs after a successful
 * parse that produced programmes).
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
     * Downloads + parses + replaces the cache. [channelIds] is the set of
     * tvg-ids in the active playlist; programmes for other channels are
     * dropped. Returns the number of programmes cached.
     */
    suspend fun refresh(epgUrl: String, channelIds: Set<String>): Int = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val windowStart = now - WINDOW_PAST_MS
        val windowEnd = now + WINDOW_FUTURE_MS

        val programmes = openStream(epgUrl).use { stream ->
            XmltvParser.parse(stream, channelIds, windowStart, windowEnd)
        }
        // Don't wipe a good cache for an empty/garbage guide.
        if (programmes.isEmpty()) {
            throw IllegalStateException("No EPG programmes found for your channels in that guide")
        }
        epgDao.replaceAll(programmes)
        programmes.size
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
