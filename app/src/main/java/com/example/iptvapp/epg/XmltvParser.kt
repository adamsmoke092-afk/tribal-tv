package com.example.iptvapp.epg

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

/**
 * Streaming XMLTV parser. Reads <programme start="..." stop="..."
 * channel="..."><title>..</title><desc>..</desc></programme> elements off
 * an InputStream with XmlPullParser, so a large guide is never held in
 * memory as a DOM.
 *
 * Two bounds keep storage sane on a low-RAM device:
 *  - channelFilter: if non-empty, only programmes whose channel id is in
 *    the set are kept (the set is the playlist's tvg-ids — no point caching
 *    guide data for channels the user doesn't have).
 *  - [windowStartMs, windowEndMs]: only programmes overlapping this window
 *    are kept (e.g. now-1h .. now+48h), so we don't store a week of guide.
 *
 * The time parsing itself lives in XmltvTime (pure, unit-tested); this
 * class is the Android-only I/O shell around it.
 */
object XmltvParser {

    fun parse(
        input: InputStream,
        channelFilter: Set<String>,
        windowStartMs: Long,
        windowEndMs: Long
    ): List<EpgProgramme> {
        val result = mutableListOf<EpgProgramme>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "programme") {
                // readProgramme leaves the parser on this element's END_TAG;
                // the parser.next() below then advances past it.
                readProgramme(parser, channelFilter, windowStartMs, windowEndMs)
                    ?.let { result += it }
            }
            event = parser.next()
        }
        return result
    }

    /**
     * Reads one <programme> element starting at its START_TAG and leaving
     * the parser on its END_TAG. Returns null (but still consumes the whole
     * element) when the programme is filtered out or malformed, so the
     * caller's loop always advances.
     */
    private fun readProgramme(
        parser: XmlPullParser,
        channelFilter: Set<String>,
        windowStartMs: Long,
        windowEndMs: Long
    ): EpgProgramme? {
        val channelId = parser.getAttributeValue(null, "channel")?.trim().orEmpty()
        val startMs = parseXmltvInstant(parser.getAttributeValue(null, "start"))
        val stopMs = parseXmltvInstant(parser.getAttributeValue(null, "stop"))

        var title: String? = null
        var desc: String? = null

        // Walk children until this programme's END_TAG.
        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.END_DOCUMENT) break
            if (event == XmlPullParser.END_TAG && parser.name == "programme") break
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "title" -> if (title == null) title = readText(parser)
                    "desc" -> if (desc == null) desc = readText(parser)
                    else -> skip(parser)
                }
            }
        }

        if (channelId.isEmpty() || startMs == null || stopMs == null || stopMs <= startMs) return null
        if (title.isNullOrBlank()) return null
        if (channelFilter.isNotEmpty() && channelId !in channelFilter) return null
        // Keep only programmes that overlap the window.
        if (stopMs < windowStartMs || startMs > windowEndMs) return null

        return EpgProgramme(
            channelId = channelId,
            startMs = startMs,
            stopMs = stopMs,
            title = title.trim(),
            description = desc?.trim()?.ifEmpty { null }
        )
    }

    /** Reads the text content of the current START_TAG, leaving parser on its END_TAG. */
    private fun readText(parser: XmlPullParser): String {
        var text = ""
        if (parser.next() == XmlPullParser.TEXT) {
            text = parser.text
            parser.next()
        }
        return text
    }

    /** Skips an unwanted element and all its descendants. */
    private fun skip(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) return
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }
}
