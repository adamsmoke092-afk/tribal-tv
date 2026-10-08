package com.example.iptvapp

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil3.SingletonImageLoader
import com.example.iptvapp.playlist.AppDatabase
import com.example.iptvapp.playlist.PlaylistRepository
import com.google.common.util.concurrent.ListenableFuture

/**
 * Single-activity entry point (SPEC §2.4). Creates the playlist
 * repository, the settings store, and the favourites store once, then
 * hands them to the Compose tree — no navigation library, just a state
 * toggle in IptvApp.kt between "channel list" and "player".
 *
 * Background play: the player itself lives in PlaybackService (a
 * MediaSessionService), so playback and its media notification outlive
 * the UI. This activity connects a MediaController — which implements
 * the Player interface — and passes it down where the activity-owned
 * ExoPlayer used to go. The Compose tree shows a spinner until the
 * controller connects.
 */
class MainActivity : ComponentActivity() {

    private lateinit var repository: PlaylistRepository
    private lateinit var settings: SettingsStore
    private lateinit var playlistStore: PlaylistStore
    private lateinit var favouritesStore: FavouritesStore
    private lateinit var recentsStore: RecentsStore
    private lateinit var deadStore: DeadChannelStore

    // Set from onPictureInPictureModeChanged; read in setContent so the
    // Compose tree recomposes when PiP mode changes.
    private val isInPip = mutableStateOf(false)

    // The UI's handle on the session player. Null until the service
    // connection completes; a spinner is shown meanwhile.
    private val mediaController = mutableStateOf<MediaController?>(null)
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Result unused: if denied, playback still runs in the
            // background but the media notification stays hidden (13+).
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android 13+ hides the media notification without this permission
        // — background playback would be invisible and uncontrollable.
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        try {
            // Pass applicationContext (a plain android.content.Context)
            // rather than the factory's PlatformContext param, so the
            // signature can't depend on how Coil aliases that type.
            SingletonImageLoader.setSafe { buildImageLoader(applicationContext) }
        } catch (ignored: IllegalStateException) {
            // Singleton already configured on this process; reuse it.
        }

        settings = SettingsStore(this)
        repository = PlaylistRepository(
            AppDatabase.getInstance(this).channelDao(),
            onRefreshed = { settings.setLastRefreshMs(System.currentTimeMillis()) }
        )
        playlistStore = PlaylistStore(this)
        playlistStore.ensureSeeded(settings.playlistUrl())
        favouritesStore = FavouritesStore(this)
        recentsStore = RecentsStore(this)
        deadStore = DeadChannelStore(this)

        connectController()

        setContent {
            TribalTvTheme {
                val controller = mediaController.value
                if (controller == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    IptvApp(
                        initialPlaylistUrl = playlistStore.activePlaylist().url,
                        settings = settings,
                        playlistStore = playlistStore,
                        favouritesStore = favouritesStore,
                        recentsStore = recentsStore,
                        deadStore = deadStore,
                        repository = repository,
                        player = controller,
                        isInPip = isInPip.value
                    )
                }
            }
        }
    }

    private fun connectController() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                val controller = try {
                    future.get()
                } catch (e: Exception) {
                    null
                }
                if (controller != null) {
                    if (isDestroyed) {
                        // The activity died mid-connection; release now or
                        // the leaked controller keeps the service bound.
                        controller.release()
                    } else {
                        mediaController.value = controller
                    }
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPip.value = isInPictureInPictureMode
    }

    override fun onDestroy() {
        val future = controllerFuture
        controllerFuture = null
        val controller = mediaController.value
        mediaController.value = null
        controller?.release()
        if (future != null && !future.isDone) {
            future.cancel(false)
        }
        super.onDestroy()
    }
}
