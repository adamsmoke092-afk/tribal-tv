package com.example.iptvapp.movies

import kotlinx.coroutines.CancellationException
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

/**
 * Fetches the VidSrc latest movies and TV shows feeds over HTTPS.
 *
 * All network I/O runs off the main thread: the caller invokes from a
 * coroutine and [fetchFeed] hops to [dispatcher]. Each feed request uses
 * bounded timeouts, closes its streams, and disconnects on completion or
 * failure. Malformed JSON and missing fields are handled gracefully —
 * absent, JSON-null, or blank optional fields become null.
 *
 * Duplicate entries are avoided: each page request returns its own list,
 * and the caller (ViewModel) is responsible for deduping across pages by
 * ID. This repository does not automatically paginate — the caller
 * requests one page at a time.
 */
class VidSrcRepository(private val dispatcher: CoroutineDispatcher = Dispatchers.IO) {

    companion object {
        private const val BASE = "https://vidsrc.sh"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 15_000

        /**
         * Parses a VidSrc feed JSON body into a [VidSrcFeed]. Pure — no
         * I/O — so it can be unit-tested directly.
         *
         * @throws org.json.JSONException when [json] is not valid JSON.
         */
        fun parseFeed(json: String): VidSrcFeed {
            val root = JSONObject(json)
            val resultArray = root.optJSONArray("result") ?: JSONArray()
            val items = ArrayList<VidSrcItem>(resultArray.length())
            for (i in 0 until resultArray.length()) {
                val obj = resultArray.optJSONObject(i) ?: continue
                items.add(
                    VidSrcItem(
                        imdbId = obj.optNonBlank("imdb_id"),
                        tmdbId = obj.optNonBlank("tmdb_id"),
                        title = obj.optNonBlank("title"),
                        embedUrl = obj.optNonBlank("embed_url"),
                        embedUrlTmdb = obj.optNonBlank("embed_url_tmdb"),
                        quality = obj.optNonBlank("quality"),
                        timeAdded = obj.optNonBlank("time_added")
                    )
                )
            }
            return VidSrcFeed(result = items, pages = root.optInt("pages", 1))
        }

        /**
         * Reads [key] as a trimmed string, returning null when it is
         * absent, JSON null, the literal string "null", or blank.
         *
         * Deliberately avoids `optString(name, null)`: the Java fallback
         * parameter is a non-null String, so passing null yields a
         * "Nothing? vs String" type warning — and `optString(name,
         * fallback)` returns the literal "null" for a present JSON null.
         */
        private fun JSONObject.optNonBlank(key: String): String? {
            if (!has(key) || isNull(key)) return null
            val value = optString(key).trim()
            return value.takeIf { it.isNotEmpty() && it != "null" }
        }
    }

    /** Fetches one page of the latest movies feed. */
    suspend fun fetchLatestMovies(page: Int = 1): Result<VidSrcFeed> =
        fetchFeed("$BASE/movies/latest/page-$page.json")

    /** Fetches one page of the latest TV shows feed. */
    suspend fun fetchLatestTvShows(page: Int = 1): Result<VidSrcFeed> =
        fetchFeed("$BASE/tvshows/latest/page-$page.json")

    /**
     * Fetches a single VidSrc feed page, parses the JSON, and returns a
     * [VidSrcFeed]. Network failures, HTTP errors, and malformed JSON are
     * all captured as [Result.failure].
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
                return@withContext Result.failure<VidSrcFeed>(
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

            Result.success(parseFeed(body.toString()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure<VidSrcFeed>(e)
        } finally {
            reader?.use { }
            connection?.disconnect()
        }
    }
}
