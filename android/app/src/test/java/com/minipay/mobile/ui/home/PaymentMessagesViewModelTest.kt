package com.minipay.mobile.ui.home

import com.minipay.mobile.finance.WalletBill
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentMessagesViewModelTest {
    @Test
    fun timelineUsesOnlySuccessfulBillsAndGroupsNewestFirst() {
        val bills = listOf(
            bill("older", "DEBIT", 1_205, occurredAt = "2026-08-07T10:00:00Z"),
            bill("newer", "CREDIT", 88_000, businessType = "COLLECTION", occurredAt = "2026-08-08T10:00:00Z"),
            bill("failed", "DEBIT", 999_999, status = "FAILED", occurredAt = "2026-08-09T10:00:00Z")
        )

        val result = paymentMessageTimeline(bills, ZoneOffset.UTC)

        assertEquals(listOf(LocalDate.of(2026, 8, 8), LocalDate.of(2026, 8, 7)), result.map { it.date })
        assertEquals("收款成功", result.first().items.single().title)
        assertTrue(result.first().items.single().credit)
        assertEquals("-¥12.05", paymentMessageMoney(1_205, credit = false))
    }

    @Test
    fun readCursorDetectsOnlyMessagesAfterStoredMarker() {
        val stored = PaymentMessageCursor("2026-08-08T10:00:00Z", "same")

        assertFalse(hasNewPaymentMessage(stored, null))
        assertFalse(hasNewPaymentMessage(stored, PaymentMessageCursor("2026-08-08T10:00:00Z", "same")))
        assertTrue(hasNewPaymentMessage(stored, PaymentMessageCursor("2026-08-08T10:00:01Z", "next")))
        assertTrue(hasNewPaymentMessage(stored, PaymentMessageCursor("2026-08-08T10:00:00Z", "different")))
    }

    private fun bill(
        id: String,
        direction: String,
        amount: Long,
        status: String = "SUCCEEDED",
        businessType: String = "TRANSFER",
        occurredAt: String
    ) = WalletBill(
        billId = id,
        businessType = businessType,
        businessNo = id,
        direction = direction,
        amountCent = amount,
        status = status,
        occurredAt = occurredAt
    )
}
