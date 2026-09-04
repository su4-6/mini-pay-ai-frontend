package com.minipay.mobile.ai

internal data class AiSpeechCandidate(val sourceId: String, val text: String)

internal class AiSpeechPolicy(
    private val maxRememberedSources: Int = 200
) {
    private val selectedSources = LinkedHashSet<String>()

    fun select(
        messages: List<AiMessageResponse>,
        completedRunId: String,
        completedMessageId: String?,
        completedCardType: String?,
        fallbackText: String?
    ): AiSpeechCandidate? {
        val candidate = selectAssistantSpeechCandidate(
            messages,
            completedRunId,
            completedMessageId,
            completedCardType,
            fallbackText
        ) ?: return null
        if (!selectedSources.add(candidate.sourceId)) return null
        while (selectedSources.size > maxRememberedSources) {
            selectedSources.remove(selectedSources.first())
        }
        return candidate
    }
}

private val SPEAKABLE_STRUCTURED_CARD_TYPES = setOf("agent.missing-slots")

internal fun selectAssistantSpeechCandidate(
    messages: List<AiMessageResponse>,
    completedRunId: String,
    completedMessageId: String?,
    completedCardType: String?,
    fallbackText: String?
): AiSpeechCandidate? {
    if (completedCardType != null && completedCardType !in SPEAKABLE_STRUCTURED_CARD_TYPES) return null
    val persisted = messages.lastOrNull {
        (completedMessageId == null || it.id == completedMessageId) &&
            it.runId == completedRunId &&
            it.role == "ASSISTANT" &&
            (it.cardType == null || it.cardType in SPEAKABLE_STRUCTURED_CARD_TYPES) &&
            it.content.isNotBlank()
    }
    if (persisted != null) return AiSpeechCandidate(persisted.id, persisted.content)
    val fallback = fallbackText?.trim().orEmpty()
    return fallback.takeIf { it.isNotEmpty() }?.let {
        AiSpeechCandidate(completedMessageId ?: "run:$completedRunId", it)
    }
}
