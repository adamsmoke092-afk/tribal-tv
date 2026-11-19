package com.example.iptvapp.supabase

import kotlinx.serialization.Serializable

/**
 * App configuration model matching the Supabase table.
 *
 * Not @Serializable for the same reason as CuratedPlaylist: `value` is
 * Map<String, Any>, which kotlinx.serialization has no serializer for, and
 * nothing encodes or decodes this type -- AppConfigRepository parses the rows
 * by hand.
 */
data class AppConfig(
    val key: String,
    val value: Map<String, Any>,
    val description: String?,
    val isPublic: Boolean,
    val updatedAt: String
)

/**
 * Parsed feature flags from app_config table.
 *
 * App-level only. IPTV settings (default playlist, EPG refresh, buffer size)
 * are not here: the free tier must work with no backend, so they live in
 * SettingsStore on the device.
 */
data class FeatureFlags(
    val maintenanceMode: MaintenanceConfig,
    val forceUpdate: ForceUpdateConfig,
    val minAppVersion: MinVersionConfig
) {
    companion object {
        val DEFAULT = FeatureFlags(
            maintenanceMode = MaintenanceConfig(false, "App is under maintenance. Please try again later."),
            forceUpdate = ForceUpdateConfig(false, 1, "A new version is required. Please update from the store."),
            minAppVersion = MinVersionConfig(1, "This version is no longer supported. Please update.")
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