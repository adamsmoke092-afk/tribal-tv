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
 */
fun selectNowNext(programmes: List<EpgProgramme>, nowMs: Long): NowNext {
    val current = programmes
        .filter { nowMs >= it.startMs && nowMs < it.stopMs }
        .minByOrNull { it.startMs }
    val next = programmes
        .filter { it.startMs > nowMs }
        .minByOrNull { it.startMs }
    return NowNext(current, next)
}
