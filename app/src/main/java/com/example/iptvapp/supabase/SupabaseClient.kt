package com.example.iptvapp.supabase

import android.content.Context
import android.util.Log
import com.example.iptvapp.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * PostgREST endpoint for Tribal TV.
 *
 * The app makes two anonymous SELECTs against Supabase, so it talks to the REST
 * API directly instead of pulling in a client SDK. PostgREST is a documented
 * HTTP interface; an SDK is a wrapper over the same requests, and a wrapper
 * that has to be version-matched to the Kotlin compiler is a liability for two
 * read-only queries.
 */
class PostgrestClient(val baseUrl: String, val apiKey: String) {

    val restUrl: String = baseUrl.trimEnd('/') + "/rest/v1"

    /**
     * GET [restUrl]/[table]?[query] and return the parsed body.
     *
     * The anon key goes in both headers: `apikey` is what the gateway checks,
     * and `Authorization: Bearer` is what makes RLS evaluate as the anon role
     * rather than as nobody.
     */
    fun get(table: String, query: String): JsonElement {
        // Unconfigured, not broken. Both repositories catch this and fall back
        // to their documented defaults (empty list / FeatureFlags.DEFAULT), so
        // the app runs on local playlists instead of failing.
        if (baseUrl.isBlank() || apiKey.isBlank()) {
            throw IOException("Supabase is not configured (SUPABASE_URL / SUPABASE_ANON_KEY)")
        }
        val conn = URL("$restUrl/$table?$query").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("apikey", apiKey)
            // A legacy anon key is a JWT, and PostgREST also needs it as the
            // Bearer token to resolve the anon role. A new-style publishable
            // key (sb_publishable_...) is not a JWT -- sending it as Bearer
            // makes PostgREST reject the request as unparseable, so those get
            // the apikey header only.
            if (apiKey.count { it == '.' } == 2) {
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
            }
            conn.setRequestProperty("Accept", "application/json")

            val code = conn.responseCode
            if (code !in 200..299) {
                val detail = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IOException("HTTP $code from $table: $detail")
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            return Json.parseToJsonElement(text)
        } finally {
            conn.disconnect()
        }
    }
}

/** What [SupabaseClient.client] returns. */
class SupabaseHandle(val postgrest: PostgrestClient)

/**
 * Supabase client singleton for Tribal TV.
 * Initialized once with the project URL and anon key from BuildConfig.
 */
object SupabaseClient {
    private const val TAG = "SupabaseClient"

    private var _client: SupabaseHandle? = null

    val client: SupabaseHandle
        get() = _client ?: throw IllegalStateException(
            "SupabaseClient not initialized. Call SupabaseClient.initialize(context) first."
        )

    @Suppress("UNUSED_PARAMETER")
    fun initialize(context: Context) {
        if (_client != null) return

        val supabaseUrl = BuildConfig.SUPABASE_URL
        val supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY

        if (supabaseUrl.isBlank() || supabaseAnonKey.isBlank()) {
            // Not fatal. Curated playlists are one extra source and the feature
            // flags have defaults, so the app runs on local playlists. Throwing
            // here instead crashed on launch in every build whose CI secrets
            // were missing -- and in every unit test, which has no BuildConfig
            // values at all.
            Log.w(TAG, "Supabase is not configured; using local playlists and default flags")
        }

        _client = SupabaseHandle(PostgrestClient(supabaseUrl, supabaseAnonKey))
    }

    fun close() {
        _client = null
    }
}
