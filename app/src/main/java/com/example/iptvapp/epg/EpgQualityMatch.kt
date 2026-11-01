package com.example.iptvapp.epg

/**
 * Loose ("quality alias") matching between a playlist tvg-id and the channel
 * ids a guide declares.
 *
 * Rules, locked with the user on 2026-10-30 — deviate from these and channels
 * silently get the wrong guide data:
 *
 *  - Exact, case-SENSITIVE id equality always wins and short-circuits
 *    everything else.
 *  - Ids are split at '@' ONLY. Everything before the '@' is the base id and
 *    is compared case-sensitively, so a country code baked into the id
 *    ("superSport.za") is part of the base, never a suffix.
 *  - Each side's suffix must be either bare or exactly one of
 *    [QUALITY_SUFFIXES], compared case-insensitively. Anything else
 *    ("@East", "@West", "@Plus1", "@Africa") is a regional/time-shift token
 *    and is NOT a quality alias: those never match loosely.
 *  - A loose match is returned only when EXACTLY ONE compatible candidate
 *    exists in that guide. With several candidates the result reports them as
 *    unconfirmed suggestions and matches nothing — we never guess.
 *  - Ids are trimmed on both sides and their original case is preserved.
 *    [normalizeTvgId] is the single normalization used both when storing a
 *    programme and when looking one up, so the two can never disagree.
 */

/** The only suffix words treated as quality aliases. Compared case-insensitively. */
val QUALITY_SUFFIXES: Set<String> = setOf("SD", "HD", "FHD", "UHD", "4K")

/** The only separator ids are split at. */
const val ID_SUFFIX_SEPARATOR: Char = '@'

/**
 * The one normalization applied to a playlist tvg-id before it is used to
 * store a programme and before it is used to look one up.
 */
fun normalizeTvgId(id: String): String = id.trim()

/** An id split into its base and optional suffix at the first '@'. */
data class SplitId(val base: String, val suffix: String?)

/**
 * Splits [id] at the FIRST '@'. No '@' means the whole id is the base and the
 * suffix is null (bare). "a@b@c" yields base "a" and suffix "b@c", which is
 * not a quality word — deliberately rejected rather than guessed at.
 */
fun splitIdSuffix(id: String): SplitId {
    val trimmed = normalizeTvgId(id)
    val at = trimmed.indexOf(ID_SUFFIX_SEPARATOR)
    return if (at < 0) {
        SplitId(trimmed, null)
    } else {
        SplitId(trimmed.substring(0, at), trimmed.substring(at + 1))
    }
}

/** True when [suffix] is bare (null/blank) or a recognised quality word. */
fun isQualitySuffix(suffix: String?): Boolean {
    if (suffix == null) return true
    if (suffix.isBlank()) return true
    return QUALITY_SUFFIXES.any { it.equals(suffix, ignoreCase = true) }
}

/**
 * True when [guideId] may loosely stand for [playlistId]: same case-sensitive
 * base id and both suffixes bare-or-quality. Ids equal to each other are also
 * "aliases" by this test, but callers check exact equality first.
 */
fun isQualityAlias(playlistId: String, guideId: String): Boolean {
    val p = splitIdSuffix(playlistId)
    val g = splitIdSuffix(guideId)
    if (p.base != g.base) return false
    return isQualitySuffix(p.suffix) && isQualitySuffix(g.suffix)
}

/**
 * How one playlist id resolved against one guide's declared channel ids.
 *
 * @param playlistId    the normalized playlist id this describes
 * @param matchedGuideId the guide id to use, or null when nothing matched
 * @param exact         true when [matchedGuideId] came from exact equality
 * @param suggestions   >1 compatible candidates, sorted; never auto-picked
 */
data class QualityResolution(
    val playlistId: String,
    val matchedGuideId: String?,
    val exact: Boolean,
    val suggestions: List<String>
) {
    val isAmbiguous: Boolean get() = matchedGuideId == null && suggestions.isNotEmpty()
    val isUnmatched: Boolean get() = matchedGuideId == null && suggestions.isEmpty()
}

/**
 * Resolves one playlist id against the ids declared by ONE guide.
 *
 * Exact match wins; otherwise a loose match is taken only when it is unique.
 * Several compatible candidates produce suggestions instead of a guess.
 *
 * @param allowAliases when false, ONLY exact matching runs and no suggestions
 *        are produced. Used when the candidate id set is known to be
 *        incomplete (a truncated guide): uniqueness cannot be established, so
 *        a quality alias must never be assumed. Exact equality is still exact.
 */
fun resolveGuideId(
    playlistId: String,
    guideIds: Collection<String>,
    allowAliases: Boolean = true
): QualityResolution {
    val wanted = normalizeTvgId(playlistId)
    val candidates = guideIds
        .map { normalizeTvgId(it) }
        .filter { it.isNotEmpty() }
        .distinct()

    val exact = candidates.firstOrNull { it == wanted }
    if (exact != null) {
        return QualityResolution(wanted, matchedGuideId = exact, exact = true, suggestions = emptyList())
    }
    if (!allowAliases) {
        // The candidate set is incomplete: any alias here would be a guess.
        return QualityResolution(wanted, matchedGuideId = null, exact = false, suggestions = emptyList())
    }

    val aliases = candidates.filter { isQualityAlias(wanted, it) }.sorted()
    return when {
        aliases.isEmpty() ->
            QualityResolution(wanted, matchedGuideId = null, exact = false, suggestions = emptyList())
        aliases.size == 1 ->
            QualityResolution(wanted, matchedGuideId = aliases[0], exact = false, suggestions = emptyList())
        else ->
            QualityResolution(wanted, matchedGuideId = null, exact = false, suggestions = aliases)
    }
}

/** Batch result for a whole playlist against one guide's ids. */
data class PlaylistMatchReport(val resolutions: List<QualityResolution>) {
    val matched: List<QualityResolution> get() = resolutions.filter { it.matchedGuideId != null }
    val ambiguous: List<QualityResolution> get() = resolutions.filter { it.isAmbiguous }
    val unmatched: List<QualityResolution> get() = resolutions.filter { it.isUnmatched }

    /**
     * The playlist id a programme must be STORED under, given the guide id it
     * was read with. Null when that guide id maps to no playlist channel, in
     * which case the programme is dropped.
     */
    fun playlistIdFor(rawGuideId: String): String? {
        val guideId = normalizeTvgId(rawGuideId)
        return resolutions.firstOrNull { it.matchedGuideId == guideId }?.playlistId
    }
}

/**
 * Resolves every playlist id against the ids of one guide. Deterministic:
 * resolutions keep the input order, suggestions are sorted.
 *
 * @param allowAliases false when the guide's candidate id set is incomplete
 *        (truncated), which disables quality-alias matching entirely.
 */
fun matchPlaylistIds(
    playlistIds: Collection<String>,
    guideIds: Collection<String>,
    allowAliases: Boolean = true
): PlaylistMatchReport {
    val guide = guideIds.map { normalizeTvgId(it) }.filter { it.isNotEmpty() }
    return PlaylistMatchReport(
        playlistIds.map { resolveGuideId(it, guide, allowAliases) }
    )
}
