package com.example.iptvapp.playlist

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches and parses a playlist (SPEC §2.1), with local Room caching so the
 * app doesn't need to re-fetch/re-parse on every launch.
 *
 * Cache-first: getChannels() serves the Room cache if it's non-empty and
 * only hits the network on first run. Call refreshChannels() explicitly
 * (e.g. a pull-to-refresh) to force a re-fetch and replace the cache.
 * onRefreshed fires after every successful fetch-and-cache-replace —
 * every fetch path goes through here, so it's the single point the
 * "Updated X ago" label can trust.
 */
class PlaylistRepository(
    private val channelDao: ChannelDao,
    private val onRefreshed: () -> Unit = {}
) {

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 15_000
    }

    suspend fun getChannels(playlistUrl: String): List<M3uChannel> {
        val cached = channelDao.getAll()
        if (cached.isNotEmpty()) return cached.map { it.toDomain() }
        return refreshChannels(playlistUrl)
    }

    suspend fun refreshChannels(playlistUrl: String): List<M3uChannel> = withContext(Dispatchers.IO) {
        val text = fetchRawText(playlistUrl)
        val channels = M3uPlaylistParser.parse(text)
        // An empty result means the URL is wrong or the content isn't an
        // M3U. Replacing the cache with an empty table would wipe good
        // data for nothing — fail BEFORE touching Room. The settings
        // dialog relies on this to validate a new URL before persisting it.
        if (channels.isEmpty()) {
            throw IllegalStateException("No channels found in that playlist")
        }
        channelDao.replaceAll(channels.map { it.toEntity() })
        onRefreshed()
        channels
    }

    private fun fetchRawText(urlString: String): String {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
        }
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
