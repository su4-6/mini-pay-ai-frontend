package com.minipay.mobile.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minipay.mobile.auth.AuthRepository
import com.minipay.mobile.finance.FinanceRepository
import com.minipay.mobile.finance.WalletBill
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PaymentMessagesUiState(
    val bills: List<WalletBill> = emptyList(),
    val timeline: List<PaymentMessageDay> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val loadMoreError: String? = null,
    val hasUnread: Boolean = false,
    val page: Int = 0,
    val total: Long = 0
)

data class PaymentMessageDay(
    val date: LocalDate,
    val items: List<PaymentMessageItem>
)

data class PaymentMessageItem(
    val billId: String,
    val title: String,
    val counterparty: String?,
    val remark: String?,
    val amountCent: Long,
    val credit: Boolean,
    val occurredAt: Instant
)

internal fun paymentMessageTimeline(
    bills: List<WalletBill>,
    zoneId: ZoneId = ZoneId.systemDefault()
): List<PaymentMessageDay> = bills
    .filter { it.status == "SUCCEEDED" }
    .distinctBy { it.billId }
    .sortedByDescending { it.occurredAt }
    .mapNotNull { bill ->
        val occurredAt = runCatching { Instant.parse(bill.occurredAt) }.getOrNull()
            ?: return@mapNotNull null
        val credit = bill.direction == "CREDIT"
        PaymentMessageItem(
            billId = bill.billId,
            title = paymentMessageTitle(bill.businessType, credit),
            counterparty = bill.counterpartyDisplay,
            remark = bill.remark?.takeIf(String::isNotBlank),
            amountCent = bill.amountCent,
            credit = credit,
            occurredAt = occurredAt
        )
    }
    .groupBy { it.occurredAt.atZone(zoneId).toLocalDate() }
    .map { (date, items) -> PaymentMessageDay(date, items) }

internal fun paymentMessageTitle(businessType: String, credit: Boolean): String =
    when (businessType.uppercase()) {
        "TRANSFER" -> if (credit) "收到转账" else "转账成功"
        "RECHARGE" -> "充值成功"
        "WITHDRAWAL" -> "提现成功"
        "COLLECTION" -> "收款成功"
        "REFUND", "REVERSAL" -> "退款到账"
        else -> if (credit) "入账成功" else "付款成功"
    }

@HiltViewModel
class PaymentMessagesViewModel @Inject internal constructor(
    private val repository: FinanceRepository,
    private val authRepository: AuthRepository,
    private val readStore: PaymentMessageReadStore
) : ViewModel() {
    private val mutableState = MutableStateFlow(PaymentMessagesUiState())
    val state: StateFlow<PaymentMessagesUiState> = mutableState.asStateFlow()
    private var refreshJob: Job? = null

    init { refresh() }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        mutableState.value = mutableState.value.copy(
            loading = mutableState.value.bills.isEmpty(),
            error = null
        )
        refreshJob = viewModelScope.launch {
            runCatching { repository.bills(page = 1, size = 20) }
            .onSuccess { first ->
                val bills = first.items
                    .filter { it.status == "SUCCEEDED" }
                    .distinctBy { it.billId }
                    .sortedByDescending { it.occurredAt }
                val unread = authRepository.currentUserId.value
                    ?.let { readStore.initializeAndCheck(it, bills) }
                    ?: false
                mutableState.value = PaymentMessagesUiState(
                    bills = bills,
                    timeline = paymentMessageTimeline(bills),
                    loading = false,
                    hasUnread = unread,
                    page = first.page,
                    total = first.total
                )
            }.onFailure {
                mutableState.value = mutableState.value.copy(loading = false, error = "支付消息加载失败")
            }
        }
    }

    fun loadMore() {
        val current = mutableState.value
        if (current.loading || current.loadingMore || current.bills.size >= current.total) return
        mutableState.value = current.copy(loadingMore = true)
        viewModelScope.launch {
            runCatching {
                repository.bills(page = current.page + 1, size = 20)
            }.onSuccess { page ->
                val bills = (current.bills + page.items.filter { it.status == "SUCCEEDED" })
                        .distinctBy { it.billId }
                        .sortedByDescending { it.occurredAt }
                mutableState.value = mutableState.value.copy(
                    bills = bills,
                    timeline = paymentMessageTimeline(bills),
                    page = page.page,
                    total = page.total,
                    loadingMore = false,
                    loadMoreError = null
                )
            }.onFailure {
                mutableState.value = mutableState.value.copy(
                    loadingMore = false,
                    loadMoreError = "加载更多失败"
                )
            }
        }
    }

    fun markVisibleMessagesRead() {
        val current = mutableState.value
        val userId = authRepository.currentUserId.value ?: return
        readStore.markRead(userId, current.bills)
        if (current.hasUnread) mutableState.value = current.copy(hasUnread = false)
    }
}
