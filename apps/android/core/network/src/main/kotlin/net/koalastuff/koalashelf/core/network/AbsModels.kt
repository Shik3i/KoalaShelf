package net.koalastuff.koalashelf.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

@Serializable
data class Tokens(
    val accessToken: String,
    val refreshToken: String? = null,
)

@Serializable
data class ServerStatus(
    val app: String = "",
    val serverVersion: String = "",
    val isInit: Boolean = true,
    val language: String = "en-us",
    val authMethods: List<String> = listOf("local"),
)

@Serializable
data class UserPermissions(
    val download: Boolean = true,
    val update: Boolean = false,
    val delete: Boolean = false,
    val upload: Boolean = false,
)

@Serializable
data class User(
    val id: String,
    val username: String,
    val type: String = "user",
    val mediaProgress: List<MediaProgress> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    val librariesAccessible: List<String> = emptyList(),
    val permissions: UserPermissions? = null,
    val accessToken: String = "",
    val refreshToken: String? = null,
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

@Serializable
data class LoginResponse(
    val user: User,
    val userDefaultLibraryId: String? = null,
)

@Serializable
data class RefreshResponse(
    val user: User,
)

@Serializable
data class Library(
    val id: String,
    val name: String,
    val displayOrder: Int = 0,
    val icon: String = "database",
    val mediaType: String = "book",
)

@Serializable
data class LibrariesResponse(
    val libraries: List<Library> = emptyList(),
)

@Serializable
data class AuthorRef(
    val id: String = "",
    val name: String = "",
)

@Serializable
data class SeriesRef(
    val id: String = "",
    val name: String = "",
    val sequence: String? = null,
)

@Serializable
data class Chapter(
    val id: Int = 0,
    val start: Double = 0.0,
    val end: Double = 0.0,
    val title: String = "",
)

@Serializable
data class FileMetadata(
    val filename: String = "",
    val ext: String = "",
    val size: Long = 0L,
)

@Serializable
data class AudioFile(
    val index: Int = 0,
    val ino: String = "",
    val format: String = "",
    val codec: String = "",
    val bitRate: Int = 0,
    val duration: Double = 0.0,
    val mimeType: String = "audio/mp4",
    val metadata: FileMetadata = FileMetadata(),
)

@Serializable
data class AudioTrack(
    val index: Int = 0,
    val startOffset: Double = 0.0,
    val duration: Double = 0.0,
    val title: String = "",
    val contentUrl: String = "",
    val mimeType: String = "audio/mp4",
    val metadata: FileMetadata? = null,
)

/**
 * Unified BookMetadata supporting both expanded (`authors[]`, `narrators[]`, `series[]`)
 * and minified (`authorName`, `narratorName`, `seriesName`) server payloads.
 */
@Serializable
data class BookMetadata(
    val title: String = "",
    val subtitle: String? = null,
    val authors: List<AuthorRef> = emptyList(),
    val narrators: List<String> = emptyList(),
    val series: JsonElement? = null,
    val authorName: String? = null,
    val narratorName: String? = null,
    val seriesName: String? = null,
    val genres: List<String> = emptyList(),
    val publishedYear: String? = null,
    val publishedDate: String? = null,
    val publisher: String? = null,
    val description: String? = null,
    val isbn: String? = null,
    val asin: String? = null,
    val language: String? = null,
    val explicit: Boolean = false,
    val abridged: Boolean = false,
) {
    val parsedSeriesList: List<SeriesRef>
        get() = when (val s = series) {
            is JsonArray -> s.mapNotNull { el ->
                val obj = el as? JsonObject ?: return@mapNotNull null
                SeriesRef(
                    id = obj["id"]?.jsonPrimitive?.contentOrNull ?: "",
                    name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "",
                    sequence = obj["sequence"]?.jsonPrimitive?.contentOrNull,
                )
            }
            is JsonObject -> listOf(
                SeriesRef(
                    id = s["id"]?.jsonPrimitive?.contentOrNull ?: "",
                    name = s["name"]?.jsonPrimitive?.contentOrNull ?: "",
                    sequence = s["sequence"]?.jsonPrimitive?.contentOrNull,
                ),
            )
            else -> emptyList()
        }

    val displayAuthor: String
        get() = authorName?.takeIf { it.isNotBlank() }
            ?: authors.joinToString(", ") { it.name }.ifBlank { "" }

    val displayNarrator: String
        get() = narratorName?.takeIf { it.isNotBlank() }
            ?: narrators.joinToString(", ").ifBlank { "" }

    val displaySeries: String
        get() = seriesName?.takeIf { it.isNotBlank() }
            ?: parsedSeriesList.firstOrNull()?.let { ref ->
                if (!ref.sequence.isNullOrBlank()) "${ref.name} #${ref.sequence}" else ref.name
            }
            ?: ""
}

@Serializable
data class BookMedia(
    val metadata: BookMetadata = BookMetadata(),
    val coverPath: String? = null,
    val tags: List<String> = emptyList(),
    val audioFiles: List<AudioFile> = emptyList(),
    val chapters: List<Chapter> = emptyList(),
    val tracks: List<AudioTrack> = emptyList(),
    val duration: Double = 0.0,
    val size: Long = 0L,
    val numChapters: Int? = null,
    val numAudioFiles: Int? = null,
)

@Serializable
data class CollapsedSeries(
    val id: String = "",
    val name: String = "",
    val nameIgnorePrefix: String = "",
    val numBooks: Int = 0,
)

@Serializable
data class LibraryItem(
    val id: String,
    val libraryId: String = "",
    val mediaType: String = "book",
    val addedAt: Long = 0L,
    val updatedAt: Long = 0L,
    val isMissing: Boolean = false,
    val isInvalid: Boolean = false,
    val isFile: Boolean = false,
    val size: Long = 0L,
    val media: BookMedia = BookMedia(),
    val userMediaProgress: MediaProgress? = null,
    val collapsedSeries: CollapsedSeries? = null,
) {
    val title: String get() = media.metadata.title
    val subtitle: String? get() = media.metadata.subtitle
    val author: String get() = media.metadata.displayAuthor
    val narrator: String get() = media.metadata.displayNarrator
    val seriesLabel: String get() = media.metadata.displaySeries
    val durationSeconds: Double get() = media.duration
    val totalBytes: Long get() = if (size > 0L) size else media.size

    fun resolvedTracks(): List<AudioTrack> {
        if (media.tracks.isNotEmpty()) return media.tracks.sortedBy { it.startOffset }
        var offset = 0.0
        return media.audioFiles.sortedBy { it.index }.map { f ->
            val track = AudioTrack(
                index = f.index,
                startOffset = offset,
                duration = f.duration,
                title = f.metadata.filename,
                contentUrl = "/api/items/$id/file/${f.ino}",
                mimeType = f.mimeType,
                metadata = f.metadata,
            )
            offset += f.duration
            track
        }
    }
}

@Serializable
data class MediaProgress(
    val id: String = "",
    val libraryItemId: String = "",
    val episodeId: String? = null,
    val duration: Double = 0.0,
    val progress: Double = 0.0,
    val currentTime: Double = 0.0,
    val isFinished: Boolean = false,
    val hideFromContinueListening: Boolean = false,
    val lastUpdate: Long = 0L,
    val startedAt: Long = 0L,
    val finishedAt: Long? = null,
)

@Serializable
data class Bookmark(
    val libraryItemId: String = "",
    val title: String = "",
    val time: Double = 0.0,
    val createdAt: Long = 0L,
)

@Serializable
data class BookmarksResponse(
    val bookmarks: List<Bookmark> = emptyList(),
)

@Serializable
data class BookmarkPayload(
    val time: Double,
    val title: String,
)

@Serializable
data class Paged<T>(
    val results: List<T> = emptyList(),
    val total: Int = 0,
    val limit: Int = 0,
    val page: Int = 0,
)

@Serializable
data class Series(
    val id: String,
    val name: String = "",
    val nameIgnorePrefix: String? = null,
    val addedAt: Long = 0L,
    val books: List<LibraryItem> = emptyList(),
)

@Serializable
data class SeriesProgress(
    val libraryItemIds: List<String> = emptyList(),
    val libraryItemIdsFinished: List<String> = emptyList(),
    val isFinished: Boolean = false,
)

@Serializable
data class SeriesDetail(
    val id: String,
    val name: String = "",
    val description: String? = null,
    val progress: SeriesProgress? = null,
)

@Serializable
data class Author(
    val id: String,
    val name: String = "",
    val description: String? = null,
    val imagePath: String? = null,
    val numBooks: Int? = null,
    val updatedAt: Long = 0L,
)

@Serializable
data class AuthorsResponse(
    val authors: List<Author> = emptyList(),
)

@Serializable
data class AuthorSeriesGroup(
    val id: String,
    val name: String = "",
    val items: List<LibraryItem> = emptyList(),
)

@Serializable
data class AuthorDetail(
    val id: String,
    val name: String = "",
    val description: String? = null,
    val imagePath: String? = null,
    val numBooks: Int? = null,
    val updatedAt: Long = 0L,
    val libraryId: String = "",
    val libraryItems: List<LibraryItem> = emptyList(),
    val series: List<AuthorSeriesGroup> = emptyList(),
)

@Serializable
data class Narrator(
    val id: String = "",
    val name: String = "",
    val numBooks: Int = 0,
)

@Serializable
data class NarratorsResponse(
    val narrators: List<Narrator> = emptyList(),
)

@Serializable
data class FilterSeriesRef(
    val id: String = "",
    val name: String = "",
)

@Serializable
data class FilterData(
    val authors: List<AuthorRef> = emptyList(),
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val series: List<FilterSeriesRef> = emptyList(),
    val narrators: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val publishers: List<String> = emptyList(),
    val publishedDecades: List<String> = emptyList(),
)

@Serializable
data class SearchBookEntry(
    val libraryItem: LibraryItem,
)

@Serializable
data class SearchSeriesEntry(
    val series: Series,
    val books: List<LibraryItem> = emptyList(),
)

@Serializable
data class SearchNarratorEntry(
    val name: String = "",
    val numBooks: Int = 0,
)

@Serializable
data class SearchTagGenreEntry(
    val name: String = "",
    val numItems: Int = 0,
)

@Serializable
data class SearchResult(
    val book: List<SearchBookEntry> = emptyList(),
    val series: List<SearchSeriesEntry> = emptyList(),
    val authors: List<Author> = emptyList(),
    val narrators: List<SearchNarratorEntry> = emptyList(),
    val tags: List<SearchTagGenreEntry> = emptyList(),
    val genres: List<SearchTagGenreEntry> = emptyList(),
)

@Serializable
data class PlaylistItem(
    val libraryItemId: String,
    val episodeId: String? = null,
    val libraryItem: LibraryItem? = null,
)

@Serializable
data class PlaylistItemRef(
    val libraryItemId: String,
)

@Serializable
data class Playlist(
    val id: String,
    val name: String = "",
    val libraryId: String = "",
    val userId: String = "",
    val description: String? = null,
    val lastUpdate: Long = 0L,
    val createdAt: Long = 0L,
    val items: List<PlaylistItem> = emptyList(),
)

@Serializable
data class CreatePlaylistPayload(
    val libraryId: String,
    val name: String,
    val items: List<PlaylistItemRef> = emptyList(),
)

@Serializable
data class UpdatePlaylistPayload(
    val name: String? = null,
    val description: String? = null,
    val items: List<PlaylistItemRef>? = null,
)

@Serializable
data class BatchPlaylistPayload(
    val items: List<PlaylistItemRef>,
)

@Serializable
data class Collection(
    val id: String,
    val libraryId: String = "",
    val name: String = "",
    val description: String? = null,
    val books: List<LibraryItem> = emptyList(),
    val lastUpdate: Long = 0L,
)

@Serializable
data class CreateCollectionPayload(
    val libraryId: String,
    val name: String,
    val description: String? = null,
    val books: List<String>,
)

@Serializable
data class UpdateCollectionPayload(
    val name: String? = null,
    val description: String? = null,
    val books: List<String>? = null,
)

@Serializable
data class CollectionBookPayload(
    val id: String,
)

@Serializable
data class BatchCollectionPayload(
    val books: List<String>,
)

@Serializable
data class RawShelf(
    val id: String,
    val label: String = "",
    val type: String = "book",
    val entities: JsonArray = JsonArray(emptyList()),
)

sealed interface Shelf {
    val id: String
    val label: String

    data class BookShelf(
        override val id: String,
        override val label: String,
        val books: List<LibraryItem>,
    ) : Shelf

    data class SeriesShelf(
        override val id: String,
        override val label: String,
        val series: List<Series>,
    ) : Shelf

    data class AuthorsShelf(
        override val id: String,
        override val label: String,
        val authors: List<Author>,
    ) : Shelf
}

@Serializable
data class DeviceInfo(
    val deviceId: String,
    val clientName: String = "KoalaShelf Android",
    val clientVersion: String = "0.1.1",
    val manufacturer: String? = null,
    val model: String? = null,
)

@Serializable
data class PlayRequest(
    val deviceInfo: DeviceInfo,
    val supportedMimeTypes: List<String>,
    val forceDirectPlay: Boolean = false,
    val forceTranscode: Boolean = false,
    val mediaPlayer: String = "exoPlayer",
)

@Serializable
data class PlaybackSession(
    val id: String,
    val libraryItemId: String = "",
    val displayTitle: String = "",
    val displayAuthor: String = "",
    val duration: Double = 0.0,
    val playMethod: Int = 0,
    val currentTime: Double = 0.0,
    val startTime: Double = 0.0,
    val chapters: List<Chapter> = emptyList(),
    val audioTracks: List<AudioTrack> = emptyList(),
    val libraryItem: LibraryItem? = null,
)

@Serializable
data class SessionSync(
    val currentTime: Double,
    val timeListened: Int,
    val duration: Double,
)

@Serializable
data class ProgressUpdate(
    val currentTime: Double? = null,
    val duration: Double? = null,
    val progress: Double? = null,
    val isFinished: Boolean? = null,
    val hideFromContinueListening: Boolean? = null,
)

@Serializable
data class BatchProgressUpdate(
    val libraryItemId: String,
    val currentTime: Double? = null,
    val duration: Double? = null,
    val progress: Double? = null,
    val isFinished: Boolean? = null,
)

@Serializable
data class ItemsInProgressResponse(
    val libraryItems: List<LibraryItem> = emptyList(),
)

@Serializable
data class BatchGetItemsRequest(
    val libraryItemIds: List<String>,
)

@Serializable
data class ListeningSessionDeviceInfo(
    val clientName: String? = null,
    val osName: String? = null,
    val browserName: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
)

@Serializable
data class ListeningSession(
    val id: String,
    val libraryItemId: String = "",
    val libraryId: String = "",
    val displayTitle: String = "",
    val displayAuthor: String = "",
    val duration: Double = 0.0,
    val startTime: Double = 0.0,
    val currentTime: Double = 0.0,
    val timeListening: Double = 0.0,
    val startedAt: Long = 0L,
    val updatedAt: Long = 0L,
    val date: String = "",
    val deviceInfo: ListeningSessionDeviceInfo? = null,
)

@Serializable
data class SessionPage(
    val total: Int = 0,
    val numPages: Int = 0,
    val page: Int = 0,
    val itemsPerPage: Int = 50,
    val sessions: List<ListeningSession> = emptyList(),
)

@Serializable
data class ListeningStatItem(
    val id: String = "",
    val timeListening: Double = 0.0,
    val mediaMetadata: BookMetadata = BookMetadata(),
)

@Serializable
data class ListeningStats(
    val totalTime: Double = 0.0,
    val today: Double = 0.0,
    val days: Map<String, Double> = emptyMap(),
    val dayOfWeek: Map<String, Double> = emptyMap(),
    val items: Map<String, ListeningStatItem> = emptyMap(),
)

@Serializable
data class YearNamedTime(
    val name: String = "",
    val time: Double = 0.0,
)

@Serializable
data class YearGenreTime(
    val genre: String = "",
    val time: Double = 0.0,
)

@Serializable
data class YearMonthTime(
    val month: Int = 0,
    val time: Double = 0.0,
)

@Serializable
data class YearLongestBook(
    val id: String = "",
    val title: String = "",
    val duration: Double = 0.0,
    val finishedAt: Long = 0L,
)

@Serializable
data class YearStats(
    val totalListeningSessions: Int = 0,
    val totalListeningTime: Double = 0.0,
    val totalBookListeningTime: Double = 0.0,
    val topAuthors: List<YearNamedTime> = emptyList(),
    val topGenres: List<YearGenreTime> = emptyList(),
    val mostListenedNarrator: YearNamedTime? = null,
    val mostListenedMonth: YearMonthTime? = null,
    val numBooksFinished: Int = 0,
    val numBooksListened: Int = 0,
    val longestAudiobookFinished: YearLongestBook? = null,
    val booksWithCovers: List<String> = emptyList(),
    val finishedBooksWithCovers: List<String> = emptyList(),
)

@Serializable
data class LibraryStatDurationItem(
    val id: String = "",
    val title: String = "",
    val duration: Double = 0.0,
)

@Serializable
data class LibraryStatSizeItem(
    val id: String = "",
    val title: String = "",
    val size: Long = 0L,
)

@Serializable
data class LibraryStatAuthorCount(
    val id: String = "",
    val name: String = "",
    val count: Int = 0,
)

@Serializable
data class LibraryStatGenreCount(
    val genre: String = "",
    val count: Int = 0,
)

@Serializable
data class LibraryStats(
    val totalItems: Int = 0,
    val totalAuthors: Int = 0,
    val totalGenres: Int = 0,
    val totalDuration: Double = 0.0,
    val totalSize: Long = 0L,
    val numAudioTracks: Int = 0,
    val longestItems: List<LibraryStatDurationItem> = emptyList(),
    val largestItems: List<LibraryStatSizeItem> = emptyList(),
    val authorsWithCount: List<LibraryStatAuthorCount> = emptyList(),
    val genresWithCount: List<LibraryStatGenreCount> = emptyList(),
)

@Serializable
data class AuthSession(
    val id: String,
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val current: Boolean = false,
)

@Serializable
data class AuthSessionsResponse(
    val sessions: List<AuthSession> = emptyList(),
)

@Serializable
data class ChangePasswordPayload(
    val password: String,
    val newPassword: String,
)

@Serializable
data class LocalSessionPayload(
    val id: String,
    val libraryItemId: String,
    val libraryId: String,
    val mediaType: String = "book",
    val displayTitle: String,
    val displayAuthor: String,
    val duration: Double,
    val playMethod: Int = 3,
    val mediaPlayer: String = "exoPlayer",
    val deviceInfo: DeviceInfo,
    val startTime: Double,
    val currentTime: Double,
    val timeListening: Int,
    val startedAt: Long,
    val updatedAt: Long,
    val date: String,
    val dayOfWeek: String,
)

@Serializable
data class ProgressSocketEvent(
    val id: String = "",
    @SerialName("sessionId")
    val sessionId: String? = null,
    val data: MediaProgress? = null,
)
