package com.minipay.mobile.ui.home

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.CurrencyYen
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.CreditScore
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Fastfood
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minipay.mobile.R
import com.minipay.mobile.finance.FinanceDestination
import com.minipay.mobile.finance.WalletBill
import com.minipay.mobile.ui.theme.MilingBorder
import com.minipay.mobile.ui.theme.MilingDivider
import com.minipay.mobile.ui.theme.MilingGradientMiddle
import com.minipay.mobile.ui.theme.MilingGradientStart
import com.minipay.mobile.ui.theme.MilingHomeBackground
import com.minipay.mobile.ui.theme.MilingHomeTokens
import com.minipay.mobile.ui.theme.MilingIconSecondary
import com.minipay.mobile.ui.theme.MilingPrimary
import com.minipay.mobile.ui.theme.MilingSurface
import com.minipay.mobile.ui.theme.MilingSurfaceSubtle
import com.minipay.mobile.ui.theme.MilingTextMuted
import com.minipay.mobile.ui.theme.MilingTextPrimary
import com.minipay.mobile.ui.theme.MilingTextSecondary
import java.time.Duration
import java.time.Instant

sealed interface RecommendationHomeAction {
    data object OpenMiling : RecommendationHomeAction
    data object OpenMessages : RecommendationHomeAction
    data object OpenPaymentMessages : RecommendationHomeAction
    data object OpenProfile : RecommendationHomeAction
    data object OpenServiceSearch : RecommendationHomeAction
    data object OpenCommonApps : RecommendationHomeAction
    data object RetryLocation : RecommendationHomeAction
    data object RetryRecentBills : RecommendationHomeAction
    data object AddFriend : RecommendationHomeAction
    data object OpenFood : RecommendationHomeAction
    data class OpenFinance(val destination: FinanceDestination) : RecommendationHomeAction
    data class ShowUnavailable(val label: String) : RecommendationHomeAction
}

private data class PrimaryAction(
    val label: String,
    val icon: ImageVector,
    val destination: FinanceDestination,
    val tag: String
)

private data class ServiceShortcut(
    val label: String,
    val icon: ImageVector,
    val color: Color,
    val background: Color,
    val tag: String
)

private data class PromotionStyle(
    val title: Color,
    val subtitle: Color,
    val button: Color
)

private class HomeLayoutSpec(
    widthScale: Float
) {
    private val horizontalScale = widthScale.coerceIn(.86f, 1f)

    fun x(value: Float): Dp = (value * horizontalScale).dp
    fun y(value: Float): Dp = (value * horizontalScale).dp
    fun icon(value: Float): Dp = (value * horizontalScale).dp
}

private val primaryActions = listOf(
    PrimaryAction("扫一扫", Icons.Outlined.CropFree, FinanceDestination.SCAN, "home_scan"),
    PrimaryAction("收付款", Icons.Outlined.CurrencyYen, FinanceDestination.RECEIVE, "home_receive"),
    PrimaryAction("转账", Icons.Outlined.SyncAlt, FinanceDestination.TRANSFER, "home_transfer"),
    PrimaryAction("钱包", Icons.Outlined.AccountBalanceWallet, FinanceDestination.WALLET, "home_wallet")
)

private val serviceShortcuts = listOf(
    ServiceShortcut("外卖", Icons.Rounded.Fastfood, Color(0xFF7356E8), Color(0xFFEDE8FF), "service_food"),
    ServiceShortcut("生活缴费", Icons.Rounded.WaterDrop, Color(0xFF1677F2), Color(0xFFE8F3FF), "service_life"),
    ServiceShortcut("火车票机票", Icons.Rounded.Flight, Color(0xFF246FE5), Color(0xFFE9F1FF), "service_travel"),
    ServiceShortcut("医疗健康", Icons.Rounded.HealthAndSafety, Color(0xFF18AA84), Color(0xFFE2F8F2), "service_health"),
    ServiceShortcut("芝麻信用", Icons.Rounded.CreditScore, Color(0xFF8162EA), Color(0xFFF0ECFF), "service_credit"),
    ServiceShortcut("我的信用卡", Icons.Rounded.CreditCard, Color(0xFFFF6255), Color(0xFFFFECE8), "service_cards"),
    ServiceShortcut("手机营业厅", Icons.Rounded.PhoneAndroid, Color(0xFF257EED), Color(0xFFE8F2FF), "service_mobile"),
    ServiceShortcut("余额宝", Icons.Rounded.PieChart, Color(0xFFFF9348), Color(0xFFFFEFE4), "service_balance"),
    ServiceShortcut("我的快递", Icons.Rounded.LocalShipping, Color(0xFF287AF3), Color(0xFFE8F2FF), "service_delivery"),
    ServiceShortcut("更多", Icons.Rounded.Apps, Color(0xFF78A4EE), Color(0xFFEDF4FF), "service_more")
)

private object HomeVisualTokens {
    val HeaderGradient = listOf(MilingGradientStart, MilingPrimary, MilingGradientMiddle)
    val SearchPlaceholder = Color(0xFF969BA5)
    val ServiceCardBorder = Color(0xFFE2E8F1)
    val QuickPay = PromotionStyle(Color(0xFF0A43A3), Color(0xFF31579E), Color(0xFF5976F2))
    val Weekend = PromotionStyle(Color(0xFF2C2340), Color(0xFF5F526B), Color(0xFF8B55E8))
    val Beverage = PromotionStyle(Color(0xFFD74725), Color(0xFFB74D34), Color(0xFFFF7A3D))
    val Digital = PromotionStyle(Color(0xFF176C65), Color(0xFF4A7873), Color(0xFF20A58D))
    val Utilities = PromotionStyle(Color(0xFF173D72), Color(0xFF3D5D7B), MilingPrimary)
}

/** Compact, single-screen reconstruction of the reference home screen. */
@Composable
fun RecommendationHomeScreen(
    onAction: (RecommendationHomeAction) -> Unit,
    showMessageReminder: Boolean = false,
    city: String = "定位中",
    weather: String = "",
    locationError: String? = null,
    recentBills: List<WalletBill> = emptyList(),
    billsLoading: Boolean = false,
    billsError: String? = null,
    commonApps: List<AppService> = emptyList(),
    showBottomNavigation: Boolean = true,
    modifier: Modifier = Modifier
) {
    HomeSystemBars()
    var plusMenuExpanded by remember { mutableStateOf(false) }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MilingHomeBackground)
            .testTag("home_root")
    ) {
        val horizontalScale = (maxWidth.value / 360f).coerceIn(.86f, 1f)
        val spec = HomeLayoutSpec(horizontalScale)

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = if (showBottomNavigation) 92.dp else 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HeaderAndServices(
                onAction = onAction,
                spec = spec,
                expanded = false,
                city = city,
                weather = weather,
                locationError = locationError,
                onOpenPlus = { plusMenuExpanded = true }
            )
            Spacer(Modifier.height(MilingHomeTokens.SectionGap))
            CommonAppsSection(commonApps, onAction, spec)
            Spacer(Modifier.height(MilingHomeTokens.SectionGap))
            RecentMessages(onAction, spec, false, recentBills, billsLoading, billsError)
            Spacer(Modifier.height(MilingHomeTokens.SectionGap))
            RecommendationSection(onAction, spec, false)
        }

        if (showBottomNavigation) {
            HomeBottomNavigation(
                onAction = onAction,
                showMessageReminder = showMessageReminder,
                spec = spec,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .widthIn(max = 460.dp)
            )
        }
        if (plusMenuExpanded) QuickPlusMenuOverlay(
            onDismiss = { plusMenuExpanded = false },
            onAction = {
                plusMenuExpanded = false
                when (it) {
                    QuickPlusAction.SCAN -> onAction(RecommendationHomeAction.OpenFinance(FinanceDestination.SCAN))
                    QuickPlusAction.RECEIVE -> onAction(RecommendationHomeAction.OpenFinance(FinanceDestination.RECEIVE))
                    QuickPlusAction.ADD_FRIEND -> onAction(RecommendationHomeAction.AddFriend)
                }
            }
        )
    }
}

@Composable
private fun HomeSystemBars() {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity) {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.light(Color.White.toArgb(), Color.White.toArgb())
        )
        onDispose {
            activity.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb()),
                navigationBarStyle = SystemBarStyle.light(Color.White.toArgb(), Color.White.toArgb())
            )
        }
    }
}

private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun HeaderAndServices(
    onAction: (RecommendationHomeAction) -> Unit,
    spec: HomeLayoutSpec,
    expanded: Boolean,
    city: String,
    weather: String,
    locationError: String?,
    onOpenPlus: () -> Unit
) {
    val totalHeight = spec.y(334f)
    val headerHeight = spec.y(198f)
    val serviceHeight = spec.y(154f)
    Box(modifier = Modifier.fillMaxWidth().height(totalHeight).testTag("home_header_services")) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(headerHeight)
                .background(Brush.verticalGradient(HomeVisualTokens.HeaderGradient))
                .statusBarsPadding()
                .padding(horizontal = MilingHomeTokens.PageHorizontal)
        ) {
            Spacer(Modifier.height(spec.y(3f)))
            HomeSearchRow(onAction, spec, city, weather, locationError, onOpenPlus)
            Spacer(Modifier.height(spec.y(3f)))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_primary_actions")
            ) {
                primaryActions.forEach { action ->
                    HeaderAction(
                        item = action,
                        spec = spec,
                        modifier = Modifier.weight(1f)
                    ) { onAction(RecommendationHomeAction.OpenFinance(action.destination)) }
                }
            }
        }
        ServiceGrid(
            onAction = onAction,
            spec = spec,
            expanded = expanded,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = MilingHomeTokens.PageHorizontal)
                .height(serviceHeight)
        )
    }
}

@Composable
private fun HomeSearchRow(
    onAction: (RecommendationHomeAction) -> Unit,
    spec: HomeLayoutSpec,
    city: String,
    weather: String,
    locationError: String?,
    onOpenPlus: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(spec.y(52f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.width(spec.x(66f)).height(48.dp)
                .clickable { onAction(RecommendationHomeAction.RetryLocation) }
                .testTag("home_location"),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    city,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Icon(Icons.Outlined.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(spec.icon(14f)))
            }
            Text(
                locationError ?: weather,
                color = Color.White.copy(alpha = .9f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(spec.y(48f))
                .clickable(role = Role.Button) { onAction(RecommendationHomeAction.OpenServiceSearch) }
                .semantics { contentDescription = "搜索服务" }
                .testTag("home_search"),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MilingHomeTokens.SearchHeight),
                color = Color.White,
                shape = RoundedCornerShape(spec.x(22f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = spec.x(11f)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Search, null, tint = Color(0xFF9299A4), modifier = Modifier.size(spec.icon(22f)))
                    Spacer(Modifier.width(spec.x(5f)))
                    Text(
                        "搜索服务",
                        color = HomeVisualTokens.SearchPlaceholder,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "搜索",
                        color = MilingPrimary,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp)
                    )
                }
            }
        }
        Spacer(Modifier.width(spec.x(3f)))
        Box(
            modifier = Modifier
                .size(spec.x(48f))
                .clickable(role = Role.Button, onClick = onOpenPlus)
                .semantics { contentDescription = "更多服务" }
                .testTag("home_add_service"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.AddCircleOutline,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(spec.icon(30f))
            )
        }
    }
}

@Composable
private fun HeaderAction(
    item: PrimaryAction,
    spec: HomeLayoutSpec,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .height(spec.y(90f))
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(item.tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            item.icon,
            contentDescription = item.label,
            tint = Color.White,
            modifier = Modifier.size(spec.icon(42f))
        )
        Spacer(Modifier.height(spec.y(5f)))
        Text(
            item.label,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium
            ),
            modifier = Modifier.testTag("${item.tag}_label")
        )
    }
}

@Composable
private fun ServiceGrid(
    onAction: (RecommendationHomeAction) -> Unit,
    spec: HomeLayoutSpec,
    expanded: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_service_grid"),
        shape = RoundedCornerShape(MilingHomeTokens.CardRadius),
        color = MilingSurface,
        border = BorderStroke(1.dp, HomeVisualTokens.ServiceCardBorder),
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = spec.x(3f), vertical = spec.y(5f))) {
            serviceShortcuts.chunked(5).forEachIndexed { index, row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    row.forEach { shortcut ->
                        ServiceItem(shortcut, spec, expanded, Modifier.weight(1f)) {
                            if (shortcut.label == "更多") {
                                onAction(RecommendationHomeAction.OpenServiceSearch)
                            } else when (val destination = appServices.firstOrNull { it.name == shortcut.label }?.destination) {
                                is ServiceDestination.Finance -> onAction(RecommendationHomeAction.OpenFinance(destination.destination))
                                ServiceDestination.AddFriend -> onAction(RecommendationHomeAction.AddFriend)
                                ServiceDestination.Food -> onAction(RecommendationHomeAction.OpenFood)
                                is ServiceDestination.Unavailable -> onAction(RecommendationHomeAction.ShowUnavailable(destination.label))
                                null -> onAction(RecommendationHomeAction.ShowUnavailable(shortcut.label))
                            }
                        }
                    }
                }
                if (index == 0) Spacer(Modifier.height(spec.y(2f)))
            }
        }
    }
}

@Composable
private fun ServiceItem(
    item: ServiceShortcut,
    spec: HomeLayoutSpec,
    expanded: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .height(spec.y(70f))
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(item.tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(spec.icon(40f))
                .clip(RoundedCornerShape(spec.x(12f)))
                .background(item.background),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                item.icon,
                contentDescription = item.label,
                tint = item.color,
                modifier = Modifier.size(spec.icon(28f))
            )
        }
        Spacer(Modifier.height(spec.y(3f)))
        Text(
            item.label,
            color = MilingTextPrimary,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
    }
}

@Composable
private fun CommonAppsSection(
    apps: List<AppService>,
    onAction: (RecommendationHomeAction) -> Unit,
    spec: HomeLayoutSpec
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MilingHomeTokens.PageHorizontal)
            .height(spec.y(126f))
            .testTag("home_common_apps"),
        shape = RoundedCornerShape(MilingHomeTokens.CardRadius),
        color = MilingSurface,
        border = BorderStroke(1.dp, MilingBorder),
        shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(horizontal = spec.x(12f), vertical = spec.y(8f))) {
            Row(
                modifier = Modifier.fillMaxWidth().height(spec.y(32f)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFE8F3FF)) {
                    Text(
                        "常用",
                        color = MilingPrimary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .height(48.dp)
                        .clickable(role = Role.Button) { onAction(RecommendationHomeAction.OpenCommonApps) }
                        .semantics { contentDescription = "管理全部常用应用" }
                        .testTag("home_common_apps_all"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("全部", style = MaterialTheme.typography.bodyMedium, color = MilingTextSecondary)
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MilingIconSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (apps.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(spec.y(76f))
                        .clickable(role = Role.Button) { onAction(RecommendationHomeAction.OpenCommonApps) }
                        .semantics { contentDescription = "添加常用应用" }
                        .testTag("home_common_apps_empty"),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.AddCircleOutline, contentDescription = null, tint = MilingPrimary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("添加常用应用", style = MaterialTheme.typography.bodyMedium, color = MilingPrimary)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().height(spec.y(76f)),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    apps.take(5).forEach { service ->
                        Column(
                            modifier = Modifier
                                .width(spec.x(64f))
                                .height(spec.y(76f))
                                .clickable(role = Role.Button) { dispatchService(service, onAction) }
                                .semantics { contentDescription = "打开${service.name}" }
                                .testTag("home_common_app_${service.id}"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            ServiceIcon(service, size = spec.icon(40f), iconSize = spec.icon(27f))
                            Spacer(Modifier.height(4.dp))
                            Text(
                                service.name,
                                color = MilingTextPrimary,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun dispatchService(
    service: AppService,
    onAction: (RecommendationHomeAction) -> Unit
) {
    when (val destination = service.destination) {
        is ServiceDestination.Finance -> onAction(RecommendationHomeAction.OpenFinance(destination.destination))
        ServiceDestination.AddFriend -> onAction(RecommendationHomeAction.AddFriend)
        ServiceDestination.Food -> onAction(RecommendationHomeAction.OpenFood)
        is ServiceDestination.Unavailable -> onAction(RecommendationHomeAction.ShowUnavailable(destination.label))
    }
}

@Composable
private fun RecentMessages(
    onAction: (RecommendationHomeAction) -> Unit,
    spec: HomeLayoutSpec,
    expanded: Boolean,
    bills: List<WalletBill>,
    loading: Boolean,
    error: String?
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MilingHomeTokens.PageHorizontal)
            .height(spec.y(106f))
            .clickable(role = Role.Button) {
                onAction(
                    if (error != null && bills.isEmpty()) RecommendationHomeAction.RetryRecentBills
                    else RecommendationHomeAction.OpenPaymentMessages
                )
            }
            .testTag("home_recent_messages"),
        shape = RoundedCornerShape(MilingHomeTokens.CardRadius),
        color = MilingSurface,
        border = BorderStroke(1.dp, MilingBorder)
    ) {
        Column(modifier = Modifier.padding(horizontal = spec.x(12f), vertical = spec.y(6f))) {
            Row(modifier = Modifier.height(spec.y(28f)), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "最近消息",
                    color = MilingTextPrimary,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MilingIconSecondary, modifier = Modifier.size(spec.icon(18f)))
            }
            when {
                loading && bills.isEmpty() -> NoticeRow("支付", "正在加载最近消息", "", spec, expanded)
                error != null && bills.isEmpty() -> NoticeRow("支付", error, "点击重试", spec, expanded)
                bills.isEmpty() -> NoticeRow("支付", "暂无最近支付消息", "", spec, expanded)
                else -> bills.take(2).forEachIndexed { index, bill ->
                    val income = bill.direction == "INCOME"
                    NoticeRow(
                        "支付",
                        (if (income) "收款成功 " else "付款成功 ") + "¥%.2f".format(bill.amountCent / 100.0),
                        formatRecentTime(bill.occurredAt),
                        spec,
                        expanded
                    )
                    if (index < bills.take(2).lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(MilingDivider))
                }
            }
        }
    }
}

@Composable
private fun NoticeRow(
    title: String,
    description: String,
    time: String,
    spec: HomeLayoutSpec,
    expanded: Boolean
) {
    Row(
        modifier = Modifier.height(spec.y(33f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(spec.icon(26f)).background(MilingPrimary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Payments, null, tint = Color.White, modifier = Modifier.size(spec.icon(17f)))
        }
        Spacer(Modifier.width(spec.x(5f)))
        Text(
            title,
            color = MilingTextPrimary,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        )
        Spacer(Modifier.width(spec.x(6f)))
        Text(
            description,
            color = MilingTextSecondary,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
        Text(
            time,
            color = MilingTextMuted,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun formatRecentTime(value: String): String = runCatching {
    val duration = Duration.between(Instant.parse(value), Instant.now())
    when {
        duration.toMinutes() < 1 -> "刚刚"
        duration.toHours() < 1 -> "${duration.toMinutes()}分钟前"
        duration.toDays() < 1 -> "${duration.toHours()}小时前"
        else -> "${duration.toDays()}天前"
    }
}.getOrDefault(value.take(10))

@Composable
private fun RecommendationSection(
    onAction: (RecommendationHomeAction) -> Unit,
    spec: HomeLayoutSpec,
    expanded: Boolean
) {
    val sectionHeight = spec.y(344f)
    val featureHeight = spec.y(212f)
    val utilityHeight = spec.y(62f)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MilingHomeTokens.PageHorizontal)
            .height(sectionHeight)
            .testTag("home_recommendation_section"),
        shape = RoundedCornerShape(MilingHomeTokens.CardRadius),
        color = MilingSurface,
        border = BorderStroke(1.dp, MilingBorder)
    ) {
        Column(modifier = Modifier.padding(horizontal = spec.x(8f), vertical = spec.y(8f))) {
            RecommendationTabs(spec, expanded)
            Spacer(Modifier.height(spec.y(4f)))
            Row(modifier = Modifier.fillMaxWidth().height(featureHeight), horizontalArrangement = Arrangement.spacedBy(spec.x(5f))) {
                ImagePromotion(
                    title = "快捷支付享好礼",
                    subtitle = "绑定银行卡立减",
                    tag = "home_promo_payment",
                    drawable = R.drawable.home_promo_quickpay,
                    modifier = Modifier.weight(.92f),
                    style = HomeVisualTokens.QuickPay,
                    button = "立即领取",
                    spec = spec,
                    expanded = expanded,
                    onAction = onAction
                )
                Column(modifier = Modifier.weight(1.18f), verticalArrangement = Arrangement.spacedBy(spec.y(5f))) {
                    ImagePromotion(
                        "周末超值特惠", "爆款好物低至5折", "home_promo_weekend",
                        R.drawable.home_promo_weekend, Modifier.fillMaxWidth().weight(1f), HomeVisualTokens.Weekend,
                        "去看看", spec, expanded, onAction
                    )
                    Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(spec.x(4f))) {
                        ImagePromotion(
                            "夏日饮品季", "满30减8", "home_promo_beverage",
                            R.drawable.home_promo_beverage, Modifier.weight(1f), HomeVisualTokens.Beverage,
                            "去抢购", spec, expanded, onAction
                        )
                        ImagePromotion(
                            "数码好物", "精选热卖", "home_promo_digital",
                            R.drawable.home_promo_digital, Modifier.weight(1f), HomeVisualTokens.Digital,
                            "立即选购", spec, expanded, onAction
                        )
                    }
                }
            }
            Spacer(Modifier.height(spec.y(6f)))
            ImagePromotion(
                "生活缴费领优惠", "水电燃气官方直供 安全便捷", "home_promo_life",
                R.drawable.home_promo_utilities, Modifier.fillMaxWidth().height(utilityHeight), HomeVisualTokens.Utilities,
                "去缴费", spec, expanded, onAction
            )
        }
    }
}

@Composable
private fun RecommendationTabs(spec: HomeLayoutSpec, expanded: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().height(spec.y(44f)),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.Top
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RecommendationTabText("为你推荐", selected = true)
            Spacer(Modifier.height(spec.y(4f)))
            Box(Modifier.width(spec.x(20f)).height(spec.y(3f)).clip(CircleShape).background(MilingPrimary))
        }
        RecommendationTabText("闪购", selected = false)
        RecommendationTabText("秒杀", selected = false)
    }
}

@Composable
private fun RecommendationTabText(text: String, selected: Boolean) {
    Text(
        text,
        color = MilingTextPrimary,
        style = MaterialTheme.typography.titleMedium.copy(
            fontSize = if (selected) 18.sp else 16.sp,
            lineHeight = 22.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    )
}

@Composable
private fun ImagePromotion(
    title: String,
    subtitle: String,
    tag: String,
    drawable: Int,
    modifier: Modifier,
    style: PromotionStyle,
    button: String,
    spec: HomeLayoutSpec,
    expanded: Boolean,
    onAction: (RecommendationHomeAction) -> Unit
) {
    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(spec.x(12f)))
            .clickable(role = Role.Button) { onAction(RecommendationHomeAction.ShowUnavailable(title)) }
            .testTag(tag)
    ) {
        val compact = maxHeight < spec.y(80f)
        val narrow = maxWidth < spec.x(130f)
        Image(
            painter = painterResource(drawable),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Column(
            modifier = Modifier.fillMaxSize().padding(
                horizontal = if (compact) spec.x(5f) else spec.x(8f),
                vertical = if (compact) spec.y(5f) else spec.y(8f)
            ),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    title,
                    color = style.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = if (compact || narrow) 12.sp else 15.sp,
                        lineHeight = if (compact || narrow) 15.sp else 19.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
                Spacer(Modifier.height(spec.y(1f)))
                Text(
                    subtitle,
                    color = style.subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = if (compact || narrow) 10.sp else 12.sp,
                        lineHeight = if (compact || narrow) 13.sp else 16.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            }
            Surface(color = style.button, shape = CircleShape) {
                Text(
                    button,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = if (compact || narrow) 10.sp else 11.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = Modifier.padding(
                        horizontal = if (compact) spec.x(6f) else spec.x(9f),
                        vertical = if (compact) spec.y(2f) else spec.y(3f)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }
}

@Composable
private fun HomeBottomNavigation(
    onAction: (RecommendationHomeAction) -> Unit,
    showMessageReminder: Boolean,
    spec: HomeLayoutSpec,
    modifier: Modifier
) {
    RootBottomNavigation(
        selected = RootTab.RECOMMENDATION,
        onSelect = { tab ->
            when (tab) {
                RootTab.RECOMMENDATION -> Unit
                RootTab.MESSAGES -> onAction(RecommendationHomeAction.OpenMessages)
                RootTab.PROFILE -> onAction(RecommendationHomeAction.OpenProfile)
            }
        },
        onOpenMiling = { onAction(RecommendationHomeAction.OpenMiling) },
        showMessageReminder = showMessageReminder,
        modifier = modifier
    )
}
