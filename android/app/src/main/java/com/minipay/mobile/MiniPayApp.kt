package com.minipay.mobile

import android.content.Context
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import android.util.Log
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.webkit.WebViewFeature
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.minipay.bridge.FoodBridgePolicy
import com.minipay.mobile.auth.AuthUiState
import com.minipay.mobile.auth.AuthViewModel
import com.minipay.mobile.auth.AuthRepository
import com.minipay.mobile.ai.MilingAiViewModel
import com.minipay.mobile.ai.AiInputMode
import com.minipay.mobile.authorization.ApplicationAuthorizationScreen
import com.minipay.mobile.food.FoodAgreementScreen
import com.minipay.mobile.food.FoodAuthorizationScreen
import com.minipay.mobile.food.FoodEntryScreen
import com.minipay.mobile.food.FoodIntegrationViewModel
import com.minipay.mobile.food.FoodOrderCenterScreen
import com.minipay.mobile.food.FoodOrderDetailScreen
import com.minipay.mobile.food.NativeFoodPaymentScreen
import com.minipay.mobile.ui.auth.LoginScreen
import com.minipay.mobile.ui.auth.VerificationCodeScreen
import com.minipay.mobile.ui.home.MilingHomeAction
import com.minipay.mobile.ui.home.MilingHomeScreen
import com.minipay.mobile.ui.home.AiAddressDialog
import com.minipay.mobile.ui.home.AiPaymentConfirmationDialog
import com.minipay.mobile.ui.home.RecommendationHomeAction
import com.minipay.mobile.ui.home.RecommendationHomeScreen
import com.minipay.mobile.ui.onboarding.OnboardingRoute
import com.minipay.mobile.profile.ProfileLoadState
import com.minipay.mobile.profile.ProfileViewModel
import com.minipay.mobile.profile.usableAvatarUrl
import com.minipay.mobile.ui.components.AvatarPreloadEffect
import com.minipay.mobile.ui.components.clearPrivateImageCache
import com.minipay.mobile.ui.profile.EditProfileRoute
import com.minipay.mobile.ui.profile.FeaturePlaceholderScreen
import com.minipay.mobile.ui.profile.ProfileRoute
import com.minipay.mobile.ui.profile.AccountSecurityRoute
import com.minipay.mobile.ui.profile.MemorySettingsRoute
import com.minipay.mobile.finance.FinanceDestination
import com.minipay.mobile.finance.PaymentOperation
import com.minipay.mobile.finance.TransferRecipientUi
import com.minipay.mobile.finance.TransferRecipientOrigin
import com.minipay.mobile.finance.TransferSource
import com.minipay.mobile.ui.finance.FinanceRoute
import com.minipay.mobile.ui.finance.UnifiedPaymentResultScreen
import com.minipay.mobile.chat.Conversation
import com.minipay.mobile.chat.ConversationListViewModel
import com.minipay.mobile.chat.ChatDetailViewModel
import com.minipay.mobile.chat.CallRealtimeService
import com.minipay.mobile.chat.FriendApiService
import com.minipay.mobile.ui.chat.ChatRoute
import com.minipay.mobile.ui.chat.FriendSettingsScreen
import com.minipay.mobile.ui.chat.RemarkSettingsScreen
import com.minipay.mobile.ui.contacts.ContactsRoute
import com.minipay.mobile.ui.contacts.AddFriendScreen
import com.minipay.mobile.ui.contacts.MyQrCodeScreen
import com.minipay.mobile.ui.contacts.GroupConversationListRoute
import com.minipay.mobile.ui.contacts.PublicFriendCardRoute
import com.minipay.mobile.ui.contacts.FriendRequestListRoute
import com.minipay.mobile.ui.group.CreateGroupRoute
import com.minipay.mobile.ui.group.GroupInfoRoute
import com.minipay.mobile.ui.group.GroupMemberPickerRoute
import com.minipay.mobile.ui.group.GroupTransferMemberPickerRoute
import com.minipay.mobile.ui.home.ConversationListScreen
import com.minipay.mobile.ui.home.MessageCenterScreen
import com.minipay.mobile.ui.home.PaymentMessagesViewModel
import com.minipay.mobile.ui.home.RootBottomNavigation
import com.minipay.mobile.ui.home.RootTab
import com.minipay.mobile.ui.home.ServiceDestination
import com.minipay.mobile.ui.home.ServiceSearchScreen
import com.minipay.mobile.ui.home.CommonAppsManageScreen
import com.minipay.mobile.home.CommonAppsViewModel
import com.minipay.mobile.home.HomeViewModel
import com.minipay.mobile.ui.theme.MilingHomeTokens
import com.minipay.mobile.network.AutoRefreshEffect
import com.minipay.mobile.ui.search.SearchFriendRoute
import com.minipay.mobile.ui.search.SearchFriendScreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.json.JSONObject
import java.util.UUID

private object AppRoute {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val CHAT = "chat"
    const val CONVERSATION_LIST = "conversation-list"
    const val CHAT_DETAIL = "chat/{conversationId}?name={name}"
    const val CHAT_SETTINGS = "chat-settings/{conversationId}?name={name}"
    const val CHAT_REMARKS = "chat-remarks/{conversationId}?name={name}"
    const val GROUP_INFO = "group-info/{conversationId}"
    const val GROUP_PICK = "group-pick/{conversationId}?removing={removing}"
    const val GROUP_TRANSFER_PICK = "group-transfer/{conversationId}"
    const val CONTACTS = "contacts"
    const val GROUP_CONVERSATIONS = "group-conversations"
    const val FRIEND_REQUESTS = "friend-requests"
    const val ADD_FRIEND = "add-friend"
    const val SEARCH_FRIEND = "search-friend?query={query}"
    const val PUBLIC_FRIEND_CARD = "public-friend-card/{minipayNo}"
    const val MY_QR_CODE = "my-qr-code"
    const val CREATE_GROUP = "create-group"
    const val FOOD_ENTRY = "food-entry"
    const val FOOD_CONSENT = "food-consent"
    const val FOOD_AGREEMENT = "food-agreement/{page}"
    const val FOOD_WEB = "food-web"
    const val FOOD_ORDERS = "food-orders"
    const val FOOD_ORDER_DETAIL = "food-orders/{orderRefId}"
    const val FOOD_PAYMENT = "food-payment/{externalOrderNo}?orderRefId={orderRefId}"
    const val PAYMENT = "payment"
    const val PROFILE = "profile"
    const val EDIT_PROFILE = "profile/edit"
    const val ACCOUNT_SECURITY = "profile/account-security"
    const val APPLICATION_AUTHORIZATIONS = "profile/application-authorizations"
    const val MEMORY_SETTINGS = "profile/memory"
    const val PLACEHOLDER = "profile/feature/{feature}"
    const val FINANCE = "finance/{destination}?billId={billId}"
    const val FINANCE_TARGET = "finance/{destination}/recipient/{recipientUserId}?recipientName={recipientName}&accountMasked={accountMasked}&groupConversationId={groupConversationId}&recipientConversationId={recipientConversationId}"
    const val SERVICE_SEARCH = "service-search"
    const val COMMON_APPS = "common-apps"
}

private const val ROOT_TAB_REQUEST_KEY = "root-tab-request"
private const val ROOT_MESSAGES_REQUEST = "messages"

@Composable
fun MiniPayApp(
    context: Context,
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    when (val state = authState) {
        AuthUiState.CheckingSession -> SessionLoadingScreen()
        is AuthUiState.PhoneEntry -> LoginScreen(
            state = state,
            onMobileChange = authViewModel::updateMobile,
            onClearMobile = authViewModel::clearMobile,
            onToggleAgreement = authViewModel::toggleAgreement,
            onSendCode = authViewModel::sendCode,
            onOpenUserAgreement = {
                openConfiguredUrl(context, BuildConfig.USER_AGREEMENT_URL, "用户协议尚未配置")
            },
            onOpenPrivacyPolicy = {
                openConfiguredUrl(context, BuildConfig.PRIVACY_POLICY_URL, "隐私政策尚未配置")
            }
        )
        is AuthUiState.CodeEntry -> VerificationCodeScreen(
            state = state,
            onCodeChange = authViewModel::updateCode,
            onResend = authViewModel::resendCode,
            onBack = authViewModel::backToPhone
        )
        is AuthUiState.Session -> key(state.sessionKey) {
            LaunchedEffect(state.sessionKey) {
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                CallRealtimeService.showOnline(context)
            }
            DisposableEffect(state.sessionKey) {
                onDispose {
                    context.stopService(Intent(context, CallRealtimeService::class.java))
                    clearPrivateImageCache(context)
                }
            }
            MainNavigation(
                startWithOnboarding = state.onboardingRequired,
                onOnboardingCompleted = authViewModel::completeOnboarding,
                onLogout = {
                    context.stopService(Intent(context, CallRealtimeService::class.java))
                    clearPrivateImageCache(context)
                    authViewModel.logout()
                },
                onSessionInvalidated = {
                    context.stopService(Intent(context, CallRealtimeService::class.java))
                    clearPrivateImageCache(context)
                    authViewModel.forceLocalLogout()
                }
            )
        }
    }
}

@Composable
private fun SessionLoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun MainNavigation(
    navController: NavHostController = rememberNavController(),
    startWithOnboarding: Boolean,
    onOnboardingCompleted: () -> Unit,
    onLogout: () -> Unit,
    onSessionInvalidated: () -> Unit
) {
    var pendingTransferRecipient by remember { mutableStateOf<TransferRecipientUi?>(null) }
    NavHost(
        navController = navController,
        startDestination = if (startWithOnboarding) AppRoute.ONBOARDING else AppRoute.HOME
    ) {
        composable(AppRoute.ONBOARDING) {
            OnboardingRoute(
                onCompleted = {
                    navController.navigate(AppRoute.HOME) {
                        popUpTo(AppRoute.ONBOARDING) { inclusive = true }
                        launchSingleTop = true
                    }
                    onOnboardingCompleted()
                },
                onLogout = onLogout
            )
        }
        composable(AppRoute.HOME) { entry ->
            val rootTabRequest by entry.savedStateHandle
                .getStateFlow(ROOT_TAB_REQUEST_KEY, "")
                .collectAsStateWithLifecycle()
            RootHomeRoute(
                navController = navController,
                onLogout = onLogout,
                rootTabRequest = rootTabRequest,
                onRootTabRequestConsumed = { entry.savedStateHandle[ROOT_TAB_REQUEST_KEY] = "" }
            )
        }
        composable(AppRoute.CHAT) { entry ->
            val homeEntry = remember(entry) { navController.getBackStackEntry(AppRoute.HOME) }
            val aiViewModel: MilingAiViewModel = hiltViewModel(homeEntry)
            MilingHomeRoute(
                onReturnToRecommendation = { navController.popBackStack() },
                onOpenProfile = { navController.navigateSingle(AppRoute.PROFILE) },
                onOpenFinance = { navController.navigateSingle("finance/${it.name}") },
                onOpenConversation = { navController.returnToRootMessages() },
                onAddFriend = { navController.navigateSingle(AppRoute.ADD_FRIEND) },
                aiViewModel = aiViewModel
            )
        }
        composable(AppRoute.CONVERSATION_LIST) {
            LaunchedEffect(Unit) { navController.returnToRootMessages() }
        }
        composable(AppRoute.SEARCH_FRIEND, arguments = listOf(androidx.navigation.navArgument("query") { type = androidx.navigation.NavType.StringType; defaultValue = "" })) { entry ->
            val context = LocalContext.current
            val friendApi = remember {
                EntryPoints.get(
                    context.applicationContext,
                    SearchFriendEntryPoint::class.java
                ).friendApiService()
            }
            SearchFriendRoute(
                onBack = { navController.popBackStack() },
                friendApi = friendApi,
                initialQuery = Uri.decode(entry.arguments?.getString("query").orEmpty())
            )
        }
        composable(AppRoute.SERVICE_SEARCH) {
            val context = LocalContext.current
            ServiceSearchScreen(
                onBack = { navController.popBackStack() },
                onServiceClick = { service ->
                    when (val destination = service.destination) {
                        is ServiceDestination.Finance -> navController.navigateSingle("finance/${destination.destination.name}")
                        ServiceDestination.AddFriend -> navController.navigateSingle(AppRoute.ADD_FRIEND)
                        ServiceDestination.Food -> navController.navigateSingle(AppRoute.FOOD_ENTRY)
                        is ServiceDestination.Unavailable -> Toast.makeText(context, "${destination.label}功能建设中", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
        composable(AppRoute.COMMON_APPS) {
            val viewModel: CommonAppsViewModel = hiltViewModel()
            val selectedApps by viewModel.apps.collectAsStateWithLifecycle()
            CommonAppsManageScreen(
                selectedApps = selectedApps,
                onBack = { navController.popBackStack() },
                onAdd = viewModel::add,
                onRemove = viewModel::remove,
                onMove = viewModel::move
            )
        }
        composable(AppRoute.CONTACTS) {
            ContactsRoute(
                onBack = { navController.popBackStack() },
                onAddFriend = { navController.navigate(AppRoute.ADD_FRIEND) },
                onSearchClick = { navController.navigate(AppRoute.SEARCH_FRIEND) },
                onGroupsClick = { navController.navigate(AppRoute.GROUP_CONVERSATIONS) },
                onFriendRequestsClick = { navController.navigate(AppRoute.FRIEND_REQUESTS) },
                onRecentTransfersClick = { navController.navigate("finance/${FinanceDestination.RECENT_TRANSFER_CONTACTS.name}") },
                onContactClick = { contact ->
                    navController.navigate("chat/${contact.id}?name=${Uri.encode(contact.name)}")
                }
            )
        }
        composable(AppRoute.GROUP_CONVERSATIONS) {
            GroupConversationListRoute(
                onBack = { navController.popBackStack() },
                onOpenChat = { group -> navController.navigate("chat/${group.id}?name=${Uri.encode(group.name)}") }
            )
        }
        composable(AppRoute.FRIEND_REQUESTS) {
            FriendRequestListRoute(onBack = { navController.popBackStack() })
        }
        composable(AppRoute.ADD_FRIEND) {
            val context = LocalContext.current
            val profileViewModel: ProfileViewModel = hiltViewModel()
            val profileState by profileViewModel.state.collectAsStateWithLifecycle()
            val auth = remember { EntryPoints.get(context.applicationContext, SearchFriendEntryPoint::class.java).authRepository() }
            val profile = (profileState as? ProfileLoadState.Ready)?.profile
            AddFriendScreen(
                accountId = auth.currentMobile() ?: "",
                onBack = { navController.popBackStack() },
                onSearchClick = { navController.navigate("search-friend") },
                onShowQrCode = { navController.navigate(AppRoute.MY_QR_CODE) },
                onScanQr = { navController.navigateSingle("finance/${FinanceDestination.SCAN.name}") }
            )
        }
        composable(AppRoute.MY_QR_CODE) {
            val context = LocalContext.current
            val profileViewModel: ProfileViewModel = hiltViewModel()
            val profileState by profileViewModel.state.collectAsStateWithLifecycle()
            val auth = remember { EntryPoints.get(context.applicationContext, SearchFriendEntryPoint::class.java).authRepository() }
            val profile = (profileState as? ProfileLoadState.Ready)?.profile
            MyQrCodeScreen(
                accountId = profile?.miniPayNo.orEmpty(),
                userName = profile?.nickname
                    ?.takeUnless { it.isBlank() || it == "米灵用户" }
                    ?: "MiniPay 用户",
                avatarUrl = profile?.avatarUrl,
                phone = auth.currentMobile().orEmpty(),
                onBack = { navController.popBackStack() },
                onOpenProfile = { navController.navigate(AppRoute.PROFILE) }
            )
        }
        composable(AppRoute.PUBLIC_FRIEND_CARD) { entry ->
            val context = LocalContext.current
            val friendApi = remember {
                EntryPoints.get(context.applicationContext, SearchFriendEntryPoint::class.java).friendApiService()
            }
            PublicFriendCardRoute(
                minipayNo = Uri.decode(entry.arguments?.getString("minipayNo").orEmpty()),
                onBack = { navController.popBackStack() },
                friendApi = friendApi
            )
        }
        composable(AppRoute.CREATE_GROUP) {
            CreateGroupRoute(
                onBack = { navController.popBackStack() },
                onGroupCreated = { conversationId, name ->
                    navController.navigate("chat/$conversationId?name=${Uri.encode(name)}")
                }
            )
        }
        composable(
            route = AppRoute.CHAT_DETAIL,
            arguments = listOf(
                androidx.navigation.navArgument("conversationId") {
                    type = androidx.navigation.NavType.StringType
                },
                androidx.navigation.navArgument("name") {
                    type = androidx.navigation.NavType.StringType
                    defaultValue = ""
                }
            )
        ) { entry ->
            ChatRoute(
                conversationId = entry.arguments?.getString("conversationId").orEmpty(),
                conversationName = Uri.decode(entry.arguments?.getString("name").orEmpty()),
                onBack = {
                    val id = entry.arguments?.getString("conversationId").orEmpty()
                    if (id.startsWith("group_")) {
                        navController.returnToRootMessages()
                    } else navController.popBackStack()
                },
                onOpenSettings = {
                    val id = entry.arguments?.getString("conversationId").orEmpty()
                    if (id.startsWith("group_")) navController.navigate("group-info/$id")
                    else navController.navigate("chat-settings/$id?name=${Uri.encode(entry.arguments?.getString("name").orEmpty())}")
                },
                onOpenTransferRecords = { target ->
                    pendingTransferRecipient = TransferRecipientUi(
                        receiverUserId = target.userId,
                        nickname = target.name,
                        display = target.name,
                        accountMasked = target.accountMasked,
                        legalNameMasked = null,
                        avatarUrl = target.avatarUrl,
                        transferSource = TransferSource.FORM,
                        origin = TransferRecipientOrigin.CONTACT,
                        conversationId = target.conversationId
                    )
                    navController.navigate(
                        "finance/${FinanceDestination.FRIEND_TRANSFER_RECORDS.name}/recipient/${target.userId}" +
                            "?recipientName=${Uri.encode(target.name)}&accountMasked=${Uri.encode(target.accountMasked.orEmpty())}"
                    )
                },
                onTransfer = { target ->
                    pendingTransferRecipient = TransferRecipientUi(
                        receiverUserId = target.userId,
                        nickname = target.name,
                        display = target.name,
                        accountMasked = target.accountMasked,
                        legalNameMasked = null,
                        avatarUrl = target.avatarUrl,
                        transferSource = TransferSource.FORM,
                        origin = TransferRecipientOrigin.CONTACT,
                        conversationId = target.conversationId
                    )
                    navController.navigate(
                        "finance/${FinanceDestination.TRANSFER.name}/recipient/${target.userId}" +
                            "?recipientName=${Uri.encode(target.name)}&accountMasked=${Uri.encode(target.accountMasked.orEmpty())}" +
                            "&recipientConversationId=${Uri.encode(target.conversationId.orEmpty())}"
                    )
                },
                onGroupTransfer = {
                    val id = entry.arguments?.getString("conversationId").orEmpty()
                    navController.navigate("group-transfer/$id")
                }
            )
        }
        composable(AppRoute.CHAT_SETTINGS) { entry ->
            val id = entry.arguments?.getString("conversationId").orEmpty()
            val viewModel: ChatDetailViewModel = hiltViewModel()
            val currentName by viewModel.conversationName.collectAsStateWithLifecycle()
            val name = currentName.ifBlank { Uri.decode(entry.arguments?.getString("name").orEmpty()) }
            FriendSettingsScreen(
                friendName = name,
                onBack = { navController.popBackStack() },
                onEditRemark = { navController.navigate("chat-remarks/$id?name=${Uri.encode(name)}") },
                onDeleteFriend = { viewModel.deleteFriend { if (it) navController.returnToRootMessages() } }
            )
        }
        composable(AppRoute.CHAT_REMARKS) { entry ->
            val viewModel: ChatDetailViewModel = hiltViewModel()
            val currentName by viewModel.conversationName.collectAsStateWithLifecycle()
            val name = currentName.ifBlank { Uri.decode(entry.arguments?.getString("name").orEmpty()) }
            RemarkSettingsScreen(name, onBack = { navController.popBackStack() }, onSave = viewModel::saveRemark)
        }
        composable(AppRoute.GROUP_INFO) {
            GroupInfoRoute(
                onBack = { navController.popBackStack() },
                onExitToMessages = { navController.returnToRootMessages() },
                onPickMembers = { removing -> navController.navigate("group-pick/${it.arguments?.getString("conversationId").orEmpty()}?removing=$removing") }
            )
        }
        composable(AppRoute.GROUP_PICK) {
            GroupMemberPickerRoute(
                removing = it.arguments?.getString("removing")?.toBoolean() == true,
                onBack = { navController.popBackStack() }
            )
        }
        composable(AppRoute.FOOD_ENTRY) {
            FoodEntryScreen(
                onReady = {
                    navController.navigate(AppRoute.FOOD_WEB) {
                        popUpTo(AppRoute.FOOD_ENTRY) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onConsentRequired = {
                    navController.navigate(AppRoute.FOOD_CONSENT) {
                        popUpTo(AppRoute.FOOD_ENTRY) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(AppRoute.FOOD_CONSENT) {
            FoodAuthorizationScreen(
                onAuthorized = {
                    navController.navigate(AppRoute.FOOD_WEB) {
                        popUpTo(AppRoute.FOOD_CONSENT) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onBack = { navController.popBackStack() },
                onOpenAgreement = { page -> navController.navigate("food-agreement/${page}") }
            )
        }
        composable(AppRoute.FOOD_AGREEMENT) { entry ->
            FoodAgreementScreen(
                page = entry.arguments?.getString("page").orEmpty(),
                origin = BuildConfig.FOOD_H5_ORIGIN,
                onBack = { navController.popBackStack() }
            )
        }
        composable(AppRoute.FOOD_WEB) { entry ->
            val resultOrderNo by entry.savedStateHandle
                .getStateFlow("foodPaymentOrderNo", "").collectAsStateWithLifecycle()
            val resultStatus by entry.savedStateHandle
                .getStateFlow("foodPaymentStatus", "").collectAsStateWithLifecycle()
            val resultExternalOrderNo by entry.savedStateHandle
                .getStateFlow("foodExternalOrderNo", "").collectAsStateWithLifecycle()
            val resultRequestId by entry.savedStateHandle
                .getStateFlow("foodBridgePaymentRequestId", "").collectAsStateWithLifecycle()
            FoodWebViewScreen(
                foodH5Origin = BuildConfig.FOOD_H5_ORIGIN,
                paymentResult = if (resultOrderNo.isBlank() || resultRequestId.isBlank()) null
                    else FoodPaymentBridgeResult(
                        resultRequestId,
                        resultExternalOrderNo,
                        resultOrderNo,
                        resultStatus
                    ),
                onPaymentResultDelivered = {
                    entry.savedStateHandle["foodPaymentOrderNo"] = ""
                    entry.savedStateHandle["foodPaymentStatus"] = ""
                    entry.savedStateHandle["foodExternalOrderNo"] = ""
                    entry.savedStateHandle["foodBridgePaymentRequestId"] = ""
                },
                onRequestPayment = { externalOrderNo, orderRefId, requestId ->
                    entry.savedStateHandle["foodBridgePaymentRequestId"] = requestId
                    entry.savedStateHandle["foodExternalOrderNo"] = externalOrderNo
                    navController.navigate(
                        "food-payment/${Uri.encode(externalOrderNo)}" +
                            "?orderRefId=${Uri.encode(orderRefId.orEmpty())}"
                    )
                },
                onClose = {
                    if (!navController.popBackStack(AppRoute.HOME, false)) {
                        navController.navigate(AppRoute.HOME) { launchSingleTop = true }
                    }
                }
            )
        }
        composable(
            route = AppRoute.FOOD_PAYMENT,
            arguments = listOf(
                navArgument("orderRefId") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { entry ->
            val externalOrderNo = Uri.decode(entry.arguments?.getString("externalOrderNo").orEmpty())
            NativeFoodPaymentScreen(
                externalOrderNo = externalOrderNo,
                onBack = { navController.popBackStack() },
                onFinished = { paymentOrderNo, status ->
                    navController.previousBackStackEntry?.savedStateHandle?.apply {
                        set("foodPaymentOrderNo", paymentOrderNo)
                        set("foodPaymentStatus", status)
                        set("foodExternalOrderNo", externalOrderNo)
                    }
                    navController.popBackStack()
                }
            )
        }
        composable(AppRoute.FOOD_ORDERS) {
            FoodOrderCenterScreen(
                onBack = { navController.popBackStack() },
                onOpenOrder = { orderRefId ->
                    navController.navigate("food-orders/${Uri.encode(orderRefId)}")
                },
                onPay = { externalOrderNo ->
                    navController.navigate("food-payment/${Uri.encode(externalOrderNo)}")
                }
            )
        }
        composable(AppRoute.FOOD_ORDER_DETAIL) {
            FoodOrderDetailScreen(
                onBack = { navController.popBackStack() },
                onPay = { externalOrderNo ->
                    navController.navigate("food-payment/${Uri.encode(externalOrderNo)}")
                }
            )
        }
        composable(AppRoute.PAYMENT) {
            FinanceRoute(
                FinanceDestination.WALLET,
                onBack = { navController.popBackStack() },
                onOpenFriendCard = { navController.navigate("public-friend-card/${Uri.encode(it)}") }
            )
        }
        composable(
            route = AppRoute.FINANCE,
            arguments = listOf(
                androidx.navigation.navArgument("billId") {
                    type = androidx.navigation.NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { entry ->
            val destination = runCatching {
                FinanceDestination.valueOf(entry.arguments?.getString("destination").orEmpty())
            }.getOrDefault(FinanceDestination.WALLET)
            FinanceRoute(
                initial = destination,
                initialBillId = entry.arguments?.getString("billId")?.let(Uri::decode),
                onBack = { navController.popBackStack() },
                onOpenFriendCard = { navController.navigate("public-friend-card/${Uri.encode(it)}") }
            )
        }
        composable(AppRoute.GROUP_TRANSFER_PICK) { entry ->
            val conversationId = entry.arguments?.getString("conversationId").orEmpty()
            GroupTransferMemberPickerRoute(
                onBack = { navController.popBackStack() },
                onSelect = { member ->
                    val name = member.displayName ?: member.nickname ?: member.originalNickname ?: "群成员"
                    pendingTransferRecipient = TransferRecipientUi(
                        receiverUserId = member.userId,
                        nickname = name,
                        display = name,
                        accountMasked = null,
                        legalNameMasked = null,
                        avatarUrl = member.avatarUrl,
                        transferSource = TransferSource.FORM,
                        origin = TransferRecipientOrigin.GROUP_MEMBER,
                        conversationId = conversationId
                    )
                    navController.navigate(
                        "finance/${FinanceDestination.TRANSFER.name}/recipient/${member.userId}" +
                            "?recipientName=${Uri.encode(name)}&accountMasked=&groupConversationId=${Uri.encode(conversationId)}"
                    )
                }
            )
        }
        composable(
            route = AppRoute.FINANCE_TARGET,
            arguments = listOf(
                androidx.navigation.navArgument("recipientName") {
                    type = androidx.navigation.NavType.StringType
                    defaultValue = ""
                },
                androidx.navigation.navArgument("accountMasked") {
                    type = androidx.navigation.NavType.StringType
                    defaultValue = ""
                },
                androidx.navigation.navArgument("groupConversationId") {
                    type = androidx.navigation.NavType.StringType
                    defaultValue = ""
                },
                androidx.navigation.navArgument("recipientConversationId") {
                    type = androidx.navigation.NavType.StringType
                    defaultValue = ""
                }
            )
        ) { entry ->
            val destination = runCatching {
                FinanceDestination.valueOf(entry.arguments?.getString("destination").orEmpty())
            }.getOrDefault(FinanceDestination.TRANSFER)
            val userId = entry.arguments?.getString("recipientUserId").orEmpty()
            val name = Uri.decode(entry.arguments?.getString("recipientName").orEmpty()).ifBlank { "好友" }
            val account = Uri.decode(entry.arguments?.getString("accountMasked").orEmpty()).ifBlank { null }
            val groupConversationId = Uri.decode(entry.arguments?.getString("groupConversationId").orEmpty()).ifBlank { null }
            val recipientConversationId = Uri.decode(entry.arguments?.getString("recipientConversationId").orEmpty()).ifBlank { null }
            FinanceRoute(
                initial = destination,
                initialRecipient = pendingTransferRecipient?.takeIf { it.receiverUserId == userId }
                    ?: TransferRecipientUi(
                    receiverUserId = userId,
                    nickname = name,
                    display = name,
                    accountMasked = account,
                    legalNameMasked = null,
                    avatarUrl = null,
                    transferSource = TransferSource.FORM,
                    origin = if (groupConversationId == null) TransferRecipientOrigin.CONTACT else TransferRecipientOrigin.GROUP_MEMBER,
                    conversationId = groupConversationId ?: recipientConversationId
                ),
                groupConversationId = groupConversationId,
                onBack = { navController.popBackStack() },
                onOpenFriendCard = { navController.navigate("public-friend-card/${Uri.encode(it)}") }
            )
        }
        composable(AppRoute.PROFILE) {
            ProfileRoute(
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigateSingle(AppRoute.EDIT_PROFILE) },
                onOpenAccountSecurity = { navController.navigateSingle(AppRoute.ACCOUNT_SECURITY) },
                onOpenFeature = {
                    if (it == "应用授权管理") navController.navigateSingle(AppRoute.APPLICATION_AUTHORIZATIONS)
                    else if (it == "钱包") navController.navigateSingle("finance/${FinanceDestination.WALLET.name}")
                    else if (it == "订单") navController.navigateSingle(AppRoute.FOOD_ORDERS)
                    else if (it == "记忆") navController.navigateSingle(AppRoute.MEMORY_SETTINGS)
                    else navController.navigateSingle("profile/feature/${Uri.encode(it)}")
                },
                onLogout = onLogout
            )
        }
        composable(AppRoute.EDIT_PROFILE) {
            EditProfileRoute(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }
        composable(AppRoute.ACCOUNT_SECURITY) {
            AccountSecurityRoute(
                onBack = {
                    if (!navController.popBackStack()) navController.navigate(AppRoute.HOME)
                },
                onSessionInvalidated = onSessionInvalidated
            )
        }
        composable(AppRoute.APPLICATION_AUTHORIZATIONS) {
            ApplicationAuthorizationScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(AppRoute.MEMORY_SETTINGS) {
            MemorySettingsRoute(onBack = { navController.popBackStack() })
        }
        composable(AppRoute.PLACEHOLDER) { entry ->
            FeaturePlaceholderScreen(
                feature = Uri.decode(entry.arguments?.getString("feature").orEmpty()),
                onBack = { navController.popBackStack() }
            )
        }
    }
}

internal enum class RootMessageContent { CONVERSATIONS, PAYMENTS }

internal fun rootMessageContentAfterTabSelection(
    tab: RootTab,
    current: RootMessageContent
): RootMessageContent = if (tab == RootTab.MESSAGES) {
    RootMessageContent.CONVERSATIONS
} else {
    current
}

@Composable
private fun RootHomeRoute(
    navController: NavHostController,
    onLogout: () -> Unit,
    rootTabRequest: String,
    onRootTabRequestConsumed: () -> Unit,
    conversationListViewModel: ConversationListViewModel = hiltViewModel(),
    paymentMessagesViewModel: PaymentMessagesViewModel = hiltViewModel(),
    aiViewModel: MilingAiViewModel = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel()
) {
    val milingPage = 0
    val homePage = 1
    val pagerState = rememberPagerState(initialPage = homePage, pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableStateOf(RootTab.RECOMMENDATION) }
    var messageContent by rememberSaveable { mutableStateOf(RootMessageContent.CONVERSATIONS) }
    val conversations by conversationListViewModel.conversations.collectAsStateWithLifecycle()
    val pendingFriendRequestCount by conversationListViewModel.pendingFriendRequestCount.collectAsStateWithLifecycle()
    val paymentMessages by paymentMessagesViewModel.state.collectAsStateWithLifecycle()
    val rootProfileState by profileViewModel.state.collectAsStateWithLifecycle()
    val rootProfile = (rootProfileState as? ProfileLoadState.Ready)?.profile
    AvatarPreloadEffect(
        listOf(rootProfile?.usableAvatarUrl()) + conversations.take(12).map { it.avatarUrl }
    )
    RootTabRequestEffect(
        rootTabRequest = rootTabRequest,
        onOpenMessages = {
            coroutineScope.launch { pagerState.scrollToPage(homePage) }
            messageContent = RootMessageContent.CONVERSATIONS
            selectedTab = RootTab.MESSAGES
        },
        onConsumed = onRootTabRequestConsumed
    )
    BackHandler(enabled = pagerState.currentPage == milingPage || selectedTab != RootTab.RECOMMENDATION) {
        if (pagerState.currentPage == milingPage) {
            coroutineScope.launch { pagerState.animateScrollToPage(homePage) }
        } else {
            selectedTab = RootTab.RECOMMENDATION
        }
    }
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = selectedTab == RootTab.RECOMMENDATION,
        key = { it }
    ) { page ->
        if (page == homePage) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().padding(bottom = MilingHomeTokens.BottomContentPadding)) {
                    when (selectedTab) {
                        RootTab.RECOMMENDATION -> RecommendationHomeRoute(
                            onOpenMiling = { coroutineScope.launch { pagerState.animateScrollToPage(milingPage) } },
                            onOpenMessages = {
                                messageContent = RootMessageContent.CONVERSATIONS
                                selectedTab = RootTab.MESSAGES
                            },
                            onOpenPaymentMessages = {
                                messageContent = RootMessageContent.PAYMENTS
                                selectedTab = RootTab.MESSAGES
                            },
                            onOpenProfile = { selectedTab = RootTab.PROFILE },
              onOpenFinance = { navController.navigateSingle("finance/${it.name}") },
              onOpenServiceSearch = { navController.navigateSingle(AppRoute.SERVICE_SEARCH) },
              onOpenCommonApps = { navController.navigateSingle(AppRoute.COMMON_APPS) },
              onOpenFood = { navController.navigateSingle(AppRoute.FOOD_ENTRY) },
              onAddFriend = { navController.navigateSingle(AppRoute.ADD_FRIEND) },
                            showBottomNavigation = false,
                            conversationListViewModel = conversationListViewModel
                        )
                        RootTab.MESSAGES -> when (messageContent) {
                            RootMessageContent.CONVERSATIONS -> ConversationListRoute(
                                navController = navController,
                                onBack = { selectedTab = RootTab.RECOMMENDATION },
                                onOpenChat = { conversationId, name ->
                                    navController.navigate("chat/$conversationId?name=${Uri.encode(name)}")
                                },
                                onOpenContacts = { navController.navigate(AppRoute.CONTACTS) },
                                onSearchClick = { navController.navigate("search-friend") }
                            )
                            RootMessageContent.PAYMENTS -> RootPaymentMessageRoute(
                                navController = navController,
                                onOpenConversations = { messageContent = RootMessageContent.CONVERSATIONS },
                                viewModel = paymentMessagesViewModel
                            )
                        }
                        RootTab.PROFILE -> ProfileRoute(
                            onBack = { selectedTab = RootTab.RECOMMENDATION },
                            onEdit = { navController.navigateSingle(AppRoute.EDIT_PROFILE) },
                            onOpenAccountSecurity = { navController.navigateSingle(AppRoute.ACCOUNT_SECURITY) },
                            onOpenFeature = {
                                if (it == "应用授权管理") navController.navigateSingle(AppRoute.APPLICATION_AUTHORIZATIONS)
                                else if (it == "钱包") navController.navigateSingle("finance/${FinanceDestination.WALLET.name}")
                                else if (it == "订单") navController.navigateSingle(AppRoute.FOOD_ORDERS)
                                else if (it == "记忆") navController.navigateSingle(AppRoute.MEMORY_SETTINGS)
                                else navController.navigateSingle("profile/feature/${Uri.encode(it)}")
                            },
                            onLogout = onLogout,
                            embeddedInRoot = true
                        )
                    }
                }
                RootBottomNavigation(
                    selected = selectedTab,
                    onSelect = { tab ->
                        messageContent = rootMessageContentAfterTabSelection(tab, messageContent)
                        selectedTab = tab
                    },
                    onOpenMiling = { coroutineScope.launch { pagerState.animateScrollToPage(milingPage) } },
                    showMessageReminder = pendingFriendRequestCount > 0 ||
                        conversations.any { it.unreadCount > 0 } || paymentMessages.hasUnread,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        } else {
            MilingHomeRoute(
                onReturnToRecommendation = { coroutineScope.launch { pagerState.animateScrollToPage(homePage) } },
                onOpenProfile = {
                    selectedTab = RootTab.PROFILE
                    coroutineScope.launch { pagerState.scrollToPage(homePage) }
                },
                onOpenFinance = { navController.navigateSingle("finance/${it.name}") },
                onOpenConversation = {
                    messageContent = RootMessageContent.CONVERSATIONS
                    selectedTab = RootTab.MESSAGES
                    coroutineScope.launch { pagerState.scrollToPage(homePage) }
                },
                onAddFriend = { navController.navigateSingle(AppRoute.ADD_FRIEND) },
                onOpenFood = { navController.navigateSingle(AppRoute.FOOD_ENTRY) },
                isVisible = pagerState.currentPage == milingPage,
                profileViewModel = profileViewModel,
                aiViewModel = aiViewModel
            )
        }
    }
}

@Composable
private fun RootPaymentMessageRoute(
    navController: NavHostController,
    onOpenConversations: () -> Unit,
    viewModel: PaymentMessagesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.loading, state.bills.firstOrNull()?.billId) {
        if (!state.loading) viewModel.markVisibleMessagesRead()
    }
    AutoRefreshEffect(
        enabled = !state.loading && !state.loadingMore,
        onRefresh = viewModel::refresh
    )
    MessageCenterScreen(
        state = state,
        onOpenConversations = onOpenConversations,
        onContacts = { navController.navigate(AppRoute.CONTACTS) },
        onCreateGroup = { navController.navigate(AppRoute.CREATE_GROUP) },
        onAddFriend = { navController.navigate(AppRoute.ADD_FRIEND) },
        onScan = { navController.navigate("finance/${FinanceDestination.SCAN.name}") },
        onOpenBill = { billId ->
            navController.navigate("finance/${FinanceDestination.BILL_DETAIL.name}?billId=${Uri.encode(billId)}")
        },
        onRetry = viewModel::refresh,
        onLoadMore = viewModel::loadMore
    )
}

@Composable
private fun RecommendationHomeRoute(
    onOpenMiling: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenPaymentMessages: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenFinance: (FinanceDestination) -> Unit,
    onOpenServiceSearch: () -> Unit,
    onOpenCommonApps: () -> Unit,
    onOpenFood: () -> Unit,
    onAddFriend: () -> Unit,
    showBottomNavigation: Boolean,
    homeViewModel: HomeViewModel = hiltViewModel(),
    conversationListViewModel: ConversationListViewModel = hiltViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val state by homeViewModel.state.collectAsStateWithLifecycle()
    val conversations by conversationListViewModel.conversations.collectAsStateWithLifecycle()
    val pendingFriendRequestCount by conversationListViewModel.pendingFriendRequestCount.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val locationPermissions = arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        homeViewModel.refreshLocation(result.values.any { it })
    }
    fun refreshLocation() {
        val granted = locationPermissions.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (granted) homeViewModel.refreshLocation(true) else permissionLauncher.launch(locationPermissions)
    }
    LaunchedEffect(Unit) { refreshLocation() }
    AutoRefreshEffect(enabled = !state.billsLoading, onRefresh = homeViewModel::refreshRecentBills)
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                conversationListViewModel.refresh()
                delay(10_000)
            }
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        RecommendationHomeScreen(
            showMessageReminder = pendingFriendRequestCount > 0 || conversations.any { it.unreadCount > 0 },
            city = state.locationWeather?.displayCity ?: if (state.locating) "定位中" else "定位失败",
            weather = state.locationWeather?.displayWeather.orEmpty(),
            locationError = state.locationError.takeIf { state.locationWeather == null },
            recentBills = state.recentBills,
            billsLoading = state.billsLoading,
            billsError = state.billsError,
            commonApps = state.commonApps,
            showBottomNavigation = showBottomNavigation,
            onAction = { action ->
                when (action) {
                    RecommendationHomeAction.OpenMiling -> onOpenMiling()
                    RecommendationHomeAction.OpenMessages -> onOpenMessages()
                    RecommendationHomeAction.OpenPaymentMessages -> onOpenPaymentMessages()
                    RecommendationHomeAction.OpenProfile -> onOpenProfile()
                    RecommendationHomeAction.OpenServiceSearch -> onOpenServiceSearch()
                    RecommendationHomeAction.OpenCommonApps -> onOpenCommonApps()
                    RecommendationHomeAction.OpenFood -> onOpenFood()
                    RecommendationHomeAction.RetryLocation -> refreshLocation()
                    RecommendationHomeAction.RetryRecentBills -> homeViewModel.refreshRecentBills()
                    RecommendationHomeAction.AddFriend -> onAddFriend()
                    is RecommendationHomeAction.OpenFinance -> onOpenFinance(action.destination)
                    is RecommendationHomeAction.ShowUnavailable -> coroutineScope.launch {
                        snackbarHostState.showSnackbar("${action.label}功能建设中")
                    }
                }
            }
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 102.dp)
        )
    }
}

@Composable
private fun MilingHomeRoute(
    onReturnToRecommendation: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenFinance: (FinanceDestination) -> Unit,
    onOpenConversation: () -> Unit = {},
    onAddFriend: () -> Unit = {},
    onOpenFood: () -> Unit = {},
    isVisible: Boolean = true,
    profileViewModel: ProfileViewModel = hiltViewModel(),
    aiViewModel: MilingAiViewModel = hiltViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val aiState by aiViewModel.state.collectAsStateWithLifecycle()
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) aiViewModel.setInputMode(AiInputMode.VOICE) else aiViewModel.voicePermissionDenied()
    }
    DisposableEffect(aiViewModel, isVisible) {
        aiViewModel.setVoiceScreenVisible(isVisible)
        onDispose { aiViewModel.setVoiceScreenVisible(false) }
    }
    AutoRefreshEffect(
        enabled = isVisible && !aiState.loading && !aiState.streaming && !aiState.confirmationInFlight &&
            aiState.paymentResult == null,
        onRefresh = aiViewModel::refresh
    )

    LaunchedEffect(aiState.errorMessage) {
        val error = aiState.errorMessage ?: return@LaunchedEffect
        snackbarHostState.currentSnackbarData?.dismiss()
        val result = snackbarHostState.showSnackbar(
            message = error,
            actionLabel = if (aiState.canRetry) "重试" else null,
            withDismissAction = true
        )
        if (result == SnackbarResult.ActionPerformed) aiViewModel.retry()
        else aiViewModel.dismissError()
    }

    LaunchedEffect(aiState.voiceMessage) {
        val message = aiState.voiceMessage ?: return@LaunchedEffect
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(message, withDismissAction = true)
        aiViewModel.dismissVoiceMessage()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val profileState by profileViewModel.state.collectAsStateWithLifecycle()
        val profile = (profileState as? ProfileLoadState.Ready)?.profile
        MilingHomeScreen(
            aiState = aiState,
            soundEnabled = aiState.aiSpeechEnabled,
            drawerProfileName = profile?.nickname ?: "小满",
            drawerAvatarUrl = profile?.avatarUrl,
            onAction = { action ->
                when (action) {
                    MilingHomeAction.ReturnToRecommendation -> onReturnToRecommendation()
                    MilingHomeAction.StartNewConversation -> aiViewModel.startNewConversation()
                    is MilingHomeAction.SelectSession -> aiViewModel.selectConversation(action.sessionId)
                    is MilingHomeAction.RenameConversation ->
                        aiViewModel.renameConversation(action.conversation, action.title)
                    is MilingHomeAction.DeleteConversation -> aiViewModel.deleteConversation(action.conversation)
                    MilingHomeAction.OpenProfile -> onOpenProfile()
                    MilingHomeAction.OpenConversation -> onOpenConversation()
                    MilingHomeAction.AddFriend -> onAddFriend()
                    MilingHomeAction.Scan -> onOpenFinance(FinanceDestination.SCAN)
                    MilingHomeAction.ReceiveMoney -> onOpenFinance(FinanceDestination.RECEIVE)
                    MilingHomeAction.Transfer -> aiViewModel.submit("我要转账")
                    MilingHomeAction.CheckBalance -> aiViewModel.submit("查询钱包余额")
                    MilingHomeAction.ViewBills -> aiViewModel.submit("查看本月账单")
                    is MilingHomeAction.OpenFinance -> onOpenFinance(action.destination)
                    MilingHomeAction.OpenLifeAssistant -> aiViewModel.submit("帮我点外卖")
                    MilingHomeAction.OpenFood -> onOpenFood()
                    is MilingHomeAction.UpdateDraft -> aiViewModel.updateDraft(action.value)
                    is MilingHomeAction.SubmitPrompt -> aiViewModel.submit(action.prompt)
                    is MilingHomeAction.ContinueCard -> aiViewModel.continueAction(action.message, action.request)
                    is MilingHomeAction.PrepareCheckout -> aiViewModel.prepareCheckout(action.message)
                    is MilingHomeAction.RequestPayment -> aiViewModel.requestPayment(action.message)
                    is MilingHomeAction.CancelOrder -> aiViewModel.cancelOrder(action.message)
                    is MilingHomeAction.SetInputMode -> if (action.mode == AiInputMode.KEYBOARD) {
                        aiViewModel.setInputMode(AiInputMode.KEYBOARD)
                    } else if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        aiViewModel.setInputMode(AiInputMode.VOICE)
                    } else {
                        microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                    MilingHomeAction.VoiceHoldStarted -> aiViewModel.startVoiceInput()
                    MilingHomeAction.VoiceHoldReleased -> aiViewModel.finishVoiceInput()
                    MilingHomeAction.VoiceHoldCancelled -> aiViewModel.cancelVoiceInput()
                    is MilingHomeAction.SetSoundEnabled -> {
                        aiViewModel.setAiSpeechEnabled(action.enabled)
                        coroutineScope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar(action.feedbackMessage())
                        }
                    }
                }
            }
        )

        if (aiState.paymentResult == null) aiState.paymentPrompt?.let { prompt ->
            AiPaymentConfirmationDialog(
                prompt = prompt,
                submitting = aiState.confirmationInFlight,
                onDismiss = aiViewModel::dismissPayment,
                onConfirm = aiViewModel::confirmPayment
            )
        }

        if (aiState.paymentResult == null && aiState.pendingCheckout != null) {
            AiAddressDialog(
                submitting = aiState.confirmationInFlight,
                onDismiss = aiViewModel::dismissAddressForm,
                onSave = aiViewModel::saveAddress
            )
        }

        aiState.paymentResult?.let { result ->
            val isTransfer = result.snapshot.reference.operation == PaymentOperation.TRANSFER
            UnifiedPaymentResultScreen(
                state = result,
                primaryLabel = "完成",
                secondaryLabel = if (isTransfer) "查看账单" else null,
                onDone = aiViewModel::completePaymentResult,
                onRefresh = aiViewModel::refreshPaymentResult,
                onSecondary = {
                    if (isTransfer) {
                        aiViewModel.completePaymentResult()
                        onOpenFinance(FinanceDestination.BILLS)
                    }
                }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 100.dp)
        )
    }
}

private fun MilingHomeAction.feedbackMessage(): String =
    when (this) {
        MilingHomeAction.ReturnToRecommendation -> "返回推荐主页"
        MilingHomeAction.StartNewConversation -> "新对话功能暂未接入"
        is MilingHomeAction.SelectSession -> "会话切换功能暂未接入"
        is MilingHomeAction.RenameConversation -> "正在重命名"
        is MilingHomeAction.DeleteConversation -> "正在删除"
        MilingHomeAction.OpenProfile -> "个人中心暂未接入"
        MilingHomeAction.OpenConversation -> "对话功能暂未接入"
        is MilingHomeAction.SetSoundEnabled -> if (enabled) "声音已开启" else "声音已关闭"
        MilingHomeAction.Scan -> "扫一扫功能暂未接入"
        MilingHomeAction.ReceiveMoney -> "收款功能暂未接入"
        MilingHomeAction.AddFriend -> "添加朋友功能暂未接入"
        MilingHomeAction.Transfer -> "转账功能暂未接入"
        MilingHomeAction.CheckBalance -> "余额查询暂未接入"
        MilingHomeAction.ViewBills -> "账单功能暂未接入"
        is MilingHomeAction.OpenFinance -> "正在打开金融服务"
        MilingHomeAction.OpenLifeAssistant -> "生活助手暂未接入"
        MilingHomeAction.OpenFood -> "进入意向外卖"
        is MilingHomeAction.SetInputMode -> if (mode == AiInputMode.VOICE) "切换到语音输入" else "切换到文字输入"
        MilingHomeAction.VoiceHoldStarted -> "开始语音输入"
        MilingHomeAction.VoiceHoldReleased -> "结束语音输入"
        MilingHomeAction.VoiceHoldCancelled -> "取消语音输入"
        is MilingHomeAction.UpdateDraft -> "草稿已更新"
        is MilingHomeAction.SubmitPrompt -> "正在发送"
        is MilingHomeAction.ContinueCard,
        is MilingHomeAction.PrepareCheckout,
        is MilingHomeAction.RequestPayment,
        is MilingHomeAction.CancelOrder -> "正在处理"
    }

private data class FoodPaymentBridgeResult(
    val requestId: String,
    val externalOrderNo: String,
    val paymentOrderNo: String,
    val status: String
)

@Composable
private fun FoodWebViewScreen(
    foodH5Origin: String,
    paymentResult: FoodPaymentBridgeResult?,
    onPaymentResultDelivered: () -> Unit,
    onRequestPayment: (String, String?, String) -> Unit,
    onClose: () -> Unit,
    viewModel: FoodIntegrationViewModel = hiltViewModel()
) {
    if (foodH5Origin.isBlank()) {
        EmptyFoodWebViewState()
        return
    }

    val context = LocalContext.current
    val pageUrl = remember(foodH5Origin) {
        Uri.parse(foodH5Origin).buildUpon()
            .appendQueryParameter("minipayNativeShell", "1")
            .appendQueryParameter("minipayVersion", BuildConfig.VERSION_CODE.toString())
            .build().toString()
    }
    var hostedWebView by remember { mutableStateOf<WebView?>(null) }
    var h5CanGoBack by remember { mutableStateOf(false) }
    var webViewLoading by remember { mutableStateOf(true) }
    var webViewError by remember { mutableStateOf<String?>(null) }
    var reloadGeneration by remember { mutableStateOf(0) }
    fun sendAuthorization(webView: WebView, requestId: String) {
        viewModel.requestHandoff { handoff ->
            val payload = JSONObject()
                .put("authorizationCode", handoff.code)
                .put("deviceProof", handoff.deviceProof)
                .put("expiresAt", handoff.expiresAt)
            val envelope = JSONObject()
                .put("version", 3)
                .put("type", "AUTHORIZATION_CODE")
                .put("requestId", requestId)
                .put("payload", payload)
            WebViewCompat.postWebMessage(
                webView,
                WebMessageCompat(envelope.toString()),
                Uri.parse(foodH5Origin)
            )
        }
    }
    fun sendLocationContext(webView: WebView, requestId: String) {
        val permissionGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!permissionGranted) {
            val envelope = JSONObject().put("version", 3)
                .put("type", "LOCATION_CONTEXT").put("requestId", requestId)
                .put("payload", JSONObject().put("status", "PERMISSION_DENIED"))
            WebViewCompat.postWebMessage(
                webView,
                WebMessageCompat(envelope.toString()),
                Uri.parse(foodH5Origin)
            )
            return
        }
        viewModel.requestLocationContext(
            onReady = { location ->
                val envelope = JSONObject().put("version", 3)
                    .put("type", "LOCATION_CONTEXT").put("requestId", requestId)
                    .put(
                        "payload",
                        JSONObject()
                            .put("status", "READY")
                            .put("locationContextId", location.locationContextId)
                            .put("expiresAt", location.expiresAt)
                    )
                WebViewCompat.postWebMessage(
                    webView,
                    WebMessageCompat(envelope.toString()),
                    Uri.parse(foodH5Origin)
                )
            },
            onUnavailable = { status ->
                val envelope = JSONObject().put("version", 3)
                    .put("type", "LOCATION_CONTEXT").put("requestId", requestId)
                    .put("payload", JSONObject().put("status", status.name))
                WebViewCompat.postWebMessage(
                    webView,
                    WebMessageCompat(envelope.toString()),
                    Uri.parse(foodH5Origin)
                )
            }
        )
    }
    BackHandler {
        val webView = hostedWebView
        when {
            webView == null -> onClose()
            h5CanGoBack -> {
                val envelope = JSONObject().put("version", 3)
                    .put("type", "NAVIGATE_BACK")
                    .put("requestId", UUID.randomUUID().toString())
                    .put("payload", JSONObject())
                WebViewCompat.postWebMessage(
                    webView,
                    WebMessageCompat(envelope.toString()),
                    Uri.parse(foodH5Origin)
                )
            }
            webView.canGoBack() -> webView.goBack()
            else -> onClose()
        }
    }
    LaunchedEffect(hostedWebView, paymentResult) {
        val webView = hostedWebView ?: return@LaunchedEffect
        val result = paymentResult ?: return@LaunchedEffect
        val envelope = JSONObject()
            .put("version", 3)
            .put("type", "PAYMENT_RESULT")
            .put("requestId", result.requestId)
            .put(
                "payload",
                JSONObject()
                    .put("externalOrderNo", result.externalOrderNo)
                    .put("paymentOrderNo", result.paymentOrderNo)
                    .put("status", result.status)
            )
        WebViewCompat.postWebMessage(
            webView,
            WebMessageCompat(envelope.toString()),
            Uri.parse(foodH5Origin)
        )
        onPaymentResultDelivered()
    }
    DisposableEffect(Unit) {
        onDispose {
            hostedWebView?.stopLoading()
            hostedWebView?.destroy()
            hostedWebView = null
        }
    }
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Text("‹", style = MaterialTheme.typography.headlineMedium)
            }
            Text("意向点餐", style = MaterialTheme.typography.titleLarge)
        }
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            key(reloadGeneration) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                hostedWebView = this
                WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.safeBrowsingEnabled = true
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.setSupportMultipleWindows(false)
                if (BuildConfig.DEBUG) {
                    settings.cacheMode = WebSettings.LOAD_NO_CACHE
                    clearCache(true)
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                        !FoodBridgePolicy.isTrustedFoodOrigin(
                            request.url.toString(),
                            foodH5Origin,
                            BuildConfig.DEBUG
                        )

                    override fun onPageFinished(view: WebView, url: String) {
                        Log.i("MiniPayFoodWeb", "Loaded H5 page url=$url appVersion=${BuildConfig.VERSION_NAME}")
                        webViewLoading = false
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError
                    ) {
                        if (!request.isForMainFrame) return
                        webViewLoading = false
                        webViewError = "外卖页面加载失败，请检查连接后重试"
                        Log.e("MiniPayFoodWeb", "Main frame load failed code=${error.errorCode}")
                    }

                    override fun onReceivedHttpError(
                        view: WebView,
                        request: WebResourceRequest,
                        errorResponse: WebResourceResponse
                    ) {
                        val path = request.url.path.orEmpty()
                        if (errorResponse.statusCode < 400 ||
                            (!path.endsWith(".js") && !path.endsWith(".css"))) return
                        webViewLoading = false
                        webViewError = "外卖页面资源不完整，请重新加载"
                        Log.e(
                            "MiniPayFoodWeb",
                            "H5 asset failed status=${errorResponse.statusCode} url=${request.url}"
                        )
                    }

                    override fun onRenderProcessGone(
                        view: WebView,
                        detail: RenderProcessGoneDetail
                    ): Boolean {
                        webViewLoading = false
                        webViewError = "外卖页面运行异常，请重新加载"
                        hostedWebView = null
                        Log.e("MiniPayFoodWeb", "Renderer gone didCrash=${detail.didCrash()}")
                        view.destroy()
                        return true
                    }
                }
                // No addJavascriptInterface: only a versioned, origin-restricted Web Message bridge is allowed.
                if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                    webViewLoading = false
                    webViewError = "当前系统 WebView 不支持安全外卖页面"
                    return@apply
                }
                WebViewCompat.addWebMessageListener(
                    this,
                    "MiniPayFoodBridge",
                    setOf(foodH5Origin)
                ) { view, message, sourceOrigin, isMainFrame, _ ->
                    if (!isMainFrame || !FoodBridgePolicy.isTrustedFoodOrigin(
                            sourceOrigin.toString(),
                            foodH5Origin,
                            BuildConfig.DEBUG
                        )
                    ) {
                        return@addWebMessageListener
                    }
                    val raw = message.data ?: return@addWebMessageListener
                    if (!FoodBridgePolicy.allowsSensitiveField(raw)) return@addWebMessageListener
                    val envelope = runCatching { JSONObject(raw) }.getOrNull()
                        ?: return@addWebMessageListener
                    if (envelope.optInt("version") != 3) return@addWebMessageListener
                    val type = envelope.optString("type")
                    if (!FoodBridgePolicy.isSupportedIncomingMessage(type)) {
                        return@addWebMessageListener
                    }
                    val requestId = envelope.optString("requestId")
                    if (requestId.isBlank()) return@addWebMessageListener
                    Log.d("MiniPayFoodWeb", "Bridge message type=$type requestId=$requestId")
                    when (type) {
                        "BRIDGE_READY", "REQUEST_AUTHORIZATION" ->
                            sendAuthorization(view, requestId)
                        "REQUEST_NATIVE_PAYMENT" -> {
                            val payload = envelope.optJSONObject("payload")
                                ?: return@addWebMessageListener
                            if (payload.length() !in 1..2 || !payload.has("externalOrderNo")) {
                                return@addWebMessageListener
                            }
                            val orderRefId = payload.optString("orderRefId")
                                .takeIf { it.isNotBlank() && it.length <= 64 }
                            payload.optString("externalOrderNo").takeIf {
                                it.isNotBlank() && it.length <= 64
                            }?.let { onRequestPayment(it, orderRefId, requestId) }
                        }
                        "NAVIGATION_STATE" -> {
                            val payload = envelope.optJSONObject("payload")
                                ?: return@addWebMessageListener
                            h5CanGoBack = payload.optBoolean("canGoBack", false)
                        }
                        "REQUEST_LOCATION_CONTEXT" -> sendLocationContext(view, requestId)
                        "CLOSE" -> onClose()
                    }
                }
                    Log.i("MiniPayFoodWeb", "Loading H5 page url=$pageUrl")
                    loadUrl(pageUrl)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (webViewLoading && webViewError == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            webViewError?.let { message ->
                Column(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(message, color = MaterialTheme.colorScheme.error)
                    Button(
                        onClick = {
                            hostedWebView?.stopLoading()
                            hostedWebView?.destroy()
                            hostedWebView = null
                            webViewError = null
                            webViewLoading = true
                            reloadGeneration += 1
                        },
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Text("重新加载")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyFoodWebViewState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("外卖 H5 尚未配置", style = MaterialTheme.typography.titleLarge)
        Text("配置受信任的 HTTPS 域名并完成授权契约后，才能加载 UniApp H5。")
    }
}

@Composable
private fun NativePaymentPlaceholderScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("MiniPay 原生付款页", style = MaterialTheme.typography.headlineSmall)
        Text("仅预留路由。金额、支付授权和密码输入将在支付后端接口完成后接入。")
    }
}

private fun openConfiguredUrl(context: Context, url: String, missingMessage: String) {
    if (url.isBlank()) {
        Toast.makeText(context, missingMessage, Toast.LENGTH_SHORT).show()
        return
    }
    val uri = runCatching { Uri.parse(url) }.getOrNull()
    if (uri == null || uri.scheme != "https") {
        Toast.makeText(context, "链接配置无效", Toast.LENGTH_SHORT).show()
        return
    }
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }.onFailure {
        Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun ConversationListRoute(
    navController: NavHostController,
    onBack: () -> Unit = { navController.popBackStack() },
    onOpenChat: (String, String) -> Unit,
    onOpenContacts: () -> Unit,
    onSearchClick: () -> Unit,
    viewModel: ConversationListViewModel = hiltViewModel()
) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val pendingFriendRequestCount by viewModel.pendingFriendRequestCount.collectAsStateWithLifecycle()
    AutoRefreshEffect(onRefresh = viewModel::refresh)
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                viewModel.refresh()
                delay(10_000)
            }
        }
    }

    ConversationListScreen(
        conversations = conversations,
        showContactsReminder = pendingFriendRequestCount > 0,
        onBack = onBack,
        onSearchClick = onSearchClick,
        onConversationClick = { conversation ->
            viewModel.clearUnread(conversation.id)
            onOpenChat(conversation.id, conversation.name)
        },
        onDeleteConversation = viewModel::deleteConversation,
        onContactsClick = onOpenContacts,
        onPlusAction = { tag ->
            when (tag) {
                "plus_create_group" -> navController.navigate(AppRoute.CREATE_GROUP)
                "plus_add_friend" -> navController.navigate(AppRoute.ADD_FRIEND)
                "plus_scan" -> navController.navigateSingle("finance/${FinanceDestination.SCAN.name}")
            }
        }
    )
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SearchFriendEntryPoint {
    fun friendApiService(): FriendApiService
    fun authRepository(): AuthRepository
}

private fun NavController.navigateSingle(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) { launchSingleTop = true }
}

@Composable
internal fun RootTabRequestEffect(
    rootTabRequest: String,
    onOpenMessages: () -> Unit,
    onConsumed: () -> Unit
) {
    LaunchedEffect(rootTabRequest) {
        if (rootTabRequest == ROOT_MESSAGES_REQUEST) {
            onOpenMessages()
            onConsumed()
        }
    }
}

private fun NavHostController.returnToRootMessages() {
    val rootEntry = runCatching { getBackStackEntry(AppRoute.HOME) }.getOrNull()
    if (rootEntry != null) {
        rootEntry.savedStateHandle[ROOT_TAB_REQUEST_KEY] = ROOT_MESSAGES_REQUEST
        popBackStack(AppRoute.HOME, inclusive = false)
    } else {
        navigate(AppRoute.HOME) { launchSingleTop = true }
        currentBackStackEntry?.savedStateHandle?.set(ROOT_TAB_REQUEST_KEY, ROOT_MESSAGES_REQUEST)
    }
}
