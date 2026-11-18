package com.example.iptvapp.supabase

import kotlinx.serialization.Serializable

/**
 * App configuration model matching the Supabase table.
 */
@Serializable
data class AppConfig(
    val key: String,
    val value: Map<String, Any>,
    val description: String?,
    val isPublic: Boolean,
    val updatedAt: String
)

/**
 * Parsed feature flags from app_config table.
 */
data class FeatureFlags(
    val maintenanceMode: MaintenanceConfig,
    val forceUpdate: ForceUpdateConfig,
    val minAppVersion: MinVersionConfig,
    val defaultPlaylistUrl: String,
    val epgAutoRefreshHours: Int,
    val maxBufferSizeMb: Int,
    val enableVidSrcMovies: Boolean,
    val vidSrcBaseUrl: String
) {
    companion object {
        val DEFAULT = FeatureFlags(
            maintenanceMode = MaintenanceConfig(false, "App is under maintenance. Please try again later."),
            forceUpdate = ForceUpdateConfig(false, 1, "A new version is required. Please update from the store."),
            minAppVersion = MinVersionConfig(1, "This version is no longer supported. Please update."),
            defaultPlaylistUrl = "https://iptv-org.github.io/iptv/countries/za.m3u",
            epgAutoRefreshHours = 6,
            maxBufferSizeMb = 50,
            enableVidSrcMovies = true,
            vidSrcBaseUrl = "https://vidsrc.sh"
        )
    }
}

@Serializable
data class MaintenanceConfig(
    val enabled: Boolean,
    val message: String
)

@Serializable
data class ForceUpdateConfig(
    val enabled: Boolean,
    val minVersionCode: Int,
    val message: String
)

@Serializable
data class MinVersionConfig(
    val minVersionCode: Int,
    val message: String
)