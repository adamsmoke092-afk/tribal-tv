package com.example.iptvapp.movies

/**
 * Top-level VidSrc feed response: a list of items plus total page count.
 */
data class VidSrcFeed(
    val result: List<VidSrcItem>,
    val pages: Int
)

/**
 * One item from the VidSrc latest movies / TV shows feed.
 * Fields are nullable because the feed may omit any of them.
 */
data class VidSrcItem(
    val imdbId: String?,
    val tmdbId: String?,
    val title: String?,
    val embedUrl: String?,
    val embedUrlTmdb: String?,
    val quality: String?,
    val timeAdded: String?
)
