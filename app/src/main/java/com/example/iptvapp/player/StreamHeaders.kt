package com.example.iptvapp.player

/**
 * A single volatile holder for the HTTP headers the currently-playing stream
 * requires (parsed from #EXTVLCOPT — e.g. User-Agent / Referer).
 *
 * Why a singleton rather than per-MediaItem headers: the UI drives playback
 * through a MediaController while the ExoPlayer lives in PlaybackService.
 * Both run in the SAME process (no android:process in the manifest), so a
 * shared value is the simplest reliable channel — and, crucially, keying
 * "the current stream's headers" rather than per-URL means HLS segment/variant
 * requests (which have different URLs than the playlist) also get the headers.
 * Only one channel plays at a time, so a single volatile map is sufficient.
 *
 * The map is never mutated in place — [PlayerFactory.loadChannel] always
 * installs a fresh, immutable map via [current]. The ResolvingDataSource reads
 * that map on every request, so each request sees a complete snapshot of
 * exactly one stream's headers. On a channel switch, [current] is reassigned
 * atomically before prepare() is called, so no request sees a
 * partially-updated state.
 *
 * The recovery listener in PlayerFactory does NOT consult this holder: it
 * compares the player's current MediaItem.mediaId (which is the stream URL)
 * against the URL it was created for. That is an exact per-channel tie and
 * avoids the stale-header ambiguity a key-based guard would have had.
 */
object StreamHeaders {

    /**
     * The headers required by the stream currently being loaded/played.
     * Reassign (never mutate) whenever [PlayerFactory.loadChannel] switches
     * channels.
     */
    @Volatile
    var current: Map<String, String> = emptyMap()
}
