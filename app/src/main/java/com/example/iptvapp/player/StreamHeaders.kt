package com.example.iptvapp.player

/**
 * Process-wide holder for the HTTP headers the currently-playing stream
 * requires (parsed from #EXTVLCOPT — e.g. User-Agent / Referer).
 *
 * Why a singleton rather than per-MediaItem headers: the UI drives playback
 * through a MediaController while the ExoPlayer lives in PlaybackService.
 * Both run in the SAME process (no android:process in the manifest), so a
 * shared static value is the simplest reliable channel — and, crucially,
 * keying "the current stream's headers" rather than per-URL means HLS
 * segment/variant requests (which have different URLs than the playlist)
 * also get the headers. Only one channel plays at a time, so a single
 * volatile map is sufficient.
 *
 * ResolvingDataSource (see PlayerFactory) injects [current] into every
 * DataSpec before it hits the network.
 */
object StreamHeaders {
    @Volatile
    var current: Map<String, String> = emptyMap()
}
