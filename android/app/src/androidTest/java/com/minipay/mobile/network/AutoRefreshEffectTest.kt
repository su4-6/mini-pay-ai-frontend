package com.minipay.mobile.network

import androidx.compose.ui.test.junit4.createComposeRule
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AutoRefreshEffectTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun reconnectionSignalsAreCoalescedForVisiblePage() {
        val provider = FakeNetworkStatusProvider()
        val refreshCount = AtomicInteger()
        compose.setContent {
            AutoRefreshEffect(statusProvider = provider) {
                refreshCount.incrementAndGet()
            }
        }

        compose.runOnIdle {
            provider.emitReconnection()
            provider.emitReconnection()
        }

        compose.waitUntil(timeoutMillis = 2_000) { refreshCount.get() == 1 }
        compose.runOnIdle { assertEquals(1, refreshCount.get()) }
    }

    @Test
    fun disabledPageDoesNotRefresh() {
        val provider = FakeNetworkStatusProvider()
        val refreshCount = AtomicInteger()
        compose.setContent {
            AutoRefreshEffect(enabled = false, statusProvider = provider) {
                refreshCount.incrementAndGet()
            }
        }

        compose.runOnIdle { provider.emitReconnection() }
        Thread.sleep(500)
        compose.runOnIdle { assertEquals(0, refreshCount.get()) }
    }

    private class FakeNetworkStatusProvider : NetworkStatusProvider {
        override val isOnline: StateFlow<Boolean> = MutableStateFlow(true)
        private val mutableReconnections = MutableSharedFlow<Unit>(
            extraBufferCapacity = 2,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        override val reconnections: Flow<Unit> = mutableReconnections

        fun emitReconnection() {
            check(mutableReconnections.tryEmit(Unit))
        }
    }
}
