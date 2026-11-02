package com.example.iptvapp.movies

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import kotlin.Result
import kotlin.Result.Failure
import kotlin.Result.companion.success

/**
 * Fetches the VidSrc latest movies and TV shows feeds over HTTPS.
 *
 * All network I/O runs off the main thread (caller must invoke from a
 * coroutine with Dispatchers.IO, or this class can be called with
 * withContext(Dispatchers.IO)). Each feed request uses bounded timeouts,
 * closes its streams, and disconnects on completion or failure. Malformed
 * JSON and missing fields are handled gracefully — missing optional
 * fields simply yield null.
 *
 * Duplicate entries are avoided: each page request returns its own list,
 * and the caller (ViewModel) is responsible for deduping across pages by
 * ID. This repository does not automatically paginate — the caller
 * requests one page at a time.
 */
class VidSrcRepository(private val dispatcher: CoroutineDispatcher = Dispatchers.IO) {

    private const val BASE = "https://vidsrc.sh"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 15_000

    /**
     * Fetches page 1 of the latest movies feed.
     */
    suspend fun fetchLatestMovies(page: Int = 1): Result<VidSrcFeed> =
        fetchFeed("$BASE/movies/latest/page-$page.json")

    /**
     * Fetches page 1 of the latest TV shows feed.
     */
    suspend fun fetchLatestTvShows(page: Int = 1): Result<VidSrcFeed> =
        fetchFeed("$BASE/tvshows/latest/page-$page.json")

    /**
     * Fetches a single VidSrc feed page, parses the JSON, and returns a
     * [VidSrcFeed]. Network failures, HTTP errors, and malformed JSON are
     * all captured as [Failure].
     */
    private suspend fun fetchFeed(url: String): Result<VidSrcFeed> = withContext(dispatcher) {
        var connection: HttpURLConnection? = null
        var reader: BufferedReader? = null
        try {
            connection = java.net.URL(url).openConnection() as HttpURLConnection
            connection.apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "TribalTV/1.0")
                instanceFollowRedirects = true
            }

            val responseCode = connection.responseCode
            if (responseCode < 200 || responseCode >= 300) {
                return@withContext Failure(
                    Exception("HTTP $responseCode for $url")
                )
            }

            val inputStream: InputStream = connection.inputStream
            reader = BufferedReader(InputStreamReader(inputStream, java.nio.charset.StandardCharsets.UTF_8))
            val body = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                body.append(line)
                body.append('\n')
            }

            val json = JSONObject(body.toString())
            val resultArray = json.optJSONArray("result") ?: JSONArray()
            val items = mutableListOf<VidSrcItem>()
            for (i in 0 until resultArray.length()) {
                val obj = resultArray.optJSONObject(i) ?: continue
                items.add(VidSrcItem(
                    imdbId = obj.optString("imdb_id", null).takeIf { it.isNotBlank() },
                    tmdbId = obj.optString("tmdb_id", null).takeIf { it.isNotBlank() },
                    title = obj.optString("title", null).takeIf { it.isNotBlank() },
                    embedUrl = obj.optString("embed_url", null).takeIf { it.isNotBlank() },
                    embedUrlTmdb = obj.optString("embed_url_tmdb", null).takeIf { it.isNotBlank() },
                    quality = obj.optString("quality", null).takeIf { it.isNotBlank() },
                    timeAdded = obj.optString("time_added", null).takeIf { it.isNotBlank() }
                ))
            }
            val pages = json.optInt("pages", 1)

            success(VidSrcFeed(result = items, pages = pages))
        } catch (e: Exception) {
            Failure(e)
        } finally {
            reader?.use { } // close the reader
            connection?.disconnect()
        }
    }
}
