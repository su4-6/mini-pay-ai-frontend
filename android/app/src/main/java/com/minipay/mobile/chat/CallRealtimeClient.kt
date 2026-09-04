package com.minipay.mobile.chat

import com.minipay.mobile.BuildConfig
import com.minipay.mobile.auth.AuthRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

@Serializable
data class RealtimeEvent(
    val eventId: String,
    val type: String,
    val targetUserId: String,
    val callId: String? = null,
    val conversationId: String? = null,
    val occurredAt: Long,
    val payload: JsonObject = JsonObject(emptyMap())
)

@Singleton
class CallRealtimeClient @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val authRepository: AuthRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableEvents = MutableSharedFlow<RealtimeEvent>(extraBufferCapacity = 64)
    val events = mutableEvents.asSharedFlow()
    @Volatile private var socket: WebSocket? = null
    @Volatile private var enabled = false

    fun connect() {
        if (enabled) return
        enabled = true
        scope.launch { open() }
    }

    fun disconnect() { enabled = false; socket?.close(1000, "logout"); socket = null }

    fun sendSignal(type: String, callId: String, payload: JsonObject) {
        val body = buildJsonObject { put("type", type); put("callId", callId); put("payload", payload) }
        socket?.send(body.toString())
    }

    private suspend fun open() {
        val token = authRepository.validAccessToken() ?: run { enabled = false; return }
        val baseUrl = BuildConfig.AGENT_BASE_URL.trimEnd('/')
        if (baseUrl.isBlank()) { enabled = false; return }
        val wsUrl = baseUrl.replaceFirst("http://", "ws://").replaceFirst("https://", "wss://") + "/api/v1/agent/realtime"
        socket = client.newWebSocket(Request.Builder().url(wsUrl).header("Authorization", "Bearer $token").build(), object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) { runCatching { json.decodeFromString<RealtimeEvent>(text) }.onSuccess { mutableEvents.tryEmit(it) } }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { socket = null; if (enabled) scope.launch { delay(3000); open() } }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { socket = null; if (enabled) scope.launch { delay(3000); open() } }
        })
    }
}
