package com.minipay.mobile.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationSnapshotTest {
    @Test
    fun acceptsOnlyFiveMinuteInMemorySnapshot() {
        val now = 1_000_000L
        assertTrue(LocationSnapshot(34.0, 112.0, capturedAtEpochMillis = now - 299_999L)
            .isFresh(now))
        assertFalse(LocationSnapshot(34.0, 112.0, capturedAtEpochMillis = now - 300_001L)
            .isFresh(now))
        assertFalse(LocationSnapshot(34.0, 112.0, capturedAtEpochMillis = now + 1L)
            .isFresh(now))
    }
}
