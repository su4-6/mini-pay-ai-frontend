package com.minipay.mobile.ai

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

@Singleton
class AiConversationRepository @Inject constructor(
    private val service: AiAgentService
) {
    suspend fun conversations(): List<AiConversationResponse> = service.listConversations().items
    suspend fun createConversation(): AiConversationResponse = service.createConversation()
    suspend fun renameConversation(
        conversationId: String,
        title: String,
        version: Long
    ): AiConversationResponse = service.renameConversation(
        conversationId,
        RenameAiConversationRequest(title, version)
    )
    suspend fun deleteConversation(conversationId: String) = service.deleteConversation(conversationId)
    suspend fun messages(conversationId: String): List<AiMessageResponse> =
        service.listMessages(conversationId).items
    suspend fun createRun(
        conversationId: String,
        request: CreateAgentRunRequest,
        idempotencyKey: String
    ): AgentRunResponse = service.createRun(conversationId, request, idempotencyKey)
    suspend fun getRun(runId: String): AgentRunResponse = service.getRun(runId)
    suspend fun continueAction(runId: String, request: AgentActionRequest): AgentRunResponse =
        service.continueAction(runId, request)
    suspend fun memorySettings(): MemorySettingDto = service.memorySettings()
    suspend fun updateMemorySettings(request: UpdateMemorySettingsRequest): MemorySettingDto =
        service.updateMemorySettings(request)
    suspend fun memoryItems(): List<MemoryItemDto> = service.memoryItems()
    suspend fun createMemoryItem(value: String, idempotencyKey: String): MemoryItemDto =
        service.createMemoryItem(CreateMemoryItemRequest("CUSTOM", value), idempotencyKey)
    suspend fun updateMemoryItem(item: MemoryItemDto, displayValue: String): MemoryItemDto =
        service.updateMemoryItem(
            item.id,
            UpdateMemoryItemRequest(
                displayValue = displayValue,
                referenceType = item.referenceType,
                referenceId = item.referenceId,
                version = item.version
            )
        )
    suspend fun deleteMemoryItem(item: MemoryItemDto) = service.deleteMemoryItem(item.id, item.version)

    fun resumableEvents(runId: String, initialLastEventId: String? = null): Flow<AgentEventEnvelope> = flow {
        var lastEventId = initialLastEventId
        var failures = 0
        var completed = false
        while (!completed) {
            try {
                service.runEvents(runId, lastEventId).collect { event ->
                    if (event.id.toLongOrNull() != null &&
                        (lastEventId?.toLongOrNull() ?: -1L) < event.id.toLong()
                    ) {
                        lastEventId = event.id
                        emit(event)
                    }
                    if (event.type == "stream.completed") completed = true
                }
                if (!completed) throw AiAgentApiException(
                    "SSE_CLOSED_EARLY",
                    "AI 连接已中断，正在恢复"
                )
            } catch (error: AiAgentApiException) {
                if (!error.retryable() || failures >= MAX_RECONNECTS) throw error
                delay(RECONNECT_DELAYS_MS[failures++])
            }
        }
    }

    fun message(error: Throwable): String = when (error) {
        is AiAgentApiException -> error.safeMessage
        else -> "AI 服务暂时不可用，请稍后重试"
    }

    private fun AiAgentApiException.retryable(): Boolean =
        code == "NETWORK_UNAVAILABLE" || code == "SSE_CLOSED_EARLY" || status?.let { it >= 500 } == true

    private companion object {
        const val MAX_RECONNECTS = 3
        val RECONNECT_DELAYS_MS = longArrayOf(500, 1_000, 2_000)
    }
}
