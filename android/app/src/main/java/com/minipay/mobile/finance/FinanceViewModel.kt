package com.minipay.mobile.finance

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minipay.mobile.auth.AuthRepository
import com.minipay.mobile.auth.IdentityApiException
import com.minipay.mobile.profile.UserProfile
import com.minipay.mobile.chat.ChatRepository
import com.minipay.mobile.chat.TransferReceiptConversationType
import com.minipay.mobile.voice.SpeechChannel
import com.minipay.mobile.voice.SpeechOutput
import com.minipay.mobile.voice.VoiceSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive

enum class FinanceDestination {
    WALLET, BILLS, ALL_BILLS, REAL_NAME, PAYMENT_PASSWORD, SCAN, TRANSFER, CARDS,
    ADD_CARD, CARD_DETAIL, CARD_BALANCE, CARD_TRANSACTIONS,
    RECHARGE, WITHDRAWAL, FUNDING_RESULT, RECHARGE_RECORDS, WITHDRAWAL_RECORDS,
    RECEIVE, RECEIPT_RECORDS, MERCHANT_RECEIVE, MERCHANT_LOCATION_PICKER, MERCHANT_PAYMENT,
    BALANCE_DETAIL, BILL_DETAIL,
    RECENT_TRANSFER_CONTACTS, FRIEND_TRANSFER_RECORDS
}

data class FinanceUiState(
    val loading: Boolean = true,
    val capabilities: ConsumerCapabilities? = null,
    val walletLoading: Boolean = false,
    val wallet: WalletSummary? = null,
    val walletError: String? = null,
    val bills: List<WalletBill> = emptyList(),
    val billPage: Int = 0,
    val billTotal: Long = 0,
    val billsLoading: Boolean = false,
    val billsLoadingMore: Boolean = false,
    val selectedBillDetail: WalletBillDetail? = null,
    val billDetailLoading: Boolean = false,
    val billTags: List<BillTag> = emptyList(),
    val billManagementSaving: Boolean = false,
    val billManagementSaved: Boolean = false,
    val rechargeRecords: List<FundingOrderListItem> = emptyList(),
    val rechargeRecordPage: Int = 0,
    val rechargeRecordTotal: Long = 0,
    val rechargeRecordsLoading: Boolean = false,
    val withdrawalRecords: List<FundingOrderListItem> = emptyList(),
    val withdrawalRecordPage: Int = 0,
    val withdrawalRecordTotal: Long = 0,
    val withdrawalRecordsLoading: Boolean = false,
    val fundingRecordsError: String? = null,
    val cards: List<BankCard> = emptyList(),
    val cardsLoading: Boolean = false,
    val cardsError: String? = null,
    val selectedCard: BankCard? = null,
    val bankBalance: BankBalance? = null,
    val bankPaymentLimits: BankPaymentLimits? = null,
    val bankTransactions: List<BankTransaction> = emptyList(),
    val bankTransactionPage: Int = 0,
    val bankTransactionTotal: Long = 0,
    val bankDataLoading: Boolean = false,
    val bankTransactionsLoadingMore: Boolean = false,
    val collectionCode: CollectionCode? = null,
    val collectionRecipient: UserProfile? = null,
    val currentUserProfile: UserProfile? = null,
    val collectionCodeLoading: Boolean = false,
    val collectionCodeError: String? = null,
    val collectionRecordsLoading: Boolean = false,
    val collectionRecordsLoadingMore: Boolean = false,
    val collectionRecordPage: Int = 0,
    val collectionRecordTotal: Long = 0,
    val collectionRecordsError: String? = null,
    val receiptSpeechEnabled: Boolean = true,
    val recipientLookupLoading: Boolean = false,
    val recipientLookupResult: TransferRecipientUi? = null,
    val recipientLookupError: String? = null,
    val transferFriends: List<TransferRecipientUi> = emptyList(),
    val transferFriendsLoading: Boolean = false,
    val transferFriendsError: String? = null,
    val recentTransferCounterparties: List<RecentTransferCounterparty> = emptyList(),
    val recentTransferCounterpartiesLoading: Boolean = false,
    val recentTransferCounterpartiesError: String? = null,
    val transferRecords: List<WalletBill> = emptyList(),
    val transferRecordMonths: List<TransferMonthSummary> = emptyList(),
    val transferRecordPage: Int = 0,
    val transferRecordTotal: Long = 0,
    val transferRecordsLoading: Boolean = false,
    val transferRecordsLoadingMore: Boolean = false,
    val transferRecordsError: String? = null,
    val submitting: Boolean = false,
    val fundingResult: FundingResult? = null,
    val paymentResult: PaymentResultUiState? = null,
    val realNameCompletion: RealNameCompletion? = null,
    val message: String? = null
)

@HiltViewModel
class FinanceViewModel @Inject constructor(
    private val repository: FinanceRepository,
    private val auth: AuthRepository,
    private val chatRepository: ChatRepository,
    private val paymentStatusMonitor: PaymentStatusMonitor,
    private val voiceSettings: VoiceSettings,
    private val speechOutput: SpeechOutput,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val mutableState = MutableStateFlow(FinanceUiState())
    private val receivedCollectionReceiptEvents = linkedSetOf<String>()
    private var collectionReceiptObserver: Job? = null
    private var refreshJob: Job? = null
    private var paymentMonitorJob: Job? = null
    private var paymentPresentation = PaymentResultPresentation()
    private var paymentTerminalAction: ((PaymentResultSnapshot) -> Unit)? = null
    val state: StateFlow<FinanceUiState> = mutableState.asStateFlow()

    fun queueTransferReceipt(conversationId: String?, order: TransferOrder, recipient: TransferRecipientUi) {
        if (order.status != "SUCCEEDED") return
        val conversationType = when (recipient.origin) {
            TransferRecipientOrigin.GROUP_MEMBER -> TransferReceiptConversationType.GROUP
            TransferRecipientOrigin.CONTACT -> TransferReceiptConversationType.DIRECT
            else -> return
        }
        val receiptConversationId = when (conversationType) {
            TransferReceiptConversationType.GROUP -> conversationId ?: recipient.conversationId ?: return
            TransferReceiptConversationType.DIRECT -> conversationId ?: recipient.conversationId.orEmpty()
        }
        viewModelScope.launch {
            chatRepository.queueTransferReceipt(
                receiptConversationId,
                order.transferId,
                recipient.receiverUserId,
                recipient.display,
                conversationType
            )
        }
    }

    init {
        viewModelScope.launch {
            voiceSettings.receiptSpeechEnabled.collect { enabled ->
                mutableState.value = mutableState.value.copy(receiptSpeechEnabled = enabled)
            }
        }
        viewModelScope.launch {
            auth.currentUserId.collectLatest { userId ->
                collectionReceiptObserver?.cancel()
                collectionReceiptObserver = null
                receivedCollectionReceiptEvents.clear()
                mutableState.value = FinanceUiState(
                    loading = userId != null,
                    receiptSpeechEnabled = voiceSettings.receiptSpeechEnabled.value
                )
                if (userId != null) {
                    refresh()
                    resumeSavedPaymentResult()
                }
            }
        }
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            val userId = auth.currentUserId.value ?: return@launch
            // Preserve already rendered data while the app returns from background or the
            // network reconnects.  A reload must not turn a usable wallet/cards screen blank.
            mutableState.value = mutableState.value.copy(
                loading = mutableState.value.capabilities == null,
                message = null
            )
            val capabilities = runCatching { repository.capabilities() }.getOrElse { error ->
                if (auth.currentUserId.value == userId) fail(error)
                return@launch
            }
            if (auth.currentUserId.value != userId) return@launch
            mutableState.value = mutableState.value.copy(
                loading = false,
                capabilities = capabilities,
                walletError = null,
                cardsError = null
            )
            if (!capabilities.realNameVerified) {
                mutableState.value = mutableState.value.copy(
                    wallet = null,
                    cards = emptyList(),
                    walletLoading = false,
                    cardsLoading = false
                )
                return@launch
            }
            refreshWalletAndCards(userId)
        }
    }

    fun refreshCards() {
        viewModelScope.launch {
            val userId = auth.currentUserId.value ?: return@launch
            refreshCards(userId)
        }
    }

    fun loadBills(
        direction: String? = null,
        businessType: String? = null,
        reset: Boolean = true
    ) {
        val nextPage = if (reset) 1 else mutableState.value.billPage + 1
        if (!reset && mutableState.value.bills.size >= mutableState.value.billTotal) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(
                billsLoading = reset,
                billsLoadingMore = !reset,
                message = null,
                bills = if (reset) emptyList() else mutableState.value.bills
            )
            runCatching { repository.bills(direction, businessType = businessType, page = nextPage) }
                .onSuccess { page ->
                    val existing = if (reset) emptyList() else mutableState.value.bills
                    mutableState.value = mutableState.value.copy(
                        bills = existing + page.items,
                        billPage = page.page,
                        billTotal = page.total,
                        billsLoading = false,
                        billsLoadingMore = false
                    )
                }
                .onFailure(::fail)
        }
    }

    fun loadBill(billId: String) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(
                billDetailLoading = true,
                selectedBillDetail = null,
                billManagementSaved = false,
                message = null
            )
            supervisorScope {
                val detail = async { runCatching { repository.bill(billId) } }
                val tags = async { runCatching { repository.billTags().items } }
                tags.await().onSuccess { mutableState.value = mutableState.value.copy(billTags = it) }
                detail.await().onSuccess {
                    mutableState.value = mutableState.value.copy(
                        selectedBillDetail = it,
                        billDetailLoading = false
                    )
                }.onFailure(::fail)
            }
        }
    }

    fun loadRecentTransferCounterparties() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(
                recentTransferCounterpartiesLoading = true,
                recentTransferCounterpartiesError = null
            )
            runCatching { repository.recentTransferCounterparties() }
                .onSuccess { page -> mutableState.value = mutableState.value.copy(
                    recentTransferCounterparties = page.items,
                    recentTransferCounterpartiesLoading = false
                ) }
                .onFailure { error -> mutableState.value = mutableState.value.copy(
                    recentTransferCounterpartiesLoading = false,
                    recentTransferCounterpartiesError = friendlyMessage(error)
                ) }
        }
    }

    fun loadTransferRecords(
        counterpartyUserId: String,
        direction: String?,
        month: String?,
        status: String?,
        reset: Boolean = true
    ) {
        val nextPage = if (reset) 1 else mutableState.value.transferRecordPage + 1
        if (!reset && mutableState.value.transferRecords.size >= mutableState.value.transferRecordTotal) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(
                transferRecordsLoading = reset,
                transferRecordsLoadingMore = !reset,
                transferRecordsError = null,
                transferRecords = if (reset) emptyList() else mutableState.value.transferRecords
            )
            runCatching { repository.transferRecords(counterpartyUserId, direction, month, status, nextPage) }
                .onSuccess { page ->
                    val existing = if (reset) emptyList() else mutableState.value.transferRecords
                    mutableState.value = mutableState.value.copy(
                        transferRecords = existing + page.items,
                        transferRecordMonths = page.months,
                        transferRecordPage = page.page,
                        transferRecordTotal = page.total,
                        transferRecordsLoading = false,
                        transferRecordsLoadingMore = false
                    )
                }
                .onFailure { error -> mutableState.value = mutableState.value.copy(
                    transferRecordsLoading = false,
                    transferRecordsLoadingMore = false,
                    transferRecordsError = friendlyMessage(error)
                ) }
        }
    }

    fun saveBillManagement(
        billId: String,
        categoryCode: String,
        tagIds: List<String>,
        userNote: String?,
        includedInStatistics: Boolean
    ) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(
                billManagementSaving = true, billManagementSaved = false, message = null
            )
            runCatching {
                repository.updateBillManagement(
                    billId,
                    UpdateBillManagementRequest(
                        categoryCode, tagIds, userNote?.trim()?.ifBlank { null }, includedInStatistics
                    )
                )
            }.onSuccess {
                mutableState.value = mutableState.value.copy(
                    selectedBillDetail = it,
                    billManagementSaving = false,
                    billManagementSaved = true
                )
            }.onFailure(::fail)
        }
    }

    fun createBillTag(name: String, onCreated: (BillTag) -> Unit = {}) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(billManagementSaving = true, message = null)
            runCatching { repository.createBillTag(name) }.onSuccess { tag ->
                mutableState.value = mutableState.value.copy(
                    billTags = (mutableState.value.billTags + tag).distinctBy { it.tagId },
                    billManagementSaving = false
                )
                onCreated(tag)
            }.onFailure(::fail)
        }
    }

    fun loadFundingRecords(type: String, reset: Boolean = true) {
        val recharge = type == "RECHARGE"
        val current = if (recharge) mutableState.value.rechargeRecords else mutableState.value.withdrawalRecords
        val currentPage = if (recharge) mutableState.value.rechargeRecordPage else mutableState.value.withdrawalRecordPage
        val total = if (recharge) mutableState.value.rechargeRecordTotal else mutableState.value.withdrawalRecordTotal
        if (!reset && current.size >= total) return
        val nextPage = if (reset) 1 else currentPage + 1
        viewModelScope.launch {
            mutableState.value = if (recharge) mutableState.value.copy(
                rechargeRecordsLoading = true, fundingRecordsError = null,
                rechargeRecords = if (reset) emptyList() else current
            ) else mutableState.value.copy(
                withdrawalRecordsLoading = true, fundingRecordsError = null,
                withdrawalRecords = if (reset) emptyList() else current
            )
            runCatching {
                if (recharge) repository.rechargeOrders(nextPage) else repository.withdrawalOrders(nextPage)
            }.onSuccess { page ->
                mutableState.value = if (recharge) mutableState.value.copy(
                    rechargeRecords = (if (reset) emptyList() else current) + page.items,
                    rechargeRecordPage = page.page, rechargeRecordTotal = page.total,
                    rechargeRecordsLoading = false
                ) else mutableState.value.copy(
                    withdrawalRecords = (if (reset) emptyList() else current) + page.items,
                    withdrawalRecordPage = page.page, withdrawalRecordTotal = page.total,
                    withdrawalRecordsLoading = false
                )
            }.onFailure { error ->
                mutableState.value = mutableState.value.copy(
                    rechargeRecordsLoading = false,
                    withdrawalRecordsLoading = false,
                    fundingRecordsError = friendlyMessage(error)
                )
            }
        }
    }

    fun loadCollectionRecords(reset: Boolean = true) {
        val current = mutableState.value
        val nextPage = if (reset) 1 else current.collectionRecordPage + 1
        if (!reset && current.bills.size >= current.collectionRecordTotal) return
        viewModelScope.launch {
            val userId = auth.currentUserId.value ?: return@launch
            mutableState.value = mutableState.value.copy(
                collectionRecordsLoading = reset,
                collectionRecordsLoadingMore = !reset,
                bills = if (reset) emptyList() else mutableState.value.bills,
                collectionRecordsError = null
            )
            runCatching {
                repository.bills(
                    direction = "INCOME",
                    source = "PERSONAL_COLLECTION_CODE",
                    page = nextPage
                )
            }.onSuccess { page ->
                val existing = if (reset) emptyList() else mutableState.value.bills
                if (auth.currentUserId.value == userId) mutableState.value = mutableState.value.copy(
                    bills = (existing + page.items).distinctBy { it.billId },
                    collectionRecordsLoading = false,
                    collectionRecordsLoadingMore = false,
                    collectionRecordPage = page.page,
                    collectionRecordTotal = page.total,
                    collectionRecordsError = null
                )
            }.onFailure { error ->
                if (auth.currentUserId.value == userId) mutableState.value = mutableState.value.copy(
                    collectionRecordsLoading = false,
                    collectionRecordsLoadingMore = false,
                    collectionRecordsError = friendlyMessage(error)
                )
            }
        }
    }

    fun observeCollectionReceipts() {
        if (collectionReceiptObserver?.isActive == true) return
        collectionReceiptObserver = viewModelScope.launch {
            val userId = auth.currentUserId.value ?: return@launch
            var reconnectAttempt = 0
            while (isActive) {
                runCatching {
                    repository.collectionReceiptEvents().collect { event ->
                        if (auth.currentUserId.value != userId) return@collect
                        if (event.source != "PERSONAL_COLLECTION_CODE"
                            || !receivedCollectionReceiptEvents.add(event.eventId)) return@collect
                        if (receivedCollectionReceiptEvents.size > 200) {
                            receivedCollectionReceiptEvents.remove(receivedCollectionReceiptEvents.first())
                        }
                        reconnectAttempt = 0
                        loadCollectionRecords()
                        mutableState.value = mutableState.value.copy(
                            message = "收款到账 ${event.amountCent / 100}.${(event.amountCent % 100).toString().padStart(2, '0')} 元"
                        )
                    }
                }
                reconnectAttempt = (reconnectAttempt + 1).coerceAtMost(4)
                delay(2_000L shl (reconnectAttempt - 1))
            }
        }
    }

    fun loadCollectionCode() {
        viewModelScope.launch {
            val userId = auth.currentUserId.value ?: return@launch
            mutableState.value = mutableState.value.copy(
                collectionCodeLoading = true,
                collectionCodeError = null
            )
            val profileRequest = async { runCatching { repository.profile() } }
            val codeResult = runCatching { repository.collectionCode() }
            profileRequest.await().onSuccess { profile ->
                if (auth.currentUserId.value == userId) {
                    mutableState.value = mutableState.value.copy(collectionRecipient = profile)
                }
            }
            codeResult.onSuccess { code ->
                if (auth.currentUserId.value == userId) mutableState.value = mutableState.value.copy(
                    collectionCode = code,
                    collectionCodeLoading = false,
                    collectionCodeError = null
                )
            }.onFailure { error ->
                if (auth.currentUserId.value == userId) mutableState.value = mutableState.value.copy(
                    collectionCodeLoading = false,
                    collectionCodeError = friendlyMessage(error)
                )
            }
        }
    }

    fun submitRealName(
        name: String,
        idNumber: String,
        jpeg: ByteArray
    ) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(submitting = true, message = null)
            val result = runCatching { repository.submitRealName(name, idNumber, jpeg) }
                .getOrElse {
                    fail(it)
                    return@launch
                }
            when (result.status) {
                "REJECTED" -> mutableState.value = mutableState.value.copy(
                    submitting = false,
                    message = realNameRejectionMessage(result.failureCode)
                )
                "VERIFIED" -> {
                    mutableState.value = mutableState.value.copy(
                        submitting = true,
                        realNameCompletion = RealNameCompletion.SynchronizingSession
                    )
                    synchronizeVerifiedRealName(result.verificationId)
                }
                else -> mutableState.value = mutableState.value.copy(
                    submitting = false,
                    message = "实名认证资料正在处理中，请稍后刷新查看结果"
                )
            }
        }
    }

    fun retryRealNameSessionSync() {
        val completion = mutableState.value.realNameCompletion as? RealNameCompletion.SessionSyncFailed
            ?: return
        mutableState.value = mutableState.value.copy(
            submitting = true,
            message = null,
            realNameCompletion = RealNameCompletion.SynchronizingSession
        )
        viewModelScope.launch { synchronizeVerifiedRealName(completion.verificationId) }
    }

    fun consumeRealNameCompletion() {
        mutableState.value = mutableState.value.copy(realNameCompletion = null)
    }

    fun setPaymentPassword(password: String, onDone: () -> Unit) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(submitting = true, message = null)
            val writeFailure = runCatching { repository.setPaymentPassword(password) }.exceptionOrNull()
            if (writeFailure != null && (writeFailure as? IdentityApiException)?.code != "PAYMENT_PASSWORD_ALREADY_SET") {
                fail(writeFailure)
                return@launch
            }
            runCatching {
                repository.refreshClaimsAfterPaymentPassword()
                loadFinancialSnapshot()
            }.onSuccess { snapshot ->
                applyFinancialSnapshot(snapshot, loading = false, submitting = false)
                onDone()
            }.onFailure(::fail)
        }
    }

    fun transfer(
        receiverId: String, amountCent: Long, remark: String?, password: String,
        source: String = "FORM",
        onDone: (TransferOrder) -> Unit
    ) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(submitting = true, message = null)
            runCatching { repository.transfer(receiverId, amountCent, remark, password, source) }
                .onSuccess {
                    mutableState.value = mutableState.value.copy(submitting = false)
                    onDone(it)
                    refresh()
                }
                .onFailure(::fail)
        }
    }

    fun resolveCode(
        value: String,
        onDone: (ScanResolution) -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching { repository.resolveCode(value) }
                .onSuccess(onDone)
                .onFailure { error ->
                    val message = friendlyMessage(error)
                    mutableState.value = mutableState.value.copy(message = message)
                    onFailure(message)
                }
        }
    }

    fun resolveTransferRecipient(mobile: String) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(
                recipientLookupLoading = true,
                recipientLookupResult = null,
                recipientLookupError = null,
                message = null
            )
            runCatching { repository.resolveTransferRecipient(mobile).toTransferRecipientUi() }
                .onSuccess { recipient ->
                    val friend = mutableState.value.transferFriends
                        .firstOrNull { it.receiverUserId == recipient.receiverUserId }
                    mutableState.value = mutableState.value.copy(
                        recipientLookupLoading = false,
                        recipientLookupResult = friend ?: recipient,
                        recipientLookupError = null
                    )
                }
                .onFailure { error ->
                    mutableState.value = mutableState.value.copy(
                        recipientLookupLoading = false,
                        recipientLookupResult = null,
                        recipientLookupError = friendlyMessage(error)
                    )
                }
        }
    }

    fun loadTransferFriends() {
        if (mutableState.value.transferFriendsLoading) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(
                transferFriendsLoading = true,
                transferFriendsError = null
            )
            runCatching { chatRepository.listTransferTargets() }
                .onSuccess { friends ->
                    mutableState.value = mutableState.value.copy(
                        transferFriends = friends.map { target ->
                            TransferRecipientUi(
                                receiverUserId = target.userId,
                                nickname = target.nickname,
                                display = target.name,
                                accountMasked = target.accountMasked ?: target.miniPayNo,
                                legalNameMasked = null,
                                avatarUrl = target.avatarUrl,
                                transferSource = TransferSource.FORM,
                                origin = TransferRecipientOrigin.CONTACT,
                                conversationId = target.conversationId
                            )
                        },
                        transferFriendsLoading = false,
                        transferFriendsError = null
                    )
                }
                .onFailure { error ->
                    mutableState.value = mutableState.value.copy(
                        transferFriendsLoading = false,
                        transferFriendsError = friendlyMessage(error)
                    )
                }
        }
    }

    fun clearRecipientLookup() {
        mutableState.value = mutableState.value.copy(
            recipientLookupLoading = false,
            recipientLookupResult = null,
            recipientLookupError = null
        )
    }

    fun bindCard(holder: String, number: String, code: String, onDone: () -> Unit) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(submitting = true, message = null)
            val card = runCatching { repository.bindCard(holder, number, code) }.getOrElse { error ->
                fail(error)
                return@launch
            }
            mutableState.value = mutableState.value.copy(submitting = false)
            // Do not return to the list before its post-bind refresh has settled.
            refreshCards(auth.currentUserId.value ?: return@launch, preferredCardId = card.cardId)
            onDone()
        }
    }

    fun loadCard(cardId: String) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(
                bankDataLoading = true, message = null, bankBalance = null
            )
            runCatching {
                repository.card(cardId) to repository.paymentLimits(cardId)
            }.onSuccess { (card, limits) ->
                mutableState.value = mutableState.value.copy(
                    selectedCard = card,
                    bankPaymentLimits = limits,
                    bankDataLoading = false
                )
            }.onFailure(::fail)
        }
    }

    fun queryBankBalance(cardId: String, password: String) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(
                submitting = true, message = null, bankBalance = null
            )
            runCatching { repository.bankBalance(cardId, password) }
                .onSuccess { balance ->
                    mutableState.value = mutableState.value.copy(
                        submitting = false, bankBalance = balance
                    )
                }
                .onFailure(::fail)
        }
    }

    fun loadBankTransactions(
        cardId: String,
        from: String,
        to: String,
        reset: Boolean = true
    ) {
        val nextPage = if (reset) 1 else mutableState.value.bankTransactionPage + 1
        if (!reset && mutableState.value.bankTransactions.size >= mutableState.value.bankTransactionTotal) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(
                bankDataLoading = reset,
                bankTransactionsLoadingMore = !reset,
                message = null,
                bankTransactions = if (reset) emptyList() else mutableState.value.bankTransactions
            )
            runCatching { repository.bankTransactions(cardId, from, to, nextPage) }
                .onSuccess { page ->
                    val current = if (reset) emptyList() else mutableState.value.bankTransactions
                    mutableState.value = mutableState.value.copy(
                        bankTransactions = current + page.items,
                        bankTransactionPage = page.page,
                        bankTransactionTotal = page.total,
                        bankDataLoading = false,
                        bankTransactionsLoadingMore = false
                    )
                }
                .onFailure(::fail)
        }
    }

    fun disableCard(cardId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(submitting = true, message = null)
            try {
                repository.disableCard(cardId)
                mutableState.value = mutableState.value.copy(
                    cards = mutableState.value.cards.filterNot { it.cardId == cardId },
                    selectedCard = mutableState.value.selectedCard?.takeUnless { it.cardId == cardId },
                    submitting = false
                )
                auth.currentUserId.value?.let { refreshCards(it) }
                onDone()
            } catch (error: Throwable) {
                fail(error)
            }
        }
    }

    fun recharge(cardId: String, amountCent: Long, password: String, onDone: () -> Unit) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(submitting = true, message = null)
            runCatching { repository.recharge(cardId, amountCent, password) }
                .onSuccess { order ->
                    val card = mutableState.value.cards.firstOrNull { it.cardId == cardId }
                    startPaymentResult(
                        order.toPaymentResultSnapshot(),
                        PaymentResultPresentation(
                            method = card?.let { "${it.bankName} ${it.maskedCardNo}" } ?: "银行卡"
                        )
                    )
                    mutableState.value = mutableState.value.copy(
                        submitting = false,
                        fundingResult = FundingResult("RECHARGE", cardId, amountCent, order.status, order.failureCode)
                    )
                    refresh()
                    onDone()
                }
                .onFailure(::fail)
        }
    }

    fun withdraw(
        cardId: String, amountCent: Long, password: String,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(submitting = true, message = null)
            runCatching { repository.withdraw(cardId, amountCent, password) }
                .onSuccess { order ->
                    val card = mutableState.value.cards.firstOrNull { it.cardId == cardId }
                    startPaymentResult(
                        order.toPaymentResultSnapshot(),
                        PaymentResultPresentation(
                            method = card?.let { "${it.bankName} ${it.maskedCardNo}" } ?: "银行卡"
                        )
                    )
                    mutableState.value = mutableState.value.copy(
                        submitting = false,
                        fundingResult = FundingResult("WITHDRAWAL", cardId, amountCent, order.status, order.failureCode)
                    )
                    refresh()
                    onDone()
                }
                .onFailure(::fail)
        }
    }

    fun clearMessage() { mutableState.value = mutableState.value.copy(message = null) }
    fun clearFundingResult() { mutableState.value = mutableState.value.copy(fundingResult = null) }

    fun observeTransferResult(
        order: TransferOrder,
        counterparty: String,
        onTerminal: (PaymentResultSnapshot) -> Unit = {}
    ) {
        startPaymentResult(
            order.toPaymentResultSnapshot(),
            PaymentResultPresentation(counterparty = counterparty, method = "账户余额"),
            onTerminal
        )
    }

    fun refreshPaymentResult() {
        val current = mutableState.value.paymentResult ?: return
        if (current.refreshing) return
        mutableState.value = mutableState.value.copy(
            paymentResult = current.copy(refreshing = true, refreshError = false)
        )
        viewModelScope.launch {
            runCatching {
                paymentStatusMonitor.refresh(current.snapshot.reference, paymentPresentation)
            }.onSuccess { latest ->
                val previous = mutableState.value.paymentResult?.snapshot
                mutableState.value = mutableState.value.copy(
                    paymentResult = PaymentResultUiState(
                        snapshot = latest,
                        polling = false,
                        timedOut = !latest.status.terminal,
                        refreshing = false,
                        refreshError = false
                    )
                )
                if (latest.status.terminal && previous?.status?.terminal != true) {
                    paymentTerminalAction?.invoke(latest)
                }
            }.onFailure {
                val latest = mutableState.value.paymentResult ?: return@onFailure
                mutableState.value = mutableState.value.copy(
                    paymentResult = latest.copy(refreshing = false, refreshError = true)
                )
            }
        }
    }

    fun setReceiptSpeechEnabled(enabled: Boolean) {
        voiceSettings.setReceiptSpeechEnabled(enabled)
        if (!enabled) speechOutput.stop(SpeechChannel.RECEIPT)
    }

    fun clearPaymentResult() {
        paymentMonitorJob?.cancel()
        paymentMonitorJob = null
        paymentTerminalAction = null
        paymentPresentation = PaymentResultPresentation()
        savedStateHandle.remove<String>(PAYMENT_OPERATION_KEY)
        savedStateHandle.remove<String>(PAYMENT_ORDER_ID_KEY)
        mutableState.value = mutableState.value.copy(paymentResult = null, fundingResult = null)
    }

    private suspend fun synchronizeVerifiedRealName(verificationId: String) {
        runCatching {
            repository.refreshClaimsAfterRealName()
            repository.capabilities()
        }.onSuccess { capabilities ->
            if (!capabilities.realNameVerified) {
                mutableState.value = mutableState.value.copy(
                    loading = false,
                    submitting = false,
                    realNameCompletion = RealNameCompletion.SessionSyncFailed(verificationId),
                    message = null
                )
                return@onSuccess
            }
            mutableState.value = mutableState.value.copy(
                loading = false,
                submitting = false,
                capabilities = capabilities,
                realNameCompletion = RealNameCompletion.Ready(verificationId)
            )
        }.onFailure {
            mutableState.value = mutableState.value.copy(
                loading = false,
                submitting = false,
                realNameCompletion = RealNameCompletion.SessionSyncFailed(verificationId),
                message = null
            )
        }
    }

    private suspend fun loadFinancialSnapshot(): FinancialSnapshot {
        val capabilities = repository.capabilities()
        val wallet = if (capabilities.realNameVerified) repository.wallet() else null
        val cards = if (capabilities.realNameVerified) repository.cards() else emptyList()
        return FinancialSnapshot(capabilities, wallet, cards)
    }

    private suspend fun refreshWalletAndCards(userId: String) = supervisorScope {
        mutableState.value = mutableState.value.copy(walletLoading = true, cardsLoading = true)
        val wallet = async { runCatching { repository.wallet() } }
        val cards = async { runCatching { repository.cards() } }
        // Profile information is presentation-only on funding screens. It must never block
        // financial reads or prevent a user from continuing a legitimate payment flow.
        val profile = async { runCatching { repository.profile() } }
        wallet.await().onSuccess { summary ->
            if (auth.currentUserId.value == userId) mutableState.value = mutableState.value.copy(
                wallet = summary, walletLoading = false, walletError = null
            )
        }.onFailure { error ->
            if (auth.currentUserId.value == userId) mutableState.value = mutableState.value.copy(
                walletLoading = false, walletError = friendlyMessage(error)
            )
        }
        cards.await().onSuccess { values ->
            if (auth.currentUserId.value == userId) mutableState.value = mutableState.value.copy(
                cards = values, cardsLoading = false, cardsError = null
            )
        }.onFailure { error ->
            if (auth.currentUserId.value == userId) mutableState.value = mutableState.value.copy(
                cardsLoading = false, cardsError = friendlyMessage(error)
            )
        }
        profile.await().onSuccess { profile ->
            if (auth.currentUserId.value == userId) {
                mutableState.value = mutableState.value.copy(currentUserProfile = profile)
            }
        }
    }

    private suspend fun refreshCards(userId: String, preferredCardId: String? = null) {
        mutableState.value = mutableState.value.copy(cardsLoading = true, cardsError = null)
        runCatching { repository.cards() }.onSuccess { values ->
            val selectedCardId = mutableState.value.selectedCard?.cardId
            if (auth.currentUserId.value == userId) mutableState.value = mutableState.value.copy(
                cards = values,
                selectedCard = preferredCardId?.let { id -> values.firstOrNull { it.cardId == id } }
                    ?: selectedCardId?.let { id -> values.firstOrNull { it.cardId == id } },
                cardsLoading = false,
                cardsError = null
            )
        }.onFailure { error ->
            if (auth.currentUserId.value == userId) mutableState.value = mutableState.value.copy(
                cardsLoading = false,
                cardsError = friendlyMessage(error)
            )
        }
    }

    private fun applyFinancialSnapshot(
        snapshot: FinancialSnapshot,
        loading: Boolean,
        submitting: Boolean = mutableState.value.submitting,
        realNameCompletion: RealNameCompletion? = mutableState.value.realNameCompletion
    ) {
        mutableState.value = mutableState.value.copy(
            loading = loading,
            submitting = submitting,
            capabilities = snapshot.capabilities,
            wallet = snapshot.wallet,
            cards = snapshot.cards,
            realNameCompletion = realNameCompletion
        )
    }

    private data class FinancialSnapshot(
        val capabilities: ConsumerCapabilities,
        val wallet: WalletSummary?,
        val cards: List<BankCard>
    )

    private fun launch(onDone: () -> Unit = {}, block: suspend () -> Any?) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(submitting = true, message = null)
            runCatching { block() }.onSuccess {
                mutableState.value = mutableState.value.copy(submitting = false)
                onDone()
                refresh()
            }.onFailure(::fail)
        }
    }

    private fun <T> launchResult(onDone: (T) -> Unit, block: suspend () -> T) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(submitting = true, message = null)
            runCatching { block() }.onSuccess {
                mutableState.value = mutableState.value.copy(submitting = false)
                onDone(it)
            }.onFailure(::fail)
        }
    }

    private fun fail(error: Throwable) {
        mutableState.value = mutableState.value.copy(
            loading = false,
            submitting = false,
            bankDataLoading = false,
            bankTransactionsLoadingMore = false,
            billsLoading = false,
            billsLoadingMore = false,
            billDetailLoading = false,
            billManagementSaving = false,
            message = friendlyMessage(error)
        )
    }

    fun createPersonalTransfer(
        receiverId: String,
        amountCent: Long,
        remark: String?,
        source: String,
        idempotencyKey: String,
        onDone: (TransferIntent) -> Unit
    ) = launchResult(onDone) {
        repository.createTransfer(receiverId, amountCent, remark, source, idempotencyKey)
    }

    fun confirmPersonalTransfer(
        intent: TransferIntent,
        password: String,
        confirmationKey: String,
        onDone: (TransferOrder) -> Unit
    ) = launchResult(onDone) {
        val authorization = repository.authorizeTransfer(intent, password)
        repository.confirmTransfer(intent.intentId, authorization.paymentAuthToken, confirmationKey)
    }

    fun refreshTransferOrder(transferId: String, onDone: (TransferOrder) -> Unit) =
        launchResult(onDone) { repository.transferOrder(transferId) }

    private fun startPaymentResult(
        initial: PaymentResultSnapshot,
        presentation: PaymentResultPresentation = PaymentResultPresentation(),
        onTerminal: (PaymentResultSnapshot) -> Unit = {}
    ) {
        paymentMonitorJob?.cancel()
        paymentPresentation = presentation
        paymentTerminalAction = onTerminal
        savedStateHandle[PAYMENT_OPERATION_KEY] = initial.reference.operation.name
        savedStateHandle[PAYMENT_ORDER_ID_KEY] = initial.reference.orderId
        val presented = initial.withPresentation(presentation)
        mutableState.value = mutableState.value.copy(
            paymentResult = PaymentResultUiState(
                snapshot = presented,
                polling = !presented.status.terminal
            )
        )
        if (presented.status.terminal) {
            onTerminal(presented)
            return
        }
        paymentMonitorJob = viewModelScope.launch {
            paymentStatusMonitor.observe(presented, presentation).collect { event ->
                when (event) {
                    is PaymentMonitorEvent.Updated -> {
                        val previous = mutableState.value.paymentResult?.snapshot
                        mutableState.value = mutableState.value.copy(
                            paymentResult = PaymentResultUiState(
                                snapshot = event.snapshot,
                                polling = !event.snapshot.status.terminal,
                                refreshError = false
                            )
                        )
                        if (event.snapshot.status.terminal && previous?.status?.terminal != true) {
                            paymentTerminalAction?.invoke(event.snapshot)
                        }
                    }
                    PaymentMonitorEvent.RefreshFailed -> {
                        val current = mutableState.value.paymentResult ?: return@collect
                        mutableState.value = mutableState.value.copy(
                            paymentResult = current.copy(refreshError = true)
                        )
                    }
                    PaymentMonitorEvent.TimedOut -> {
                        val current = mutableState.value.paymentResult ?: return@collect
                        mutableState.value = mutableState.value.copy(
                            paymentResult = current.copy(
                                polling = false,
                                timedOut = true,
                                refreshError = false
                            )
                        )
                    }
                }
            }
        }
    }

    private fun resumeSavedPaymentResult() {
        if (mutableState.value.paymentResult != null || paymentMonitorJob?.isActive == true) return
        val operation = savedStateHandle.get<String>(PAYMENT_OPERATION_KEY)
            ?.let { runCatching { PaymentOperation.valueOf(it) }.getOrNull() }
            ?: return
        val orderId = savedStateHandle.get<String>(PAYMENT_ORDER_ID_KEY) ?: return
        viewModelScope.launch {
            runCatching {
                paymentStatusMonitor.refresh(PaymentResultReference(operation, orderId))
            }.onSuccess { startPaymentResult(it) }
                .onFailure {
                    savedStateHandle.remove<String>(PAYMENT_OPERATION_KEY)
                    savedStateHandle.remove<String>(PAYMENT_ORDER_ID_KEY)
                }
        }
    }

    fun payMerchantCollection(
        resolution: ScanResolution,
        amountCent: Long,
        password: String,
        onDone: (MerchantPaymentOrder) -> Unit
    ) = launchResult(onDone) {
        repository.payMerchantCollection(resolution, amountCent, password)
    }

    private fun friendlyMessage(error: Throwable): String {
        val apiError = error as? IdentityApiException
        val code = apiError?.code
        if (apiError?.status == 401 || apiError?.status == 403) return "登录状态已失效，请重新登录"
        if (apiError?.status != null && apiError.status >= 500) return "服务暂时不可用，请稍后重试"
        return when (code) {
                "REAL_NAME_VERIFICATION_REQUIRED" -> "请先完成实名认证"
                "REAL_NAME_VERIFICATION_PROCESSING" -> "实名认证正在处理中"
                "PAYMENT_PASSWORD_REQUIRED" -> "请先设置支付密码"
                "PAYMENT_PASSWORD_NOT_SET" -> "请先设置支付密码"
                "ANNUAL_OUTFLOW_LIMIT_EXCEEDED" -> "本年度余额支付额度已用完，请绑定银行卡解除限制"
                "ACTIVE_BANK_CARD_REQUIRED" -> "请先绑定一张有效银行卡"
                "PAYMENT_AUTHORIZATION_INVALID" -> "支付授权已失效，请重新输入支付密码"
                "PAYMENT_AUTHORIZATION_EXPIRED" -> "支付授权已过期，请重新输入支付密码"
                "PAYMENT_AUTHORIZATION_CONSUMED" -> "该支付授权已使用，请重新输入支付密码"
                "IDENTITY_CLIENT_CREDENTIALS_REJECTED" -> "支付服务配置异常，请联系管理员后重试"
                "IDENTITY_AUTHORIZATION_ACCESS_DENIED" -> "支付授权服务权限异常，请稍后重试"
                "IDENTITY_AUTHORIZATION_SERVICE_UNAVAILABLE" -> "支付授权服务暂时不可用，请稍后重试"
                "PAYMENT_PASSWORD_INVALID" -> "支付密码格式不正确，请输入 6 位数字"
                "PAYMENT_PASSWORD_ALREADY_SET" -> "支付密码已设置，正在更新登录状态"
                "PAYMENT_PASSWORD_LOCKED" -> "支付密码已暂时锁定，请稍后再试"
                "BANK_CARD_NOT_FOUND" -> "银行卡不存在或已解除绑定"
                "BANK_CARD_INACTIVE" -> "该银行卡当前不可用"
                "INVALID_BANK_CARD" -> "请输入有效银行卡号"
                "INVALID_CARD_HOLDER" -> "请输入有效持卡人姓名"
                "BANK_VERIFICATION_FAILED" -> "沙箱验证码不正确，请输入 123456"
                "BANK_INSUFFICIENT_BALANCE" -> "银行卡余额不足"
                "BANK_SINGLE_LIMIT_EXCEEDED" -> "超过银行卡单笔支付限额"
                "BANK_DAILY_LIMIT_EXCEEDED" -> "超过银行卡当日支付限额"
                "INVALID_COLLECTION_CODE", "INVALID_OR_EXPIRED_COLLECTION_CODE",
                "UNSUPPORTED_COLLECTION_CODE", "COLLECTION_CODE_INVALID",
                "COLLECTION_CODE_EXPIRED" -> "收款码无效、已过期或不属于当前服务环境"
                "COLLECTION_RECIPIENT_UNAVAILABLE" -> "收款人信息暂不可用，请稍后重试"
                "MOBILE_INVALID" -> "请输入正确的 11 位手机号"
                "TRANSFER_RECIPIENT_NOT_FOUND" -> "未找到可转账的 MiniPay 账户"
                "SELF_TRANSFER_NOT_ALLOWED" -> "不能向自己的账户转账"
                "INVALID_TRANSFER_SOURCE" -> "转账方式无效，请重新进入转账页面"
                "WALLET_ACCOUNT_RESOLUTION_FAILED" -> "钱包账户暂时无法确认，请稍后重试"
                "WALLET_NOT_ACTIVE" -> "付款账户当前不可用"
                "RECIPIENT_LOOKUP_RATE_LIMITED" -> "查询过于频繁，请稍后重试"
                "RECIPIENT_LOOKUP_UNAVAILABLE" -> "收款账户查询暂不可用，请稍后重试"
                "NETWORK_UNAVAILABLE" -> "网络连接失败，请稍后重试"
                else -> {
                    Log.w(
                        "FinanceViewModel",
                        "finance request failed code=${code ?: "UNCLASSIFIED"} " +
                            "status=${apiError?.status ?: "none"} " +
                            "requestId=${apiError?.requestId ?: "none"}"
                    )
                    "操作失败，请稍后重试"
                }
        }
    }

    private fun realNameRejectionMessage(code: String?): String = when (code) {
        "SANDBOX_IDENTITY_REJECTED" -> "沙箱认证未通过，请更换有效测试身份证号后重试"
        "REAL_NAME_MISMATCH" -> "姓名、身份证或人脸信息不一致，请核对后重试"
        else -> "实名认证未通过，请核对信息并重新拍摄"
    }

    private companion object {
        const val PAYMENT_OPERATION_KEY = "finance_payment_operation"
        const val PAYMENT_ORDER_ID_KEY = "finance_payment_order_id"
    }
}
