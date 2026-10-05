package com.example.iptvapp

import com.example.iptvapp.playlist.M3uChannel

/**
 * Pure display helpers for the channel list (UI redesign, step 2).
 * No Android or Compose imports — plain JVM, directly unit-testable.
 */

/** What a row needs: cleaned name, optional quality token, free-text tags. */
data class ChannelDisplay(
    val cleanName: String,
    val quality: String?,
    val tags: List<String>
)

private val QUALITY_REGEX = Regex("""\((\d{3,4}p)\)""")
private val TAG_REGEX = Regex("""\[([^]]+)]""")
private val WHITESPACE = Regex("""\s+""")

/**
 * "eMovies (1080p) [Geo-blocked] [Not 24/7]" → cleanName "eMovies",
 * quality "1080p", tags ["Geo-blocked", "Not 24/7"]. Parentheses that are
 * NOT a quality token (e.g. "News (DStv 403)") stay in the name.
 */
fun parseDisplay(name: String): ChannelDisplay {
    val quality = QUALITY_REGEX.find(name)?.groupValues?.get(1)
    val tags = TAG_REGEX.findAll(name).map { it.groupValues[1].trim() }.toList()
    val cleanName = name
        .replace(QUALITY_REGEX, "")
        .replace(TAG_REGEX, "")
        .replace(WHITESPACE, " ")
        .trim()
        .ifEmpty { "Unknown channel" }
    return ChannelDisplay(cleanName, quality, tags)
}

/**
 * iptv-org group-title is ';'-separated multi-category
 * ("Entertainment;Family;General") — the channel is listed under EACH.
 * Null, empty, or all-blank segments → "Ungrouped".
 */
fun categoriesForGroupTitle(groupTitle: String?): List<String> =
    groupTitle.orEmpty()
        .split(';')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .ifEmpty { listOf("Ungrouped") }

/**
 * Groups channels by category; sorted alphabetically with "Ungrouped" last.
 * Returned as pairs so the screen can flatten into a single LazyColumn.
 */
fun groupChannels(channels: List<M3uChannel>): List<Pair<String, List<M3uChannel>>> =
    channels
        .flatMap { channel -> categoriesForGroupTitle(channel.groupTitle).map { it to channel } }
        .groupBy({ it.first }, { it.second })
        .toSortedMap(compareBy<String> { it == "Ungrouped" }.thenBy { it })
        .toList()
