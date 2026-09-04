package com.minipay.mobile.finance

import java.io.IOException
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentStatusMonitorTest {
    private val reference = PaymentResultReference(PaymentOperation.TRANSFER, "transfer-1")
    private val processing = PaymentResultSnapshot(reference, PaymentResultStatus.PROCESSING, 5_000)

    @Test
    fun terminalInitialStateDoesNotQueryAgain() = runTest {
        var queries = 0
        val monitor = PaymentStatusMonitor(PaymentStatusReader {
            queries += 1
            error("terminal state must not be queried")
        })

        val events = monitor.observe(processing.copy(status = PaymentResultStatus.SUCCEEDED)).toList()

        assertEquals(1, events.size)
        assertEquals(0, queries)
    }

    @Test
    fun processingStateConvergesWithoutCreatingOrConfirmingAgain() = runTest {
        var queries = 0
        val monitor = PaymentStatusMonitor(PaymentStatusReader {
            queries += 1
            if (queries == 1) processing else processing.copy(status = PaymentResultStatus.SUCCEEDED)
        })

        val events = monitor.observe(processing).toList()

        assertEquals(2, queries)
        assertEquals(
            listOf(PaymentResultStatus.PROCESSING, PaymentResultStatus.PROCESSING, PaymentResultStatus.SUCCEEDED),
            events.filterIsInstance<PaymentMonitorEvent.Updated>().map { it.snapshot.status }
        )
    }

    @Test
    fun transientNetworkFailureKeepsPolling() = runTest {
        var queries = 0
        val monitor = PaymentStatusMonitor(PaymentStatusReader {
            queries += 1
            if (queries == 1) throw IOException("temporary")
            processing.copy(status = PaymentResultStatus.FAILED, failureCode = "DECLINED")
        })

        val events = monitor.observe(processing).toList()

        assertTrue(events.any { it is PaymentMonitorEvent.RefreshFailed })
        assertEquals(PaymentResultStatus.FAILED, (events.last() as PaymentMonitorEvent.Updated).snapshot.status)
        assertEquals(2, queries)
    }

    @Test
    fun pollingStopsAfterThirtySeconds() = runTest {
        var queries = 0
        val monitor = PaymentStatusMonitor(PaymentStatusReader {
            queries += 1
            processing
        })

        val events = monitor.observe(processing).toList()

        assertEquals(8, queries)
        assertTrue(events.last() is PaymentMonitorEvent.TimedOut)
    }

    @Test
    fun manualRefreshPerformsOneAuthoritativeQuery() = runTest {
        var queries = 0
        val monitor = PaymentStatusMonitor(PaymentStatusReader {
            queries += 1
            processing.copy(status = PaymentResultStatus.SUCCEEDED)
        })

        val snapshot = monitor.refresh(reference)

        assertEquals(1, queries)
        assertEquals(PaymentResultStatus.SUCCEEDED, snapshot.status)
    }

    @Test
    fun cancellingCollectionCancelsFutureQueries() = runTest {
        var queries = 0
        val monitor = PaymentStatusMonitor(PaymentStatusReader {
            queries += 1
            processing
        })

        monitor.observe(processing).take(2).toList()

        assertEquals(1, queries)
    }
}
