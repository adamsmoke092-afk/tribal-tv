package com.example.iptvapp.player

import androidx.media3.common.C
import androidx.media3.common.ParserException
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.example.iptvapp.MAX_LOADABLE_RETRY_COUNT
import com.example.iptvapp.RETRY_AFTER_MAX_MS
import com.example.iptvapp.RETRY_BASE_DELAY_MS
import com.example.iptvapp.RETRY_MAX_DELAY_MS
import java.io.FileNotFoundException

/**
 * Pure retry-classification helpers, extracted from
 * [BoundedLoadErrorHandlingPolicy] so they can be unit-tested without an
 * Android runtime or a real ExoPlayer.
 */

/** True for the HTTP status codes worth retrying: 408, 429 and every 5xx.
 * Everything else — notably 401/403/404 — is an access restriction or a
 * missing resource and must fail fast rather than be retried. */
fun isRetryableHttpCode(code: Int): Boolean =
    code == 408 || code == 429 || code in 500..599

/** Substrings that mark a network-ish IOException as transient. Used only as
 * a fallback for exception types we don't recognise explicitly. */
private val TRANSIENT_MESSAGE_HINTS = listOf(
    "timeout", "timed out", "timedout",
    "connection reset", "connection refused",
    "no route to host", "dns", "network",
    "stream closed", "unexpected end of stream", "premature end of stream"
)

/**
 * True when a load failure is a transient network interruption worth retrying.
 *
 * Deliberately conservative: anything not recognised as transient returns
 * false, so an unknown failure surfaces to the user instead of being retried
 * blindly. `ParserException`, `FileNotFoundException` and EOF are permanent.
 *
 * The cause chain is walked so a wrapped error (e.g. a
 * [HttpDataSource.InvalidResponseCodeException] nested inside an
 * `HttpDataSourceException`) is still classified.
 */
fun isTransientLoadFailure(error: Throwable?): Boolean {
    var current: Throwable? = error
    var depth = 0
    while (current != null && depth < 5) {
        val node = current
        when (node) {
            is HttpDataSource.InvalidResponseCodeException ->
                return isRetryableHttpCode(node.responseCode)
            is ParserException -> return false
            is FileNotFoundException -> return false
            is java.net.SocketTimeoutException -> return true
            is java.net.ConnectException -> return true
            is java.net.UnknownHostException -> return true
            is java.io.EOFException -> return false
        }
        current = node.cause
        depth++
    }
    // Unknown type: fall back to a message scan, which is the only signal left.
    val message = error?.message?.lowercase() ?: return false
    return TRANSIENT_MESSAGE_HINTS.any { message.contains(it) }
}

/**
 * Exponential backoff for the given 1-based error count: RETRY_BASE_DELAY_MS
 * doubled per attempt (1s, 2s, 4s, ...), capped at RETRY_MAX_DELAY_MS.
 */
fun retryDelayMs(errorCount: Int): Long {
    val exponent = (errorCount - 1).coerceAtLeast(0)
    // Guard the shift against absurd errorCount values.
    val factor = if (exponent >= 32) Long.MAX_VALUE else RETRY_BASE_DELAY_MS shl exponent
    return if (factor <= 0) RETRY_MAX_DELAY_MS else minOf(factor, RETRY_MAX_DELAY_MS)
}

/** Parses a Retry-After header value (seconds) into ms, capped at
 * RETRY_AFTER_MAX_MS. Returns 0 when absent, non-numeric, or negative
 * (HTTP-date form is not honoured — conservative: fall back to backoff). */
fun retryAfterMs(headerValue: String?): Long {
    val seconds = headerValue?.trim()?.toLongOrNull() ?: return 0L
    if (seconds <= 0) return 0L
    return (seconds * 1_000L).coerceAtMost(RETRY_AFTER_MAX_MS)
}

/**
 * Bounded, classification-aware retry policy wired through
 * `DefaultMediaSourceFactory.setLoadErrorHandlingPolicy()` (Media3 1.4.1).
 *
 * This replaces the old `RetryingDataSource`, which unsafely wrapped the
 * delegate DataSource and retried `DataSource.open`/`read` with Thread.sleep()
 * inside the media path. Retrying `read()` after the delegate's HTTP response
 * had already been consumed is fundamentally broken: the stream is either
 * consumed or the connection unusable, so a replayed read cannot resume from
 * the correct position.
 *
 * Media3 1.4.1's [LoadErrorHandlingPolicy] decides retries through two methods:
 *   - [getMinimumLoadableRetryCount] — the maximum number of retries per load
 *     task. Pinned to [MAX_LOADABLE_RETRY_COUNT] so the budget is finite.
 *   - [getRetryDelayMsFor] — the delay before the next retry, or
 *     [C.TIME_UNSET] to refuse to retry at all. This is where classification
 *     happens.
 *
 * Only transient failures are retried (see [isTransientLoadFailure]); HTTP 429
 * honours a valid `Retry-After` value (capped at
 * [RETRY_AFTER_MAX_MS]) in preference to the exponential backoff.
 *
 * Note on the stock default this replaces: Media3 1.4.1's
 * `DefaultLoadErrorHandlingPolicy` retries almost every IOException — including
 * HTTP 403 (a geoblock) — which is exactly the "makes existing problems worse"
 * behaviour this policy removes.
 */
internal class BoundedLoadErrorHandlingPolicy : DefaultLoadErrorHandlingPolicy() {

    override fun getMinimumLoadableRetryCount(dataType: Int): Int = MAX_LOADABLE_RETRY_COUNT

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val exception = loadErrorInfo.exception
        if (!isTransientLoadFailure(exception)) {
            // Permanent: surface it immediately (no wasted waiting).
            return C.TIME_UNSET
        }
        val retryAfter = retryAfterMs(retryAfterHeaderOf(exception))
        return if (retryAfter > 0) retryAfter else retryDelayMs(loadErrorInfo.errorCount)
    }

    /** Extracts the `Retry-After` response header (if any) from an HTTP error,
     * walking the cause chain to find the [HttpDataSource.InvalidResponseCodeException].
     * The lookup is case-insensitive because header casing is not guaranteed. */
    private fun retryAfterHeaderOf(error: Throwable?): String? {
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < 5) {
            val node = current
            if (node is HttpDataSource.InvalidResponseCodeException) {
                return node.headerFields.entries
                    .firstOrNull { it.key.equals("Retry-After", ignoreCase = true) }
                    ?.value
                    ?.firstOrNull()
            }
            current = node.cause
            depth++
        }
        return null
    }
}
