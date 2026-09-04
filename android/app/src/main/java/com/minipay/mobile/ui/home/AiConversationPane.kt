package com.minipay.mobile.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.minipay.mobile.R
import com.minipay.mobile.ai.AgentActionRequest
import com.minipay.mobile.ai.AiHomeMessage
import com.minipay.mobile.ai.isNativePaymentResultMessage
import com.minipay.mobile.ai.AiHomeMessageRole
import com.minipay.mobile.ai.AiHomeUiState
import com.minipay.mobile.finance.FinanceDestination
import com.minipay.mobile.ui.components.UserAvatar
import com.minipay.mobile.ui.theme.MilingBorder
import com.minipay.mobile.ui.theme.MilingPrimary
import com.minipay.mobile.ui.theme.MilingPrimarySoft
import com.minipay.mobile.ui.theme.MilingRadii
import com.minipay.mobile.ui.theme.MilingSurface
import com.minipay.mobile.ui.theme.MilingSurfaceSubtle
import com.minipay.mobile.ui.theme.MilingTextPrimary
import com.minipay.mobile.ui.theme.MilingTextSecondary
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private val AI_CARD_DATE_TIME = DateTimeFormatter.ofPattern("M月d日 HH:mm")
private val AI_CARD_DATE = DateTimeFormatter.ofPattern("M月d日")

private val VISIBLE_AI_CARD_TYPES = setOf(
    "wallet.summary",
    "wallet.bill-summary",
    "wallet.bill-list",
    "commerce.food-entry",
    "commerce.merchants",
    "commerce.menu",
    "commerce.cart",
    "payment.transfer-intent",
    "payment.transfer-order",
    "commerce.checkout",
    "commerce.order",
    "commerce.cancellation-preview",
    "agent.contact-selection",
    "memory.confirmation",
    "memory.saved"
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AiConversationPane(
    state: AiHomeUiState,
    onAction: (MilingHomeAction) -> Unit,
    imeVisible: Boolean = false,
    modifier: Modifier = Modifier
) {
    val messages = state.messages.filterNot(AiHomeMessage::isNativePaymentResultMessage)
    val listState = rememberLazyListState()
    val bottomAnchorRequester = remember { BringIntoViewRequester() }
    var previousConversationKey by remember { mutableStateOf<String?>(null) }
    var previousImeVisible by remember { mutableStateOf(false) }
    var previousLatestMessageId by remember { mutableStateOf<String?>(null) }
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("ai_message_list"),
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp
        ),
        verticalArrangement = if (imeVisible) {
            Arrangement.spacedBy(12.dp, Alignment.Bottom)
        } else {
            Arrangement.spacedBy(12.dp)
        }
    ) {
        itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
            MessageBubble(
                message = message,
                paymentCompleted = message.runId in state.completedPaymentRunIds,
                paymentStatusChecking = message.runId in state.checkingPaymentRunIds,
                showTime = shouldShowAiMessageTime(
                    current = message.createdAt,
                    previous = messages.getOrNull(index - 1)?.createdAt
                ),
                onAction = onAction
            )
        }
        if (state.streaming) {
            item("agent-progress") {
                Row(
                    modifier = Modifier
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("米灵正在处理…", color = MilingTextSecondary)
                }
            }
        }
        item("message-bottom-anchor") {
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .bringIntoViewRequester(bottomAnchorRequester)
                    .testTag("ai_message_bottom_anchor")
            )
        }
    }
    val latestTextLength = messages.lastOrNull()?.text?.length ?: 0
    val conversationKey = state.selectedConversationId
        ?: if (state.newConversationSelected) "new-conversation" else "unselected"
    val latestMessage = messages.lastOrNull()
    LaunchedEffect(
        conversationKey,
        latestMessage?.id,
        latestTextLength,
        latestMessage?.cardType,
        latestMessage?.cardPayload?.hashCode(),
        state.streaming,
        imeVisible
    ) {
        val conversationChanged = previousConversationKey != conversationKey
        val imeOpened = imeVisible && !previousImeVisible
        val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        val totalItems = listState.layoutInfo.totalItemsCount
        val nearBottom = totalItems == 0 || lastVisibleIndex >= totalItems - 2
        val latestMessageChanged = latestMessage?.id != previousLatestMessageId
        val userJustSent = latestMessageChanged && latestMessage?.role == AiHomeMessageRole.USER
        previousConversationKey = conversationKey
        previousImeVisible = imeVisible
        previousLatestMessageId = latestMessage?.id

        if (conversationChanged || imeOpened || userJustSent || nearBottom) {
            withFrameNanos { }
            withFrameNanos { }
            if (imeOpened) delay(48)
            withFrameNanos { }
            val anchorIndex = listState.layoutInfo.totalItemsCount - 1
            if (anchorIndex >= 0) {
                listState.scrollToItem(anchorIndex)
                withFrameNanos { }
                bottomAnchorRequester.bringIntoView()
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: AiHomeMessage,
    paymentCompleted: Boolean,
    paymentStatusChecking: Boolean,
    showTime: Boolean,
    onAction: (MilingHomeAction) -> Unit
) {
    val user = message.role == AiHomeMessageRole.USER
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai_message_${message.id}"),
        horizontalAlignment = if (user) Alignment.End else Alignment.Start
    ) {
        if (showTime && message.createdAt != null) {
            val time = formatAiMessageTime(message.createdAt)
            Text(
                text = time,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 8.dp)
                    .semantics { contentDescription = "消息时间 $time" }
                    .testTag("ai_message_time_${message.id}"),
                color = MilingTextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Surface(
            color = if (user) MilingPrimary else MilingSurfaceSubtle,
            shape = RoundedCornerShape(
                topStart = MilingRadii.Large,
                topEnd = MilingRadii.Large,
                bottomStart = if (user) MilingRadii.Large else 4.dp,
                bottomEnd = if (user) 4.dp else MilingRadii.Large
            ),
            modifier = Modifier.widthIn(max = 336.dp)
        ) {
            Text(
                message.text,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                color = if (user) Color.White else MilingTextPrimary,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        if (message.cardPayload != null
            && message.cardType != null
            && message.cardType in VISIBLE_AI_CARD_TYPES
        ) {
            Spacer(Modifier.height(8.dp))
            AuthoritativeCard(message, paymentCompleted, paymentStatusChecking, onAction)
        }
    }
}

@Composable
private fun AuthoritativeCard(
    message: AiHomeMessage,
    paymentCompleted: Boolean,
    paymentStatusChecking: Boolean,
    onAction: (MilingHomeAction) -> Unit
) {
    val payload = message.cardPayload ?: return
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("ai_card_${message.cardType}"),
        shape = RoundedCornerShape(MilingRadii.Large),
        color = MilingSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MilingBorder)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (message.cardType) {
                "wallet.summary" -> WalletCard(payload, onAction)
                "wallet.bill-summary" -> BillSummaryCard(payload, onAction)
                "wallet.bill-list" -> BillListCard(payload, onAction)
                "commerce.food-entry" -> FoodEntryCard(onAction)
                "commerce.merchants" -> MerchantCard(message, payload, onAction)
                "commerce.menu" -> MenuCard(message, payload, onAction)
                "commerce.cart" -> CartCard(message, payload, onAction)
                "payment.transfer-intent" -> TransferCard(
                    message, payload, paymentCompleted, paymentStatusChecking, onAction
                )
                "payment.transfer-order" -> TransferResultCard(message, payload, onAction)
                "commerce.checkout" -> CheckoutCard(message, payload, onAction)
                "commerce.order" -> OrderCard(message, payload, onAction)
                "commerce.cancellation-preview" -> CancellationCard(message, payload, onAction)
                "agent.contact-selection" -> ContactSelectionCard(message, payload, onAction)
                "memory.confirmation" -> MemoryConfirmationCard(message, payload, onAction)
                "memory.saved" -> MemorySavedCard(payload)
            }
        }
    }
}

@Composable
internal fun FoodEntryCard(onAction: (MilingHomeAction) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(MilingRadii.Medium)
        ) {
            Image(
                painter = painterResource(R.drawable.yixiang_food_logo),
                contentDescription = "意向外卖 Logo",
                modifier = Modifier.size(56.dp).padding(6.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "意向外卖",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MilingTextPrimary
            )
            Text(
                "外卖点餐、到店自取",
                style = MaterialTheme.typography.bodySmall,
                color = MilingTextSecondary
            )
        }
    }
    Button(
        onClick = { onAction(MilingHomeAction.OpenFood) },
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("ai_food_entry_button")
    ) {
        Text("进入意向外卖")
    }
}

@Composable
private fun WalletCard(payload: JsonObject, onAction: (MilingHomeAction) -> Unit) {
    val available = payload.long("availableAmountCent") ?: payload.long("totalAmountCent") ?: 0
    val frozen = payload.long("frozenAmountCent")
    val annualRemaining = payload.long("annualOutflowRemainingCent")
    val recentBills = payload.objects("recentBills").take(3)
    FinancialCardHeader(
        title = "MiniPay 钱包",
        subtitle = "沙箱资产",
        status = payload.string("status")
    )
    Surface(
        color = MilingPrimarySoft,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text("可用余额", style = MaterialTheme.typography.bodySmall, color = MilingTextSecondary)
            Text(
                "¥${centText(available)}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MilingTextPrimary,
                modifier = Modifier.semantics {
                    contentDescription = "可用余额 ${centText(available)} 元"
                }
            )
            if (frozen != null || annualRemaining != null) {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    frozen?.let {
                        FinancialMetric("冻结金额", "¥${centText(it)}", Modifier.weight(1f))
                    }
                    annualRemaining?.let {
                        FinancialMetric("年度剩余额度", "¥${centText(it)}", Modifier.weight(1f))
                    }
                }
            }
        }
    }
    if (recentBills.isNotEmpty()) {
        FinancialSectionHeader("最近交易") {
            onAction(MilingHomeAction.OpenFinance(FinanceDestination.BILLS))
        }
        recentBills.forEachIndexed { index, bill ->
            if (index > 0) androidx.compose.material3.HorizontalDivider(color = MilingBorder)
            FinancialBillRow(bill) {
                onAction(MilingHomeAction.OpenFinance(FinanceDestination.BILLS))
            }
        }
    }
    payload.string("sandboxNotice")?.takeIf(String::isNotBlank)?.let { notice ->
        Surface(color = MilingPrimarySoft, shape = RoundedCornerShape(10.dp)) {
            Text(
                notice,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MilingTextSecondary
            )
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = { onAction(MilingHomeAction.OpenFinance(FinanceDestination.TRANSFER)) },
            modifier = Modifier.weight(1f).height(48.dp)
        ) { Text("转账") }
        OutlinedButton(
            onClick = { onAction(MilingHomeAction.OpenFinance(FinanceDestination.WALLET)) },
            modifier = Modifier.weight(1f).height(48.dp)
        ) { Text("钱包详情") }
    }
}

@Composable
private fun BillSummaryCard(payload: JsonObject, onAction: (MilingHomeAction) -> Unit) {
    val income = payload.long("incomeAmountCent") ?: 0
    val expense = payload.long("expenseAmountCent") ?: 0
    val net = income - expense
    val totalFlow = income + expense
    FinancialCardHeader("收支汇总", formatDateRange(payload), null, Icons.AutoMirrored.Outlined.ReceiptLong)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FinancialMetric("收入", "+¥${centText(income)}", Modifier.weight(1f))
        FinancialMetric("支出", "-¥${centText(expense)}", Modifier.weight(1f))
    }
    FinancialMetric(
        "净收支",
        "${if (net >= 0) "+" else "-"}¥${centText(abs(net))}",
        Modifier.fillMaxWidth()
    )
    if (totalFlow > 0) {
        val incomeWeight = (income.toFloat() / totalFlow.toFloat()).coerceIn(0.02f, 0.98f)
        Row(
            Modifier.fillMaxWidth().height(6.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Box(Modifier.weight(incomeWeight).fillMaxSize().background(MilingPrimary, RoundedCornerShape(3.dp)))
            Box(Modifier.weight(1f - incomeWeight).fillMaxSize().background(Color(0xFFDCE5F5), RoundedCornerShape(3.dp)))
        }
    }
    payload.objects("groups").take(3).forEach { group ->
        val label = businessTypeLabel(group.string("businessType").orEmpty())
        val count = group.long("billCount") ?: 0
        KeyValue("$label · $count 笔", "¥${centText(group.long("totalAmountCent") ?: 0)}")
    }
    FinancialCardFooter("查看全部账单") {
        onAction(MilingHomeAction.OpenFinance(FinanceDestination.BILLS))
    }
}

@Composable
private fun BillListCard(payload: JsonObject, onAction: (MilingHomeAction) -> Unit) {
    val records = payload.objects("items")
    val visibleRecords = records.take(3)
    val total = payload.long("total") ?: records.size.toLong()
    FinancialCardHeader(
        "交易记录",
        "${formatDateRange(payload)} · 共 $total 条",
        null,
        Icons.AutoMirrored.Outlined.ReceiptLong
    )
    if (visibleRecords.isEmpty()) {
        Surface(color = MilingSurfaceSubtle, shape = RoundedCornerShape(12.dp)) {
            Text(
                "该时间范围内暂无交易记录",
                modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp),
                color = MilingTextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    } else {
        visibleRecords.forEachIndexed { index, bill ->
            if (index > 0) androidx.compose.material3.HorizontalDivider(color = MilingBorder)
            FinancialBillRow(bill) {
                onAction(MilingHomeAction.OpenFinance(FinanceDestination.BILLS))
            }
        }
    }
    FinancialCardFooter("查看全部账单") {
        onAction(MilingHomeAction.OpenFinance(FinanceDestination.BILLS))
    }
}

@Composable
private fun MerchantCard(message: AiHomeMessage, payload: JsonObject, onAction: (MilingHomeAction) -> Unit) {
    CardTitle("推荐商家", "沙箱商家与配送报价")
    payload.objects("items").forEach { merchant ->
        CardRow(
            merchant.string("name") ?: "商家",
            "配送 ¥${centText(merchant.long("deliveryFeeCent") ?: 0)} · ${merchant.long("estimatedDeliveryMinutes") ?: 0} 分钟"
        ) {
            onAction(MilingHomeAction.ContinueCard(message, AgentActionRequest(
                action = "GET_MENU", merchantId = merchant.string("id")
            )))
        }
    }
}

@Composable
private fun MenuCard(message: AiHomeMessage, payload: JsonObject, onAction: (MilingHomeAction) -> Unit) {
    CardTitle("选择商品", "价格与库存来自 Commerce")
    val merchantId = payload.string("merchantId")
    payload.objects("items").forEach { item ->
        val sku = item.objects("skus").firstOrNull() ?: return@forEach
        CardRow(
            item.string("name") ?: "商品",
            "${sku.string("name").orEmpty()} · ¥${centText(sku.long("priceCent") ?: 0)} · 库存 ${sku.long("availableQuantity") ?: 0}"
        ) {
            onAction(MilingHomeAction.ContinueCard(message, AgentActionRequest(
                action = "UPDATE_CART",
                merchantId = merchantId,
                skuId = sku.string("skuId"),
                quantity = 1,
                optionIds = emptyList()
            )))
        }
    }
}

@Composable
private fun CartCard(message: AiHomeMessage, payload: JsonObject, onAction: (MilingHomeAction) -> Unit) {
    CardTitle(payload.string("merchantName") ?: "购物车", "购物车版本 ${payload.long("version") ?: 0}")
    payload.objects("items").forEach { item ->
        KeyValue(
            "${item.string("itemName") ?: "商品"} × ${item.long("quantity") ?: 0}",
            "¥${centText(item.long("lineAmountCent") ?: 0)}"
        )
    }
    KeyValue("商品合计", "¥${centText(payload.long("itemAmountCent") ?: 0)}", strong = true)
    Button(
        onClick = { onAction(MilingHomeAction.PrepareCheckout(message)) },
        modifier = Modifier.fillMaxWidth().height(48.dp)
    ) { Text("选择地址并结算") }
}

@Composable
private fun TransferCard(
    message: AiHomeMessage,
    payload: JsonObject,
    paymentCompleted: Boolean,
    paymentStatusChecking: Boolean,
    onAction: (MilingHomeAction) -> Unit
) {
    val recipient = payload["recipient"]?.jsonObject
    CardTitle("转账确认", "意图创建后金额不可修改")
    KeyValue("收款人", recipient?.string("nickname") ?: "收款人")
    recipient?.string("legalNameMasked")?.let { KeyValue("实名", it) }
    recipient?.string("phoneMasked")?.let { KeyValue("账户", it) }
    AmountText(payload.long("amountCent") ?: 0)
    SafetyNotice()
    Button(
        onClick = { onAction(MilingHomeAction.RequestPayment(message)) },
        enabled = !paymentCompleted && !paymentStatusChecking,
        modifier = Modifier.fillMaxWidth().height(48.dp)
    ) {
        Text(
            when {
                paymentCompleted -> "转账已完成"
                paymentStatusChecking -> "正在确认转账状态"
                else -> "确认并验证支付密码"
            }
        )
    }
}

@Composable
private fun ContactSelectionCard(
    message: AiHomeMessage,
    payload: JsonObject,
    onAction: (MilingHomeAction) -> Unit
) {
    val candidates = payload.objects("items").ifEmpty { payload.objects("candidates") }
    CardTitle("选择收款好友", "存在同名或跨字段匹配，请确认收款人")
    candidates.forEach { candidate ->
        val nickname = candidate.string("nickname") ?: "好友"
        val details = listOfNotNull(
            candidate.string("legalNameMasked")?.let { "实名 $it" },
            candidate.string("phoneMasked")
        ).joinToString(" · ")
        CardRow(nickname, details) {
            onAction(MilingHomeAction.ContinueCard(
                message,
                AgentActionRequest(
                    action = "SELECT_TRANSFER_RECIPIENT",
                    recipientUserId = candidate.string("recipientUserId")
                        ?: candidate.string("userId")
                )
            ))
        }
    }
}

@Composable
private fun MemoryConfirmationCard(
    message: AiHomeMessage,
    payload: JsonObject,
    onAction: (MilingHomeAction) -> Unit
) {
    val candidateId = payload.string("candidateId")
    val type = when (payload.string("memoryType") ?: payload.string("type")) {
        "FOOD_PREFERENCE" -> "餐饮偏好"
        "ALLERGEN_AVOIDANCE" -> "忌口与过敏原"
        "MEAL_BUDGET" -> "用餐预算"
        else -> "长期偏好"
    }
    CardTitle("保存到长期记忆？", "仅在你确认后保存")
    KeyValue("类型", type)
    Text(payload.string("displayValue") ?: payload.string("content").orEmpty())
    Button(
        onClick = {
            onAction(MilingHomeAction.ContinueCard(
                message,
                AgentActionRequest(action = "CONFIRM_MEMORY", candidateId = candidateId)
            ))
        },
        enabled = !candidateId.isNullOrBlank(),
        modifier = Modifier.fillMaxWidth().height(48.dp)
    ) { Text("保存到长期记忆") }
    OutlinedButton(
        onClick = {
            onAction(MilingHomeAction.ContinueCard(
                message,
                AgentActionRequest(action = "DISMISS_MEMORY", candidateId = candidateId)
            ))
        },
        enabled = !candidateId.isNullOrBlank(),
        modifier = Modifier.fillMaxWidth().height(48.dp)
    ) { Text("仅本次使用") }
}

@Composable
private fun MemorySavedCard(payload: JsonObject) {
    CardTitle("已保存到长期记忆", "可在个人中心 · 记忆中修改或删除")
    Text(payload.string("displayValue") ?: payload.string("content") ?: "已保存")
}

@Composable
private fun CheckoutCard(message: AiHomeMessage, payload: JsonObject, onAction: (MilingHomeAction) -> Unit) {
    CardTitle("外卖结算", "金额来自 Commerce 权威报价")
    KeyValue("商品", "¥${centText(payload.long("itemAmountCent") ?: 0)}")
    KeyValue("配送费", "¥${centText(payload.long("deliveryFeeCent") ?: 0)}")
    KeyValue("优惠", "-¥${centText(payload.long("discountCent") ?: 0)}")
    KeyValue("实付", "¥${centText(payload.long("payableAmountCent") ?: 0)}", strong = true)
    SafetyNotice()
    Button(
        onClick = { onAction(MilingHomeAction.RequestPayment(message)) },
        modifier = Modifier.fillMaxWidth().height(48.dp)
    ) { Text("确认下单并付款") }
}

@Composable
private fun TransferResultCard(
    message: AiHomeMessage,
    payload: JsonObject,
    onAction: (MilingHomeAction) -> Unit
) {
    val status = payload.string("status").orEmpty()
    CardTitle("转账结果", "状态来自 Payment")
    KeyValue("状态", statusLabel(status), strong = true)
    KeyValue("金额", "¥${centText(payload.long("amountCent") ?: 0)}", strong = true)
    payload.string("failureCode")?.let { KeyValue("失败原因", it) }
    if (status == "PROCESSING") {
        OutlinedButton(
            onClick = {
                onAction(MilingHomeAction.ContinueCard(
                    message,
                    AgentActionRequest(
                        action = "GET_TRANSFER",
                        transferId = payload.string("transferId")
                    )
                ))
            },
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) { Text("刷新转账状态") }
    }
}

@Composable
private fun OrderCard(message: AiHomeMessage, payload: JsonObject, onAction: (MilingHomeAction) -> Unit) {
    CardTitle(payload.string("merchantName") ?: "外卖订单", payload.string("orderNo") ?: "")
    KeyValue("履约状态", statusLabel(payload.string("status").orEmpty()), strong = true)
    KeyValue("支付状态", statusLabel(payload.string("paymentStatus").orEmpty()))
    KeyValue("实付", "¥${centText(payload.long("payableAmountCent") ?: 0)}")
    if (payload.string("status") in setOf("PENDING_PAYMENT", "PAID", "REFUND_FAILED")) {
        OutlinedButton(
            onClick = {
                onAction(MilingHomeAction.ContinueCard(message, AgentActionRequest(
                    action = "PREPARE_CANCELLATION",
                    orderId = payload.string("id")
                )))
            },
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) { Text("取消订单 / 申请退款") }
    }
}

@Composable
private fun CancellationCard(message: AiHomeMessage, payload: JsonObject, onAction: (MilingHomeAction) -> Unit) {
    CardTitle("取消与退款确认", "操作需由你明确确认")
    KeyValue("订单", payload.string("orderNo") ?: "")
    KeyValue("方式", if (payload.string("action") == "FULL_REFUND") "全额退款" else "取消订单")
    KeyValue("金额", "¥${centText(payload.long("amountCent") ?: 0)}", strong = true)
    Button(
        enabled = payload["cancellable"]?.jsonPrimitive?.contentOrNull == "true",
        onClick = { onAction(MilingHomeAction.CancelOrder(message)) },
        modifier = Modifier.fillMaxWidth().height(48.dp)
    ) { Text("确认执行") }
}

@Composable
private fun FinancialCardHeader(
    title: String,
    subtitle: String,
    status: String?,
    icon: ImageVector = Icons.Outlined.AccountBalanceWallet
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(color = MilingPrimarySoft, shape = RoundedCornerShape(12.dp)) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MilingPrimary, modifier = Modifier.size(24.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MilingTextPrimary,
                modifier = Modifier.semantics { heading() }
            )
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MilingTextSecondary)
            }
        }
        status?.let {
            val label = statusLabel(it)
            Surface(color = statusBackground(it), shape = RoundedCornerShape(50)) {
                Text(
                    label,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = statusForeground(it)
                )
            }
        }
    }
}

@Composable
private fun FinancialMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = MilingSurfaceSubtle, shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MilingTextSecondary)
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MilingTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FinancialSectionHeader(title: String, onViewAll: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MilingTextPrimary
        )
        TextButton(onClick = onViewAll, modifier = Modifier.height(48.dp)) {
            Text("查看全部")
            Icon(
                Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun FinancialBillRow(bill: JsonObject, onClick: () -> Unit) {
    val businessType = bill.string("businessType").orEmpty()
    val direction = bill.string("direction").orEmpty()
    val incoming = direction == "IN" || direction == "INCOME" || direction == "CREDIT"
    val amount = abs(bill.long("amountCent") ?: 0)
    val profile = bill.obj("counterpartyProfile")
    val nickname = profile?.string("nickname")
        ?: bill.string("counterpartyNickname")
    val legalName = profile?.string("legalNameMasked")
        ?: bill.string("counterpartyLegalNameMasked")
    val title = nickname?.takeIf(String::isNotBlank)
        ?: bill.string("counterpartyDisplay")
        ?: legalName
        ?: bill.string("remark")
        ?: businessTypeLabel(businessType)
    val avatarUrl = profile?.usableAvatarUrl()
        ?: bill.usableAvatarUrl("counterpartyAvatarUrl", "counterpartyAvatarUrlExpiresAt")
    val hasPersonalCounterparty = profile != null || !bill.string("counterpartyUserId").isNullOrBlank()
    val typeAndStatus = "${businessTypeLabel(businessType)} · ${statusLabel(bill.string("status").orEmpty())}"
    val time = formatBillTime(bill.string("occurredAt"))
    val metadata = listOfNotNull(typeAndStatus, time).joinToString(" · ")
    val identity = legalName?.takeIf { it.isNotBlank() && it != title }?.let { "实名 $it" }
    val amountText = "${if (incoming) "+" else "-"}¥${centText(amount)}"
    val accessibilityText = listOfNotNull(title, identity, metadata, amountText).joinToString("，")
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = accessibilityText
            },
        color = Color.Transparent,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 4.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (hasPersonalCounterparty) {
                UserAvatar(
                    name = title,
                    avatarUrl = avatarUrl,
                    colorIndex = bill.string("counterpartyUserId")?.hashCode() ?: title.hashCode(),
                    size = 44.dp
                )
            } else {
                Surface(color = MilingPrimarySoft, shape = RoundedCornerShape(12.dp)) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            financialIcon(businessType),
                            contentDescription = null,
                            tint = MilingPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    title,
                    color = MilingTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                identity?.let {
                    Text(
                        it,
                        color = MilingTextSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    metadata,
                    color = MilingTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    amountText,
                    color = if (incoming) MilingPrimary else MilingTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1
                )
                Text(
                    if (incoming) "收入" else "支出",
                    color = MilingTextSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun FinancialCardFooter(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(48.dp)
    ) {
        Text(label)
        Spacer(Modifier.size(4.dp))
        Icon(
            Icons.AutoMirrored.Outlined.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable private fun CardTitle(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MilingTextSecondary)
}

@Composable private fun AmountText(amountCent: Long) {
    Text("¥${centText(amountCent)}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
}

@Composable private fun KeyValue(label: String, value: String, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MilingTextSecondary, modifier = Modifier.weight(1f))
        Text(
            value,
            color = MilingTextPrimary,
            fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable private fun CardRow(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MilingRadii.Medium),
        color = MilingPrimarySoft
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = MilingTextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MilingTextSecondary)
        }
    }
}

@Composable private fun SafetyNotice() {
    Box(
        Modifier.fillMaxWidth().background(MilingPrimarySoft, RoundedCornerShape(8.dp)).padding(10.dp)
    ) { Text("AI 不能代替你付款；支付密码不会发送给模型。", style = MaterialTheme.typography.bodySmall) }
}

private fun JsonObject.string(name: String): String? = get(name)?.jsonPrimitive?.contentOrNull
private fun JsonObject.long(name: String): Long? = get(name)?.jsonPrimitive?.longOrNull
private fun JsonObject.obj(name: String): JsonObject? = get(name) as? JsonObject
private fun JsonObject.objects(name: String): List<JsonObject> =
    (get(name) as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
private fun JsonObject.usableAvatarUrl(
    urlField: String = "avatarUrl",
    expiryField: String = "avatarUrlExpiresAt"
): String? {
    val url = string(urlField)?.takeIf(String::isNotBlank) ?: return null
    val expiresAt = string(expiryField)?.takeIf(String::isNotBlank) ?: return url
    return url.takeIf {
        runCatching { Instant.parse(expiresAt).isAfter(Instant.now().plusSeconds(30)) }
            .getOrDefault(false)
    }
}
private fun centText(value: Long): String {
    val whole = value / 100
    val fraction = kotlin.math.abs(value % 100)
    return "$whole.${fraction.toString().padStart(2, '0')}"
}
private fun financialIcon(businessType: String): ImageVector = when (businessType) {
    "TRANSFER" -> Icons.Outlined.SwapHoriz
    "RECHARGE", "WITHDRAWAL" -> Icons.Outlined.Savings
    else -> Icons.AutoMirrored.Outlined.ReceiptLong
}
private fun formatBillTime(value: String?): String? = value?.let { raw ->
    runCatching {
        AI_CARD_DATE_TIME.format(Instant.parse(raw).atZone(ZoneId.systemDefault()))
    }.getOrElse { raw.replace("T", " ").take(16) }
}
private fun formatDateRange(payload: JsonObject): String {
    val from = payload.string("from")?.let(::formatCardDate)
    val to = payload.string("to")?.let(::formatCardDate)
    return when {
        from != null && to != null -> "$from–$to"
        from != null -> "$from 起"
        else -> "最近 30 天"
    }
}
private fun formatCardDate(value: String): String? = runCatching {
    AI_CARD_DATE.format(Instant.parse(value).atZone(ZoneId.systemDefault()))
}.getOrNull()
private fun statusBackground(value: String): Color = when (value) {
    "ACTIVE", "SUCCEEDED", "PAID" -> Color(0xFFE8F6EF)
    "FAILED", "LIMITED", "RESTRICTED" -> Color(0xFFFFEBEE)
    else -> MilingPrimarySoft
}
private fun statusForeground(value: String): Color = when (value) {
    "ACTIVE", "SUCCEEDED", "PAID" -> Color(0xFF18794E)
    "FAILED", "LIMITED", "RESTRICTED" -> Color(0xFFB3261E)
    else -> MilingPrimary
}
private fun statusLabel(value: String): String = when (value) {
    "ACTIVE" -> "正常"
    "LIMITED", "RESTRICTED" -> "受限"
    "FROZEN" -> "已冻结"
    "PENDING_PAYMENT" -> "待支付"
    "PAID" -> "已支付"
    "MERCHANT_ACCEPTED" -> "商家已接单"
    "PREPARING" -> "制作中"
    "DELIVERING" -> "配送中"
    "DELIVERED" -> "已送达"
    "CANCELLATION_PENDING" -> "取消处理中"
    "CANCELLED" -> "已取消"
    "REFUND_FAILED" -> "退款失败"
    "SUCCEEDED" -> "成功"
    "FAILED" -> "失败"
    "PROCESSING" -> "处理中"
    "PENDING" -> "待处理"
    "REVERSED" -> "已冲正"
    "UNPAID" -> "未支付"
    else -> "状态未知"
}

private fun businessTypeLabel(value: String): String = when (value) {
    "TRANSFER" -> "转账"
    "PAYMENT" -> "付款"
    "REFUND" -> "退款"
    "RECHARGE" -> "充值"
    "WITHDRAWAL" -> "提现"
    else -> "交易"
}
