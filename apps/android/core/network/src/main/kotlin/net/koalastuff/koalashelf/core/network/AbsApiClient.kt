package net.koalastuff.koalashelf.core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLQueryComponent
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.OutputStream
import java.util.concurrent.TimeUnit

class AbsError(
    val status: Int,
    override val message: String,
) : Exception(message)

val AbsJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    encodeDefaults = true
    explicitNulls = false
}

fun createDefaultHttpClient(engine: HttpClientEngine? = null): HttpClient {
    val block: io.ktor.client.HttpClientConfig<*>.() -> Unit = {
        install(ContentNegotiation) {
            json(AbsJson)
        }
        install(WebSockets)
    }
    return if (engine != null) {
        HttpClient(engine, block)
    } else {
        HttpClient(OkHttp) {
            block()
            engine {
                config {
                    connectTimeout(15, TimeUnit.SECONDS)
                    readTimeout(30, TimeUnit.SECONDS)
                    writeTimeout(30, TimeUnit.SECONDS)
                }
            }
        }
    }
}

private suspend fun readError(res: HttpResponse): String {
    val text = runCatching { res.bodyAsText() }.getOrDefault("")
    return try {
        val obj = AbsJson.parseToJsonElement(text).jsonObject
        obj["error"]?.jsonPrimitive?.content ?: text.ifBlank { res.status.description }
    } catch (_: Exception) {
        text.ifBlank { res.status.description }
    }
}

/**
 * Unauthenticated server probe (`/status`). Verifies the server is an Audiobookshelf instance.
 */
suspend fun getServerStatus(
    baseUrl: String,
    httpClient: HttpClient = createDefaultHttpClient(),
): ServerStatus {
    val normalized = normalizeBaseUrl(baseUrl)
    val res = httpClient.get("$normalized/status")
    if (!res.status.isSuccess()) {
        throw AbsError(res.status.value, readError(res))
    }
    val status: ServerStatus = res.body()
    if (!status.app.equals("audiobookshelf", ignoreCase = true)) {
        throw AbsError(0, "Not an Audiobookshelf server")
    }
    return status
}

/**
 * Signs in with username and password using `x-return-tokens: true` (ABS >= 2.26.0).
 */
suspend fun login(
    baseUrl: String,
    username: String,
    password: String,
    httpClient: HttpClient = createDefaultHttpClient(),
): LoginResponse {
    val normalized = normalizeBaseUrl(baseUrl)
    val res = httpClient.post("$normalized/login") {
        contentType(ContentType.Application.Json)
        header("x-return-tokens", "true")
        setBody(LoginRequest(username = username, password = password))
    }
    if (!res.status.isSuccess()) {
        throw AbsError(res.status.value, readError(res))
    }
    return res.body()
}

/**
 * Full-featured Audiobookshelf API client matching `packages/abs-client/src/client.ts`.
 */
class AbsApiClient(
    baseUrl: String,
    initialTokens: Tokens,
    private val httpClient: HttpClient = createDefaultHttpClient(),
    private val onTokens: (Tokens) -> Unit = {},
    private val onAuthLost: () -> Unit = {},
) {
    val baseUrl: String = normalizeBaseUrl(baseUrl)

    @Volatile
    private var tokens: Tokens = initialTokens

    @Volatile
    private var skewedToken: String? = null

    private val refreshMutex = Mutex()
    private var inFlightRefresh: Deferred<Boolean>? = null

    val accessToken: String
        get() = tokens.accessToken

    val currentTokens: Tokens
        get() = tokens

    suspend fun ensureFresh(marginSeconds: Long = 60L) {
        val exp = tokenExpiry(tokens.accessToken)
        val nowSec = System.currentTimeMillis() / 1000L
        if (exp != null && exp - nowSec < marginSeconds) {
            refresh()
        }
    }

    private suspend fun renewSoon() {
        val token = tokens.accessToken
        val exp = tokenExpiry(token) ?: return
        val nowSec = System.currentTimeMillis() / 1000L
        if (token == skewedToken || exp - nowSec > 15L) return
        refresh()
        val next = tokenExpiry(tokens.accessToken)
        if (next != null && next - (System.currentTimeMillis() / 1000L) <= 15L) {
            skewedToken = tokens.accessToken
        }
    }

    suspend fun refresh(): Boolean {
        val deferred = refreshMutex.withLock {
            inFlightRefresh?.takeIf { it.isActive } ?: CompletableDeferred<Boolean>().also { newDef ->
                inFlightRefresh = newDef
            }
        }
        if (deferred is CompletableDeferred<Boolean> && !deferred.isCompleted) {
            val result = try {
                val refreshToken = tokens.refreshToken
                if (refreshToken.isNullOrBlank()) {
                    false
                } else {
                    val res = httpClient.post("${this.baseUrl}/auth/refresh") {
                        header("x-refresh-token", refreshToken)
                    }
                    if (!res.status.isSuccess()) {
                        false
                    } else {
                        val payload: RefreshResponse = res.body()
                        val updated = Tokens(
                            accessToken = payload.user.accessToken,
                            refreshToken = payload.user.refreshToken ?: refreshToken,
                        )
                        tokens = updated
                        onTokens(updated)
                        true
                    }
                }
            } catch (_: Exception) {
                false
            } finally {
                refreshMutex.withLock {
                    if (inFlightRefresh === deferred) {
                        inFlightRefresh = null
                    }
                }
            }
            deferred.complete(result)
        }
        return deferred.await()
    }

    suspend fun executeRaw(
        path: String,
        method: HttpMethod = HttpMethod.Get,
        query: Map<String, Any?> = emptyMap(),
        extraHeaders: Map<String, String> = emptyMap(),
        configureBody: (HttpRequestBuilder.() -> Unit)? = null,
    ): HttpResponse {
        suspend fun send(): HttpResponse {
            val url = "${this.baseUrl}$path"
            return when (method) {
                HttpMethod.Get -> httpClient.get(url) {
                    applyHeadersAndQuery(query, extraHeaders, configureBody)
                }
                HttpMethod.Post -> httpClient.post(url) {
                    applyHeadersAndQuery(query, extraHeaders, configureBody)
                }
                HttpMethod.Patch -> httpClient.patch(url) {
                    applyHeadersAndQuery(query, extraHeaders, configureBody)
                }
                HttpMethod.Delete -> httpClient.delete(url) {
                    applyHeadersAndQuery(query, extraHeaders, configureBody)
                }
                else -> httpClient.get(url) {
                    applyHeadersAndQuery(query, extraHeaders, configureBody)
                }
            }
        }

        renewSoon()
        var res = send()
        if (res.status == HttpStatusCode.Unauthorized) {
            if (refresh()) {
                res = send()
            }
            if (res.status == HttpStatusCode.Unauthorized) {
                onAuthLost()
                throw AbsError(401, "Session expired")
            }
        }
        if (!res.status.isSuccess()) {
            throw AbsError(res.status.value, readError(res))
        }
        return res
    }

    private fun HttpRequestBuilder.applyHeadersAndQuery(
        query: Map<String, Any?>,
        extraHeaders: Map<String, String>,
        configureBody: (HttpRequestBuilder.() -> Unit)?,
    ) {
        header(HttpHeaders.Authorization, "Bearer ${tokens.accessToken}")
        header(HttpHeaders.CacheControl, "no-store")
        extraHeaders.forEach { (k, v) -> header(k, v) }
        query.forEach { (k, v) ->
            when (v) {
                null -> Unit
                is Boolean -> parameter(k, if (v) "1" else "0")
                else -> parameter(k, v.toString())
            }
        }
        configureBody?.invoke(this)
    }

    suspend inline fun <reified T> request(
        path: String,
        method: HttpMethod = HttpMethod.Get,
        query: Map<String, Any?> = emptyMap(),
        extraHeaders: Map<String, String> = emptyMap(),
        noinline configureBody: (HttpRequestBuilder.() -> Unit)? = null,
    ): T {
        val res = executeRaw(path, method, query, extraHeaders, configureBody)
        return res.body()
    }

    suspend fun logout() {
        runCatching {
            httpClient.post("${this.baseUrl}/logout") {
                tokens.refreshToken?.let { header("x-refresh-token", it) }
            }
        }
    }

    // --- URLs ---

    fun coverUrl(itemId: String, width: Int = 400, updatedAt: Long? = null): String {
        val tsPart = if (updatedAt != null && updatedAt > 0L) "&ts=$updatedAt" else ""
        val tokenPart = tokens.accessToken.takeIf { it.isNotBlank() }
            ?.let { "&token=${it.encodeURLQueryComponent()}" } ?: ""
        return "${this.baseUrl}/api/items/$itemId/cover?width=$width&format=webp$tsPart$tokenPart"
    }

    fun authorImageUrl(authorId: String, width: Int = 200, updatedAt: Long? = null): String {
        val tsPart = if (updatedAt != null && updatedAt > 0L) "&ts=$updatedAt" else ""
        val tokenPart = tokens.accessToken.takeIf { it.isNotBlank() }
            ?.let { "&token=${it.encodeURLQueryComponent()}" } ?: ""
        return "${this.baseUrl}/api/authors/$authorId/image?width=$width&format=webp$tsPart$tokenPart"
    }

    fun downloadUrl(itemId: String): String {
        val encoded = tokens.accessToken.encodeURLQueryComponent()
        return "${this.baseUrl}/api/items/$itemId/download?token=$encoded"
    }

    fun trackUrl(contentUrl: String): String {
        val sep = if (contentUrl.contains('?')) '&' else '?'
        val encoded = tokens.accessToken.encodeURLQueryComponent()
        return "${this.baseUrl}$contentUrl${sep}token=$encoded"
    }

    // --- Me & Progress ---

    suspend fun me(): User = request("/api/me")

    suspend fun itemsInProgress(): List<LibraryItem> =
        request<ItemsInProgressResponse>("/api/me/items-in-progress").libraryItems

    suspend fun progress(itemId: String): MediaProgress =
        request("/api/me/progress/$itemId")

    suspend fun updateProgress(itemId: String, update: ProgressUpdate) {
        executeRaw(
            path = "/api/me/progress/$itemId",
            method = HttpMethod.Patch,
        ) {
            contentType(ContentType.Application.Json)
            setBody(update)
        }
    }

    suspend fun updateProgressMany(updates: List<BatchProgressUpdate>) {
        executeRaw(
            path = "/api/me/progress/batch/update",
            method = HttpMethod.Patch,
        ) {
            contentType(ContentType.Application.Json)
            setBody(updates)
        }
    }

    suspend fun removeProgress(progressId: String) {
        executeRaw("/api/me/progress/$progressId", method = HttpMethod.Delete)
    }

    suspend fun hideFromContinueListening(progressId: String) {
        executeRaw("/api/me/progress/$progressId/remove-from-continue-listening")
    }

    suspend fun listeningSessions(page: Int = 0, itemsPerPage: Int = 50, userId: String? = null): SessionPage =
        request(
            path = if (userId != null) "/api/users/$userId/listening-sessions" else "/api/me/listening-sessions",
            query = mapOf("page" to page, "itemsPerPage" to itemsPerPage),
        )

    suspend fun itemListeningSessions(itemId: String, page: Int = 0, itemsPerPage: Int = 20): SessionPage =
        request(
            path = "/api/me/item/listening-sessions/$itemId",
            query = mapOf("page" to page, "itemsPerPage" to itemsPerPage),
        )

    suspend fun listeningStats(userId: String? = null): ListeningStats {
        val path = if (userId != null) "/api/users/$userId/listening-stats" else "/api/me/listening-stats"
        return request(path)
    }

    suspend fun users(): List<ServerUser> = request<UsersResponse>("/api/users").users

    suspend fun yearStats(year: Int): YearStats = request("/api/me/stats/year/$year")

    // --- Bookmarks ---

    suspend fun bookmarks(itemId: String): List<Bookmark> =
        request<BookmarksResponse>("/api/me/bookmarks/$itemId").bookmarks

    suspend fun allBookmarks(): List<Bookmark> =
        request<BookmarksResponse>("/api/me/bookmarks").bookmarks

    suspend fun createBookmark(itemId: String, time: Double, title: String): Bookmark =
        request(
            path = "/api/me/item/$itemId/bookmark",
            method = HttpMethod.Post,
        ) {
            contentType(ContentType.Application.Json)
            setBody(BookmarkPayload(time = time, title = title))
        }

    suspend fun updateBookmark(itemId: String, time: Double, title: String): Bookmark =
        request(
            path = "/api/me/item/$itemId/bookmark",
            method = HttpMethod.Patch,
        ) {
            contentType(ContentType.Application.Json)
            setBody(BookmarkPayload(time = time, title = title))
        }

    suspend fun deleteBookmark(itemId: String, time: Double) {
        val timeParam = if (time % 1.0 == 0.0) time.toLong().toString() else time.toString()
        executeRaw("/api/me/item/$itemId/bookmark/$timeParam", method = HttpMethod.Delete)
    }

    // --- Libraries & Browsing ---

    suspend fun libraries(): List<Library> =
        request<LibrariesResponse>("/api/libraries").libraries

    suspend fun libraryItems(
        libraryId: String,
        page: Int = 0,
        limit: Int = 60,
        sort: String? = null,
        desc: Boolean? = null,
        filter: String? = null,
        collapseSeries: Boolean? = null,
    ): Paged<LibraryItem> = request(
        path = "/api/libraries/$libraryId/items",
        query = mapOf(
            "limit" to limit,
            "page" to page,
            "sort" to sort,
            "desc" to desc,
            "filter" to filter,
            "collapseseries" to collapseSeries,
            "minified" to true,
        ),
    )

    suspend fun filterData(libraryId: String): FilterData =
        request("/api/libraries/$libraryId/filterdata")

    suspend fun series(
        libraryId: String,
        page: Int = 0,
        limit: Int = 40,
        sort: String? = null,
        desc: Boolean? = null,
    ): Paged<Series> = request(
        path = "/api/libraries/$libraryId/series",
        query = mapOf(
            "limit" to limit,
            "page" to page,
            "sort" to sort,
            "desc" to desc,
            "minified" to true,
        ),
    )

    suspend fun seriesDetail(libraryId: String, seriesId: String): SeriesDetail =
        request(
            path = "/api/libraries/$libraryId/series/$seriesId",
            query = mapOf("include" to "progress"),
        )

    suspend fun setSeriesInContinue(seriesId: String, shown: Boolean) {
        val suffix = if (shown) "readd-to-continue-listening" else "remove-from-continue-listening"
        executeRaw("/api/me/series/$seriesId/$suffix")
    }

    suspend fun authors(libraryId: String): List<Author> =
        request<AuthorsResponse>("/api/libraries/$libraryId/authors").authors

    suspend fun author(authorId: String, libraryId: String): AuthorDetail =
        request(
            path = "/api/authors/$authorId",
            query = mapOf("include" to "items,series", "library" to libraryId),
        )

    suspend fun narrators(libraryId: String): List<Narrator> =
        request<NarratorsResponse>("/api/libraries/$libraryId/narrators").narrators

    suspend fun libraryStats(libraryId: String): LibraryStats =
        request("/api/libraries/$libraryId/stats")

    suspend fun personalized(libraryId: String, limit: Int = 12): List<Shelf> {
        val rawShelves = request<List<RawShelf>>(
            path = "/api/libraries/$libraryId/personalized",
            query = mapOf("limit" to limit, "minified" to true),
        )
        return rawShelves.mapNotNull { raw ->
            when (raw.type) {
                "book" -> Shelf.BookShelf(
                    id = raw.id,
                    label = raw.label,
                    books = raw.entities.mapNotNull {
                        runCatching { AbsJson.decodeFromJsonElement<LibraryItem>(it) }.getOrNull()
                    },
                )
                "series" -> Shelf.SeriesShelf(
                    id = raw.id,
                    label = raw.label,
                    series = raw.entities.mapNotNull {
                        runCatching { AbsJson.decodeFromJsonElement<Series>(it) }.getOrNull()
                    },
                )
                "authors" -> Shelf.AuthorsShelf(
                    id = raw.id,
                    label = raw.label,
                    authors = raw.entities.mapNotNull {
                        runCatching { AbsJson.decodeFromJsonElement<Author>(it) }.getOrNull()
                    },
                )
                else -> null
            }
        }
    }

    suspend fun search(libraryId: String, q: String, limit: Int = 12): SearchResult =
        request(
            path = "/api/libraries/$libraryId/search",
            query = mapOf("q" to q, "limit" to limit),
        )

    // --- Playlists ---

    suspend fun playlists(libraryId: String): List<Playlist> =
        request<Paged<Playlist>>(
            path = "/api/libraries/$libraryId/playlists",
            query = mapOf("limit" to 0),
        ).results

    suspend fun playlist(id: String): Playlist =
        request("/api/playlists/$id")

    suspend fun createPlaylist(libraryId: String, name: String, itemIds: List<String> = emptyList()): Playlist =
        request(
            path = "/api/playlists",
            method = HttpMethod.Post,
        ) {
            contentType(ContentType.Application.Json)
            setBody(
                CreatePlaylistPayload(
                    libraryId = libraryId,
                    name = name,
                    items = itemIds.map { PlaylistItemRef(it) },
                ),
            )
        }

    suspend fun updatePlaylist(
        id: String,
        name: String? = null,
        description: String? = null,
        itemIds: List<String>? = null,
    ): Playlist = request(
        path = "/api/playlists/$id",
        method = HttpMethod.Patch,
    ) {
        contentType(ContentType.Application.Json)
        setBody(
            UpdatePlaylistPayload(
                name = name,
                description = description,
                items = itemIds?.map { PlaylistItemRef(it) },
            ),
        )
    }

    suspend fun deletePlaylist(id: String) {
        executeRaw("/api/playlists/$id", method = HttpMethod.Delete)
    }

    suspend fun addToPlaylist(id: String, libraryItemId: String): Playlist =
        request(
            path = "/api/playlists/$id/item",
            method = HttpMethod.Post,
        ) {
            contentType(ContentType.Application.Json)
            setBody(PlaylistItemRef(libraryItemId))
        }

    suspend fun removeFromPlaylist(id: String, libraryItemId: String): Playlist =
        request(
            path = "/api/playlists/$id/item/$libraryItemId",
            method = HttpMethod.Delete,
        )

    suspend fun addManyToPlaylist(id: String, itemIds: List<String>): Playlist =
        request(
            path = "/api/playlists/$id/batch/add",
            method = HttpMethod.Post,
        ) {
            contentType(ContentType.Application.Json)
            setBody(BatchPlaylistPayload(itemIds.map { PlaylistItemRef(it) }))
        }

    suspend fun removeManyFromPlaylist(id: String, itemIds: List<String>): Playlist =
        request(
            path = "/api/playlists/$id/batch/remove",
            method = HttpMethod.Post,
        ) {
            contentType(ContentType.Application.Json)
            setBody(BatchPlaylistPayload(itemIds.map { PlaylistItemRef(it) }))
        }

    suspend fun playlistFromCollection(collectionId: String): Playlist =
        request(
            path = "/api/playlists/collection/$collectionId",
            method = HttpMethod.Post,
        )

    // --- Collections ---

    suspend fun collections(libraryId: String): List<Collection> =
        request<Paged<Collection>>("/api/libraries/$libraryId/collections").results

    suspend fun collection(id: String): Collection =
        request("/api/collections/$id")

    suspend fun createCollection(
        libraryId: String,
        name: String,
        bookIds: List<String>,
        description: String? = null,
    ): Collection = request(
        path = "/api/collections",
        method = HttpMethod.Post,
    ) {
        contentType(ContentType.Application.Json)
        setBody(
            CreateCollectionPayload(
                libraryId = libraryId,
                name = name,
                description = description,
                books = bookIds,
            ),
        )
    }

    suspend fun updateCollection(
        id: String,
        name: String? = null,
        description: String? = null,
        bookIds: List<String>? = null,
    ): Collection = request(
        path = "/api/collections/$id",
        method = HttpMethod.Patch,
    ) {
        contentType(ContentType.Application.Json)
        setBody(
            UpdateCollectionPayload(
                name = name,
                description = description,
                books = bookIds,
            ),
        )
    }

    suspend fun deleteCollection(id: String) {
        executeRaw("/api/collections/$id", method = HttpMethod.Delete)
    }

    suspend fun addToCollection(id: String, libraryItemId: String): Collection =
        request(
            path = "/api/collections/$id/book",
            method = HttpMethod.Post,
        ) {
            contentType(ContentType.Application.Json)
            setBody(CollectionBookPayload(libraryItemId))
        }

    suspend fun removeFromCollection(id: String, libraryItemId: String): Collection =
        request(
            path = "/api/collections/$id/book/$libraryItemId",
            method = HttpMethod.Delete,
        )

    suspend fun addManyToCollection(id: String, itemIds: List<String>): Collection =
        request(
            path = "/api/collections/$id/batch/add",
            method = HttpMethod.Post,
        ) {
            contentType(ContentType.Application.Json)
            setBody(BatchCollectionPayload(itemIds))
        }

    suspend fun removeManyFromCollection(id: String, itemIds: List<String>): Collection =
        request(
            path = "/api/collections/$id/batch/remove",
            method = HttpMethod.Post,
        ) {
            contentType(ContentType.Application.Json)
            setBody(BatchCollectionPayload(itemIds))
        }

    // --- Account & Devices ---

    suspend fun changePassword(password: String, newPassword: String) {
        executeRaw(
            path = "/api/me/password",
            method = HttpMethod.Patch,
        ) {
            contentType(ContentType.Application.Json)
            setBody(ChangePasswordPayload(password = password, newPassword = newPassword))
        }
    }

    suspend fun authSessions(): List<AuthSession> {
        val extra = tokens.refreshToken?.let { mapOf("x-refresh-token" to it) } ?: emptyMap()
        return request<AuthSessionsResponse>(
            path = "/api/me/sessions",
            query = mapOf("itemsPerPage" to 50),
            extraHeaders = extra,
        ).sessions
    }

    suspend fun signOutDevice(sessionId: String) {
        executeRaw("/api/me/sessions/$sessionId", method = HttpMethod.Delete)
    }

    // --- Items & Playback ---

    suspend fun item(itemId: String): LibraryItem =
        request(
            path = "/api/items/$itemId",
            query = mapOf("expanded" to 1, "include" to "progress"),
        )

    suspend fun items(ids: List<String>): List<LibraryItem> =
        request<ItemsInProgressResponse>(
            path = "/api/items/batch/get",
            method = HttpMethod.Post,
        ) {
            contentType(ContentType.Application.Json)
            setBody(BatchGetItemsRequest(ids))
        }.libraryItems

    suspend fun play(
        itemId: String,
        deviceInfo: DeviceInfo,
        supportedMimeTypes: List<String> = DEFAULT_ANDROID_MIME_TYPES,
    ): PlaybackSession = request(
        path = "/api/items/$itemId/play",
        method = HttpMethod.Post,
    ) {
        contentType(ContentType.Application.Json)
        setBody(
            PlayRequest(
                deviceInfo = deviceInfo,
                supportedMimeTypes = supportedMimeTypes,
            ),
        )
    }

    suspend fun syncSession(sessionId: String, sync: SessionSync) {
        executeRaw(
            path = "/api/session/$sessionId/sync",
            method = HttpMethod.Post,
        ) {
            contentType(ContentType.Application.Json)
            setBody(sync)
        }
    }

    suspend fun syncLocalSession(local: LocalSessionPayload) {
        executeRaw(
            path = "/api/session/local",
            method = HttpMethod.Post,
        ) {
            contentType(ContentType.Application.Json)
            setBody(local)
        }
    }

    suspend fun closeSession(sessionId: String, sync: SessionSync? = null) {
        executeRaw(
            path = "/api/session/$sessionId/close",
            method = HttpMethod.Post,
        ) {
            contentType(ContentType.Application.Json)
            if (sync != null) {
                setBody(sync)
            } else {
                setBody(emptyMap<String, String>())
            }
        }
    }

    /**
     * Streams a binary download (audio file or cover) to `outputStream` while reporting byte progress.
     */
    suspend fun downloadToStream(
        path: String,
        outputStream: OutputStream,
        startByte: Long = 0L,
        onBytesRead: (Long) -> Unit = {},
    ) {
        renewSoon()
        val url = if (path.startsWith("http")) path else "${this.baseUrl}$path"
        var retried = false

        suspend fun streamOnce(): Boolean {
            var unauthorized = false
            httpClient.prepareGet(url) {
                header(HttpHeaders.Authorization, "Bearer ${tokens.accessToken}")
                if (startByte > 0L) {
                    header(HttpHeaders.Range, "bytes=$startByte-")
                }
            }.execute { response ->
                if (response.status == HttpStatusCode.Unauthorized) {
                    unauthorized = true
                    return@execute
                }
                if (!response.status.isSuccess() && response.status != HttpStatusCode.PartialContent) {
                    throw AbsError(response.status.value, response.status.description)
                }
                val channel = response.bodyAsChannel()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (!channel.isClosedForRead) {
                    val read = channel.readAvailable(buffer)
                    if (read <= 0) break
                    outputStream.write(buffer, 0, read)
                    onBytesRead(read.toLong())
                }
                outputStream.flush()
            }
            return !unauthorized
        }

        if (!streamOnce()) {
            if (!retried && refresh()) {
                retried = true
                if (!streamOnce()) {
                    throw AbsError(401, "Session expired")
                }
            } else {
                throw AbsError(401, "Session expired")
            }
        }
    }

    companion object {
        val DEFAULT_ANDROID_MIME_TYPES = listOf(
            "audio/mpeg",
            "audio/mp4",
            "audio/aac",
            "audio/flac",
            "audio/ogg",
            "audio/opus",
            "audio/webm",
            "audio/wav",
            "audio/x-m4a",
            "audio/x-m4b",
        )
    }
}
