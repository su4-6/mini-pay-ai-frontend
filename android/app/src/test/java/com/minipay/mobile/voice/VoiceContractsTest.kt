package com.minipay.mobile.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceContractsTest {
    @Test
    fun splitsLongSpeechAtNaturalBoundariesWithoutLosingText() {
        val chunks = splitSpeechText("第一句话。第二句话很长。第三句话。", maxLength = 12)

        assertTrue(chunks.all { it.length <= 12 })
        assertEquals("第一句话。第二句话很长。第三句话。", chunks.joinToString(""))
    }

    @Test
    fun blankSpeechProducesNoChunks() {
        assertTrue(splitSpeechText("   \n ").isEmpty())
    }

    @Test
    fun finalTranscriptAppendsToDraftWithoutSubmittingOrOverflowing() {
        assertEquals("已有草稿 新识别内容", mergeVoiceTranscript("已有草稿 ", " 新识别内容 "))
        assertEquals("已有草稿", mergeVoiceTranscript("已有草稿", "  "))
        assertEquals(null, mergeVoiceTranscript("12345", "67890", maxLength = 9))
    }
}
