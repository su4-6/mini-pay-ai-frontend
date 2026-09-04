package com.minipay.mobile.voice

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

sealed interface VoiceInputState {
    data object Idle : VoiceInputState
    data class Listening(val partialText: String = "") : VoiceInputState
    data object Processing : VoiceInputState
    data class Error(val message: String) : VoiceInputState
}

enum class SpeechChannel { AI, RECEIPT }
enum class SpeechBlocker { VOICE_INPUT, VOICE_CALL }

sealed interface SpeechOutputState {
    data object Initializing : SpeechOutputState
    data object Ready : SpeechOutputState
    data class Speaking(val channel: SpeechChannel) : SpeechOutputState
    data class Unavailable(val message: String) : SpeechOutputState
}

interface VoiceInputController {
    val state: StateFlow<VoiceInputState>
    val finalTranscripts: SharedFlow<String>
    fun start()
    fun stop()
    fun cancel()
}

interface SpeechOutput {
    val state: StateFlow<SpeechOutputState>
    val errors: SharedFlow<String>
    fun prepare()
    fun speakAi(messageId: String, text: String)
    fun speakReceipt(eventId: String, text: String)
    fun stop(channel: SpeechChannel)
    fun stopAll()
    fun setBlocked(blocker: SpeechBlocker, blocked: Boolean)
}

interface VoiceSettings {
    val aiSpeechEnabled: StateFlow<Boolean>
    val receiptSpeechEnabled: StateFlow<Boolean>
    fun setAiSpeechEnabled(enabled: Boolean)
    fun setReceiptSpeechEnabled(enabled: Boolean)
}

internal fun mergeVoiceTranscript(existing: String, transcript: String, maxLength: Int = 1_000): String? {
    val normalized = transcript.trim()
    if (normalized.isEmpty()) return existing
    val prefix = existing.trimEnd()
    val merged = if (prefix.isEmpty()) normalized else "$prefix $normalized"
    return merged.takeIf { it.length <= maxLength }
}

internal fun splitSpeechText(text: String, maxLength: Int = 3_500): List<String> {
    val normalized = text.replace(Regex("\\s+"), " ").trim()
    if (normalized.isEmpty()) return emptyList()
    if (normalized.length <= maxLength) return listOf(normalized)
    val chunks = mutableListOf<String>()
    var remaining = normalized
    while (remaining.isNotEmpty()) {
        if (remaining.length <= maxLength) {
            chunks += remaining
            break
        }
        val candidate = remaining.substring(0, maxLength)
        val boundary = listOf('。', '！', '？', '；', '，', '.', '!', '?', ';', ',')
            .maxOf { candidate.lastIndexOf(it) }
            .takeIf { it >= maxLength / 2 }
            ?: candidate.lastIndexOf(' ').takeIf { it >= maxLength / 2 }
            ?: maxLength
        chunks += remaining.substring(0, boundary + if (boundary < maxLength) 1 else 0).trim()
        remaining = remaining.substring((boundary + if (boundary < maxLength) 1 else 0).coerceAtMost(remaining.length)).trim()
    }
    return chunks.filter(String::isNotEmpty)
}
