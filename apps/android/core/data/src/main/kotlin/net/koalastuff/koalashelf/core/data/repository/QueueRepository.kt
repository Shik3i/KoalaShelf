package net.koalastuff.koalashelf.core.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import net.koalastuff.koalashelf.core.data.local.KoalaDao
import net.koalastuff.koalashelf.core.data.local.QueuedItemEntity
import net.koalastuff.koalashelf.core.data.session.SessionRepository
import net.koalastuff.koalashelf.core.network.AbsJson
import net.koalastuff.koalashelf.core.network.LibraryItem

/**
 * Per-account "Up Next" playback queue stored locally in Room (`apps/web/src/lib/queue.svelte.ts`).
 */
class QueueRepository(
    private val dao: KoalaDao,
    private val sessionRepository: SessionRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _items = MutableStateFlow<List<LibraryItem>>(emptyList())
    val items: StateFlow<List<LibraryItem>> = _items.asStateFlow()

    init {
        sessionRepository.onLeave {
            _items.value = emptyList()
        }
        sessionRepository.onEnter { account ->
            scope.launch {
                val entities = dao.getQueue(account.user.id)
                _items.value = entities.mapNotNull {
                    runCatching { AbsJson.decodeFromString<LibraryItem>(it.itemJson) }.getOrNull()
                }
            }
        }
        sessionRepository.onForgetUser { userId ->
            scope.launch {
                dao.clearQueue(userId)
            }
        }
    }

    fun has(itemId: String): Boolean = _items.value.any { it.id == itemId }

    fun add(item: LibraryItem) {
        if (has(item.id)) return
        val slimmed = slim(item)
        val next = (_items.value + slimmed).takeLast(100)
        persist(next)
    }

    fun insert(item: LibraryItem, index: Int) {
        if (has(item.id)) return
        val slimmed = slim(item)
        val mutable = _items.value.toMutableList()
        mutable.add(index.coerceIn(0, mutable.size), slimmed)
        persist(mutable.takeLast(100))
    }

    fun remove(itemId: String) {
        if (!has(itemId)) return
        persist(_items.value.filterNot { it.id == itemId })
    }

    fun move(itemId: String, delta: Int) {
        val current = _items.value.toMutableList()
        val from = current.indexOfFirst { it.id == itemId }
        val to = from + delta
        if (from < 0 || to < 0 || to >= current.size) return
        val moved = current.removeAt(from)
        current.add(to, moved)
        persist(current)
    }

    fun shift(): LibraryItem? {
        val current = _items.value
        val first = current.firstOrNull() ?: return null
        persist(current.drop(1))
        return first
    }

    fun clear() {
        persist(emptyList())
    }

    private fun slim(item: LibraryItem): LibraryItem {
        return item.copy(
            media = item.media.copy(
                audioFiles = emptyList(),
                chapters = emptyList(),
                tracks = emptyList(),
            ),
        )
    }

    private fun persist(list: List<LibraryItem>) {
        _items.value = list
        val userId = sessionRepository.activeAccount.value?.user?.id ?: return
        scope.launch {
            dao.clearQueue(userId)
            if (list.isNotEmpty()) {
                val entities = list.mapIndexed { idx, item ->
                    QueuedItemEntity(
                        compositeKey = "$userId|${item.id}",
                        userId = userId,
                        itemId = item.id,
                        position = idx,
                        itemJson = AbsJson.encodeToString(item),
                    )
                }
                dao.upsertQueueItems(entities)
            }
        }
    }
}
