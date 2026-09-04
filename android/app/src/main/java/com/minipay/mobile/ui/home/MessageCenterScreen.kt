package com.minipay.mobile.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.minipay.mobile.ui.theme.MilingBackground
import com.minipay.mobile.ui.theme.MilingDivider
import com.minipay.mobile.ui.theme.MilingPrimary
import com.minipay.mobile.ui.theme.MilingSpacing
import com.minipay.mobile.ui.theme.MilingSurface
import com.minipay.mobile.ui.theme.MilingTextMuted
import com.minipay.mobile.ui.theme.MilingTextPrimary
import com.minipay.mobile.ui.theme.MilingTextSecondary
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class MessageCenterTab { CONVERSATION, PAYMENT }

@Composable
fun MessageCenterScreen(
    state: PaymentMessagesUiState,
    onOpenConversations: () -> Unit,
    onContacts: () -> Unit,
    onCreateGroup: () -> Unit,
    onAddFriend: () -> Unit,
    onScan: () -> Unit,
    onOpenBill: (String) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit
) {
    var menuExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize()
            .background(MilingBackground)
            .statusBarsPadding()
            .testTag("message_center")
    ) {
        MessageCenterHeader(
            selectedTab = MessageCenterTab.PAYMENT,
            onSelect = { if (it == MessageCenterTab.CONVERSATION) onOpenConversations() },
            menuExpanded = menuExpanded,
            onToggleMenu = { menuExpanded = !menuExpanded },
            onDismissMenu = { menuExpanded = false },
            onContacts = onContacts,
            onCreateGroup = onCreateGroup,
            onAddFriend = onAddFriend,
            onScan = onScan
        )
        PaymentMessagesTimeline(
            state = state,
            onRetry = onRetry,
            onLoadMore = onLoadMore,
            onOpenBill = onOpenBill
        )
    }
}

@Composable
private fun MessageCenterHeader(
    selectedTab: MessageCenterTab,
    onSelect: (MessageCenterTab) -> Unit,
    menuExpanded: Boolean,
    onToggleMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onContacts: () -> Unit,
    onCreateGroup: () -> Unit,
    onAddFriend: () -> Unit,
    onScan: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = MilingSpacing.Md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MessageTab("会话消息", selectedTab == MessageCenterTab.CONVERSATION, "message_tab_conversation") {
            onSelect(MessageCenterTab.CONVERSATION)
        }
        MessageTab("支付消息", selectedTab == MessageCenterTab.PAYMENT, "message_tab_payment") {
            onSelect(MessageCenterTab.PAYMENT)
        }
        Spacer(Modifier.weight(1f))
        Box {
            IconButton(onClick = onToggleMenu) { Icon(Icons.Outlined.MoreVert, "更多") }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = onDismissMenu) {
                listOf(
                    Triple("通讯录", Icons.Outlined.Contacts, onContacts),
                    Triple("发起群聊", Icons.Outlined.Add, onCreateGroup),
                    Triple("添加朋友", Icons.Outlined.Add, onAddFriend),
                    Triple("扫一扫", Icons.Rounded.Payments, onScan)
                ).forEach { (label, icon, action) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        leadingIcon = { Icon(icon, null) },
                        onClick = { onDismissMenu(); action() }
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageTab(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    Column(
        Modifier.height(58.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp)
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            label,
            color = if (selected) MilingPrimary else MilingTextPrimary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier.height(3.dp).fillMaxWidth()
                .background(if (selected) MilingPrimary else Color.Transparent, CircleShape)
        )
    }
}

@Composable
private fun PaymentMessagesTimeline(
    state: PaymentMessagesUiState,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenBill: (String) -> Unit
) {
    when {
        state.loading && state.bills.isEmpty() -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator() }

        state.error != null && state.bills.isEmpty() -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.error)
                Spacer(Modifier.height(MilingSpacing.Md))
                Button(onClick = onRetry) { Text("重新加载") }
            }
        }

        state.timeline.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无支付消息", color = MilingTextMuted)
        }

        else -> {
            val listState = rememberLazyListState()
            val shouldLoadMore by remember(state.total, state.bills.size, state.loadingMore) {
                derivedStateOf {
                    val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    lastVisible >= listState.layoutInfo.totalItemsCount - 3 &&
                        state.bills.size < state.total && !state.loadingMore
                }
            }
            LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) onLoadMore() }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().testTag("payment_messages"),
                contentPadding = PaddingValues(
                    start = MilingSpacing.Lg,
                    end = MilingSpacing.Lg,
                    top = MilingSpacing.Sm,
                    bottom = 92.dp
                )
            ) {
                state.timeline.forEach { day ->
                    item(key = "date-${day.date}") { PaymentDayHeader(day.date) }
                    items(day.items, key = { it.billId }) { message ->
                        PaymentMessageRow(message, onOpenBill)
                    }
                }
                item(key = "payment-message-footer") {
                    when {
                        state.loadingMore -> Box(
                            Modifier.fillMaxWidth().padding(MilingSpacing.Lg),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator(Modifier.size(22.dp)) }
                        state.loadMoreError != null -> TextButton(
                            onClick = onLoadMore,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("加载失败，点击重试") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentDayHeader(date: LocalDate) {
    val today = LocalDate.now()
    val label = when (date) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> date.format(DateTimeFormatter.ofPattern("MM月dd日"))
    }
    Text(
        label,
        color = MilingTextSecondary,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.fillMaxWidth().padding(top = MilingSpacing.Lg, bottom = MilingSpacing.Sm)
    )
}

@Composable
private fun PaymentMessageRow(message: PaymentMessageItem, onOpenBill: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clickable { onOpenBill(message.billId) }
            .padding(vertical = MilingSpacing.Md)
            .testTag("payment_message_${message.billId}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = RoundedCornerShape(14.dp),
            color = if (message.credit) Color(0xFFECFDF3) else Color(0xFFEAF3FF)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.Payments,
                    contentDescription = null,
                    tint = if (message.credit) Color(0xFF168A45) else MilingPrimary
                )
            }
        }
        Spacer(Modifier.size(MilingSpacing.Md))
        Column(Modifier.weight(1f)) {
            Text(
                message.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val detail = message.counterparty ?: message.remark ?: "账户余额"
            Text(
                detail,
                color = MilingTextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                paymentMessageMoney(message.amountCent, message.credit),
                color = if (message.credit) Color(0xFF168A45) else MilingTextPrimary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                message.occurredAt.atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("HH:mm")),
                color = MilingTextMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
    HorizontalDivider(color = MilingDivider)
}

internal fun paymentMessageMoney(amountCent: Long, credit: Boolean): String {
    val amount = amountCent.coerceAtLeast(0)
    val yuan = amount / 100
    val cents = amount % 100
    return "${if (credit) "+" else "-"}¥$yuan.${cents.toString().padStart(2, '0')}"
}
