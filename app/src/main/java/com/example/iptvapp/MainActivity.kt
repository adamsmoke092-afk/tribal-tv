package com.example.iptvapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

        setContent {
            TribalTvTheme {
                IptvApp(
                    initialPlaylistUrl = settings.playlistUrl(),
                    settings = settings,
                    favouritesStore = favouritesStore,
                    recentsStore = recentsStore,
                    repository = repository,
                    player = player
                )
            }
        }
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }
}