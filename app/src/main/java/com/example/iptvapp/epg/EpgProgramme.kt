package com.example.iptvapp.epg

import androidx.room.Entity

/**
 * One EPG programme (an XMLTV <programme> entry), cached in Room.
 *
 * channelId matches an XMLTV channel id, which we line up with a channel's
 * tvg-id (the id iptv-org and most providers share between the M3U and the
 * guide). startMs/stopMs are epoch millis (parsed from the XMLTV
 * "YYYYMMDDHHMMSS +ZZZZ" form — see XmltvTime).
 *
 * Composite primary key (channelId, startMs): a channel can't have two
 * programmes starting at the same instant, so this both de-dupes and gives
 * the now/next query a usable index for free.
 *
 * Like the channel cache, this is re-fetchable network data kept out of the
 * user-data stores; a schema bump rebuilds it from the next EPG fetch.
 */
@Entity(tableName = "programmes", primaryKeys = ["channelId", "startMs"])
data class EpgProgramme(
    val channelId: String,
    val startMs: Long,
    val stopMs: Long,
    val title: String,
    val description: String? = null
)
