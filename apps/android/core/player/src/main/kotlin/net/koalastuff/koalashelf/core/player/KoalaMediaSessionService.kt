package net.koalastuff.koalashelf.core.player

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import net.koalastuff.koalashelf.core.data.repository.DownloadRepository
import net.koalastuff.koalashelf.core.data.repository.LibraryRepository
import net.koalastuff.koalashelf.core.data.session.SessionRepository
import net.koalastuff.koalashelf.core.network.LibraryItem
import net.koalastuff.koalashelf.core.network.Shelf
import org.koin.android.ext.android.inject

/**
 * AndroidX Media3 `MediaLibraryService` providing:
 * - Persistent Android 13+ Media Notification with progress scrubbing, Play/Pause,
 *   configurable Skip Back / Skip Forward actions, and cover artwork.
 * - Lockscreen controls, Bluetooth AVRCP & Headset media button handling.
 * - Android Auto & Automotive OS media tree browsing (Continue Listening, Downloaded, Recent).
 * - Automatic Audio Focus handling (pause on calls, ducking on navigation prompts)
 *   and pause when headphones disconnect (`handleAudioBecomingNoisy`).
 */
@OptIn(UnstableApi::class)
class KoalaMediaSessionService : MediaLibraryService() {

    private val playerController: KoalaPlayerController by inject()
    private val libraryRepository: LibraryRepository by inject()
    private val downloadRepository: DownloadRepository by inject()
    private val sessionRepository: SessionRepository by inject()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
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

            override fun onGetLibraryRoot(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                params: LibraryParams?,
            ): ListenableFuture<LibraryResult<MediaItem>> {
                val rootItem = MediaItem.Builder()
                    .setMediaId(MEDIA_ID_ROOT)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle("KoalaShelf")
                            .setIsBrowsable(true)
                            .setIsPlayable(false)
                            .build(),
                    )
                    .build()
                return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
            }

            override fun onGetChildren(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                parentId: String,
                page: Int,
                pageSize: Int,
                params: LibraryParams?,
            ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
                return serviceScope.future(Dispatchers.IO) {
                    val items: List<MediaItem> = when (parentId) {
                        MEDIA_ID_ROOT -> {
                            listOf(
                                buildFolderItem(MEDIA_ID_CONTINUE, "Continue Listening", "Resume your recent audiobooks"),
                                buildFolderItem(MEDIA_ID_DOWNLOADED, "Downloaded", "Offline listening on the road"),
                                buildFolderItem(MEDIA_ID_RECENT, "Recently Added", "Newest audiobooks in your shelf"),
                            )
                        }
                        MEDIA_ID_CONTINUE -> {
                            val list = mutableListOf<MediaItem>()
                            playerController.item.value?.let { current ->
                                list.add(buildBookItem(current))
                            }
                            val activeLibId = sessionRepository.activeLibraryId.value
                            if (!activeLibId.isNullOrBlank()) {
                                runCatching {
                                    val shelves = libraryRepository.getHomeShelves(activeLibId)
                                    val bookShelves = shelves.filterIsInstance<Shelf.BookShelf>()
                                    val continueShelf = bookShelves.firstOrNull { it.id.contains("continue") }
                                    continueShelf?.books?.forEach { book ->
                                        if (list.none { it.mediaId == book.id }) {
                                            list.add(buildBookItem(book))
                                        }
                                    }
                                }
                            }
                            list
                        }
                        MEDIA_ID_DOWNLOADED -> {
                            downloadRepository.books.value.values.map { downloaded ->
                                buildBookItem(downloaded.item)
                            }
                        }
                        MEDIA_ID_RECENT -> {
                            val list = mutableListOf<MediaItem>()
                            val activeLibId = sessionRepository.activeLibraryId.value
                            if (!activeLibId.isNullOrBlank()) {
                                runCatching {
                                    val shelves = libraryRepository.getHomeShelves(activeLibId)
                                    val bookShelves = shelves.filterIsInstance<Shelf.BookShelf>()
                                    val recentShelf = bookShelves.firstOrNull { it.id.contains("recent") }
                                    recentShelf?.books?.forEach { book ->
                                        list.add(buildBookItem(book))
                                    }
                                }
                            }
                            list
                        }
                        else -> emptyList()
                    }
                    LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
                }
            }

            override fun onGetItem(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                mediaId: String,
            ): ListenableFuture<LibraryResult<MediaItem>> {
                return serviceScope.future(Dispatchers.IO) {
                    val current = playerController.item.value
                    if (current?.id == mediaId) {
                        return@future LibraryResult.ofItem(buildBookItem(current), null)
                    }
                    val downloaded = downloadRepository.books.value[mediaId]
                    if (downloaded != null) {
                        return@future LibraryResult.ofItem(buildBookItem(downloaded.item), null)
                    }
                    val book = libraryRepository.getBookDetail(mediaId)
                    if (book != null) {
                        LibraryResult.ofItem(buildBookItem(book), null)
                    } else {
                        LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
                    }
                }
            }

            override fun onSetMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: MutableList<MediaItem>,
                startIndex: Int,
                startPositionMs: Long,
            ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                val target = mediaItems.getOrNull(startIndex) ?: mediaItems.firstOrNull()
                if (target != null && target.mediaId.isNotBlank() && !target.mediaId.startsWith("koala_")) {
                    val startSec = if (startPositionMs > 0L) startPositionMs / 1000.0 else null
                    playerController.play(itemId = target.mediaId, startAtSeconds = startSec)
                }
                return Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs),
                )
            }

            override fun onAddMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: MutableList<MediaItem>,
            ): ListenableFuture<MutableList<MediaItem>> {
                val target = mediaItems.firstOrNull()
                if (target != null && target.mediaId.isNotBlank() && !target.mediaId.startsWith("koala_")) {
                    playerController.play(itemId = target.mediaId)
                }
                return Futures.immediateFuture(mediaItems)
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

    private fun buildFolderItem(
        id: String,
        title: String,
        subtitle: String? = null,
    ): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .build()
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun buildBookItem(item: LibraryItem): MediaItem {
        val coverUrl = sessionRepository.apiClient?.coverUrl(item.id, width = 400, updatedAt = item.updatedAt)
        val coverUri = if (!coverUrl.isNullOrBlank()) Uri.parse(coverUrl) else null
        val metadata = MediaMetadata.Builder()
            .setTitle(item.title)
            .setArtist(item.author)
            .setAlbumTitle(item.seriesLabel.ifBlank { item.title })
            .apply {
                if (coverUri != null) {
                    setArtworkUri(coverUri)
                }
            }
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .build()
        return MediaItem.Builder()
            .setMediaId(item.id)
            .setMediaMetadata(metadata)
            .build()
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
        serviceScope.cancel()
        mediaLibrarySession?.run {
            release()
            mediaLibrarySession = null
        }
        super.onDestroy()
    }

    companion object {
        const val ACTION_SKIP_BACK = "net.koalastuff.koalashelf.ACTION_SKIP_BACK"
        const val ACTION_SKIP_FORWARD = "net.koalastuff.koalashelf.ACTION_SKIP_FORWARD"

        const val MEDIA_ID_ROOT = "koala_root"
        const val MEDIA_ID_CONTINUE = "koala_continue"
        const val MEDIA_ID_DOWNLOADED = "koala_downloaded"
        const val MEDIA_ID_RECENT = "koala_recent"
    }
}
