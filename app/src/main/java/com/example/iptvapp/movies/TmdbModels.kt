package com.example.iptvapp.movies

/**
 * A normalised movie or TV summary from TMDB.
 *
 * [posterPath] is TMDB's relative image path (e.g. "/abc.jpg"); use
 * [tmdbPosterUrl] to turn it into a full, cacheable URL.
 */
data class MediaSummary(
    val tmdbId: Int,
    val title: String,
    val year: String?,
    val posterPath: String?,
    val rating: Double?,
    val mediaType: MediaType
)

/** One page of TMDB results, with the total page count for Load-more. */
data class TmdbPage<T>(
    val items: List<T>,
    val page: Int,
    val totalPages: Int
)

/** A season of a TV show. Specials (season 0) are excluded by the parser. */
data class TvSeason(
    val seasonNumber: Int,
    val name: String,
    val episodeCount: Int
)

/** One episode within a TV season. */
data class TvEpisode(
    val episodeNumber: Int,
    val name: String,
    val airDate: String?
)

/** Builds a full TMDB image URL for a relative poster path. */
fun tmdbPosterUrl(path: String?, size: String = "w185"): String? =
    if (path.isNullOrBlank()) null else "https://image.tmdb.org/t/p/$size$path"