package com.minipay.mobile.finance

import java.util.LinkedHashSet

internal fun formatCollectionReceiptAnnouncement(amountCent: Long): String {
    require(amountCent > 0) { "amountCent must be positive" }
    val yuan = amountCent / 100
    val fen = amountCent % 100
    return if (fen == 0L) "MiniPay 收款到账 $yuan 元"
    else "MiniPay 收款到账 $yuan 元 $fen 分"
}

internal class CollectionReceiptAnnouncementFilter(
    private val maxRemembered: Int = 200
) {
    private val received = LinkedHashSet<String>()

    @Synchronized
    fun announcement(event: CollectionReceiptEvent): String? {
        if (event.source != "PERSONAL_COLLECTION_CODE" || event.amountCent <= 0) return null
        if (!received.add(event.eventId)) return null
        while (received.size > maxRemembered) received.remove(received.first())
        return formatCollectionReceiptAnnouncement(event.amountCent)
    }
}
