package net.koalastuff.koalashelf.core.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.url
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive

/**
 * Socket.IO v4 WebSocket client for real-time progress synchronization (`apps/web/src/lib/live.ts`).
 * Connects to `<baseUrl>/socket.io/?EIO=4&transport=websocket`, authenticates with the access token,
 * responds to Engine.IO ping (`2`) with pong (`3`), and emits `user_item_progress_updated` events.
 */
class AbsLiveSocket(
    private val httpClient: HttpClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var wsSession: WebSocketSession? = null

    private val _progressEvents = MutableSharedFlow<ProgressSocketEvent>(extraBufferCapacity = 16)
    val progressEvents: SharedFlow<ProgressSocketEvent> = _progressEvents.asSharedFlow()

    fun start(client: AbsApiClient) {
        stop()
        val wsUrl = toSocketIoWsUrl(client.baseUrl) ?: return
        job = scope.launch {
            var retryCount = 0
            while (isActive && retryCount < 5) {
                try {
                    val session = httpClient.webSocketSession {
                        url(wsUrl)
                    }
                    wsSession = session
                    var authRetried = false

                    for (frame in session.incoming) {
                        val text = (frame as? Frame.Text)?.readText() ?: continue
                        when {
                            // Engine.IO open packet ("0{...}") -> connect to default Socket.IO namespace ("40")
                            text.startsWith("0") -> {
                                session.send("40")
                            }
                            // Engine.IO ping ("2") -> pong ("3")
                            text == "2" -> {
                                session.send("3")
                            }
                            // Socket.IO connect ack ("40...") -> emit ["auth", accessToken]
                            text.startsWith("40") -> {
                                val authMsg = """42["auth","${client.accessToken}"]"""
                                session.send(authMsg)
                            }
                            // Socket.IO event packet ("42[...]")
                            text.startsWith("42") -> {
                                val jsonPayload = text.substring(2)
                                val arr = runCatching {
                                    AbsJson.parseToJsonElement(jsonPayload) as? JsonArray
                                }.getOrNull() ?: continue
                                val eventName = arr.getOrNull(0)?.jsonPrimitive?.contentOrNull ?: continue
                                when (eventName) {
                                    "init" -> {
                                        authRetried = false
                                        retryCount = 0
                                    }
                                    "auth_failed" -> {
                                        if (!authRetried && client.refresh()) {
                                            authRetried = true
                                            session.send("""42["auth","${client.accessToken}"]""")
                                        }
                                    }
                                    "user_item_progress_updated" -> {
                                        val eventObj = arr.getOrNull(1) ?: continue
                                        val parsed = runCatching {
                                            AbsJson.decodeFromJsonElement<ProgressSocketEvent>(eventObj)
                                        }.getOrNull()
                                        if (parsed?.data != null && parsed.data.episodeId == null) {
                                            _progressEvents.tryEmit(parsed)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                    retryCount++
                    delay(3000L * retryCount)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        val s = wsSession
        wsSession = null
        if (s != null) {
            scope.launch {
                runCatching { s.close() }
            }
        }
    }

    companion object {
        fun toSocketIoWsUrl(baseUrl: String): String? {
            val clean = normalizeBaseUrl(baseUrl)
            if (clean.isEmpty()) return null
            val wsBase = when {
                clean.startsWith("https://", ignoreCase = true) -> "wss://" + clean.substring(8)
                clean.startsWith("http://", ignoreCase = true) -> "ws://" + clean.substring(7)
                else -> return null
            }
            return "$wsBase/socket.io/?EIO=4&transport=websocket"
        }
    }
}
