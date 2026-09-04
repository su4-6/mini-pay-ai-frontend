package com.minipay.mobile.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiSpeechPolicyTest {
    @Test
    fun selectsOnlyPlainAssistantMessageFromCompletedRun() {
        val messages = listOf(
            message("old", "run-old", "ASSISTANT", "历史回复"),
            message("user", "run-new", "USER", "用户内容"),
            message("card", "run-new", "ASSISTANT", "结构化卡片", cardType = "BALANCE"),
            message("answer", "run-new", "ASSISTANT", "本次新回复")
        )

        assertEquals(
            AiSpeechCandidate("answer", "本次新回复"),
            selectAssistantSpeechCandidate(messages, "run-new", "answer", null, null)
        )
        assertNull(selectAssistantSpeechCandidate(messages, "missing-run", null, null, null))
    }

    @Test
    fun fallsBackToCompletedStreamTextButNeverReadsSensitiveStructuredCard() {
        assertEquals(
            AiSpeechCandidate("message-new", "完整流式回复"),
            selectAssistantSpeechCandidate(
                messages = emptyList(),
                completedRunId = "run-new",
                completedMessageId = "message-new",
                completedCardType = null,
                fallbackText = "完整流式回复"
            )
        )
        listOf(
            "wallet.card",
            "bills.card",
            "payment.transfer-intent",
            "payment.transfer-order"
        ).forEach { cardType ->
            assertNull(
                selectAssistantSpeechCandidate(
                    messages = emptyList(),
                    completedRunId = "run-new",
                    completedMessageId = "message-new",
                    completedCardType = cardType,
                    fallbackText = "不应朗读"
                )
            )
        }
    }

    @Test
    fun readsMissingTransferSlotsButOnlyOncePerCompletedMessage() {
        val messages = listOf(
            message(
                "missing-slots-message",
                "run-transfer",
                "ASSISTANT",
                "请告诉我收款人和转账金额。",
                cardType = "agent.missing-slots"
            )
        )
        val policy = AiSpeechPolicy()

        assertEquals(
            AiSpeechCandidate("missing-slots-message", "请告诉我收款人和转账金额。"),
            policy.select(
                messages,
                "run-transfer",
                "missing-slots-message",
                "agent.missing-slots",
                null
            )
        )
        assertNull(
            policy.select(
                messages,
                "run-transfer",
                "missing-slots-message",
                "agent.missing-slots",
                null
            )
        )
    }

    private fun message(
        id: String,
        runId: String,
        role: String,
        content: String,
        cardType: String? = null
    ) = AiMessageResponse(
        id = id,
        runId = runId,
        role = role,
        content = content,
        cardType = cardType,
        sequenceNo = 1,
        createdAt = "2026-08-09T00:00:00Z"
    )
}
