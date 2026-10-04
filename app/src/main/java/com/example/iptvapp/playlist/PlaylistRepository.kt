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
 */
class PlaylistRepository(private val channelDao: ChannelDao) {

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
        channelDao.replaceAll(channels.map { it.toEntity() })
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
