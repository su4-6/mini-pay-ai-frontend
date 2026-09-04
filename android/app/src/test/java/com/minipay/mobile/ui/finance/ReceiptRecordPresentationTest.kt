package com.minipay.mobile.ui.finance

import com.minipay.mobile.finance.CounterpartyProfile
import com.minipay.mobile.finance.WalletBill
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptRecordPresentationTest {
    @Test
    fun payerPresentationPrefersResolvedIdentityData() {
        val bill = bill(
            counterpartyDisplay = "站内付款人",
            counterpartyProfile = CounterpartyProfile(
                userId = "user-2",
                nickname = "小满",
                legalNameMasked = "张*"
            )
        )

        assertEquals("小满", receiptRecordPayerName(bill))
        assertEquals("实名 张* · 个人收钱码", receiptRecordPayerDetail(bill))
    }

    @Test
    fun dateLabelsUseHumanFriendlyChineseGroups() {
        val today = LocalDate.of(2026, 8, 10)

        assertEquals("今日", receiptRecordDateTitle(today, today))
        assertEquals("昨天", receiptRecordDateTitle(today.minusDays(1), today))
        assertEquals("8月8日 星期六", receiptRecordDateTitle(today.minusDays(2), today))
    }

    @Test
    fun payerPresentationFallsBackWithoutExposingRawIdentifiers() {
        val bill = bill(counterpartyDisplay = null, counterpartyProfile = null)

        assertEquals("付款方", receiptRecordPayerName(bill))
        assertEquals("个人收钱码", receiptRecordPayerDetail(bill))
    }

    private fun bill(
        counterpartyDisplay: String?,
        counterpartyProfile: CounterpartyProfile?
    ) = WalletBill(
        billId = "bill-1",
        businessType = "TRANSFER",
        businessNo = "T1",
        direction = "INCOME",
        amountCent = 1_000,
        counterpartyDisplay = counterpartyDisplay,
        counterpartyProfile = counterpartyProfile,
        source = "PERSONAL_COLLECTION_CODE",
        status = "SUCCEEDED",
        occurredAt = "2026-08-09T08:13:00Z"
    )
}
