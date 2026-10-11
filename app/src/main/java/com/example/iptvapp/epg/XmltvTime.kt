package com.example.iptvapp.epg

import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Parses an XMLTV timestamp into epoch millis. XMLTV uses
 * "YYYYMMDDHHMMSS ZZZZ", e.g. "20260101183000 +0200"; the offset may be
 * absent (then UTC is assumed, per the XMLTV spec's fallback) and some
 * feeds use "+02:00" or just the 14 digits.
 *
 * Pure java.time (available on API 26+, the app's floor) so it's directly
 * JVM unit-testable. Returns null on anything malformed rather than
 * guessing a time — a wrong programme time is worse than none.
 */
fun parseXmltvInstant(raw: String?): Long? {
    val s = raw?.trim().orEmpty()
    if (s.length < 14) return null
    val digits = s.substring(0, 14)
    if (!digits.all { it.isDigit() }) return null

    return try {
        val year = digits.substring(0, 4).toInt()
        val month = digits.substring(4, 6).toInt()
        val day = digits.substring(6, 8).toInt()
        val hour = digits.substring(8, 10).toInt()
        val minute = digits.substring(10, 12).toInt()
        val second = digits.substring(12, 14).toInt()

        val offsetPart = s.substring(14).trim()
        val offset = if (offsetPart.isEmpty()) {
            ZoneOffset.UTC
        } else {
            // ZoneOffset.of accepts "+0200", "+02:00" and "+02".
            ZoneOffset.of(offsetPart)
        }

        OffsetDateTime.of(year, month, day, hour, minute, second, 0, offset)
            .toInstant()
            .toEpochMilli()
    } catch (e: Exception) {
        null
    }
}
