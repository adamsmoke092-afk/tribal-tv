package com.example.iptvapp

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.C
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.iptvapp.player.PlayerFactory

/**
 * Owns the player so playback outlives the UI: a MediaSessionService
 * wrapping the ExoPlayer in a MediaSession. While something is playing,
 * the service runs in the foreground with a media notification
 * (play/pause, tap to return to the app); when playback stops it drops
 * back to background and the notification goes away. The default
 * onTaskRemoved keeps playback going when the app is swiped away, and
 * stops the service when nothing is playing.
 *
 * The UI never touches the ExoPlayer directly — MainActivity connects a
 * MediaController (which implements Player) and proxies everything
 * through the session.
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = PlayerFactory.create(this).apply {
            // Keeps the CPU/wifi awake while the screen is off and a stream
            // is playing or buffering (needs WAKE_LOCK, declared in the
            // manifest). Audio focus is already handled by PlayerFactory.
            setWakeMode(C.WAKE_MODE_NETWORK)
        }
        val settings = SettingsStore(this)
        applyPlaybackPreferences(player, settings.dataSaver(), settings.audioOnly())
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        // MediaSession.release() does NOT release the wrapped player —
        // the service must release both (MediaSessionService contract).
        mediaSession?.let { session ->
            session.player.release()
            session.release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
