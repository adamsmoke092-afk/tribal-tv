package com.example.iptvapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.example.iptvapp.player.PlayerFactory
import com.example.iptvapp.playlist.M3uChannel
import com.example.iptvapp.playlist.PlaylistRepository
import kotlinx.coroutines.launch

/**
 * Top-level screen toggle: shows the channel list until something is
 * tapped, then hands the stream URL to PlayerFactory.loadChannel and
 * switches to the player screen (SPEC §2.4/§5 — "wire it together").
 *
 * Multiple playlists: the registry lives in PlaylistStore; this screen
 * owns the active playlist's URL as state. It only changes through the
 * settings dialog's save path, which fetches and parses the target
 * playlist before anything is persisted or cached. Batch B: also
 * forwards MainActivity's picture-in-picture state so the player can
 * strip its chrome.
 * Background play: the player param is a MediaController proxying the
 * session player in PlaybackService; on first load the UI resyncs to
 * whatever the session is already playing.
 */
@Composable
fun IptvApp(
    initialPlaylistUrl: String,
    settings: SettingsStore,
    playlistStore: PlaylistStore,
    favouritesStore: FavouritesStore,
    recentsStore: RecentsStore,
    deadStore: DeadChannelStore,
    repository: PlaylistRepository,
    player: Player,
    isInPip: Boolean
) {
    var channels by remember { mutableStateOf<List<M3uChannel>>(emptyList()) }
    var selectedChannel by remember { mutableStateOf<M3uChannel?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var playlistUrl by remember { mutableStateOf(initialPlaylistUrl) }
    var playlists by remember { mutableStateOf(playlistStore.playlists()) }
    var activePlaylistId by remember { mutableStateOf(playlistStore.activePlaylist().id) }
    var showLogos by remember { mutableStateOf(settings.showLogos()) }
    var hideGeoBlocked by remember { mutableStateOf(settings.hideGeoBlocked()) }
    var hideNot24x7 by remember { mutableStateOf(settings.hideNot24x7()) }
    var hdOnly by remember { mutableStateOf(settings.hdOnly()) }
    var favourites by remember { mutableStateOf(favouritesStore.favourites()) }
    var recents by remember { mutableStateOf(recentsStore.recents()) }
    var deadChannels by remember { mutableStateOf(deadStore.dead()) }
    var resumeOnLaunch by remember { mutableStateOf(settings.resumeOnLaunch()) }
    var dataSaver by remember { mutableStateOf(settings.dataSaver()) }
    var audioOnly by remember { mutableStateOf(settings.audioOnly()) }
    var resumedLast by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    // The ordered channel list the user is currently looking at — the
    // player's prev/next buttons zap through it.
    var playSequence by remember { mutableStateOf(emptyList<M3uChannel>()) }
    // mediaId == streamUrl of whatever the session is playing; the list
    // flags that row. Seeded from the current item so reconnecting to an
    // already-playing session starts correct.
    var playingUrl by remember { mutableStateOf(player.currentMediaItem?.mediaId) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            channels = repository.getChannels(playlistUrl)
        } catch (e: Exception) {
            loadError = "Failed to load playlist: ${e.message}"
        }
        // Cache older than a day: refresh silently in the background while
        // the cache keeps serving. A failure keeps the cache and only
        // shows the non-blocking banner (no Retry button — channels are
        // on screen to pull-to-refresh).
        if (System.currentTimeMillis() - settings.lastRefreshMs() > STALE_PLAYLIST_MS) {
            try {
                channels = repository.refreshChannels(playlistUrl)
                loadError = null
            } catch (e: Exception) {
                loadError = "Background refresh failed — showing cached channels"
            }
        }
    }

    // Once per session, when the channel list arrives: if the playback
    // session is already playing something (app reopened from the
    // background), land back on that channel's player screen instead of
    // reloading it. Otherwise, with "Resume on launch" on, reopen the
    // last-watched channel if it's still in the playlist. The
    // resumedLast guard keeps a refresh from re-triggering either path.
    LaunchedEffect(channels) {
        if (resumedLast || channels.isEmpty()) return@LaunchedEffect
        resumedLast = true
        val playingUrl = player.currentMediaItem?.mediaId
        if (playingUrl != null) {
            selectedChannel = channels.firstOrNull { it.streamUrl == playingUrl }
        } else if (resumeOnLaunch) {
            val lastUrl = recents.firstOrNull() ?: return@LaunchedEffect
            val last = channels.firstOrNull { it.streamUrl == lastUrl } ?: return@LaunchedEffect
            PlayerFactory.loadChannel(player, last.streamUrl)
            selectedChannel = last
        }
    }

    // Keeps playingUrl live as the session loads, switches or stops —
    // mostly for background play, where the list and the notification are
    // the only windows onto playback.
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                playingUrl = mediaItem?.mediaId
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Recomputed whenever the channel data changes (a successful refresh
    // swaps the list); it doesn't tick between refreshes, which is fine
    // for a label that always refers to the last fetch.
    val lastRefreshText = remember(channels) {
        formatLastRefreshText(System.currentTimeMillis(), settings.lastRefreshMs())
    }

    // Channel zapping: moves through the list the user came from,
    // skipping offline channels where possible, wrapping around at the
    // ends — friendlier for TV flipping than stopping dead.
    fun moveChannel(from: M3uChannel, delta: Int) {
        val alive = playSequence
            .filterNot { it.streamUrl in deadChannels }
            .ifEmpty { playSequence }
        if (alive.isEmpty()) return
        val index = alive.indexOfFirst { it.streamUrl == from.streamUrl }
        if (index < 0) return
        val next = alive[(index + delta).mod(alive.size)]
        PlayerFactory.loadChannel(player, next.streamUrl)
        selectedChannel = next
        recentsStore.add(next.streamUrl)
        recents = recentsStore.recents()
    }

    val current = selectedChannel
    if (current == null) {
        ChannelListScreen(
            channels = channels,
            error = loadError,
            isRefreshing = isRefreshing,
            lastRefreshText = lastRefreshText,
            playingUrl = playingUrl,
            showLogos = showLogos,
            hideGeoBlocked = hideGeoBlocked,
            hideNot24x7 = hideNot24x7,
            hdOnly = hdOnly,
            favourites = favourites,
            recents = recents,
            deadChannels = deadChannels,
            onToggleFavourite = { channel ->
                favourites = favouritesStore.toggle(channel.streamUrl)
            },
            onChannelSelected = { channel ->
                PlayerFactory.loadChannel(player, channel.streamUrl)
                selectedChannel = channel
                recentsStore.add(channel.streamUrl)
                recents = recentsStore.recents()
            },
            onRefresh = {
                // Shared by the top-bar icon and pull-to-refresh; the guard
                // keeps a pull mid-refresh from stacking a second fetch.
                if (!isRefreshing) {
                    scope.launch {
                        isRefreshing = true
                        try {
                            channels = repository.refreshChannels(playlistUrl)
                            loadError = null
                        } catch (e: Exception) {
                            loadError = "Refresh failed: ${e.message}"
                        } finally {
                            isRefreshing = false
                        }
                    }
                }
            },
            onOpenSettings = { showSettings = true },
            onPlaySequenceChanged = { playSequence = it }
        )

        if (showSettings) {
            SettingsDialog(
                playlists = playlists,
                initialActivePlaylistId = activePlaylistId,
                initialShowLogos = showLogos,
                initialHideGeoBlocked = hideGeoBlocked,
                initialHideNot24x7 = hideNot24x7,
                initialHdOnly = hdOnly,
                initialResumeOnLaunch = resumeOnLaunch,
                initialDataSaver = dataSaver,
                initialAudioOnly = audioOnly,
                onDismiss = { showSettings = false },
                onSave = { draft ->
                    try {
                        val activeId = playlistStore.activePlaylist().id
                        val newName = draft.newPlaylistName
                        val newUrl = draft.newPlaylistUrl
                        val targetUrl: String = when {
                            // Adding implies activating: the new playlist is
                            // fetched, registered and made active in one go.
                            newName != null && newUrl != null -> newUrl
                            draft.selectedPlaylistId != activeId -> playlistStore.playlists()
                                .firstOrNull { it.id == draft.selectedPlaylistId }
                                ?.url
                                ?: throw IllegalStateException("Playlist not found")
                            else -> ""
                        }
                        if (targetUrl.isNotEmpty()) {
                            // Fetch/parse FIRST — it throws before the Room
                            // cache or any store is touched, so a failure
                            // leaves everything exactly as it was.
                            val loaded = repository.refreshChannels(targetUrl)
                            // Renames apply before removals so a renamed-
                            // and-removed playlist leaves no orphan keys.
                            draft.renamedPlaylists.forEach { (id, name) ->
                                playlistStore.rename(id, name)
                            }
                            draft.removedPlaylistIds.forEach { playlistStore.remove(it) }
                            if (newName != null && newUrl != null) {
                                val added = playlistStore.add(newName, newUrl)
                                playlistStore.setActive(added.id)
                            } else {
                                playlistStore.setActive(draft.selectedPlaylistId)
                            }
                            channels = loaded
                            playlistUrl = targetUrl
                        } else {
                            draft.renamedPlaylists.forEach { (id, name) ->
                                playlistStore.rename(id, name)
                            }
                            draft.removedPlaylistIds.forEach { playlistStore.remove(it) }
                        }
                        if (draft.showLogos != showLogos) {
                            showLogos = draft.showLogos
                            settings.setShowLogos(draft.showLogos)
                        }
                        if (draft.hideGeoBlocked != hideGeoBlocked) {
                            hideGeoBlocked = draft.hideGeoBlocked
                            settings.setHideGeoBlocked(draft.hideGeoBlocked)
                        }
                        if (draft.hideNot24x7 != hideNot24x7) {
                            hideNot24x7 = draft.hideNot24x7
                            settings.setHideNot24x7(draft.hideNot24x7)
                        }
                        if (draft.hdOnly != hdOnly) {
                            hdOnly = draft.hdOnly
                            settings.setHdOnly(draft.hdOnly)
                        }
                        if (draft.resumeOnLaunch != resumeOnLaunch) {
                            resumeOnLaunch = draft.resumeOnLaunch
                            settings.setResumeOnLaunch(draft.resumeOnLaunch)
                        }
                        if (draft.dataSaver != dataSaver || draft.audioOnly != audioOnly) {
                            dataSaver = draft.dataSaver
                            audioOnly = draft.audioOnly
                            settings.setDataSaver(dataSaver)
                            settings.setAudioOnly(audioOnly)
                            applyPlaybackPreferences(player, dataSaver, audioOnly)
                        }
                        playlists = playlistStore.playlists()
                        activePlaylistId = playlistStore.activePlaylist().id
                        null
                    } catch (e: Exception) {
                        e.message ?: "Failed to load that playlist"
                    }
                }
            )
        }
    } else {
        PlayerScreen(
            player = player,
            channelName = current.name,
            isInPip = isInPip,
            dataSaver = dataSaver,
            audioOnly = audioOnly,
            onToggleDataSaver = {
                dataSaver = !dataSaver
                settings.setDataSaver(dataSaver)
                applyPlaybackPreferences(player, dataSaver, audioOnly)
            },
            onToggleAudioOnly = {
                audioOnly = !audioOnly
                settings.setAudioOnly(audioOnly)
                applyPlaybackPreferences(player, dataSaver, audioOnly)
            },
            onPrevChannel = { moveChannel(current, -1) },
            onNextChannel = { moveChannel(current, 1) },
            onPlaybackFailed = {
                // Audio-only mode fails on a stream with no audio track;
                // that failure says nothing about the channel being dead.
                if (!audioOnly) deadChannels = deadStore.mark(current.streamUrl)
            },
            onPlaybackSucceeded = {
                if (current.streamUrl in deadChannels) {
                    deadChannels = deadStore.clear(current.streamUrl)
                }
            },
            onBack = {
                player.stop()
                selectedChannel = null
            }
        )
    }
}
