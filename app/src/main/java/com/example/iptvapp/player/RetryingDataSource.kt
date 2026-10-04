package com.example.iptvapp.player

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import java.io.IOException

/**
 * Wraps a delegate DataSource (typically a tuned DefaultHttpDataSource) and
 * retries failed opens/reads with exponential backoff instead of failing
 * immediately.
 *
 * Rationale (SPEC §2.3): over a lossy VPN tunnel, a segment fetch failure is
 * often transient — a brief drop, not a dead link. ExoPlayer/Media3 doesn't
 * retry segment loads by default, so without this, one bad packet = a hard
 * player error.
 *
 * IMPORTANT (SPEC §4 audit finding): a 4xx HTTP response (e.g. a geoblock's
 * 403, per §2.3.2's SABC 1 case) is an access restriction, not a transient
 * network failure — retrying it just wastes time before the same failure.
 * Media3's HttpDataSource.InvalidResponseCodeException *is* an IOException,
 * so a naive "catch IOException, retry" would retry those too. This wrapper
 * explicitly fails fast on 4xx instead.
 */
class RetryingDataSource(
    private val delegate: DataSource,
    private val maxRetries: Int = 3,
    private val initialDelayMs: Long = 1000L // 1s -> 2s -> 4s
) : DataSource by delegate {

    override fun open(dataSpec: DataSpec): Long = withRetry { delegate.open(dataSpec) }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        withRetry { delegate.read(buffer, offset, length) }

    private fun <T> withRetry(block: () -> T): T {
        var attempt = 0
        var lastError: IOException? = null

        while (attempt <= maxRetries) {
            try {
                return block()
            } catch (e: IOException) {
                if (isPermanentFailure(e)) throw e // fail fast — e.g. geoblock, not a network hiccup

                lastError = e
                attempt++
                if (attempt > maxRetries) break
                sleepBackoff(attempt)
            }
        }
        throw lastError ?: IOException("RetryingDataSource: retry loop exited with no exception captured")
    }

    /** 4xx = the server actively rejected the request (auth/geoblock/not found) — retrying won't help. */
    private fun isPermanentFailure(e: IOException): Boolean {
        val httpError = e as? HttpDataSource.InvalidResponseCodeException ?: return false
        return httpError.responseCode in 400..499
    }

    private fun sleepBackoff(attempt: Int) {
        try {
            Thread.sleep(initialDelayMs * (1L shl (attempt - 1)))
        } catch (ignored: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    /**
     * Factory that wraps a delegate factory (e.g. a tuned DefaultHttpDataSource.Factory)
     * so every DataSource ExoPlayer creates goes through the retry wrapper.
     */
    class Factory(
        private val delegateFactory: DataSource.Factory,
        private val maxRetries: Int = 3,
        private val initialDelayMs: Long = 1000L
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            RetryingDataSource(delegateFactory.createDataSource(), maxRetries, initialDelayMs)
    }
}
