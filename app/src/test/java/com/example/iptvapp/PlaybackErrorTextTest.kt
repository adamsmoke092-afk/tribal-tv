package com.example.iptvapp

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Tests the user-facing playback error copy: the right category per error
 * code, no internal detail leaked, and stream URLs (which may carry tokens)
 * never surfaced.
 *
 * These call the pure [playbackErrorMessage] overload (error code + cause +
 * code name) rather than constructing a PlaybackException. Media3 1.4.1's
 * 3-arg constructor calls Clock.DEFAULT.elapsedRealtime(), which reaches
 * android.os.SystemClock.elapsedRealtime() — a stubbed Android method that
 * throws in a plain JVM unit test. Constructing one here would fail every test
 * in this class before any assertion ran, which is what previously happened.
 */
class PlaybackErrorTextTest {

    /**
     * @param codeName mirrors PlaybackException.getErrorCodeName(); the
     *        default matches Media3's output for an unrecognised code.
     */
    private fun message(
        code: Int,
        cause: Throwable? = null,
        codeName: String = "invalid error code"
    ): String = playbackErrorMessage(errorCode = code, cause = cause, errorCodeName = codeName)

    @Test
    fun `connection failure message tells the user to check the network`() {
        val text = message(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        assertTrue(text.contains("connect", ignoreCase = true))
        assertTrue(text.contains("network", ignoreCase = true))
    }

    @Test
    fun `timeout message mentions timing out`() {
        assertTrue(
            message(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)
                .contains("timed out", ignoreCase = true)
        )
        assertTrue(
            message(PlaybackException.ERROR_CODE_TIMEOUT)
                .contains("timed out", ignoreCase = true)
        )
    }

    @Test
    fun `unsupported format message is user facing`() {
        val text = message(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED)
        assertTrue(text.contains("format", ignoreCase = true))
    }

    @Test
    fun `bad http status without a parsed response still reads cleanly`() {
        val text = message(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)
        assertFalse("should not print a null status", text.contains("null"))
        assertTrue(text.isNotBlank())
    }

    @Test
    fun `403 copy does not declare the channel permanently dead`() {
        val text = httpStatusMessageFor(403)
        assertTrue(text.contains("blocked", ignoreCase = true))
        assertTrue("403 should invite a retry, not a verdict", text.contains("retry", ignoreCase = true))
        assertFalse(text.contains("dead", ignoreCase = true))
        assertFalse(text.contains("permanent", ignoreCase = true))
    }

    @Test
    fun `http status copy distinguishes access, missing, throttled and server errors`() {
        assertTrue(httpStatusMessageFor(null).contains("couldn't interpret", ignoreCase = true))
        assertTrue(httpStatusMessageFor(401).contains("authentication", ignoreCase = true))
        assertTrue(httpStatusMessageFor(404).contains("not found", ignoreCase = true))
        assertTrue(httpStatusMessageFor(408).contains("timed out", ignoreCase = true))
        assertTrue(httpStatusMessageFor(429).contains("refusing", ignoreCase = true))
        assertTrue(httpStatusMessageFor(503).contains("server", ignoreCase = true))
        assertEquals("The stream responded with HTTP status 418.", httpStatusMessageFor(418))
    }

    @Test
    fun `messages never leak stack frames, class names or media3 internals`() {
        val codes = listOf(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
            PlaybackException.ERROR_CODE_NOT_AVAILABLE_IN_REGION,
            999_999 // an unrecognised code, exercising the fallback branch
        )
        for (code in codes) {
            val text = message(code, SocketTimeoutException("raw detail"))
            assertTrue("empty message for code $code", text.isNotBlank())
            assertFalse("stack frame leaked for code $code: $text", text.contains("\tat "))
            assertFalse("java class name leaked for code $code: $text", text.contains("java.lang."))
            assertFalse("media3 internals leaked for code $code: $text", text.contains("androidx.media3"))
        }
    }

    @Test
    fun `a stream URL in the cause message is redacted`() {
        val cause = IOException("unexpected error loading http://host.example/live.m3u8?token=SECRET123")
        val text = message(999_999, cause)
        assertFalse("a token-bearing URL leaked: $text", text.contains("SECRET123"))
        assertFalse("a raw URL leaked: $text", text.contains("http://host.example"))
        assertTrue("the redaction marker is missing: $text", text.contains("<url>"))
    }

    @Test
    fun `fallback includes the player error code name but not the raw cause class`() {
        val text = message(
            code = 999_999,
            cause = IOException("boom"),
            codeName = "custom error code"
        )
        assertTrue(text.contains("Playback failed: custom error code.", ignoreCase = true))
        assertFalse(text.contains("IOException"))
    }
}