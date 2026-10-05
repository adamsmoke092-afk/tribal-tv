package com.example.iptvapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.media3.exoplayer.ExoPlayer
import com.example.iptvapp.player.PlayerFactory
import com.example.iptvapp.playlist.AppDatabase
import com.example.iptvapp.playlist.PlaylistRepository

/**
 * Single-activity entry point (SPEC §2.4). Creates the player and the
 * playlist repository once, then hands them to the Compose tree — no
 * navigation library, just a state toggle in IptvApp.kt between "channel
 * list" and "player", since this is a personal single-screen app rather
 * than something needing a back stack.
 */
class MainActivity : ComponentActivity() {

    // Default: iptv-org's South Africa playlist (SABC etc.). Swap in any
    // M3U URL here, or wire up a settings screen later.
    private val playlistUrl = "https://iptv-org.github.io/iptv/countries/za.m3u"

    private lateinit var player: ExoPlayer
    private lateinit var repository: PlaylistRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        player = PlayerFactory.create(this)
        repository = PlaylistRepository(AppDatabase.getInstance(this).channelDao())

        setContent {
            TribalTvTheme {
                IptvApp(
                    playlistUrl = playlistUrl,
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
