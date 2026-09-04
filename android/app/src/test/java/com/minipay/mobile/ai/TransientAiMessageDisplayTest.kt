package com.minipay.mobile.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class TransientAiMessageDisplayTest {
    @Test
    fun restoresOriginalMobileOnlyForTheMatchingUserRun() {
        val display = TransientAiMessageDisplay()
        display.remember("run-1", "转给13838517417 1元")

        assertEquals(
            "转给13838517417 1元",
            display.displayText("run-1", "USER", "转给[MOBILE_EXACT] 1元")
        )
        assertEquals(
            "账户138****7418",
            display.displayText("run-1", "ASSISTANT", "账户138****7418")
        )
        assertEquals(
            "转给[MOBILE_EXACT] 1元",
            display.displayText("another-run", "USER", "转给[MOBILE_EXACT] 1元")
        )
    }

    @Test
    fun doesNotPersistAcrossDisplayInstances() {
        TransientAiMessageDisplay().remember("run-1", "转给13838517417 1元")

        assertEquals(
            "转给[MOBILE_EXACT] 1元",
            TransientAiMessageDisplay().displayText("run-1", "USER", "转给[MOBILE_EXACT] 1元")
        )
    }

    @Test
    fun ignoresPromptsWithoutAnExactMobile() {
        val display = TransientAiMessageDisplay()
        display.remember("run-1", "转给叶顺光1元")

        assertEquals(
            "服务端消息",
            display.displayText("run-1", "USER", "服务端消息")
        )
    }

    @Test
    fun restoresMobileFromPrivateStoreAfterDisplayIsRecreated() {
        val values = mutableMapOf<String, String>()
        val store = object : AiOriginalPromptStore {
            override fun write(runId: String, prompt: String) {
                values[runId] = prompt
            }

            override fun read(runId: String): String? = values[runId]
        }
        TransientAiMessageDisplay(persistentStore = store)
            .remember("run-1", "转给13838517417 1元")

        assertEquals(
            "转给13838517417 1元",
            TransientAiMessageDisplay(persistentStore = store)
                .displayText("run-1", "USER", "转给[MOBILE_EXACT] 1元")
        )
    }
}
