package com.example.iptvapp.epg

import java.io.IOException
import java.io.InputStream

/**
 * Thrown when a guide exceeds its size budget. Subclasses IOException so a
 * failing source is reported like any other network/parse failure — never
 * allowed to OOM the app.
 */
class EpgLimitExceededException(message: String) : IOException(message)

/**
 * Bounds the bytes read from a guide stream. Because guides are commonly
 * gzipped, this wraps the DECOMPRESSED stream: the compressed download and
 * the decompressed content are both capped by the same budget, which is the
 * number that actually decides memory use.
 *
 * Throwing (rather than silently truncating) keeps the parse streaming and
 * means an oversized guide is reported as that source's failure — it never
 * corrupts a half-cached result, because the repository only persists a
 * source's rows after its parse completes.
 */
class LimitedInputStream(
    private val delegate: InputStream,
    private val maxBytes: Long
) : InputStream() {

    private var bytesRead: Long = 0

    override fun read(): Int {
        val value = delegate.read()
        if (value >= 0) count(1)
        return value
    }

    override fun read(buffer: ByteArray, off: Int, len: Int): Int {
        val n = delegate.read(buffer, off, len)
        if (n > 0) count(n.toLong())
        return n
    }

    private fun count(n: Long) {
        bytesRead += n
        if (bytesRead > maxBytes) {
            throw EpgLimitExceededException(
                "Guide is larger than the ${maxBytes / (1024 * 1024)} MB limit"
            )
        }
    }

    override fun close() {
        delegate.close()
    }
}
