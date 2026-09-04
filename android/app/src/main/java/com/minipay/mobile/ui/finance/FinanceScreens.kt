package com.minipay.mobile.ui.finance

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.CameraSelector
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.Image
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minipay.mobile.finance.*
import com.minipay.mobile.network.AutoRefreshEffect
import com.minipay.mobile.profile.UserProfile
import com.minipay.mobile.profile.usableAvatarUrl
import com.minipay.mobile.ui.components.UserAvatar
import com.minipay.mobile.merchant.MerchantPortalViewModel
import com.minipay.mobile.merchant.MerchantLocationPickerScreen
import com.minipay.mobile.merchant.MerchantReceiveScreen
import com.minipay.mobile.merchant.MerchantPaymentScreen
import com.minipay.mobile.ui.theme.*
import com.minipay.mobile.ui.components.UserAvatar
import com.minipay.mobile.ui.scan.MiniPayQrCode
import com.minipay.mobile.ui.scan.parseMiniPayQrCode
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.minipay.mobile.ui.components.AvatarImage
import java.text.NumberFormat
import java.io.ByteArrayOutputStream
import java.time.YearMonth
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun FinanceRoute(
    initial: FinanceDestination,
    initialCardId: String? = null,
    initialBillId: String? = null,
    initialRecipient: TransferRecipientUi? = null,
    groupConversationId: String? = null,
    onBack: () -> Unit,
    onOpenFriendCard: (String) -> Unit = {},
    viewModel: FinanceViewModel = hiltViewModel(),
    merchantViewModel: MerchantPortalViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val merchantState by merchantViewModel.state.collectAsStateWithLifecycle()
    var destination by rememberSaveable(initial) { mutableStateOf(initial) }
    var cardId by rememberSaveable(initialCardId) { mutableStateOf(initialCardId) }
    var billId by rememberSaveable(initialBillId) { mutableStateOf(initialBillId) }
    var pendingDestinationName by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCardId by rememberSaveable { mutableStateOf<String?>(null) }
    // Recipient information is intentionally memory-only. It is resolved again after process death
    // instead of being written to saved state or local storage.
    var selectedTransferRecipient by remember(initialRecipient) { mutableStateOf(initialRecipient) }
    // Merchant scan resolutions are single-use and stay in memory only until the payer confirms.
    var selectedMerchantResolution by remember { mutableStateOf<ScanResolution?>(null) }
    var merchantPaymentCode by remember { mutableStateOf<String?>(null) }
    val history = remember { mutableStateListOf<Triple<FinanceDestination, String?, String?>>() }
    val capabilities = state.capabilities
    AutoRefreshEffect(enabled = !state.submitting) {
        viewModel.refresh()
        when (destination) {
            FinanceDestination.BILLS, FinanceDestination.ALL_BILLS ->
                viewModel.loadBills(reset = true)
            FinanceDestination.BILL_DETAIL -> billId?.let(viewModel::loadBill)
            FinanceDestination.CARD_DETAIL, FinanceDestination.CARD_BALANCE ->
                cardId?.let(viewModel::loadCard)
            FinanceDestination.RECHARGE_RECORDS ->
                viewModel.loadFundingRecords(type = "RECHARGE", reset = true)
            FinanceDestination.WITHDRAWAL_RECORDS ->
                viewModel.loadFundingRecords(type = "WITHDRAWAL", reset = true)
            FinanceDestination.RECEIPT_RECORDS -> viewModel.loadCollectionRecords()
            FinanceDestination.RECEIVE -> viewModel.loadCollectionCode()
            else -> Unit
        }
    }
    val navigate: (FinanceDestination, String?) -> Unit = { next, nextCardId ->
        viewModel.clearMessage()
        history.add(Triple(destination, cardId, billId))
        destination = next
        cardId = nextCardId ?: cardId
    }
    val routeBack = {
        viewModel.clearMessage()
        if (history.isNotEmpty()) {
            val previous = history.removeAt(history.lastIndex)
            destination = previous.first
            cardId = previous.second
            billId = previous.third
        } else onBack()
    }
    val pendingDestination = {
        val pending = pendingDestinationName
            ?.let { name -> FinanceDestination.entries.firstOrNull { it.name == name } }
            ?: FinanceDestination.WALLET
        cardId = pendingCardId
        pending
    }
    val completePendingDestination = {
        val pending = pendingDestination()
        pendingDestinationName = null
        pendingCardId = null
        destination = pending
    }
    val continueAfterRealName = {
        val pending = pendingDestination()
        if (!requiresPaymentPassword(pending) || capabilities?.payPasswordSet == true) {
            completePendingDestination()
        } else {
            destination = FinanceDestination.PAYMENT_PASSWORD
        }
    }
    BackHandler(onBack = routeBack)

    SecureFinanceWindow(state.paymentResult != null || destination in setOf(
        FinanceDestination.REAL_NAME, FinanceDestination.PAYMENT_PASSWORD,
        FinanceDestination.TRANSFER, FinanceDestination.RECHARGE, FinanceDestination.WITHDRAWAL,
        FinanceDestination.CARD_BALANCE, FinanceDestination.FUNDING_RESULT,
        FinanceDestination.MERCHANT_PAYMENT
    ))

    if (state.loading && state.capabilities == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    state.paymentResult?.let { result ->
        val operation = result.snapshot.reference.operation
        val secondaryLabel = when (operation) {
            PaymentOperation.TRANSFER -> "查看账单"
            PaymentOperation.RECHARGE -> "查看充值记录"
            PaymentOperation.WITHDRAWAL -> "查看提现记录"
            PaymentOperation.PAYMENT -> null
        }
        UnifiedPaymentResultScreen(
            state = result,
            secondaryLabel = secondaryLabel,
            onDone = {
                viewModel.clearPaymentResult()
                when (operation) {
                    PaymentOperation.TRANSFER, PaymentOperation.PAYMENT -> {
                        selectedTransferRecipient = null
                        viewModel.refresh()
                        onBack()
                    }
                    PaymentOperation.RECHARGE, PaymentOperation.WITHDRAWAL -> {
                        history.clear()
                        cardId = null
                        destination = FinanceDestination.WALLET
                        viewModel.refresh()
                    }
                }
            },
            onRefresh = viewModel::refreshPaymentResult,
            onSecondary = {
                viewModel.clearPaymentResult()
                history.clear()
                destination = when (operation) {
                    PaymentOperation.TRANSFER, PaymentOperation.PAYMENT -> FinanceDestination.BILLS
                    PaymentOperation.RECHARGE -> FinanceDestination.RECHARGE_RECORDS
                    PaymentOperation.WITHDRAWAL -> FinanceDestination.WITHDRAWAL_RECORDS
                }
            }
        )
        return
    }
    (state.realNameCompletion as? RealNameCompletion.Ready)?.let { completion ->
        RealNameSuccessDialog(
            onContinue = {
                viewModel.consumeRealNameCompletion()
                viewModel.refresh()
                continueAfterRealName()
            }
        )
    }
    if (capabilities != null && !capabilities.realNameVerified &&
        destination != FinanceDestination.REAL_NAME && destination != FinanceDestination.SCAN
    ) {
        RealNameRequiredScreen(
            processing = capabilities.realNameStatus == "PROCESSING",
            onAction = {
                if (capabilities.realNameStatus == "PROCESSING") viewModel.refresh()
                else {
                    viewModel.clearMessage()
                    pendingDestinationName = destination.name
                    pendingCardId = cardId
                    destination = FinanceDestination.REAL_NAME
                }
            },
            onBack = routeBack
        )
        return
    }
    if (capabilities != null && !capabilities.payPasswordSet && requiresPaymentPassword(destination)) {
        PaymentPasswordScreen(
            submitting = state.submitting,
            message = state.message,
            allowSkip = false,
            onBack = routeBack,
            onSkip = {},
            onSubmit = { password -> viewModel.setPaymentPassword(password, completePendingDestination) }
        )
        return
    }

    when (destination) {
        FinanceDestination.WALLET -> WalletHome(
            state = state,
            onBack = routeBack,
            open = { navigate(it, null) },
            onBillClick = { selected ->
                billId = selected
                navigate(FinanceDestination.BALANCE_DETAIL, null)
            },
            onRetry = viewModel::refresh
        )
        FinanceDestination.BILLS -> BillsScreen(
            title = "余额变动明细",
            state = state,
            onBack = routeBack,
            load = viewModel::loadBills,
            onBillClick = { selected ->
                billId = selected
                navigate(FinanceDestination.BALANCE_DETAIL, null)
            }
        )
        FinanceDestination.ALL_BILLS -> BillsScreen(
            title = "全部账单",
            state = state,
            onBack = routeBack,
            load = viewModel::loadBills,
            onBillClick = { selected ->
                billId = selected
                navigate(FinanceDestination.BILL_DETAIL, null)
            }
        )
        FinanceDestination.BALANCE_DETAIL -> BalanceBillDetailScreen(
            billId = requireNotNull(billId),
            state = state,
            onBack = routeBack,
            load = viewModel::loadBill,
            onOpenLinkedBill = { navigate(FinanceDestination.BILL_DETAIL, null) }
        )
        FinanceDestination.BILL_DETAIL -> BillManagementDetailScreen(
            billId = requireNotNull(billId),
            state = state,
            onBack = routeBack,
            onAllBills = {
                history.clear()
                destination = FinanceDestination.ALL_BILLS
            },
            load = viewModel::loadBill,
            save = viewModel::saveBillManagement,
            createTag = viewModel::createBillTag
        )
        FinanceDestination.RECENT_TRANSFER_CONTACTS -> RecentTransferContactsScreen(
            state = state,
            onBack = routeBack,
            load = viewModel::loadRecentTransferCounterparties,
            onTransfer = { contact ->
                selectedTransferRecipient = contact.toTransferRecipientUi()
                navigate(FinanceDestination.TRANSFER, null)
            }
        )
        FinanceDestination.FRIEND_TRANSFER_RECORDS -> {
            val recipient = requireNotNull(selectedTransferRecipient)
            FriendTransferRecordsScreen(
                recipient = recipient,
                state = state,
                onBack = routeBack,
                load = viewModel::loadTransferRecords,
                onBillClick = { selected ->
                    billId = selected
                    navigate(FinanceDestination.BILL_DETAIL, null)
                },
                onTransfer = { navigate(FinanceDestination.TRANSFER, null) }
            )
        }
        FinanceDestination.REAL_NAME -> RealNameScreen(
            state, routeBack,
            onEnter = viewModel::clearMessage,
            onSubmit = viewModel::submitRealName,
            onRetrySession = viewModel::retryRealNameSessionSync
        )
        FinanceDestination.PAYMENT_PASSWORD -> PaymentPasswordScreen(
            submitting = state.submitting,
            message = state.message,
            allowSkip = true,
            onBack = routeBack,
            onSkip = { destination = FinanceDestination.WALLET },
            onSubmit = { password ->
                viewModel.setPaymentPassword(password, completePendingDestination)
            }
        )
        FinanceDestination.TRANSFER -> {
            val recipient = selectedTransferRecipient
            if (recipient == null) {
                TransferRecipientLookupScreen(
                    state = state,
                    onBack = {
                        viewModel.clearRecipientLookup()
                        routeBack()
                    },
                    onResolve = viewModel::resolveTransferRecipient,
                    onLoadFriends = viewModel::loadTransferFriends,
                    onClear = viewModel::clearRecipientLookup,
                    onSelect = {
                        selectedTransferRecipient = it
                        viewModel.clearRecipientLookup()
                    }
                )
            } else {
                TransferPaymentFlow(
                    state = state,
                    recipient = recipient,
                    onBack = {
                        if (recipient.origin == TransferRecipientOrigin.MOBILE_LOOKUP) selectedTransferRecipient = null
                        else routeBack()
                    },
                    onRecords = {
                        navigate(
                            if (recipient.origin == TransferRecipientOrigin.CONTACT) {
                                FinanceDestination.FRIEND_TRANSFER_RECORDS
                            } else {
                                FinanceDestination.BILLS
                            },
                            null
                        )
                    },
                    createIntent = viewModel::createPersonalTransfer,
                    confirm = viewModel::confirmPersonalTransfer,
                    refreshOrder = viewModel::refreshTransferOrder,
                    onPaymentResult = { order ->
                        viewModel.observeTransferResult(order, recipient.display)
                    },
                    onSucceeded = { order ->
                        viewModel.queueTransferReceipt(groupConversationId ?: recipient.conversationId, order, recipient)
                    },
                    onFinished = {
                        selectedTransferRecipient = null
                        viewModel.refresh()
                        onBack()
                    }
                )
            }
        }
        FinanceDestination.SCAN -> ScanScreen(
            state = state,
            onBack = routeBack,
            onOpenReceiveCode = { navigate(FinanceDestination.RECEIVE, null) },
            onEnter = viewModel::clearMessage
        ) { value, retry ->
            when (val code = parseMiniPayQrCode(value)) {
                is MiniPayQrCode.FriendCard -> onOpenFriendCard(code.miniPayNo)
                is MiniPayQrCode.PersonalCollection -> viewModel.resolveCode(code.rawValue, { resolution ->
                    selectedTransferRecipient = resolution.toTransferRecipientUi(
                        TransferSource.PERSONAL_COLLECTION_CODE
                    )
                    destination = FinanceDestination.TRANSFER
                }, retry)
                is MiniPayQrCode.MerchantCollection -> viewModel.resolveCode(code.rawValue, { resolution ->
                    if (!resolution.resolutionId.isNullOrBlank() && !resolution.merchantId.isNullOrBlank()) {
                        merchantPaymentCode = code.rawValue
                        selectedMerchantResolution = resolution
                    } else retry("经营收款码无效或已过期")
                }, retry)
                null -> retry("仅支持当前服务环境的 MiniPay 收款码或好友名片")
            }
        }
        FinanceDestination.CARDS -> CardsScreen(
            state = state,
            onBack = routeBack,
            onAdd = { navigate(FinanceDestination.ADD_CARD, null) },
            onSelect = { navigate(FinanceDestination.CARD_DETAIL, it) },
            onRetry = viewModel::refreshCards
        )
        FinanceDestination.ADD_CARD -> AddCardScreen(
            state = state,
            onBack = routeBack,
            bind = viewModel::bindCard,
            onBound = routeBack
        )
        FinanceDestination.CARD_DETAIL -> CardDetailScreen(
            state = state,
            cardId = requireNotNull(cardId),
            onBack = routeBack,
            load = viewModel::loadCard,
            open = { navigate(it, cardId) },
            disable = { id -> viewModel.disableCard(id, routeBack) }
        )
        FinanceDestination.CARD_BALANCE -> BankBalanceScreen(
            state = state,
            cardId = requireNotNull(cardId),
            onBack = routeBack,
            query = viewModel::queryBankBalance
        )
        FinanceDestination.CARD_TRANSACTIONS -> BankTransactionsScreen(
            state = state,
            cardId = requireNotNull(cardId),
            onBack = routeBack,
            load = viewModel::loadBankTransactions
        )
        FinanceDestination.RECEIVE -> ReceiveScreen(
            state = state,
            onBack = routeBack,
            load = viewModel::loadCollectionCode,
            loadRecords = { viewModel.loadCollectionRecords() },
            observeReceipts = viewModel::observeCollectionReceipts,
            onReceiptSpeechEnabledChange = viewModel::setReceiptSpeechEnabled,
            onRecords = { navigate(FinanceDestination.RECEIPT_RECORDS, null) },
            onMerchantReceive = { navigate(FinanceDestination.MERCHANT_RECEIVE, null) }
        )
        FinanceDestination.RECEIPT_RECORDS -> ReceiptRecordsScreen(
            state = state,
            onBack = routeBack,
            load = viewModel::loadCollectionRecords,
            onBillClick = { selected ->
                billId = selected
                navigate(FinanceDestination.BILL_DETAIL, null)
            }
        )
        FinanceDestination.MERCHANT_RECEIVE -> MerchantReceiveScreen(
            state = merchantState,
            onBack = routeBack,
            onLoad = merchantViewModel::load,
            onUpload = merchantViewModel::upload,
            onShopNameChange = merchantViewModel::updateShopName,
            onSelectLocation = { navigate(FinanceDestination.MERCHANT_LOCATION_PICKER, null) },
            onRemoveImage = merchantViewModel::removeImage,
            onSubmit = merchantViewModel::submit,
            onRetryInitialization = merchantViewModel::retryInitialization,
            onRealName = { navigate(FinanceDestination.REAL_NAME, null) }
        )
        FinanceDestination.MERCHANT_LOCATION_PICKER -> MerchantLocationPickerScreen(
            initialLatitude = merchantState.draft.latitude,
            initialLongitude = merchantState.draft.longitude,
            initialAddress = merchantState.draft.address,
            onBack = routeBack,
            onConfirm = { selection ->
                merchantViewModel.updateSelectedLocation(
                    selection.latitude,
                    selection.longitude,
                    selection.address
                )
                routeBack()
            }
        )
        FinanceDestination.MERCHANT_PAYMENT -> MerchantPaymentScreen(
            merchantName = merchantState.resolution?.merchantName ?: "商户收款",
            state = merchantState,
            onBack = routeBack,
            onPay = { amount, password ->
                merchantPaymentCode?.let { merchantViewModel.resolveAndPay(it, amount, password) }
            }
        )
        FinanceDestination.RECHARGE -> FundingScreen(
            title = "充值", state = state, requirePassword = true,
            preselectedCardId = cardId, onBack = routeBack,
            onBindCard = { navigate(FinanceDestination.ADD_CARD, null) },
            onRecords = { navigate(FinanceDestination.RECHARGE_RECORDS, null) },
            onCompleted = { navigate(FinanceDestination.FUNDING_RESULT, null) },
            onSubmit = { card, cents, password, done -> viewModel.recharge(card, cents, password, done) }
        )
        FinanceDestination.WITHDRAWAL -> FundingScreen(
            title = "提现", state = state, requirePassword = true,
            preselectedCardId = cardId, onBack = routeBack,
            onBindCard = { navigate(FinanceDestination.ADD_CARD, null) },
            onRecords = { navigate(FinanceDestination.WITHDRAWAL_RECORDS, null) },
            onCompleted = { navigate(FinanceDestination.FUNDING_RESULT, null) },
            onSubmit = { card, cents, password, done -> viewModel.withdraw(card, cents, password, done) }
        )
        FinanceDestination.FUNDING_RESULT -> FundingResultScreen(
            result = state.fundingResult,
            cards = state.cards,
            onBack = {
                viewModel.clearFundingResult()
                routeBack()
            },
            onWallet = {
                viewModel.clearFundingResult()
                history.clear()
                cardId = null
                destination = FinanceDestination.WALLET
            },
            onRecords = { type ->
                viewModel.clearFundingResult()
                history.clear()
                destination = if (type == "RECHARGE") {
                    FinanceDestination.RECHARGE_RECORDS
                } else {
                    FinanceDestination.WITHDRAWAL_RECORDS
                }
            }
        )
        FinanceDestination.RECHARGE_RECORDS -> FundingRecordsScreen(
            title = "充值记录", type = "RECHARGE",
            state = state,
            onBack = routeBack,
            load = viewModel::loadFundingRecords
        )
        FinanceDestination.WITHDRAWAL_RECORDS -> FundingRecordsScreen(
            title = "提现记录", type = "WITHDRAWAL",
            state = state,
            onBack = routeBack,
            load = viewModel::loadFundingRecords
        )
    }
    selectedMerchantResolution?.let { resolution ->
        MerchantCollectionPaymentDialog(
            merchantName = resolution.merchantName.orEmpty().ifBlank { "商户" },
            submitting = state.submitting,
            message = state.message,
            onDismiss = {
                selectedMerchantResolution = null
                viewModel.clearMessage()
            },
            onPay = { amountCent, password ->
                viewModel.payMerchantCollection(resolution, amountCent, password) {
                    selectedMerchantResolution = null
                    viewModel.refresh()
                    routeBack()
                }
            }
        )
    }
}

internal fun destinationAfterRealName(
    status: String,
    payPasswordSet: Boolean
): FinanceDestination? = when (status) {
    "VERIFIED" -> if (payPasswordSet) FinanceDestination.WALLET else FinanceDestination.PAYMENT_PASSWORD
    "PROCESSING" -> FinanceDestination.WALLET
    else -> null
}

internal fun requiresPaymentPassword(destination: FinanceDestination): Boolean = destination in setOf(
    FinanceDestination.TRANSFER,
    FinanceDestination.CARD_BALANCE
)

@Composable
private fun MerchantCollectionPaymentDialog(
    merchantName: String,
    submitting: Boolean,
    message: String?,
    onDismiss: () -> Unit,
    onPay: (Long, String) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val amountCent = yuanToCent(amount)
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text("向${merchantName}付款") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("经营码付款将使用钱包余额，金额和支付密码仅在确认时提交。")
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { char -> char.isDigit() || char == '.' }.take(10) },
                    label = { Text("金额（元）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    enabled = !submitting
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it.filter(Char::isDigit).take(6) },
                    label = { Text("6 位支付密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    enabled = !submitting
                )
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                enabled = !submitting && amountCent != null && password.length == 6,
                onClick = { onPay(requireNotNull(amountCent), password) }
            ) { Text(if (submitting) "支付中" else "确认支付") }
        },
        dismissButton = {
            TextButton(enabled = !submitting, onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
internal fun RealNameSuccessDialog(onContinue: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1500)
        onContinue()
    }
    AlertDialog(
        onDismissRequest = {},
        icon = {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = RoundedCornerShape(MilingRadii.Large),
                color = MilingSuccessSoft
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = MilingSuccess,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
        },
        title = { Text("实名认证成功", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("身份信息已核验完成", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(MilingSpacing.Sm))
                Text("正在安全返回你刚才的操作", color = MilingTextSecondary)
            }
        },
        confirmButton = {}
    )
}

@Composable
internal fun WalletHome(
    state: FinanceUiState,
    onBack: () -> Unit,
    open: (FinanceDestination) -> Unit,
    onBillClick: (String) -> Unit = {},
    onRetry: () -> Unit = {}
) {
    val wallet = state.wallet
    FinanceScaffold("钱包", onBack) {
        if (wallet == null) {
            if (state.walletLoading) {
                Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                ErrorText(state.walletError ?: "钱包加载失败，请稍后重试")
                TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text("重新加载") }
            }
            return@FinanceScaffold
        }
        Surface(
            modifier = Modifier.fillMaxWidth().border(1.dp, MilingBorder, RoundedCornerShape(MilingRadii.ExtraLarge)),
            shape = RoundedCornerShape(MilingRadii.ExtraLarge),
            color = MilingSurface
        ) {
            Column(Modifier.fillMaxWidth().padding(MilingSpacing.Xxl)) {
                Text("可用余额（元）", color = MilingTextSecondary, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(MilingSpacing.Sm))
                Text(
                    money(wallet.availableAmountCent),
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.semantics { contentDescription = "可用余额 ${money(wallet.availableAmountCent)}" }
                )
                Spacer(Modifier.height(MilingSpacing.Sm))
                Text("账户资金由米灵安全守护", color = MilingTextSecondary, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(MilingSpacing.Xl))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MilingSpacing.Md)) {
                    Button(
                        onClick = { open(FinanceDestination.RECHARGE) },
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                    ) {
                        Icon(Icons.Outlined.AddCard, contentDescription = null)
                        Spacer(Modifier.width(MilingSpacing.Sm))
                        Text("充值")
                    }
                    OutlinedButton(
                        onClick = { open(FinanceDestination.WITHDRAWAL) },
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                    ) {
                        Icon(Icons.Outlined.Savings, contentDescription = null)
                        Spacer(Modifier.width(MilingSpacing.Sm))
                        Text("提现")
                    }
                }
            }
        }
        if (wallet.sandboxNotice.isNotBlank()) {
            Text(
                wallet.sandboxNotice,
                color = MilingTextSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        Spacer(Modifier.height(MilingSpacing.Xl))
        Surface(
            modifier = Modifier.fillMaxWidth().border(1.dp, MilingBorder, RoundedCornerShape(MilingRadii.Large)).clickable { open(FinanceDestination.CARDS) },
            shape = RoundedCornerShape(MilingRadii.Large),
            color = MilingSurface
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 92.dp).padding(horizontal = MilingSpacing.Lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(Modifier.size(52.dp), RoundedCornerShape(MilingRadii.Medium), color = MilingPrimarySoft) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.AccountBalance, null, tint = MilingPrimary) }
                }
                Spacer(Modifier.width(MilingSpacing.Lg))
                Column(Modifier.weight(1f)) {
                    Text("银行卡", style = MaterialTheme.typography.titleMedium)
                    Text("查看和管理已绑定银行卡", color = MilingTextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
                Icon(Icons.Outlined.ChevronRight, "进入银行卡", tint = MilingIconSecondary)
            }
        }
        Spacer(Modifier.height(MilingSpacing.Section))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("最近动态", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { open(FinanceDestination.BILLS) }) { Text("查看全部") }
        }
        if (wallet.recentBills.isEmpty()) EmptyState("暂无最近动态")
        wallet.recentBills.forEach { bill ->
            ListItem(
                modifier = Modifier.clickable { onBillClick(bill.billId) },
                leadingContent = { BillAvatar(bill, 44.dp, state.currentUserProfile) },
                headlineContent = { Text(bill.counterpartyDisplay ?: businessTypeText(bill.businessType)) },
                supportingContent = { Text(formatOccurredAt(bill.occurredAt)) },
                trailingContent = {
                    Text(
                        (if (bill.direction == "INCOME") "+" else "-") + money(bill.amountCent),
                        color = if (bill.direction == "INCOME") MilingSuccess else MilingTextPrimary
                    )
                }
            )
            HorizontalDivider(color = MilingDivider)
        }
        Spacer(Modifier.height(MilingSpacing.Section))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("资金记录", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { open(FinanceDestination.BILLS) }) { Text("查看全部") }
        }
        Spacer(Modifier.height(MilingSpacing.Sm))
        WalletRecordEntry("充值记录", "查看全部充值明细", Icons.Outlined.AddCard) { open(FinanceDestination.RECHARGE_RECORDS) }
        Spacer(Modifier.height(MilingSpacing.Md))
        WalletRecordEntry("提现记录", "查看全部提现明细", Icons.Outlined.Savings) { open(FinanceDestination.WITHDRAWAL_RECORDS) }
        state.message?.let { ErrorText(it) }
    }
}

@Composable
private fun WalletRecordEntry(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().border(1.dp, MilingBorder, RoundedCornerShape(MilingRadii.Large)).clickable(onClick = onClick),
        shape = RoundedCornerShape(MilingRadii.Large),
        color = MilingSurface
    ) {
        Row(Modifier.heightIn(min = 88.dp).padding(horizontal = MilingSpacing.Lg), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(48.dp), RoundedCornerShape(MilingRadii.Medium), color = MilingPrimarySoft) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = MilingPrimary) }
            }
            Spacer(Modifier.width(MilingSpacing.Lg))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, color = MilingTextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.Outlined.ChevronRight, "查看$title", tint = MilingIconSecondary)
        }
    }
}

private fun RecentTransferCounterparty.toTransferRecipientUi() = TransferRecipientUi(
    receiverUserId = userId,
    nickname = nickname,
    display = nickname,
    accountMasked = null,
    legalNameMasked = null,
    avatarUrl = avatarUrl,
    transferSource = TransferSource.FORM,
    origin = TransferRecipientOrigin.RECENT_COUNTERPARTY
)

@Composable
internal fun RecentTransferContactsScreen(
    state: FinanceUiState,
    onBack: () -> Unit,
    load: () -> Unit,
    onTransfer: (RecentTransferCounterparty) -> Unit
) {
    LaunchedEffect(Unit) { load() }
    Column(Modifier.fillMaxSize().background(Color.White).statusBarsPadding().navigationBarsPadding()) {
        FinanceTopBar("最近转账联系人", onBack)
        when {
            state.recentTransferCounterpartiesLoading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.recentTransferCounterpartiesError != null ->
                Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ErrorText(state.recentTransferCounterpartiesError)
                    TextButton(onClick = load) { Text("重新加载") }
                }
            state.recentTransferCounterparties.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { EmptyState("暂无最近转账联系人") }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(state.recentTransferCounterparties, key = { it.userId }) { contact ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 88.dp).padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(Modifier.size(52.dp), RoundedCornerShape(6.dp), Color(0xFFF0F1F3)) {
                            if (!contact.avatarUrl.isNullOrBlank()) AvatarImage(
                                avatarUrl = contact.avatarUrl, contentDescription = "${contact.nickname}头像",
                                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                            ) else Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Person, null, tint = Color(0xFFB8B8B8), modifier = Modifier.size(34.dp))
                            }
                        }
                        Text(contact.nickname, style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f).padding(horizontal = 18.dp))
                        OutlinedButton(
                            onClick = { onTransfer(contact) },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                        ) { Text("转账") }
                    }
                    HorizontalDivider(color = Color(0xFFECECEC))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FriendTransferRecordsScreen(
    recipient: TransferRecipientUi,
    state: FinanceUiState,
    onBack: () -> Unit,
    load: (String, String?, String?, String?, Boolean) -> Unit,
    onBillClick: (String) -> Unit,
    onTransfer: () -> Unit
) {
    var direction by rememberSaveable(recipient.receiverUserId) { mutableStateOf<String?>(null) }
    var month by rememberSaveable(recipient.receiverUserId) { mutableStateOf<String?>(null) }
    var status by rememberSaveable(recipient.receiverUserId) { mutableStateOf<String?>(null) }
    var showFilter by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(recipient.receiverUserId, direction, month, status) {
        load(recipient.receiverUserId, direction, month, status, true)
    }
    if (showFilter) ModalBottomSheet(onDismissRequest = { showFilter = false }) {
        Text("筛选转账记录", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
        Text("月份", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(20.dp, 18.dp, 20.dp, 8.dp))
        val months = remember { (0 until 24).map { YearMonth.now().minusMonths(it.toLong()).toString() } }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
            item {
                FilterSheetRow("全部月份", month == null) { month = null }
            }
            items(months) { value ->
                FilterSheetRow(value.replace("-", "年") + "月", month == value) { month = value }
            }
        }
        Text("状态", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(20.dp, 18.dp, 20.dp, 8.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(null to "全部", "SUCCEEDED" to "成功", "PROCESSING" to "处理中", "FAILED" to "失败").forEach { (value, label) ->
                FilterChip(selected = status == value, onClick = { status = value }, label = { Text(label) })
            }
        }
        Button(onClick = { showFilter = false }, modifier = Modifier.fillMaxWidth().padding(20.dp).heightIn(min = 52.dp)) {
            Text("完成")
        }
    }
    Column(Modifier.fillMaxSize().background(Color(0xFFF4F5F8)).statusBarsPadding().navigationBarsPadding()) {
        FinanceTopBar("转账记录", onBack)
        Row(
            Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            UserAvatar(
                name = recipient.nickname,
                avatarUrl = recipient.avatarUrl,
                colorIndex = recipient.receiverUserId.hashCode(),
                size = 44.dp,
                shape = RoundedCornerShape(10.dp)
            )
            Text(recipient.nickname, style = MaterialTheme.typography.titleMedium)
        }
        Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(null to "全部", "INCOME" to "转入", "EXPENSE" to "转出").forEach { (value, label) ->
                FilterChip(selected = direction == value, onClick = { direction = value }, label = { Text(label) })
            }
            TextButton(onClick = { showFilter = true }, modifier = Modifier.weight(1f)) {
                Text(if (month == null && status == null) "筛选 ▼" else "已筛选 ▼", color = MilingTextPrimary)
            }
        }
        Box(Modifier.weight(1f)) {
            when {
                state.transferRecordsLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.transferRecordsError != null && state.transferRecords.isEmpty() -> Column(
                    Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ErrorText(state.transferRecordsError)
                    TextButton(onClick = { load(recipient.receiverUserId, direction, month, status, true) }) { Text("重新加载") }
                }
                state.transferRecords.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { EmptyState("暂无转账记录") }
                else -> LazyColumn(
                    Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 104.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    state.transferRecords.groupBy(::billMonthKey).forEach { (monthKey, bills) ->
                        val totals = state.transferRecordMonths.firstOrNull { it.month == monthKey }
                        item("transfer-month-$monthKey") {
                            Surface(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), Color.White) {
                                Column {
                                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(billMonthTitle(monthKey), style = MaterialTheme.typography.headlineMedium)
                                        Spacer(Modifier.weight(1f))
                                    }
                                    Text(
                                        "支出 ${money(totals?.expenseAmountCent ?: 0)}    收入 ${money(totals?.incomeAmountCent ?: 0)}",
                                        modifier = Modifier.padding(horizontal = 18.dp).padding(bottom = 14.dp),
                                        color = MilingTextSecondary
                                    )
                                    HorizontalDivider(color = Color(0xFFF0F0F0))
                                    bills.forEachIndexed { index, bill ->
                                        WalletBillRow(bill, state.currentUserProfile) { onBillClick(bill.billId) }
                                        if (index != bills.lastIndex) HorizontalDivider(Modifier.padding(start = 74.dp), color = Color(0xFFF0F0F0))
                                    }
                                }
                            }
                        }
                    }
                    if (state.transferRecords.size < state.transferRecordTotal) item("transfer-load-more") {
                        TextButton(
                            onClick = { load(recipient.receiverUserId, direction, month, status, false) },
                            enabled = !state.transferRecordsLoadingMore,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (state.transferRecordsLoadingMore) "加载中…" else "加载更多") }
                    }
                }
            }
            Button(
                onClick = onTransfer,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp).heightIn(min = 56.dp),
                shape = RoundedCornerShape(28.dp)
            ) { Text("向TA转账", style = MaterialTheme.typography.titleLarge) }
        }
    }
}

@Composable
private fun FilterSheetRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Outlined.Check, null, tint = MilingPrimary)
    }
}

@Composable
internal fun BillsScreen(
    title: String,
    state: FinanceUiState,
    onBack: () -> Unit,
    businessType: String? = null,
    load: (String?, String?, Boolean) -> Unit,
    onBillClick: (String) -> Unit = {}
) {
    var direction by rememberSaveable(businessType) { mutableStateOf<String?>(null) }
    val collapsed = remember { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(direction, businessType) { load(direction, businessType, true) }
    Column(
        Modifier.fillMaxSize().background(Color(0xFFF4F5F8))
            .statusBarsPadding().navigationBarsPadding()
    ) {
        FinanceTopBar(title, onBack)
        if (businessType == null) {
            Row(Modifier.fillMaxWidth().background(Color.White)) {
                BillFilter("全部", direction == null) { direction = null }
                BillFilter("支出", direction == "EXPENSE") { direction = "EXPENSE" }
                BillFilter("收入", direction == "INCOME") { direction = "INCOME" }
            }
        }
        if (state.billsLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (state.message != null && state.bills.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ErrorText(state.message)
                TextButton(onClick = { load(direction, businessType, true) }) { Text("重新加载") }
            }
        } else if (state.bills.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    when {
                        title == "全部账单" -> "暂无账单"
                        businessType == null -> "暂无余额变动"
                        else -> "暂无$title"
                    }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp)
            ) {
                state.bills.groupBy(::billMonthKey).forEach { (monthKey, bills) ->
                    item(key = "month-$monthKey") {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                .clickable { collapsed[monthKey] = !(collapsed[monthKey] ?: false) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(billMonthTitle(monthKey), style = MaterialTheme.typography.headlineMedium)
                            Icon(
                                if (collapsed[monthKey] == true) Icons.Outlined.ArrowDropDown
                                else Icons.Outlined.ArrowDropUp,
                                contentDescription = if (collapsed[monthKey] == true) "展开" else "折叠"
                            )
                        }
                    }
                    if (collapsed[monthKey] != true) item(key = "card-$monthKey") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = Color.White
                        ) {
                            Column(Modifier.padding(horizontal = 16.dp)) {
                                bills.forEachIndexed { index, bill ->
                                    WalletBillRow(bill, state.currentUserProfile) { onBillClick(bill.billId) }
                                    if (index != bills.lastIndex) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(start = 58.dp),
                                            color = Color(0xFFF0F0F0)
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                    }
                }
                if (state.bills.size < state.billTotal) item("load-more") {
                    TextButton(
                        onClick = { load(direction, businessType, false) },
                        enabled = !state.billsLoadingMore,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) {
                        if (state.billsLoadingMore) CircularProgressIndicator(Modifier.size(20.dp))
                        else Text("加载更多")
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.BillFilter(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.weight(1f).height(64.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(label, color = if (selected) MilingPrimary else MilingTextPrimary, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(9.dp))
        Box(
            Modifier.width(42.dp).height(3.dp)
                .background(if (selected) MilingPrimary else Color.Transparent, RoundedCornerShape(2.dp))
        )
    }
}

@Composable
private fun BalanceBillDetailScreen(
    billId: String,
    state: FinanceUiState,
    onBack: () -> Unit,
    load: (String) -> Unit,
    onOpenLinkedBill: () -> Unit
) {
    LaunchedEffect(billId) { load(billId) }
    val detail = state.selectedBillDetail
    Column(
        Modifier.fillMaxSize().background(Color(0xFFF4F5F8))
            .statusBarsPadding().navigationBarsPadding()
    ) {
        FinanceTopBar("余额明细详情", onBack)
        if (state.billDetailLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (detail == null) {
            Column(Modifier.padding(24.dp)) {
                state.message?.let { ErrorText(it) }
                TextButton(onClick = { load(billId) }) { Text("重新加载") }
            }
        } else {
            val bill = detail.bill
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                shape = RoundedCornerShape(20.dp), color = Color.White
            ) {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    BillAvatar(bill, 56.dp, state.currentUserProfile)
                    Spacer(Modifier.height(14.dp))
                    Text(billDisplayTitle(bill), style = MaterialTheme.typography.titleLarge)
                    Text(signedPlainAmount(bill), style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.height(32.dp))
                    DetailRow("关联账单") {
                        TextButton(onClick = onOpenLinkedBill, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("查看账单详情", color = Color(0xFF174A78))
                        }
                    }
                    HorizontalDivider(color = Color(0xFFF0F0F0))
                    DetailTextRow("交易时间", formatOccurredAtSeconds(bill.occurredAt))
                    DetailTextRow("交易类型", if (bill.direction == "INCOME") "收入" else "支出")
                    DetailTextRow("商品说明", billDisplayTitle(bill))
                    DetailTextRow("对方账户", bill.counterpartyProfile?.nickname ?: bill.counterpartyDisplay ?: "--")
                    DetailTextRow("交易号", bill.businessNo)
                    DetailTextRow("备注", bill.remark ?: "--")
                    DetailTextRow("余额", bill.balanceAfterCent?.let { "¥${plainAmount(it)}" } ?: "--")
                    DetailTextRow("状态", statusText(bill.status))
                    bill.failureCode?.let { DetailTextRow("失败原因", it) }
                }
            }
        }
    }
}

@Composable
private fun BillManagementDetailScreen(
    billId: String,
    state: FinanceUiState,
    onBack: () -> Unit,
    onAllBills: () -> Unit,
    load: (String) -> Unit,
    save: (String, String, List<String>, String?, Boolean) -> Unit,
    createTag: (String, (BillTag) -> Unit) -> Unit
) {
    LaunchedEffect(billId) { load(billId) }
    val detail = state.selectedBillDetail
    var category by remember(billId) { mutableStateOf("OTHER") }
    var selectedTags by remember(billId) { mutableStateOf(setOf<String>()) }
    var note by remember(billId) { mutableStateOf<String?>(null) }
    var included by remember(billId) { mutableStateOf(true) }
    var showMore by rememberSaveable(billId) { mutableStateOf(false) }
    var categoryPicker by remember { mutableStateOf(false) }
    var noteEditor by remember { mutableStateOf(false) }
    var tagEditor by remember { mutableStateOf(false) }
    LaunchedEffect(detail?.management) {
        val management = detail?.management ?: return@LaunchedEffect
        category = management.categoryCode
        selectedTags = management.tags.map { it.tagId }.toSet()
        note = management.userNote
        included = management.includedInStatistics
    }
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().background(Color(0xFFF4F5F8))
            .statusBarsPadding().navigationBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
            Text("账单详情", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onAllBills, modifier = Modifier.heightIn(min = 48.dp)) { Text("全部账单", color = MilingTextPrimary) }
        }
        if (state.billDetailLoading || detail == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (state.billDetailLoading) CircularProgressIndicator()
                else TextButton(onClick = { load(billId) }) { Text("重新加载") }
            }
        } else {
            val bill = detail.bill
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Surface(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), Color.White) {
                        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            BillAvatar(bill, 56.dp, state.currentUserProfile)
                            Spacer(Modifier.height(10.dp))
                            Text(bill.counterpartyProfile?.nickname ?: bill.counterpartyDisplay ?: businessTypeText(bill.businessType), style = MaterialTheme.typography.titleLarge)
                            Text(signedPlainAmount(bill), style = MaterialTheme.typography.displaySmall)
                            Text(statusText(bill.status), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(24.dp))
                            DetailTextRow("创建时间", formatOccurredAtSeconds(bill.occurredAt))
                            DetailTextRow("交易备注", bill.remark ?: "--")
                            DetailTextRow("对方账户", bill.counterpartyProfile?.legalNameMasked ?: bill.counterpartyDisplay ?: "--")
                            TextButton(onClick = { showMore = !showMore }, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(if (showMore) "收起" else "更多", color = Color(0xFF999999))
                                Icon(if (showMore) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown, null)
                            }
                            if (showMore) {
                                DetailTextRow("交易号", bill.businessNo)
                                DetailTextRow("业务类型", businessTypeText(bill.businessType))
                                DetailTextRow("来源", bill.source ?: "--")
                                DetailTextRow("交易后余额", bill.balanceAfterCent?.let { "¥${plainAmount(it)}" } ?: "--")
                                bill.failureCode?.let { DetailTextRow("失败原因", it) }
                            }
                        }
                    }
                }
                item {
                    Surface(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), Color.White) {
                        Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                            Text("账单管理", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 10.dp))
                            ManagementActionRow("账单分类", billCategoryText(category)) { categoryPicker = true }
                            ManagementActionRow("标签", if (selectedTags.isEmpty()) "请选择" else "已选 ${selectedTags.size} 个") { tagEditor = true }
                            Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("计入收支", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Switch(checked = included, onCheckedChange = { included = it })
                            }
                            ManagementActionRow("备注", note ?: "添加") { noteEditor = true }
                            Button(
                                onClick = { save(billId, category, selectedTags.toList(), note, included) },
                                enabled = !state.billManagementSaving,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                            ) {
                                if (state.billManagementSaving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                else Text(if (state.billManagementSaved) "已保存" else "保存")
                            }
                            state.message?.let { ErrorText(it) }
                        }
                    }
                }
                item {
                    Surface(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), Color.White) {
                        Column(Modifier.padding(8.dp)) {
                            val unavailable = { Toast.makeText(context, "功能暂未开放", Toast.LENGTH_SHORT).show() }
                            Row(Modifier.fillMaxWidth()) {
                                PrototypeAction("查看往来记录", Icons.Outlined.ManageSearch, unavailable, Modifier.weight(1f))
                                PrototypeAction("往来流水证明", Icons.Outlined.Description, unavailable, Modifier.weight(1f))
                            }
                            Row(Modifier.fillMaxWidth()) {
                                PrototypeAction("申请电子回单", Icons.Outlined.MailOutline, unavailable, Modifier.weight(1f))
                                PrototypeAction("对此订单有疑问", Icons.Outlined.HelpOutline, unavailable, Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
    if (categoryPicker) {
        AlertDialog(
            onDismissRequest = { categoryPicker = false },
            title = { Text("选择账单分类") },
            text = { LazyColumn { items(BILL_CATEGORIES) { item ->
                Text(item.second, Modifier.fillMaxWidth().clickable { category = item.first; categoryPicker = false }.padding(vertical = 14.dp))
            } } },
            confirmButton = { TextButton(onClick = { categoryPicker = false }) { Text("取消") } }
        )
    }
    if (noteEditor) {
        var draft by remember(noteEditor) { mutableStateOf(note.orEmpty()) }
        AlertDialog(
            onDismissRequest = { noteEditor = false }, title = { Text("添加备注") },
            text = { OutlinedTextField(draft, { if (it.length <= 200) draft = it }, supportingText = { Text("${draft.length}/200") }) },
            confirmButton = { TextButton(onClick = { note = draft.trim().ifBlank { null }; noteEditor = false }) { Text("确定") } },
            dismissButton = { TextButton(onClick = { noteEditor = false }) { Text("取消") } }
        )
    }
    if (tagEditor) {
        var newName by remember(tagEditor) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { tagEditor = false }, title = { Text("选择标签") },
            text = {
                Column {
                    state.billTags.forEach { tag ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable {
                            selectedTags = if (tag.tagId in selectedTags) selectedTags - tag.tagId
                            else if (selectedTags.size < 5) selectedTags + tag.tagId else selectedTags
                        }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(tag.tagId in selectedTags, onCheckedChange = null)
                            Text(tag.name)
                        }
                    }
                    OutlinedTextField(newName, { if (it.length <= 12) newName = it }, label = { Text("新建标签") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) createTag(newName.trim()) { selectedTags = selectedTags + it.tagId }
                    else tagEditor = false
                }) { Text(if (newName.isBlank()) "完成" else "新建") }
            },
            dismissButton = { TextButton(onClick = { tagEditor = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun FundingRecordsScreen(
    title: String,
    type: String,
    state: FinanceUiState,
    onBack: () -> Unit,
    load: (String, Boolean) -> Unit
) {
    LaunchedEffect(type) { load(type, true) }
    val recharge = type == "RECHARGE"
    val records = if (recharge) state.rechargeRecords else state.withdrawalRecords
    val loading = if (recharge) state.rechargeRecordsLoading else state.withdrawalRecordsLoading
    val total = if (recharge) state.rechargeRecordTotal else state.withdrawalRecordTotal
    Column(Modifier.fillMaxSize().background(Color(0xFFF4F5F8)).statusBarsPadding().navigationBarsPadding()) {
        FinanceTopBar(title, onBack)
        when {
            loading && records.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.fundingRecordsError != null && records.isEmpty() -> Column(Modifier.padding(24.dp)) {
                ErrorText(state.fundingRecordsError)
                TextButton(onClick = { load(type, true) }) { Text("重新加载") }
            }
            records.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("暂无$title", color = MilingTextSecondary) }
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(records, key = { it.applicationId }) { order ->
                    Surface(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp), Color.White) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(Modifier.size(46.dp), CircleShape, if (recharge) Color(0xFFEAF7EF) else Color(0xFFEAF2FF)) {
                                Box(contentAlignment = Alignment.Center) { Icon(if (recharge) Icons.Outlined.AddCard else Icons.Outlined.Savings, null, tint = if (recharge) Color(0xFF20A15A) else MilingPrimary) }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${if (recharge) "充值" else "提现"} · ${order.bankName}", style = MaterialTheme.typography.titleMedium)
                                Text(order.maskedCardNo, color = MilingTextSecondary)
                                Text(formatOccurredAtSeconds(order.createdAt), color = MilingTextMuted, style = MaterialTheme.typography.bodySmall)
                                order.failureCode?.let { Text(it, color = MilingError, style = MaterialTheme.typography.bodySmall) }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text((if (recharge) "+" else "-") + plainAmount(order.amountCent), style = MaterialTheme.typography.titleMedium)
                                Text(statusText(order.status), color = fundingStatusColor(order.status), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                if (records.size < total) item { TextButton(onClick = { load(type, false) }, enabled = !loading, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (loading) "加载中" else "加载更多") } }
            }
        }
    }
}

internal fun billReceiverAvatarUrl(bill: WalletBill, currentUserProfile: UserProfile?): String? =
    if (bill.businessType == "TRANSFER" && bill.direction == "INCOME") {
        currentUserProfile?.avatarUrl
    } else {
        bill.counterpartyProfile?.avatarUrl
    }

internal fun billReceiverName(bill: WalletBill, currentUserProfile: UserProfile?): String =
    if (bill.businessType == "TRANSFER" && bill.direction == "INCOME") {
        currentUserProfile?.nickname ?: "收款人"
    } else {
        bill.counterpartyProfile?.nickname ?: "收款人"
    }

@Composable
private fun BillAvatar(
    bill: WalletBill,
    size: androidx.compose.ui.unit.Dp,
    currentUserProfile: UserProfile?
) {
    val avatar = billReceiverAvatarUrl(bill, currentUserProfile)
    if (!avatar.isNullOrBlank()) AvatarImage(
        avatarUrl = avatar,
        contentDescription = "${billReceiverName(bill, currentUserProfile)}的头像",
        modifier = Modifier.size(size).clip(CircleShape),
        contentScale = ContentScale.Crop
    )
    else Surface(Modifier.size(size), CircleShape, billIconBackground(bill.businessType, bill.direction == "INCOME")) {
        Box(contentAlignment = Alignment.Center) { Icon(billIcon(bill.businessType, bill.direction == "INCOME"), null, tint = billIconTint(bill.businessType, bill.direction == "INCOME")) }
    }
}

@Composable private fun DetailRow(label: String, value: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color(0xFF999999), modifier = Modifier.weight(1f)); value()
    }
}
@Composable private fun DetailTextRow(label: String, value: String) = DetailRow(label) {
    Text(value, modifier = Modifier.weight(1.9f), textAlign = TextAlign.Start, color = MilingTextPrimary)
}
@Composable private fun ManagementActionRow(label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f)); Text(value, color = MilingTextSecondary); Icon(Icons.Outlined.ChevronRight, null, tint = MilingTextMuted)
    }
}
@Composable private fun PrototypeAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier) {
    Row(modifier.heightIn(min = 64.dp).clickable(onClick = onClick).padding(8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Color(0xFF174A78)); Spacer(Modifier.width(8.dp)); Text(label, color = Color(0xFF174A78), style = MaterialTheme.typography.bodyMedium) }
}

@Composable
private fun WalletBillRow(
    bill: WalletBill,
    currentUserProfile: UserProfile?,
    onClick: () -> Unit = {}
) {
    val income = bill.direction == "INCOME"
    Row(
        Modifier.fillMaxWidth().heightIn(min = 82.dp).clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val avatar = billReceiverAvatarUrl(bill, currentUserProfile)
        if (!avatar.isNullOrBlank()) {
            AvatarImage(
                avatarUrl = avatar,
                contentDescription = "${billReceiverName(bill, currentUserProfile)}的头像",
                modifier = Modifier.size(44.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Surface(Modifier.size(44.dp), CircleShape, billIconBackground(bill.businessType, income)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(billIcon(bill.businessType, income), null, tint = billIconTint(bill.businessType, income))
                }
            }
        }
        Spacer(Modifier.width(MilingSpacing.Md))
        Column(Modifier.weight(1f)) {
            Text(billDisplayTitle(bill), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(formatOccurredAt(bill.occurredAt), color = MilingTextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                signedPlainAmount(bill),
                color = if (income) Color(0xFFF05A28) else Color(0xFF222222),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { contentDescription = (if (income) "收入" else "支出") + plainAmount(bill.amountCent) + "元" }
            )
            bill.balanceAfterCent?.let { Text("余额 ${plainAmount(it)}元", color = Color(0xFF999999), style = MaterialTheme.typography.bodySmall) }
            if (bill.status != "SUCCEEDED") Text(statusText(bill.status), color = MilingTextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PaymentPasswordScreen(
    submitting: Boolean, message: String?, allowSkip: Boolean, onBack: () -> Unit,
    onSkip: () -> Unit, onSubmit: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val mismatch = confirm.isNotEmpty() && password != confirm
    FinanceScaffold("设置支付密码", onBack) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = RoundedCornerShape(MilingRadii.Large),
                color = MilingPrimarySoft
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Lock, null, tint = MilingPrimary, modifier = Modifier.size(30.dp))
                }
            }
            Spacer(Modifier.height(MilingSpacing.Xl))
            Text(
                "设置独立支付密码",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(MilingSpacing.Sm))
            Text(
                "6 位数字，用于确认资金操作",
                style = MaterialTheme.typography.bodyLarge,
                color = MilingTextSecondary,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(MilingSpacing.Xxl))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(MilingRadii.Large),
            color = MilingSurfaceSubtle
        ) {
            Column(Modifier.padding(MilingSpacing.Lg)) {
                Text("设置密码", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(MilingSpacing.Lg))
                SecurePasswordField("支付密码", password) {
                    password = it.filter(Char::isDigit).take(6)
                }
                Spacer(Modifier.height(MilingSpacing.Md))
                SecurePasswordField("再次输入", confirm, isError = mismatch) {
                    confirm = it.filter(Char::isDigit).take(6)
                }
                if (mismatch) {
                    Text("两次输入的密码不一致", color = MilingError, modifier = Modifier.padding(top = MilingSpacing.Sm))
                }
            }
        }
        Spacer(Modifier.height(MilingSpacing.Md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.VerifiedUser, null, tint = MilingIconSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(MilingSpacing.Sm))
            Text("请勿使用生日或连续数字", style = MaterialTheme.typography.bodyMedium, color = MilingTextSecondary)
        }
        message?.let { ErrorText(it) }
        Spacer(Modifier.height(MilingSpacing.Xl))
        Button(
            onClick = { onSubmit(password) },
            enabled = password.length == 6 && password == confirm && !submitting,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(MilingRadii.Medium)
        ) {
            if (submitting) {
                CircularProgressIndicator(Modifier.size(20.dp), color = androidx.compose.ui.graphics.Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(MilingSpacing.Sm))
                Text("正在安全设置")
            } else Text("确认设置")
        }
        if (allowSkip) {
            TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("稍后设置") }
        }
    }
}

@Composable
private fun SecurePasswordField(
    label: String,
    value: String,
    isError: Boolean = false,
    change: (String) -> Unit
) {
    OutlinedTextField(
        value = value, onValueChange = change, label = { Text(label) },
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        visualTransformation = PasswordVisualTransformation(), singleLine = true,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$label，6 位数字安全输入" }
    )
}

@Composable
private fun RealNameScreen(
    state: FinanceUiState, onBack: () -> Unit,
    onEnter: () -> Unit,
    onSubmit: (String, String, ByteArray) -> Unit,
    onRetrySession: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var id by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<ByteArray?>(null) }
    var cameraOpen by rememberSaveable { mutableStateOf(false) }
    val idInvalid = id.isNotEmpty() && !ChineseIdNumber.isValid(id)
    LaunchedEffect(Unit) { onEnter() }
    if (cameraOpen) {
        FullScreenFaceCamera(
            onDismiss = { cameraOpen = false },
            onCaptured = {
                photo = it
                cameraOpen = false
            }
        )
        return
    }
    FinanceScaffold("实名认证", onBack) {
        Text("用于确认账户资金操作由你本人发起。原始身份证号和人脸照片不会在本机或服务端保存。", color = MilingTextSecondary)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(name, { name = it.take(64) }, label = { Text("姓名") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = id,
            onValueChange = { id = ChineseIdNumber.normalize(it) },
            label = { Text("身份证号") },
            isError = idInvalid,
            supportingText = { if (idInvalid) Text("请输入有效的 18 位身份证号") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(16.dp))
        FaceCaptureCard(
            captured = photo != null,
            onOpenCamera = { cameraOpen = true },
            onRetake = { photo = null; cameraOpen = true }
        )
        Text(if (photo == null) "请现场拍摄正面人脸" else "现场照片已采集，仅保留在内存中", color = if (photo == null) MilingTextSecondary else MilingSuccess)
        when (state.realNameCompletion) {
            RealNameCompletion.SynchronizingSession -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("实名认证完成，正在更新登录状态", color = MilingTextSecondary)
                }
            }
            is RealNameCompletion.SessionSyncFailed -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(MilingRadii.Medium),
                    color = MilingSuccessSoft
                ) {
                    Column(Modifier.padding(MilingSpacing.Lg)) {
                        Text("实名认证已完成", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(MilingSpacing.Xs))
                        Text("登录状态更新失败，暂时无法使用资金功能。请重试更新，无需重新提交资料。")
                        Spacer(Modifier.height(MilingSpacing.Md))
                        OutlinedButton(
                            onClick = onRetrySession,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            enabled = !state.submitting
                        ) { Text("重试更新") }
                    }
                }
            }
            else -> Unit
        }
        state.message?.let { ErrorText(it) }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { onSubmit(name.trim(), id.trim(), photo!!) },
            enabled = name.isNotBlank() && ChineseIdNumber.isValid(id) && photo != null &&
                !state.submitting && state.realNameCompletion == null,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text(if (state.submitting) "提交中…" else "同意并提交核验") }
    }
}

@Composable
private fun FaceCaptureCard(
    captured: Boolean,
    onOpenCamera: () -> Unit,
    onRetake: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MilingRadii.Medium),
        color = if (captured) MilingSuccessSoft else MilingSurfaceSubtle
    ) {
        if (captured) {
            Row(
                Modifier.padding(MilingSpacing.Xl),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.CheckCircle, null, tint = MilingSuccess, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(MilingSpacing.Md))
                Text("现场照片已采集", color = MilingSuccess, modifier = Modifier.weight(1f))
                TextButton(onClick = onRetake, modifier = Modifier.heightIn(min = 48.dp)) { Text("重新拍摄") }
            }
        } else {
            Column(
                Modifier.padding(MilingSpacing.Xl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Outlined.CameraAlt, null, tint = MilingPrimary, modifier = Modifier.size(36.dp))
                Spacer(Modifier.height(MilingSpacing.Md))
                Text("请现场拍摄正面人脸", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(MilingSpacing.Lg))
                OutlinedButton(onClick = onOpenCamera, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.CameraAlt, null)
                    Spacer(Modifier.width(MilingSpacing.Sm))
                    Text("拍摄现场照片")
                }
            }
        }
    }
}

@Composable
internal fun FullScreenFaceCamera(onDismiss: () -> Unit, onCaptured: (ByteArray) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var permissionDenied by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var cameraAvailable by remember { mutableStateOf(true) }
    var capturing by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        permissionDenied = !it
    }
    ImmersiveSystemBars()
    BackHandler(onBack = onDismiss)
    if (!granted) {
        Surface(
            modifier = Modifier.fillMaxSize(), color = Color.Black
        ) {
            Column(
                Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Outlined.CameraAlt, null, tint = Color.White, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(16.dp))
                Text("需要相机权限拍摄现场人脸照片", color = Color.White, style = MaterialTheme.typography.bodyLarge)
                if (permissionDenied) Text("相机权限未开启，请授权后重试", color = Color(0xFFFFB4AB))
                Spacer(Modifier.height(20.dp))
                Button(onClick = { permission.launch(Manifest.permission.CAMERA) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("授权相机") }
                TextButton(onClick = onDismiss) { Text("返回", color = Color.White) }
            }
        }
        return
    }
    val controller = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            setEnabledUseCases(LifecycleCameraController.IMAGE_CAPTURE)
        }
    }
    DisposableEffect(owner, controller) {
        runCatching { controller.bindToLifecycle(owner) }
            .onFailure {
                cameraAvailable = false
                cameraError = "无法启动前置摄像头，请确认设备摄像头可用"
            }
        onDispose { controller.unbind() }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (cameraAvailable) AndroidView(
            factory = { PreviewView(it).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE; scaleType = PreviewView.ScaleType.FILL_CENTER; this.controller = controller } },
            modifier = Modifier.fillMaxSize()
        )
        IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp).size(48.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回实名认证", tint = Color.White)
        }
        Surface(color = Color.Black.copy(alpha = .46f), shape = RoundedCornerShape(MilingRadii.Small), modifier = Modifier.align(Alignment.Center).padding(24.dp)) {
            Text("请正对镜头，保持面部清晰", color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
        }
        Button(onClick = {
            capturing = true
            cameraError = null
            controller.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    runCatching { image.toCompressedJpeg() }.onSuccess(onCaptured).onFailure { cameraError = "照片处理失败，请重新拍摄" }
                    image.close()
                    capturing = false
                }
                override fun onError(exception: ImageCaptureException) {
                    capturing = false
                    cameraError = "拍摄失败，请保持应用在前台后重试"
                }
            })
        },
            enabled = !capturing && cameraAvailable,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp).heightIn(min = 52.dp)
        ) { if (capturing) CircularProgressIndicator(Modifier.size(20.dp)) else Icon(Icons.Outlined.CameraAlt, null); Spacer(Modifier.width(8.dp)); Text(if (capturing) "正在拍摄…" else "拍摄") }
        cameraError?.let { Text(it, color = Color(0xFFFFB4AB), modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 92.dp, start = 24.dp, end = 24.dp)) }
    }
}

@Composable
internal fun TransferRecipientLookupScreen(
    state: FinanceUiState,
    onBack: () -> Unit,
    onResolve: (String) -> Unit,
    onClear: () -> Unit,
    onSelect: (TransferRecipientUi) -> Unit,
    onLoadFriends: () -> Unit = {}
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var query by rememberSaveable { mutableStateOf("") }
    var friendPickerOpen by rememberSaveable { mutableStateOf(false) }
    val normalizedQuery = query.trim()
    val mobileValid = normalizedQuery.matches(Regex("^1[3-9]\\d{9}$"))
    val result = state.recipientLookupResult
    val friendMatches = remember(normalizedQuery, state.transferFriends) {
        if (normalizedQuery.isBlank() || normalizedQuery.all(Char::isDigit)) {
            emptyList()
        } else {
            state.transferFriends.filter { friend ->
                friend.display.contains(normalizedQuery, ignoreCase = true) ||
                    friend.nickname.contains(normalizedQuery, ignoreCase = true)
            }
        }
    }

    LaunchedEffect(Unit) {
        onClear()
        onLoadFriends()
        focusRequester.requestFocus()
        keyboard?.show()
    }
    LaunchedEffect(result) {
        if (result != null) keyboard?.hide()
    }

    if (friendPickerOpen) {
        TransferFriendPickerScreen(
            state = state,
            onBack = { friendPickerOpen = false },
            onRetry = onLoadFriends,
            onSelect = {
                friendPickerOpen = false
                onSelect(it)
            }
        )
        return
    }

    Column(
        Modifier.fillMaxSize().background(Color(0xFFF7F7F8))
            .statusBarsPadding().navigationBarsPadding()
    ) {
        Box(Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 8.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).size(48.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回")
            }
        }
        Spacer(Modifier.height(94.dp))
        Text(
            "填写对方 MiniPay 账户",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(22.dp))
        Text(
            "可输入好友昵称或手机号，转账成功后不可撤回",
            style = MaterialTheme.typography.bodyLarge,
            color = MilingTextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(62.dp))
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp)
                .background(MilingSurface)
                .border(width = 0.5.dp, color = MilingDivider)
                .padding(start = 24.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("对方账户", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(24.dp))
            BasicTextField(
                value = query,
                onValueChange = {
                    query = it.take(40)
                    onClear()
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(color = MilingTextPrimary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    if (mobileValid && !state.recipientLookupLoading) onResolve(normalizedQuery)
                }),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth()) {
                        if (query.isBlank()) {
                            Text("手机号或好友昵称", color = MilingTextMuted, style = MaterialTheme.typography.titleMedium)
                        }
                        inner()
                    }
                },
                modifier = Modifier.weight(1f).focusRequester(focusRequester)
                    .semantics { contentDescription = "输入收款人手机号" }
            )
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = {
                        query = ""
                        onClear()
                        focusRequester.requestFocus()
                        keyboard?.show()
                    },
                    modifier = Modifier.size(48.dp)
                ) { Icon(Icons.Outlined.Cancel, "清除", tint = MilingTextMuted) }
            }
            IconButton(
                onClick = {
                    keyboard?.hide()
                    friendPickerOpen = true
                },
                modifier = Modifier.size(48.dp).semantics { contentDescription = "打开好友通讯录" }
            ) { Icon(Icons.Outlined.Contacts, null, tint = MilingPrimary) }
        }

        state.recipientLookupError?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp)
            )
        }
        when {
            result != null -> TransferRecipientResultCard(result, onSelect)
            normalizedQuery.isNotBlank() && !normalizedQuery.all(Char::isDigit) -> {
                if (state.transferFriendsLoading) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                    }
                } else if (friendMatches.isEmpty()) {
                    Text(
                        "未找到匹配的好友",
                        color = MilingTextSecondary,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(28.dp)
                    )
                } else {
                    LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                        items(friendMatches, key = { it.receiverUserId }) { friend ->
                            TransferFriendRow(friend = friend, onClick = { onSelect(friend) })
                        }
                    }
                }
            }
            else -> {
                Spacer(Modifier.height(34.dp))
                Button(
                    onClick = { keyboard?.hide(); onResolve(normalizedQuery) },
                    enabled = mobileValid && !state.recipientLookupLoading,
                    shape = RoundedCornerShape(30.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(58.dp)
                ) {
                    if (state.recipientLookupLoading) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                    } else Text("确认", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.align(Alignment.CenterHorizontally).padding(bottom = 32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.VerifiedUser, null, tint = MilingTextMuted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("转账保障中", color = MilingTextMuted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun TransferFriendPickerScreen(
    state: FinanceUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSelect: (TransferRecipientUi) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val friends = remember(query, state.transferFriends) {
        val keyword = query.trim()
        if (keyword.isBlank()) state.transferFriends else state.transferFriends.filter {
            it.display.contains(keyword, ignoreCase = true) || it.nickname.contains(keyword, ignoreCase = true)
        }
    }
    Column(
        Modifier.fillMaxSize().background(Color(0xFFF7F7F8))
            .statusBarsPadding().navigationBarsPadding()
    ) {
        Box(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回")
            }
            Text("选择好友", style = MaterialTheme.typography.titleLarge, modifier = Modifier.align(Alignment.Center))
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(40) },
            placeholder = { Text("搜索昵称或备注名") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                .semantics { contentDescription = "搜索好友" }
        )
        when {
            state.transferFriendsLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.transferFriendsError != null -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(state.transferFriendsError, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onRetry) { Text("重试") }
            }
            friends.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (query.isBlank()) "暂无好友" else "未找到匹配的好友", color = MilingTextSecondary)
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(friends, key = { it.receiverUserId }) { friend ->
                    TransferFriendRow(friend = friend, onClick = { onSelect(friend) })
                    HorizontalDivider(color = MilingDivider)
                }
            }
        }
    }
}

@Composable
private fun TransferFriendRow(friend: TransferRecipientUi, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp)
            .semantics { contentDescription = "选择好友${friend.display}" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(
            name = friend.display,
            avatarUrl = friend.avatarUrl,
            colorIndex = friend.receiverUserId.hashCode(),
            size = 52.dp,
            shape = RoundedCornerShape(10.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(friend.display, style = MaterialTheme.typography.titleMedium)
            friend.accountMasked?.takeIf(String::isNotBlank)?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MilingTextSecondary)
            }
        }
        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = MilingTextMuted)
    }
}

@Composable
private fun LegacyTransferRecipientLookupScreen(
    state: FinanceUiState,
    onBack: () -> Unit,
    onResolve: (String) -> Unit,
    onClear: () -> Unit,
    onSelect: (TransferRecipientUi) -> Unit
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var mobile by remember { mutableStateOf("") }
    val mobileValid = mobile.matches(Regex("^1[3-9]\\d{9}$"))
    val result = state.recipientLookupResult

    LaunchedEffect(Unit) {
        onClear()
        focusRequester.requestFocus()
        keyboard?.show()
    }
    LaunchedEffect(result) {
        if (result != null) keyboard?.hide()
    }

    Column(
        Modifier.fillMaxSize().background(Color(0xFFF7F7F8))
            .statusBarsPadding().navigationBarsPadding()
    ) {
        Box(Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 8.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).size(48.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回")
            }
            IconButton(
                onClick = { Toast.makeText(context, "客服功能暂未开放", Toast.LENGTH_SHORT).show() },
                modifier = Modifier.align(Alignment.CenterEnd).size(48.dp)
                    .semantics { contentDescription = "联系客服" }
            ) { Icon(Icons.Outlined.HeadsetMic, null) }
        }
        Spacer(Modifier.height(94.dp))
        Text(
            "填写对方 MiniPay 账户",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(22.dp))
        Text(
            "请确认对方账户信息，转账成功后不可撤回",
            style = MaterialTheme.typography.bodyLarge,
            color = MilingTextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(62.dp))
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp)
                .background(MilingSurface)
                .border(width = 0.5.dp, color = MilingDivider)
                .padding(start = 24.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("对方账户", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(24.dp))
            BasicTextField(
                value = mobile,
                onValueChange = { value ->
                    mobile = value.filter(Char::isDigit).take(11)
                    onClear()
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(color = MilingTextPrimary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (mobileValid && !state.recipientLookupLoading) onResolve(mobile)
                }),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth()) {
                        if (mobile.isBlank()) Text("手机号", color = MilingTextMuted, style = MaterialTheme.typography.titleMedium)
                        inner()
                    }
                },
                modifier = Modifier.weight(1f).focusRequester(focusRequester)
                    .semantics { contentDescription = "输入收款人手机号" }
            )
            if (mobile.isNotEmpty()) {
                IconButton(
                    onClick = { mobile = ""; onClear(); focusRequester.requestFocus(); keyboard?.show() },
                    modifier = Modifier.size(48.dp)
                ) { Icon(Icons.Outlined.Cancel, "清除手机号", tint = MilingTextMuted) }
            } else {
                IconButton(
                    onClick = { Toast.makeText(context, "好友通讯录暂未开放", Toast.LENGTH_SHORT).show() },
                    modifier = Modifier.size(48.dp).semantics { contentDescription = "打开好友通讯录" }
                ) { Icon(Icons.Outlined.Contacts, null, tint = MilingPrimary) }
            }
        }

        state.recipientLookupError?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp)
                    .semantics { contentDescription = "收款人查询失败：$it" }
            )
        }
        if (result != null) {
            TransferRecipientResultCard(result, onSelect)
        } else {
            Spacer(Modifier.height(34.dp))
            Button(
                onClick = { keyboard?.hide(); onResolve(mobile) },
                enabled = mobileValid && !state.recipientLookupLoading,
                shape = RoundedCornerShape(30.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(58.dp)
            ) {
                if (state.recipientLookupLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp).semantics { contentDescription = "正在查询收款人" },
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else Text("确认", style = MaterialTheme.typography.titleLarge)
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.align(Alignment.CenterHorizontally).padding(bottom = 32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.VerifiedUser, null, tint = MilingTextMuted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("转账保障中", color = MilingTextMuted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun TransferRecipientResultCard(
    recipient: TransferRecipientUi,
    onSelect: (TransferRecipientUi) -> Unit
) {
    Surface(
        color = MilingSurface,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp).fillMaxWidth()
            .clickable { onSelect(recipient) }
            .semantics { contentDescription = "选择收款人 ${recipient.display}" }
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (recipient.avatarUrl == null) {
                Surface(Modifier.size(56.dp), RoundedCornerShape(10.dp), MilingPrimarySoft) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Person, "收款人默认头像", tint = MilingPrimary, modifier = Modifier.size(30.dp))
                    }
                }
            } else {
                AvatarImage(
                    avatarUrl = recipient.avatarUrl,
                    contentDescription = "${recipient.nickname}的头像",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp))
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (recipient.legalNameMasked.isNullOrBlank()) "${recipient.nickname}（未实名）" else recipient.nickname,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    recipient.legalNameMasked?.let { "实名：$it" } ?: "该账户暂未实名认证",
                    color = MilingTextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                recipient.accountMasked?.let {
                    Text(it, color = MilingPrimary, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = MilingIconSecondary)
        }
    }
}

@Composable
internal fun ScanScreen(
    state: FinanceUiState,
    onBack: () -> Unit,
    onOpenReceiveCode: () -> Unit,
    onEnter: () -> Unit = {},
    resolve: (String, (String) -> Unit) -> Unit
) {
    val context = LocalContext.current
    var handled by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var torchEnabled by remember { mutableStateOf(false) }
    var toggleTorch by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    LaunchedEffect(Unit) { onEnter() }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null || handled) return@rememberLauncherForActivityResult
        error = null
        val scanner = BarcodeScanning.getClient(miniPayQrScannerOptions)
        runCatching { InputImage.fromFilePath(context, uri) }
            .onSuccess { image ->
                scanner.process(image)
                    .addOnSuccessListener { codes ->
                        val raw = codes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }
                            ?.rawValue?.trim()
                        if (raw.isNullOrBlank()) error = "未在这张图片中识别到二维码，请换一张图片重试"
                        else if (!handled) {
                            handled = true
                            resolve(raw) { message -> handled = false; error = message }
                        }
                    }
                    .addOnFailureListener { error = "图片二维码识别失败，请重试" }
                    .addOnCompleteListener { scanner.close() }
            }
            .onFailure { scanner.close(); error = "无法读取所选图片，请重新选择" }
    }
    ImmersiveSystemBars()
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        QrCameraScanner(
            onCode = { value ->
                if (!handled) {
                    handled = true
                    resolve(value) { message -> handled = false; error = message }
                }
            },
            onError = { error = it },
            onTorchReady = { toggleTorch = it }
        )
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp).size(48.dp)
        ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回", tint = Color.White) }
        Column(
            modifier = Modifier.align(Alignment.Center).offset(y = (-18).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedScanFrame(272.dp)
            Spacer(Modifier.height(22.dp))
            Text("将二维码或条码放入框内", color = Color.White, style = MaterialTheme.typography.bodyLarge)
        }
        IconButton(
            onClick = {
                torchEnabled = !torchEnabled
                toggleTorch?.invoke(torchEnabled)
            },
            modifier = Modifier.align(Alignment.Center).offset(y = 193.dp).size(52.dp)
        ) { Icon(Icons.Outlined.FlashlightOn, "轻触照亮", tint = Color.White) }
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 32.dp, vertical = 28.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ScanAction(Icons.Outlined.QrCode2, "收付款", "打开个人收款码", onOpenReceiveCode)
            ScanAction(Icons.Outlined.PhotoLibrary, "相册", "从相册识别二维码") { gallery.launch("image/*") }
        }
        (error ?: state.message)?.let {
            Surface(
                color = Color.Black.copy(alpha = .7f), shape = RoundedCornerShape(12.dp),
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 104.dp, start = 24.dp, end = 24.dp)
            ) { Text(it, color = Color.White, modifier = Modifier.padding(12.dp), textAlign = TextAlign.Center) }
        }
    }
}

@Composable
private fun AnimatedScanFrame(frameSize: androidx.compose.ui.unit.Dp) {
    val transition = rememberInfiniteTransition(label = "scan_frame")
    val beamProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(
                1800,
                easing = androidx.compose.animation.core.LinearEasing
            ),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "scan_beam"
    )
    val blue = Color(0xFF2878FF)
    Canvas(
        modifier = Modifier
            .size(frameSize)
            .semantics { contentDescription = "动态扫码框" }
    ) {
        val corner = this.size.minDimension * .22f
        val stroke = 4.dp.toPx()
        val radius = 25.dp.toPx()
        val width = this.size.width
        val height = this.size.height
        fun segment(fromX: Float, fromY: Float, toX: Float, toY: Float) = drawLine(
            blue,
            androidx.compose.ui.geometry.Offset(fromX, fromY),
            androidx.compose.ui.geometry.Offset(toX, toY),
            stroke
        )
        val cornerStroke = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
        drawArc(blue, 180f, 90f, false, androidx.compose.ui.geometry.Offset.Zero, androidx.compose.ui.geometry.Size(radius * 2, radius * 2), style = cornerStroke)
        drawArc(blue, 270f, 90f, false, androidx.compose.ui.geometry.Offset(width - radius * 2, 0f), androidx.compose.ui.geometry.Size(radius * 2, radius * 2), style = cornerStroke)
        drawArc(blue, 0f, 90f, false, androidx.compose.ui.geometry.Offset(width - radius * 2, height - radius * 2), androidx.compose.ui.geometry.Size(radius * 2, radius * 2), style = cornerStroke)
        drawArc(blue, 90f, 90f, false, androidx.compose.ui.geometry.Offset.Zero.copy(y = height - radius * 2), androidx.compose.ui.geometry.Size(radius * 2, radius * 2), style = cornerStroke)
        // The original scan reference has four open rounded corners, never a solid rectangle.
        segment(radius, 0f, corner, 0f); segment(0f, radius, 0f, corner)
        segment(width - corner, 0f, width - radius, 0f); segment(width, radius, width, corner)
        segment(radius, height, corner, height); segment(0f, height - radius, 0f, height - corner)
        segment(width - corner, height, width - radius, height); segment(width, height - radius, width, height - corner)
        val y = height * beamProgress
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                .42f to blue.copy(alpha = .08f),
                .5f to blue.copy(alpha = .78f),
                .58f to blue.copy(alpha = .08f),
                1f to Color.Transparent,
                startY = y - 26.dp.toPx(),
                endY = y + 26.dp.toPx()
            ),
            topLeft = androidx.compose.ui.geometry.Offset(8.dp.toPx(), y - 26.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(width - 16.dp.toPx(), 52.dp.toPx())
        )
        drawLine(
            color = blue.copy(alpha = .9f),
            start = androidx.compose.ui.geometry.Offset(10.dp.toPx(), y),
            end = androidx.compose.ui.geometry.Offset(width - 10.dp.toPx(), y),
            strokeWidth = 1.5.dp.toPx()
        )
    }
}

@Composable
private fun ScanAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, description: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).semantics { contentDescription = description }.padding(4.dp)) {
        Surface(shape = RoundedCornerShape(14.dp), color = Color.Black.copy(alpha = .36f), modifier = Modifier.size(52.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.bodyMedium)
    }
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
private fun QrCameraScanner(
    onCode: (String) -> Unit,
    onError: (String) -> Unit,
    onTorchReady: ((Boolean) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var cameraAvailable by remember { mutableStateOf(true) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    if (!granted) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("需要相机权限才能扫码", color = Color.White)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text("授权相机") }
        }
        return
    }
    val scanner = remember { BarcodeScanning.getClient(miniPayQrScannerOptions) }
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(LifecycleCameraController.IMAGE_ANALYSIS)
            setImageAnalysisAnalyzer(ContextCompat.getMainExecutor(context)) { proxy ->
                val media = proxy.image
                if (media == null) { proxy.close(); return@setImageAnalysisAnalyzer }
                scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                    .addOnSuccessListener { codes ->
                        val raw = codes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }
                            ?.rawValue?.trim()
                        if (!raw.isNullOrBlank()) onCode(raw)
                    }
                    .addOnCompleteListener { proxy.close() }
            }
        }
    }
    LaunchedEffect(controller) { onTorchReady { enabled -> controller.enableTorch(enabled) } }
    DisposableEffect(owner, controller) {
        runCatching { controller.bindToLifecycle(owner) }.onFailure {
            cameraAvailable = false
            onError("无法启动摄像头，请确认设备摄像头可用")
        }
        onDispose {
            onTorchReady({})
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            scanner.close()
        }
    }
    if (cameraAvailable) AndroidView(
        factory = { PreviewView(it).apply { scaleType = PreviewView.ScaleType.FILL_CENTER; this.controller = controller } },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun ImmersiveSystemBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (window != null) {
                WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

private fun ImageProxy.toCompressedJpeg(): ByteArray {
    val buffer = planes.firstOrNull()?.buffer ?: error("Camera returned no image data")
    val bytes = ByteArray(buffer.remaining()).also(buffer::get)
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?: error("Camera image could not be decoded")
    val bitmap = if (decoded.width > 1280) Bitmap.createScaledBitmap(
        decoded, 1280, decoded.height * 1280 / decoded.width, true
    ).also { decoded.recycle() } else decoded
    return ByteArrayOutputStream().use { output ->
        check(bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output))
        bitmap.recycle()
        output.toByteArray()
    }
}

@Composable
internal fun CardsScreen(
    state: FinanceUiState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onSelect: (String) -> Unit,
    onRetry: () -> Unit = {}
) = FinanceScaffold("银行卡", onBack) {
    val activeCards = activeBankCards(state.cards)
    if (activeCards.isEmpty()) {
        EmptyState("尚未绑定银行卡\n绑定银行卡后可用于充值、提现和沙箱银行查询")
    }
    activeCards.forEach { card ->
        BankCardVisual(
            card = card,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            onClick = { onSelect(card.cardId) }
        )
    }
    state.cardsError?.let {
        ErrorText(it)
        TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text("重新加载") }
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        onClick = onAdd,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
    ) {
        Icon(Icons.Outlined.Add, null)
        Spacer(Modifier.width(8.dp))
        Text("添加银行卡")
    }
}

@Composable
internal fun AddCardScreen(
    state: FinanceUiState,
    onBack: () -> Unit,
    bind: (String, String, String, () -> Unit) -> Unit,
    onBound: () -> Unit
) = FinanceScaffold("添加银行卡", onBack) {
    var holder by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    val invalidCardNumber = number.isNotEmpty() && !BankCardNumber.isValid(number)
    Text("银行卡号只用于沙箱银行令牌化，不会以完整卡号保存。", color = MilingTextSecondary)
    Spacer(Modifier.height(20.dp))
    OutlinedTextField(holder, { holder = it.take(64) }, label = { Text("持卡人姓名") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = number,
        onValueChange = { number = BankCardNumber.normalize(it) },
        label = { Text("银行卡号") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = invalidCardNumber,
        supportingText = {
            if (invalidCardNumber) Text("请输入有效银行卡号")
            else Text("支持 16–19 位银行卡号")
        }
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, label = { Text("沙箱验证码") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true)
    Text("当前演示环境固定为 123456，不会发送真实短信。", color = MilingTextSecondary, style = MaterialTheme.typography.bodyMedium)
    state.message?.let { ErrorText(it) }
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = { bind(holder.trim(), number, code, onBound) },
        enabled = holder.isNotBlank() && BankCardNumber.isValid(number) && code.length == 6 && !state.submitting,
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) { Text(if (state.submitting) "绑定中…" else "绑定银行卡") }
}

internal fun activeBankCards(cards: List<BankCard>): List<BankCard> =
    cards.filter { it.status == "ACTIVE" }

private val miniPayQrScannerOptions = BarcodeScannerOptions.Builder()
    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
    .build()

/** The client accepts only local MiniPay personal-collection links before a server resolve. */
internal fun isPersonalCollectionDeepLink(value: String): Boolean = runCatching {
    parseMiniPayQrCode(value) is MiniPayQrCode.PersonalCollection
}.getOrDefault(false)

@Composable
private fun CardDetailScreen(
    state: FinanceUiState,
    cardId: String,
    onBack: () -> Unit,
    load: (String) -> Unit,
    open: (FinanceDestination) -> Unit,
    disable: (String) -> Unit
) {
    var confirmDisable by remember { mutableStateOf(false) }
    LaunchedEffect(cardId) { load(cardId) }
    FinanceScaffold("银行卡详情", onBack) {
        val card = state.selectedCard?.takeIf { it.cardId == cardId }
            ?: state.cards.firstOrNull { it.cardId == cardId }
        if (card == null && state.bankDataLoading) {
            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@FinanceScaffold
        }
        if (card == null) {
            state.message?.let { ErrorText(it) } ?: EmptyState("银行卡暂不可用")
            return@FinanceScaffold
        }
        BankCardVisual(card, Modifier.fillMaxWidth(), null)
        Spacer(Modifier.height(24.dp))
        Surface(shape = RoundedCornerShape(MilingRadii.Large), color = MilingSurfaceSubtle) {
            Column(Modifier.fillMaxWidth()) {
                CardActionRow("余额查询", Icons.Outlined.AccountBalanceWallet) {
                    open(FinanceDestination.CARD_BALANCE)
                }
                HorizontalDivider(color = MilingDivider)
                CardActionRow("交易明细", Icons.AutoMirrored.Outlined.ReceiptLong) {
                    open(FinanceDestination.CARD_TRANSACTIONS)
                }
                HorizontalDivider(color = MilingDivider)
                CardActionRow("使用此卡充值", Icons.Outlined.AddCard) {
                    open(FinanceDestination.RECHARGE)
                }
                HorizontalDivider(color = MilingDivider)
                CardActionRow("提现到此卡", Icons.Outlined.Savings) {
                    open(FinanceDestination.WITHDRAWAL)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("支付限额", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        val limits = state.bankPaymentLimits
        if (limits != null) {
            LimitRow("单笔限额", money(limits.singlePaymentLimitCent))
            LimitRow("当日限额", money(limits.dailyPaymentLimitCent))
            LimitRow("当日剩余", money(limits.dailyRemainingCent))
        } else if (state.bankDataLoading) {
            CircularProgressIndicator(Modifier.size(24.dp))
        }
        state.message?.let { ErrorText(it) }
        Spacer(Modifier.height(28.dp))
        TextButton(
            onClick = { confirmDisable = true },
            enabled = !state.submitting,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) { Text("解除绑定", color = MilingError) }
    }
    if (confirmDisable) AlertDialog(
        onDismissRequest = { confirmDisable = false },
        title = { Text("解除绑定银行卡？") },
        text = { Text("解除后该卡不能继续用于充值、提现或查询。已有交易记录仍会保留。") },
        confirmButton = {
            TextButton(onClick = { confirmDisable = false; disable(cardId) }) {
                Text("解除绑定", color = MilingError)
            }
        },
        dismissButton = { TextButton(onClick = { confirmDisable = false }) { Text("取消") } }
    )
}

@Composable
internal fun BankBalanceScreen(
    state: FinanceUiState,
    cardId: String,
    onBack: () -> Unit,
    query: (String, String) -> Unit
) {
    var password by remember(cardId) { mutableStateOf("") }
    var showPasswordPanel by remember(cardId) { mutableStateOf(state.bankBalance == null) }
    val card = state.selectedCard ?: state.cards.firstOrNull { it.cardId == cardId }
    LaunchedEffect(state.bankBalance) {
        if (state.bankBalance != null) {
            password = ""
            showPasswordPanel = false
        }
    }
    LaunchedEffect(showPasswordPanel, state.submitting, state.message) {
        if (showPasswordPanel && !state.submitting && state.message != null) password = ""
    }
    Box(Modifier.fillMaxSize()) {
        FinanceScaffold("余额查询", onBack) {
            card?.let { BankCardVisual(it, Modifier.fillMaxWidth(), null) }
            Spacer(Modifier.height(24.dp))
            state.bankBalance?.let { balance ->
                Text("银行卡可用余额", color = MilingTextSecondary)
                Text(
                    money(balance.availableAmountCent),
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.semantics {
                        contentDescription = "银行卡可用余额 ${money(balance.availableAmountCent)}"
                    }
                )
                Text(balance.sandboxNotice, color = MilingTextSecondary, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(24.dp))
                OutlinedButton(
                    onClick = { password = ""; showPasswordPanel = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) { Text("重新验证查询") }
            }
            if (state.bankBalance == null && !showPasswordPanel) {
                Text("每次查询都需要验证支付密码", color = MilingTextSecondary)
                Spacer(Modifier.height(16.dp))
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { password = ""; showPasswordPanel = true },
                    enabled = !state.submitting,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("验证支付密码")
                }
            }
        }
        if (showPasswordPanel && card != null) {
            PaymentPasswordSheet(
                purpose = "查询银行卡余额",
                counterparty = "${card.bankName}${card.maskedCardNo}",
                password = password,
                busy = state.submitting,
                error = state.message,
                onClose = { password = ""; onBack() },
                onDigit = { digit ->
                    if (state.submitting || password.length >= 6) return@PaymentPasswordSheet
                    password += digit
                    if (password.length == 6) query(cardId, password)
                },
                onDelete = { if (!state.submitting) password = password.dropLast(1) }
            )
        }
    }
}

@Composable
private fun BankTransactionsScreen(
    state: FinanceUiState,
    cardId: String,
    onBack: () -> Unit,
    load: (String, String, String, Boolean) -> Unit
) {
    var month by remember { mutableStateOf(YearMonth.now(ZoneOffset.UTC)) }
    val from = month.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC).toString()
    val to = month.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC).toString()
    LaunchedEffect(cardId, month) { load(cardId, from, to, true) }
    Column(Modifier.fillMaxSize().background(MilingSurface).statusBarsPadding().navigationBarsPadding()) {
        FinanceTopBar("交易明细", onBack)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { month = month.minusMonths(1) }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "上个月")
            }
            Text(month.format(DateTimeFormatter.ofPattern("yyyy年M月")), style = MaterialTheme.typography.titleMedium)
            IconButton(
                onClick = { month = month.plusMonths(1) },
                enabled = month < YearMonth.now(ZoneOffset.UTC),
                modifier = Modifier.size(48.dp)
            ) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, "下个月") }
        }
        if (state.bankDataLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (state.message != null && state.bankTransactions.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ErrorText(state.message)
                Button(onClick = { load(cardId, from, to, true) }) { Text("重试") }
            }
        } else if (state.bankTransactions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { EmptyState("本月暂无交易") }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                items(state.bankTransactions, key = { it.transactionId }) { transaction ->
                    BankTransactionRow(transaction)
                    HorizontalDivider(color = MilingDivider)
                }
                state.message?.let { message ->
                    item(key = "load-error") {
                        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                            ErrorText(message)
                            TextButton(onClick = { load(cardId, from, to, false) }) {
                                Text("重试加载")
                            }
                        }
                    }
                }
                if (state.bankTransactions.size < state.bankTransactionTotal) {
                    item(key = "load-more") {
                        TextButton(
                            onClick = { load(cardId, from, to, false) },
                            enabled = !state.bankTransactionsLoadingMore,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) {
                            if (state.bankTransactionsLoadingMore) CircularProgressIndicator(Modifier.size(20.dp))
                            else Text("加载更多")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegacyReceiveScreen(state: FinanceUiState, onBack: () -> Unit, load: () -> Unit) {
    LaunchedEffect(Unit) { load() }
    FinanceScaffold("个人收款码", onBack) {
        state.collectionCode?.let {
            Surface(shape = RoundedCornerShape(20.dp), color = MilingSurfaceSubtle) { Text(it.deepLink, modifier = Modifier.padding(24.dp)) }
            Text("有效期至 ${it.expiresAt}", color = MilingTextSecondary, modifier = Modifier.padding(top = 12.dp))
        } ?: EmptyState("正在生成收款码…")
    }
}

internal fun collectionRecipientDisplayName(profile: UserProfile?): String? {
    val nickname = profile?.nickname?.trim().orEmpty()
    if (nickname.isBlank()) return null
    val maskedName = profile?.legalNameMasked?.trim().orEmpty()
    return if (maskedName.isBlank()) nickname else "$nickname（$maskedName）"
}

internal fun collectionCodeRefreshDelayMillis(
    expiresAt: Instant,
    nowMillis: Long = System.currentTimeMillis()
): Long = (expiresAt.toEpochMilli() - nowMillis - 60_000L).coerceAtLeast(0L)

@Composable
internal fun ReceiveScreen(
    state: FinanceUiState,
    onBack: () -> Unit,
    load: () -> Unit,
    loadRecords: () -> Unit,
    observeReceipts: () -> Unit,
    onReceiptSpeechEnabledChange: (Boolean) -> Unit = {},
    onRecords: () -> Unit,
    onMerchantReceive: () -> Unit = {}
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestLoad by rememberUpdatedState(load)
    LaunchedEffect(Unit) {
        load()
        loadRecords()
        observeReceipts()
    }
    LaunchedEffect(state.collectionCode?.expiresAt) {
        val expiry = state.collectionCode?.expiresAt
            ?.let { runCatching { Instant.parse(it) }.getOrNull() }
            ?: return@LaunchedEffect
        val refreshDelayMs = collectionCodeRefreshDelayMillis(expiry)
        delay(refreshDelayMs)
        load()
    }
    DisposableEffect(lifecycleOwner, state.collectionCode?.expiresAt) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            val expiresAt = state.collectionCode?.expiresAt
                ?.let { runCatching { Instant.parse(it) }.getOrNull() }
                ?: return@LifecycleEventObserver
            if (expiresAt.isBefore(Instant.now().plusSeconds(90))) latestLoad()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val orange = Color(0xFFFF9D1C)
    val receipts = state.bills.filter {
        it.direction == "INCOME" && it.source == "PERSONAL_COLLECTION_CODE"
    }
    val todayTotal = receipts.sumOf { it.amountCent }
    Box(Modifier.fillMaxSize().background(orange).statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().height(64.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回", tint = Color.White)
                }
                Text("收钱", color = Color.White, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(14.dp))
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(horizontal = 22.dp, vertical = 18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CurrencyYen, null, tint = Color(0xFF272727), modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(14.dp))
                        Text("个人收钱", style = MaterialTheme.typography.titleLarge, color = Color(0xFF252525))
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Outlined.ExpandLess, null, tint = Color(0xFF969696))
                    }
                    HorizontalDivider(Modifier.padding(top = 16.dp), color = Color(0xFFF0F0F0))
                    val code = state.collectionCode
                    val recipientName = collectionRecipientDisplayName(state.collectionRecipient)
                    if (code == null && state.collectionCodeLoading) {
                        Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                color = Color(0xFF2878FF),
                                modifier = Modifier.semantics { contentDescription = "正在加载收款码" }
                            )
                        }
                    } else if (code == null) {
                        Column(
                            modifier = Modifier.fillMaxWidth().height(300.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                state.collectionCodeError ?: "收款码暂时无法加载",
                                color = Color(0xFFD14343),
                                textAlign = TextAlign.Center
                            )
                            TextButton(
                                onClick = load,
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) { Text("重新加载") }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.VerifiedUser,
                                contentDescription = "收款保障",
                                tint = Color(0xFF929292),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                "你敢收 我敢赔",
                                color = Color(0xFF8A8A8A),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val profile = state.collectionRecipient
                            UserAvatar(
                                name = profile?.nickname?.ifBlank { "收款人" } ?: "收款人",
                                avatarUrl = profile?.usableAvatarUrl(),
                                colorIndex = profile?.userId?.hashCode() ?: 0,
                                size = 44.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                recipientName ?: "个人收款码",
                                color = Color(0xFF242424),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        CollectionQrCode(code.deepLink, Modifier.align(Alignment.CenterHorizontally).padding(vertical = 18.dp))
                    }
                    HorizontalDivider(color = Color(0xFFF0F0F0))
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.VolumeUp,
                            contentDescription = null,
                            tint = Color(0xFF343434),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("到账语音提醒", style = MaterialTheme.typography.titleMedium, color = Color(0xFF303030))
                            Text("后台或锁屏时播报本人的收款金额", style = MaterialTheme.typography.bodySmall, color = Color(0xFF777777))
                        }
                        Switch(
                            checked = state.receiptSpeechEnabled,
                            onCheckedChange = onReceiptSpeechEnabledChange,
                            modifier = Modifier.semantics {
                                contentDescription = if (state.receiptSpeechEnabled) "关闭到账语音提醒" else "开启到账语音提醒"
                            }
                        )
                    }
                    HorizontalDivider(color = Color(0xFFF0F0F0))
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onRecords),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.ReceiptLong, null, tint = Color(0xFF343434), modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(16.dp))
                        Text("收款记录", style = MaterialTheme.typography.titleMedium, color = Color(0xFF303030))
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, "查看收款记录", tint = Color(0xFFB0B0B0))
                    }
                    if (receipts.isNotEmpty()) {
                        HorizontalDivider(color = Color(0xFFF0F0F0))
                        Text(
                            "今日已收${receipts.size}笔 | 共${money(todayTotal)}",
                            color = Color(0xFF555555),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 14.dp, bottom = 2.dp)
                        )
                    }
                    state.collectionRecordsError?.let { error ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                error,
                                color = Color(0xFFD14343),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = loadRecords,
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) { Text("重试") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp).clickable(onClick = onMerchantReceive)
            ) {
                Row(Modifier.padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Storefront, null, tint = Color(0xFF343434), modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("经营收钱", style = MaterialTheme.typography.titleLarge, color = Color(0xFF303030))
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, "进入经营收钱", tint = Color(0xFF8E8E8E))
                }
            }
        }
        state.message?.takeIf { it.startsWith("收款到账") }?.let { notice ->
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 80.dp, start = 24.dp, end = 24.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF1D1D1D).copy(alpha = .88f),
                shadowElevation = 8.dp
            ) { Text(notice, color = Color.White, modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) }
        }
    }
}

@Composable
private fun CollectionQrCode(value: String, modifier: Modifier = Modifier) {
    val bitmap = remember(value) {
        val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 560, 560)
        Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).also { image ->
            for (x in 0 until matrix.width) for (y in 0 until matrix.height) {
                image.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }.asImageBitmap()
    }
    Image(
        bitmap = bitmap,
        contentDescription = "个人收款二维码",
        modifier = modifier.size(236.dp)
    )
}

@Composable
private fun MerchantCollectionPlaceholder(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color(0xFFFF9D1C)).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回", tint = Color.White) }
            Text("经营收钱", color = Color.White, style = MaterialTheme.typography.titleLarge)
        }
        Surface(
            modifier = Modifier.padding(18.dp).fillMaxWidth(),
            shape = RoundedCornerShape(28.dp), color = Color.White
        ) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Storefront, null, tint = Color(0xFF2878FF), modifier = Modifier.size(46.dp))
                Spacer(Modifier.height(18.dp))
                Text("经营收钱即将上线", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

internal enum class ReceiptRecordFilter(val label: String) {
    ALL("全部"), PERSONAL("收钱码"), MERCHANT("经营码")
}

@Composable
internal fun ReceiptRecordsScreen(
    state: FinanceUiState,
    onBack: () -> Unit,
    load: (Boolean) -> Unit,
    onBillClick: (String) -> Unit = {}
) {
    LaunchedEffect(Unit) { load(true) }
    var selectedFilterName by rememberSaveable { mutableStateOf(ReceiptRecordFilter.ALL.name) }
    val selectedFilter = ReceiptRecordFilter.valueOf(selectedFilterName)
    val personalReceipts = state.bills.filter {
        it.direction == "INCOME" && it.source == "PERSONAL_COLLECTION_CODE"
    }
    val visibleReceipts = when (selectedFilter) {
        ReceiptRecordFilter.ALL, ReceiptRecordFilter.PERSONAL -> personalReceipts
        ReceiptRecordFilter.MERCHANT -> emptyList()
    }
    val groupedReceipts = visibleReceipts
        .groupBy(::receiptRecordLocalDate)
        .toList()
        .sortedByDescending { it.first }
    val total = visibleReceipts.sumOf { it.amountCent }

    Column(
        Modifier
            .fillMaxSize()
            .background(MilingHomeBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回")
            }
            Text(
                "收款记录",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.size(48.dp))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "filters") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReceiptRecordFilter.entries.forEach { filter ->
                        ReceiptFilterChip(
                            label = filter.label,
                            selected = filter == selectedFilter,
                            onClick = { selectedFilterName = filter.name }
                        )
                    }
                }
            }

            if (state.collectionRecordsLoading && personalReceipts.isEmpty()) {
                item(key = "loading") {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().semantics {
                            contentDescription = "正在加载收款记录"
                        },
                        color = MilingPrimary,
                        trackColor = MilingPrimarySoft
                    )
                }
            }

            state.collectionRecordsError?.let { error ->
                item(key = "error") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFFFF5F5)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                error,
                                color = MilingError,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { load(true) }, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text("重新加载")
                            }
                        }
                    }
                }
            }

            if (!state.collectionRecordsLoading || visibleReceipts.isNotEmpty()) {
                item(key = "summary") {
                    ReceiptRecordsSummary(receipts = visibleReceipts, totalAmountCent = total)
                }
            }

            if (!state.collectionRecordsLoading && visibleReceipts.isEmpty()) {
                item(key = "empty") {
                    ReceiptRecordsEmptyState(selectedFilter)
                }
            }

            groupedReceipts.forEach { (date, records) ->
                item(key = "date-$date") {
                    Text(
                        receiptRecordDateTitle(date),
                        style = MaterialTheme.typography.titleMedium,
                        color = MilingTextSecondary,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                    )
                }
                item(key = "records-$date") {
                    Surface(
                        modifier = Modifier.fillMaxWidth().border(
                            1.dp,
                            MilingBorder,
                            RoundedCornerShape(18.dp)
                        ),
                        shape = RoundedCornerShape(18.dp),
                        color = MilingSurface
                    ) {
                        Column {
                            records.forEachIndexed { index, bill ->
                                ReceiptRecordRow(bill = bill, onClick = { onBillClick(bill.billId) })
                                if (index < records.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 76.dp),
                                        color = MilingDivider
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (selectedFilter != ReceiptRecordFilter.MERCHANT &&
                personalReceipts.size < state.collectionRecordTotal
            ) {
                item(key = "load-more") {
                    TextButton(
                        onClick = { load(false) },
                        enabled = !state.collectionRecordsLoadingMore,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) {
                        if (state.collectionRecordsLoadingMore) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (state.collectionRecordsLoadingMore) "加载中" else "加载更多")
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiptFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(24.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MilingSurface,
            labelColor = MilingTextSecondary,
            selectedContainerColor = MilingPrimarySoft,
            selectedLabelColor = MilingPrimary
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MilingBorder,
            selectedBorderColor = MilingPrimary
        )
    )
}

@Composable
private fun ReceiptRecordsSummary(receipts: List<WalletBill>, totalAmountCent: Long) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MilingSurfaceBlue
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MilingPrimarySoft
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ReceiptLong,
                            contentDescription = null,
                            tint = MilingPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("收款汇总", style = MaterialTheme.typography.titleMedium)
                    Text("当前筛选结果", color = MilingTextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("收款笔数", color = MilingTextSecondary, style = MaterialTheme.typography.bodyMedium)
                    Text("${receipts.size} 笔", style = MaterialTheme.typography.headlineSmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("收款总额", color = MilingTextSecondary, style = MaterialTheme.typography.bodyMedium)
                    Text(money(totalAmountCent), style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
    }
}

@Composable
private fun ReceiptRecordsEmptyState(filter: ReceiptRecordFilter) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MilingSurface
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.ReceiptLong,
                contentDescription = null,
                tint = MilingIconSecondary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                if (filter == ReceiptRecordFilter.MERCHANT) "暂无经营码收款" else "暂无收款记录",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                if (filter == ReceiptRecordFilter.MERCHANT) "经营收款记录将在业务开通后显示" else "通过个人收钱码到账后会显示在这里",
                color = MilingTextSecondary,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ReceiptRecordRow(bill: WalletBill, onClick: () -> Unit) {
    val payerName = receiptRecordPayerName(bill)
    val payerDetail = receiptRecordPayerDetail(bill)
    val receiptStatus = if (bill.status == "SUCCEEDED") "已入余额" else statusText(bill.status)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 84.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "$payerName，$payerDetail，到账${money(bill.amountCent)}，$receiptStatus，${receiptRecordTime(bill)}"
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(
            name = payerName,
            avatarUrl = bill.counterpartyProfile?.avatarUrl,
            colorIndex = (bill.counterpartyProfile?.userId ?: bill.billId).hashCode(),
            size = 44.dp
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                payerName,
                style = MaterialTheme.typography.titleMedium,
                color = MilingTextPrimary,
                maxLines = 1
            )
            Text(
                payerDetail,
                style = MaterialTheme.typography.bodySmall,
                color = MilingTextSecondary,
                maxLines = 1
            )
            Text(
                receiptRecordTime(bill),
                style = MaterialTheme.typography.bodySmall,
                color = MilingTextMuted
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "+${money(bill.amountCent)}",
                style = MaterialTheme.typography.titleMedium,
                color = MilingTextPrimary
            )
            Text(
                receiptStatus,
                style = MaterialTheme.typography.bodySmall,
                color = if (bill.status == "SUCCEEDED") MilingSuccess else MilingTextSecondary
            )
        }
    }
}

@Composable
internal fun FundingScreen(
    title: String,
    state: FinanceUiState,
    requirePassword: Boolean,
    preselectedCardId: String?,
    onBack: () -> Unit,
    onBindCard: () -> Unit,
    onRecords: () -> Unit,
    onCompleted: () -> Unit,
    onSubmit: (String, Long, String, () -> Unit) -> Unit
) {
    val active = state.cards.filter { it.status == "ACTIVE" }
    var selected by remember(active, preselectedCardId) {
        mutableStateOf(
            preselectedCardId?.takeIf { id -> active.any { it.cardId == id } }.orEmpty()
        )
    }
    var amount by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPaymentMethods by rememberSaveable { mutableStateOf(false) }
    var showPasswordPanel by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(showPasswordPanel, state.submitting, state.message) {
        if (showPasswordPanel && !state.submitting && state.message != null) {
            // Payment authorization errors are recoverable: keep the protected sheet open and
            // require a fresh six-digit entry rather than leaking the error to another page.
            password = ""
        }
    }
    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = "正在加载银行卡" })
        }
        return
    }
    val card = active.firstOrNull { it.cardId == selected }
    val cents = yuanToCent(amount)
    Box(Modifier.fillMaxSize()) {
        if (showPasswordPanel && cents != null && card != null) {
            PaymentConfirmationBackdrop(
                title = title,
                counterparty = state.currentUserProfile?.nickname?.ifBlank { "当前账户" } ?: "当前账户",
                counterpartyDetail = state.currentUserProfile?.legalNameMasked,
                amountCent = cents,
                method = "${card.bankName}${card.maskedCardNo}"
            )
        } else {
            FundingAmountEntry(
                title = title,
                profile = state.currentUserProfile,
                availableAmountCent = state.wallet?.availableAmountCent,
                amount = amount,
                error = state.message?.takeIf { !showPaymentMethods && !showPasswordPanel },
                onBack = onBack,
                onRecords = onRecords,
                onDigit = { amount = amount.appendFundingDigit(it) },
                onDelete = { amount = amount.dropLast(1) },
                onClear = { amount = "" },
                onNext = { showPaymentMethods = true },
                inputDisabled = state.submitting,
                nextEnabled = cents != null && !state.submitting
            )
        }
        if (showPaymentMethods) {
            if (cents != null) PaymentMethodSheet(
                title = "选择支付方式",
                counterparty = if (title == "充值") "充值至我的余额" else "提现至银行卡",
                amountCent = cents,
                cards = active,
                selectedCardId = selected.takeIf { it.isNotBlank() },
                confirmText = title,
                busy = state.submitting,
                onClose = { showPaymentMethods = false },
                onSelectCard = { selected = it },
                onConfirm = {
                    if (selected.isBlank()) return@PaymentMethodSheet
                    showPaymentMethods = false
                    if (requirePassword) {
                        password = ""
                        showPasswordPanel = true
                    } else onSubmit(selected, cents, "", onCompleted)
                },
                showAddCard = true,
                onAddCard = {
                    showPaymentMethods = false
                    onBindCard()
                }
            )
        }
        if (showPasswordPanel && card != null) {
            if (cents != null) {
                PaymentPasswordSheet(
                    purpose = title,
                    counterparty = "${card.bankName}${card.maskedCardNo}",
                    amountCent = cents,
                    password = password,
                    busy = state.submitting,
                    error = state.message,
                    onClose = {
                        password = ""
                        showPasswordPanel = false
                        showPaymentMethods = true
                    },
                    onDigit = { digit ->
                        if (state.submitting || password.length >= 6) return@PaymentPasswordSheet
                        password += digit
                        if (password.length == 6) {
                            onSubmit(selected, cents, password, onCompleted)
                        }
                    },
                    onDelete = { if (!state.submitting) password = password.dropLast(1) }
                )
            }
        }
    }
}

@Composable
private fun FundingAmountEntry(
    title: String,
    profile: UserProfile?,
    availableAmountCent: Long?,
    amount: String,
    error: String?,
    onBack: () -> Unit,
    onRecords: () -> Unit,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    onNext: () -> Unit,
    inputDisabled: Boolean,
    nextEnabled: Boolean
) {
    Column(
        Modifier.fillMaxSize().background(MilingSurface).statusBarsPadding().navigationBarsPadding()
    ) {
        PaymentEntryTopBar(title, if (title == "充值") "充值记录" else "提现记录", onBack, onRecords)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            SelfFundingIdentity(profile, title, availableAmountCent)
            Spacer(Modifier.height(24.dp))
            LargeAmountCard("${title}金额", amount, onClear)
            error?.let { ErrorText(it) }
        }
        FundingAmountKeypad(
            inputDisabled = inputDisabled,
            nextEnabled = nextEnabled,
            onDigit = onDigit,
            onDelete = onDelete,
            onNext = onNext
        )
    }
}

@Composable
internal fun PaymentEntryTopBar(title: String, recordLabel: String, onBack: () -> Unit, onRecords: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).size(48.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回")
        }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.align(Alignment.Center))
        if (recordLabel.isNotBlank()) Text(
            recordLabel,
            color = MilingTextPrimary,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.align(Alignment.CenterEnd).clickable(onClick = onRecords).padding(horizontal = 12.dp)
        )
    }
}

@Composable
private fun SelfFundingIdentity(profile: UserProfile?, title: String, availableAmountCent: Long?) {
    Surface(
        modifier = Modifier.fillMaxWidth().border(1.dp, MilingBorder, RoundedCornerShape(MilingRadii.Large)),
        shape = RoundedCornerShape(MilingRadii.Large),
        color = MilingSurface
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (profile?.avatarUrl == null) {
                Surface(Modifier.size(56.dp), CircleShape, MilingPrimarySoft) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Person, "当前账户头像", tint = MilingPrimary, modifier = Modifier.size(30.dp))
                    }
                }
            } else {
                AvatarImage(
                    avatarUrl = profile.avatarUrl,
                    contentDescription = "当前账户头像",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(56.dp).clip(CircleShape)
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(profile?.nickname?.ifBlank { "当前账户" } ?: "当前账户", style = MaterialTheme.typography.titleMedium)
                profile?.legalNameMasked?.takeIf { it.isNotBlank() }?.let {
                    Text("实名：$it", color = MilingTextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    if (title == "充值") "充值至我的余额" else "提现至已绑定银行卡",
                    color = MilingTextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (title == "提现" && availableAmountCent != null) {
                    Text("可提现余额 ${money(availableAmountCent)}", color = MilingTextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
internal fun LargeAmountCard(label: String, amount: String, onClear: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 190.dp),
        shape = RoundedCornerShape(MilingRadii.ExtraLarge),
        color = MilingSurface,
        tonalElevation = 1.dp
    ) {
        Column(Modifier.padding(24.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("¥", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.width(10.dp))
                Text(
                    if (amount.isBlank()) "0" else amount,
                    style = MaterialTheme.typography.displayLarge,
                    color = if (amount.isBlank()) MilingTextMuted else MilingTextPrimary
                )
                Spacer(Modifier.weight(1f))
                if (amount.isNotBlank()) IconButton(onClick = onClear, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.Close, "清除金额", tint = MilingIconSecondary)
                }
            }
        }
    }
}

@Composable
internal fun FundingAmountKeypad(
    inputDisabled: Boolean,
    nextEnabled: Boolean,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onNext: () -> Unit
) {
    Surface(color = MilingSurface, tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().height(62.dp)) {
                listOf("1", "2", "3", "delete").forEach { key ->
                    FundingKey(Modifier.weight(1f).fillMaxHeight(), key, inputDisabled, onDigit, onDelete)
                }
            }
            Row(Modifier.fillMaxWidth().height(186.dp)) {
                Column(Modifier.weight(3f)) {
                    listOf(listOf("4", "5", "6"), listOf("7", "8", "9")).forEach { row ->
                        Row(Modifier.fillMaxWidth().weight(1f)) {
                            row.forEach { key -> FundingKey(Modifier.weight(1f).fillMaxHeight(), key, inputDisabled, onDigit, onDelete) }
                        }
                    }
                    Row(Modifier.fillMaxWidth().weight(1f)) {
                        FundingKey(Modifier.weight(2f).fillMaxHeight(), "0", inputDisabled, onDigit, onDelete)
                        FundingKey(Modifier.weight(1f).fillMaxHeight(), ".", inputDisabled, onDigit, onDelete)
                    }
                }
                Button(
                    onClick = onNext,
                    enabled = nextEnabled,
                    shape = RoundedCornerShape(0.dp),
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) { Text("下一步", style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}

@Composable
private fun FundingKey(
    modifier: Modifier,
    key: String,
    disabled: Boolean,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit
) {
    when (key) {
        "blank" -> Spacer(modifier)
        "delete" -> FundingKeySurface(modifier.semantics { contentDescription = "删除金额" }, !disabled, onDelete) {
            Icon(Icons.AutoMirrored.Outlined.Backspace, null)
        }
        else -> FundingKeySurface(modifier.semantics { contentDescription = "数字 $key" }, !disabled, { onDigit(key.single()) }) {
            Text(key, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
private fun FundingKeySurface(
    modifier: Modifier,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        color = if (enabled) MilingSurface else MilingSurfaceSubtle,
        modifier = modifier
            .border(1.dp, MilingDivider)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
    ) { Box(contentAlignment = Alignment.Center) { content() } }
}

@Composable
internal fun PaymentMethodSheet(
    title: String,
    counterparty: String,
    amountCent: Long,
    cards: List<BankCard>,
    selectedCardId: String?,
    confirmText: String,
    busy: Boolean,
    onClose: () -> Unit,
    onSelectCard: (String) -> Unit,
    onConfirm: () -> Unit,
    showBalance: Boolean = false,
    showAddCard: Boolean = false,
    onAddCard: () -> Unit = {}
) {
    FinanceBottomSheetOverlay(
        preferredHeight = 620.dp,
        sheetTestTag = "payment-method-sheet",
        scrimColor = Color(0x66000000)
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(Modifier.size(width = 40.dp, height = 4.dp), RoundedCornerShape(4.dp), MilingBorder) {}
            Box(Modifier.fillMaxWidth().height(52.dp)) {
                IconButton(onClick = onClose, enabled = !busy, modifier = Modifier.align(Alignment.CenterStart).size(48.dp)) {
                    Icon(Icons.Outlined.Close, "关闭支付方式")
                }
            }
            Text(counterparty, color = MilingTextSecondary, style = MaterialTheme.typography.bodyMedium)
            Text(money(amountCent), style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier.fillMaxWidth().weight(1f)
                    .clip(RoundedCornerShape(MilingRadii.Large))
                    .background(MilingSurfaceSubtle)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("payment-method-list"),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (showBalance) item {
                        PaymentMethodChoice(
                            icon = Icons.Outlined.AccountBalanceWallet,
                            title = "账户余额",
                            selected = selectedCardId == null,
                            onClick = { onSelectCard("") }
                        )
                    }
                    items(cards, key = { it.cardId }) { card ->
                        PaymentMethodChoice(
                            icon = Icons.Outlined.AccountBalance,
                            title = "${card.bankName}${card.cardTypeText()} ${card.maskedCardNo}",
                            selected = selectedCardId == card.cardId,
                            onClick = { onSelectCard(card.cardId) }
                        )
                    }
                    if (showAddCard) item {
                        PaymentMethodChoice(
                            icon = Icons.Outlined.CreditCard,
                            title = "添加银行卡",
                            selected = false,
                            onClick = onAddCard,
                            trailingArrow = true
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onConfirm,
                enabled = !busy && (showBalance || selectedCardId != null),
                modifier = Modifier.fillMaxWidth().height(54.dp).testTag("payment-method-confirm"),
                shape = RoundedCornerShape(28.dp)
            ) { Text(confirmText, style = MaterialTheme.typography.titleLarge) }
        }
    }
}

@Composable
private fun PaymentMethodChoice(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    trailingArrow: Boolean = false
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(MilingRadii.Medium))
            .background(if (selected) MilingSurfaceBlue else MilingSurface)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MilingPrimary)
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Outlined.CheckCircle, "已选择", tint = MilingPrimary)
        else if (trailingArrow) Icon(Icons.AutoMirrored.Outlined.ArrowForward, "进入添加银行卡", tint = MilingTextSecondary)
    }
}

private fun String.appendFundingDigit(digit: Char): String {
    if (digit == '.' && contains('.')) return this
    if (digit != '.' && contains('.') && substringAfter('.').length >= 2) return this
    if (length >= 12) return this
    return if (isEmpty() && digit == '.') "0." else this + digit
}

@Composable
internal fun FundingResultScreen(
    result: FundingResult?,
    cards: List<BankCard>,
    onBack: () -> Unit,
    onWallet: () -> Unit,
    onRecords: (String) -> Unit
) {
    if (result == null) {
        FinanceScaffold("交易结果", onBack) { EmptyState("未找到本次资金操作结果") }
        return
    }
    val card = cards.firstOrNull { it.cardId == result.cardId }
    val succeeded = result.status == "SUCCEEDED"
    val processing = result.status == "PROCESSING"
    val tint = when {
        succeeded -> MilingSuccess
        processing -> MilingPrimary
        else -> MilingError
    }
    val icon = when {
        succeeded -> Icons.Outlined.CheckCircle
        processing -> Icons.Outlined.Schedule
        else -> Icons.Outlined.ErrorOutline
    }
    FinanceScaffold("${if (result.type == "RECHARGE") "充值" else "提现"}结果", onBack) {
        Column(Modifier.fillMaxWidth().padding(top = MilingSpacing.Section), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(Modifier.size(72.dp), CircleShape, if (succeeded) MilingSuccessSoft else MilingPrimarySoft) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint, modifier = Modifier.size(38.dp)) }
            }
            Spacer(Modifier.height(MilingSpacing.Lg))
            Text(statusText(result.status), style = MaterialTheme.typography.headlineMedium, color = tint)
            Spacer(Modifier.height(MilingSpacing.Sm))
            Text(money(result.amountCent), style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(MilingSpacing.Section))
            Surface(Modifier.fillMaxWidth(), RoundedCornerShape(MilingRadii.Large), MilingSurfaceSubtle) {
                Column(Modifier.padding(MilingSpacing.Lg)) {
                    ResultInfoRow("操作", if (result.type == "RECHARGE") "充值到余额" else "提现到银行卡")
                    ResultInfoRow("银行卡", card?.let { "${it.bankName} ${it.maskedCardNo}" } ?: "已选择银行卡")
                    if (processing) ResultInfoRow("说明", "资金正在处理中，请在记录中查看最终结果")
                    if (!succeeded && !processing) ResultInfoRow("说明", "本次操作未完成，请返回后重新发起")
                }
            }
            Spacer(Modifier.height(MilingSpacing.Xl))
            Button(onClick = onWallet, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("返回钱包") }
            TextButton(onClick = { onRecords(result.type) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("查看${if (result.type == "RECHARGE") "充值" else "提现"}记录") }
        }
    }
}

@Composable
private fun ResultInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = MilingSpacing.Sm), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MilingTextSecondary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
    }
}

private fun BankCard.cardTypeText(): String = if (cardType == "DEBIT") "储蓄卡" else cardType

@Composable
private fun BankCardVisual(
    card: BankCard,
    modifier: Modifier,
    onClick: (() -> Unit)?
) {
    val interaction = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)
    Surface(
        modifier = modifier.then(interaction).semantics {
            contentDescription = "${card.bankName}，借记卡，${card.maskedCardNo}，${statusText(card.status)}"
        },
        shape = RoundedCornerShape(MilingRadii.Large),
        color = MilingPrimary
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(MilingRadii.Small),
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = .18f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.AccountBalance,
                            contentDescription = null,
                            tint = androidx.compose.ui.graphics.Color.White
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(card.bankName, color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.titleMedium)
                    Text("借记卡", color = androidx.compose.ui.graphics.Color.White.copy(alpha = .78f))
                }
                if (onClick != null) Icon(Icons.Outlined.ChevronRight, null, tint = androidx.compose.ui.graphics.Color.White)
            }
            Spacer(Modifier.height(28.dp))
            Text(card.maskedCardNo, color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun CardActionRow(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MilingIconSecondary)
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(Icons.Outlined.ChevronRight, null, tint = MilingIconSecondary)
    }
}

@Composable
private fun LimitRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MilingTextSecondary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun BankTransactionRow(transaction: BankTransaction) {
    ListItem(
        headlineContent = { Text(transaction.description, maxLines = 2) },
        supportingContent = {
            Text("${formatOccurredAt(transaction.occurredAt)} · ${statusText(transaction.status)}")
        },
        leadingContent = {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(MilingRadii.Small),
                color = if (transaction.direction == "INCOME") MilingSuccessSoft else MilingSurfaceSubtle
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (transaction.direction == "INCOME") Icons.Outlined.SouthWest else Icons.Outlined.NorthEast,
                        null,
                        tint = if (transaction.direction == "INCOME") MilingSuccess else MilingIconSecondary
                    )
                }
            }
        },
        trailingContent = {
            Text(
                (if (transaction.direction == "INCOME") "+" else "-") + money(transaction.amountCent),
                color = if (transaction.direction == "INCOME") MilingSuccess else MilingTextPrimary,
                modifier = Modifier.semantics {
                    contentDescription = (if (transaction.direction == "INCOME") "收入" else "支出") + money(transaction.amountCent)
                }
            )
        }
    )
}

@Composable
internal fun RealNameRequiredScreen(
    processing: Boolean,
    onAction: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MilingSurface)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        FinanceTopBar("实名认证", onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MilingSpacing.Xxl, vertical = MilingSpacing.Xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(88.dp),
                shape = RoundedCornerShape(MilingRadii.ExtraLarge),
                color = MilingPrimarySoft
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (processing) Icons.Outlined.HourglassTop else Icons.Outlined.VerifiedUser,
                        contentDescription = null,
                        tint = MilingPrimary,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }
            Spacer(Modifier.height(MilingSpacing.Xxl))
            Text(
                text = if (processing) "认证结果处理中" else "完成实名认证后继续",
                style = MaterialTheme.typography.headlineMedium,
                color = MilingTextPrimary
            )
            Spacer(Modifier.height(MilingSpacing.Md))
            Text(
                text = if (processing) {
                    "认证资料已提交，我们正在核验。完成后即可使用钱包与资金功能。"
                } else {
                    "为保障账户与资金安全，需要确认是你本人。"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MilingTextSecondary
            )
            Spacer(Modifier.height(MilingSpacing.Section))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(MilingRadii.Large),
                color = MilingSurfaceSubtle
            ) {
                Column(Modifier.padding(horizontal = MilingSpacing.Xl, vertical = MilingSpacing.Sm)) {
                    VerificationInfoRow(
                        icon = Icons.Outlined.Badge,
                        title = "认证材料",
                        detail = "姓名、身份证和现场人脸照片"
                    )
                    HorizontalDivider(color = MilingDivider)
                    VerificationInfoRow(
                        icon = Icons.Outlined.Lock,
                        title = "安全保护",
                        detail = "认证信息将被加密保护"
                    )
                    HorizontalDivider(color = MilingDivider)
                    VerificationInfoRow(
                        icon = Icons.Outlined.Schedule,
                        title = "认证时长",
                        detail = if (processing) "可稍后返回查看结果" else "通常约 1 分钟"
                    )
                }
            }
        }
        Surface(color = MilingSurface, shadowElevation = 4.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MilingSpacing.Xxl, vertical = MilingSpacing.Lg),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = onAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(MilingRadii.Medium)
                ) {
                    if (processing) {
                        Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(MilingSpacing.Sm))
                    }
                    Text(if (processing) "刷新认证状态" else "开始认证")
                }
                Spacer(Modifier.height(MilingSpacing.Sm))
                Text(
                    text = "认证信息仅用于身份核验",
                    style = MaterialTheme.typography.bodySmall,
                    color = MilingTextMuted
                )
            }
        }
    }
}

@Composable
private fun VerificationInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MilingSpacing.Lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(MilingRadii.Small),
            color = MilingPrimarySoft
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MilingPrimary, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(MilingSpacing.Lg))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MilingTextPrimary)
            Spacer(Modifier.height(MilingSpacing.Xs))
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MilingTextSecondary)
        }
    }
}

@Composable
private fun FinanceScaffold(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(MilingSurface).statusBarsPadding().navigationBarsPadding()) {
        FinanceTopBar(title, onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), content = content)
    }
}

@Composable
private fun FinanceTopBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回")
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable private fun EmptyState(text: String) { Text(text, color = MilingTextSecondary, modifier = Modifier.padding(vertical = 24.dp)) }
@Composable private fun ErrorText(text: String) { Text(text, color = MilingError, modifier = Modifier.padding(top = 12.dp)) }
private fun money(cents: Long): String = NumberFormat.getCurrencyInstance(Locale.CHINA)
    .format(java.math.BigDecimal.valueOf(cents, 2))
private fun statusText(value: String) = when (value) {
    "SUCCEEDED", "VERIFIED" -> "已完成"
    "ACTIVE" -> "可用"
    "DISABLED" -> "已解绑"
    "PROCESSING" -> "处理中"
    "PENDING_CONFIRMATION" -> "待确认"
    "CLOSED" -> "已关闭"
    "FAILED", "REJECTED" -> "失败"
    else -> value
}
private fun businessTypeText(value: String) = when (value) {
    "OPENING_GRANT" -> "开户演示金"
    "RECHARGE" -> "充值"
    "WITHDRAWAL" -> "提现"
    "WITHDRAWAL_REVERSAL" -> "提现冲正"
    "TRANSFER" -> "转账"
    "PAYMENT" -> "支付"
    "REFUND" -> "退款"
    else -> value
}
private fun billMonthKey(bill: WalletBill): String = runCatching {
    YearMonth.from(Instant.parse(bill.occurredAt).atZone(ZoneId.systemDefault())).toString()
}.getOrDefault("其他")
private fun billMonthTitle(key: String): String = runCatching {
    val value = YearMonth.parse(key)
    if (value.year == YearMonth.now().year) "${value.monthValue}月"
    else "${value.year}年${value.monthValue}月"
}.getOrDefault(key)
internal fun receiptRecordLocalDate(bill: WalletBill): LocalDate = runCatching {
    Instant.parse(bill.occurredAt).atZone(ZoneId.systemDefault()).toLocalDate()
}.getOrDefault(LocalDate.MIN)
internal fun receiptRecordDateTitle(
    date: LocalDate,
    today: LocalDate = LocalDate.now()
): String = when (date) {
    LocalDate.MIN -> "日期未知"
    today -> "今日"
    today.minusDays(1) -> "昨天"
    else -> date.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA))
}
internal fun receiptRecordPayerName(bill: WalletBill): String =
    bill.counterpartyProfile?.nickname?.trim()?.takeIf { it.isNotEmpty() }
        ?: bill.counterpartyDisplay?.trim()?.takeIf { it.isNotEmpty() }
        ?: "付款方"
internal fun receiptRecordPayerDetail(bill: WalletBill): String {
    val maskedName = bill.counterpartyProfile?.legalNameMasked?.trim()?.takeIf { it.isNotEmpty() }
    return listOfNotNull(maskedName?.let { "实名 $it" }, "个人收钱码").joinToString(" · ")
}
internal fun receiptRecordTime(bill: WalletBill): String = runCatching {
    Instant.parse(bill.occurredAt).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm"))
}.getOrDefault(bill.occurredAt.take(16).substringAfterLast('T'))
private fun formatOccurredAt(value: String): String = runCatching {
    java.time.Instant.parse(value).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
}.getOrDefault(value.take(19).replace('T', ' '))
private fun formatOccurredAtSeconds(value: String): String = formatOccurredAt(value)
private fun plainAmount(cents: Long): String = java.math.BigDecimal.valueOf(cents, 2).toPlainString()
private fun signedPlainAmount(bill: WalletBill): String =
    (if (bill.direction == "INCOME") "+" else "-") + plainAmount(bill.amountCent)
private fun billDisplayTitle(bill: WalletBill): String {
    val counterpart = bill.counterpartyProfile?.nickname?.takeIf { it.isNotBlank() }
        ?: bill.counterpartyDisplay?.takeIf { it.isNotBlank() }
    return when (bill.businessType) {
        "TRANSFER" -> if (bill.direction == "INCOME") {
            if (bill.source == "PERSONAL_COLLECTION_CODE") "收钱码收款${counterpart?.let { "-来自$it" } ?: ""}"
            else "收款${counterpart?.let { "-$it" } ?: ""}"
        } else "转账${counterpart?.let { "-$it" } ?: ""}"
        "RECHARGE" -> "余额充值"
        "WITHDRAWAL" -> "余额提现"
        "WITHDRAWAL_REVERSAL" -> "提现失败退款"
        "PAYMENT", "MERCHANT_PAYMENT" -> bill.remark ?: counterpart ?: "余额支付"
        "REFUND", "MERCHANT_REFUND", "REVERSAL" -> bill.remark ?: "退款"
        else -> bill.remark ?: counterpart ?: businessTypeText(bill.businessType)
    }
}
private fun billIcon(type: String, income: Boolean): androidx.compose.ui.graphics.vector.ImageVector = when (type) {
    "RECHARGE" -> Icons.Outlined.AddCard
    "WITHDRAWAL" -> Icons.Outlined.Savings
    "PAYMENT", "MERCHANT_PAYMENT" -> Icons.Outlined.Storefront
    "REFUND", "MERCHANT_REFUND", "REVERSAL", "WITHDRAWAL_REVERSAL" -> Icons.Outlined.Replay
    else -> if (income) Icons.Outlined.SouthWest else Icons.Outlined.NorthEast
}
private fun billIconBackground(type: String, income: Boolean): Color = when (type) {
    "RECHARGE", "REFUND", "MERCHANT_REFUND", "REVERSAL", "WITHDRAWAL_REVERSAL" -> Color(0xFFEAF7EF)
    else -> Color(0xFFEAF2FF)
}
private fun billIconTint(type: String, income: Boolean): Color =
    if (income || type in setOf("REFUND", "MERCHANT_REFUND", "REVERSAL", "WITHDRAWAL_REVERSAL")) Color(0xFF20A15A) else MilingPrimary
private fun fundingStatusColor(status: String): Color = when (status) {
    "SUCCEEDED" -> Color(0xFF20A15A)
    "FAILED", "CLOSED" -> MilingError
    else -> MilingPrimary
}
private val BILL_CATEGORIES = listOf(
    "TRANSFER" to "转账/收款",
    "FUNDING" to "充值/提现",
    "DINING" to "餐饮",
    "SHOPPING" to "购物",
    "TRANSPORT" to "交通",
    "LIFE_SERVICE" to "生活服务",
    "MEDICAL" to "医疗",
    "EDUCATION" to "教育",
    "ENTERTAINMENT" to "娱乐",
    "REFUND" to "退款",
    "OTHER" to "其他"
)
private fun billCategoryText(code: String): String = BILL_CATEGORIES.firstOrNull { it.first == code }?.second ?: "其他"

@Composable
private fun SecureFinanceWindow(enabled: Boolean) {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity, enabled) {
        val wasSecure = activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        if (enabled) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (enabled && !wasSecure) activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
