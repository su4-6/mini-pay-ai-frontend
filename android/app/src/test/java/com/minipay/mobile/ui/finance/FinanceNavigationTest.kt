package com.minipay.mobile.ui.finance

import com.minipay.mobile.finance.FinanceDestination
import com.minipay.mobile.finance.BankCard
import com.minipay.mobile.finance.CounterpartyProfile
import com.minipay.mobile.finance.WalletBill
import com.minipay.mobile.profile.UserProfile
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FinanceNavigationTest {
    @Test
    fun verifiedUserWithoutPaymentPasswordContinuesToPasswordSetup() {
        assertEquals(
            FinanceDestination.PAYMENT_PASSWORD,
            destinationAfterRealName("VERIFIED", payPasswordSet = false)
        )
    }

    @Test
    fun verifiedUserWithPaymentPasswordContinuesToWallet() {
        assertEquals(
            FinanceDestination.WALLET,
            destinationAfterRealName("VERIFIED", payPasswordSet = true)
        )
    }

    @Test
    fun processingVerificationReturnsToStatusGate() {
        assertEquals(
            FinanceDestination.WALLET,
            destinationAfterRealName("PROCESSING", payPasswordSet = false)
        )
    }

    @Test
    fun rejectedVerificationStaysOnForm() {
        assertNull(destinationAfterRealName("REJECTED", payPasswordSet = false))
    }

    @Test
    fun scannerOpensBeforePaymentSetupAndDefersChecksUntilPayment() {
        assertEquals(false, requiresPaymentPassword(FinanceDestination.SCAN))
        assertEquals(false, requiresPaymentPassword(FinanceDestination.WALLET))
        assertEquals(false, requiresPaymentPassword(FinanceDestination.RECEIVE))
    }

    @Test
    fun showsNicknameAndOnlyServerMaskedLegalNameOnCollectionCode() {
        val profile = UserProfile(
            userId = "user-1",
            nickname = "小满",
            miniPayNo = "MP001",
            version = 1,
            legalNameMasked = "张*"
        )

        assertEquals("小满（张*）", collectionRecipientDisplayName(profile))
        assertEquals("小满", collectionRecipientDisplayName(profile.copy(legalNameMasked = null)))
    }

    @Test
    fun refreshesCollectionCodeOneMinuteBeforeExpiry() {
        val now = Instant.parse("2026-08-06T00:00:00Z").toEpochMilli()

        assertEquals(60_000L, collectionCodeRefreshDelayMillis(
            Instant.ofEpochMilli(now + 120_000L), now
        ))
        assertEquals(0L, collectionCodeRefreshDelayMillis(
            Instant.ofEpochMilli(now + 30_000L), now
        ))
    }

    @Test
    fun onlyAcceptsMiniPayPersonalCollectionLinksBeforeResolving() {
        assertEquals(
            true,
            isPersonalCollectionDeepLink("minipay://collect/personal?token=token-value")
        )
        assertEquals(false, isPersonalCollectionDeepLink("https://collect/personal?token=token-value"))
        assertEquals(false, isPersonalCollectionDeepLink("minipay://collect/merchant?token=token-value"))
        assertEquals(false, isPersonalCollectionDeepLink("minipay://collect/personal"))
    }

    @Test
    fun transferAvatarAlwaysRepresentsTheReceiver() {
        val owner = UserProfile(
            userId = "owner",
            nickname = "本人",
            miniPayNo = "MP001",
            avatarUrl = "https://example.test/owner.jpg",
            version = 1
        )
        val counterparty = CounterpartyProfile(
            userId = "other",
            nickname = "对方",
            avatarUrl = "https://example.test/other.jpg"
        )
        val outgoing = walletBill("EXPENSE", counterparty)
        val incoming = walletBill("INCOME", counterparty)

        assertEquals("https://example.test/other.jpg", billReceiverAvatarUrl(outgoing, owner))
        assertEquals("https://example.test/owner.jpg", billReceiverAvatarUrl(incoming, owner))
    }

    @Test
    fun disabledCardsAreNotShownAsBoundCards() {
        val active = bankCard("active", "ACTIVE")
        val disabled = bankCard("disabled", "DISABLED")

        assertEquals(listOf(active), activeBankCards(listOf(disabled, active)))
    }

    private fun walletBill(direction: String, profile: CounterpartyProfile) = WalletBill(
        billId = "bill-$direction",
        businessType = "TRANSFER",
        businessNo = "T-$direction",
        direction = direction,
        amountCent = 100,
        counterpartyProfile = profile,
        status = "SUCCEEDED",
        occurredAt = "2026-08-08T08:00:00Z"
    )

    private fun bankCard(cardId: String, status: String) = BankCard(
        cardId = cardId,
        bankName = "测试银行",
        cardType = "DEBIT",
        maskedCardNo = "**** 1234",
        status = status
    )
}
