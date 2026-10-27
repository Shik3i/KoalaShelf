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
import androidx.media3.session.SessionError
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
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(COMMAND_PLAY_PAUSE)
                    .add(COMMAND_STOP)
                    .add(COMMAND_SEEK_TO_PREVIOUS)
                    .add(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .add(COMMAND_SEEK_TO_NEXT)
                    .add(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(COMMAND_SEEK_BACK)
                    .add(COMMAND_SEEK_FORWARD)
                    .build()
            }

            override fun isCommandAvailable(command: Int): Boolean {
                return when (command) {
                    COMMAND_PLAY_PAUSE,
                    COMMAND_STOP,
                    COMMAND_SEEK_TO_PREVIOUS,
                    COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    COMMAND_SEEK_TO_NEXT,
                    COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    COMMAND_SEEK_BACK,
                    COMMAND_SEEK_FORWARD -> true
                    else -> super.isCommandAvailable(command)
                }
            }

            override fun seekToPrevious() {
                playerController.handleMediaKeyPrevious()
            }

            override fun seekToPreviousMediaItem() {
                playerController.handleMediaKeyPrevious()
            }

            override fun seekToNext() {
                playerController.handleMediaKeyNext()
            }

            override fun seekToNextMediaItem() {
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
                val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                    .add(Player.COMMAND_PLAY_PAUSE)
                    .add(Player.COMMAND_STOP)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_BACK)
                    .add(Player.COMMAND_SEEK_FORWARD)
                    .build()
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(sessionCommands)
                    .setAvailablePlayerCommands(playerCommands)
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
                val isRecentRequest = params?.isRecent == true
                val rootId = if (isRecentRequest) MEDIA_ID_CONTINUE else MEDIA_ID_ROOT
                val rootParams = if (isRecentRequest) {
                    LibraryParams.Builder().setRecent(true).build()
                } else {
                    params
                }
                val rootItem = MediaItem.Builder()
                    .setMediaId(rootId)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle("KoalaShelf")
                            .setIsBrowsable(true)
                            .setIsPlayable(false)
                            .build(),
                    )
                    .build()
                return Futures.immediateFuture(LibraryResult.ofItem(rootItem, rootParams))
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
                                buildFolderItem(MEDIA_ID_SERIES, "Series", "Browse audiobooks by series"),
                                buildFolderItem(MEDIA_ID_AUTHORS, "Authors", "Browse audiobooks by author"),
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
                        MEDIA_ID_SERIES -> {
                            val activeLibId = sessionRepository.activeLibraryId.value
                            if (!activeLibId.isNullOrBlank()) {
                                runCatching {
                                    libraryRepository.getSeriesList(activeLibId).map { series ->
                                        buildFolderItem(
                                            id = "$MEDIA_ID_SERIES_PREFIX${series.id}",
                                            title = series.name.ifBlank { "Untitled Series" },
                                            subtitle = if (series.books.isNotEmpty()) "${series.books.size} books" else null,
                                        )
                                    }
                                }.getOrDefault(emptyList())
                            } else {
                                emptyList()
                            }
                        }
                        MEDIA_ID_AUTHORS -> {
                            val activeLibId = sessionRepository.activeLibraryId.value
                            if (!activeLibId.isNullOrBlank()) {
                                runCatching {
                                    libraryRepository.getAuthors(activeLibId).map { author ->
                                        val count = author.numBooks ?: 0
                                        buildFolderItem(
                                            id = "$MEDIA_ID_AUTHOR_PREFIX${author.id}",
                                            title = author.name.ifBlank { "Unknown Author" },
                                            subtitle = if (count > 0) "$count books" else null,
                                        )
                                    }
                                }.getOrDefault(emptyList())
                            } else {
                                emptyList()
                            }
                        }
                        else -> when {
                            parentId.startsWith(MEDIA_ID_SERIES_PREFIX) -> {
                                val seriesId = parentId.removePrefix(MEDIA_ID_SERIES_PREFIX)
                                val activeLibId = sessionRepository.activeLibraryId.value
                                if (!activeLibId.isNullOrBlank() && seriesId.isNotBlank()) {
                                    runCatching {
                                        val (_, books) = libraryRepository.getSeriesDetail(activeLibId, seriesId)
                                        books.map { buildBookItem(it) }
                                    }.getOrDefault(emptyList())
                                } else {
                                    emptyList()
                                }
                            }
                            parentId.startsWith(MEDIA_ID_AUTHOR_PREFIX) -> {
                                val authorId = parentId.removePrefix(MEDIA_ID_AUTHOR_PREFIX)
                                val activeLibId = sessionRepository.activeLibraryId.value
                                if (!activeLibId.isNullOrBlank() && authorId.isNotBlank()) {
                                    runCatching {
                                        val detail = libraryRepository.getAuthorDetail(authorId, activeLibId)
                                        detail?.libraryItems?.map { buildBookItem(it) }.orEmpty()
                                    }.getOrDefault(emptyList())
                                } else {
                                    emptyList()
                                }
                            }
                            else -> emptyList()
                        }
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
                        LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
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
        val localCover = downloadRepository.localCoverFile(item.id)
        val coverUrl = if (item.hasCover) {
            sessionRepository.apiClient?.coverUrl(item.id, width = 400, updatedAt = item.updatedAt)
        } else {
            null
        }
        val coverUri = when {
            localCover != null -> Uri.fromFile(localCover)
            !coverUrl.isNullOrBlank() -> Uri.parse(coverUrl)
            else -> null
        }
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
        const val MEDIA_ID_SERIES = "koala_series"
        const val MEDIA_ID_AUTHORS = "koala_authors"
        const val MEDIA_ID_SERIES_PREFIX = "koala_series:"
        const val MEDIA_ID_AUTHOR_PREFIX = "koala_author:"
    }
}
