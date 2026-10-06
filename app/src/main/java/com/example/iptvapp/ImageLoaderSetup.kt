package com.example.iptvapp

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import java.util.concurrent.TimeUnit
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath

/**
 * Shared Coil image loader, tuned for a ~370 Kbps VPN tunnel on a 3 GB
 * phone: capped memory cache, small disk cache, and OkHttp as the network
 * fetcher with short timeouts and a low per-host concurrency limit so a
 * screenful of logos can't saturate the link. No crossfade anywhere —
 * crossfades cancel each other's requests on fast scroll.
 *
 * Note: okhttp3 types are used directly here; okhttp is a transitive
 * dependency of coil-network-okhttp (which exposes Call.Factory in its
 * public API), so it's on the compile classpath.
 */
private const val MEMORY_CACHE_PERCENT = 0.12
private const val DISK_CACHE_BYTES = 20L * 1024 * 1024
private const val NETWORK_TIMEOUT_MS = 10_000L
private const val MAX_REQUESTS_PER_HOST = 4

fun buildImageLoader(context: Context): ImageLoader {
    val client = OkHttpClient.Builder()
        .dispatcher(Dispatcher().apply { maxRequestsPerHost = MAX_REQUESTS_PER_HOST })
        .connectTimeout(NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .callTimeout(NETWORK_TIMEOUT_MS * 2, TimeUnit.MILLISECONDS)
        .build()

    return ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
        .memoryCache {
            MemoryCache.Builder()
                .maxSizePercent(context, MEMORY_CACHE_PERCENT)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("image_cache").toOkioPath())
                .maxSizeBytes(DISK_CACHE_BYTES)
                .build()
        }
        .build()
}