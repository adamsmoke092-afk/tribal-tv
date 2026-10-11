package com.example.iptvapp

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.example.iptvapp.player.PlayerFactory
import com.example.iptvapp.playlist.AppDatabase
import com.example.iptvapp.playlist.M3uChannel
import com.example.iptvapp.playlist.PlaylistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns all app data + screen state so it survives Activity recreation
 * (rotation, dark-mode toggle, multi-window, config changes not in the
 * manifest's configChanges list) instead of being rebuilt from disk every
 * time. Also moves the store/repository setup and the first reads OFF the
 * main thread (previously done synchronously in MainActivity.onCreate).
 *
 * State lives as Compose `mutableStateOf` properties so the Compose tree
 * reads them directly and recomposes on change. The player (a
 * MediaController) is Activity/session-scoped and is NOT held here — it's
 * passed into the methods that drive playback, so this ViewModel never
 * leaks a controller across Activity death.
 */
class IptvViewModel(application: Application) : AndroidViewModel(application) {

    // Backing stores/repository — created off the main thread in init.
    private lateinit var settings: SettingsStore
    private lateinit var playlistStore: PlaylistStore
    private lateinit var favouritesStore: FavouritesStore
    private lateinit var recentsStore: RecentsStore
    private lateinit var deadStore: DeadChannelStore
    private lateinit var repository: PlaylistRepository

    // True once stores are built and initial settings/user data are loaded.
    // The UI shows a spinner until then.
    var ready by mutableStateOf(false)
        private set

    var channels by mutableStateOf<List<M3uChannel>>(emptyList())
        private set
    var selectedChannel by mutableStateOf<M3uChannel?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var playlistUrl by mutableStateOf("")
        private set
    var playlists by mutableStateOf<List<SavedPlaylist>>(emptyList())
        private set
    var activePlaylistId by mutableStateOf(NEW_PLAYLIST_ID)
        private set
    var showLogos by mutableStateOf(true)
        private set
    var hideGeoBlocked by mutableStateOf(false)
        private set
    var hideNot24x7 by mutableStateOf(false)
        private set
    var hdOnly by mutableStateOf(false)
        private set
    var resumeOnLaunch by mutableStateOf(false)
        private set
    var dataSaver by mutableStateOf(false)
        private set
    var audioOnly by mutableStateOf(false)
        private set
    var favourites by mutableStateOf<Set<String>>(emptySet())
        private set
    var recents by mutableStateOf<List<String>>(emptyList())
        private set
    var deadChannels by mutableStateOf<Set<String>>(emptySet())
        private set
    var isRefreshing by mutableStateOf(false)
        private set
    var playSequence by mutableStateOf<List<M3uChannel>>(emptyList())
        private set
    var playingUrl by mutableStateOf<String?>(null)
        private set
    var showSettings by mutableStateOf(false)
        private set
    var lastRefreshText by mutableStateOf<String?>(null)
        private set

    // Guards so a refresh (which re-emits channels) can't re-trigger the
    // once-per-session resume/attach.
    private var attached = false

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val app = getApplication<Application>()
                settings = SettingsStore(app)
                repository = PlaylistRepository(
                    AppDatabase.getInstance(app).channelDao(),
                    onRefreshed = { settings.setLastRefreshMs(System.currentTimeMillis()) }
                )
                playlistStore = PlaylistStore(app)
                favouritesStore = FavouritesStore(app)
                recentsStore = RecentsStore(app)
                deadStore = DeadChannelStore(app)
                playlistStore.ensureSeeded(settings.playlistUrl())
            }
            // Back on the main thread: publish initial state.
            val active = playlistStore.activePlaylist()
            playlistUrl = active.url
            playlists = playlistStore.playlists()
            activePlaylistId = active.id
            showLogos = settings.showLogos()
            hideGeoBlocked = settings.hideGeoBlocked()
            hideNot24x7 = settings.hideNot24x7()
            hdOnly = settings.hdOnly()
            resumeOnLaunch = settings.resumeOnLaunch()
            dataSaver = settings.dataSaver()
            audioOnly = settings.audioOnly()
            favourites = favouritesStore.favourites()
            recents = recentsStore.recents()
            deadChannels = deadStore.dead()
            ready = true

            loadInitialChannels()
        }
    }

    private fun setChannels(list: List<M3uChannel>) {
        channels = list
        lastRefreshText = formatLastRefreshText(System.currentTimeMillis(), settings.lastRefreshMs())
    }

    private suspend fun loadInitialChannels() {
        try {
            setChannels(repository.getChannels(playlistUrl))
        } catch (e: Exception) {
            loadError = "Failed to load playlist: ${e.message}"
        }
        // Cache older than a day: refresh silently while the cache keeps
        // serving; a failure keeps the cache and only shows the banner.
        if (System.currentTimeMillis() - settings.lastRefreshMs() > STALE_PLAYLIST_MS) {
            try {
                setChannels(repository.refreshChannels(playlistUrl))
                loadError = null
            } catch (e: Exception) {
                loadError = "Background refresh failed — showing cached channels"
            }
        }
    }

    /**
     * Once per session, after channels arrive: if the session is already
     * playing something (app reopened from background), land back on that
     * channel; otherwise, with resume-on-launch, reopen the last channel.
     */
    fun attachToPlayingOrResume(player: Player) {
        if (attached || channels.isEmpty()) return
        attached = true
        val playing = player.currentMediaItem?.mediaId
        if (playing != null) {
            selectedChannel = channels.firstOrNull { it.streamUrl == playing }
        } else if (resumeOnLaunch) {
            val lastUrl = recents.firstOrNull() ?: return
            val last = channels.firstOrNull { it.streamUrl == lastUrl } ?: return
            PlayerFactory.loadChannel(player, last.streamUrl, last.headers)
            selectedChannel = last
        }
    }

    fun updatePlayingUrl(mediaId: String?) {
        playingUrl = mediaId
    }

    fun selectChannel(player: Player, channel: M3uChannel) {
        PlayerFactory.loadChannel(player, channel.streamUrl, channel.headers)
        selectedChannel = channel
        recentsStore.add(channel.streamUrl)
        recents = recentsStore.recents()
    }

    /** Channel zapping through the list the user came from, skipping dead ones. */
    fun moveChannel(player: Player, from: M3uChannel, delta: Int) {
        val alive = playSequence
            .filterNot { it.streamUrl in deadChannels }
            .ifEmpty { playSequence }
        if (alive.isEmpty()) return
        val index = alive.indexOfFirst { it.streamUrl == from.streamUrl }
        if (index < 0) return
        val next = alive[(index + delta).mod(alive.size)]
        PlayerFactory.loadChannel(player, next.streamUrl, next.headers)
        selectedChannel = next
        recentsStore.add(next.streamUrl)
        recents = recentsStore.recents()
    }

    fun toggleFavourite(channel: M3uChannel) {
        favourites = favouritesStore.toggle(channel.streamUrl)
    }

    fun onPlaySequenceChanged(sequence: List<M3uChannel>) {
        playSequence = sequence
    }

    fun openSettings() { showSettings = true }
    fun closeSettings() { showSettings = false }

    fun back(player: Player) {
        player.stop()
        selectedChannel = null
    }

    fun refresh() {
        if (isRefreshing) return
        viewModelScope.launch {
            isRefreshing = true
            try {
                setChannels(repository.refreshChannels(playlistUrl))
                loadError = null
            } catch (e: Exception) {
                loadError = "Refresh failed: ${e.message}"
            } finally {
                isRefreshing = false
            }
        }
    }

    fun toggleDataSaver(player: Player) {
        dataSaver = !dataSaver
        settings.setDataSaver(dataSaver)
        applyPlaybackPreferences(player, dataSaver, audioOnly)
    }

    fun toggleAudioOnly(player: Player) {
        audioOnly = !audioOnly
        settings.setAudioOnly(audioOnly)
        applyPlaybackPreferences(player, dataSaver, audioOnly)
    }

    fun onPlaybackFailed() {
        val current = selectedChannel ?: return
        // Audio-only mode fails on a video-less stream; that says nothing
        // about the channel being dead.
        if (!audioOnly) deadChannels = deadStore.mark(current.streamUrl)
    }

    fun onPlaybackSucceeded() {
        val current = selectedChannel ?: return
        if (current.streamUrl in deadChannels) {
            deadChannels = deadStore.clear(current.streamUrl)
        }
    }

    /**
     * Applies the settings dialog's draft. Mirrors the original inline
     * save: a playlist switch/add is fetched and parsed FIRST, so a bad
     * URL or empty playlist throws before any store or cache is touched,
     * leaving everything as it was. Returns null on success, else an error.
     */
    suspend fun saveSettings(player: Player, draft: SettingsDraft): String? {
        return try {
            val activeId = playlistStore.activePlaylist().id
            val newName = draft.newPlaylistName
            val newUrl = draft.newPlaylistUrl
            val targetUrl: String = when {
                newName != null && newUrl != null -> newUrl
                draft.selectedPlaylistId != activeId -> playlistStore.playlists()
                    .firstOrNull { it.id == draft.selectedPlaylistId }
                    ?.url
                    ?: throw IllegalStateException("Playlist not found")
                else -> ""
            }
            if (targetUrl.isNotEmpty()) {
                // Fetch/parse FIRST — throws before the cache or any store
                // is touched, so a failure leaves everything as it was.
                val loaded = repository.refreshChannels(targetUrl)
                draft.renamedPlaylists.forEach { (id, name) -> playlistStore.rename(id, name) }
                draft.removedPlaylistIds.forEach { playlistStore.remove(it) }
                if (newName != null && newUrl != null) {
                    val added = playlistStore.add(newName, newUrl)
                    playlistStore.setActive(added.id)
                } else {
                    playlistStore.setActive(draft.selectedPlaylistId)
                }
                setChannels(loaded)
                playlistUrl = targetUrl
            } else {
                draft.renamedPlaylists.forEach { (id, name) -> playlistStore.rename(id, name) }
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
}
