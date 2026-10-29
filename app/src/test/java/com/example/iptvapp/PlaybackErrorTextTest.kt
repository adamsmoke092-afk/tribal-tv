package com.example.iptvapp

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Tests the user-facing playback error copy: the right category per error
 * code, no internal detail leaked, and stream URLs (which may carry tokens)
 * never surfaced.
 */
class PlaybackErrorTextTest {

    private fun error(code: Int, cause: Throwable? = null): PlaybackException =
        PlaybackException("boom", cause, code)

    @Test
    fun `connection failure message tells the user to check the network`() {
        val message = playbackErrorMessage(error(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
        assertTrue(message.contains("connect", ignoreCase = true))
        assertTrue(message.contains("network", ignoreCase = true))
    }

    @Test
    fun `timeout message mentions timing out`() {
        assertTrue(
            playbackErrorMessage(error(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT))
                .contains("timed out", ignoreCase = true)
        )
        assertTrue(
            playbackErrorMessage(error(PlaybackException.ERROR_CODE_TIMEOUT))
                .contains("timed out", ignoreCase = true)
        )
    }

    @Test
    fun `unsupported format message is user facing`() {
        val message = playbackErrorMessage(error(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED))
        assertTrue(message.contains("format", ignoreCase = true))
    }

    @Test
    fun `bad http status without a parsed response still reads cleanly`() {
        val message = playbackErrorMessage(error(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS))
        assertFalse("should not print a null status", message.contains("null"))
        assertTrue(message.isNotBlank())
    }

    @Test
    fun `403 copy does not declare the channel permanently dead`() {
        // With no response cause we only get the generic line, but it must not
        // claim the channel is dead either way.
        val message = playbackErrorMessage(error(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS))
        assertFalse(message.contains("dead", ignoreCase = true))
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
            val message = playbackErrorMessage(error(code, SocketTimeoutException("raw detail")))
            assertTrue("empty message for code $code", message.isNotBlank())
            assertFalse("stack frame leaked for code $code: $message", message.contains("\tat "))
            assertFalse("java class name leaked for code $code: $message", message.contains("java.lang."))
            assertFalse("media3 internals leaked for code $code: $message", message.contains("androidx.media3"))
        }
    }

    @Test
    fun `a stream URL in the cause message is redacted`() {
        val cause = IOException("unexpected error loading http://host.example/live.m3u8?token=SECRET123")
        val message = playbackErrorMessage(error(999_999, cause))
        assertFalse("a token-bearing URL leaked: $message", message.contains("SECRET123"))
        assertFalse("a raw URL leaked: $message", message.contains("http://host.example"))
        assertTrue("the redaction marker is missing: $message", message.contains("<url>"))
    }
}
