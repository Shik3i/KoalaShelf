package net.koalastuff.koalashelf.core.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.koin.android.ext.android.inject

/**
 * AndroidX Media3 `MediaLibraryService` providing:
 * - Persistent Android 13+ Media Notification with progress scrubbing, Play/Pause,
 *   configurable Skip Back / Skip Forward actions, and cover artwork.
 * - Lockscreen controls, Bluetooth AVRCP & Headset media button handling.
 * - Automatic Audio Focus handling (pause on calls, ducking on navigation prompts)
 *   and pause when headphones disconnect (`handleAudioBecomingNoisy`).
 */
@OptIn(UnstableApi::class)
class KoalaMediaSessionService : MediaLibraryService() {

    private val playerController: KoalaPlayerController by inject()
    private var mediaLibrarySession: MediaLibrarySession? = null

    override fun onCreate() {
        super.onCreate()
        val exoPlayer = playerController.getOrCreateExoPlayer()

        val forwardingPlayer = object : ForwardingPlayer(exoPlayer) {
            override fun seekToPrevious() {
                playerController.handleMediaKeyPrevious()
            }

            override fun seekToNext() {
                playerController.handleMediaKeyNext()
            }

            override fun seekBack() {
                playerController.back()
            }

            override fun seekForward() {
                playerController.forward()
            }
        }

        val sessionActivityIntent = packageManager?.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
            PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val skipBackCommand = SessionCommand(ACTION_SKIP_BACK, Bundle.EMPTY)
        val skipFwdCommand = SessionCommand(ACTION_SKIP_FORWARD, Bundle.EMPTY)

        val skipBackButton = CommandButton.Builder(CommandButton.ICON_SKIP_BACK)
            .setDisplayName("Skip Back")
            .setSessionCommand(skipBackCommand)
            .build()

        val skipForwardButton = CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD)
            .setDisplayName("Skip Forward")
            .setSessionCommand(skipFwdCommand)
            .build()

        val callback = object : MediaLibrarySession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
            ): MediaSession.ConnectionResult {
                val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                    .add(skipBackCommand)
                    .add(skipFwdCommand)
                    .build()
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(sessionCommands)
                    .setCustomLayout(ImmutableList.of(skipBackButton, skipForwardButton))
                    .build()
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle,
            ): ListenableFuture<SessionResult> {
                when (customCommand.customAction) {
                    ACTION_SKIP_BACK -> playerController.back()
                    ACTION_SKIP_FORWARD -> playerController.forward()
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }

        mediaLibrarySession = MediaLibrarySession.Builder(this, forwardingPlayer, callback)
            .apply {
                if (sessionActivityIntent != null) {
                    setSessionActivity(sessionActivityIntent)
                }
            }
            .build()
            .also { session ->
                addSession(session)
            }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaLibrarySession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaLibrarySession?.run {
            release()
            mediaLibrarySession = null
        }
        super.onDestroy()
    }

    companion object {
        const val ACTION_SKIP_BACK = "net.koalastuff.koalashelf.ACTION_SKIP_BACK"
        const val ACTION_SKIP_FORWARD = "net.koalastuff.koalashelf.ACTION_SKIP_FORWARD"
    }
}
