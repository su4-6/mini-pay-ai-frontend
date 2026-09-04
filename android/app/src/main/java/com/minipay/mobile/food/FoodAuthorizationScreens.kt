package com.minipay.mobile.food

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.minipay.mobile.R
import kotlinx.coroutines.launch

@Composable
fun FoodEntryScreen(
    onReady: () -> Unit,
    onConsentRequired: () -> Unit,
    onBack: () -> Unit,
    viewModel: FoodIntegrationViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.checkEntry(onReady, onConsentRequired) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (state.checkingEntry) CircularProgressIndicator()
            Text(if (state.checkingEntry) "正在安全登录意向点餐…" else state.error.orEmpty())
            if (!state.checkingEntry && state.error != null) {
                Spacer(Modifier.height(16.dp))
                Button(onClick = { viewModel.checkEntry(onReady, onConsentRequired) }) {
                    Text("重试")
                }
                OutlinedButton(onClick = onBack) { Text("返回") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FoodAuthorizationScreen(
    onAuthorized: () -> Unit,
    onBack: () -> Unit,
    onOpenAgreement: (String) -> Unit,
    viewModel: FoodIntegrationViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var mobile by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var agreementsAccepted by remember { mutableStateOf(false) }
    var locationDenied by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val mobileBringIntoView = remember { BringIntoViewRequester() }
    val codeBringIntoView = remember { BringIntoViewRequester() }
    val feedbackBringIntoView = remember { BringIntoViewRequester() }
    val codeFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { viewModel.loadConsent() }
    LaunchedEffect(state.phoneChallengeId) {
        if (state.phoneChallengeId != null) {
            codeFocusRequester.requestFocus()
            codeBringIntoView.bringIntoView()
        }
    }
    LaunchedEffect(state.error) {
        if (state.error != null) feedbackBringIntoView.bringIntoView()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        locationDenied = !granted
        if (granted) viewModel.authorize(code, onAuthorized)
    }
    fun authorizeWithLocation() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.authorize(code, onAuthorized)
        } else {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("账号绑定") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回")
                }
            }
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BindingLogo(R.drawable.minipay_binding_logo, "MiniPay")
                Icon(
                    Icons.Outlined.Link,
                    contentDescription = "绑定",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 22.dp).size(34.dp)
                )
                BindingLogo(R.drawable.yixiang_food_logo, "意向点餐")
            }

            Text(
                "登录意向点餐账号，并与 MiniPay 绑定",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "同意意向点餐获取以下信息，用于提供点餐、配送和订单服务",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 14.dp)
            )
            PermissionBullet("MiniPay 昵称和头像，用于初始化并展示意向点餐账号")
            PermissionBullet("验证后的手机号，用于账号登录、配送联系和订单通知")
            PermissionBullet("当前位置，用于查询附近可配送或自取门店")

            OutlinedTextField(
                value = mobile,
                onValueChange = {
                    val next = it.filter(Char::isDigit).take(11)
                    if (next != mobile) {
                        code = ""
                        viewModel.clearPhoneChallenge()
                    }
                    mobile = next
                },
                leadingIcon = { Text("+86") },
                label = { Text("请输入手机号") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth().padding(top = 22.dp)
                    .bringIntoViewRequester(mobileBringIntoView)
                    .onFocusChanged { focus ->
                        if (focus.isFocused) coroutineScope.launch {
                            mobileBringIntoView.bringIntoView()
                        }
                    }
            )
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.filter(Char::isDigit).take(6) },
                label = { Text("请输入验证码") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                trailingIcon = {
                    TextButton(
                        onClick = { viewModel.requestPhoneCode(mobile) },
                        enabled = mobile.length == 11 && !state.sendingCode &&
                            state.resendAfterSeconds == 0
                    ) {
                        Text(when {
                            state.sendingCode -> "发送中…"
                            state.resendAfterSeconds > 0 -> "${state.resendAfterSeconds}s"
                            else -> "获取验证码"
                        })
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    .focusRequester(codeFocusRequester)
                    .bringIntoViewRequester(codeBringIntoView)
                    .onFocusChanged { focus ->
                        if (focus.isFocused) coroutineScope.launch {
                            codeBringIntoView.bringIntoView()
                        }
                    }
            )

            state.codeSentToMaskedMobile?.let { maskedMobile ->
                Text(
                    "验证码已发送至 $maskedMobile",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(agreementsAccepted, { agreementsAccepted = it })
                Text("同意", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "《账号绑定协议》",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onOpenAgreement("binding-agreement") }
                )
                Text(
                    "《用户服务协议》",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onOpenAgreement("terms") }
                )
            }

            if (locationDenied) {
                Text(
                    "需要定位权限才能完成绑定。你可以重新授权，或前往系统设置开启权限。",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                )
                TextButton(onClick = {
                    context.startActivity(Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    ))
                }) { Text("前往系统设置") }
            }
            state.error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                        .bringIntoViewRequester(feedbackBringIntoView)
                )
            }
            if (state.authorization == null && state.error != null) {
                OutlinedButton(
                    onClick = viewModel::loadConsent,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                ) { Text("重新加载授权信息") }
            }

            Button(
                onClick = ::authorizeWithLocation,
                enabled = state.authorization != null && mobile.length == 11 && code.length >= 4 &&
                    state.phoneChallengeId != null && agreementsAccepted && !state.authorizing,
                modifier = Modifier.fillMaxWidth().padding(top = 22.dp).height(52.dp),
                shape = RoundedCornerShape(26.dp)
            ) {
                if (state.authorizing) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text("确认授权")
                }
            }
            Text(
                "绑定后再次进入点餐将自动登录，可在“我的－应用授权管理”中解除绑定。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 18.dp)
            )
        }
    }
}

@Composable
private fun BindingLogo(drawable: Int, description: String) {
    Box(
        Modifier.size(82.dp).clip(RoundedCornerShape(20.dp)).background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = drawable,
            contentDescription = description,
            contentScale = ContentScale.Fit,
            placeholder = painterResource(drawable),
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun PermissionBullet(text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("•", color = MaterialTheme.colorScheme.outline)
        Text(
            text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodAgreementScreen(page: String, origin: String, onBack: () -> Unit) {
    val safePage = if (page == "binding-agreement") "binding-agreement" else "terms"
    val target = origin.trimEnd('/') + "/#/pages/agreement/" + safePage
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (safePage == "binding-agreement") "账号绑定协议" else "用户服务协议") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回")
                }
            }
        )
    }) { padding ->
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(padding),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest
                        ): Boolean = request.url.toString() != target
                    }
                    loadUrl(target)
                }
            }
        )
    }
}
