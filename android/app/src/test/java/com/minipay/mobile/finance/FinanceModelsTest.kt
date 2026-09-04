package com.minipay.mobile.finance

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceModelsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodesBankBalanceLimitsAndTransactionPage() {
        val balance = json.decodeFromString<BankBalance>(
            """{"cardId":"card-1","availableAmountCent":10000000,"currency":"CNY","asOf":"2026-08-05T00:00:00Z","sandboxNotice":"沙箱"}"""
        )
        val page = json.decodeFromString<BankTransactionPage>(
            """{"items":[{"transactionId":"tx-1","transactionType":"DEBIT","direction":"EXPENSE","description":"MiniPay 充值","amountCent":600,"status":"SUCCEEDED","occurredAt":"2026-08-05T00:00:00Z"}],"page":1,"size":20,"total":1}"""
        )

        assertEquals(10_000_000L, balance.availableAmountCent)
        assertEquals("EXPENSE", page.items.single().direction)
        assertTrue(page.total == 1L)
    }

    @Test
    fun balanceAuthorizationUsesZeroAmountAndCardSubject() {
        val request = IssueAuthorizationRequest(
            subjectType = "BANK_CARD_BALANCE_QUERY",
            subjectId = "card-1",
            amountCent = 0,
            deviceId = "device-1",
            payPassword = "123456"
        )

        assertEquals("BANK_CARD_BALANCE_QUERY", request.subjectType)
        assertEquals(0L, request.amountCent)
    }

    @Test
    fun mobileRecipientUsesTheExistingFormTransferSource() {
        val recipient = TransferRecipientResponse(
            recipientUserId = "recipient-1",
            nickname = "小满",
            phoneMasked = "155****7517",
            legalNameMasked = "张*",
            verified = true
        ).toTransferRecipientUi()

        assertEquals(TransferSource.FORM, recipient.transferSource)
        assertEquals("FORM", recipient.transferSource.wireValue)
        assertEquals(TransferRecipientOrigin.MOBILE_LOOKUP, recipient.origin)
    }

    @Test
    fun scannedPersonalCollectionKeepsItsDedicatedTransferSource() {
        val recipient = ScanResolution(
            type = "PERSONAL_COLLECTION",
            receiverUserId = "recipient-1",
            receiverNickname = "小满"
        ).toTransferRecipientUi()

        assertEquals(TransferSource.PERSONAL_COLLECTION_CODE, recipient.transferSource)
        assertEquals(TransferRecipientOrigin.SCAN, recipient.origin)
    }

    @Test
    fun decodesEnrichedBillDetailWithoutLosingAuthoritativeBalanceFields() {
        val detail = json.decodeFromString<WalletBillDetail>(
            """{"bill":{"billId":"0198","businessType":"TRANSFER","businessNo":"T1","source":"FORM","direction":"INCOME","amountCent":100,"counterpartyUserId":"user-1","counterpartyProfile":{"userId":"user-1","nickname":"子昂","avatarUrl":"https://example.test/a.jpg"},"status":"SUCCEEDED","balanceAfterCent":10419,"occurredAt":"2026-08-06T07:28:02Z"},"management":{"categoryCode":"TRANSFER","tags":[{"tagId":"tag-1","name":"朋友"}],"userNote":"午餐","includedInStatistics":false}}"""
        )

        assertEquals("子昂", detail.bill.counterpartyProfile?.nickname)
        assertEquals(10_419L, detail.bill.balanceAfterCent)
        assertEquals("朋友", detail.management.tags.single().name)
        assertTrue(!detail.management.includedInStatistics)
    }

    @Test
    fun rechargeAndWithdrawalPagesUseApplicationOrdersAndCompleteStatuses() {
        val page = json.decodeFromString<FundingOrderPage>(
            """{"items":[{"applicationId":"order-1","businessNo":"R1","bankCardId":"card-1","bankName":"建设银行","maskedCardNo":"**** 1234","amountCent":500,"status":"PENDING_CONFIRMATION","createdAt":"2026-08-08T08:00:00Z","updatedAt":"2026-08-08T08:00:00Z"},{"applicationId":"order-2","businessNo":"R2","bankCardId":"card-1","bankName":"建设银行","maskedCardNo":"**** 1234","amountCent":600,"status":"CLOSED","failureCode":"CONFIRMATION_EXPIRED","createdAt":"2026-08-08T07:00:00Z","updatedAt":"2026-08-08T07:02:00Z"}],"page":1,"size":20,"total":2}"""
        )

        assertEquals(listOf("PENDING_CONFIRMATION", "CLOSED"), page.items.map { it.status })
        assertEquals(2L, page.total)
        assertEquals(2, page.items.distinctBy { it.applicationId }.size)
    }

    @Test
    fun decodesAuthoritativeCollectionSummaryAndRefundRecord() {
        val page = json.decodeFromString<CollectionRecordPage>(
            """{"items":[{"billId":"bill-1","businessType":"MERCHANT_REFUND","businessNo":"R1","source":"MERCHANT_REFUND","direction":"EXPENSE","amountCent":500,"status":"SUCCEEDED","occurredAt":"2026-08-10T00:00:00Z"}],"page":1,"size":20,"total":1,"type":"MERCHANT","period":"TODAY","from":"2026-08-09T16:00:00Z","to":"2026-08-10T16:00:00Z","summary":{"collectionCount":2,"collectionAmountCent":3000,"refundCount":1,"refundAmountCent":500,"netAmountCent":2500}}"""
        )

        assertEquals("MERCHANT_REFUND", page.items.single().source)
        assertEquals(500L, page.summary.refundAmountCent)
        assertEquals(2_500L, page.summary.netAmountCent)
    }
}
