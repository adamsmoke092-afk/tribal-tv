package com.example.iptvapp.supabase

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppConfigTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testParseFeatureFlags() {
        val configMap = mapOf(
            "maintenance_mode" to mapOf(
                "enabled" to "false",
                "message" to "Under maintenance"
            ),
            "force_update" to mapOf(
                "enabled" to "true",
                "min_version_code" to "5",
                "message" to "Update required"
            ),
            "min_app_version" to mapOf(
                "min_version_code" to "3",
                "message" to "Too old"
            ),
            "default_playlist_url" to mapOf("default_playlist_url" to "https://example.com/default.m3u"),
            "epg_auto_refresh_hours" to mapOf("epg_auto_refresh_hours" to "12"),
            "max_buffer_size_mb" to mapOf("max_buffer_size_mb" to "100"),
            "enable_vidsrc_movies" to mapOf("enable_vidsrc_movies" to "false"),
            "vidsrc_base_url" to mapOf("vidsrc_base_url" to "https://custom.vidsrc.com")
        )

        val flags = AppConfigRepository.Companion.parseFeatureFlags(configMap)

        assertFalse(flags.maintenanceMode.enabled)
        assertEquals("Under maintenance", flags.maintenanceMode.message)

        assertTrue(flags.forceUpdate.enabled)
        assertEquals(5, flags.forceUpdate.minVersionCode)
        assertEquals("Update required", flags.forceUpdate.message)

        assertEquals(3, flags.minAppVersion.minVersionCode)
        assertEquals("Too old", flags.minAppVersion.message)

        assertEquals("https://example.com/default.m3u", flags.defaultPlaylistUrl)
        assertEquals(12, flags.epgAutoRefreshHours)
        assertEquals(100, flags.maxBufferSizeMb)
        assertFalse(flags.enableVidSrcMovies)
        assertEquals("https://custom.vidsrc.com", flags.vidSrcBaseUrl)
    }

    @Test
    fun testParseFeatureFlagsWithDefaults() {
        val configMap = mapOf<String, Map<String, Any>>()

        val flags = AppConfigRepository.Companion.parseFeatureFlags(configMap)

        val defaults = FeatureFlags.DEFAULT
        assertEquals(defaults.maintenanceMode.enabled, flags.maintenanceMode.enabled)
        assertEquals(defaults.maintenanceMode.message, flags.maintenanceMode.message)
        assertEquals(defaults.forceUpdate.enabled, flags.forceUpdate.enabled)
        assertEquals(defaults.forceUpdate.minVersionCode, flags.forceUpdate.minVersionCode)
        assertEquals(defaults.minAppVersion.minVersionCode, flags.minAppVersion.minVersionCode)
        assertEquals(defaults.defaultPlaylistUrl, flags.defaultPlaylistUrl)
        assertEquals(defaults.epgAutoRefreshHours, flags.epgAutoRefreshHours)
        assertEquals(defaults.maxBufferSizeMb, flags.maxBufferSizeMb)
        assertEquals(defaults.enableVidSrcMovies, flags.enableVidSrcMovies)
        assertEquals(defaults.vidSrcBaseUrl, flags.vidSrcBaseUrl)
    }

    @Test
    fun testParseMaintenanceConfig() {
        val map = mapOf(
            "enabled" to "true",
            "message" to "Custom message"
        )
        val config = AppConfigRepository.Companion.parseMaintenance(map)

        assertTrue(config.enabled)
        assertEquals("Custom message", config.message)
    }

    @Test
    fun testParseForceUpdateConfig() {
        val map = mapOf(
            "enabled" to "true",
            "min_version_code" to "10",
            "message" to "Force update msg"
        )
        val config = AppConfigRepository.Companion.parseForceUpdate(map)

        assertTrue(config.enabled)
        assertEquals(10, config.minVersionCode)
        assertEquals("Force update msg", config.message)
    }

    @Test
    fun testParseMinVersionConfig() {
        val map = mapOf(
            "min_version_code" to "7",
            "message" to "Min version msg"
        )
        val config = AppConfigRepository.Companion.parseMinVersion(map)

        assertEquals(7, config.minVersionCode)
        assertEquals("Min version msg", config.message)
    }
}