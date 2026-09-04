package com.minipay.mobile.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CollectionReceiptAnnouncementTest {
    @Test
    fun formatsCentsWithoutFloatingPoint() {
        assertEquals("MiniPay 收款到账 12 元", formatCollectionReceiptAnnouncement(1_200))
        assertEquals("MiniPay 收款到账 12 元 34 分", formatCollectionReceiptAnnouncement(1_234))
    }

    @Test
    fun acceptsEachPersonalReceiptOnlyOnce() {
        val filter = CollectionReceiptAnnouncementFilter()
        val event = receipt("event-1", "PERSONAL_COLLECTION_CODE", 88)

        assertEquals("MiniPay 收款到账 0 元 88 分", filter.announcement(event))
        assertNull(filter.announcement(event))
    }

    @Test
    fun ignoresOtherSourcesAndInvalidAmounts() {
        val filter = CollectionReceiptAnnouncementFilter()

        assertNull(filter.announcement(receipt("event-1", "FORM", 100)))
        assertNull(filter.announcement(receipt("event-2", "PERSONAL_COLLECTION_CODE", 0)))
    }

    private fun receipt(id: String, source: String, amountCent: Long) = CollectionReceiptEvent(
        eventId = id,
        billId = "bill-$id",
        source = source,
        amountCent = amountCent,
        occurredAt = "2026-08-09T00:00:00Z"
    )
}
