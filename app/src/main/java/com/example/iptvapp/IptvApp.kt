package com.example.iptvapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.media3.exoplayer.ExoPlayer
import com.example.iptvapp.player.PlayerFactory
import com.example.iptvapp.playlist.M3uChannel
import com.example.iptvapp.playlist.PlaylistRepository
import kotlinx.coroutines.launch

/**
 * Top-level screen toggle: shows the channel list until something is
 * tapped, then hands the stream URL to PlayerFactory.loadChannel and
 * switches to the player screen (SPEC §2.4/§5 — "wire it together").
 *
 * Feature batch: owns the playlist URL as state, seeded from
 * SettingsStore by MainActivity. It only changes through the settings
 * dialog's save path, which fetches and parses the new playlist before
 * anything is persisted or cached. Batch B: also forwards MainActivity's
 * picture-in-picture state so the player can strip its chrome.
 */
@Composable
fun IptvApp(
    initialPlaylistUrl: String,
    settings: SettingsStore,
    favouritesStore: FavouritesStore,
    recentsStore: RecentsStore,
    deadStore: DeadChannelStore,
    repository: PlaylistRepository,
    player: ExoPlayer,
    isInPip: Boolean
) {
    var channels by remember { mutableStateOf<List<M3uChannel>>(emptyList()) }
    var selectedChannel by remember { mutableStateOf<M3uChannel?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var playlistUrl by remember { mutableStateOf(initialPlaylistUrl) }
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
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            channels = repository.getChannels(playlistUrl)
        } catch (e: Exception) {
            loadError = "Failed to load playlist: ${e.message}"
        }
    }

    // Reopen the last-watched channel once per session, only if the
    // "Resume on launch" setting is on and the channel is still in the
    // current playlist. Fires when the channel list arrives (cache or
    // network); the guard keeps a refresh from re-opening the player.
    LaunchedEffect(channels) {
        if (resumedLast || !resumeOnLaunch || channels.isEmpty()) return@LaunchedEffect
        resumedLast = true
        val lastUrl = recents.firstOrNull() ?: return@LaunchedEffect
        val last = channels.firstOrNull { it.streamUrl == lastUrl } ?: return@LaunchedEffect
        PlayerFactory.loadChannel(player, last.streamUrl)
        selectedChannel = last
    }

    val current = selectedChannel
    if (current == null) {
        ChannelListScreen(
            channels = channels,
            error = loadError,
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
                scope.launch {
                    try {
                        channels = repository.refreshChannels(playlistUrl)
                        loadError = null
                    } catch (e: Exception) {
                        loadError = "Refresh failed: ${e.message}"
                    }
                }
            },
            onOpenSettings = { showSettings = true }
        )

        if (showSettings) {
            SettingsDialog(
                currentUrl = playlistUrl,
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
                        if (draft.playlistUrl != playlistUrl) {
                            // Throws before the Room cache is replaced if the
                            // fetch fails or the playlist parses to nothing.
                            val loaded = repository.refreshChannels(draft.playlistUrl)
                            channels = loaded
                            playlistUrl = draft.playlistUrl
                            settings.setPlaylistUrl(draft.playlistUrl)
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
