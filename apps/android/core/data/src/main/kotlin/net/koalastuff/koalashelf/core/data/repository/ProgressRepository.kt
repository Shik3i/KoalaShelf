package net.koalastuff.koalashelf.core.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.koalastuff.koalashelf.core.data.local.KoalaDao
import net.koalastuff.koalashelf.core.data.local.MediaProgressEntity
import net.koalastuff.koalashelf.core.data.session.SessionRepository
import net.koalastuff.koalashelf.core.network.MediaProgress

enum class BookListeningStatus {
    NEW,
    RUNNING,
    DONE,
}

/**
 * Reactive media progress store with server-timestamp conflict resolution,
 * duplicate server progress deduplication (`lastUpdate`), and gold record shine celebration
 * (`apps/web/src/lib/progress.svelte.ts`).
 */
class ProgressRepository(
    private val dao: KoalaDao,
    private val sessionRepository: SessionRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _byItem = MutableStateFlow<Map<String, MediaProgress>>(emptyMap())
    val byItem: StateFlow<Map<String, MediaProgress>> = _byItem.asStateFlow()

    private val _justFinished = MutableStateFlow<Pair<String, Int>?>(null)
    val justFinished: StateFlow<Pair<String, Int>?> = _justFinished.asStateFlow()

    private val localEntries = mutableSetOf<String>()
    private var generation = 0

    init {
        sessionRepository.onLeave {
            reset()
        }
        sessionRepository.onEnter { account ->
            scope.launch {
                val cached = dao.getProgressForUser(account.user.id)
                _byItem.value = cached.associate { it.libraryItemId to it.toModel() }
                loadFromServer(force = true)
            }
        }
        sessionRepository. onForgetUser { userId ->
            scope.launch {
                dao.clearProgressForUser(userId)
            }
        }
        scope.launch {
            sessionRepository.liveSocket.progressEvents.collect { event ->
                val data = event.data ?: return@collect
                mergeRemote(data)
            }
        }
    }

    suspend fun loadFromServer(force: Boolean = false) {
        val client = sessionRepository.apiClient ?: return
        val userId = sessionRepository.activeAccount.value?.user?.id ?: return
        val gen = ++generation
        runCatching {
            val me = client.me()
            if (gen != generation) return
            val deduped = LinkedHashMap<String, MediaProgress>()
            for (p in me.mediaProgress) {
                if (p.episodeId != null) continue
                val known = deduped[p.libraryItemId]
                if (known == null || p.lastUpdate >= known.lastUpdate) {
                    deduped[p.libraryItemId] = p
                }
            }
            localEntries.clear()
            _byItem.value = deduped
            dao.upsertAllProgress(deduped.values.map { it.toEntity(userId, isLocal = false) })
        }
    }

    fun get(itemId: String): MediaProgress? = _byItem.value[itemId]

    fun status(itemId: String): BookListeningStatus {
        val p = _byItem.value[itemId] ?: return BookListeningStatus.NEW
        if (p.progress <= 0.0 && p.currentTime <= 0.0 && !p.isFinished) return BookListeningStatus.NEW
        return if (p.isFinished) BookListeningStatus.DONE else BookListeningStatus.RUNNING
    }

    fun setLocal(progress: MediaProgress) {
        val userId = sessionRepository.activeAccount.value?.user?.id ?: return
        localEntries.add(progress.libraryItemId)
        _byItem.value = _byItem.value + (progress.libraryItemId to progress)
        scope.launch {
            dao.upsertProgress(progress.toEntity(userId, isLocal = true))
        }
    }

    fun mergeRemote(progress: MediaProgress): Boolean {
        val userId = sessionRepository.activeAccount.value?.user?.id ?: return false
        val known = _byItem.value[progress.libraryItemId]
        val isKnownLocal = progress.libraryItemId in localEntries
        if (known != null && !progress.isFinished && !isKnownLocal && known.lastUpdate > progress.lastUpdate) {
            return false
        }
        localEntries.remove(progress.libraryItemId)
        _byItem.value = _byItem.value + (progress.libraryItemId to progress)
        scope.launch {
            dao.upsertProgress(progress.toEntity(userId, isLocal = false))
        }
        return true
    }

    fun removeLocal(itemId: String) {
        val userId = sessionRepository.activeAccount.value?.user?.id ?: return
        localEntries.remove(itemId)
        _byItem.value = _byItem.value - itemId
        scope.launch {
            dao.deleteProgress("$userId|$itemId")
        }
    }

    fun celebrateFinished(itemId: String) {
        val nextN = (_justFinished.value?.second ?: 0) + 1
        _justFinished.value = itemId to nextN
    }

    fun shineCountFor(itemId: String?): Int {
        val jf = _justFinished.value ?: return 0
        return if (itemId != null && jf.first == itemId) jf.second else 0
    }

    fun reset() {
        generation++
        localEntries.clear()
        _byItem.value = emptyMap()
    }

    private fun MediaProgress.toEntity(userId: String, isLocal: Boolean) = MediaProgressEntity(
        compositeKey = "$userId|$libraryItemId",
        userId = userId,
        progressId = id.ifBlank { libraryItemId },
        libraryItemId = libraryItemId,
        duration = duration,
        progress = progress,
        currentTime = currentTime,
        isFinished = isFinished,
        hideFromContinueListening = hideFromContinueListening,
        lastUpdate = lastUpdate,
        startedAt = startedAt,
        finishedAt = finishedAt,
        isLocalOnly = isLocal,
    )

    private fun MediaProgressEntity.toModel() = MediaProgress(
        id = progressId,
        libraryItemId = libraryItemId,
        episodeId = null,
        duration = duration,
        progress = progress,
        currentTime = currentTime,
        isFinished = isFinished,
        hideFromContinueListening = hideFromContinueListening,
        lastUpdate = lastUpdate,
        startedAt = startedAt,
        finishedAt = finishedAt,
    )
}
