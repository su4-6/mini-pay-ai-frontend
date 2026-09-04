package com.minipay.mobile.food

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FoodBindingSynchronizationTest {
    @Test
    fun `does not start a duplicate binding while event binding becomes ready`() = runTest {
        var checks = 0
        var directBindings = 0

        synchronizeFoodBinding(
            isReady = { ++checks == 3 },
            bindDirectly = { directBindings++ },
            attempts = 4,
            retryDelayMillis = 0
        )

        assertEquals(3, checks)
        assertEquals(0, directBindings)
    }

    @Test
    fun `uses direct binding when the authorization event was not processed`() = runTest {
        var checks = 0
        var directBindings = 0

        synchronizeFoodBinding(
            isReady = { checks++; false },
            bindDirectly = { directBindings++ },
            attempts = 3,
            retryDelayMillis = 0
        )

        assertEquals(3, checks)
        assertEquals(1, directBindings)
    }
}
