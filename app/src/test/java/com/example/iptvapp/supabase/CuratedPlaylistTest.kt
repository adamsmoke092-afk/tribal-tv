package com.example.iptvapp.supabase

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CuratedPlaylistTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testParsePlaylistFromJson() {
        val jsonObj = JsonObject(mapOf(
            "id" to JsonPrimitive("test-uuid"),
            "name" to JsonPrimitive("Test Playlist"),
            "description" to JsonPrimitive("Test description"),
            "m3u_url" to JsonPrimitive("https://example.com/playlist.m3u"),
            "logo_url" to JsonPrimitive("https://example.com/logo.png"),
            "category" to JsonPrimitive("SPORTS"),
            "country_code" to JsonPrimitive("ZA"),
            "is_active" to JsonPrimitive("true"),
            "sort_order" to JsonPrimitive("5"),
            "metadata" to JsonObject(mapOf("source" to JsonPrimitive("iptv-org"))),
            "created_at" to JsonPrimitive("2026-01-01T00:00:00Z"),
            "updated_at" to JsonPrimitive("2026-01-01T00:00:00Z")
        ))

        val playlist = CuratedPlaylistRepository.Companion.parsePlaylist(jsonObj)

        assertEquals("test-uuid", playlist.id)
        assertEquals("Test Playlist", playlist.name)
        assertEquals("Test description", playlist.description)
        assertEquals("https://example.com/playlist.m3u", playlist.m3uUrl)
        assertEquals("https://example.com/logo.png", playlist.logoUrl)
        assertEquals(CuratedPlaylist.PlaylistCategory.SPORTS, playlist.category)
        assertEquals("ZA", playlist.countryCode)
        assertEquals(true, playlist.isActive)
        assertEquals(5, playlist.sortOrder)
        assertEquals("iptv-org", playlist.metadata["source"])
        assertEquals("2026-01-01T00:00:00Z", playlist.createdAt)
        assertEquals("2026-01-01T00:00:00Z", playlist.updatedAt)
    }

    @Test
    fun testParsePlaylistWithNullFields() {
        val jsonObj = JsonObject(mapOf(
            "id" to JsonPrimitive("test-uuid-2"),
            "name" to JsonPrimitive("Minimal Playlist"),
            "description" to JsonPrimitive(""),
            "m3u_url" to JsonPrimitive("https://example.com/minimal.m3u"),
            "logo_url" to JsonPrimitive(""),
            "category" to JsonPrimitive("OTHER"),
            "country_code" to JsonPrimitive(""),
            "is_active" to JsonPrimitive("true"),
            "sort_order" to JsonPrimitive("0"),
            "metadata" to JsonObject(mapOf()),
            "created_at" to JsonPrimitive(""),
            "updated_at" to JsonPrimitive("")
        ))

        val playlist = CuratedPlaylistRepository.Companion.parsePlaylist(jsonObj)

        assertEquals("test-uuid-2", playlist.id)
        assertEquals("Minimal Playlist", playlist.name)
        assertEquals("", playlist.description)
        assertEquals("https://example.com/minimal.m3u", playlist.m3uUrl)
        assertEquals("", playlist.logoUrl)
        assertEquals(CuratedPlaylist.PlaylistCategory.OTHER, playlist.category)
        assertEquals("", playlist.countryCode)
        assertEquals(true, playlist.isActive)
        assertEquals(0, playlist.sortOrder)
    }
}

object CuratedPlaylistRepository {
    @JvmStatic
    fun parsePlaylist(obj: JsonObject): CuratedPlaylist {
        val categoryStr = obj["category"]?.jsonPrimitive?.content ?: "OTHER"
        val category = CuratedPlaylist.PlaylistCategory.valueOf(categoryStr.uppercase())

        val metadataMap = mutableMapOf<String, Any>()
        val metadata = obj["metadata"] as? JsonObject
        metadata?.forEach { (key, value) ->
            metadataMap[key] = when (value) {
                is JsonPrimitive -> value.content
                else -> value.toString()
            }
        }

        return CuratedPlaylist(
            id = obj["id"]?.jsonPrimitive?.content ?: "",
            name = obj["name"]?.jsonPrimitive?.content ?: "",
            description = obj["description"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() },
            m3uUrl = obj["m3u_url"]?.jsonPrimitive?.content ?: "",
            logoUrl = obj["logo_url"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() },
            category = category,
            countryCode = obj["country_code"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() },
            isActive = obj["is_active"]?.jsonPrimitive?.content?.toBoolean() ?: false,
            sortOrder = obj["sort_order"]?.jsonPrimitive?.content?.toInt() ?: 0,
            metadata = metadataMap,
            createdAt = obj["created_at"]?.jsonPrimitive?.content ?: "",
            updatedAt = obj["updated_at"]?.jsonPrimitive?.content ?: ""
        )
    }
}