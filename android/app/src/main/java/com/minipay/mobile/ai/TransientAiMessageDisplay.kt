package com.minipay.mobile.ai

/**
 * Keeps a user's just-submitted mobile number visible without persisting it or returning it from
 * the Agent service. Entries live only as long as the owning ViewModel/process.
 */
internal class TransientAiMessageDisplay(
    private val maxEntries: Int = 32,
    private val persistentStore: AiOriginalPromptStore? = null
) {
    private val originalPrompts = object : LinkedHashMap<String, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > maxEntries
    }

    fun remember(runId: String, originalPrompt: String) {
        if (EXACT_MOBILE.containsMatchIn(originalPrompt)) {
            originalPrompts[runId] = originalPrompt
            persistentStore?.write(runId, originalPrompt)
        }
    }

    fun displayText(runId: String?, role: String, serverText: String): String =
        if (role == "USER" && runId != null) {
            originalPrompts[runId] ?: persistentStore?.read(runId) ?: serverText
        } else {
            serverText
        }

    private companion object {
        val EXACT_MOBILE = Regex("(?<!\\d)1[3-9]\\d{9}(?!\\d)")
    }
}
