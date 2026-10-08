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

/**
 * Case-insensitive "contains" filter over each channel's clean name and
 * its category names. A blank query returns the list unchanged. Meant for
 * live search-as-you-type; cheap enough to recompute per keystroke at
 * playlist scale (hundreds of channels).
 */
fun filterChannels(query: String, channels: List<M3uChannel>): List<M3uChannel> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return channels
    return channels.filter { channel ->
        parseDisplay(channel.name).cleanName.lowercase().contains(needle) ||
            categoriesForGroupTitle(channel.groupTitle).any { it.lowercase().contains(needle) }
    }
}

/** True if the channel carries the given tag, ignoring case. */
fun hasTag(display: ChannelDisplay, tag: String): Boolean =
    display.tags.any { it.equals(tag, ignoreCase = true) }

/** "720p" and up counts as HD. Unknown/unparsed quality is not HD. */
fun isHd(quality: String?): Boolean {
    val height = quality?.removeSuffix("p")?.toIntOrNull() ?: return false
    return height >= 720
}

/**
 * Applies the user's list-visibility filters. Every filter is opt-in, so
 * with all three off this returns the input list unchanged (same
 * instance — cheap fast path for the common case).
 *
 * hdOnly only drops channels whose quality token parsed AND is below 720p;
 * a channel with no quality token is kept, since we can't know its
 * resolution and dropping it would hide most of the playlist.
 */
fun applyFilters(
    channels: List<M3uChannel>,
    hideGeoBlocked: Boolean,
    hideNot24x7: Boolean,
    hdOnly: Boolean
): List<M3uChannel> {
    if (!hideGeoBlocked && !hideNot24x7 && !hdOnly) return channels
    return channels.filter { channel ->
        val display = parseDisplay(channel.name)
        when {
            hideGeoBlocked && hasTag(display, "Geo-blocked") -> false
            hideNot24x7 && hasTag(display, "Not 24/7") -> false
            hdOnly && display.quality != null && !isHd(display.quality) -> false
            else -> true
        }
    }
}

/**
 * "Updated X ago" label for the channel list, from the wall-clock ms of
 * the last successful playlist fetch. Pure clock math — JVM-testable; a
 * non-positive timestamp (never refreshed) or a future one (clock skew)
 * degrades gracefully instead of showing a lie.
 */
fun formatLastRefreshText(nowMs: Long, refreshedMs: Long): String? {
    if (refreshedMs <= 0) return null
    val minutes = (nowMs - refreshedMs) / 60_000
    return when {
        minutes < 1 -> "Updated just now"
        minutes < 60 -> "Updated ${minutes}m ago"
        minutes < 24 * 60 -> "Updated ${minutes / 60}h ago"
        else -> "Updated ${minutes / (24 * 60)}d ago"
    }
}
