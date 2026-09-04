package com.minipay.mobile.ui.chat

import com.minipay.mobile.chat.ChatMessage
import com.minipay.mobile.chat.MessageType
import com.minipay.mobile.chat.SenderType
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ChatTimelineTest {
    private val utc = ZoneId.of("UTC")

    @Test
    fun `first message always has time and adjacent messages wait thirty minutes`() {
        val start = Instant.parse("2026-08-07T12:00:00Z").toEpochMilli()
        val timeline = buildChatTimeline(
            listOf(
                message(1, start),
                message(2, start + 29 * 60 * 1000L + 59_000L),
                message(3, start + 59 * 60 * 1000L + 59_000L)
            ),
            utc
        )

        assertEquals("8月7日 12:00", timeline[0].timeHeader)
        assertNull(timeline[1].timeHeader)
        assertNotNull(timeline[2].timeHeader)
    }

    @Test
    fun `exactly thirty minutes and a date boundary show time`() {
        val start = Instant.parse("2026-08-07T23:29:30Z").toEpochMilli()
        val nextDay = Instant.parse("2026-08-08T00:00:00Z").toEpochMilli()
        val timeline = buildChatTimeline(
            listOf(message(1, start), message(2, start + 30 * 60 * 1000L), message(3, nextDay)),
            utc
        )

        assertNotNull(timeline[1].timeHeader)
        assertEquals("8月8日 00:00", timeline[2].timeHeader)
    }

    @Test
    fun `transfer amount is grouped and has two decimals`() {
        assertEquals("¥2,000.00", formatTransferAmount("2000.00"))
    }

    private fun message(id: Long, timestamp: Long) = ChatMessage(
        id = id,
        conversationId = "conv_test",
        senderType = SenderType.Other,
        content = "hello",
        messageType = MessageType.Text,
        transferAmount = null,
        transferStatus = null,
        transferDirection = null,
        timestamp = timestamp
    )
}
