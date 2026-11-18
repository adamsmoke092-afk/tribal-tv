package com.example.iptvapp.supabase

import android.util.Log
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Repository for fetching curated playlists from Supabase.
 */
class CuratedPlaylistRepository(private val postgrest: PostgrestClient) {

    private val TAG = "CuratedPlaylistRepo"

    private val columns =
        "id,name,description,m3u_url,logo_url,category,country_code," +
            "is_active,sort_order,metadata,created_at,updated_at"

    /**
     * Fetches all active curated playlists, ordered by sort_order.
     * Returns empty list on failure (caller should fall back to local defaults).
     */
    suspend fun getActivePlaylists(): List<CuratedPlaylist> = withContext(Dispatchers.IO) {
        try {
            val body = postgrest.get(
                table = "curated_playlists",
                query = "select=$columns&is_active=eq.true&order=sort_order.asc",
            )
            val rows = body as? JsonArray ?: return@withContext emptyList()
            rows.mapNotNull { (it as? JsonObject)?.let { obj -> parsePlaylist(obj) } }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch curated playlists", e)
            emptyList()
        }
    }

    /**
     * Fetches a single playlist by ID.
     */
    suspend fun getPlaylistById(id: String): CuratedPlaylist? = withContext(Dispatchers.IO) {
        try {
            val body = postgrest.get(
                table = "curated_playlists",
                query = "select=$columns&id=eq.${URLEncoder.encode(id, "UTF-8")}&limit=1",
            )
            (body as? JsonArray)?.firstOrNull()?.let { row ->
                (row as? JsonObject)?.let { parsePlaylist(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch playlist $id", e)
            null
        }
    }

    companion object {
        /**
         * Row parsing lives here so the test source set exercises this exact
         * code. When it was duplicated into the test file the copy drifted: it
         * collapsed empty strings to null while the assertions expected "".
         * That copy could never have passed, which is how the duplication
         * survived.
         *
         * internal, not public: the test module is a friend of this one, so
         * internal is visible there and nowhere else.
         */
        internal fun parsePlaylist(obj: JsonObject): CuratedPlaylist {
            val categoryStr = obj["category"]?.jsonPrimitive?.contentOrNull ?: "OTHER"
            // valueOf throws on an unknown value, and that throw would be
            // caught by the caller's catch-all -- one bad row would empty the
            // whole list.
            val category = runCatching {
                CuratedPlaylist.PlaylistCategory.valueOf(categoryStr.uppercase())
            }.getOrDefault(CuratedPlaylist.PlaylistCategory.OTHER)

            val metadataMap = mutableMapOf<String, Any>()
            (obj["metadata"] as? JsonObject)?.forEach { (key, value) ->
                val text = (value as? JsonPrimitive)?.contentOrNull
                if (text != null) metadataMap[key] = text
            }

            return CuratedPlaylist(
                id = obj["id"]?.jsonPrimitive?.contentOrNull ?: "",
                name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "",
                description = obj["description"]?.jsonPrimitive?.contentOrNull,
                m3uUrl = obj["m3u_url"]?.jsonPrimitive?.contentOrNull ?: "",
                logoUrl = obj["logo_url"]?.jsonPrimitive?.contentOrNull,
                category = category,
                countryCode = obj["country_code"]?.jsonPrimitive?.contentOrNull,
                isActive = obj["is_active"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false,
                sortOrder = obj["sort_order"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
                metadata = metadataMap,
                createdAt = obj["created_at"]?.jsonPrimitive?.contentOrNull ?: "",
                updatedAt = obj["updated_at"]?.jsonPrimitive?.contentOrNull ?: ""
            )
        }
    }
}

/**
 * Repository for fetching app configuration from Supabase.
 */
class AppConfigRepository(private val postgrest: PostgrestClient) {

    private val TAG = "AppConfigRepo"

    /**
     * Fetches all public app config values and parses them into FeatureFlags.
     * Returns DEFAULT flags on failure.
     */
    suspend fun getFeatureFlags(): FeatureFlags = withContext(Dispatchers.IO) {
        try {
            val body = postgrest.get(
                table = "app_config",
                query = "select=key,value,description,is_public,updated_at&is_public=eq.true",
            )
            val rows = body as? JsonArray ?: return@withContext FeatureFlags.DEFAULT

            val configMap = mutableMapOf<String, Map<String, Any>>()
            for (row in rows) {
                val obj = row as? JsonObject ?: continue
                val key = obj["key"]?.jsonPrimitive?.contentOrNull ?: continue
                val valueObj = obj["value"] as? JsonObject ?: continue
                val valueMap = mutableMapOf<String, Any>()
                valueObj.forEach { (k, v) ->
                    val text = (v as? JsonPrimitive)?.contentOrNull
                    if (text != null) valueMap[k] = text
                }
                configMap[key] = valueMap
            }

            parseFeatureFlags(configMap)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch app config", e)
            FeatureFlags.DEFAULT
        }
    }

    companion object {
        /** See the note on CuratedPlaylistRepository.parsePlaylist. */
        internal fun parseFeatureFlags(configMap: Map<String, Map<String, Any>>): FeatureFlags {
            val maintenance = configMap["maintenance_mode"]?.let { parseMaintenance(it) } ?: FeatureFlags.DEFAULT.maintenanceMode
            val forceUpdate = configMap["force_update"]?.let { parseForceUpdate(it) } ?: FeatureFlags.DEFAULT.forceUpdate
            val minVersion = configMap["min_app_version"]?.let { parseMinVersion(it) } ?: FeatureFlags.DEFAULT.minAppVersion
            val defaultPlaylist = configMap["default_playlist_url"]?.get("default_playlist_url")?.toString()
                ?: FeatureFlags.DEFAULT.defaultPlaylistUrl
            val epgRefresh = configMap["epg_auto_refresh_hours"]?.get("epg_auto_refresh_hours")?.toString()?.toIntOrNull()
                ?: FeatureFlags.DEFAULT.epgAutoRefreshHours
            val maxBuffer = configMap["max_buffer_size_mb"]?.get("max_buffer_size_mb")?.toString()?.toIntOrNull()
                ?: FeatureFlags.DEFAULT.maxBufferSizeMb
            val enableVidSrc = configMap["enable_vidsrc_movies"]?.get("enable_vidsrc_movies")?.toString()?.toBoolean()
                ?: FeatureFlags.DEFAULT.enableVidSrcMovies
            val vidSrcUrl = configMap["vidsrc_base_url"]?.get("vidsrc_base_url")?.toString()
                ?: FeatureFlags.DEFAULT.vidSrcBaseUrl

            return FeatureFlags(
                maintenanceMode = maintenance,
                forceUpdate = forceUpdate,
                minAppVersion = minVersion,
                defaultPlaylistUrl = defaultPlaylist,
                epgAutoRefreshHours = epgRefresh,
                maxBufferSizeMb = maxBuffer,
                enableVidSrcMovies = enableVidSrc,
                vidSrcBaseUrl = vidSrcUrl
            )
        }

        internal fun parseMaintenance(map: Map<String, Any>): MaintenanceConfig {
            return MaintenanceConfig(
                enabled = map["enabled"]?.toString()?.toBoolean() ?: false,
                message = map["message"]?.toString() ?: FeatureFlags.DEFAULT.maintenanceMode.message
            )
        }

        internal fun parseForceUpdate(map: Map<String, Any>): ForceUpdateConfig {
            return ForceUpdateConfig(
                enabled = map["enabled"]?.toString()?.toBoolean() ?: false,
                minVersionCode = map["min_version_code"]?.toString()?.toIntOrNull() ?: 1,
                message = map["message"]?.toString() ?: FeatureFlags.DEFAULT.forceUpdate.message
            )
        }

        internal fun parseMinVersion(map: Map<String, Any>): MinVersionConfig {
            return MinVersionConfig(
                minVersionCode = map["min_version_code"]?.toString()?.toIntOrNull() ?: 1,
                message = map["message"]?.toString() ?: FeatureFlags.DEFAULT.minAppVersion.message
            )
        }
    }
}