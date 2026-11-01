package com.example.iptvapp.epg

/**
 * The per-source and overall outcome of one EPG refresh, as data — no
 * formatting, no Android. The settings UI renders these values; the repository
 * produces them.
 */

/** How ONE guide source ended up. */
enum class EpgSourceStatus {
    /** Parsed, matched and written: this source's cache was replaced. */
    LOADED,

    /** Fetched and parsed fine, but nothing usable for this playlist. The
     *  previous cache is kept — an empty answer is not a reason to wipe it. */
    EMPTY,

    /** Hit the programme cap, so the candidate id set is incomplete. Quality
     *  aliases were disabled; exact matches were MERGED in without deleting
     *  anything. Never a replacement of the old dataset. */
    TRUNCATED,

    /** Fetch, decompress or parse failed. Previous cache kept untouched. */
    FAILED
}

/** Whether a source's result should be treated as usable cached data. */
fun EpgSourceStatus.keepsPreviousCache(): Boolean = this != EpgSourceStatus.LOADED

/**
 * One source's result. [sourceKey] is the irreversible digest — it is a storage
 * detail and is never shown; [redactedUrl] is what the user sees.
 * [unmatchedIds] and [suggestions] are pre-capped by the repository so a huge
 * guide cannot flood the dialog.
 */
data class EpgSourceResult(
    val sourceKey: String,
    val redactedUrl: String,
    val status: EpgSourceStatus,
    val programmeCount: Int = 0,
    val errorMessage: String? = null,
    val unmatchedIds: List<String> = emptyList(),
    val suggestions: Map<String, List<String>> = emptyMap()
)

/** Overall state the diagnostics line shows. */
enum class EpgRefreshState {
    NO_GUIDE_CONFIGURED,
    LOADING,
    LOADED,
    PARTIALLY_LOADED,
    FAILED,
    CACHED_DATA_AVAILABLE
}

/**
 * Decides the overall state. Kept separate from the counts so it can be unit
 * tested without Room or networking.
 *
 * Rules: while refreshing over existing rows the state stays
 * CACHED_DATA_AVAILABLE (the user still sees schedules); a refresh where every
 * configured source failed also falls back to CACHED_DATA_AVAILABLE when rows
 * survive, and only reports FAILED when there is nothing to show.
 */
fun epgRefreshState(
    sources: List<EpgSourceResult>,
    cachedProgrammeCount: Int,
    isLoading: Boolean
): EpgRefreshState = when {
    sources.isEmpty() && cachedProgrammeCount > 0 -> EpgRefreshState.CACHED_DATA_AVAILABLE
    sources.isEmpty() -> if (isLoading) EpgRefreshState.LOADING else EpgRefreshState.NO_GUIDE_CONFIGURED
    isLoading && cachedProgrammeCount > 0 -> EpgRefreshState.CACHED_DATA_AVAILABLE
    isLoading -> EpgRefreshState.LOADING
    sources.all { it.status == EpgSourceStatus.LOADED } -> EpgRefreshState.LOADED
    sources.any { it.status == EpgSourceStatus.LOADED } -> EpgRefreshState.PARTIALLY_LOADED
    cachedProgrammeCount > 0 -> EpgRefreshState.CACHED_DATA_AVAILABLE
    else -> EpgRefreshState.FAILED
}

/** Aggregate counts for the compact status line. */
data class EpgDiagnosticsSummary(
    val loaded: Int,
    val failed: Int,
    val truncated: Int,
    val empty: Int,
    val matchedChannels: Int
)

fun epgDiagnosticsSummary(
    sources: List<EpgSourceResult>,
    matchedChannels: Int
): EpgDiagnosticsSummary = EpgDiagnosticsSummary(
    loaded = sources.count { it.status == EpgSourceStatus.LOADED },
    failed = sources.count { it.status == EpgSourceStatus.FAILED },
    truncated = sources.count { it.status == EpgSourceStatus.TRUNCATED },
    empty = sources.count { it.status == EpgSourceStatus.EMPTY },
    matchedChannels = matchedChannels
)
