package com.minipay.mobile.ai

import com.minipay.mobile.BuildConfig
import com.minipay.mobile.auth.AuthRepository
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

@Serializable
data class AiConversationResponse(
    val id: String,
    val title: String,
    val status: String,
    val version: Long,
    val lastMessageAt: String,
    val createdAt: String,
    val updatedAt: String
)

@Serializable data class AiConversationPage(
    val items: List<AiConversationResponse>,
    val nextCursor: String? = null
)
@Serializable data class CreateAiConversationRequest(val title: String? = null)
@Serializable data class RenameAiConversationRequest(val title: String, val version: Long)
@Serializable data class CreateAgentRunRequest(
    val clientMessageId: String,
    val message: String,
    val contextVersion: Long
)

@Serializable
data class AgentRunResponse(
    val runId: String,
    val conversationId: String,
    val status: String,
    val businessRefType: String? = null,
    val businessRefId: String? = null,
    val eventsUrl: String,
    val replayed: Boolean,
    val version: Long,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class AiMessageResponse(
    val id: String,
    val runId: String? = null,
    val role: String,
    val content: String,
    val cardType: String? = null,
    val cardVersion: Int? = null,
    val cardPayload: String? = null,
    val sequenceNo: Long,
    val createdAt: String
)

@Serializable data class AiMessagePage(
    val items: List<AiMessageResponse>,
    val nextCursor: Long? = null
)

@Serializable
data class AgentEventEnvelope(
    val id: String,
    val type: String,
    val version: Int,
    val conversationId: String,
    val runId: String,
    val occurredAt: String,
    val traceId: String? = null,
    val payload: JsonObject
)

@Serializable
data class AgentActionRequest(
    val action: String,
    val recipientUserId: String? = null,
    val candidateId: String? = null,
    val merchantId: String? = null,
    val skuId: String? = null,
    val quantity: Int? = null,
    val optionIds: List<String>? = null,
    val expectedCartVersion: Long? = null,
    val addressId: String? = null,
    val orderId: String? = null,
    val transferId: String? = null
)

@Serializable
data class MemorySettingDto(
    val userId: String,
    val enabled: Boolean,
    val foodPreferenceEnabled: Boolean,
    val allergenAvoidanceEnabled: Boolean,
    val mealBudgetEnabled: Boolean,
    val contactAliasEnabled: Boolean,
    val addressAliasEnabled: Boolean,
    val version: Long,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class UpdateMemorySettingsRequest(
    val enabled: Boolean,
    val foodPreferenceEnabled: Boolean,
    val allergenAvoidanceEnabled: Boolean,
    val mealBudgetEnabled: Boolean,
    val contactAliasEnabled: Boolean,
    val addressAliasEnabled: Boolean,
    val version: Long
)

@Serializable
data class MemoryItemDto(
    val id: String,
    val userId: String,
    val type: String,
    val displayValue: String,
    val referenceType: String? = null,
    val referenceId: String? = null,
    val status: String,
    val consentMessageId: String? = null,
    val consentSource: String = "CHAT_EXPLICIT",
    val version: Long,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class UpdateMemoryItemRequest(
    val displayValue: String,
    val referenceType: String? = null,
    val referenceId: String? = null,
    val version: Long
)

@Serializable
data class CreateMemoryItemRequest(
    val type: String,
    val displayValue: String,
    val referenceType: String? = null,
    val referenceId: String? = null,
    val consentMessageId: String? = null
)

@Serializable private data class AgentProblem(
    val code: String? = null,
    val detail: String? = null,
    val requestId: String? = null
)

interface AiAgentService {
    suspend fun listConversations(): AiConversationPage
    suspend fun createConversation(title: String? = null): AiConversationResponse
    suspend fun renameConversation(
        conversationId: String,
        request: RenameAiConversationRequest
    ): AiConversationResponse
    suspend fun deleteConversation(conversationId: String)
    suspend fun listMessages(conversationId: String): AiMessagePage
    suspend fun createRun(
        conversationId: String,
        request: CreateAgentRunRequest,
        idempotencyKey: String
    ): AgentRunResponse
    suspend fun getRun(runId: String): AgentRunResponse
    suspend fun continueAction(runId: String, request: AgentActionRequest): AgentRunResponse
    fun runEvents(runId: String, lastEventId: String? = null): Flow<AgentEventEnvelope>
    suspend fun memorySettings(): MemorySettingDto
    suspend fun updateMemorySettings(request: UpdateMemorySettingsRequest): MemorySettingDto
    suspend fun memoryItems(): List<MemoryItemDto>
    suspend fun createMemoryItem(request: CreateMemoryItemRequest, idempotencyKey: String): MemoryItemDto
    suspend fun updateMemoryItem(itemId: String, request: UpdateMemoryItemRequest): MemoryItemDto
    suspend fun deleteMemoryItem(itemId: String, version: Long)
}

@Singleton
class AiAgentApi @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val auth: AuthRepository
) : AiAgentService {
    private val baseUrl = BuildConfig.AGENT_BASE_URL.trimEnd('/')

    override suspend fun listConversations(): AiConversationPage =
        execute(Request.Builder().url(url("/api/v1/agent/ai/conversations?limit=100")).get().build())

    override suspend fun createConversation(title: String?): AiConversationResponse =
        post("/api/v1/agent/ai/conversations", CreateAiConversationRequest(title))

    override suspend fun renameConversation(
        conversationId: String,
        request: RenameAiConversationRequest
    ): AiConversationResponse = patch("/api/v1/agent/ai/conversations/$conversationId", request)

    override suspend fun deleteConversation(conversationId: String) =
        delete("/api/v1/agent/ai/conversations/$conversationId")

    override suspend fun listMessages(conversationId: String): AiMessagePage = execute(
        Request.Builder()
            .url(url("/api/v1/agent/ai/conversations/$conversationId/messages?limit=100"))
            .get().build()
    )

    override suspend fun createRun(
        conversationId: String,
        request: CreateAgentRunRequest,
        idempotencyKey: String
    ): AgentRunResponse = post(
        "/api/v1/agent/ai/conversations/$conversationId/runs",
        request,
        idempotencyKey
    )

    override suspend fun getRun(runId: String): AgentRunResponse =
        get("/api/v1/agent/ai/runs/$runId")

    override suspend fun continueAction(
        runId: String,
        request: AgentActionRequest
    ): AgentRunResponse = post("/api/v1/agent/ai/runs/$runId/actions", request)

    override fun runEvents(runId: String, lastEventId: String?): Flow<AgentEventEnvelope> = flow {
        val token = auth.validAccessToken()
            ?: throw AiAgentApiException("NOT_AUTHENTICATED", "请重新登录")
        emitSse(runId, lastEventId, token).collect(::emit)
    }.flowOn(Dispatchers.IO)

    override suspend fun memorySettings(): MemorySettingDto = get("/api/v1/agent/ai/memory/settings")

    override suspend fun updateMemorySettings(
        request: UpdateMemorySettingsRequest
    ): MemorySettingDto = put("/api/v1/agent/ai/memory/settings", request)

    override suspend fun memoryItems(): List<MemoryItemDto> = get("/api/v1/agent/ai/memory/items?limit=100")

    override suspend fun createMemoryItem(
        request: CreateMemoryItemRequest,
        idempotencyKey: String
    ): MemoryItemDto = post("/api/v1/agent/ai/memory/items", request, idempotencyKey)

    override suspend fun updateMemoryItem(
        itemId: String,
        request: UpdateMemoryItemRequest
    ): MemoryItemDto = patch("/api/v1/agent/ai/memory/items/$itemId", request)

    override suspend fun deleteMemoryItem(itemId: String, version: Long) =
        delete("/api/v1/agent/ai/memory/items/$itemId?version=$version")

    private fun emitSse(
        runId: String,
        lastEventId: String?,
        token: String
    ): Flow<AgentEventEnvelope> = callbackFlow {
        val requestBuilder = Request.Builder()
            .url(url("/api/v1/agent/ai/runs/$runId/events"))
            .header("Authorization", "Bearer $token")
            .header("Accept", "text/event-stream")
            .header("X-Request-Id", UUID.randomUUID().toString())
        if (!lastEventId.isNullOrBlank()) requestBuilder.header("Last-Event-ID", lastEventId)
        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (type == "heartbeat") return
                val event = runCatching { json.decodeFromString<AgentEventEnvelope>(data) }
                    .getOrElse {
                        close(AiAgentApiException(
                            "INVALID_SSE_EVENT",
                            "AI 返回了无法识别的事件",
                            cause = it
                        ))
                        eventSource.cancel()
                        return
                    }
                trySend(event)
            }

            override fun onClosed(eventSource: EventSource) {
                close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                close(if (response != null) problem(response) else AiAgentApiException(
                    "NETWORK_UNAVAILABLE",
                    "网络连接中断，请稍后重试",
                    cause = t
                ))
            }
        }
        val source = EventSources.createFactory(client)
            .newEventSource(requestBuilder.build(), listener)
        awaitClose { source.cancel() }
    }

    private suspend inline fun <reified T, reified B> post(
        path: String,
        body: B,
        idempotencyKey: String? = null
    ): T {
        val requestBody = json.encodeToString(body)
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val builder = Request.Builder().url(url(path)).post(requestBody)
        if (idempotencyKey != null) builder.header("Idempotency-Key", idempotencyKey)
        return execute(builder.build())
    }

    private suspend inline fun <reified T> get(path: String): T =
        execute(Request.Builder().url(url(path)).get().build())

    private suspend inline fun <reified T, reified B> patch(path: String, body: B): T =
        execute(Request.Builder().url(url(path)).patch(
            json.encodeToString(body).toRequestBody("application/json; charset=utf-8".toMediaType())
        ).build())

    private suspend inline fun <reified T, reified B> put(path: String, body: B): T =
        execute(Request.Builder().url(url(path)).put(
            json.encodeToString(body).toRequestBody("application/json; charset=utf-8".toMediaType())
        ).build())

    private suspend fun delete(path: String) = withContext(Dispatchers.IO) {
        val token = auth.validAccessToken()
            ?: throw AiAgentApiException("NOT_AUTHENTICATED", "请重新登录")
        val response = try {
            client.newCall(Request.Builder().url(url(path)).delete()
                .header("Authorization", "Bearer $token")
                .header("X-Request-Id", UUID.randomUUID().toString())
                .build()).execute()
        } catch (cause: IOException) {
            throw AiAgentApiException("NETWORK_UNAVAILABLE", "网络不可用，请检查连接后重试", cause = cause)
        }
        response.use { if (!it.isSuccessful) throw problem(it) }
    }

    private suspend inline fun <reified T> execute(request: Request): T =
        withContext(Dispatchers.IO) {
            val token = auth.validAccessToken()
                ?: throw AiAgentApiException("NOT_AUTHENTICATED", "请重新登录")
            val authenticated = request.newBuilder()
                .header("Authorization", "Bearer $token")
                .header("X-Request-Id", UUID.randomUUID().toString())
                .build()
            val response = try {
                client.newCall(authenticated).execute()
            } catch (cause: IOException) {
                throw AiAgentApiException("NETWORK_UNAVAILABLE", "网络不可用，请检查连接后重试", cause = cause)
            }
            response.use {
                if (!it.isSuccessful) throw problem(it)
                runCatching { json.decodeFromString<T>(it.body?.string().orEmpty()) }
                    .getOrElse { cause ->
                        throw AiAgentApiException(
                            "INVALID_RESPONSE",
                            "AI 服务返回异常，请稍后重试",
                            it.code,
                            cause = cause
                        )
                    }
            }
        }

    private fun problem(response: Response): AiAgentApiException {
        val parsed = runCatching {
            json.decodeFromString<AgentProblem>(response.body?.string().orEmpty())
        }.getOrNull()
        return AiAgentApiException(
            parsed?.code ?: "REQUEST_FAILED",
            parsed?.detail ?: "AI 请求失败，请稍后重试",
            response.code,
            parsed?.requestId
        )
    }

    private fun url(path: String): String {
        if (baseUrl.isBlank()) throw AiAgentApiException("AGENT_NOT_CONFIGURED", "AI 服务尚未配置")
        return "$baseUrl$path"
    }
}

class AiAgentApiException(
    val code: String,
    val safeMessage: String,
    val status: Int? = null,
    val requestId: String? = null,
    cause: Throwable? = null
) : RuntimeException(safeMessage, cause)
