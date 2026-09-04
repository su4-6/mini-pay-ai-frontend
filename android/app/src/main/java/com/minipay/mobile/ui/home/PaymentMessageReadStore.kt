package com.minipay.mobile.ui.home

import android.content.Context
import com.minipay.mobile.finance.WalletBill
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

internal data class PaymentMessageCursor(
    val occurredAt: String,
    val billId: String
)

internal fun newestPaymentMessageCursor(bills: List<WalletBill>): PaymentMessageCursor? =
    bills.maxWithOrNull(
        compareBy<WalletBill>({ parsePaymentMessageTime(it.occurredAt) }, { it.billId })
    )?.let { PaymentMessageCursor(it.occurredAt, it.billId) }

internal fun hasNewPaymentMessage(
    stored: PaymentMessageCursor,
    newest: PaymentMessageCursor?
): Boolean {
    newest ?: return false
    val storedTime = parsePaymentMessageTime(stored.occurredAt)
    val newestTime = parsePaymentMessageTime(newest.occurredAt)
    return newestTime > storedTime || (newestTime == storedTime && newest.billId != stored.billId)
}

private fun parsePaymentMessageTime(value: String): Instant =
    runCatching { Instant.parse(value) }.getOrDefault(Instant.EPOCH)

@Singleton
internal class PaymentMessageReadStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences(
        "payment_message_read_cursor",
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun initializeAndCheck(userId: String, bills: List<WalletBill>): Boolean {
        val newest = newestPaymentMessageCursor(bills) ?: return false
        val stored = read(userId)
        if (stored == null) {
            write(userId, newest)
            return false
        }
        return hasNewPaymentMessage(stored, newest)
    }

    @Synchronized
    fun markRead(userId: String, bills: List<WalletBill>) {
        newestPaymentMessageCursor(bills)?.let { write(userId, it) }
    }

    private fun read(userId: String): PaymentMessageCursor? {
        val occurredAt = preferences.getString("$userId.occurred_at", null) ?: return null
        val billId = preferences.getString("$userId.bill_id", null) ?: return null
        return PaymentMessageCursor(occurredAt, billId)
    }

    private fun write(userId: String, cursor: PaymentMessageCursor) {
        preferences.edit()
            .putString("$userId.occurred_at", cursor.occurredAt)
            .putString("$userId.bill_id", cursor.billId)
            .apply()
    }
}
