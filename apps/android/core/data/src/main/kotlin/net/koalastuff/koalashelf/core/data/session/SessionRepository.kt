package net.koalastuff.koalashelf.core.data.session

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import net.koalastuff.koalashelf.core.network.AbsApiClient
import net.koalastuff.koalashelf.core.network.AbsJson
import net.koalastuff.koalashelf.core.network.AbsLiveSocket
import net.koalastuff.koalashelf.core.network.DeviceInfo
import net.koalastuff.koalashelf.core.network.Library
import net.koalastuff.koalashelf.core.network.MIN_SERVER_VERSION
import net.koalastuff.koalashelf.core.network.ServerStatus
import net.koalastuff.koalashelf.core.network.Tokens
import net.koalastuff.koalashelf.core.network.getServerStatus
import net.koalastuff.koalashelf.core.network.isVersionAtLeast
import net.koalastuff.koalashelf.core.network.login
import net.koalastuff.koalashelf.core.network.normalizeBaseUrl
import net.koalastuff.koalashelf.core.network.serverLabel
import java.util.UUID

private val Context.accountsDataStore: DataStore<Preferences> by preferencesDataStore(name = "koalashelf_accounts")

@Serializable
data class AccountUser(
    val id: String,
    val username: String,
    val type: String = "user",
)

@Serializable
data class SavedAccount(
    val key: String, // "$baseUrl|${user.id}"
    val baseUrl: String,
    val user: AccountUser,
    val tokens: Tokens? = null,
    val libraryId: String? = null,
    val libraries: List<Library> = emptyList(),
    val lastUsed: Long = 0L,
) {
    val label: String get() = serverLabel(baseUrl)
    val isAdmin: Boolean get() = user.type == "root" || user.type == "admin"
}

@Serializable
data class StoredAccountsState(
    val activeKey: String? = null,
    val accounts: List<SavedAccount> = emptyList(),
    val deviceId: String = UUID.randomUUID().toString(),
    val recentServers: List<String> = emptyList(),
)

data class ServerAccountGroup(
    val baseUrl: String,
    val label: String,
    val accounts: List<SavedAccount>,
)

class ServerTooOldException(
    val version: String,
    val minVersion: String = MIN_SERVER_VERSION,
) : Exception("Audiobookshelf $version < $minVersion")

/**
 * Multi-Account & Server Session Manager (`apps/web/src/lib/session.svelte.ts` + `accounts.ts`).
 */
class SessionRepository(
    private val context: Context,
    private val httpClient: HttpClient,
    val liveSocket: AbsLiveSocket,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _accounts = MutableStateFlow<List<SavedAccount>>(emptyList())
    val accounts: StateFlow<List<SavedAccount>> = _accounts.asStateFlow()

    private val _activeKey = MutableStateFlow<String?>(null)
    val activeKey: StateFlow<String?> = _activeKey.asStateFlow()

    private val _activeAccount = MutableStateFlow<SavedAccount?>(null)
    val activeAccount: StateFlow<SavedAccount?> = _activeAccount.asStateFlow()

    private val _libraries = MutableStateFlow<List<Library>>(emptyList())
    val libraries: StateFlow<List<Library>> = _libraries.asStateFlow()

    private val _activeLibraryId = MutableStateFlow<String?>(null)
    val activeLibraryId: StateFlow<String?> = _activeLibraryId.asStateFlow()

    private val _expired = MutableStateFlow(false)
    val expired: StateFlow<Boolean> = _expired.asStateFlow()

    private val _canDownload = MutableStateFlow(true)
    val canDownload: StateFlow<Boolean> = _canDownload.asStateFlow()

    private val _canUpdate = MutableStateFlow(false)
    val canUpdate: StateFlow<Boolean> = _canUpdate.asStateFlow()

    private val _canDelete = MutableStateFlow(false)
    val canDelete: StateFlow<Boolean> = _canDelete.asStateFlow()

    private val _recentServers = MutableStateFlow<List<String>>(emptyList())
    val recentServers: StateFlow<List<String>> = _recentServers.asStateFlow()

    @Volatile
    private var deviceId: String = UUID.randomUUID().toString()

    @Volatile
    var apiClient: AbsApiClient? = null
        private set

    private val leaveHooks = mutableListOf<() -> Unit>()
    private val enterHooks = mutableListOf<(SavedAccount) -> Unit>()
    private val forgetHooks = mutableListOf<(String) -> Unit>()

    init {
        scope.launch {
            restore()
        }
    }

    fun onLeave(hook: () -> Unit) {
        leaveHooks.add(hook)
    }

    fun onEnter(hook: (SavedAccount) -> Unit) {
        enterHooks.add(hook)
    }

    fun onForgetUser(hook: (String) -> Unit) {
        forgetHooks.add(hook)
    }

    fun deviceInfo(): DeviceInfo = DeviceInfo(
        deviceId = deviceId,
        clientName = "KoalaShelf Android",
        clientVersion = "0.1.1",
        manufacturer = Build.MANUFACTURER,
        model = Build.MODEL,
    )

    suspend fun probeServer(serverUrl: String): ServerStatus {
        return getServerStatus(normalizeBaseUrl(serverUrl), httpClient)
    }

    suspend fun restore() {
        val prefs = context.accountsDataStore.data.first()
        val raw = prefs[KEY_ACCOUNTS_JSON]
        val stored = if (raw != null) {
            runCatching { AbsJson.decodeFromString<StoredAccountsState>(raw) }.getOrNull()
        } else {
            null
        } ?: StoredAccountsState()

        deviceId = stored.deviceId
        _accounts.value = stored.accounts
        _activeKey.value = stored.activeKey
        _recentServers.value = stored.recentServers

        val active = stored.accounts.find { it.key == stored.activeKey }
        if (active?.tokens != null) {
            enterAccount(active)
            runCatching { refreshLibrariesAndMe() }
        } else {
            _activeKey.value = null
            _activeAccount.value = null
        }
        _ready.value = true
    }

    suspend fun signIn(serverUrl: String, username: String, password: String): SavedAccount {
        val baseUrl = normalizeBaseUrl(serverUrl)
        val status = getServerStatus(baseUrl, httpClient)
        if (!isVersionAtLeast(status.serverVersion, MIN_SERVER_VERSION)) {
            throw ServerTooOldException(status.serverVersion, MIN_SERVER_VERSION)
        }
        val res = login(baseUrl, username.trim(), password, httpClient)
        val key = "$baseUrl|${res.user.id}"
        val existingLibId = _accounts.value.find { it.key == key }?.libraryId
        val account = SavedAccount(
            key = key,
            baseUrl = baseUrl,
            user = AccountUser(id = res.user.id, username = res.user.username, type = res.user.type),
            tokens = Tokens(
                accessToken = res.user.accessToken,
                refreshToken = res.user.refreshToken,
            ),
            libraryId = existingLibId ?: res.userDefaultLibraryId,
            libraries = _accounts.value.find { it.key == key }?.libraries ?: emptyList(),
            lastUsed = System.currentTimeMillis(),
        )

        leaveCurrentAccount()
        val updatedList = listOf(account) + _accounts.value.filterNot { it.key == key }
        _accounts.value = updatedList
        _activeKey.value = key
        _expired.value = false
        _recentServers.value = (listOf(baseUrl) + _recentServers.value.filterNot { it == baseUrl }).take(6)

        enterAccount(account)
        runCatching { refreshLibrariesAndMe() }
        persist()
        return _activeAccount.value ?: account
    }

    suspend fun switchTo(key: String): Boolean {
        if (key == _activeKey.value && apiClient != null) return true
        val target = _accounts.value.find { it.key == key } ?: return false
        if (target.tokens == null) return false

        leaveCurrentAccount()
        val updatedTarget = target.copy(lastUsed = System.currentTimeMillis())
        _accounts.value = _accounts.value.map { if (it.key == key) updatedTarget else it }
        _activeKey.value = key
        enterAccount(updatedTarget)
        persist()
        runCatching { refreshLibrariesAndMe() }
        return apiClient != null
    }

    suspend fun signOut() {
        val key = _activeKey.value
        runCatching { apiClient?.logout() }
        leaveCurrentAccount()
        if (key != null) {
            forgetLocalAccount(key)
        }
    }

    suspend fun forget(key: String) {
        if (key == _activeKey.value) {
            signOut()
            return
        }
        val target = _accounts.value.find { it.key == key }
        if (target?.tokens != null) {
            val tempClient = AbsApiClient(
                baseUrl = target.baseUrl,
                initialTokens = target.tokens,
                httpClient = httpClient,
            )
            runCatching { tempClient.logout() }
        }
        forgetLocalAccount(key)
    }

    fun selectLibrary(libraryId: String) {
        if (_activeLibraryId.value == libraryId) return
        _activeLibraryId.value = libraryId
        val key = _activeKey.value ?: return
        updateAccount(key) { it.copy(libraryId = libraryId) }
    }

    fun accountsGroupedByServer(): List<ServerAccountGroup> {
        val list = _accounts.value
        val activeLabel = _activeAccount.value?.label
        return list
            .groupBy { it.label }
            .map { (label, group) ->
                ServerAccountGroup(
                    baseUrl = group.first().baseUrl,
                    label = label,
                    accounts = group.sortedBy { it.user.username.lowercase() },
                )
            }
            .sortedWith(
                compareByDescending<ServerAccountGroup> { it.label == activeLabel }
                    .thenBy { it.label.lowercase() },
            )
    }

    private suspend fun refreshLibrariesAndMe() {
        val client = apiClient ?: return
        val allLibs = client.libraries()
        val bookLibs = allLibs.filter { it.mediaType == "book" }.sortedBy { it.displayOrder }
        val effectiveLibs = bookLibs.ifEmpty { allLibs.sortedBy { it.displayOrder } }
        _libraries.value = effectiveLibs
        val currentLibId = _activeLibraryId.value
        if (effectiveLibs.none { it.id == currentLibId }) {
            _activeLibraryId.value = effectiveLibs.firstOrNull()?.id
        }

        val me = client.me()
        _canDownload.value = me.permissions?.download != false
        _canUpdate.value = me.type != "guest" && me.permissions?.update == true
        _canDelete.value = me.type != "guest" && me.permissions?.delete == true

        val key = _activeKey.value
        if (key != null) {
            updateAccount(key) {
                it.copy(
                    user = it.user.copy(type = me.type),
                    libraryId = _activeLibraryId.value,
                    libraries = effectiveLibs,
                )
            }
        }
    }

    private fun enterAccount(account: SavedAccount) {
        val tokens = account.tokens ?: return
        lateinit var createdClient: AbsApiClient
        createdClient = AbsApiClient(
            baseUrl = account.baseUrl,
            initialTokens = tokens,
            httpClient = httpClient,
            onTokens = { rotated ->
                if (apiClient === createdClient) {
                    updateAccount(account.key) { it.copy(tokens = rotated) }
                }
            },
            onAuthLost = {
                if (apiClient === createdClient) {
                    val key = _activeKey.value
                    _expired.value = true
                    leaveCurrentAccount()
                    if (key != null) {
                        updateAccount(key) { it.copy(tokens = null) }
                    }
                }
            },
        )
        apiClient = createdClient
        _activeAccount.value = account
        _libraries.value = account.libraries
        _activeLibraryId.value = account.libraryId ?: account.libraries.firstOrNull()?.id
        liveSocket.start(createdClient)
        enterHooks.forEach { runCatching { it(account) } }
    }

    private fun leaveCurrentAccount() {
        if (apiClient == null && _activeAccount.value == null) return
        liveSocket.stop()
        leaveHooks.forEach { runCatching { it() } }
        apiClient = null
        _activeAccount.value = null
        _libraries.value = emptyList()
        _activeLibraryId.value = null
        _canDownload.value = true
        _canUpdate.value = false
        _canDelete.value = false
    }

    private fun forgetLocalAccount(key: String) {
        val removed = _accounts.value.find { it.key == key }
        val remaining = _accounts.value.filterNot { it.key == key }
        _accounts.value = remaining
        if (_activeKey.value == key) {
            _activeKey.value = remaining.firstOrNull { it.tokens != null }?.key
            val nextActive = remaining.find { it.key == _activeKey.value }
            if (nextActive != null) {
                enterAccount(nextActive)
            }
        }
        persist()
        val removedUserId = removed?.user?.id
        if (removedUserId != null && remaining.none { it.user.id == removedUserId }) {
            forgetHooks.forEach { runCatching { it(removedUserId) } }
        }
    }

    private fun updateAccount(key: String, transform: (SavedAccount) -> SavedAccount) {
        _accounts.value = _accounts.value.map { if (it.key == key) transform(it) else it }
        if (_activeKey.value == key) {
            _activeAccount.value = _accounts.value.find { it.key == key }
        }
        persist()
    }

    private fun persist() {
        val state = StoredAccountsState(
            activeKey = _activeKey.value,
            accounts = _accounts.value,
            deviceId = deviceId,
            recentServers = _recentServers.value,
        )
        scope.launch {
            context.accountsDataStore.edit { store ->
                store[KEY_ACCOUNTS_JSON] = AbsJson.encodeToString(state)
            }
        }
    }

    companion object {
        private val KEY_ACCOUNTS_JSON = stringPreferencesKey("ks_accounts_state_json")
    }
}
