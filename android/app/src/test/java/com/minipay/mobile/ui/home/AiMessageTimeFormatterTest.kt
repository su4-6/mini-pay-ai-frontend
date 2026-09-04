package com.minipay.mobile.ui.home

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiMessageTimeFormatterTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = Instant.parse("2026-08-09T04:00:00Z")

    @Test
    fun formatsTodayYesterdayCurrentYearAndPreviousYear() {
        assertEquals("11:30", formatAiMessageTime(Instant.parse("2026-08-09T03:30:00Z"), now, zone))
        assertEquals("昨天 20:15", formatAiMessageTime(Instant.parse("2026-08-08T12:15:00Z"), now, zone))
        assertEquals("7月1日 09:05", formatAiMessageTime(Instant.parse("2026-07-01T01:05:00Z"), now, zone))
        assertEquals("2025年12月31日 23:59", formatAiMessageTime(Instant.parse("2025-12-31T15:59:00Z"), now, zone))
    }

    @Test
    fun groupsOnlyAdjacentMessagesInTheSameMinute() {
        val first = Instant.parse("2026-08-09T01:30:05Z")
        val sameMinute = Instant.parse("2026-08-09T01:30:59Z")
        val nextMinute = Instant.parse("2026-08-09T01:31:00Z")

        assertTrue(shouldShowAiMessageTime(first, null))
        assertFalse(shouldShowAiMessageTime(sameMinute, first))
        assertTrue(shouldShowAiMessageTime(nextMinute, sameMinute))
        assertFalse(shouldShowAiMessageTime(null, nextMinute))
    }
}
