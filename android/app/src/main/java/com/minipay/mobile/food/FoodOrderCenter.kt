package com.minipay.mobile.food

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.HeadsetMic
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.minipay.mobile.ai.CommerceApi
import com.minipay.mobile.ai.FoodOrderDetailDto
import com.minipay.mobile.ai.FoodOrderItemDto
import com.minipay.mobile.ai.FoodOrderSummaryDto
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FoodOrderTab(val label: String) {
    ALL("全部"),
    ACTIVE("进行中"),
    COMPLETED("已完成"),
    REFUND("退款")
}

data class FoodOrderCenterState(
    val loading: Boolean = true,
    val orders: List<FoodOrderSummaryDto> = emptyList(),
    val query: String = "",
    val tab: FoodOrderTab = FoodOrderTab.ALL,
    val error: String? = null
) {
    val visibleOrders: List<FoodOrderSummaryDto>
        get() = filterFoodOrders(orders, query, tab)
}

@HiltViewModel
class FoodOrderCenterViewModel @Inject constructor(
    private val commerce: CommerceApi
) : ViewModel() {
    private val mutableState = MutableStateFlow(FoodOrderCenterState())
    val state: StateFlow<FoodOrderCenterState> = mutableState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null) }
            runCatching { commerce.foodOrders() }
                .onSuccess { orders -> mutableState.update { it.copy(loading = false, orders = orders) } }
                .onFailure { mutableState.update { it.copy(loading = false, error = "外卖订单加载失败，请稍后重试") } }
        }
    }

    fun updateQuery(query: String) = mutableState.update { it.copy(query = query) }
    fun selectTab(tab: FoodOrderTab) = mutableState.update { it.copy(tab = tab) }
}

data class FoodOrderDetailState(
    val loading: Boolean = true,
    val order: FoodOrderDetailDto? = null,
    val error: String? = null
)

@HiltViewModel
class FoodOrderDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val commerce: CommerceApi
) : ViewModel() {
    private val orderRefId: String = requireNotNull(savedStateHandle["orderRefId"])
    private val mutableState = MutableStateFlow(FoodOrderDetailState())
    val state: StateFlow<FoodOrderDetailState> = mutableState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null) }
            runCatching { commerce.foodOrder(orderRefId) }
                .onSuccess { order -> mutableState.value = FoodOrderDetailState(false, order) }
                .onFailure { mutableState.value = FoodOrderDetailState(false, error = "订单详情加载失败，请稍后重试") }
        }
    }
}

@Composable
fun FoodOrderCenterScreen(
    onBack: () -> Unit,
    onOpenOrder: (String) -> Unit,
    onPay: (String) -> Unit,
    viewModel: FoodOrderCenterViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)) {
        Column(Modifier.fillMaxSize()) {
            OrderSearchHeader(state.query, viewModel::updateQuery, onBack)
            OrderTabs(state.tab, viewModel::selectTab)
            when {
                state.loading -> OrderListSkeleton()
                state.error != null -> OrderLoadError(requireNotNull(state.error), viewModel::refresh)
                state.visibleOrders.isEmpty() -> EmptyOrders(hasQuery = state.query.isNotBlank())
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.visibleOrders, key = { it.orderRefId }) { order ->
                        FoodOrderCard(order, onOpenOrder, onPay)
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderSearchHeader(query: String, onQueryChange: (String) -> Unit, onBack: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回")
            }
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f).height(52.dp),
                singleLine = true,
                placeholder = { Text("搜门店或订单号") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                shape = RoundedCornerShape(18.dp),
                keyboardActions = KeyboardActions(onSearch = {})
            )
        }
    }
}

@Composable
private fun OrderTabs(selected: FoodOrderTab, onSelected: (FoodOrderTab) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            FoodOrderTab.entries.forEach { tab ->
                val active = tab == selected
                Column(
                    modifier = Modifier.weight(1f).clickable { onSelected(tab) }.padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        tab.label,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                    )
                    Spacer(Modifier.height(7.dp))
                    Box(
                        Modifier.width(if (active) 24.dp else 0.dp).height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                    )
                }
            }
        }
    }
}

@Composable
internal fun FoodOrderCard(
    order: FoodOrderSummaryDto,
    onOpenOrder: (String) -> Unit,
    onPay: (String) -> Unit
) {
    Card(
        onClick = { onOpenOrder(order.orderRefId) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    order.storeName?.takeIf { it.isNotBlank() } ?: "外卖门店",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(foodOrderStatusLabel(order.paymentStatus, order.fulfillmentStatus, order.refundStatus),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val preview = order.items.firstOrNull()
            Row(verticalAlignment = Alignment.CenterVertically) {
                FoodProductImage(preview?.image, Modifier.size(82.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(preview?.name ?: "商品信息同步中", maxLines = 2,
                        overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    preview?.sku?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                    Text("订单号 ${order.externalOrderNo}", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatCent(order.amountCent), fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium)
                    Text("共 ${order.totalQuantity.coerceAtLeast(preview?.quantity ?: 0)} 件",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            if (isFoodOrderPayable(order.paymentStatus, order.fulfillmentStatus, order.expiresAt)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = { onPay(order.externalOrderNo) }) { Text("继续支付") }
                }
            }
        }
    }
}

@Composable
fun FoodOrderDetailScreen(
    onBack: () -> Unit,
    onPay: (String) -> Unit,
    viewModel: FoodOrderDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val unavailable = { Toast.makeText(context, "还没开发", Toast.LENGTH_SHORT).show() }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)) {
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.error != null -> Column(Modifier.fillMaxSize()) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Row(Modifier.fillMaxWidth().padding(8.dp)) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回")
                        }
                    }
                }
                Box(Modifier.weight(1f)) {
                    OrderLoadError(requireNotNull(state.error), viewModel::refresh)
                }
            }
            state.order != null -> FoodOrderDetailContent(requireNotNull(state.order), onBack, onPay, unavailable)
        }
    }
}

@Composable
internal fun FoodOrderDetailContent(
    order: FoodOrderDetailDto,
    onBack: () -> Unit,
    onPay: (String) -> Unit,
    unavailable: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                        Spacer(Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable(onClick = unavailable).padding(horizontal = 12.dp)) {
                            Icon(Icons.Outlined.HeadsetMic, "客服")
                            Text("客服", style = MaterialTheme.typography.labelSmall)
                        }
                        IconButton(onClick = unavailable) { Icon(Icons.Outlined.MoreHoriz, "更多") }
                    }
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                        Text(foodOrderStatusLabel(order.paymentStatus, order.fulfillmentStatus, order.refundStatus),
                            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        Text(foodOrderStatusDescription(order),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.titleMedium)
                        if (order.fulfillmentType == "TAKEOUT" && !order.address.isNullOrBlank()) {
                            Spacer(Modifier.height(14.dp))
                            Text("送至 ${order.address}", maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val contact = listOfNotNull(order.recipient, order.phone).filter { it.isNotBlank() }.joinToString("  ")
                            if (contact.isNotBlank()) Text(contact, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (isFoodOrderPayable(order.paymentStatus, order.fulfillmentStatus, order.expiresAt)) {
                            Spacer(Modifier.height(18.dp))
                            Button(onClick = { onPay(order.externalOrderNo) }, modifier = Modifier.fillMaxWidth()) {
                                Text("继续支付 ${formatCent(order.amountCent)}")
                            }
                        }
                    }
                }
            }
        }
        item { DetailProductsCard(order, unavailable) }
        item { PriceDetailsCard(order) }
        item { OrderInformationCard(order) }
    }
}

@Composable
private fun DetailProductsCard(order: FoodOrderDetailDto, unavailable: () -> Unit) {
    Card(
        modifier = Modifier.padding(horizontal = 14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth().clickable(onClick = unavailable), verticalAlignment = Alignment.CenterVertically) {
                Text(order.storeName ?: "外卖门店", modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Icon(Icons.Outlined.ChevronRight, null)
            }
            if (order.items.isEmpty()) {
                Text("商品明细同步中", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                order.items.forEach { item -> DetailProductRow(item) }
            }
        }
    }
}

@Composable
private fun DetailProductRow(item: FoodOrderItemDto) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FoodProductImage(item.image, Modifier.size(72.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            item.sku?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            Text("×${item.quantity}", color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
        }
        Text(formatCent(item.lineAmountCent), fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PriceDetailsCard(order: FoodOrderDetailDto) {
    Card(
        modifier = Modifier.padding(horizontal = 14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("价格明细", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            PriceRow("商品小计", order.subtotalCent)
            PriceRow("配送费", order.deliveryFeeCent)
            if (order.discountCent > 0) PriceRow("优惠", -order.discountCent)
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("共 ${order.totalQuantity} 件", modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("实付 ${formatCent(order.amountCent)}", fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun PriceRow(label: String, amountCent: Long) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if (amountCent < 0) "-${formatCent(-amountCent)}" else formatCent(amountCent))
    }
}

@Composable
private fun OrderInformationCard(order: FoodOrderDetailDto) {
    Card(
        modifier = Modifier.padding(horizontal = 14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Outlined.ReceiptLong, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("订单信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            InformationRow("订单号", order.externalOrderNo)
            InformationRow("下单时间", formatOrderTime(order.createdAt))
            InformationRow("履约方式", if (order.fulfillmentType == "PICKUP") "到店自取" else "外卖配送")
            InformationRow("支付状态", paymentStatusLabel(order.paymentStatus))
        }
    }
}

@Composable
private fun InformationRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(label, modifier = Modifier.width(76.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun FoodProductImage(image: String?, modifier: Modifier = Modifier) {
    AsyncImage(
        model = image?.trim()?.takeIf { it.isNotBlank() },
        contentDescription = "商品图片",
        modifier = modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentScale = ContentScale.Crop,
        placeholder = painterResource(android.R.drawable.ic_menu_gallery),
        error = painterResource(android.R.drawable.ic_menu_gallery),
        fallback = painterResource(android.R.drawable.ic_menu_gallery)
    )
}

@Composable
private fun OrderListSkeleton() {
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(3) {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.fillMaxWidth(0.55f).height(20.dp).clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant))
                    Box(Modifier.fillMaxWidth().height(82.dp).clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant))
                }
            }
        }
    }
}

@Composable
private fun OrderLoadError(message: String, retry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.AutoMirrored.Outlined.ReceiptLong, null, modifier = Modifier.size(54.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = retry) { Text("重新加载") }
    }
}

@Composable
private fun EmptyOrders(hasQuery: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.AutoMirrored.Outlined.ReceiptLong, null, modifier = Modifier.size(58.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text(if (hasQuery) "没有找到相关订单" else "暂无外卖订单",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun filterFoodOrders(
    orders: List<FoodOrderSummaryDto>,
    query: String,
    tab: FoodOrderTab
): List<FoodOrderSummaryDto> {
    val keyword = query.trim()
    return orders.filter { order ->
        (keyword.isEmpty() || order.externalOrderNo.contains(keyword, ignoreCase = true)
                || order.storeName.orEmpty().contains(keyword, ignoreCase = true)) &&
                when (tab) {
                    FoodOrderTab.ALL -> true
                    FoodOrderTab.ACTIVE -> isActiveFoodOrder(order.paymentStatus, order.fulfillmentStatus, order.refundStatus)
                    FoodOrderTab.COMPLETED -> order.fulfillmentStatus == "COMPLETED" && order.refundStatus in setOf("NONE", "REJECTED")
                    FoodOrderTab.REFUND -> order.refundStatus != "NONE"
                }
    }
}

internal fun isActiveFoodOrder(payment: String, fulfillment: String, refund: String): Boolean =
    refund == "NONE" && fulfillment !in setOf("COMPLETED", "CANCELLED") && payment != "CLOSED"

internal fun foodOrderStatusLabel(payment: String, fulfillment: String, refund: String): String = when {
    refund == "REFUNDED" -> "已退款"
    refund == "PROCESSING" -> "退款处理中"
    refund == "REQUESTED" -> "退款申请中"
    refund == "REJECTED" -> "退款未通过"
    payment == "CLOSED" || fulfillment == "CANCELLED" -> "已取消"
    payment == "UNPAID" -> "待支付"
    payment == "FAILED" -> "支付失败"
    payment == "PROCESSING" -> "支付处理中"
    fulfillment == "PREPARING" -> "商家备餐中"
    fulfillment == "DELIVERING" -> "配送中"
    fulfillment == "READY_FOR_PICKUP" -> "待取餐"
    fulfillment == "COMPLETED" -> "已完成"
    else -> "已下单"
}

private fun foodOrderStatusDescription(order: FoodOrderDetailDto): String = when {
    order.refundStatus in setOf("REQUESTED", "PROCESSING") -> "退款申请正在处理中，请留意订单状态"
    order.refundStatus == "REFUNDED" -> "退款已原路退回 MiniPay 钱包"
    order.paymentStatus == "UNPAID" -> "请在订单关闭前完成付款"
    order.paymentStatus == "FAILED" -> "上次支付未完成，可以重新支付"
    order.fulfillmentStatus == "PREPARING" -> "商家正在准备商品"
    order.fulfillmentStatus == "DELIVERING" -> "订单正在配送，请耐心等待"
    order.fulfillmentStatus == "READY_FOR_PICKUP" -> "商品已准备好，请及时到店取餐"
    order.fulfillmentStatus == "COMPLETED" -> "订单已完成"
    order.fulfillmentStatus == "CANCELLED" || order.paymentStatus == "CLOSED" -> "订单已取消"
    order.fulfillmentType == "PICKUP" -> "订单已提交，请留意取餐状态"
    else -> "订单已提交，请留意配送状态"
}

internal fun isFoodOrderPayable(payment: String, fulfillment: String, expiresAt: String, now: Instant = Instant.now()): Boolean =
    payment in setOf("UNPAID", "FAILED") && fulfillment != "CANCELLED" &&
            runCatching { Instant.parse(expiresAt).isAfter(now) }.getOrDefault(false)

private fun paymentStatusLabel(status: String): String = when (status) {
    "UNPAID" -> "待支付"
    "PROCESSING" -> "支付处理中"
    "PAID" -> "已支付"
    "FAILED" -> "支付失败"
    "CLOSED" -> "已关闭"
    else -> status
}

internal fun formatCent(amountCent: Long): String = "¥${BigDecimal.valueOf(amountCent, 2).toPlainString()}"

private fun formatOrderTime(raw: String): String = runCatching {
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .format(Instant.parse(raw).atZone(ZoneId.systemDefault()))
}.getOrDefault(raw)
