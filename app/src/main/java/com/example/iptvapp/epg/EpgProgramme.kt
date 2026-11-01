package com.example.iptvapp.epg

import androidx.room.Entity

/**
 * One EPG programme (an XMLTV <programme> entry), cached in Room.
 *
 * channelId is the NORMALIZED playlist tvg-id (see normalizeTvgId), never the
 * raw guide id: when loose quality matching maps a guide id onto a playlist
 * channel, the row is stored under the playlist's id so the same id used for
 * lookup is the one used for storage.
 *
 * source is the guide URL the row came from. It is part of the key so that
 *  - two guides may describe the same channel without overwriting each other,
 *  - a refresh can replace exactly one source's rows, and a failed source can
 *    keep whatever it cached last time.
 *
 * Composite primary key (source, channelId, startMs) — a channel cannot have
 * two programmes starting at the same instant in the same guide, so this
 * de-dupes within a source while keeping sources independent. startMs/stopMs
 * are epoch millis (parsed from the XMLTV "YYYYMMDDHHMMSS +ZZZZ" form — see
 * XmltvTime).
 *
 * Like the channel cache, this is re-fetchable network data kept out of the
 * user-data stores.
 */
@Entity(tableName = "programmes", primaryKeys = ["source", "channelId", "startMs"])
data class EpgProgramme(
    val source: String,
    val channelId: String,
    val startMs: Long,
    val stopMs: Long,
    val title: String,
    val description: String? = null
)
