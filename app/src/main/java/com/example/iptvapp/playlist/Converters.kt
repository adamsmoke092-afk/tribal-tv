package com.example.iptvapp.playlist

import androidx.room.TypeConverter
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Room type converters. The only non-primitive column is a channel's HTTP
 * headers map (from #EXTVLCOPT). Stored as a query-string-style encoding
 * ("k1=v1&k2=v2" with each key/value percent-encoded) so values containing
 * '=', '&', spaces, or ':' (common in User-Agent / Referer strings) round-trip
 * losslessly. Dependency-free (java.net), so it's also JVM unit-testable.
 */
class Converters {

    @TypeConverter
    fun fromHeaders(headers: Map<String, String>?): String {
        if (headers.isNullOrEmpty()) return ""
        return headers.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
    }

    @TypeConverter
    fun toHeaders(encoded: String?): Map<String, String> {
        if (encoded.isNullOrEmpty()) return emptyMap()
        return encoded.split("&").mapNotNull { pair ->
            val i = pair.indexOf('=')
            if (i < 0) return@mapNotNull null
            val key = URLDecoder.decode(pair.substring(0, i), "UTF-8")
            val value = URLDecoder.decode(pair.substring(i + 1), "UTF-8")
            if (key.isEmpty()) null else key to value
        }.toMap()
    }
}
