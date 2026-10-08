package com.example.iptvapp

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.media3.exoplayer.ExoPlayer
import coil3.SingletonImageLoader
import com.example.iptvapp.player.PlayerFactory
import com.example.iptvapp.playlist.AppDatabase
import com.example.iptvapp.playlist.PlaylistRepository

/**
 * Single-activity entry point (SPEC §2.4). Creates the player, the
 * playlist repository, the settings store, and the favourites store once,
 * then hands them to the Compose tree — no navigation library, just a
 * state toggle in IptvApp.kt between "channel list" and "player".
 */
class MainActivity : ComponentActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var repository: PlaylistRepository
    private lateinit var settings: SettingsStore
    private lateinit var favouritesStore: FavouritesStore
    private lateinit var recentsStore: RecentsStore
    private lateinit var deadStore: DeadChannelStore

    // Set from onPictureInPictureModeChanged; read in setContent so the
    // Compose tree recomposes when PiP mode changes.
    private val isInPip = mutableStateOf(false)

    // Set only when onStop pauses playback on the way to the background;
    // onStart resumes exactly what this flag paused — never a pause the
    // user or the system chose.
    private var pausedForBackgroundExit = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            // Pass applicationContext (a plain android.content.Context)
            // rather than the factory's PlatformContext param, so the
            // signature can't depend on how Coil aliases that type.
            SingletonImageLoader.setSafe { buildImageLoader(applicationContext) }
        } catch (ignored: IllegalStateException) {
            // Singleton already configured on this process; reuse it.
        }

        player = PlayerFactory.create(this)
        repository = PlaylistRepository(AppDatabase.getInstance(this).channelDao())
        settings = SettingsStore(this)
        favouritesStore = FavouritesStore(this)
        recentsStore = RecentsStore(this)
        deadStore = DeadChannelStore(this)

        applyPlaybackPreferences(player, settings.dataSaver(), settings.audioOnly())

        setContent {
            TribalTvTheme {
                IptvApp(
                    initialPlaylistUrl = settings.playlistUrl(),
                    settings = settings,
                    favouritesStore = favouritesStore,
                    recentsStore = recentsStore,
                    deadStore = deadStore,
                    repository = repository,
                    player = player,
                    isInPip = isInPip.value
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (pausedForBackgroundExit) {
            pausedForBackgroundExit = false
            player.play()
        }
    }

    override fun onStop() {
        super.onStop()
        // Backgrounding without PiP pauses playback — there is no media
        // notification, so playback left running would be uncontrollable
        // invisible audio. Dismissing the PiP window also lands here, by
        // which point isInPictureInPictureMode is already false.
        if (!isInPictureInPictureMode && player.playWhenReady) {
            pausedForBackgroundExit = true
            player.pause()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPip.value = isInPictureInPictureMode
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }
}