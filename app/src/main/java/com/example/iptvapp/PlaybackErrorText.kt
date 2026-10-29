package com.example.iptvapp

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource

/**
 * Human-readable playback failure text for the PlayerScreen card.
 *
 * Rules:
 *  - No raw stack traces and no internal class names. Only the mapped error
 *    category plus the player's own code name for the unknown bucket.
 *  - A short cause message is appended for the unknown bucket only, with any
 *    URL stripped (a stream URL may carry a token or credentials in its query
 *    string — never surfaced, and never logged).
 *  - HTTP 403/404 are NOT described as "permanently dead": they may be
 *    transient access errors or provider-side changes, so the copy asks the
 *    user to retry where that is plausible. The dead-channel cache is only
 *    populated by a genuine playback error, not by transient buffering.
 *
 * Every error code referenced here is verified to exist in
 * androidx.media3.common.PlaybackException for Media3 1.4.1.
 */
fun playbackErrorMessage(error: PlaybackException): String {
    return when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
            "Can't connect to the stream — check your network or VPN connection."

        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_TIMEOUT ->
            "Connection timed out — the stream or your network may be temporarily unavailable."

        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
            httpStatusMessage(error)

        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
            "The stream file isn't available at this address."

        PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE ->
            "The server returned an unexpected content type for this stream."

        PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED ->
            "The provider requires a plain HTTP connection, which this app blocks."

        PlaybackException.ERROR_CODE_IO_NO_PERMISSION ->
            "Permission was denied while reading this stream."

        PlaybackException.ERROR_CODE_IO_UNSPECIFIED ->
            "The stream couldn't be loaded — the network or the provider returned an error."

        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED ->
            "This stream's format isn't supported or is malformed."

        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED ->
            "This device can't decode this stream's format."

        PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED ->
            "An audio problem stopped this stream."

        PlaybackException.ERROR_CODE_NOT_AVAILABLE_IN_REGION ->
            "This stream isn't available in your region."

        PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW ->
            "The live stream fell too far behind — retrying should rejoin it."

        else -> buildString {
            append("Playback failed: ${error.errorCodeName}.")
            val detail = sanitisedCauseMessage(error)
            if (detail.isNotBlank()) append(' ').append(detail)
        }
    }
}

/** Maps an HTTP-status failure (Media3's ERROR_CODE_IO_BAD_HTTP_STATUS, whose
 * cause is an HttpDataSource.InvalidResponseCodeException) to clear copy. */
private fun httpStatusMessage(error: PlaybackException): String {
    val code = httpStatusCode(error.cause)
    return when {
        code == null -> "The stream returned an error the app couldn't interpret."
        code == 401 -> "The provider requires authentication."
        code == 403 -> "The provider blocked this stream (often a geoblock — retry from the right network)."
        code == 404 -> "The stream was not found at this address."
        code == 408 -> "The provider's request timed out (please retry)."
        code == 429 -> "The provider is temporarily refusing requests (please retry)."
        code in 500..599 -> "The provider's server returned an error (please retry)."
        else -> "The stream responded with HTTP status $code."
    }
}

/** Finds an InvalidResponseCodeException in the cause chain (it is sometimes
 * wrapped) and returns its response code, or null. */
private fun httpStatusCode(cause: Throwable?): Int? {
    var current: Throwable? = cause
    var depth = 0
    while (current != null && depth < 5) {
        val node = current
        if (node is HttpDataSource.InvalidResponseCodeException) return node.responseCode
        current = node.cause
        depth++
    }
    return null
}

private val URL_REGEX = Regex("""https?://\S+""")

/** The cause's message, with any URL redacted and length-capped — for the
 * unknown bucket only, so development diagnostics survive without ever
 * exposing a token-bearing stream URL. */
private fun sanitisedCauseMessage(error: PlaybackException): String =
    (error.cause?.message ?: "")
        .replace(URL_REGEX, "<url>")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .take(120)
