package net.koalastuff.koalashelf.core.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import net.koalastuff.koalashelf.core.data.local.CachedBookEntity
import net.koalastuff.koalashelf.core.data.local.KoalaDao
import net.koalastuff.koalashelf.core.data.session.SessionRepository
import net.koalastuff.koalashelf.core.data.util.defuseCsvCell
import net.koalastuff.koalashelf.core.data.util.formatClock
import net.koalastuff.koalashelf.core.data.util.parseDurationQuery
import net.koalastuff.koalashelf.core.network.AbsApiClient
import net.koalastuff.koalashelf.core.network.AbsJson
import net.koalastuff.koalashelf.core.network.Author
import net.koalastuff.koalashelf.core.network.AuthorDetail
import net.koalastuff.koalashelf.core.network.BatchProgressUpdate
import net.koalastuff.koalashelf.core.network.Bookmark
import net.koalastuff.koalashelf.core.network.Collection
import net.koalastuff.koalashelf.core.network.FilterData
import net.koalastuff.koalashelf.core.network.FilterGroup
import net.koalastuff.koalashelf.core.network.LibraryItem
import net.koalastuff.koalashelf.core.network.LibraryStats
import net.koalastuff.koalashelf.core.network.ListeningStats
import net.koalastuff.koalashelf.core.network.Narrator
import net.koalastuff.koalashelf.core.network.Playlist
import net.koalastuff.koalashelf.core.network.ProgressUpdate
import net.koalastuff.koalashelf.core.network.SearchBookEntry
import net.koalastuff.koalashelf.core.network.SearchResult
import net.koalastuff.koalashelf.core.network.Series
import net.koalastuff.koalashelf.core.network.ServerUser
import net.koalastuff.koalashelf.core.network.SeriesDetail
import net.koalastuff.koalashelf.core.network.SessionPage
import net.koalastuff.koalashelf.core.network.Shelf
import net.koalastuff.koalashelf.core.network.YearStats
import net.koalastuff.koalashelf.core.network.encodeFilter
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

enum class LibraryStatusFilter(val wireValue: String) {
    ALL(""),
    IN_PROGRESS("in-progress"),
    NOT_STARTED("not-started"),
    FINISHED("finished"),
}

enum class LibrarySortOption(val key: String, val defaultDesc: Boolean) {
    TITLE("media.metadata.title", false),
    AUTHOR("media.metadata.authorNameLF", false),
    ADDED("addedAt", true),
    PLAYED("progress", true),
    STARTED("progress.createdAt", true),
    FINISHED("progress.finishedAt", true),
    DURATION("media.duration", false),
    PUBLISHED("media.metadata.publishedYear", true),
    SEQUENCE("sequence", false),
    RANDOM("random", false),
}

enum class DurationPreset(val id: String, val minHours: Double, val maxHours: Double) {
    ALL("", 0.0, Double.POSITIVE_INFINITY),
    LT2("lt2", 0.0, 2.0),
    H2_6("2-6", 2.0, 6.0),
    H6_12("6-12", 6.0, 12.0),
    H12_24("12-24", 12.0, 24.0),
    GT24("gt24", 24.0, Double.POSITIVE_INFINITY),
}

data class LibraryQueryState(
    val status: LibraryStatusFilter = LibraryStatusFilter.ALL,
    val genres: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val narrators: Set<String> = emptySet(),
    val languages: Set<String> = emptySet(),
    val authorId: String = "",
    val seriesId: String = "",
    val publisher: String = "",
    val decade: String = "",
    val durationPreset: DurationPreset = DurationPreset.ALL,
    val textQuery: String = "",
    val sort: LibrarySortOption = LibrarySortOption.TITLE,
    val desc: Boolean = false,
    val groupSeries: Boolean = false,
    val shuffleSeed: Int = 0,
) {
    val activeFilterCount: Int
        get() = genres.size + tags.size + narrators.size + languages.size +
            (if (authorId.isNotBlank()) 1 else 0) +
            (if (seriesId.isNotBlank()) 1 else 0) +
            (if (publisher.isNotBlank()) 1 else 0) +
            (if (decade.isNotBlank()) 1 else 0) +
            (if (status != LibraryStatusFilter.ALL) 1 else 0) +
            (if (durationPreset != DurationPreset.ALL) 1 else 0) +
            (if (textQuery.isNotBlank()) 1 else 0)
}

data class ChapterSearchHit(
    val book: LibraryItem,
    val chapterTitle: String,
    val startSeconds: Double,
)

/**
 * Full Library, Browse, Search, Collections, Playlists, Bookmarks & Stats Repository
 * matching `apps/web/src/lib/library-query.ts` and `apps/web/src/routes/`.
 */
class LibraryRepository(
    private val dao: KoalaDao,
    private val sessionRepository: SessionRepository,
    private val progressRepository: ProgressRepository,
) {
    private val filterIdsCache = mutableMapOf<String, Pair<Long, Set<String>>>()

    private val api: AbsApiClient?
        get() = sessionRepository.apiClient

    suspend fun getHomeShelves(libraryId: String): List<Shelf> = withContext(Dispatchers.IO) {
        val client = api ?: return@withContext emptyList()
        client.personalized(libraryId, limit = 16)
    }

    suspend fun getFilterData(libraryId: String): FilterData = withContext(Dispatchers.IO) {
        api?.filterData(libraryId) ?: FilterData()
    }

    /**
     * Executes a library query using either direct server paging (for <= 1 simple filter)
     * or exact set intersection/union across multiple facets + natural duration parsing
     * (`runSet` in `apps/web/src/lib/library-query.ts`). Offline fallback reads cached books.
     */
    suspend fun queryLibraryItems(
        libraryId: String,
        query: LibraryQueryState,
    ): List<LibraryItem> = withContext(Dispatchers.IO) {
        val client = api
        val accountKey = sessionRepository.activeKey.value ?: "offline"

        if (client == null) {
            return@withContext loadCachedBooks(accountKey, libraryId, query)
        }

        try {
            val facets = buildFacets(query)
            val serverConditions = facets.size + (if (query.status != LibraryStatusFilter.ALL) 1 else 0)
            val isSimple = query.textQuery.isBlank() &&
                query.durationPreset == DurationPreset.ALL &&
                query.sort != LibrarySortOption.RANDOM &&
                serverConditions <= 1 &&
                (facets.isEmpty() || facets.first().second.size == 1)

            val items = if (isSimple) {
                val singleFilter = facets.firstOrNull()?.second?.firstOrNull()
                    ?: if (query.status != LibraryStatusFilter.ALL) {
                        encodeFilter(FilterGroup.PROGRESS, query.status.wireValue)
                    } else {
                        null
                    }
                client.libraryItems(
                    libraryId = libraryId,
                    page = 0,
                    limit = 0,
                    sort = query.sort.key,
                    desc = query.desc,
                    filter = singleFilter,
                    collapseSeries = query.groupSeries && query.seriesId.isBlank(),
                ).results
            } else {
                val baseFacet = facets.firstOrNull { it.second.size == 1 }
                val restFacets = facets.filterNot { it === baseFacet }
                val durParsed = parseDurationQuery(query.textQuery)
                val cleanText = durParsed.cleanQuery

                val baseDeferred = async {
                    client.libraryItems(
                        libraryId = libraryId,
                        page = 0,
                        limit = 0,
                        sort = if (query.sort == LibrarySortOption.RANDOM) LibrarySortOption.TITLE.key else query.sort.key,
                        desc = query.desc,
                        filter = baseFacet?.second?.firstOrNull(),
                    ).results
                }
                val restSetsDeferred = restFacets.map { (_, filters) ->
                    async {
                        val sets = filters.map { flt -> idsForFilter(client, libraryId, flt) }
                        sets.flatten().toSet()
                    }
                }
                val textSetDeferred = async {
                    if (cleanText.isNotBlank()) textSearchIds(client, libraryId, cleanText) else null
                }

                val baseList = baseDeferred.await()
                val restSets = restSetsDeferred.awaitAll()
                val textSet = textSetDeferred.await()

                val minSec = max(query.durationPreset.minHours * 3600.0, durParsed.minSeconds ?: 0.0)
                val maxSec = min(query.durationPreset.maxHours * 3600.0, durParsed.maxSeconds ?: Double.POSITIVE_INFINITY)
                val wantedStatus = when (query.status) {
                    LibraryStatusFilter.ALL -> null
                    LibraryStatusFilter.NOT_STARTED -> BookListeningStatus.NEW
                    LibraryStatusFilter.IN_PROGRESS -> BookListeningStatus.RUNNING
                    LibraryStatusFilter.FINISHED -> BookListeningStatus.DONE
                }

                val filtered = baseList.filter { item ->
                    restSets.all { item.id in it } &&
                        (textSet == null || item.id in textSet) &&
                        (wantedStatus == null || progressRepository.status(item.id) == wantedStatus) &&
                        item.durationSeconds >= minSec &&
                        item.durationSeconds < maxSec
                }

                if (query.sort == LibrarySortOption.RANDOM) {
                    filtered.shuffled(Random(query.shuffleSeed))
                } else {
                    filtered
                }
            }

            // Cache books locally for offline browsing
            if (facets.isEmpty() && query.textQuery.isBlank() && query.status == LibraryStatusFilter.ALL) {
                val entities = items.take(250).map { item ->
                    CachedBookEntity(
                        compositeKey = "$accountKey|${item.id}",
                        accountKey = accountKey,
                        libraryId = libraryId,
                        itemId = item.id,
                        title = item.title,
                        author = item.author,
                        itemJson = AbsJson.encodeToString(item),
                        updatedAt = System.currentTimeMillis(),
                    )
                }
                dao.upsertCachedBooks(entities)
            }
            items
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            loadCachedBooks(accountKey, libraryId, query)
        }
    }

    private suspend fun loadCachedBooks(
        accountKey: String,
        libraryId: String,
        query: LibraryQueryState,
    ): List<LibraryItem> {
        val cached = dao.getCachedBooks(accountKey, libraryId).mapNotNull {
            runCatching { AbsJson.decodeFromString<LibraryItem>(it.itemJson) }.getOrNull()
        }
        val durParsed = parseDurationQuery(query.textQuery)
        val q = durParsed.cleanQuery.lowercase()
        val minSec = max(query.durationPreset.minHours * 3600.0, durParsed.minSeconds ?: 0.0)
        val maxSec = min(query.durationPreset.maxHours * 3600.0, durParsed.maxSeconds ?: Double.POSITIVE_INFINITY)

        return cached.filter { item ->
            val matchesText = q.isBlank() ||
                item.title.lowercase().contains(q) ||
                item.author.lowercase().contains(q) ||
                item.narrator.lowercase().contains(q) ||
                item.seriesLabel.lowercase().contains(q)
            val matchesDuration = item.durationSeconds >= minSec && item.durationSeconds < maxSec
            val matchesStatus = when (query.status) {
                LibraryStatusFilter.ALL -> true
                LibraryStatusFilter.NOT_STARTED -> progressRepository.status(item.id) == BookListeningStatus.NEW
                LibraryStatusFilter.IN_PROGRESS -> progressRepository.status(item.id) == BookListeningStatus.RUNNING
                LibraryStatusFilter.FINISHED -> progressRepository.status(item.id) == BookListeningStatus.DONE
            }
            matchesText && matchesDuration && matchesStatus
        }
    }

    private fun buildFacets(q: LibraryQueryState): List<Pair<String, List<String>>> {
        val result = mutableListOf<Pair<String, List<String>>>()
        if (q.seriesId.isNotBlank()) result += "series" to listOf(encodeFilter(FilterGroup.SERIES, q.seriesId))
        if (q.authorId.isNotBlank()) result += "author" to listOf(encodeFilter(FilterGroup.AUTHORS, q.authorId))
        if (q.narrators.isNotEmpty()) result += "narrator" to q.narrators.map { encodeFilter(FilterGroup.NARRATORS, it) }
        if (q.publisher.isNotBlank()) result += "publisher" to listOf(encodeFilter(FilterGroup.PUBLISHERS, q.publisher))
        if (q.tags.isNotEmpty()) result += "tag" to q.tags.map { encodeFilter(FilterGroup.TAGS, it) }
        if (q.decade.isNotBlank()) result += "decade" to listOf(encodeFilter(FilterGroup.PUBLISHED_DECADES, q.decade))
        if (q.genres.isNotEmpty()) result += "genre" to q.genres.map { encodeFilter(FilterGroup.GENRES, it) }
        if (q.languages.isNotEmpty()) result += "language" to q.languages.map { encodeFilter(FilterGroup.LANGUAGES, it) }
        return result
    }

    private suspend fun idsForFilter(
        client: AbsApiClient,
        libraryId: String,
        filter: String,
    ): Set<String> {
        val key = "$libraryId:$filter"
        val now = System.currentTimeMillis()
        val cached = filterIdsCache[key]
        if (cached != null && now - cached.first < 120_000L) {
            return cached.second
        }
        val ids = client.libraryItems(libraryId = libraryId, limit = 0, filter = filter).results.map { it.id }.toSet()
        filterIdsCache[key] = now to ids
        return ids
    }

    private suspend fun textSearchIds(
        client: AbsApiClient,
        libraryId: String,
        text: String,
    ): Set<String> = coroutineScope {
        val r = client.search(libraryId, text, limit = 200)
        val ids = mutableSetOf<String>()
        r.book.forEach { ids.add(it.libraryItem.id) }
        r.series.forEach { s -> s.books.forEach { ids.add(it.id) } }
        val relatedFilters = buildList {
            r.authors.take(5).forEach { add(encodeFilter(FilterGroup.AUTHORS, it.id)) }
            r.narrators.take(5).forEach { add(encodeFilter(FilterGroup.NARRATORS, it.name)) }
            r.genres.take(3).forEach { add(encodeFilter(FilterGroup.GENRES, it.name)) }
            r.tags.take(3).forEach { add(encodeFilter(FilterGroup.TAGS, it.name)) }
        }
        val relatedSets = relatedFilters.map { flt -> async { idsForFilter(client, libraryId, flt) } }.awaitAll()
        relatedSets.forEach { ids.addAll(it) }
        ids
    }

    suspend fun getBookDetail(itemId: String): LibraryItem? = withContext(Dispatchers.IO) {
        val accountKey = sessionRepository.activeKey.value ?: "offline"
        try {
            val item = api?.item(itemId)
            if (item != null) {
                item.userMediaProgress?.let { progressRepository.mergeRemote(it) }
                dao.upsertCachedBooks(
                    listOf(
                        CachedBookEntity(
                            compositeKey = "$accountKey|$itemId",
                            accountKey = accountKey,
                            libraryId = item.libraryId,
                            itemId = item.id,
                            title = item.title,
                            author = item.author,
                            itemJson = AbsJson.encodeToString(item),
                            updatedAt = System.currentTimeMillis(),
                        ),
                    ),
                )
                item
            } else {
                dao.getCachedBook("$accountKey|$itemId")?.let {
                    runCatching { AbsJson.decodeFromString<LibraryItem>(it.itemJson) }.getOrNull()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            dao.getCachedBook("$accountKey|$itemId")?.let {
                runCatching { AbsJson.decodeFromString<LibraryItem>(it.itemJson) }.getOrNull()
            }
        }
    }

    suspend fun getSeriesList(libraryId: String): List<Series> = withContext(Dispatchers.IO) {
        api?.series(libraryId = libraryId, limit = 100)?.results ?: emptyList()
    }

    suspend fun getSeriesDetail(libraryId: String, seriesId: String): Pair<SeriesDetail?, List<LibraryItem>> = withContext(Dispatchers.IO) {
        val client = api ?: return@withContext null to emptyList()
        val detailDef = async { runCatching { client.seriesDetail(libraryId, seriesId) }.getOrNull() }
        val booksDef = async {
            runCatching {
                client.libraryItems(
                    libraryId = libraryId,
                    limit = 0,
                    filter = encodeFilter(FilterGroup.SERIES, seriesId),
                    sort = "sequence",
                ).results
            }.getOrDefault(emptyList())
        }
        detailDef.await() to booksDef.await()
    }

    suspend fun getAuthors(libraryId: String): List<Author> = withContext(Dispatchers.IO) {
        api?.authors(libraryId) ?: emptyList()
    }

    suspend fun getNarrators(libraryId: String): List<Narrator> = withContext(Dispatchers.IO) {
        api?.narrators(libraryId) ?: emptyList()
    }

    suspend fun getAuthorDetail(authorId: String, libraryId: String): AuthorDetail? = withContext(Dispatchers.IO) {
        runCatching { api?.author(authorId, libraryId) }.getOrNull()
    }

    suspend fun search(
        libraryId: String,
        query: String,
        allLibraries: Boolean = false,
        includeChapters: Boolean = false,
    ): Pair<SearchResult, List<ChapterSearchHit>> = withContext(Dispatchers.IO) {
        val client = api ?: return@withContext SearchResult() to emptyList()
        val q = query.trim()
        if (q.isBlank()) return@withContext SearchResult() to emptyList()

        val targetLibs = if (allLibraries) {
            sessionRepository.libraries.value.map { it.id }.ifEmpty { listOf(libraryId) }
        } else {
            listOf(libraryId)
        }

        val results = targetLibs.map { libId ->
            async { runCatching { client.search(libId, q, limit = 25) }.getOrDefault(SearchResult()) }
        }.awaitAll()

        val merged = SearchResult(
            book = results.flatMap { it.book }.distinctBy { it.libraryItem.id },
            series = results.flatMap { it.series }.distinctBy { it.series.id },
            authors = results.flatMap { it.authors }.distinctBy { it.id },
            narrators = results.flatMap { it.narrators }.distinctBy { it.name },
            tags = results.flatMap { it.tags }.distinctBy { it.name },
            genres = results.flatMap { it.genres }.distinctBy { it.name },
        )

        val extraBooksFromNarrators = if (merged.narrators.isNotEmpty()) {
            merged.narrators.take(4).flatMap { narr ->
                targetLibs.flatMap { libId ->
                    runCatching {
                        client.libraryItems(
                            libraryId = libId,
                            filter = encodeFilter(FilterGroup.NARRATORS, narr.name),
                            limit = 25,
                        ).results
                    }.getOrDefault(emptyList())
                }
            }.map { SearchBookEntry(it) }
        } else {
            emptyList()
        }

        val extraBooksFromGenres = if (merged.genres.isNotEmpty() && (merged.book.size + extraBooksFromNarrators.size) < 10) {
            merged.genres.take(2).flatMap { g ->
                targetLibs.flatMap { libId ->
                    runCatching {
                        client.libraryItems(
                            libraryId = libId,
                            filter = encodeFilter(FilterGroup.GENRES, g.name),
                            limit = 25,
                        ).results
                    }.getOrDefault(emptyList())
                }
            }.map { SearchBookEntry(it) }
        } else {
            emptyList()
        }

        val extraBooksFromTags = if (merged.tags.isNotEmpty() && (merged.book.size + extraBooksFromNarrators.size + extraBooksFromGenres.size) < 10) {
            merged.tags.take(2).flatMap { t ->
                targetLibs.flatMap { libId ->
                    runCatching {
                        client.libraryItems(
                            libraryId = libId,
                            filter = encodeFilter(FilterGroup.TAGS, t.name),
                            limit = 25,
                        ).results
                    }.getOrDefault(emptyList())
                }
            }.map { SearchBookEntry(it) }
        } else {
            emptyList()
        }

        val allMerged = merged.copy(
            book = (merged.book + extraBooksFromNarrators + extraBooksFromGenres + extraBooksFromTags).distinctBy { it.libraryItem.id },
        )

        val chapterHits = if (includeChapters) {
            val lowerQ = q.lowercase()
            allMerged.book.flatMap { entry ->
                val b = entry.libraryItem
                b.media.chapters
                    .filter { it.title.lowercase().contains(lowerQ) }
                    .map { ch ->
                        ChapterSearchHit(
                            book = b,
                            chapterTitle = ch.title,
                            startSeconds = ch.start,
                        )
                    }
            }
        } else {
            emptyList()
        }

        allMerged to chapterHits
    }

    // --- Progress Actions ---

    suspend fun setBookFinished(item: LibraryItem, finished: Boolean) {
        val client = api ?: return
        val dur = item.durationSeconds
        client.updateProgress(
            itemId = item.id,
            update = ProgressUpdate(
                isFinished = finished,
                currentTime = if (finished) dur else 0.0,
                progress = if (finished) 1.0 else 0.0,
                duration = dur,
            ),
        )
        val existing = progressRepository.get(item.id)
        progressRepository.setLocal(
            (existing ?: net.koalastuff.koalashelf.core.network.MediaProgress(id = item.id, libraryItemId = item.id, duration = dur)).copy(
                isFinished = finished,
                currentTime = if (finished) dur else 0.0,
                progress = if (finished) 1.0 else 0.0,
                lastUpdate = System.currentTimeMillis(),
            ),
        )
        if (finished) progressRepository.celebrateFinished(item.id)
    }

    suspend fun resetBookProgress(itemId: String) {
        val client = api ?: return
        val progressId = progressRepository.get(itemId)?.id ?: itemId
        runCatching { client.removeProgress(progressId) }
        progressRepository.removeLocal(itemId)
    }

    suspend fun markBatchFinished(items: List<LibraryItem>, finished: Boolean) {
        val client = api ?: return
        val updates = items.map { item ->
            BatchProgressUpdate(
                libraryItemId = item.id,
                isFinished = finished,
                currentTime = if (finished) item.durationSeconds else 0.0,
                progress = if (finished) 1.0 else 0.0,
            )
        }
        client.updateProgressMany(updates)
        progressRepository.loadFromServer(force = true)
    }

    // --- Collections & Playlists ---

    suspend fun getCollections(libraryId: String): List<Collection> = withContext(Dispatchers.IO) {
        runCatching { api?.collections(libraryId) ?: emptyList() }.getOrDefault(emptyList())
    }

    suspend fun getCollection(id: String): Collection? = withContext(Dispatchers.IO) {
        runCatching { api?.collection(id) }.getOrNull()
    }

    suspend fun createCollection(libraryId: String, name: String, bookIds: List<String>, description: String? = null): Collection? = withContext(Dispatchers.IO) {
        runCatching { api?.createCollection(libraryId, name, bookIds, description) }.getOrNull()
    }

    suspend fun addToCollection(collectionId: String, itemId: String): Collection? = withContext(Dispatchers.IO) {
        runCatching { api?.addToCollection(collectionId, itemId) }.getOrNull()
    }

    suspend fun removeFromCollection(collectionId: String, itemId: String): Collection? = withContext(Dispatchers.IO) {
        runCatching { api?.removeFromCollection(collectionId, itemId) }.getOrNull()
    }

    suspend fun deleteCollection(collectionId: String) = withContext(Dispatchers.IO) {
        runCatching { api?.deleteCollection(collectionId) }
    }

    suspend fun getPlaylists(libraryId: String): List<Playlist> = withContext(Dispatchers.IO) {
        runCatching { api?.playlists(libraryId) ?: emptyList() }.getOrDefault(emptyList())
    }

    suspend fun getPlaylist(id: String): Playlist? = withContext(Dispatchers.IO) {
        runCatching { api?.playlist(id) }.getOrNull()
    }

    suspend fun createPlaylist(libraryId: String, name: String, itemIds: List<String>): Playlist? = withContext(Dispatchers.IO) {
        runCatching { api?.createPlaylist(libraryId, name, itemIds) }.getOrNull()
    }

    suspend fun addToPlaylist(playlistId: String, itemId: String): Playlist? = withContext(Dispatchers.IO) {
        runCatching { api?.addToPlaylist(playlistId, itemId) }.getOrNull()
    }

    suspend fun removeFromPlaylist(playlistId: String, itemId: String): Playlist? = withContext(Dispatchers.IO) {
        runCatching { api?.removeFromPlaylist(playlistId, itemId) }.getOrNull()
    }

    suspend fun deletePlaylist(playlistId: String) = withContext(Dispatchers.IO) {
        runCatching { api?.deletePlaylist(playlistId) }
    }

    suspend fun createPlaylistFromCollection(collectionId: String): Playlist? = withContext(Dispatchers.IO) {
        runCatching { api?.playlistFromCollection(collectionId) }.getOrNull()
    }

    // --- Bookmarks & Export ---

    suspend fun getAllBookmarksWithBooks(): List<Pair<Bookmark, LibraryItem?>> = withContext(Dispatchers.IO) {
        val client = api ?: return@withContext emptyList()
        val bookmarks = runCatching { client.allBookmarks() }.getOrDefault(emptyList())
        val itemIds = bookmarks.map { it.libraryItemId }.distinct()
        val booksById = if (itemIds.isNotEmpty()) {
            runCatching { client.items(itemIds).associateBy { it.id } }.getOrDefault(emptyMap())
        } else {
            emptyMap()
        }
        bookmarks
            .sortedWith(compareBy<Bookmark> { it.libraryItemId }.thenBy { it.time })
            .map { b -> b to booksById[b.libraryItemId] }
    }

    fun exportBookmarksMarkdown(items: List<Pair<Bookmark, LibraryItem?>>): String {
        val grouped = items.groupBy { it.second?.title ?: it.first.libraryItemId }
        return buildString {
            appendLine("# KoalaShelf Bookmarks")
            appendLine()
            for ((bookTitle, list) in grouped) {
                appendLine("## $bookTitle")
                for ((b, _) in list) {
                    appendLine("- **`${formatClock(b.time)}`** — ${b.title}")
                }
                appendLine()
            }
        }
    }

    fun exportBookmarksCsv(items: List<Pair<Bookmark, LibraryItem?>>): String {
        return buildString {
            appendLine("Book,Author,Time,Seconds,Note")
            for ((b, book) in items) {
                val title = defuseCsvCell(book?.title ?: b.libraryItemId)
                val author = defuseCsvCell(book?.author ?: "")
                val clock = defuseCsvCell(formatClock(b.time))
                val sec = b.time.toLong().toString()
                val note = defuseCsvCell(b.title)
                appendLine("$title,$author,$clock,$sec,$note")
            }
        }
    }

    // --- Stats & History ---

    suspend fun getUsers(): List<ServerUser> = withContext(Dispatchers.IO) {
        runCatching { api?.users() ?: emptyList() }.getOrDefault(emptyList())
    }

    suspend fun getListeningStats(userId: String? = null): ListeningStats? = withContext(Dispatchers.IO) {
        runCatching { api?.listeningStats(userId) }.getOrNull()
    }

    suspend fun getYearStats(year: Int): YearStats? = withContext(Dispatchers.IO) {
        runCatching { api?.yearStats(year) }.getOrNull()
    }

    suspend fun getLibraryStats(libraryId: String): LibraryStats? = withContext(Dispatchers.IO) {
        runCatching { api?.libraryStats(libraryId) }.getOrNull()
    }

    suspend fun getListeningSessions(page: Int = 0, itemsPerPage: Int = 50, userId: String? = null): SessionPage = withContext(Dispatchers.IO) {
        runCatching { api?.listeningSessions(page, itemsPerPage, userId) ?: SessionPage() }.getOrDefault(SessionPage())
    }

    suspend fun getItemListeningSessions(itemId: String): SessionPage = withContext(Dispatchers.IO) {
        runCatching { api?.itemListeningSessions(itemId) ?: SessionPage() }.getOrDefault(SessionPage())
    }

    suspend fun hideFromContinueListening(progressId: String) {
        runCatching { api?.hideFromContinueListening(progressId) }
    }

    suspend fun setSeriesInContinue(seriesId: String, shown: Boolean) {
        runCatching { api?.setSeriesInContinue(seriesId, shown) }
    }

    suspend fun updateCollection(
        id: String,
        name: String? = null,
        description: String? = null,
        bookIds: List<String>? = null,
    ): Collection? = runCatching { api?.updateCollection(id, name, description, bookIds) }.getOrNull()

    suspend fun updatePlaylist(
        id: String,
        name: String? = null,
        description: String? = null,
        itemIds: List<String>? = null,
    ): Playlist? = runCatching { api?.updatePlaylist(id, name, description, itemIds) }.getOrNull()

    suspend fun getAuthSessions(): List<net.koalastuff.koalashelf.core.network.AuthSession> =
        runCatching { api?.authSessions() ?: emptyList() }.getOrDefault(emptyList())

    suspend fun signOutDevice(sessionId: String) {
        runCatching { api?.signOutDevice(sessionId) }
    }

    suspend fun changePassword(currentPassword: String, newPassword: String): Boolean =
        runCatching {
            api?.changePassword(currentPassword, newPassword)
            true
        }.getOrDefault(false)

    fun exportHistoryCsv(sessions: List<net.koalastuff.koalashelf.core.network.ListeningSession>): String {
        return buildString {
            appendLine("Date,Title,Author,ListenedSeconds,StartPosition,EndPosition")
            for (s in sessions) {
                val date = defuseCsvCell(s.date)
                val title = defuseCsvCell(s.displayTitle)
                val author = defuseCsvCell(s.displayAuthor)
                val listened = s.timeListening.toLong().toString()
                val startPos = defuseCsvCell(formatClock(s.startTime))
                val endPos = defuseCsvCell(formatClock(s.currentTime))
                appendLine("$date,$title,$author,$listened,$startPos,$endPos")
            }
        }
    }
}
