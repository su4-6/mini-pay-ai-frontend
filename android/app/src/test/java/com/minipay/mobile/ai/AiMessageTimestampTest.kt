package com.minipay.mobile.ai

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiMessageTimestampTest {
    @Test
    fun parsesValidServerTimestampAndRejectsMalformedValue() {
        assertEquals(
            Instant.parse("2026-08-09T01:30:00Z"),
            parseAiMessageInstant("2026-08-09T01:30:00Z")
        )
        assertNull(parseAiMessageInstant("not-a-timestamp"))
    }
}
