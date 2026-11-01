package com.example.iptvapp.epg

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

/**
 * One raw XMLTV <programme>, still keyed by the GUIDE's own channel id.
 *
 * Keeping the raw guide id here (rather than rewriting it during parsing) is
 * what lets the caller decide how the guide's ids map onto playlist channels:
 * exact match, a unique quality alias, or nothing. The mapping — and the
 * decision to store the row under the playlist's id — happens afterwards in
 * EpgRepository.
 */
data class GuideProgramme(
    val guideChannelId: String,
    val startMs: Long,
    val stopMs: Long,
    val title: String,
    val description: String?
)

/** One guide's parse: its candidate programmes, and whether the count cap hit. */
data class GuideParse(
    val programmes: List<GuideProgramme>,
    val truncated: Boolean
)

/**
 * Streaming XMLTV parser. Reads <programme start="..." stop="..."
 * channel="..."><title>..</title><desc>..</desc></programme> elements off an
 * InputStream with XmlPullParser, so a large guide is never held in memory as
 * a DOM.
 *
 * Two bounds keep memory sane on a low-RAM device:
 *  - [isCandidate]: only programmes whose channel id could possibly belong to
 *    the playlist are returned. The caller passes a base-id relevance test
 *    (exact id, or same base id as some playlist channel) rather than an exact
 *    set, because uniqueness of a quality match can only be judged from every
 *    candidate id the guide mentions. Unrelated channels are dropped during
 *    the stream, so they never occupy memory.
 *  - [windowStartMs, windowEndMs]: only programmes overlapping this window are
 *    kept (e.g. now-1h .. now+48h), so we don't store a week of guide.
 *
 * The time parsing itself lives in XmltvTime (pure, unit-tested); this class is
 * the Android-only I/O shell around it.
 */
object XmltvParser {

    /**
     * Upper bound on programmes kept from ONE guide. A safety net rather than
     * a target — the time window and the candidate filter normally keep this
     * far below it, but a pathological or hostile guide can never exhaust
     * memory. Programmes beyond the cap are still parsed and discarded, so the
     * stream is consumed either way and the caller is told it was truncated.
     */
    internal const val MAX_GUIDE_PROGRAMMES = 40_000

    /**
     * @return the parsed programmes, plus whether the cap was hit (a truncated
     *         guide is reported to the user rather than silently incomplete).
     */
    fun parse(
        input: InputStream,
        isCandidate: (rawGuideChannelId: String) -> Boolean,
        windowStartMs: Long,
        windowEndMs: Long,
        maxProgrammes: Int = MAX_GUIDE_PROGRAMMES
    ): GuideParse {
        val result = mutableListOf<GuideProgramme>()
        var truncated = false
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "programme") {
                // readProgramme leaves the parser on this element's END_TAG;
                // the parser.next() below then advances past it.
                readProgramme(parser, isCandidate, windowStartMs, windowEndMs)
                    ?.let { programme ->
                        if (result.size < maxProgrammes) result += programme else truncated = true
                    }
            }
            event = parser.next()
        }
        return GuideParse(result, truncated)
    }

    /**
     * Reads one <programme> element starting at its START_TAG and leaving the
     * parser on its END_TAG. Returns null (but still consumes the whole
     * element) when the programme is filtered out or malformed, so the caller's
     * loop always advances.
     */
    private fun readProgramme(
        parser: XmlPullParser,
        isCandidate: (String) -> Boolean,
        windowStartMs: Long,
        windowEndMs: Long
    ): GuideProgramme? {
        val channelId = parser.getAttributeValue(null, "channel")
            ?.let { normalizeTvgId(it) }
            .orEmpty()
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
        if (!isCandidate(channelId)) return null
        // Keep only programmes that overlap the window.
        if (stopMs < windowStartMs || startMs > windowEndMs) return null

        return GuideProgramme(
            guideChannelId = channelId,
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

/**
 * The relevance test the parser runs per programme channel id: keep it when it
 * exactly equals a playlist id, or shares a playlist id's case-sensitive base
 * id. Anything else cannot possibly be matched (loosely or exactly) and is
 * dropped during the stream.
 *
 * Pure, so it is unit-testable without Android.
 */
fun epgCandidateIds(playlistIds: Collection<String>): (String) -> Boolean {
    val exact = playlistIds.map { normalizeTvgId(it) }.filter { it.isNotEmpty() }.toSet()
    val bases = exact.map { splitIdSuffix(it).base }.toSet()
    return { rawGuideId ->
        val id = normalizeTvgId(rawGuideId)
        id in exact || splitIdSuffix(id).base in bases
    }
}
