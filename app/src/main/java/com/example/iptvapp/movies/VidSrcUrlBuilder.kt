package com.example.iptvapp.movies

/**
 * Media type for VidSrc embed URLs.
 */
enum class MediaType {
    MOVIE,
    TV
}

/**
 * Builds VidSrc embed URLs from validated IMDb or TMDB IDs.
 *
 * Never accepts an arbitrary URL from the UI — only a validated ID and a
 * media type. The embed URL is always constructed from the fixed HTTPS
 * VidSrc hostname and the ID, so no feed-supplied URL is ever trusted.
 *
 * Validation rules:
 *  - IMDb IDs must match ^tt[0-9]+$
 *  - TMDB IDs must contain digits only
 */
object VidSrcUrlBuilder {

    private const val BASE_HOST = "https://vidsrc.sh"

    private val IMDB_ID_PATTERN = Regex("^tt[0-9]+$")
    private val TMDB_ID_PATTERN = Regex("^[0-9]+$")

    /**
     * Returns true when [id] is a valid IMDb ID (e.g. tt0111161).
     */
    fun isValidImdbId(id: String?): Boolean =
        IMDB_ID_PATTERN.matches(id.orEmpty())

    /**
     * Returns true when [id] is a valid TMDB ID (digits only, e.g. 111161).
     */
    fun isValidTmdbId(id: String?): Boolean =
        TMDB_ID_PATTERN.matches(id.orEmpty())

    /**
     * Returns true when [id] matches either the IMDb or TMDB pattern.
     */
    fun isValidId(id: String?): Boolean =
        isValidImdbId(id) || isValidTmdbId(id)

    /**
     * Constructs an embed URL for the given [type] and [id].
     *
     * [id] must be a valid IMDb ID (tt...) or TMDB ID (digits). Throws
     * [IllegalArgumentException] for any other input. Never constructs a
     * URL from an arbitrary string supplied by the UI.
     */
    fun buildEmbedUrl(type: MediaType, id: String): String {
        if (!isValidId(id)) {
            throw IllegalArgumentException("Invalid VidSrc ID: '$id'. Expected IMDb (tt...) or TMDB (digits).")
        }
        return when (type) {
            MediaType.MOVIE -> "$BASE_HOST/embed/movie/$id"
            MediaType.TV -> "$BASE_HOST/embed/tv/$id"
        }
    }

    /**
     * Selects the best available ID from a [VidSrcItem]: IMDb ID when
     * present and valid, otherwise the TMDB ID when present and valid.
     * Returns null when neither is usable.
     */
    fun bestId(item: VidSrcItem): String? =
        when {
            isValidImdbId(item.imdbId) -> item.imdbId
            isValidTmdbId(item.tmdbId) -> item.tmdbId
            else -> null
        }
}
