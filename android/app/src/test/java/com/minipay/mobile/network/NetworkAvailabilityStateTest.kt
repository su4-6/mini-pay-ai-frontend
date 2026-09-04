package com.minipay.mobile.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkAvailabilityStateTest {
    @Test
    fun onlyOfflineToOnlineTransitionReportsRecovery() {
        val state = NetworkAvailabilityState(initialOnline = true)

        assertFalse(state.update(online = true))
        assertFalse(state.update(online = false))
        assertFalse(state.update(online = false))
        assertTrue(state.update(online = true))
        assertFalse(state.update(online = true))
    }
}
