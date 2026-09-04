package com.minipay.mobile.finance

import org.junit.Assert.assertEquals
import org.junit.Test

class PaymentResultModelsTest {
    @Test
    fun mapsEverySupportedOrderToAUnifiedSnapshot() {
        val transfer = TransferOrder("transfer-1", "intent-1", 100, "PROCESSING", updatedAt = "2026-08-09T01:00:00Z")
        val recharge = RechargeOrder("recharge-1", "R1", "card-1", 200, "SUCCEEDED", updatedAt = "2026-08-09T01:01:00Z")
        val withdrawal = WithdrawalOrder("withdrawal-1", "W1", "card-1", 300, "FAILED", "LIMIT_EXCEEDED")
        val payment = PaymentOrder(
            paymentOrderId = "payment-1",
            paymentOrderNo = "P1",
            amountCent = 400,
            currency = "CNY",
            subject = "外卖订单",
            paymentMethod = "WALLET_BALANCE",
            status = "CLOSED",
            expiresAt = "2026-08-09T01:30:00Z",
            updatedAt = "2026-08-09T01:02:00Z"
        )

        val snapshots = listOf(
            transfer.toPaymentResultSnapshot(),
            recharge.toPaymentResultSnapshot(),
            withdrawal.toPaymentResultSnapshot(),
            payment.toPaymentResultSnapshot()
        )

        assertEquals(PaymentOperation.entries, snapshots.map { it.reference.operation })
        assertEquals(
            listOf(
                PaymentResultStatus.PROCESSING,
                PaymentResultStatus.SUCCEEDED,
                PaymentResultStatus.FAILED,
                PaymentResultStatus.CLOSED
            ),
            snapshots.map { it.status }
        )
        assertEquals(listOf(100L, 200L, 300L, 400L), snapshots.map { it.amountCent })
        assertEquals("P1", snapshots.last().businessNo)
        assertEquals("外卖订单", snapshots.last().subject)
    }

    @Test
    fun presentationAddsOnlySafeDisplayFields() {
        val snapshot = TransferOrder("transfer-1", "intent-1", 5_000, "PROCESSING")
            .toPaymentResultSnapshot()
            .withPresentation(PaymentResultPresentation(counterparty = "小李", method = "账户余额"))

        assertEquals("小李", snapshot.counterparty)
        assertEquals("账户余额", snapshot.method)
        assertEquals("transfer-1", snapshot.reference.orderId)
    }
}
