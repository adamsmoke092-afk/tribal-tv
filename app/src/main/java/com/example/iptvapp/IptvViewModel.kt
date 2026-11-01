package com.example.iptvapp

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.example.iptvapp.epg.EpgRefreshResult
import com.example.iptvapp.epg.EpgRefreshState
import com.example.iptvapp.epg.EpgRepository
import com.example.iptvapp.epg.EpgSourceResult
import com.example.iptvapp.epg.NowNext
import com.example.iptvapp.epg.epgRefreshState
import com.example.iptvapp.epg.epgSourceKeys
import com.example.iptvapp.playlist.AppDatabase
import com.example.iptvapp.playlist.M3uChannel
import com.example.iptvapp.playlist.PlaylistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// A cached EPG older than this gets a background refresh on launch.
private const val STALE_EPG_MS: Long = 6 * 60 * 60 * 1000L

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
    private lateinit var epgRepository: EpgRepository

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
    // This playlist's OWN manual EPG override (was one global setting).
    var epgManualOverride by mutableStateOf("")
        private set
    // The guide URL(s) this playlist declared in its own M3U header, if any.
    var epgHeaderUrl by mutableStateOf("")
        private set
    // Loose quality-alias matching for EPG ids. Default OFF (exact only).
    var epgLoose by mutableStateOf(false)
        private set
    // What the diagnostics line shows for this playlist's EPG.
    var epgStatus by mutableStateOf<EpgUiStatus?>(null)
        private set
    var epgLoading by mutableStateOf(false)
        private set
    // Now/next for the currently selected channel; null when EPG is off or
    // the channel has no guide data.
    var nowNext by mutableStateOf<NowNext?>(null)
        private set

    // Guards so a refresh (which re-emits channels) can't re-trigger the
    // once-per-session resume/attach.
    private var attached = false

    // The EPG refresh for the CURRENT playlist. Cancelled and replaced when
    // the playlist changes, so an old playlist's network work can neither
    // overwrite the new playlist's diagnostics nor keep running pointlessly.
    private var epgRefreshJob: Job? = null
    // Bumped on every playlist switch; a refresh captures the value it started
    // with and abandons its UI writes if it no longer matches. Cancelling the
    // job is the primary guard; this catches anything already past a
    // suspension point when the cancel lands.
    private var epgGeneration = 0

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val app = getApplication<Application>()
                settings = SettingsStore(app)
                val db = AppDatabase.getInstance(app)
                repository = PlaylistRepository(
                    db.channelDao(),
                    onRefreshed = { settings.setLastRefreshMs(System.currentTimeMillis()) }
                )
                epgRepository = EpgRepository(db.epgDao())
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
            epgLoose = settings.epgLooseMatching()
            // One-time carry-over of the old global EPG setting onto the
            // playlist that is active right now, then per-playlist from here.
            playlistStore.migrateLegacyEpgOverride(settings.epgUrl(), active.id)
            epgManualOverride = playlistStore.epgOverride(active.id)
            epgHeaderUrl = playlistStore.epgHeaderUrl(active.id)
            ready = true

            loadInitialChannels()
            maybeRefreshEpg()
        }
    }

    /**
     * Refreshes the EPG cache in the background when at least one guide URL is
     * in play and the cache is empty or stale. Silent: EPG is a nice-to-have,
     * so a failure leaves now/next blank and shows up as diagnostics rather
     * than an error dialog.
     *
     * Job + generation guards (requirement: an asynchronous refresh belonging
     * to an old playlist must never touch the newly selected playlist's state):
     * the previous job is cancelled before a new one starts, and the new job
     * captures the generation it was created for, refusing every UI write if
     * the playlist has changed since — including between two suspension points
     * where a cancel could not land.
     */
    private fun maybeRefreshEpg(force: Boolean = false) {
        epgGeneration++
        val generation = epgGeneration
        val urls = effectiveEpgUrls()
        if (urls.isEmpty()) {
            epgRefreshJob?.cancel()
            epgRefreshJob = null
            epgStatus = epgStatusOff()
            return
        }
        epgRefreshJob?.cancel()
        epgRefreshJob = viewModelScope.launch {
            epgLoading = true
            try {
                val stale = force || System.currentTimeMillis() - settings.lastEpgRefreshMs() > STALE_EPG_MS
                val keys = epgSourceKeys(activePlaylistId.toString(), urls)
                val hasData = runCatching { epgRepository.hasData(keys) }.getOrDefault(false)
                if (hasData && !stale) {
                    if (generation != epgGeneration) return@launch
                    epgStatus = buildEpgStatus(result = null, isLoading = false)
                    refreshNowNext()
                    return@launch
                }
                val channelIds = channels.mapNotNull { it.tvgId?.takeIf { id -> id.isNotBlank() } }.toSet()
                if (channelIds.isEmpty()) {
                    if (generation != epgGeneration) return@launch
                    epgStatus = buildEpgStatus(result = null, isLoading = false)
                    refreshNowNext()
                    return@launch
                }
                val result = epgRepository.refresh(
                    playlistKey = activePlaylistId.toString(),
                    guideUrls = urls,
                    playlistIds = channelIds,
                    looseMatching = epgLoose
                )
                settings.setLastEpgRefreshMs(System.currentTimeMillis())
                if (generation != epgGeneration) return@launch // playlist switched mid-refresh
                epgStatus = buildEpgStatus(result = result, isLoading = false)
                refreshNowNext()
            } finally {
                if (generation == epgGeneration) epgLoading = false
            }
        }
    }

    /**
     * The guide URLs actually in force for the ACTIVE playlist, by precedence:
     * that playlist's manual override, else the guide URL its own M3U header
     * declares, else none. The two are never combined, so a failing manual
     * override cannot silently fall back to the playlist's URL.
     *
     * Internal rather than private so tests can assert precedence directly.
     */
    internal fun effectiveEpgUrls(): List<String> =
        effectiveEpgUrlsFor(epgManualOverride, epgHeaderUrl)

    /** Recomputes now/next for the selected channel's tvg-id. */
    private fun refreshNowNext() {
        val channel = selectedChannel
        val tvgId = channel?.tvgId
        if (tvgId.isNullOrBlank()) {
            nowNext = null
            return
        }
        val keys = epgSourceKeys(activePlaylistId.toString(), effectiveEpgUrls())
        viewModelScope.launch {
            val result = runCatching { epgRepository.nowNext(tvgId, keys) }.getOrNull()
            // A zap mid-lookup must not show the previous channel's schedule.
            if (selectedChannel?.tvgId == tvgId) nowNext = result
        }
    }

    /** Builds the diagnostics state from the last refresh plus the active URLs. */
    private suspend fun buildEpgStatus(result: EpgRefreshResult?, isLoading: Boolean): EpgUiStatus {
        val urls = effectiveEpgUrls()
        val origin = epgSourceOrigin(epgManualOverride, epgHeaderUrl)
        val keys = epgSourceKeys(activePlaylistId.toString(), urls)
        val cached = runCatching { epgRepository.countCached(keys) }.getOrDefault(0)
        return EpgUiStatus(
            state = result?.state(isLoading) ?: epgRefreshState(emptyList(), cached, isLoading),
            sources = result?.sources ?: emptyList(),
            loadedSources = result?.summary?.loaded ?: 0,
            failedSources = result?.summary?.failed ?: 0,
            truncatedSources = result?.summary?.truncated ?: 0,
            matchedChannels = result?.matchedChannels ?: 0,
            cachedProgrammes = cached,
            activeUrls = urls,
            origin = origin
        )
    }

    /** Diagnostics for a playlist with no guide configured at all. */
    private fun epgStatusOff(): EpgUiStatus = EpgUiStatus(
        state = EpgRefreshState.NO_GUIDE_CONFIGURED,
        sources = emptyList(),
        loadedSources = 0,
        failedSources = 0,
        truncatedSources = 0,
        matchedChannels = 0,
        cachedProgrammes = 0,
        activeUrls = emptyList(),
        origin = EpgSourceOrigin.NONE
    )

    private fun updateChannels(list: List<M3uChannel>) {
        channels = list
        lastRefreshText = formatLastRefreshText(System.currentTimeMillis(), settings.lastRefreshMs())
    }

    private suspend fun loadInitialChannels() {
        try {
            updateChannels(repository.getChannels(playlistUrl))
        } catch (e: Exception) {
            loadError = "Failed to load playlist: ${e.message}"
        }
        // Cache older than a day: refresh silently while the cache keeps
        // serving; a failure keeps the cache and only shows the banner.
        if (System.currentTimeMillis() - settings.lastRefreshMs() > STALE_PLAYLIST_MS) {
            try {
                updateChannels(repository.refreshChannels(playlistUrl))
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
            // Loading is owned by PlayerScreen: it reacts to the
            // selectedChannel change and loads the stream (unless the player
            // is already on it). Keeping the load out of the VM means the
            // recovery listener is disposed with the screen rather than
            // accumulating in process state.
            selectedChannel = last
        }
        if (selectedChannel != null) refreshNowNext()
    }

    fun updatePlayingUrl(mediaId: String?) {
        playingUrl = mediaId
    }

    fun selectChannel(channel: M3uChannel) {
        selectedChannel = channel
        // Never let the previous channel's schedule flash on the new one.
        nowNext = null
        recentsStore.add(channel.streamUrl)
        recents = recentsStore.recents()
        refreshNowNext()
    }

    /** Channel zapping through the list the user came from, skipping dead ones. */
    fun moveChannel(from: M3uChannel, delta: Int) {
        val alive = playSequence
            .filterNot { it.streamUrl in deadChannels }
            .ifEmpty { playSequence }
        if (alive.isEmpty()) return
        val index = alive.indexOfFirst { it.streamUrl == from.streamUrl }
        if (index < 0) return
        val next = alive[(index + delta).mod(alive.size)]
        selectedChannel = next
        nowNext = null
        recentsStore.add(next.streamUrl)
        recents = recentsStore.recents()
        refreshNowNext()
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
        nowNext = null
    }

    fun refresh() {
        if (isRefreshing) return
        viewModelScope.launch {
            isRefreshing = true
            try {
                // Re-read the playlist's own header so its declared guide
                // stays current, then show the refreshed channels.
                //
                // Whether a fresh EPG download is warranted depends on the
                // EFFECTIVE source (a manual override outranks the playlist
                // header), not the header alone: with an override in force a
                // changed header must NOT trigger a download of an unchanged
                // manual guide, and adding/removing an override switches the
                // source even when the header did not change.
                val effectiveBefore = effectiveEpgUrls()
                val playlist = repository.fetchPlaylist(playlistUrl)
                val headerUrls = playlist.epgUrls.joinToString(",")
                playlistStore.setEpgHeaderUrl(activePlaylistId, headerUrls)
                epgHeaderUrl = headerUrls
                updateChannels(playlist.channels)
                loadError = null
                // Force only when the source actually in use changed; otherwise
                // fall through to the normal 6h stale-cache policy.
                maybeRefreshEpg(
                    force = shouldForceEpgRefresh(effectiveBefore, effectiveEpgUrls())
                )
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
            // Set when something about the guide changes (active playlist
            // swapped, or the manual EPG URL edited) — refetched once at
            // the end, so a Save that changes both doesn't fetch twice.
            var epgNeedsRefetch = false
            if (targetUrl.isNotEmpty()) {
                // Fetch/parse FIRST — throws before the cache or any store
                // is touched, so a failure leaves everything as it was.
                val playlist = repository.fetchPlaylist(targetUrl)
                draft.renamedPlaylists.forEach { (id, name) -> playlistStore.rename(id, name) }
                draft.removedPlaylistIds.forEach { playlistStore.remove(it) }
                if (newName != null && newUrl != null) {
                    val added = playlistStore.add(newName, newUrl)
                    playlistStore.setActive(added.id)
                } else {
                    playlistStore.setActive(draft.selectedPlaylistId)
                }
                val activeId = playlistStore.activePlaylist().id
                // Persist what THIS playlist declares in its own header. An
                // empty header clears the stored URL: the playlist changed.
                val headerUrls = playlist.epgUrls.joinToString(",")
                playlistStore.setEpgHeaderUrl(activeId, headerUrls)

                // Restore THAT playlist's own EPG configuration, then switch
                // the UI over to it. Cancels any in-flight refresh belonging
                // to the old playlist and bumps the generation so a result
                // that already escaped cancellation cannot be written.
                epgRefreshJob?.cancel()
                epgGeneration++
                activePlaylistId = activeId
                playlistUrl = targetUrl
                updateChannels(playlist.channels)
                epgManualOverride = playlistStore.epgOverride(activeId)
                epgHeaderUrl = headerUrls
                // The EPG cache is scoped to the OLD playlist's source keys —
                // a playlist swap invalidates it even when the guide URL is
                // unchanged (its rows belong to another playlist's keys).
                settings.setLastEpgRefreshMs(0L)
                nowNext = null
                epgStatus = null
                epgNeedsRefetch = true
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
            // The effective source set before applying EPG-related draft
            // changes, so a whitespace-only or reordered override doesn't
            // force an unnecessary download.
            val epgSourceSetBefore = settings.normalizedUrlSet(effectiveEpgUrls())
            if (draft.epgLoose != epgLoose) {
                epgLoose = draft.epgLoose
                settings.setEpgLooseMatching(draft.epgLoose)
                // Matching changed: same sources, different rows. Refetch.
                epgNeedsRefetch = true
            }
            if (draft.epgUrl != epgManualOverride) {
                // The EPG field edits THIS playlist's override — no other
                // playlist's configuration is touched.
                playlistStore.setEpgOverride(activePlaylistId, draft.epgUrl)
                epgManualOverride = draft.epgUrl
                // Only invalidate the cache and force a download when the
                // effective source set actually changed. Whitespace-only edits
                // or reordering of equivalent lists keep the same source set
                // and fall through to the normal staleness window; the stored
                // value and diagnostics origin are still updated correctly.
                if (settings.normalizedUrlSet(effectiveEpgUrls()) != epgSourceSetBefore) {
                    settings.setLastEpgRefreshMs(0L)
                    nowNext = null
                    epgNeedsRefetch = true
                }
            }
            if (epgNeedsRefetch) maybeRefreshEpg(force = true)
            playlists = playlistStore.playlists()
            activePlaylistId = playlistStore.activePlaylist().id
            null
        } catch (e: Exception) {
            e.message ?: "Failed to load that playlist"
        }
    }
}

/**
 * What the settings dialog shows about the active playlist's EPG. Pure data —
 * the dialog formats it. [activeUrls] and [origin] answer "which guide is in
 * force and did it come from me or from the playlist"; the counts come from
 * the last refresh.
 */
data class EpgUiStatus(
    val state: EpgRefreshState,
    val sources: List<EpgSourceResult>,
    val loadedSources: Int,
    val failedSources: Int,
    val truncatedSources: Int,
    val matchedChannels: Int,
    val cachedProgrammes: Int,
    val activeUrls: List<String>,
    val origin: EpgSourceOrigin
)
