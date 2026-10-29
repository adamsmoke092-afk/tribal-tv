package com.example.iptvapp.player

import androidx.media3.common.ParserException
import com.example.iptvapp.RETRY_AFTER_MAX_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.EOFException
import java.io.FileNotFoundException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Tests for the pure retry-classification and backoff logic behind
 * BoundedLoadErrorHandlingPolicy. No Android runtime or ExoPlayer needed.
 */
class RetryPolicyTest {

    // ---- HTTP status classification ----

    @Test
    fun `408 and 429 are retryable`() {
        assertTrue(isRetryableHttpCode(408))
        assertTrue(isRetryableHttpCode(429))
    }

    @Test
    fun `server 5xx errors are retryable`() {
        assertTrue(isRetryableHttpCode(500))
        assertTrue(isRetryableHttpCode(502))
        assertTrue(isRetryableHttpCode(503))
        assertTrue(isRetryableHttpCode(599))
    }

    @Test
    fun `access restrictions and missing resources are not retryable`() {
        assertFalse(isRetryableHttpCode(401))
        assertFalse(isRetryableHttpCode(403))
        assertFalse(isRetryableHttpCode(404))
        assertFalse(isRetryableHttpCode(410))
    }

    @Test
    fun `other 4xx are not retryable`() {
        assertFalse(isRetryableHttpCode(400))
        assertFalse(isRetryableHttpCode(402))
        assertFalse(isRetryableHttpCode(418))
        assertFalse(isRetryableHttpCode(499))
    }

    @Test
    fun `success and redirect codes are not retryable`() {
        assertFalse(isRetryableHttpCode(200))
        assertFalse(isRetryableHttpCode(302))
    }

    // ---- transient vs permanent exception classification ----

    @Test
    fun `socket timeout is transient`() {
        assertTrue(isTransientLoadFailure(SocketTimeoutException()))
    }

    @Test
    fun `connection refused is transient`() {
        assertTrue(isTransientLoadFailure(ConnectException("Connection refused")))
    }

    @Test
    fun `unknown host is transient`() {
        assertTrue(isTransientLoadFailure(UnknownHostException("example.com")))
    }

    @Test
    fun `EOF is permanent (truncated stream)`() {
        assertFalse(isTransientLoadFailure(EOFException()))
    }

    @Test
    fun `file not found is permanent`() {
        assertFalse(isTransientLoadFailure(FileNotFoundException()))
    }

    @Test
    fun `parser failure is permanent`() {
        val parserError = ParserException.createForMalformedContainer("bad media", null)
        assertFalse(isTransientLoadFailure(parserError))
    }

    @Test
    fun `unknown exception is permanent by default`() {
        assertFalse(isTransientLoadFailure(IOException("something entirely unexpected")))
    }

    @Test
    fun `null is permanent`() {
        assertFalse(isTransientLoadFailure(null))
    }

    @Test
    fun `a transient cause is found through a wrapper`() {
        val wrapped = IOException("outer wrapper", SocketTimeoutException("inner timeout"))
        assertTrue(isTransientLoadFailure(wrapped))
    }

    @Test
    fun `a permanent cause is respected even when wrapped`() {
        // The wrapper is unknown, but its cause is a ParserException.
        val wrapped = IOException("outer", ParserException.createForMalformedContainer("bad", null))
        assertFalse(isTransientLoadFailure(wrapped))
    }

    @Test
    fun `message fallback flags transient network hints`() {
        assertTrue(isTransientLoadFailure(IOException("read timed out")))
        assertTrue(isTransientLoadFailure(IOException("Connection reset by peer")))
        assertTrue(isTransientLoadFailure(IOException("No route to host")))
        assertFalse(isTransientLoadFailure(IOException("unexpected payload shape")))
    }

    // ---- exponential backoff ----

    @Test
    fun `backoff doubles per attempt then caps`() {
        assertEquals(1_000L, retryDelayMs(1))
        assertEquals(2_000L, retryDelayMs(2))
        assertEquals(4_000L, retryDelayMs(3))
        assertEquals(8_000L, retryDelayMs(4))
        // capped at RETRY_MAX_DELAY_MS
        assertEquals(8_000L, retryDelayMs(5))
        assertEquals(8_000L, retryDelayMs(12))
        assertEquals(8_000L, retryDelayMs(40))
    }

    @Test
    fun `backoff treats non-positive error counts as the first attempt`() {
        assertEquals(1_000L, retryDelayMs(0))
        assertEquals(1_000L, retryDelayMs(-3))
    }

    // ---- Retry-After ----

    @Test
    fun `retry-after seconds are converted to milliseconds`() {
        assertEquals(5_000L, retryAfterMs("5"))
        assertEquals(1_000L, retryAfterMs(" 1 "))
    }

    @Test
    fun `retry-after is capped`() {
        assertEquals(RETRY_AFTER_MAX_MS, retryAfterMs("99999"))
    }

    @Test
    fun `retry-after ignores absent or invalid values`() {
        assertEquals(0L, retryAfterMs(null))
        assertEquals(0L, retryAfterMs(""))
        assertEquals(0L, retryAfterMs("Wed, 21 Oct 2015 07:28:00 GMT"))
        assertEquals(0L, retryAfterMs("-5"))
        assertEquals(0L, retryAfterMs("0"))
    }
}
