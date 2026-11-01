package com.example.iptvapp.epg

/**
 * The "now / next" pair for a channel. Both are nullable: a channel may
 * have no current programme (gap in the guide) and/or no upcoming one
 * (end of the cached window).
 */
data class NowNext(
    val current: EpgProgramme?,
    val next: EpgProgramme?
)

/**
 * Picks the current and next programme from a channel's programme list at
 * [nowMs]. Pure (no Room/Android), so it's JVM unit-testable:
 *  - current = the programme whose [startMs, stopMs) window contains now,
 *  - next    = the earliest programme that starts strictly after now.
 *
 * Tolerant of unsorted input and of overlapping/gapped guides.
 *
 * Several guides may describe the same channel, so two rows can share a start
 * time. Ties are broken by [EpgProgramme.source] ascending rather than by
 * whichever row happened to arrive first, so Now/Next is stable across runs
 * and across query-order changes. Which source wins a tie is otherwise
 * arbitrary — the point is only that it is deterministic.
 */
fun selectNowNext(programmes: List<EpgProgramme>, nowMs: Long): NowNext {
    val byStartThenSource = compareBy<EpgProgramme>({ it.startMs }, { it.source })
    val current = programmes
        .filter { nowMs >= it.startMs && nowMs < it.stopMs }
        .minWithOrNull(byStartThenSource)
    val next = programmes
        .filter { it.startMs > nowMs }
        .minWithOrNull(byStartThenSource)
    return NowNext(current, next)
}
