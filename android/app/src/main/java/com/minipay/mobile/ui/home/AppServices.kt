package com.minipay.mobile.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.minipay.mobile.finance.FinanceDestination

sealed interface ServiceDestination {
    data class Finance(val destination: FinanceDestination) : ServiceDestination
    data object AddFriend : ServiceDestination
    data object Food : ServiceDestination
    data class Unavailable(val label: String) : ServiceDestination
}

data class AppService(
    val id: String,
    val name: String,
    val aliases: List<String>,
    val destination: ServiceDestination
) {
    val available: Boolean get() = destination !is ServiceDestination.Unavailable
}

val appServices = listOf(
    AppService("scan", "扫一扫", listOf("扫码", "二维码"), ServiceDestination.Finance(FinanceDestination.SCAN)),
    AppService("receive", "收付款", listOf("收款", "付款", "收钱"), ServiceDestination.Finance(FinanceDestination.RECEIVE)),
    AppService("transfer", "转账", listOf("汇款"), ServiceDestination.Finance(FinanceDestination.TRANSFER)),
    AppService("wallet", "钱包", listOf("余额"), ServiceDestination.Finance(FinanceDestination.WALLET)),
    AppService("bills", "账单", listOf("交易记录", "支付消息"), ServiceDestination.Finance(FinanceDestination.ALL_BILLS)),
    AppService("food", "外卖", listOf("点餐", "餐饮", "自取", "奶茶", "咖啡"), ServiceDestination.Food),
    AppService("add_friend", "添加朋友", listOf("好友", "加好友"), ServiceDestination.AddFriend),
    AppService("coupon", "神券", listOf("优惠券"), ServiceDestination.Unavailable("神券")),
    AppService("life", "生活缴费", listOf("水费", "电费", "燃气"), ServiceDestination.Unavailable("生活缴费")),
    AppService("travel", "火车票机票", listOf("火车", "机票", "出行"), ServiceDestination.Unavailable("火车票机票")),
    AppService("health", "医疗健康", listOf("医院", "挂号"), ServiceDestination.Unavailable("医疗健康")),
    AppService("credit", "芝麻信用", listOf("信用"), ServiceDestination.Unavailable("芝麻信用")),
    AppService("cards", "我的信用卡", listOf("信用卡"), ServiceDestination.Unavailable("我的信用卡")),
    AppService("mobile", "手机营业厅", listOf("话费", "流量"), ServiceDestination.Unavailable("手机营业厅")),
    AppService("balance", "余额宝", listOf("理财"), ServiceDestination.Unavailable("余额宝")),
    AppService("delivery", "我的快递", listOf("物流", "包裹"), ServiceDestination.Unavailable("我的快递"))
)

fun searchServices(query: String): List<AppService> {
    val normalized = query.trim()
    if (normalized.isEmpty()) return appServices
    return appServices.filter { service ->
        service.name.contains(normalized, ignoreCase = true) ||
            service.aliases.any { it.contains(normalized, ignoreCase = true) }
    }
}

fun appServiceIcon(service: AppService): ImageVector = when (service.id) {
    "scan" -> Icons.Outlined.CropFree
    "receive" -> Icons.Outlined.Payments
    "transfer" -> Icons.Outlined.SwapHoriz
    "wallet" -> Icons.Outlined.AccountBalanceWallet
    "bills" -> Icons.Outlined.ReceiptLong
    "add_friend" -> Icons.Outlined.PersonAdd
    else -> Icons.Outlined.AddCircleOutline
}

fun appServiceColor(service: AppService): Color = when (service.id) {
    "scan", "bills" -> Color(0xFF1677FF)
    "receive" -> Color(0xFF5B6CFF)
    "transfer" -> Color(0xFF0AA39A)
    "wallet" -> Color(0xFF7A5AF8)
    "add_friend" -> Color(0xFFFF7A45)
    else -> Color(0xFF667085)
}
