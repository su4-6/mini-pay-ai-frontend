package com.minipay.mobile.ai

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minipay.mobile.auth.IdentityApiException
import com.minipay.mobile.finance.FinanceRepository
import com.minipay.mobile.finance.PaymentMonitorEvent
import com.minipay.mobile.finance.PaymentOperation
import com.minipay.mobile.finance.PaymentResultPresentation
import com.minipay.mobile.finance.PaymentResultReference
import com.minipay.mobile.finance.PaymentResultSnapshot
import com.minipay.mobile.finance.PaymentResultStatus
import com.minipay.mobile.finance.PaymentResultUiState
import com.minipay.mobile.finance.PaymentStatusMonitor
import com.minipay.mobile.finance.TransferIntent
import com.minipay.mobile.finance.toPaymentResultSnapshot
import com.minipay.mobile.voice.SpeechChannel
import com.minipay.mobile.voice.SpeechOutput
import com.minipay.mobile.voice.SpeechOutputState
import com.minipay.mobile.voice.VoiceInputController
import com.minipay.mobile.voice.VoiceInputState
import com.minipay.mobile.voice.VoiceSettings
import com.minipay.mobile.voice.mergeVoiceTranscript
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class AiHomeConversation(val id: String, val title: String, val version: Long)
enum class AiHomeMessageRole { USER, ASSISTANT, SYSTEM }
enum class AiConversationRunState { RUNNING, COMPLETED, FAILED }
enum class AiInputMode { KEYBOARD, VOICE }

internal fun aiTransferConfirmationError(error: Throwable): String =
    when ((error as? IdentityApiException)?.code) {
        "PAYMENT_PASSWORD_NOT_SET" -> "尚未设置支付密码，请先打开“转账”完成设置后再付款"
        "PAYMENT_PASSWORD_INVALID" -> "支付密码错误，转账未提交，请重新输入"
        "PAYMENT_PASSWORD_LOCKED" -> "支付密码已锁定，转账未提交，请稍后再试"
        "PAYMENT_PASSWORD_DISABLED" -> "支付密码当前不可用，转账未提交，请前往账户安全检查"
        "REAL_NAME_VERIFICATION_REQUIRED" -> "请先完成实名认证，转账未提交"
        else -> "暂时无法确认转账结果，请先查看账单，切勿重复付款"
    }

data class AiHomeMessage(
    val id: String,
    val runId: String? = null,
    val role: AiHomeMessageRole,
    val text: String,
    val cardType: String? = null,
    val cardPayload: JsonObject? = null,
    val streaming: Boolean = false,
    val createdAt: Instant? = null
)

internal fun AiHomeMessage.isNativePaymentResultMessage(): Boolean =
    cardType == "payment.transfer-order"

internal fun isTransferConfirmationAllowed(runStatus: String): Boolean =
    runStatus == "WAITING_CONFIRMATION"

data class AiPendingCheckout(val runId: String, val merchantId: String, val cartVersion: Long)
data class AiPaymentPrompt(
    val messageId: String,
    val type: Type,
    val title: String,
    val counterparty: String,
    val amountCent: Long
) {
    enum class Type { TRANSFER, FOOD }
}

data class AiHomeUiState(
    val loading: Boolean = true,
    val conversations: List<AiHomeConversation> = emptyList(),
    val selectedConversationId: String? = null,
    val newConversationSelected: Boolean = false,
    val draftText: String = "",
    val inputMode: AiInputMode = AiInputMode.KEYBOARD,
    val voiceInputState: VoiceInputState = VoiceInputState.Idle,
    val aiSpeechEnabled: Boolean = true,
    val speechOutputState: SpeechOutputState = SpeechOutputState.Ready,
    val voiceMessage: String? = null,
    val messages: List<AiHomeMessage> = emptyList(),
    val streaming: Boolean = false,
    val errorMessage: String? = null,
    val canRetry: Boolean = false,
    val pendingCheckout: AiPendingCheckout? = null,
    val paymentPrompt: AiPaymentPrompt? = null,
    val confirmationInFlight: Boolean = false,
    val paymentResult: PaymentResultUiState? = null,
    val completedPaymentRunIds: Set<String> = emptySet(),
    val checkingPaymentRunIds: Set<String> = emptySet(),
    val memoryVisible: Boolean = false,
    val memoryLoading: Boolean = false,
    val memorySettings: MemorySettingDto? = null,
    val memoryItems: List<MemoryItemDto> = emptyList()
)

internal class AiConversationDraftStore {
    private val values = mutableMapOf<String, String>()

    fun read(key: String): String = values[key].orEmpty()

    fun write(key: String, value: String) {
        if (value.isEmpty()) values.remove(key) else values[key] = value
    }

    fun remove(key: String) {
        values.remove(key)
    }
}

@HiltViewModel
class MilingAiViewModel @Inject constructor(
    private val repository: AiConversationRepository,
    private val commerce: CommerceApi,
    private val finance: FinanceRepository,
    private val json: Json,
    private val paymentStatusMonitor: PaymentStatusMonitor,
    private val voiceInput: VoiceInputController,
    private val speechOutput: SpeechOutput,
    private val voiceSettings: VoiceSettings,
    originalPromptStore: SecureAiOriginalPromptStore,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val mutableState = MutableStateFlow(AiHomeUiState())
    val state: StateFlow<AiHomeUiState> = mutableState.asStateFlow()
    private var selectedConversation: AiConversationResponse? = null
    private var pendingSubmission: PendingSubmission? = null
    private var activeRunId: String? = null
    private var lastEventId: String? = null
    private var streamJob: Job? = null
    private var refreshJob: Job? = null
    private var paymentMonitorJob: Job? = null
    private var paymentPresentation = PaymentResultPresentation()
    private val summarizedPaymentOrderIds = mutableSetOf<String>()
    private val drafts = AiConversationDraftStore()
    private val transientMessageDisplay = TransientAiMessageDisplay(
        persistentStore = originalPromptStore
    )
    private val completedSpeech = mutableMapOf<String, CompletedSpeech>()
    private val aiSpeechPolicy = AiSpeechPolicy()
    private var newConversationSelected = false
    private var voiceScreenVisible = false

    private data class CompletedSpeech(val messageId: String?, val cardType: String?)

    init {
        viewModelScope.launch {
            voiceInput.state.collect { inputState ->
                mutableState.update { it.copy(voiceInputState = inputState) }
            }
        }
        viewModelScope.launch {
            voiceInput.finalTranscripts.collect { transcript ->
                val merged = mergeVoiceTranscript(mutableState.value.draftText, transcript)
                if (merged == null) {
                    mutableState.update { it.copy(voiceMessage = "语音识别内容过长，请缩短后重试") }
                } else {
                    updateDraft(merged)
                    mutableState.update { it.copy(inputMode = AiInputMode.KEYBOARD) }
                }
            }
        }
        viewModelScope.launch {
            voiceSettings.aiSpeechEnabled.collect { enabled ->
                mutableState.update { it.copy(aiSpeechEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            speechOutput.state.collect { outputState ->
                mutableState.update { it.copy(speechOutputState = outputState) }
                if (voiceScreenVisible && outputState is SpeechOutputState.Unavailable) {
                    mutableState.update { it.copy(voiceMessage = outputState.message) }
                }
            }
        }
        viewModelScope.launch {
            speechOutput.errors.collect { message ->
                if (voiceScreenVisible) mutableState.update { it.copy(voiceMessage = message) }
            }
        }
        refreshInitialState()
    }

    fun refresh() {
        val currentState = mutableState.value
        if (refreshJob?.isActive == true || currentState.loading || currentState.streaming ||
            currentState.confirmationInFlight
        ) return
        val selectedId = selectedConversation?.id
        val keepNewConversation = newConversationSelected
        refreshJob = viewModelScope.launch {
            runCatching {
                val conversations = repository.conversations()
                val selected = if (keepNewConversation) null else {
                    conversations.firstOrNull { it.id == selectedId } ?: conversations.firstOrNull()
                }
                Triple(conversations, selected, selected?.let { repository.messages(it.id) }.orEmpty())
            }.onSuccess { (conversations, selected, messages) ->
                reconcileTransferConfirmationCards(messages)
                selectedConversation = selected
                newConversationSelected = keepNewConversation || selected == null
                mutableState.update {
                    it.copy(
                        loading = false,
                        conversations = conversations.map { it.toSummary() },
                        selectedConversationId = selected?.id,
                        newConversationSelected = newConversationSelected,
                        draftText = drafts.read(currentDraftKey()),
                        messages = messages.map { it.toDisplayUi() },
                        errorMessage = null,
                        canRetry = false
                    )
                }
            }.onFailure(::showRecoverableError)
        }
    }

    fun startNewConversation() {
        if (rejectWhileRunning()) return
        selectedConversation = null
        newConversationSelected = true
        pendingSubmission = null
        mutableState.update {
            it.copy(
                loading = false,
                selectedConversationId = null,
                newConversationSelected = true,
                draftText = drafts.read(NEW_CONVERSATION_DRAFT),
                messages = emptyList(),
                errorMessage = null,
                canRetry = false
            )
        }
    }

    fun selectConversation(conversationId: String) {
        if (rejectWhileRunning() || conversationId == selectedConversation?.id) return
        viewModelScope.launch {
            mutableState.update { it.copy(loading = true, errorMessage = null, canRetry = false) }
            runCatching {
                val conversation = repository.conversations().firstOrNull { it.id == conversationId }
                    ?: throw AiAgentApiException("AGENT_CONVERSATION_NOT_FOUND", "该会话已不存在")
                conversation to repository.messages(conversation.id)
            }.onSuccess { (conversation, messages) ->
                reconcileTransferConfirmationCards(messages)
                selectedConversation = conversation
                newConversationSelected = false
                mutableState.update {
                    it.copy(
                        loading = false,
                        selectedConversationId = conversation.id,
                        newConversationSelected = false,
                        draftText = drafts.read(conversation.id),
                        messages = messages.map { message -> message.toDisplayUi() },
                        conversations = mergeConversations(it.conversations, listOf(conversation))
                    )
                }
            }.onFailure(::showRecoverableError)
        }
    }

    fun renameConversation(conversation: AiHomeConversation, title: String) {
        val normalized = title.trim()
        if (normalized.isEmpty() || normalized.length > 128 || rejectWhileRunning()) return
        viewModelScope.launch {
            runCatching { repository.renameConversation(conversation.id, normalized, conversation.version) }
                .onSuccess { renamed ->
                    if (selectedConversation?.id == renamed.id) selectedConversation = renamed
                    mutableState.update {
                        it.copy(conversations = mergeConversations(it.conversations, listOf(renamed)))
                    }
                }.onFailure(::showRecoverableError)
        }
    }

    fun deleteConversation(conversation: AiHomeConversation) {
        if (rejectWhileRunning()) return
        viewModelScope.launch {
            runCatching { repository.deleteConversation(conversation.id) }
                .onSuccess {
                    drafts.remove(conversation.id)
                    val remaining = mutableState.value.conversations.filterNot { it.id == conversation.id }
                    if (selectedConversation?.id != conversation.id) {
                        mutableState.update { state -> state.copy(conversations = remaining) }
                        return@onSuccess
                    }
                    val next = remaining.firstOrNull()
                    val nextDetails = next?.let {
                        repository.conversations().firstOrNull { value -> value.id == it.id }
                    }
                    val messages = nextDetails?.let { repository.messages(it.id) }.orEmpty()
                    reconcileTransferConfirmationCards(messages)
                    selectedConversation = nextDetails
                    newConversationSelected = nextDetails == null
                    mutableState.update { state ->
                        state.copy(
                            conversations = remaining,
                            selectedConversationId = nextDetails?.id,
                            newConversationSelected = newConversationSelected,
                            draftText = drafts.read(currentDraftKey()),
                            messages = messages.map { it.toDisplayUi() }
                        )
                    }
                }.onFailure(::showRecoverableError)
        }
    }

    fun openMemory() {
        mutableState.update { it.copy(memoryVisible = true, memoryLoading = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { repository.memorySettings() to repository.memoryItems() }
                .onSuccess { (settings, items) ->
                    mutableState.update {
                        it.copy(memoryLoading = false, memorySettings = settings, memoryItems = items)
                    }
                }.onFailure {
                    mutableState.update { state -> state.copy(memoryVisible = false, memoryLoading = false) }
                    showRecoverableError(it)
                }
        }
    }

    fun closeMemory() {
        if (!mutableState.value.memoryLoading) mutableState.update { it.copy(memoryVisible = false) }
    }

    fun updateMemorySettings(request: UpdateMemorySettingsRequest) {
        mutableState.update { it.copy(memoryLoading = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { repository.updateMemorySettings(request) }
                .onSuccess { settings ->
                    mutableState.update { it.copy(memoryLoading = false, memorySettings = settings) }
                }.onFailure { showMemoryError(it) }
        }
    }

    fun updateMemoryItem(item: MemoryItemDto, displayValue: String) {
        val normalized = displayValue.trim()
        if (normalized.isEmpty() || normalized.length > 256) return
        mutableState.update { it.copy(memoryLoading = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { repository.updateMemoryItem(item, normalized) }
                .onSuccess { updated ->
                    mutableState.update { state ->
                        state.copy(
                            memoryLoading = false,
                            memoryItems = state.memoryItems.map { if (it.id == updated.id) updated else it }
                        )
                    }
                }.onFailure { showMemoryError(it) }
        }
    }

    fun deleteMemoryItem(item: MemoryItemDto) {
        mutableState.update { it.copy(memoryLoading = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { repository.deleteMemoryItem(item) }
                .onSuccess {
                    mutableState.update { state ->
                        state.copy(
                            memoryLoading = false,
                            memoryItems = state.memoryItems.filterNot { it.id == item.id }
                        )
                    }
                }.onFailure { showMemoryError(it) }
        }
    }

    fun submit(prompt: String) {
        val normalized = prompt.trim()
        if (normalized.isEmpty() || mutableState.value.streaming) return
        if (normalized.length > 1000) {
            mutableState.update { it.copy(errorMessage = "单条消息不能超过 1000 个字符") }
            return
        }
        viewModelScope.launch {
            val originalDraftKey = currentDraftKey()
            val originalDraft = drafts.read(originalDraftKey)
            val submittedCurrentDraft = originalDraft.trim() == normalized
            val conversation = selectedConversation ?: runCatching { repository.createConversation() }
                .getOrElse { showRecoverableError(it); return@launch }
                .also { created ->
                    selectedConversation = created
                    newConversationSelected = false
                    drafts.remove(NEW_CONVERSATION_DRAFT)
                    if (!submittedCurrentDraft && originalDraft.isNotEmpty()) drafts.write(created.id, originalDraft)
                    mutableState.update {
                        it.copy(
                            conversations = listOf(created.toSummary()) + it.conversations,
                            selectedConversationId = created.id,
                            newConversationSelected = false,
                            draftText = drafts.read(created.id)
                        )
                    }
                }
            if (submittedCurrentDraft) {
                drafts.remove(conversation.id)
                drafts.remove(originalDraftKey)
                mutableState.update { it.copy(draftText = "") }
            }
            val pending = PendingSubmission(
                conversation.id,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                normalized,
                conversation.version
            )
            pendingSubmission = pending
            mutableState.update {
                it.copy(
                    messages = it.messages + AiHomeMessage(
                        pending.clientMessageId,
                        role = AiHomeMessageRole.USER,
                        text = normalized,
                        createdAt = Instant.now()
                    ),
                    streaming = true,
                    errorMessage = null,
                    canRetry = false
                )
            }
            submitPending(pending)
        }
    }

    fun continueAction(message: AiHomeMessage, request: AgentActionRequest) {
        val runId = message.runId ?: return
        continueAction(runId, request)
    }

    fun prepareCheckout(message: AiHomeMessage) {
        val payload = message.cardPayload ?: return
        val merchantId = payload.string("merchantId") ?: return
        val version = payload.long("version") ?: return
        val runId = message.runId ?: return
        viewModelScope.launch {
            mutableState.update { it.copy(confirmationInFlight = true, errorMessage = null) }
            runCatching { commerce.addresses() }
                .onSuccess { addresses ->
                    val selected = addresses.firstOrNull { it.defaultAddress } ?: addresses.firstOrNull()
                    if (selected == null) {
                        mutableState.update {
                            it.copy(
                                pendingCheckout = AiPendingCheckout(runId, merchantId, version),
                                confirmationInFlight = false
                            )
                        }
                    } else {
                        mutableState.update { it.copy(confirmationInFlight = false) }
                        continueAction(runId, AgentActionRequest(
                            action = "PREPARE_CHECKOUT",
                            merchantId = merchantId,
                            expectedCartVersion = version,
                            addressId = selected.id
                        ))
                    }
                }.onFailure { showOperationError("读取配送地址失败") }
        }
    }

    fun saveAddress(request: CreateDeliveryAddressRequest) {
        val pending = mutableState.value.pendingCheckout ?: return
        viewModelScope.launch {
            mutableState.update { it.copy(confirmationInFlight = true, errorMessage = null) }
            runCatching { commerce.createAddress(request) }
                .onSuccess { address ->
                    mutableState.update {
                        it.copy(pendingCheckout = null, confirmationInFlight = false)
                    }
                    continueAction(pending.runId, AgentActionRequest(
                        action = "PREPARE_CHECKOUT",
                        merchantId = pending.merchantId,
                        expectedCartVersion = pending.cartVersion,
                        addressId = address.id
                    ))
                }.onFailure { showOperationError("保存配送地址失败") }
        }
    }

    fun dismissAddressForm() {
        mutableState.update { it.copy(pendingCheckout = null) }
    }

    fun requestPayment(message: AiHomeMessage) {
        val payload = message.cardPayload ?: return
        if (message.cardType == "payment.transfer-intent") {
            val runId = message.runId ?: return
            if (runId in mutableState.value.completedPaymentRunIds ||
                runId in mutableState.value.checkingPaymentRunIds
            ) return
            viewModelScope.launch {
                mutableState.update { it.copy(confirmationInFlight = true, errorMessage = null) }
                runCatching { repository.getRun(runId) }
                    .onSuccess { run ->
                        if (isTransferConfirmationAllowed(run.status)) {
                            mutableState.update {
                                it.copy(
                                    confirmationInFlight = false,
                                    paymentPrompt = message.toPaymentPrompt(payload)
                                )
                            }
                        } else {
                            mutableState.update {
                                it.copy(
                                    confirmationInFlight = false,
                                    paymentPrompt = null,
                                    completedPaymentRunIds = it.completedPaymentRunIds + runId
                                )
                            }
                        }
                    }
                    .onFailure {
                        mutableState.update { it.copy(confirmationInFlight = false) }
                        showOperationError("暂时无法确认该转账是否仍可支付，请稍后重试")
                    }
            }
            return
        }
        val prompt = when (message.cardType) {
            "commerce.checkout" -> AiPaymentPrompt(
                message.id,
                AiPaymentPrompt.Type.FOOD,
                "确认下单并付款",
                "沙箱外卖订单",
                payload.long("payableAmountCent") ?: return
            )
            else -> return
        }
        mutableState.update { it.copy(paymentPrompt = prompt, errorMessage = null) }
    }

    private fun AiHomeMessage.toPaymentPrompt(payload: JsonObject): AiPaymentPrompt =
        AiPaymentPrompt(
            id,
            AiPaymentPrompt.Type.TRANSFER,
            "确认转账",
            payload["recipient"]?.jsonObject?.string("nickname") ?: "收款人",
            payload.long("amountCent") ?: 0
        )

    fun dismissPayment() {
        if (!mutableState.value.confirmationInFlight) mutableState.update { it.copy(paymentPrompt = null) }
    }

    fun confirmPayment(password: String) {
        val prompt = mutableState.value.paymentPrompt ?: return
        val message = mutableState.value.messages.firstOrNull { it.id == prompt.messageId } ?: return
        if (!password.matches(Regex("\\d{6}"))) {
            mutableState.update { it.copy(errorMessage = "请输入 6 位支付密码") }
            return
        }
        viewModelScope.launch {
            mutableState.update { it.copy(confirmationInFlight = true, errorMessage = null) }
            val result = runCatching {
                when (prompt.type) {
                    AiPaymentPrompt.Type.TRANSFER -> confirmTransfer(message, password)
                    AiPaymentPrompt.Type.FOOD -> confirmFood(message, password)
                }
            }
            result.onSuccess { snapshot ->
                mutableState.update {
                    it.copy(
                        paymentPrompt = null,
                        confirmationInFlight = false,
                        completedPaymentRunIds = if (prompt.type == AiPaymentPrompt.Type.TRANSFER) {
                            it.completedPaymentRunIds + listOfNotNull(message.runId)
                        } else {
                            it.completedPaymentRunIds
                        }
                    )
                }
                startPaymentResult(
                    snapshot,
                    PaymentResultPresentation(
                        counterparty = prompt.counterparty,
                        method = snapshot.method ?: "账户余额"
                    )
                )
            }.onFailure { error ->
                showOperationError(
                    if (prompt.type == AiPaymentPrompt.Type.TRANSFER) {
                        aiTransferConfirmationError(error)
                    } else {
                        "支付未完成，请核对密码或余额后重试"
                    }
                )
            }
        }
    }

    fun updateDraft(value: String) {
        val key = currentDraftKey()
        drafts.write(key, value)
        mutableState.update { it.copy(draftText = value) }
    }

    fun setInputMode(mode: AiInputMode) {
        if (mutableState.value.inputMode == mode) return
        voiceInput.cancel()
        mutableState.update { it.copy(inputMode = mode) }
    }

    fun startVoiceInput() {
        if (mutableState.value.inputMode != AiInputMode.VOICE ||
            mutableState.value.voiceInputState == VoiceInputState.Processing
        ) return
        speechOutput.stop(SpeechChannel.AI)
        voiceInput.start()
    }

    fun finishVoiceInput() {
        if (mutableState.value.voiceInputState is VoiceInputState.Listening) voiceInput.stop()
    }

    fun cancelVoiceInput() {
        voiceInput.cancel()
    }

    fun voicePermissionDenied() {
        voiceInput.cancel()
        mutableState.update {
            it.copy(inputMode = AiInputMode.KEYBOARD, voiceMessage = "需要麦克风权限才能使用语音输入")
        }
    }

    fun dismissVoiceMessage() {
        mutableState.update { it.copy(voiceMessage = null) }
        if (mutableState.value.voiceInputState is VoiceInputState.Error) voiceInput.cancel()
    }

    fun setAiSpeechEnabled(enabled: Boolean) {
        voiceSettings.setAiSpeechEnabled(enabled)
        if (!enabled) speechOutput.stop(SpeechChannel.AI)
    }

    fun setVoiceScreenVisible(visible: Boolean) {
        voiceScreenVisible = visible
        if (visible && speechOutput.state.value is SpeechOutputState.Unavailable) {
            val unavailable = speechOutput.state.value as SpeechOutputState.Unavailable
            mutableState.update { it.copy(voiceMessage = unavailable.message) }
        }
        if (!visible) {
            voiceInput.cancel()
            speechOutput.stop(SpeechChannel.AI)
            mutableState.update { it.copy(inputMode = AiInputMode.KEYBOARD) }
        }
    }

    fun refreshPaymentResult() {
        val current = mutableState.value.paymentResult ?: return
        if (current.refreshing) return
        mutableState.update {
            it.copy(paymentResult = current.copy(refreshing = true, refreshError = false))
        }
        viewModelScope.launch {
            runCatching {
                paymentStatusMonitor.refresh(current.snapshot.reference, paymentPresentation)
            }.onSuccess { latest ->
                mutableState.update {
                    it.copy(
                        paymentResult = PaymentResultUiState(
                            snapshot = latest,
                            timedOut = !latest.status.terminal
                        )
                    )
                }
                if (latest.status.terminal) appendPaymentSummary(latest)
            }.onFailure {
                mutableState.update { state ->
                    state.copy(
                        paymentResult = state.paymentResult?.copy(
                            refreshing = false,
                            refreshError = true
                        )
                    )
                }
            }
        }
    }

    fun completePaymentResult() {
        mutableState.value.paymentResult?.snapshot?.let(::appendPaymentSummary)
        paymentMonitorJob?.cancel()
        paymentMonitorJob = null
        paymentPresentation = PaymentResultPresentation()
        savedStateHandle.remove<String>(PAYMENT_OPERATION_KEY)
        savedStateHandle.remove<String>(PAYMENT_ORDER_ID_KEY)
        mutableState.update { it.copy(paymentResult = null) }
    }

    fun cancelOrder(message: AiHomeMessage) {
        val orderId = message.cardPayload?.string("orderId")
            ?: message.cardPayload?.string("id") ?: return
        viewModelScope.launch {
            mutableState.update { it.copy(confirmationInFlight = true, errorMessage = null) }
            runCatching { commerce.cancelOrder(orderId) }
                .onSuccess { order ->
                    mutableState.update {
                        it.copy(
                            confirmationInFlight = false,
                        messages = it.messages + AiHomeMessage(
                            UUID.randomUUID().toString(), role = AiHomeMessageRole.SYSTEM,
                            text = if (order.refundStatus == "SUCCEEDED") "退款成功" else "取消申请已提交",
                            createdAt = Instant.now()
                        )
                        )
                    }
                }.onFailure { showOperationError("取消或退款失败") }
        }
    }

    fun retry() {
        if (mutableState.value.streaming) return
        mutableState.update { it.copy(streaming = true, errorMessage = null, canRetry = false) }
        activeRunId?.let { observeRun(it); return }
        val pending = pendingSubmission ?: run { refreshInitialState(); return }
        viewModelScope.launch { submitPending(pending) }
    }

    fun dismissError() = mutableState.update { it.copy(errorMessage = null, canRetry = false) }

    private fun continueAction(runId: String, request: AgentActionRequest) {
        if (mutableState.value.streaming) return
        viewModelScope.launch {
            mutableState.update { it.copy(streaming = true, errorMessage = null, canRetry = false) }
            runCatching { repository.continueAction(runId, request) }
                .onSuccess {
                    activeRunId = runId
                    lastEventId = null
                    observeRun(runId)
                }.onFailure {
                    mutableState.update { state -> state.copy(streaming = false) }
                    showRecoverableError(it)
                }
        }
    }

    private suspend fun confirmTransfer(message: AiHomeMessage, password: String): PaymentResultSnapshot {
        val intentJson = message.cardPayload?.get("intent")?.jsonObject
            ?: throw IllegalStateException("Missing transfer intent")
        val intent = json.decodeFromJsonElement(TransferIntent.serializer(), intentJson)
        val authorization = finance.authorizeTransfer(intent, password)
        val order = finance.confirmTransfer(
            intent.intentId,
            authorization.paymentAuthToken,
            "ai-transfer-confirm:${message.runId ?: message.id}"
        )
        completeNativeTransferHandoff(message, order.transferId)
        return order.toPaymentResultSnapshot()
    }

    private fun completeNativeTransferHandoff(message: AiHomeMessage, transferId: String) {
        message.runId?.let { runId ->
            viewModelScope.launch {
                runCatching {
                    repository.continueAction(
                        runId,
                        AgentActionRequest(
                            action = "COMPLETE_NATIVE_TRANSFER",
                            transferId = transferId
                        )
                    )
                }
            }
        }
    }

    private suspend fun confirmFood(message: AiHomeMessage, password: String): PaymentResultSnapshot {
        val quoteId = message.cardPayload?.string("id")
            ?: throw IllegalStateException("Missing checkout quote")
        val order = commerce.createOrder(quoteId, "${message.runId}:food-order")
        var payment = runCatching { finance.foodPaymentOrder(order.id) }.getOrNull()
        repeat(9) {
            if (payment != null) return@repeat
            delay(300)
            payment = runCatching { finance.foodPaymentOrder(order.id) }.getOrNull()
        }
        val paymentOrder = payment ?: throw IllegalStateException("Payment order is not ready")
        val authorization = finance.authorizePaymentOrder(paymentOrder, password)
        val confirmed = finance.confirmPaymentOrder(paymentOrder, authorization.paymentAuthToken)
        delay(400)
        message.runId?.let { runId ->
            runCatching {
                repository.continueAction(runId, AgentActionRequest("GET_ORDER", orderId = order.id))
            }.onSuccess {
                activeRunId = runId
                lastEventId = null
                observeRun(runId)
            }
        }
        return confirmed.toPaymentResultSnapshot()
    }

    private fun startPaymentResult(
        initial: PaymentResultSnapshot,
        presentation: PaymentResultPresentation = PaymentResultPresentation()
    ) {
        paymentMonitorJob?.cancel()
        paymentPresentation = presentation
        savedStateHandle[PAYMENT_OPERATION_KEY] = initial.reference.operation.name
        savedStateHandle[PAYMENT_ORDER_ID_KEY] = initial.reference.orderId
        val presented = initial.withPresentation(presentation)
        mutableState.update {
            it.copy(
                paymentResult = PaymentResultUiState(
                    snapshot = presented,
                    polling = !presented.status.terminal
                )
            )
        }
        if (presented.status.terminal) {
            appendPaymentSummary(presented)
            return
        }
        paymentMonitorJob = viewModelScope.launch {
            paymentStatusMonitor.observe(presented, presentation).collect { event ->
                when (event) {
                    is PaymentMonitorEvent.Updated -> {
                        mutableState.update {
                            it.copy(
                                paymentResult = PaymentResultUiState(
                                    snapshot = event.snapshot,
                                    polling = !event.snapshot.status.terminal
                                )
                            )
                        }
                        if (event.snapshot.status.terminal) appendPaymentSummary(event.snapshot)
                    }
                    PaymentMonitorEvent.RefreshFailed -> mutableState.update {
                        it.copy(paymentResult = it.paymentResult?.copy(refreshError = true))
                    }
                    PaymentMonitorEvent.TimedOut -> mutableState.update {
                        it.copy(
                            paymentResult = it.paymentResult?.copy(
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

    private fun appendPaymentSummary(snapshot: PaymentResultSnapshot) {
        if (snapshot.reference.operation == PaymentOperation.TRANSFER) return
        val orderId = snapshot.reference.orderId
        if (!summarizedPaymentOrderIds.add(orderId)) return
        val summary = when (snapshot.status) {
            PaymentResultStatus.SUCCEEDED -> if (snapshot.reference.operation == PaymentOperation.PAYMENT) {
                "下单并付款成功，商家正在接单"
            } else {
                "转账成功"
            }
            PaymentResultStatus.PROCESSING -> if (snapshot.reference.operation == PaymentOperation.PAYMENT) {
                "订单支付处理中，可稍后返回查看"
            } else {
                "转账处理中，可稍后在账单中查看"
            }
            PaymentResultStatus.FAILED, PaymentResultStatus.CLOSED -> "支付未完成，请核对余额后重试"
        }
        mutableState.update {
            it.copy(
                messages = it.messages + AiHomeMessage(
                    UUID.randomUUID().toString(),
                    role = AiHomeMessageRole.SYSTEM,
                    text = summary,
                    createdAt = Instant.now()
                )
            )
        }
    }

    private fun refreshInitialState() {
        viewModelScope.launch {
            mutableState.update { it.copy(loading = true, errorMessage = null, canRetry = false) }
            runCatching {
                val conversations = repository.conversations()
                val selected = conversations.firstOrNull()
                Triple(conversations, selected, selected?.let { repository.messages(it.id) }.orEmpty())
            }.onSuccess { (conversations, selected, messages) ->
                reconcileTransferConfirmationCards(messages)
                selectedConversation = selected
                newConversationSelected = selected == null
                mutableState.value = AiHomeUiState(
                    loading = false,
                    conversations = conversations.map { it.toSummary() },
                    selectedConversationId = selected?.id,
                    newConversationSelected = newConversationSelected,
                    draftText = drafts.read(currentDraftKey()),
                    messages = messages.map { it.toDisplayUi() },
                    voiceInputState = voiceInput.state.value,
                    aiSpeechEnabled = voiceSettings.aiSpeechEnabled.value
                )
                resumeSavedPaymentResult()
            }.onFailure(::showRecoverableError)
        }
    }

    private suspend fun submitPending(pending: PendingSubmission) {
        runCatching {
            repository.createRun(
                pending.conversationId,
                CreateAgentRunRequest(pending.clientMessageId, pending.prompt, pending.contextVersion),
                pending.idempotencyKey
            )
        }.onSuccess { run ->
            pendingSubmission = null
            transientMessageDisplay.remember(run.runId, pending.prompt)
            activeRunId = run.runId
            lastEventId = null
            observeRun(run.runId)
        }.onFailure { error ->
            mutableState.update {
                it.copy(streaming = false, errorMessage = repository.message(error), canRetry = true)
            }
            refreshMessagesKeepingError(pending.conversationId)
        }
    }

    private fun observeRun(runId: String) {
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            runCatching {
                repository.resumableEvents(runId, lastEventId).collect { event ->
                    lastEventId = event.id
                    applyEvent(event)
                }
            }.onFailure { error ->
                mutableState.update {
                    it.copy(streaming = false, errorMessage = repository.message(error), canRetry = true)
                }
            }
        }
    }

    private suspend fun applyEvent(event: AgentEventEnvelope) {
        when (event.type) {
            "message.delta" -> event.payload.string("text")?.takeIf { it.isNotEmpty() }
                ?.let { appendAssistantDelta(event.runId, it, event.occurredAt) }
            "message.completed" -> completedSpeech[event.runId] = CompletedSpeech(
                messageId = event.payload.string("messageId"),
                cardType = event.payload.string("cardType")
            )
            "task.error", "security.error" -> mutableState.update {
                it.copy(
                    errorMessage = event.payload.string("message") ?: "AI 任务执行失败，请稍后重试",
                    canRetry = event.payload["retryable"]?.jsonPrimitive?.booleanOrNull ?: false
                )
            }
            "stream.completed" -> finishRun(event.conversationId, event.runId)
        }
    }

    private fun appendAssistantDelta(runId: String, delta: String, occurredAt: String) {
        val id = "stream-$runId"
        mutableState.update { current ->
            val index = current.messages.indexOfFirst { it.id == id }
            val messages = if (index < 0) current.messages + AiHomeMessage(
                id,
                runId,
                AiHomeMessageRole.ASSISTANT,
                delta,
                streaming = true,
                createdAt = parseAiMessageInstant(occurredAt) ?: Instant.now()
            ) else current.messages.toMutableList().also {
                it[index] = it[index].copy(text = it[index].text + delta)
            }
            current.copy(messages = messages)
        }
    }

    private suspend fun finishRun(conversationId: String, completedRunId: String) {
        activeRunId = null
        lastEventId = null
        val error = mutableState.value.errorMessage
        val retry = mutableState.value.canRetry
        val fallbackText = mutableState.value.messages.lastOrNull {
            it.id == "stream-$completedRunId" && it.role == AiHomeMessageRole.ASSISTANT
        }?.text
        val completion = completedSpeech.remove(completedRunId)
        val selected = runCatching { repository.conversations() }.getOrNull()
            ?.firstOrNull { it.id == conversationId }
        val messages = runCatching { repository.messages(conversationId) }.getOrNull()
        messages?.let(::reconcileTransferConfirmationCards)
        if (selected != null) selectedConversation = selected
        val candidate = aiSpeechPolicy.select(
            messages = messages.orEmpty(),
            completedRunId = completedRunId,
            completedMessageId = completion?.messageId,
            completedCardType = completion?.cardType,
            fallbackText = fallbackText
        )
        mutableState.update { current ->
            current.copy(
                loading = false,
                streaming = false,
                conversations = mergeConversations(current.conversations, listOfNotNull(selected)),
                messages = messages?.map { message -> message.toDisplayUi() }
                    ?: current.messages.map { message ->
                        if (message.runId == completedRunId) message.copy(streaming = false) else message
                    },
                errorMessage = error,
                canRetry = retry
            )
        }
        if (voiceScreenVisible && voiceSettings.aiSpeechEnabled.value) {
            candidate?.let { speechOutput.speakAi(it.sourceId, it.text) }
        }
    }

    override fun onCleared() {
        voiceInput.cancel()
        speechOutput.stop(SpeechChannel.AI)
        super.onCleared()
    }

    private suspend fun refreshMessagesKeepingError(conversationId: String) {
        val refreshed = runCatching { repository.messages(conversationId) }.getOrNull() ?: return
        reconcileTransferConfirmationCards(refreshed)
        mutableState.update { it.copy(messages = refreshed.map { message -> message.toDisplayUi() }) }
    }

    private fun reconcileTransferConfirmationCards(messages: List<AiMessageResponse>) {
        val runIds = messages.asSequence()
            .filter { it.cardType == "payment.transfer-intent" }
            .mapNotNull(AiMessageResponse::runId)
            .toSet()
        if (runIds.isEmpty()) return
        mutableState.update {
            it.copy(checkingPaymentRunIds = it.checkingPaymentRunIds + runIds)
        }
        viewModelScope.launch {
            val completed = runIds.filterTo(mutableSetOf()) { runId ->
                val status = runCatching { repository.getRun(runId).status }.getOrNull()
                status != null && !isTransferConfirmationAllowed(status)
            }
            mutableState.update {
                it.copy(
                    completedPaymentRunIds = it.completedPaymentRunIds + completed,
                    checkingPaymentRunIds = it.checkingPaymentRunIds - runIds
                )
            }
        }
    }

    private fun showRecoverableError(error: Throwable) {
        mutableState.update {
            it.copy(loading = false, streaming = false, errorMessage = repository.message(error), canRetry = true)
        }
    }

    private fun showOperationError(message: String) {
        mutableState.update {
            it.copy(confirmationInFlight = false, errorMessage = message, canRetry = false)
        }
    }

    private fun showMemoryError(error: Throwable) {
        mutableState.update {
            it.copy(memoryLoading = false, errorMessage = repository.message(error), canRetry = false)
        }
    }

    private fun currentDraftKey(): String = selectedConversation?.id ?: NEW_CONVERSATION_DRAFT

    private fun rejectWhileRunning(): Boolean {
        if (!mutableState.value.streaming) return false
        mutableState.update { it.copy(errorMessage = "当前任务仍在处理，请完成后再切换会话") }
        return true
    }

    private fun AiConversationResponse.toSummary() = AiHomeConversation(id, title, version)
    private fun AiMessageResponse.toUi() = AiHomeMessage(
        id = id,
        runId = runId,
        role = when (role) {
            "USER" -> AiHomeMessageRole.USER
            "SYSTEM" -> AiHomeMessageRole.SYSTEM
            else -> AiHomeMessageRole.ASSISTANT
        },
        text = content,
        cardType = cardType,
        cardPayload = cardPayload?.let { raw ->
            runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
        },
        createdAt = parseAiMessageInstant(createdAt)
    )

    private fun AiMessageResponse.toDisplayUi(): AiHomeMessage = toUi().copy(
        text = transientMessageDisplay.displayText(runId, role, content)
    )

    private fun mergeConversations(
        existing: List<AiHomeConversation>,
        refreshed: List<AiConversationResponse>
    ): List<AiHomeConversation> {
        val byId = refreshed.associateBy { it.id }
        val replacements = existing.map { byId[it.id]?.toSummary() ?: it }
        return refreshed.filter { value -> existing.none { it.id == value.id } }
            .map { it.toSummary() } + replacements
    }

    private fun JsonObject.string(name: String): String? =
        get(name)?.jsonPrimitive?.contentOrNull
    private fun JsonObject.long(name: String): Long? = get(name)?.jsonPrimitive?.longOrNull

    private data class PendingSubmission(
        val conversationId: String,
        val clientMessageId: String,
        val idempotencyKey: String,
        val prompt: String,
        val contextVersion: Long
    )

    private companion object {
        const val NEW_CONVERSATION_DRAFT = "NEW_CONVERSATION_DRAFT"
        const val PAYMENT_OPERATION_KEY = "ai_payment_operation"
        const val PAYMENT_ORDER_ID_KEY = "ai_payment_order_id"
    }
}

internal fun parseAiMessageInstant(value: String): Instant? =
    runCatching { Instant.parse(value) }.getOrNull()
