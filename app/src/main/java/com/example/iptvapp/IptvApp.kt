package com.example.iptvapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.example.iptvapp.movies.MoviesScreen
import com.example.iptvapp.movies.MoviesViewModel

/**
 * Top-level screen toggle: shows the channel list until something is
 * tapped, then switches to the player screen. All data and screen state
 * live in [IptvViewModel] so they survive Activity recreation; this
 * composable just renders that state and forwards events back to the VM.
 *
 * The player is a MediaController proxying the session player in
 * PlaybackService; it's passed into VM methods that drive playback rather
 * than held by the VM, so no controller leaks across Activity death. On
 * first load the UI resyncs to whatever the session is already playing.
 *
 * Movies navigation: a separate [MoviesViewModel] owns all movie state —
 * nothing movie-related lives in [IptvViewModel]. When [showMovies] is
 * true, the Movies screen replaces the IPTV channel list / player.
 */
@Composable
fun IptvApp(
    vm: IptvViewModel,
    player: Player,
    isInPip: Boolean,
    onOpenMovies: () -> Unit,
    onMovieBack: () -> Unit,
    onMovieSelected: (com.example.iptvapp.movies.VidSrcItem) -> Unit
) {
    // Movies screen state — local to this composable, never in IptvViewModel.
    var showMovies by remember { mutableStateOf(false) }
    val moviesViewModel = remember { MoviesViewModel() }

    // Once channels are available, attach to the already-playing session or
    // resume the last channel. Keyed on channels so it fires when the list
    // first arrives; the VM guards against re-running after a refresh.
    LaunchedEffect(vm.channels) {
        if (!showMovies) vm.attachToPlayingOrResume(player)
    }

    // Keep playingUrl live as the session loads, switches or stops — mostly
    // for background play, where the list and notification are the only
    // windows onto playback.
    DisposableEffect(player) {
        vm.updatePlayingUrl(player.currentMediaItem?.mediaId)
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                vm.updatePlayingUrl(mediaItem?.mediaId)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    if (showMovies) {
        MoviesScreen(
            viewModel = moviesViewModel,
            onMovieSelected = { item ->
                // Pause IPTV before starting a movie to prevent overlapping audio.
                vm.back(player)
                onMovieSelected(item)
            },
            onBack = {
                showMovies = false
                onMovieBack()
            }
        )
    } else {
        val current = vm.selectedChannel
        if (current == null) {
            ChannelListScreen(
                channels = vm.channels,
                error = vm.loadError,
                isRefreshing = vm.isRefreshing,
                lastRefreshText = vm.lastRefreshText,
                playingUrl = vm.playingUrl,
                showLogos = vm.showLogos,
                hideGeoBlocked = vm.hideGeoBlocked,
                hideNot24x7 = vm.hideNot24x7,
                hdOnly = vm.hdOnly,
                favourites = vm.favourites,
                recents = vm.recents,
                deadChannels = vm.deadChannels,
                onToggleFavourite = { channel -> vm.toggleFavourite(channel) },
                onChannelSelected = { channel -> vm.selectChannel(channel) },
                onRefresh = { vm.refresh() },
                onOpenSettings = { vm.openSettings() },
                onOpenMovies = { onOpenMovies(); showMovies = true },
                onPlaySequenceChanged = { vm.onPlaySequenceChanged(it) }
            )

            if (vm.showSettings) {
                SettingsDialog(
                    playlists = vm.playlists,
                    initialActivePlaylistId = vm.activePlaylistId,
                    initialShowLogos = vm.showLogos,
                    initialHideGeoBlocked = vm.hideGeoBlocked,
                    initialHideNot24x7 = vm.hideNot24x7,
                    initialHdOnly = vm.hdOnly,
                    initialResumeOnLaunch = vm.resumeOnLaunch,
                    initialDataSaver = vm.dataSaver,
                    initialAudioOnly = vm.audioOnly,
                    initialEpgLoose = vm.epgLoose,
                    initialEpgUrl = vm.epgManualOverride,
                    epgStatus = vm.epgStatus,
                    onDismiss = { vm.closeSettings() },
                    onSave = { draft -> vm.saveSettings(player, draft) }
                )
            }
        } else {
            PlayerScreen(
                player = player,
                selectedChannel = current,
                isInPip = isInPip,
                dataSaver = vm.dataSaver,
                audioOnly = vm.audioOnly,
                nowNext = vm.nowNext,
                onToggleDataSaver = { vm.toggleDataSaver(player) },
                onToggleAudioOnly = { vm.toggleAudioOnly(player) },
                onPrevChannel = { vm.moveChannel(current, -1) },
                onNextChannel = { vm.moveChannel(current, 1) },
                onPlaybackFailed = { vm.onPlaybackFailed() },
                onPlaybackSucceeded = { vm.onPlaybackSucceeded() },
                onBack = { vm.back(player) }
            )
        }
    }
}
