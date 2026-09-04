package com.minipay.mobile.ui.finance

import android.animation.ValueAnimator
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.minipay.mobile.finance.FinanceUiState
import com.minipay.mobile.finance.TransferIntent
import com.minipay.mobile.finance.TransferOrder
import com.minipay.mobile.finance.TransferRecipientUi
import com.minipay.mobile.ui.theme.MilingBorder
import com.minipay.mobile.ui.theme.MilingPrimary
import com.minipay.mobile.ui.theme.MilingPrimarySoft
import com.minipay.mobile.ui.theme.MilingSurface
import com.minipay.mobile.ui.theme.MilingTextPrimary
import com.minipay.mobile.ui.theme.MilingTextSecondary
import com.minipay.mobile.ui.components.UserAvatar
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private enum class TransferPaymentStep { AMOUNT, METHODS, PASSWORD, PROCESSING, SUCCESS, FAILED }
private const val TRANSFER_PROCESSING_POLL_INTERVAL_MS = 1_000L
private const val TRANSFER_PROCESSING_POLL_LIMIT = 30

@Composable
@Suppress("UNUSED_PARAMETER")
internal fun TransferPaymentFlow(
    state: FinanceUiState,
    recipient: TransferRecipientUi,
    onBack: () -> Unit,
    onRecords: () -> Unit,
    createIntent: (String, Long, String?, String, String, (TransferIntent) -> Unit) -> Unit,
    confirm: (TransferIntent, String, String, (TransferOrder) -> Unit) -> Unit,
    refreshOrder: (String, (TransferOrder) -> Unit) -> Unit,
    onPaymentResult: (TransferOrder) -> Unit = {},
    onSucceeded: (TransferOrder) -> Unit = {},
    onFinished: () -> Unit
) {
    var step by rememberSaveable { mutableStateOf(TransferPaymentStep.AMOUNT.name) }
    var amount by rememberSaveable { mutableStateOf("") }
    var remark by rememberSaveable { mutableStateOf("") }
    var intent by remember { mutableStateOf<TransferIntent?>(null) }
    var order by remember { mutableStateOf<TransferOrder?>(null) }
    var password by remember { mutableStateOf("") }
    var passwordSubmitted by remember { mutableStateOf(false) }
    var reportedSuccessId by rememberSaveable { mutableStateOf<String?>(null) }
    var processingTimedOut by rememberSaveable { mutableStateOf(false) }
    val amountCent = amount.toTransferAmountCent()
    val current = TransferPaymentStep.valueOf(step)

    LaunchedEffect(order?.transferId, order?.status) {
        val succeeded = order?.takeIf { it.status == "SUCCEEDED" } ?: return@LaunchedEffect
        if (reportedSuccessId != succeeded.transferId) {
            reportedSuccessId = succeeded.transferId
            onSucceeded(succeeded)
        }
    }

    LaunchedEffect(order?.transferId, order?.status, current) {
        val transferId = order?.transferId ?: return@LaunchedEffect
        if (current != TransferPaymentStep.PROCESSING || order?.status != "PROCESSING") return@LaunchedEffect
        processingTimedOut = false
        repeat(TRANSFER_PROCESSING_POLL_LIMIT) {
            if (!isActive) return@LaunchedEffect
            delay(TRANSFER_PROCESSING_POLL_INTERVAL_MS)
            refreshOrder(transferId) { updated ->
                order = updated
                if (updated.status == "SUCCEEDED") step = TransferPaymentStep.SUCCESS.name
                if (updated.status == "FAILED") step = TransferPaymentStep.FAILED.name
            }
            if (order?.status != "PROCESSING") return@LaunchedEffect
        }
        processingTimedOut = true
    }


    LaunchedEffect(current, state.submitting, state.message) {
        if (current == TransferPaymentStep.PASSWORD && !state.submitting && state.message != null) {
            password = ""
            passwordSubmitted = false
        }
    }

    when (current) {
        TransferPaymentStep.AMOUNT -> TransferAmountScreen(
            recipient = recipient,
            amount = amount,
            remark = remark,
            busy = state.submitting,
            error = state.message,
            onBack = onBack,
            onRecords = onRecords,
            onDigit = { amount = amount.appendTransferDigit(it) },
            onDelete = { amount = amount.dropLast(1) },
            onClear = { amount = "" },
            onRemarkChange = { remark = it.take(50) },
            onNext = {
                val cents = amountCent ?: return@TransferAmountScreen
                if (state.submitting) return@TransferAmountScreen
                createIntent(
                    recipient.receiverUserId,
                    cents,
                    remark.ifBlank { null },
                    recipient.transferSource.wireValue,
                    UUID.randomUUID().toString()
                ) { created ->
                    intent = created
                    step = TransferPaymentStep.METHODS.name
                }
            }
        )
        TransferPaymentStep.METHODS -> Box(Modifier.fillMaxSize()) {
            TransferAmountScreen(
                recipient = recipient,
                amount = amount,
                remark = remark,
                busy = true,
                error = null,
                onBack = {},
                onRecords = {},
                onDigit = {},
                onDelete = {},
                onClear = {},
                onRemarkChange = {},
                onNext = {}
            )
            PaymentMethodSheet(
                title = "选择支付方式",
                counterparty = "向${recipient.display}转账",
                amountCent = requireNotNull(intent).amountCent,
                cards = emptyList(),
                selectedCardId = null,
                confirmText = "付款",
                busy = state.submitting,
                onClose = { step = TransferPaymentStep.AMOUNT.name },
                onSelectCard = {},
                onConfirm = { step = TransferPaymentStep.PASSWORD.name },
                showBalance = true
            )
        }
        TransferPaymentStep.PASSWORD -> Box(Modifier.fillMaxSize()) {
            PaymentConfirmationBackdrop(
                title = "转账",
                counterparty = recipient.display,
                counterpartyDetail = recipient.legalNameMasked ?: recipient.accountMasked,
                amountCent = requireNotNull(intent).amountCent,
                method = "账户余额"
            )
            PaymentPasswordSheet(
                purpose = "付款",
                counterparty = recipient.display,
                amountCent = requireNotNull(intent).amountCent,
                password = password,
                busy = state.submitting,
                error = state.message,
                onClose = {
                    password = ""
                    passwordSubmitted = false
                    step = TransferPaymentStep.METHODS.name
                },
                onDigit = { digit ->
                    if (passwordSubmitted || password.length >= 6) return@PaymentPasswordSheet
                    val updated = password + digit
                    password = updated
                    if (updated.length == 6) {
                        passwordSubmitted = true
                        confirm(requireNotNull(intent), updated, UUID.randomUUID().toString()) { confirmed ->
                            password = ""
                            order = confirmed
                            onPaymentResult(confirmed)
                            if (confirmed.status == "SUCCEEDED") onSucceeded(confirmed)
                            step = when (confirmed.status) {
                                "SUCCEEDED" -> TransferPaymentStep.SUCCESS.name
                                "FAILED" -> TransferPaymentStep.FAILED.name
                                else -> TransferPaymentStep.PROCESSING.name
                            }
                        }
                    }
                },
                onDelete = { if (!passwordSubmitted) password = password.dropLast(1) }
            )
        }
        TransferPaymentStep.PROCESSING -> TransferProcessingScreen(
            recipient = recipient,
            amountCent = requireNotNull(intent).amountCent,
            error = state.message,
            timedOut = processingTimedOut,
            onBack = onBack,
            onRecords = onRecords
        )
        TransferPaymentStep.SUCCESS -> TransferSuccessScreen(
            recipient = recipient,
            amountCent = order?.amountCent ?: requireNotNull(intent).amountCent,
            onFinished = onFinished
        )
        TransferPaymentStep.FAILED -> TransferFailureScreen(order?.failureCode, onBack)
    }
}

@Composable
private fun TransferAmountScreen(
    recipient: TransferRecipientUi,
    amount: String,
    remark: String,
    busy: Boolean,
    error: String?,
    onBack: () -> Unit,
    onRecords: () -> Unit,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    onRemarkChange: (String) -> Unit,
    onNext: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(Color(0xFFF6F7F9)).statusBarsPadding().navigationBarsPadding()) {
        PaymentEntryTopBar("转账", "转账记录", onBack, onRecords)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            RecipientHeader(recipient)
            Spacer(Modifier.height(18.dp))
            LargeAmountCard("转账金额", amount, onClear)
            Spacer(Modifier.height(16.dp))
            TransferRemarkCard(remark, onRemarkChange)
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
            }
            Text(
                "转账保障中",
                color = Color(0xFFA1A5AD),
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                textAlign = TextAlign.Center
            )
        }
        FundingAmountKeypad(
            inputDisabled = busy,
            nextEnabled = amount.toTransferAmountCent() != null && !busy,
            onDigit = onDigit,
            onDelete = onDelete,
            onNext = onNext
        )
    }
}

@Composable
private fun TransferRemarkCard(remark: String, onRemarkChange: (String) -> Unit) {
    val tags = listOf("出借", "谢谢", "生活费", "房租", "代买", "还款")
    Surface(shape = RoundedCornerShape(22.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text("转账留言", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = remark,
                onValueChange = onRemarkChange,
                placeholder = { Text("添加备注(50字以内)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                tags.forEach { tag ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE4E6EA)),
                        modifier = Modifier.weight(1f).clickable { onRemarkChange(tag) }
                    ) {
                        Text(tag, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecipientHeader(recipient: TransferRecipientUi) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        UserAvatar(
            name = recipient.nickname,
            avatarUrl = recipient.avatarUrl,
            colorIndex = recipient.receiverUserId.hashCode(),
            size = 64.dp,
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(recipient.nickname, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                recipient.legalNameMasked ?: recipient.accountMasked ?: "未实名",
                color = MilingTextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
internal fun PaymentConfirmationBackdrop(
    title: String,
    counterparty: String,
    counterpartyDetail: String?,
    amountCent: Long,
    method: String
) {
    Column(Modifier.fillMaxSize().background(Color(0xFFF7F8FA)).statusBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(64.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.align(Alignment.Center))
        }
        Surface(
            Modifier.padding(horizontal = 24.dp).fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = Color.White
        ) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(56.dp), CircleShape, MilingPrimarySoft) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Person, null, tint = MilingPrimary) }
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(counterparty, style = MaterialTheme.typography.titleMedium)
                    counterpartyDetail?.let { Text(it, color = MilingTextSecondary) }
                }
            }
        }
        Spacer(Modifier.height(42.dp))
        Text("${title}金额", color = MilingTextSecondary, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(formatTransferMoney(amountCent), style = MaterialTheme.typography.displayLarge, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(36.dp))
        Surface(
            Modifier.padding(horizontal = 24.dp).fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = Color.White
        ) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (title == "提现") "到账方式" else "付款方式", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text(method, color = MilingTextSecondary)
            }
        }
    }
}

@Composable
internal fun PaymentPasswordSheet(
    purpose: String,
    counterparty: String,
    amountCent: Long? = null,
    password: String,
    busy: Boolean,
    error: String?,
    onClose: () -> Unit,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    SecureFinanceWindow()
    BackHandler(enabled = !busy, onBack = onClose)
    FinanceBottomSheetOverlay(
        preferredHeight = 600.dp,
        sheetTestTag = "payment-password-sheet",
        scrimColor = Color(0x99000000)
    ) { compactHeight ->
        Surface(
            modifier = Modifier.padding(top = 10.dp).size(width = 40.dp, height = 4.dp)
                .align(Alignment.CenterHorizontally),
            shape = RoundedCornerShape(4.dp),
            color = MilingBorder
        ) {}
        Box(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp)) {
            IconButton(
                onClick = onClose,
                enabled = !busy,
                modifier = Modifier.align(Alignment.CenterStart).size(48.dp)
                    .semantics { contentDescription = "关闭支付密码" }
            ) { Icon(Icons.Outlined.Close, null) }
            Text("请输入支付密码", style = MaterialTheme.typography.titleLarge, modifier = Modifier.align(Alignment.Center))
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(6) { index ->
                    val entered = index < password.length
                    Box(
                        Modifier.weight(1f).height(56.dp)
                            .border(
                                1.dp,
                                if (index == password.length && !busy) MilingPrimary else Color(0xFFD8DDE8),
                                RoundedCornerShape(8.dp)
                            )
                            .semantics {
                                contentDescription = "支付密码第${index + 1}位，${if (entered) "已输入" else "未输入"}"
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (entered) Box(Modifier.size(10.dp).clip(CircleShape).background(MilingTextPrimary))
                    }
                }
            }
            Text(
                "忘记密码",
                color = MilingPrimary,
                modifier = Modifier.clickable {
                    Toast.makeText(context, "忘记密码功能暂未开放", Toast.LENGTH_SHORT).show()
                }.padding(16.dp).semantics { contentDescription = "忘记密码" }
            )
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
            if (busy) Text("正在安全验证支付密码", color = MilingTextSecondary, modifier = Modifier.padding(top = 8.dp))
        }
        HorizontalDivider(color = MilingBorder)
        PasswordKeypad(
            disabled = busy,
            rowHeight = if (compactHeight) 48.dp else 56.dp,
            onDigit = onDigit,
            onDelete = onDelete
        )
    }
}

@Composable
private fun PasswordKeypad(disabled: Boolean, rowHeight: androidx.compose.ui.unit.Dp, onDigit: (Char) -> Unit, onDelete: () -> Unit) {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("blank", "0", "delete"))
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        rows.forEachIndexed { rowIndex, row ->
            Row(
                Modifier.fillMaxWidth().then(
                    if (rowIndex == rows.lastIndex) Modifier.testTag("payment-password-keypad-last-row") else Modifier
                ),
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                row.forEach { key ->
                    val modifier = Modifier.weight(1f).height(rowHeight)
                    when (key) {
                        "blank" -> Spacer(modifier.background(Color(0xFFF2F3F5)))
                        "delete" -> PasswordKey(modifier, !disabled, onDelete) {
                            Icon(Icons.AutoMirrored.Outlined.Backspace, "删除支付密码")
                        }
                        else -> PasswordKey(modifier, !disabled, { onDigit(key.single()) }) {
                            Text(key, style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PasswordKey(modifier: Modifier, enabled: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        color = if (enabled) Color.White else Color(0xFFF2F3F5),
        modifier = modifier.then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
    ) { Box(contentAlignment = Alignment.Center) { content() } }
}

@Composable
private fun TransferProcessingScreen(
    recipient: TransferRecipientUi,
    amountCent: Long,
    error: String?,
    timedOut: Boolean,
    onBack: () -> Unit,
    onRecords: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "payment_processing")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (ValueAnimator.areAnimatorsEnabled()) 360f else 0f,
        animationSpec = infiniteRepeatable(tween(1_500, easing = LinearEasing), RepeatMode.Restart),
        label = "payment_mark_rotation"
    )
    Column(
        Modifier.fillMaxSize().background(Color(0xFFF7F9FE)).statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PaymentEntryTopBar("转账", "", onBack, {})
        Spacer(Modifier.height(100.dp))
        Text("向${recipient.display}付款", style = MaterialTheme.typography.titleLarge)
        Text(formatTransferMoney(amountCent), style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.weight(1f))
        Surface(
            shape = CircleShape,
            color = MilingPrimary,
            modifier = Modifier.size(86.dp).graphicsLayer { rotationZ = rotation }
                .semantics { contentDescription = "支付处理中" }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.AccountBalanceWallet, null, tint = Color.White, modifier = Modifier.size(46.dp))
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            if (timedOut) "处理时间较长" else "正在安全处理付款",
            color = MilingTextSecondary
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
        if (timedOut) {
            Text(
                "付款结果尚未确认，请勿重复付款，可到转账记录查看最终结果",
                color = MilingTextSecondary,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp)
            )
            OutlinedButton(onClick = onRecords) { Text("查看转账记录") }
        }
        Spacer(Modifier.height(100.dp))
    }
}

@Composable
private fun TransferSuccessScreen(recipient: TransferRecipientUi, amountCent: Long, onFinished: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MilingPrimary).statusBarsPadding().navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CheckCircle, null, tint = Color.White, modifier = Modifier.size(34.dp))
            Spacer(Modifier.width(10.dp))
            Text("付款成功", color = Color.White, style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(Modifier.height(38.dp))
        Text(formatTransferMoney(amountCent), color = Color.White, style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(70.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 42.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(recipient.display, color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text(formatTransferMoney(amountCent), color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 42.dp, vertical = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("付款方式", color = Color.White.copy(alpha = .84f))
            Text("账户余额", color = Color.White)
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onFinished,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = MilingPrimary),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.padding(horizontal = 40.dp).fillMaxWidth().height(54.dp)
        ) { Text("完成", style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TransferFailureScreen(failureCode: String?, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MilingSurface).statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("付款未完成", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            if (failureCode == null) "请返回后重新发起付款" else "付款失败，请返回后重新发起付款",
            color = MilingTextSecondary
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onBack, modifier = Modifier.padding(horizontal = 40.dp).fillMaxWidth().height(52.dp)) { Text("返回") }
    }
}

private fun String.appendTransferDigit(digit: Char): String {
    if (digit == '.' && contains('.')) return this
    if (digit != '.' && contains('.') && substringAfter('.').length >= 2) return this
    if (length >= 12) return this
    return if (isEmpty() && digit == '.') "0." else this + digit
}

private fun String.toTransferAmountCent(): Long? = runCatching {
    if (isBlank() || endsWith('.')) return null
    BigDecimal(this).movePointRight(2).longValueExact().takeIf { it in 1..1_000_000 }
}.getOrNull()

private fun formatTransferMoney(amountCent: Long): String = "¥" + NumberFormat.getNumberInstance(Locale.CHINA).apply {
    minimumFractionDigits = 2
    maximumFractionDigits = 2
}.format(BigDecimal.valueOf(amountCent, 2))
