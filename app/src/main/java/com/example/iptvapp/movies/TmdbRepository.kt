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
 * Fetches movie / TV listings and TV season data from the TMDB v3 API,
 * authenticated with a v4 read access token (Bearer header).
 *
 * All network I/O runs off the main thread: the caller invokes from a
 * coroutine and [get] hops to [dispatcher]. Each request uses bounded
 * timeouts, closes its stream and disconnects.
 *
 * The JSON parsers are pure `companion` functions so they can be tested
 * under Robolectric without any network.
 */
class TmdbRepository(private val dispatcher: CoroutineDispatcher = Dispatchers.IO) {

    companion object {
        /**
         * TMDB v4 read access token. Hardcoded for this private,
         * personal-use app only — never publish this repo publicly.
         */
        const val READ_TOKEN =
            "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiI0M2YxNmE4MzExNWFmMzYxMmE5NzE4MDczNTk4MmUxMyIsIm5iZiI6MTc5MzY1MzQ0MC41NzksInN1YiI6IjZhZThmYWMwOWFiN2MzNWY3MWQ1ZTNjZSIsInNjb3BlcyI6WyJhcGlfcmVhZCJdLCJ2ZXJzaW9uIjoxfQ.ZwMX1zqyL1tsi7dZel9QGvglo26wn6QDIMbNFiXSWh"

        private const val BASE = "https://api.themoviedb.org/3"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 15_000

        /** Parses a `/trending/movie/day` response. */
        fun parseMoviePage(json: String): TmdbPage<MediaSummary> =
            parsePage(json, MediaType.MOVIE)

        /** Parses a `/trending/tv/day` response. */
        fun parseTvPage(json: String): TmdbPage<MediaSummary> =
            parsePage(json, MediaType.TV)

        /** Parses a `/tv/{id}` response into its season list. */
        fun parseTvSeasons(json: String): List<TvSeason> {
            val root = JSONObject(json)
            val array = root.optJSONArray("seasons") ?: JSONArray()
            val seasons = ArrayList<TvSeason>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val number = obj.optInt("season_number", -1)
                if (number < 1) continue
                seasons.add(
                    TvSeason(
                        seasonNumber = number,
                        name = obj.optNonBlank("name") ?: "Season $number",
                        episodeCount = obj.optInt("episode_count", 0)
                    )
                )
            }
            return seasons.sortedBy { it.seasonNumber }
        }

        /** Parses a `/tv/{id}/season/{n}` response into its episode list. */
        fun parseTvEpisodes(json: String): List<TvEpisode> {
            val root = JSONObject(json)
            val array = root.optJSONArray("episodes") ?: JSONArray()
            val episodes = ArrayList<TvEpisode>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val number = obj.optInt("episode_number", -1)
                if (number < 1) continue
                episodes.add(
                    TvEpisode(
                        episodeNumber = number,
                        name = obj.optNonBlank("name") ?: "Episode $number",
                        airDate = obj.optNonBlank("air_date")
                    )
                )
            }
            return episodes.sortedBy { it.episodeNumber }
        }

        private fun parsePage(json: String, type: MediaType): TmdbPage<MediaSummary> {
            val root = JSONObject(json)
            val array = root.optJSONArray("results") ?: JSONArray()
            val items = ArrayList<MediaSummary>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optInt("id", 0)
                if (id <= 0) continue

                val title = if (type == MediaType.MOVIE) {
                    obj.optNonBlank("title")
                } else {
                    obj.optNonBlank("name")
                } ?: "Untitled"

                val date = if (type == MediaType.MOVIE) {
                    obj.optNonBlank("release_date")
                } else {
                    obj.optNonBlank("first_air_date")
                }
                val year = date?.takeIf { it.length >= 4 }?.substring(0, 4)

                val rating = obj.optDouble("vote_average", Double.NaN)
                    .takeUnless { it.isNaN() }
                    ?.takeIf { it > 0.0 }

                items.add(
                    MediaSummary(
                        tmdbId = id,
                        title = title,
                        year = year,
                        posterPath = obj.optNonBlank("poster_path"),
                        rating = rating,
                        mediaType = type
                    )
                )
            }

            val page = root.optInt("page", 1).coerceAtLeast(1)
            val totalPages = root.optInt("total_pages", page).coerceAtLeast(1)
            return TmdbPage(items = items, page = page, totalPages = totalPages)
        }

        /**
         * Reads [key] as a trimmed string, returning null when it is
         * absent, JSON null, the literal "null", or blank. Mirrors the
         * JSON-null handling used elsewhere in the movies package.
         */
        private fun JSONObject.optNonBlank(key: String): String? {
            if (!has(key) || isNull(key)) return null
            val value = optString(key).trim()
            return value.takeIf { it.isNotEmpty() && it != "null" }
        }
    }

    /** Trending movies for today, paginated. */
    suspend fun fetchTrendingMovies(page: Int = 1): Result<TmdbPage<MediaSummary>> =
        get("$BASE/trending/movie/day?page=$page") { parseMoviePage(it) }

    /** Trending TV shows for today, paginated. */
    suspend fun fetchTrendingTv(page: Int = 1): Result<TmdbPage<MediaSummary>> =
        get("$BASE/trending/tv/day?page=$page") { parseTvPage(it) }

    /** Seasons for a TV show, from `/tv/{id}`. */
    suspend fun fetchSeasons(tmdbId: Int): Result<List<TvSeason>> =
        get("$BASE/tv/$tmdbId") { parseTvSeasons(it) }

    /** Episodes for one season, from `/tv/{id}/season/{n}`. */
    suspend fun fetchEpisodes(tmdbId: Int, season: Int): Result<List<TvEpisode>> =
        get("$BASE/tv/$tmdbId/season/$season") { parseTvEpisodes(it) }

    private suspend fun <T> get(url: String, parse: (String) -> T): Result<T> =
        withContext(dispatcher) {
            var connection: HttpURLConnection? = null
            var reader: BufferedReader? = null
            try {
                connection = java.net.URL(url).openConnection() as HttpURLConnection
                connection.apply {
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Authorization", "Bearer $READ_TOKEN")
                    instanceFollowRedirects = true
                }

                val responseCode = connection.responseCode
                if (responseCode < 200 || responseCode >= 300) {
                    return@withContext Result.failure<T>(Exception("HTTP $responseCode"))
                }

                val inputStream: InputStream = connection.inputStream
                reader = BufferedReader(
                    InputStreamReader(inputStream, java.nio.charset.StandardCharsets.UTF_8)
                )
                val body = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    body.append(line)
                    body.append('\n')
                }

                Result.success(parse(body.toString()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure<T>(e)
            } finally {
                reader?.use { }
                connection?.disconnect()
            }
        }
}