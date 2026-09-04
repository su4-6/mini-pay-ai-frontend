package com.minipay.mobile.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class AiConversationRepositoryTest {
    @Test
    fun reconnectsFromLastEventAndDoesNotEmitDuplicates() = runTest {
        val service = ReconnectingAgentService()
        val events = AiConversationRepository(service).resumableEvents("run-1").toList()

        assertEquals(listOf("1", "2"), events.map { it.id })
        assertEquals(listOf(null, "1"), service.lastEventIds)
    }

    private class ReconnectingAgentService : AiAgentService {
        val lastEventIds = mutableListOf<String?>()

        override fun runEvents(runId: String, lastEventId: String?): Flow<AgentEventEnvelope> {
            lastEventIds += lastEventId
            return if (lastEventIds.size == 1) flow {
                emit(event("1", "message.delta"))
                throw AiAgentApiException("NETWORK_UNAVAILABLE", "断线")
            } else flowOf(event("1", "message.delta"), event("2", "stream.completed"))
        }

        private fun event(id: String, type: String) = AgentEventEnvelope(
            id, type, 1, "conversation-1", "run-1", "2026-08-08T00:00:00Z", payload = buildJsonObject {}
        )

        override suspend fun listConversations() = unsupported<AiConversationPage>()
        override suspend fun createConversation(title: String?) = unsupported<AiConversationResponse>()
        override suspend fun renameConversation(conversationId: String, request: RenameAiConversationRequest) =
            unsupported<AiConversationResponse>()
        override suspend fun deleteConversation(conversationId: String) = Unit
        override suspend fun listMessages(conversationId: String) = unsupported<AiMessagePage>()
        override suspend fun createRun(
            conversationId: String,
            request: CreateAgentRunRequest,
            idempotencyKey: String
        ) = unsupported<AgentRunResponse>()
        override suspend fun getRun(runId: String) = unsupported<AgentRunResponse>()
        override suspend fun continueAction(runId: String, request: AgentActionRequest) =
            unsupported<AgentRunResponse>()
        override suspend fun memorySettings() = unsupported<MemorySettingDto>()
        override suspend fun updateMemorySettings(request: UpdateMemorySettingsRequest) =
            unsupported<MemorySettingDto>()
        override suspend fun memoryItems() = emptyList<MemoryItemDto>()
        override suspend fun createMemoryItem(
            request: CreateMemoryItemRequest,
            idempotencyKey: String
        ) = unsupported<MemoryItemDto>()
        override suspend fun updateMemoryItem(itemId: String, request: UpdateMemoryItemRequest) =
            unsupported<MemoryItemDto>()
        override suspend fun deleteMemoryItem(itemId: String, version: Long) = Unit

        private fun <T> unsupported(): T = throw UnsupportedOperationException()
    }
}
