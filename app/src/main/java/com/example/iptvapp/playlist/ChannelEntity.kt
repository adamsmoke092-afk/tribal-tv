package com.example.iptvapp.playlist

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room-persisted form of a parsed channel (SPEC §2.1). Kept separate from
 * M3uChannel so the parser/network code doesn't need to know about Room —
 * mapping happens via the toEntity()/toDomain() extensions below.
 *
 * streamUrl is the primary key: in practice it's the stable unique thing
 * about a channel entry (tvg-id is often missing/inconsistent in public
 * playlists, per SPEC §4's note on unreliable metadata).
 */
@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val streamUrl: String,
    val name: String,
    val logoUrl: String?,
    val groupTitle: String?,
    val tvgId: String?
)

fun M3uChannel.toEntity(): ChannelEntity = ChannelEntity(
    streamUrl = streamUrl,
    name = name,
    logoUrl = logoUrl,
    groupTitle = groupTitle,
    tvgId = tvgId
)

fun ChannelEntity.toDomain(): M3uChannel = M3uChannel(
    name = name,
    streamUrl = streamUrl,
    logoUrl = logoUrl,
    groupTitle = groupTitle,
    tvgId = tvgId
)
