package net.koalastuff.koalashelf.core.data.repository

import android.content.Context
import android.os.StatFs
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import net.koalastuff.koalashelf.core.data.local.DownloadedBookEntity
import net.koalastuff.koalashelf.core.data.local.DownloadedTrackManifest
import net.koalastuff.koalashelf.core.data.local.KoalaDao
import net.koalastuff.koalashelf.core.data.local.LocalSessionEntity
import net.koalastuff.koalashelf.core.data.session.SessionRepository
import net.koalastuff.koalashelf.core.network.AbsJson
import net.koalastuff.koalashelf.core.network.LibraryItem
import net.koalastuff.koalashelf.core.network.LocalSessionPayload
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

data class DownloadedBook(
    val id: String,
    val item: LibraryItem,
    val tracks: List<DownloadedTrackManifest>,
    val sizeBytes: Long,
    val hasCover: Boolean,
    val downloadedAt: Long,
    val owners: List<String>,
)

data class DownloadJobState(
    val itemId: String,
    val receivedBytes: Long,
    val totalBytes: Long,
    val status: Status,
) {
    enum class Status { RUNNING, FAILED }

    val progressPercent: Int
        get() = if (totalBytes > 0L) ((receivedBytes * 100L) / totalBytes).coerceIn(0L, 100L).toInt() else 0
}

data class StorageRadarInfo(
    val audiobooksBytes: Long,
    val otherUsedBytes: Long,
    val freeBytes: Long,
    val totalBytes: Long,
)

/**
 * Offline Downloads & Local Session Queue (`apps/web/src/lib/downloads.svelte.ts`).
 * Supports WorkManager background execution, multi-account ownership on disk,
 * Storage Radar metrics, and automatic upload of offline sessions (`flushOfflineSessions()`).
 */
class DownloadRepository(
    private val context: Context,
    private val dao: KoalaDao,
    private val sessionRepository: SessionRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _books = MutableStateFlow<Map<String, DownloadedBook>>(emptyMap())
    val books: StateFlow<Map<String, DownloadedBook>> = _books.asStateFlow()

    private val _jobs = MutableStateFlow<Map<String, DownloadJobState>>(emptyMap())
    val jobs: StateFlow<Map<String, DownloadJobState>> = _jobs.asStateFlow()

    private val activeJobs = mutableMapOf<String, Job>()

    private val rootDir: File
        get() = File(context.filesDir, "ks-downloads").apply { mkdirs() }

    init {
        sessionRepository.onLeave {
            _books.value = emptyMap()
        }
        sessionRepository.onEnter {
            scope.launch {
                refreshActiveAccountDownloads()
                flushOfflineSessions()
            }
        }
        sessionRepository.onForgetUser { userId ->
            scope.launch {
                forgetOwner(userId)
                dao.clearLocalSessionsForUser(userId)
            }
        }
        scope.launch {
            refreshActiveAccountDownloads()
        }
    }

    fun has(itemId: String): Boolean = _books.value.containsKey(itemId)

    fun bookDir(itemId: String): File = File(rootDir, itemId)

    fun localTrackFiles(itemId: String): Map<Int, File>? {
        val book = _books.value[itemId] ?: return null
        val dir = bookDir(itemId)
        if (!dir.exists()) return null
        val map = LinkedHashMap<Int, File>()
        for (t in book.tracks) {
            val file = File(dir, t.fileName)
            if (!file.exists()) return null
            map[t.index] = file
        }
        return map
    }

    fun localCoverFile(itemId: String): File? {
        val book = _books.value[itemId] ?: return null
        if (!book.hasCover) return null
        return File(bookDir(itemId), "cover.webp")
    }

    fun enqueueDownload(itemId: String) {
        if (_jobs.value[itemId]?.status == DownloadJobState.Status.RUNNING || has(itemId)) return

        // Schedule resilient WorkManager job and start immediate coroutine download
        runCatching {
            val workRequest = OneTimeWorkRequestBuilder<BookDownloadWorker>()
                .setInputData(workDataOf(BookDownloadWorker.KEY_ITEM_ID to itemId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "download_$itemId",
                ExistingWorkPolicy.KEEP,
                workRequest,
            )
        }

        val job = scope.launch {
            performDownload(itemId)
        }
        activeJobs[itemId] = job
    }

    suspend fun performDownload(itemId: String) {
        if (has(itemId)) return
        val me = sessionRepository.activeAccount.value?.user?.id

        // If already downloaded on this device by another account, share ownership without re-downloading
        val existingEntity = dao.getDownload(itemId)
        if (existingEntity != null && bookDir(itemId).exists() && me != null) {
            val owners = (existingEntity.ownersCsv.split(',').filter { it.isNotBlank() } + me).distinct()
            val updated = existingEntity.copy(ownersCsv = owners.joinToString(","))
            dao.upsertDownload(updated)
            refreshActiveAccountDownloads()
            return
        }

        val api = sessionRepository.apiClient ?: return
        _jobs.value = _jobs.value + (
            itemId to DownloadJobState(
                itemId = itemId,
                receivedBytes = 0L,
                totalBytes = 0L,
                status = DownloadJobState.Status.RUNNING,
            )
            )

        val dir = bookDir(itemId).apply { mkdirs() }
        try {
            val item = api.item(itemId)
            val tracks = item.resolvedTracks()
            val totalExpected = tracks.sumOf { it.metadata?.size ?: 0L }
            var received = 0L

            _jobs.value = _jobs.value + (
                itemId to DownloadJobState(
                    itemId = itemId,
                    receivedBytes = 0L,
                    totalBytes = totalExpected,
                    status = DownloadJobState.Status.RUNNING,
                )
                )

            val savedTracks = mutableListOf<DownloadedTrackManifest>()
            for (t in tracks) {
                val ext = t.metadata?.ext?.let { if (it.startsWith(".")) it else ".$it" } ?: ".m4b"
                val fileName = "track-${t.index}$ext"
                val finalFile = File(dir, fileName)
                val expectedSize = t.metadata?.size ?: 0L

                if (!finalFile.exists() || (expectedSize > 0L && finalFile.length() < expectedSize)) {
                    val partFile = File(dir, "$fileName.part")
                    val existingBytes = if (partFile.exists()) partFile.length() else 0L
                    received += existingBytes

                    FileOutputStream(partFile, /* append = */ existingBytes > 0L).use { fos ->
                        api.downloadToStream(
                            path = "${t.contentUrl}/download",
                            outputStream = fos,
                            startByte = existingBytes,
                        ) { delta ->
                            received += delta
                            _jobs.value = _jobs.value + (
                                itemId to DownloadJobState(
                                    itemId = itemId,
                                    receivedBytes = received,
                                    totalBytes = max(totalExpected, received),
                                    status = DownloadJobState.Status.RUNNING,
                                )
                            )
                        }
                    }
                    if (partFile.exists()) {
                        partFile.renameTo(finalFile)
                    }
                } else {
                    received += finalFile.length()
                }

                savedTracks.add(
                    DownloadedTrackManifest(
                        index = t.index,
                        startOffset = t.startOffset,
                        duration = t.duration,
                        mimeType = t.mimeType,
                        fileName = fileName,
                    ),
                )
            }

            var hasCover = false
            if (!item.media.coverPath.isNullOrBlank()) {
                runCatching {
                    val coverFile = File(dir, "cover.webp")
                    FileOutputStream(coverFile).use { fos ->
                        api.downloadToStream(
                            path = api.coverUrl(itemId, width = 600, updatedAt = item.updatedAt),
                            outputStream = fos,
                        )
                    }
                    hasCover = coverFile.exists() && coverFile.length() > 0L
                }
            }

            val entity = DownloadedBookEntity(
                itemId = itemId,
                title = item.title,
                author = item.author,
                itemJson = AbsJson.encodeToString(item),
                tracksJson = AbsJson.encodeToString(savedTracks),
                sizeBytes = received,
                hasCover = hasCover,
                downloadedAt = System.currentTimeMillis(),
                ownersCsv = me ?: "",
            )
            dao.upsertDownload(entity)
            _jobs.value = _jobs.value - itemId
            refreshActiveAccountDownloads()
        } catch (e: CancellationException) {
            // Keep .part and completed files intact for automatic resume
            _jobs.value = _jobs.value - itemId
            throw e
        } catch (_: Exception) {
            // Keep existing downloaded bytes for resume on retry
            _jobs.value = _jobs.value + (
                itemId to DownloadJobState(
                    itemId = itemId,
                    receivedBytes = _jobs.value[itemId]?.receivedBytes ?: 0L,
                    totalBytes = _jobs.value[itemId]?.totalBytes ?: 0L,
                    status = DownloadJobState.Status.FAILED,
                )
            )
        } finally {
            activeJobs.remove(itemId)
        }
    }

    fun cancel(itemId: String) {
        activeJobs.remove(itemId)?.cancel()
        runCatching { WorkManager.getInstance(context).cancelUniqueWork("download_$itemId") }
        _jobs.value = _jobs.value - itemId
    }

    suspend fun remove(itemId: String) {
        cancel(itemId)
        val me = sessionRepository.activeAccount.value?.user?.id
        val entity = dao.getDownload(itemId)
        val remainingOwners = entity?.ownersCsv
            ?.split(',')
            ?.filter { it.isNotBlank() && it != me }
            ?: emptyList()

        if (entity != null && remainingOwners.isNotEmpty()) {
            dao.upsertDownload(entity.copy(ownersCsv = remainingOwners.joinToString(",")))
        } else {
            dao.deleteDownload(itemId)
            bookDir(itemId).deleteRecursively()
        }
        refreshActiveAccountDownloads()
    }

    private suspend fun forgetOwner(userId: String) {
        for (entity in dao.getAllDownloads()) {
            val owners = entity.ownersCsv.split(',').filter { it.isNotBlank() }
            if (userId !in owners) continue
            val rest = owners.filterNot { it == userId }
            if (rest.isNotEmpty()) {
                dao.upsertDownload(entity.copy(ownersCsv = rest.joinToString(",")))
            } else {
                dao.deleteDownload(entity.itemId)
                bookDir(entity.itemId).deleteRecursively()
            }
        }
        refreshActiveAccountDownloads()
    }

    suspend fun refreshActiveAccountDownloads() {
        val me = sessionRepository.activeAccount.value?.user?.id
        val all = dao.getAllDownloads()
        val scoped = LinkedHashMap<String, DownloadedBook>()
        for (entity in all) {
            val owners = entity.ownersCsv.split(',').filter { it.isNotBlank() }
            if (me == null || owners.isEmpty() || me in owners) {
                val item = runCatching { AbsJson.decodeFromString<LibraryItem>(entity.itemJson) }.getOrNull() ?: continue
                val tracks = runCatching {
                    AbsJson.decodeFromString<List<DownloadedTrackManifest>>(entity.tracksJson)
                }.getOrDefault(emptyList())
                scoped[entity.itemId] = DownloadedBook(
                    id = entity.itemId,
                    item = item,
                    tracks = tracks,
                    sizeBytes = entity.sizeBytes,
                    hasCover = entity.hasCover,
                    downloadedAt = entity.downloadedAt,
                    owners = owners,
                )
            }
        }
        _books.value = scoped
    }

    fun storageRadar(): StorageRadarInfo {
        val audiobooksBytes = _books.value.values.sumOf { it.sizeBytes }
        return try {
            val stat = StatFs(context.filesDir.absolutePath)
            val total = stat.totalBytes
            val free = stat.availableBytes
            val used = (total - free).coerceAtLeast(audiobooksBytes)
            StorageRadarInfo(
                audiobooksBytes = audiobooksBytes,
                otherUsedBytes = (used - audiobooksBytes).coerceAtLeast(0L),
                freeBytes = free,
                totalBytes = total,
            )
        } catch (_: Exception) {
            StorageRadarInfo(
                audiobooksBytes = audiobooksBytes,
                otherUsedBytes = 0L,
                freeBytes = 0L,
                totalBytes = audiobooksBytes,
            )
        }
    }

    // --- Offline Local Sessions Queue ---

    suspend fun saveLocalSession(session: LocalSessionPayload) {
        val userId = sessionRepository.activeAccount.value?.user?.id ?: "default"
        dao.upsertLocalSession(
            LocalSessionEntity(
                sessionId = session.id,
                userId = userId,
                libraryItemId = session.libraryItemId,
                payloadJson = AbsJson.encodeToString(session),
                updatedAt = session.updatedAt,
            ),
        )
        runCatching {
            OfflineSessionSyncWorker.enqueue(context)
        }
    }

    suspend fun flushOfflineSessions() {
        val api = sessionRepository.apiClient ?: return
        val userId = sessionRepository.activeAccount.value?.user?.id ?: return
        val pending = dao.getLocalSessions(userId)
        for (entry in pending) {
            runCatching {
                val payload = AbsJson.decodeFromString<LocalSessionPayload>(entry.payloadJson)
                api.syncLocalSession(payload)
                dao.deleteLocalSession(entry.sessionId)
            }
        }
    }
}

class BookDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {
    private val downloadRepository: DownloadRepository by inject()

    override suspend fun doWork(): Result {
        val itemId = inputData.getString(KEY_ITEM_ID) ?: return Result.failure()
        downloadRepository.performDownload(itemId)
        return if (downloadRepository.has(itemId)) Result.success() else Result.retry()
    }

    companion object {
        const val KEY_ITEM_ID = "item_id"
    }
}

/**
 * WorkManager worker that automatically awakens when an active internet connection returns
 * and synchronizes all offline listening progress back to the Audiobookshelf server backbone.
 */
class OfflineSessionSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {
    private val downloadRepository: DownloadRepository by inject()

    override suspend fun doWork(): Result {
        return runCatching {
            downloadRepository.flushOfflineSessions()
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "net.koalastuff.koalashelf.OFFLINE_SESSION_SYNC"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<OfflineSessionSyncWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
