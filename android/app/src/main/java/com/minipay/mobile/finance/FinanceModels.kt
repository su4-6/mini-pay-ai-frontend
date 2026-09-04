package com.minipay.mobile.finance

import kotlinx.serialization.Serializable

@Serializable
data class ConsumerCapabilities(
    val onboardingCompleted: Boolean,
    val realNameStatus: String,
    val realNameVerified: Boolean,
    val payPasswordSet: Boolean
)

@Serializable
data class WalletSummary(
    val walletId: String,
    val availableAmountCent: Long,
    val frozenAmountCent: Long,
    val totalAmountCent: Long,
    val currency: String,
    val status: String,
    val annualOutflowYear: Int,
    val annualOutflowLimitCent: Long,
    val annualOutflowUsedCent: Long,
    val annualOutflowRemainingCent: Long,
    val sandboxNotice: String = "",
    val recentBills: List<WalletBill> = emptyList()
)

@Serializable
data class WalletBill(
    val billId: String,
    val businessType: String,
    val businessNo: String,
    val direction: String,
    val amountCent: Long,
    val counterpartyDisplay: String? = null,
    val counterpartyUserId: String? = null,
    val counterpartyProfile: CounterpartyProfile? = null,
    val remark: String? = null,
    val source: String? = null,
    val status: String,
    val balanceAfterCent: Long? = null,
    val failureCode: String? = null,
    val occurredAt: String,
    val updatedAt: String? = null
)

@Serializable data class CounterpartyProfile(
    val userId: String,
    val nickname: String? = null,
    val avatarUrl: String? = null,
    val avatarUrlExpiresAt: String? = null,
    val legalNameMasked: String? = null
)
@Serializable data class BillTag(val tagId: String, val name: String, val createdAt: String? = null)
@Serializable data class BillManagement(
    val categoryCode: String,
    val tags: List<BillTag> = emptyList(),
    val userNote: String? = null,
    val includedInStatistics: Boolean = true
)
@Serializable data class WalletBillDetail(val bill: WalletBill, val management: BillManagement)
@Serializable data class BillTagPage(val items: List<BillTag> = emptyList(), val page: Int, val size: Int, val total: Long)
@Serializable data class UpdateBillManagementRequest(
    val categoryCode: String,
    val tagIds: List<String>,
    val userNote: String?,
    val includedInStatistics: Boolean
)
@Serializable data class CreateBillTagRequest(val name: String)

@Serializable data class BillPage(val items: List<WalletBill> = emptyList(), val page: Int, val size: Int, val total: Long)
enum class CollectionRecordType { ALL, PERSONAL, MERCHANT }
enum class CollectionRecordPeriod { TODAY, MONTH }
@Serializable data class CollectionRecordSummary(
    val collectionCount: Long = 0,
    val collectionAmountCent: Long = 0,
    val refundCount: Long = 0,
    val refundAmountCent: Long = 0,
    val netAmountCent: Long = 0
)
@Serializable data class CollectionRecordPage(
    val items: List<WalletBill> = emptyList(),
    val page: Int,
    val size: Int,
    val total: Long,
    val type: String,
    val period: String,
    val from: String,
    val to: String,
    val summary: CollectionRecordSummary = CollectionRecordSummary()
)
@Serializable data class TransferMonthSummary(
    val month: String,
    val incomeAmountCent: Long,
    val expenseAmountCent: Long
)
@Serializable data class TransferRecordPage(
    val items: List<WalletBill> = emptyList(),
    val page: Int,
    val size: Int,
    val total: Long,
    val months: List<TransferMonthSummary> = emptyList()
)
@Serializable data class RecentTransferCounterparty(
    val userId: String,
    val nickname: String,
    val avatarUrl: String? = null,
    val avatarUrlExpiresAt: String? = null,
    val lastTransferAt: String
)
@Serializable data class RecentTransferCounterpartyPage(
    val items: List<RecentTransferCounterparty> = emptyList(),
    val page: Int,
    val size: Int,
    val total: Long
)
@Serializable data class BankCard(
    val cardId: String,
    val bankName: String,
    val cardType: String,
    val maskedCardNo: String,
    val status: String,
    val verifiedAt: String? = null
)
@Serializable data class BankBalance(
    val cardId: String,
    val availableAmountCent: Long,
    val currency: String,
    val asOf: String,
    val sandboxNotice: String
)
@Serializable data class BankPaymentLimits(
    val cardId: String,
    val singlePaymentLimitCent: Long,
    val dailyPaymentLimitCent: Long,
    val dailyUsedCent: Long,
    val dailyRemainingCent: Long,
    val currency: String,
    val asOf: String
)
@Serializable data class BankTransaction(
    val transactionId: String,
    val transactionType: String,
    val direction: String,
    val description: String,
    val amountCent: Long,
    val status: String,
    val occurredAt: String
)
@Serializable data class BankTransactionPage(
    val items: List<BankTransaction> = emptyList(),
    val page: Int,
    val size: Int,
    val total: Long
)
@Serializable data class RealNameResult(
    val verificationId: String,
    val status: String,
    val legalNameMasked: String,
    val idNumberMasked: String,
    val failureCode: String? = null
)

sealed interface RealNameCompletion {
    data object SynchronizingSession : RealNameCompletion
    data class Ready(val verificationId: String) : RealNameCompletion
    data class SessionSyncFailed(val verificationId: String) : RealNameCompletion
}

internal object ChineseIdNumber {
    private val weights = intArrayOf(7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2)
    private val checkCodes = charArrayOf('1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2')

    fun normalize(value: String): String = value
        .filter { it.isDigit() || it == 'x' || it == 'X' }
        .uppercase()
        .take(18)

    fun isValid(value: String): Boolean {
        if (!value.matches(Regex("^[1-9]\\d{16}[0-9X]$"))) return false
        val sum = (0 until 17).sumOf { index ->
            (value[index] - '0') * weights[index]
        }
        return checkCodes[sum % 11] == value[17]
    }
}
@Serializable data class TransferIntent(val intentId: String, val amountCent: Long, val status: String, val expiresAt: String)
@Serializable data class TransferOrder(
    val transferId: String,
    val intentId: String,
    val amountCent: Long,
    val status: String,
    val failureCode: String? = null,
    val receiverUserId: String? = null,
    val updatedAt: String? = null
)
@Serializable data class ResolveTransferRecipientRequest(val mobile: String)
@Serializable data class TransferRecipientResponse(
    val recipientUserId: String,
    val nickname: String,
    val phoneMasked: String,
    val legalNameMasked: String? = null,
    val avatarUrl: String? = null,
    val verified: Boolean
)
data class TransferRecipientUi(
    val receiverUserId: String,
    val nickname: String,
    val display: String,
    val accountMasked: String?,
    val legalNameMasked: String?,
    val avatarUrl: String?,
    val transferSource: TransferSource,
    val origin: TransferRecipientOrigin,
    val conversationId: String? = null
)
enum class TransferSource(val wireValue: String) {
    FORM("FORM"),
    AI("AI"),
    PERSONAL_COLLECTION_CODE("PERSONAL_COLLECTION_CODE")
}
enum class TransferRecipientOrigin { MOBILE_LOOKUP, SCAN, CONTACT, GROUP_MEMBER, RECENT_COUNTERPARTY }
@Serializable data class PaymentAuthorization(val authorizationId: String, val paymentAuthToken: String, val expiresAt: String)
@Serializable data class PaymentOrder(
    val paymentOrderId: String,
    val paymentOrderNo: String,
    val amountCent: Long,
    val currency: String,
    val subject: String,
    val paymentMethod: String,
    val status: String,
    val redirectUrl: String? = null,
    val failureCode: String? = null,
    val expiresAt: String,
    val updatedAt: String
)
@Serializable data class CollectionCode(val type: String, val deepLink: String, val expiresAt: String, val sandboxNotice: String? = null)
@Serializable data class ScanResolution(
    val type: String = "",
    val receiverUserId: String? = null,
    val receiverDisplay: String? = null,
    val receiverNickname: String = "",
    val receiverAvatarUrl: String? = null,
    val receiverLegalNameMasked: String? = null,
    val resolutionId: String? = null,
    val merchantId: String? = null,
    val merchantName: String? = null,
    val appId: String? = null,
    val allowedChannels: List<String> = emptyList()
)
@Serializable data class CollectionReceiptEvent(
    val eventId: String,
    val billId: String,
    val source: String,
    val amountCent: Long,
    val counterpartyDisplay: String? = null,
    val occurredAt: String
)

@Serializable data class SetPaymentPasswordRequest(val paymentPassword: String)
@Serializable data class CreateTransferRequest(val receiverUserId: String, val amountCent: Long, val remark: String? = null, val source: String = "FORM")
@Serializable data class ConfirmRequest(val paymentAuthToken: String)
@Serializable data class CreatePaymentOrderRequest(
    val amountCent: Long,
    val subject: String,
    val paymentMethod: String,
    val resolutionId: String
)
@Serializable data class MerchantPaymentOrder(
    val paymentOrderId: String,
    val paymentOrderNo: String,
    val amountCent: Long,
    val status: String,
    val failureCode: String? = null
)
@Serializable data class IssueAuthorizationRequest(val subjectType: String, val subjectId: String, val amountCent: Long, val deviceId: String, val payPassword: String)
@Serializable data class ScanRequest(val deepLink: String)
@Serializable data class BindBankCardRequest(val holderName: String, val cardNumber: String, val verificationCode: String)
@Serializable data class FundingRequest(val bankCardId: String, val amountCent: Long)
@Serializable data class BalanceQueryRequest(val paymentAuthToken: String)
@Serializable data class RechargeOrder(
    val rechargeId: String,
    val rechargeNo: String,
    val bankCardId: String,
    val amountCent: Long,
    val status: String,
    val failureCode: String? = null,
    val channel: String = "BANK_CARD",
    val updatedAt: String? = null
)
@Serializable data class WithdrawalOrder(
    val withdrawalId: String,
    val withdrawalNo: String,
    val bankCardId: String,
    val amountCent: Long,
    val status: String,
    val failureCode: String? = null,
    val updatedAt: String? = null
)
@Serializable data class FundingOrderListItem(
    val applicationId: String,
    val businessNo: String,
    val bankCardId: String,
    val bankName: String,
    val maskedCardNo: String,
    val amountCent: Long,
    val status: String,
    val failureCode: String? = null,
    val createdAt: String,
    val updatedAt: String
)
@Serializable data class FundingOrderPage(
    val items: List<FundingOrderListItem> = emptyList(),
    val page: Int,
    val size: Int,
    val total: Long
)

data class FundingResult(
    val type: String,
    val cardId: String,
    val amountCent: Long,
    val status: String,
    val failureCode: String? = null
)

fun ScanResolution.toTransferRecipientUi(
    source: TransferSource = TransferSource.PERSONAL_COLLECTION_CODE
) =
    TransferRecipientUi(
    receiverUserId = requireNotNull(receiverUserId) { "Personal collection resolution is missing receiverUserId" },
        nickname = receiverNickname.ifBlank { receiverDisplay ?: "收款人" },
        display = receiverDisplay ?: receiverNickname.ifBlank { "收款人" },
        accountMasked = null,
        legalNameMasked = receiverLegalNameMasked,
        avatarUrl = receiverAvatarUrl,
        transferSource = source,
        origin = TransferRecipientOrigin.SCAN
    )

fun TransferRecipientResponse.toTransferRecipientUi() = TransferRecipientUi(
    receiverUserId = recipientUserId,
    nickname = nickname,
    display = if (legalNameMasked.isNullOrBlank()) "$nickname（未实名）" else "$nickname（$legalNameMasked）",
    accountMasked = phoneMasked,
    legalNameMasked = legalNameMasked,
    avatarUrl = avatarUrl,
    transferSource = TransferSource.FORM,
    origin = TransferRecipientOrigin.MOBILE_LOOKUP
)

/** Converts a user-entered yuan amount without floating-point rounding. */
internal fun yuanToCent(value: String): Long? {
    if (!value.matches(Regex("^(?:0|[1-9]\\d*)(?:\\.\\d{1,2})?$"))) return null
    return runCatching {
        java.math.BigDecimal(value)
            .movePointRight(2)
            .longValueExact()
            .takeIf { it in 1..1_000_000L }
    }.getOrNull()
}

/** Client-side feedback only; Payment remains the authoritative card validator. */
internal object BankCardNumber {
    /** The sandbox accepts only ASCII card digits, 16 through 19 characters. */
    fun normalize(value: String): String = value.filter { it in '0'..'9' }.take(19)

    fun isValid(value: String): Boolean {
        val number = normalize(value)
        return number.length in 16..19
    }
}
