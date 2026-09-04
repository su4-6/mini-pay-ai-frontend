package com.minipay.mobile.ui.finance

import android.animation.ValueAnimator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.minipay.mobile.finance.PaymentOperation
import com.minipay.mobile.finance.PaymentResultSnapshot
import com.minipay.mobile.finance.PaymentResultStatus
import com.minipay.mobile.finance.PaymentResultUiState
import com.minipay.mobile.ui.theme.MilingBorder
import com.minipay.mobile.ui.theme.MilingDivider
import com.minipay.mobile.ui.theme.MilingError
import com.minipay.mobile.ui.theme.MilingPrimary
import com.minipay.mobile.ui.theme.MilingPrimarySoft
import com.minipay.mobile.ui.theme.MilingRadii
import com.minipay.mobile.ui.theme.MilingSpacing
import com.minipay.mobile.ui.theme.MilingSurface
import com.minipay.mobile.ui.theme.MilingSurfaceSubtle
import com.minipay.mobile.ui.theme.MilingTextPrimary
import com.minipay.mobile.ui.theme.MilingTextSecondary
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

@Composable
internal fun UnifiedPaymentResultScreen(
    state: PaymentResultUiState,
    primaryLabel: String = "完成",
    secondaryLabel: String? = null,
    onDone: () -> Unit,
    onRefresh: () -> Unit,
    onSecondary: () -> Unit = {}
) {
    val snapshot = state.snapshot
    val processing = snapshot.status == PaymentResultStatus.PROCESSING
    val statusTitle = paymentStatusTitle(snapshot)
    val stateDescription = "$statusTitle，金额${formatPaymentMoney(snapshot.amountCent)}"
    BackHandler(onBack = onDone)

    Column(
        Modifier.fillMaxSize()
            .background(MilingSurface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = stateDescription
            }
            .testTag("payment-result-screen")
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PaymentStatusMark(snapshot.status)
            Spacer(Modifier.height(22.dp))
            Text(
                statusTitle,
                color = if (snapshot.status in setOf(PaymentResultStatus.FAILED, PaymentResultStatus.CLOSED)) {
                    MilingError
                } else {
                    MilingTextPrimary
                },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                formatPaymentMoney(snapshot.amountCent),
                style = MaterialTheme.typography.displayLarge,
                color = MilingTextPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(14.dp))
            Text(
                paymentStatusDescription(state),
                color = if (state.refreshError) MilingError else MilingTextSecondary,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(MilingSpacing.Section))
            PaymentResultDetails(snapshot)
            Spacer(Modifier.height(20.dp))
            Surface(
                color = MilingPrimarySoft,
                shape = RoundedCornerShape(MilingRadii.Medium),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "MiniPay 沙箱资产，仅用于功能体验，不产生真实资金。",
                    color = MilingTextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        Surface(tonalElevation = 4.dp, color = MilingSurface) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = if (processing && state.timedOut) onRefresh else onDone,
                    enabled = !state.refreshing,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
                        .testTag("payment-result-primary")
                ) {
                    if (state.refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            if (processing && state.timedOut) "刷新状态" else primaryLabel,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
                secondaryLabel?.let { label ->
                    TextButton(
                        onClick = onSecondary,
                        enabled = !state.refreshing,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .testTag("payment-result-secondary")
                    ) { Text(label, style = MaterialTheme.typography.bodyLarge) }
                }
            }
        }
    }
}

@Composable
private fun PaymentStatusMark(status: PaymentResultStatus) {
    val animationsEnabled = ValueAnimator.areAnimatorsEnabled()
    val successProgress by animateFloatAsState(
        targetValue = if (status == PaymentResultStatus.SUCCEEDED) 1f else 0f,
        animationSpec = tween(if (animationsEnabled) 300 else 0),
        label = "payment_success_mark"
    )
    val successScale by animateFloatAsState(
        targetValue = if (status == PaymentResultStatus.SUCCEEDED) 1f else .92f,
        animationSpec = tween(if (animationsEnabled) 220 else 0),
        label = "payment_success_scale"
    )
    Surface(
        modifier = Modifier.size(88.dp).graphicsLayer {
            scaleX = successScale
            scaleY = successScale
        }.testTag("payment-status-mark"),
        shape = CircleShape,
        color = if (status in setOf(PaymentResultStatus.FAILED, PaymentResultStatus.CLOSED)) {
            Color(0xFFFFEEEE)
        } else {
            MilingPrimarySoft
        }
    ) {
        Box(contentAlignment = Alignment.Center) {
            when (status) {
                PaymentResultStatus.PROCESSING -> ProcessingPaymentStatusMark(animationsEnabled)
                PaymentResultStatus.SUCCEEDED -> Canvas(Modifier.size(46.dp)) {
                    val start = Offset(size.width * .16f, size.height * .52f)
                    val middle = Offset(size.width * .42f, size.height * .75f)
                    val end = Offset(size.width * .86f, size.height * .27f)
                    val first = min(successProgress * 2f, 1f)
                    val second = max((successProgress - .5f) * 2f, 0f)
                    drawLine(
                        MilingPrimary,
                        start,
                        Offset(
                            start.x + (middle.x - start.x) * first,
                            start.y + (middle.y - start.y) * first
                        ),
                        strokeWidth = 5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    if (second > 0f) drawLine(
                        MilingPrimary,
                        middle,
                        Offset(
                            middle.x + (end.x - middle.x) * second,
                            middle.y + (end.y - middle.y) * second
                        ),
                        strokeWidth = 5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                PaymentResultStatus.FAILED, PaymentResultStatus.CLOSED ->
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = null,
                        tint = MilingError,
                        modifier = Modifier.size(42.dp)
                    )
            }
        }
    }
}

@Composable
private fun ProcessingPaymentStatusMark(animationsEnabled: Boolean) {
    val transition = rememberInfiniteTransition(label = "payment_processing_mark")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (animationsEnabled) 360f else 0f,
        animationSpec = infiniteRepeatable(tween(1_200, easing = LinearEasing), RepeatMode.Restart),
        label = "payment_processing_rotation"
    )
    Canvas(Modifier.fillMaxSize().padding(8.dp).graphicsLayer { rotationZ = rotation }) {
        drawArc(
            color = MilingPrimary,
            startAngle = -70f,
            sweepAngle = 250f,
            useCenter = false,
            topLeft = Offset(3.dp.toPx(), 3.dp.toPx()),
            size = Size(size.width - 6.dp.toPx(), size.height - 6.dp.toPx()),
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        )
    }
    Icon(
        Icons.Outlined.AccountBalanceWallet,
        contentDescription = null,
        tint = MilingPrimary,
        modifier = Modifier.size(36.dp)
    )
}

@Composable
private fun PaymentResultDetails(snapshot: PaymentResultSnapshot) {
    val rows = buildList {
        snapshot.counterparty?.takeIf(String::isNotBlank)?.let { add("收款方" to it) }
        snapshot.subject?.takeIf(String::isNotBlank)?.let { add("交易说明" to it) }
        snapshot.method?.takeIf(String::isNotBlank)?.let {
            add((if (snapshot.reference.operation == PaymentOperation.WITHDRAWAL) "到账方式" else "付款方式") to it)
        }
        snapshot.businessNo?.takeIf(String::isNotBlank)?.let { add("交易单号" to it) }
        snapshot.updatedAt?.let(::formatPaymentTime)?.takeIf(String::isNotBlank)?.let { add("更新时间" to it) }
    }
    if (rows.isEmpty()) return
    Surface(
        color = MilingSurfaceSubtle,
        shape = RoundedCornerShape(MilingRadii.Large),
        border = androidx.compose.foundation.BorderStroke(1.dp, MilingBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
            rows.forEachIndexed { index, (label, value) ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(label, color = MilingTextSecondary, modifier = Modifier.weight(1f))
                    Text(
                        value,
                        color = MilingTextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1.8f)
                    )
                }
                if (index != rows.lastIndex) HorizontalDivider(color = MilingDivider)
            }
        }
    }
}

private fun paymentStatusTitle(snapshot: PaymentResultSnapshot): String = when (snapshot.status) {
    PaymentResultStatus.PROCESSING -> when (snapshot.reference.operation) {
        PaymentOperation.RECHARGE -> "充值处理中"
        PaymentOperation.WITHDRAWAL -> "提现处理中"
        else -> "付款处理中"
    }
    PaymentResultStatus.SUCCEEDED -> when (snapshot.reference.operation) {
        PaymentOperation.RECHARGE -> "充值成功"
        PaymentOperation.WITHDRAWAL -> "提现成功"
        else -> "付款成功"
    }
    PaymentResultStatus.FAILED, PaymentResultStatus.CLOSED -> when (snapshot.reference.operation) {
        PaymentOperation.RECHARGE -> "充值未完成"
        PaymentOperation.WITHDRAWAL -> "提现未完成"
        else -> "付款未完成"
    }
}

private fun paymentStatusDescription(state: PaymentResultUiState): String {
    val snapshot = state.snapshot
    if (state.refreshError) return "暂时无法刷新状态，请检查网络后重试；请勿重复付款。"
    return when (snapshot.status) {
        PaymentResultStatus.PROCESSING -> if (state.timedOut) {
            "系统仍在确认最终结果，资金不会丢失。你可以刷新状态或稍后在记录中查看。"
        } else {
            "正在安全确认交易结果，请勿重复付款。"
        }
        PaymentResultStatus.SUCCEEDED -> when (snapshot.reference.operation) {
            PaymentOperation.WITHDRAWAL -> "资金已转入所选银行卡。"
            PaymentOperation.RECHARGE -> "资金已转入 MiniPay 余额。"
            else -> "交易已完成。"
        }
        PaymentResultStatus.FAILED, PaymentResultStatus.CLOSED -> safeFailureMessage(snapshot.failureCode)
    }
}

private fun safeFailureMessage(code: String?): String = when (code) {
    "INSUFFICIENT_BALANCE" -> "余额不足，本次交易未完成。"
    "BANK_PAYOUT_FAILED", "BANK_CHANNEL_UNAVAILABLE" -> "银行转账能力暂不可用，本次未完成，资金不会丢失。"
    "PAYMENT_AUTHORIZATION_EXPIRED" -> "支付授权已过期，请返回后重新确认。"
    else -> "本次交易未完成，请返回后重新发起。"
}

private fun formatPaymentMoney(amountCent: Long): String =
    "¥" + NumberFormat.getNumberInstance(Locale.CHINA).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(BigDecimal.valueOf(amountCent, 2))

private fun formatPaymentTime(value: String): String = runCatching {
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())
        .format(Instant.parse(value))
}.getOrDefault(value)
