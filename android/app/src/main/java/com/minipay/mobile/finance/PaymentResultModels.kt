package com.minipay.mobile.finance

enum class PaymentOperation {
    TRANSFER,
    RECHARGE,
    WITHDRAWAL,
    PAYMENT
}

enum class PaymentResultStatus {
    PROCESSING,
    SUCCEEDED,
    FAILED,
    CLOSED;

    val terminal: Boolean get() = this != PROCESSING
}

data class PaymentResultReference(
    val operation: PaymentOperation,
    val orderId: String
)

data class PaymentResultPresentation(
    val counterparty: String? = null,
    val method: String? = null
)

data class PaymentResultSnapshot(
    val reference: PaymentResultReference,
    val status: PaymentResultStatus,
    val amountCent: Long,
    val businessNo: String? = null,
    val subject: String? = null,
    val method: String? = null,
    val counterparty: String? = null,
    val updatedAt: String? = null,
    val failureCode: String? = null
) {
    fun withPresentation(presentation: PaymentResultPresentation): PaymentResultSnapshot = copy(
        counterparty = presentation.counterparty ?: counterparty,
        method = presentation.method ?: method
    )
}

data class PaymentResultUiState(
    val snapshot: PaymentResultSnapshot,
    val polling: Boolean = false,
    val timedOut: Boolean = false,
    val refreshing: Boolean = false,
    val refreshError: Boolean = false
)

internal fun String.toPaymentResultStatus(): PaymentResultStatus = when (this) {
    "SUCCEEDED" -> PaymentResultStatus.SUCCEEDED
    "FAILED" -> PaymentResultStatus.FAILED
    "CLOSED" -> PaymentResultStatus.CLOSED
    else -> PaymentResultStatus.PROCESSING
}

fun TransferOrder.toPaymentResultSnapshot() = PaymentResultSnapshot(
    reference = PaymentResultReference(PaymentOperation.TRANSFER, transferId),
    status = status.toPaymentResultStatus(),
    amountCent = amountCent,
    businessNo = transferId,
    updatedAt = updatedAt,
    failureCode = failureCode
)

fun RechargeOrder.toPaymentResultSnapshot() = PaymentResultSnapshot(
    reference = PaymentResultReference(PaymentOperation.RECHARGE, rechargeId),
    status = status.toPaymentResultStatus(),
    amountCent = amountCent,
    businessNo = rechargeNo,
    method = "银行卡",
    updatedAt = updatedAt,
    failureCode = failureCode
)

fun WithdrawalOrder.toPaymentResultSnapshot() = PaymentResultSnapshot(
    reference = PaymentResultReference(PaymentOperation.WITHDRAWAL, withdrawalId),
    status = status.toPaymentResultStatus(),
    amountCent = amountCent,
    businessNo = withdrawalNo,
    method = "银行卡",
    updatedAt = updatedAt,
    failureCode = failureCode
)

fun PaymentOrder.toPaymentResultSnapshot() = PaymentResultSnapshot(
    reference = PaymentResultReference(PaymentOperation.PAYMENT, paymentOrderId),
    status = status.toPaymentResultStatus(),
    amountCent = amountCent,
    businessNo = paymentOrderNo,
    subject = subject,
    method = when (paymentMethod) {
        "WALLET_BALANCE" -> "账户余额"
        "ALIPAY" -> "支付宝沙箱"
        "WECHAT_PAY" -> "微信支付沙箱"
        else -> "已选择支付方式"
    },
    updatedAt = updatedAt,
    failureCode = failureCode
)
