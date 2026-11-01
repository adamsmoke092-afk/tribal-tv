package com.example.iptvapp.epg

/**
 * The single gate for turning an EPG source into something displayable.
 *
 * Public guide URLs routinely carry secrets that are NOT only in the query
 * string: userinfo ("user:pass@host"), and path segments such as
 * "/live/user/john/pass/abc123/guide.xml". So redaction works in three layers:
 *
 *  - the query string and fragment are dropped outright,
 *  - userinfo is dropped (only scheme://host survives),
 *  - each path segment is individually judged: a segment that looks like a
 *    credential (contains ':' or '=', matches a sensitive keyword, or is too
 *    long to be a human-readable name) becomes "<redacted>". A keyword segment
 *    also hides the segment that FOLLOWS it, which is how "user/john" and
 *    "pass/abc123" pairs are caught. Only the first few segments survive at
 *    all, so the display stays small on a phone.
 *
 * [sanitizeErrorMessage] runs the same treatment over exception text, because
 * java.net exceptions routinely embed the failing URL verbatim.
 *
 * Pure java.net/regex — JVM unit-testable.
 */

/** Path segments kept at most before the rest is collapsed to "…". */
private const val MAX_PATH_SEGMENTS = 3

/** Segments longer than this are treated as opaque tokens, not names. */
private const val MAX_HUMAN_SEGMENT = 24

private val SENSITIVE_KEYWORD =
    Regex("""(?i)(user|pass|pwd|token|secret|key|auth|cred|api|sig|session|account)""")

private val ANY_URL = Regex("""[a-zA-Z][a-zA-Z0-9+.\-]*://\S+""")

/** True when a path segment must not be shown verbatim. */
fun isSensitivePathSegment(segment: String): Boolean =
    segment.isBlank() ||
        segment.length > MAX_HUMAN_SEGMENT ||
        segment.contains(':') ||
        segment.contains('=') ||
        SENSITIVE_KEYWORD.containsMatchIn(segment)

/** Redacts a URL's path, segment by segment, keeping at most a few of them. */
private fun redactPath(path: String): String {
    val segments = path.split('/').filter { it.isNotEmpty() }
    if (segments.isEmpty()) return ""

    val out = ArrayList<String>(minOf(segments.size, MAX_PATH_SEGMENTS))
    var hideNext = false
    for (segment in segments) {
        if (out.size >= MAX_PATH_SEGMENTS) {
            out.add("…")
            break
        }
        if (hideNext) {
            out.add("<redacted>")
            hideNext = false
            continue
        }
        if (isSensitivePathSegment(segment)) {
            out.add("<redacted>")
            // "user/john", "pass/abc123": the keyword names the NEXT segment.
            hideNext = SENSITIVE_KEYWORD.containsMatchIn(segment)
        } else {
            out.add(segment)
        }
    }
    return "/" + out.joinToString("/")
}

/**
 * A display-safe rendering of an EPG URL: scheme, host and at most a few
 * non-sensitive path segments. Never the query, the fragment or userinfo.
 */
fun redactEpgUrl(url: String): String {
    val trimmed = url.trim()
    val withoutQuery = trimmed.substringBefore('?').substringBefore('#')
    return try {
        val uri = java.net.URI(withoutQuery)
        val scheme = uri.scheme
        val host = uri.host
        if (scheme == null || host.isNullOrBlank()) {
            withoutQuery.take(80)
        } else {
            scheme + "://" + host + redactPath(uri.rawPath.orEmpty())
        }
    } catch (e: Exception) {
        withoutQuery.take(80)
    }
}

/**
 * Exception text with every embedded URL redacted, newlines flattened and the
 * whole thing length-capped — safe to show in diagnostics and to log.
 */
fun sanitizeErrorMessage(message: String?): String {
    if (message.isNullOrBlank()) return ""
    val withoutUrls = ANY_URL.replace(message) { match -> redactEpgUrl(match.value) }
    val flattened = withoutUrls.replace(Regex("""[\r\n\t]+"""), " ").trim()
    return if (flattened.length > 160) flattened.take(157) + "…" else flattened
}