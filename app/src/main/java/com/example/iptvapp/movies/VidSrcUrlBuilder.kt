package com.example.iptvapp.movies

import java.net.URI

/**
 * Media type for VidSrc embed URLs.
 */
enum class MediaType {
    MOVIE,
    TV
}

/**
 * Builds and validates VidSrc embed URLs.
 *
 * vidsrc.wiki embeds take TMDB numeric IDs:
 *  - movie:  https://vidsrc.wiki/embed/movie/{tmdbId}/
 *  - TV:     https://vidsrc.wiki/embed/tv/{tmdbId}/{season}/{episode}/
 *
 * URLs are always constructed here from a validated numeric ID (never from
 * a feed-supplied string), and [isAllowedEmbedUrl] re-checks the exact
 * shape before the player loads anything.
 */
object VidSrcUrlBuilder {

    private const val BASE_HOST = "https://vidsrc.wiki"

    private val MOVIE_PATH = Regex("^/embed/movie/\\d+/?$")
    private val TV_PATH = Regex("^/embed/tv/\\d+/\\d+/\\d+/?$")

    /** Builds a movie embed URL for [tmdbId]. */
    fun buildMovieEmbedUrl(tmdbId: Int): String {
        require(tmdbId > 0) { "Invalid TMDB movie id: $tmdbId" }
        return "$BASE_HOST/embed/movie/$tmdbId/"
    }

    /** Builds a TV episode embed URL for [tmdbId], [season] and [episode]. */
    fun buildTvEmbedUrl(tmdbId: Int, season: Int, episode: Int): String {
        require(tmdbId > 0) { "Invalid TMDB TV id: $tmdbId" }
        require(season >= 1) { "Invalid season: $season" }
        require(episode >= 1) { "Invalid episode: $episode" }
        return "$BASE_HOST/embed/tv/$tmdbId/$season/$episode/"
    }

    /**
     * Returns true only for URLs this app itself could have built: https,
     * host exactly vidsrc.wiki, no user info / port / query / fragment, and
     * exactly the movie or TV path shape.
     *
     * Uses [java.net.URI] (pure JVM) so it stays unit-testable without
     * Robolectric and cannot be fooled by Android `Uri` quirks.
     */
    fun isAllowedEmbedUrl(url: String): Boolean {
        val uri = try {
            URI(url)
        } catch (e: Exception) {
            return false
        }
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        if (!uri.host.equals("vidsrc.wiki", ignoreCase = true)) return false
        if (uri.userInfo != null) return false
        if (uri.port != -1) return false
        if (uri.rawQuery != null) return false
        if (uri.rawFragment != null) return false
        val path = uri.path ?: return false
        return MOVIE_PATH.matches(path) || TV_PATH.matches(path)
    }
}