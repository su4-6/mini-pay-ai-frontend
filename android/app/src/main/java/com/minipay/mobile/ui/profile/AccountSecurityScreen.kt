package com.minipay.mobile.ui.profile

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minipay.mobile.network.AutoRefreshEffect
import com.minipay.mobile.profile.account.AccountPage
import com.minipay.mobile.profile.account.AccountSecurityEffect
import com.minipay.mobile.profile.account.AccountSecurityOverview
import com.minipay.mobile.profile.account.AccountSecurityUiState
import com.minipay.mobile.profile.account.AccountSecurityViewModel
import com.minipay.mobile.ui.theme.MilingError
import com.minipay.mobile.ui.theme.MilingSurface
import com.minipay.mobile.ui.theme.MilingSurfaceSubtle
import com.minipay.mobile.ui.theme.MilingTextSecondary

@Composable
fun AccountSecurityRoute(
    onBack: () -> Unit,
    onSessionInvalidated: () -> Unit,
    viewModel: AccountSecurityViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AutoRefreshEffect(
        enabled = !state.submitting && state.page is AccountPage.Overview,
        onRefresh = viewModel::refresh
    )
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                AccountSecurityEffect.PhoneChanged,
                AccountSecurityEffect.SessionInvalid -> onSessionInvalidated()
            }
        }
    }
    val accountBack = {
        if (!viewModel.backWithinAccount()) onBack()
    }
    BackHandler(onBack = accountBack)
    SecureWindow(enabled = state.page is AccountPage.PaymentPassword)
    AccountSecurityScreen(
        state = state,
        onBack = accountBack,
        onRetry = viewModel::refresh,
        onOpenPhone = viewModel::openPhone,
        onOpenEmail = viewModel::openEmail,
        onOpenEmailInput = viewModel::openEmailInput,
        onOpenPayment = viewModel::openPaymentPassword,
        onTargetChange = viewModel::updateTarget,
        onCodeChange = viewModel::updateCode,
        onSendPhone = { viewModel.requestPhoneChallenge() },
        onConfirmPhone = viewModel::confirmPhone,
        onSendEmail = { viewModel.requestEmailChallenge() },
        onConfirmEmail = viewModel::confirmEmail,
        onDeleteEmail = viewModel::deleteEmail,
        onConfirmPaymentCode = viewModel::confirmPaymentCode,
        onChangePaymentPassword = viewModel::changePaymentPassword,
        onResend = viewModel::resendCode,
        onDone = viewModel::returnToOverview
    )
}

@Composable
internal fun AccountSecurityScreen(
    state: AccountSecurityUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenPhone: () -> Unit,
    onOpenEmail: () -> Unit,
    onOpenEmailInput: () -> Unit,
    onOpenPayment: () -> Unit,
    onTargetChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onSendPhone: () -> Unit,
    onConfirmPhone: () -> Unit,
    onSendEmail: () -> Unit,
    onConfirmEmail: () -> Unit,
    onDeleteEmail: () -> Unit,
    onConfirmPaymentCode: () -> Unit,
    onChangePaymentPassword: (String, String) -> Unit,
    onResend: () -> Unit,
    onDone: () -> Unit
) {
    Scaffold(
        containerColor = MilingSurfaceSubtle,
        topBar = { AccountTopBar(onBack) },
        modifier = Modifier.statusBarsPadding().navigationBarsPadding()
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading && state.overview == null -> LoadingPage()
                state.overview == null -> ErrorPage(state.errorMessage, state.requestId, onRetry)
                else -> AccountPageContent(
                    state,
                    onRetry,
                    onOpenPhone,
                    onOpenEmail,
                    onOpenEmailInput,
                    onOpenPayment,
                    onTargetChange,
                    onCodeChange,
                    onSendPhone,
                    onConfirmPhone,
                    onSendEmail,
                    onConfirmEmail,
                    onDeleteEmail,
                    onConfirmPaymentCode,
                    onChangePaymentPassword,
                    onResend,
                    onDone
                )
            }
        }
    }
}

@Composable
private fun AccountPageContent(
    state: AccountSecurityUiState,
    onRetry: () -> Unit,
    onOpenPhone: () -> Unit,
    onOpenEmail: () -> Unit,
    onOpenEmailInput: () -> Unit,
    onOpenPayment: () -> Unit,
    onTargetChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onSendPhone: () -> Unit,
    onConfirmPhone: () -> Unit,
    onSendEmail: () -> Unit,
    onConfirmEmail: () -> Unit,
    onDeleteEmail: () -> Unit,
    onConfirmPaymentCode: () -> Unit,
    onChangePaymentPassword: (String, String) -> Unit,
    onResend: () -> Unit,
    onDone: () -> Unit
) {
    when (val page = state.page) {
        AccountPage.Overview -> OverviewPage(
            requireNotNull(state.overview),
            state,
            onRetry,
            onOpenPhone,
            onOpenEmail,
            onOpenPayment
        )
        AccountPage.PhoneInput -> TargetInputPage(
            title = "更换手机号",
            label = "新手机号",
            hint = "请输入 11 位中国大陆手机号",
            value = state.targetInput,
            keyboardType = KeyboardType.Phone,
            state = state,
            onValueChange = onTargetChange,
            onSubmit = onSendPhone
        )
        is AccountPage.PhoneCode -> CodePage(
            title = "验证新手机号",
            challengeTarget = page.challenge.maskedTarget,
            submitText = "确认更换",
            state = state,
            onCodeChange = onCodeChange,
            onSubmit = onConfirmPhone,
            onResend = onResend
        )
        AccountPage.EmailInput -> TargetInputPage(
            title = if (state.overview?.maskedEmail == null) "绑定邮箱" else "更换邮箱",
            label = "邮箱地址",
            hint = "name@example.com",
            value = state.targetInput,
            keyboardType = KeyboardType.Email,
            state = state,
            onValueChange = onTargetChange,
            onSubmit = onSendEmail
        )
        AccountPage.EmailCurrent -> EmailCurrentPage(
            maskedEmail = state.overview?.maskedEmail.orEmpty(),
            state = state,
            onChange = onOpenEmailInput,
            onDelete = onDeleteEmail
        )
        is AccountPage.EmailCode -> CodePage(
            title = "验证邮箱",
            challengeTarget = page.challenge.maskedTarget,
            submitText = "确认绑定",
            state = state,
            onCodeChange = onCodeChange,
            onSubmit = onConfirmEmail,
            onResend = onResend
        )
        is AccountPage.PaymentCode -> CodePage(
            title = "验证当前手机号",
            challengeTarget = page.challenge.maskedTarget,
            submitText = "验证身份",
            state = state,
            onCodeChange = onCodeChange,
            onSubmit = onConfirmPaymentCode,
            onResend = onResend
        )
        is AccountPage.PaymentPassword -> PaymentPasswordPage(
            state = state,
            onSubmit = onChangePaymentPassword
        )
        is AccountPage.Result -> ResultPage(page.title, page.message, onDone)
    }
}

@Composable
private fun AccountTopBar(onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).background(MilingSurface),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
        }
        Text("账号管理", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun LoadingPage() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.semantics { contentDescription = "账号信息加载中" })
    }
}

@Composable
private fun ErrorPage(message: String?, requestId: String?, retry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(message ?: "账号信息加载失败", color = MilingError)
        RequestId(requestId)
        Spacer(Modifier.height(20.dp))
        Button(onClick = retry) { Text("重试") }
    }
}

@Composable
private fun OverviewPage(
    overview: AccountSecurityOverview,
    state: AccountSecurityUiState,
    retry: () -> Unit,
    openPhone: () -> Unit,
    openEmail: () -> Unit,
    openPayment: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("安全信息", style = MaterialTheme.typography.headlineSmall)
        AccountRow("手机号", overview.maskedMobile, openPhone)
        AccountRow("邮箱", overview.maskedEmail ?: "未绑定", openEmail)
        AccountRow(
            "支付密码",
            if (overview.paymentPasswordSet) "已设置" else "未设置",
            if (overview.paymentPasswordSet) openPayment else null
        )
        if (state.refreshing || state.submitting) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp))
                Text(
                    if (state.submitting) "正在处理安全请求" else "正在刷新",
                    Modifier.padding(start = 10.dp),
                    color = MilingTextSecondary
                )
            }
        }
        state.errorMessage?.let {
            InlineError(it, state.requestId)
            TextButton(onClick = retry) { Text("重新加载") }
        }
    }
}

@Composable
private fun TargetInputPage(
    title: String,
    label: String,
    hint: String,
    value: String,
    keyboardType: KeyboardType,
    state: AccountSecurityUiState,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    FormColumn {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            placeholder = { Text(hint) },
            singleLine = true,
            enabled = !state.submitting,
            isError = state.errorMessage != null,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
        )
        state.errorMessage?.let { InlineError(it, state.requestId) }
        Button(
            onClick = onSubmit,
            enabled = value.isNotBlank() && !state.submitting,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            if (state.submitting) CircularProgressIndicator(Modifier.size(20.dp))
            else Text("获取验证码")
        }
    }
}

@Composable
private fun CodePage(
    title: String,
    challengeTarget: String,
    submitText: String,
    state: AccountSecurityUiState,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onResend: () -> Unit
) {
    FormColumn {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text("验证码已发送至 $challengeTarget", color = MilingTextSecondary)
        if (state.secondsUntilExpiry > 0) {
            Text("验证码 ${state.secondsUntilExpiry} 秒内有效", color = MilingTextSecondary)
        }
        OutlinedTextField(
            value = state.codeInput,
            onValueChange = onCodeChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("6 位验证码") },
            singleLine = true,
            enabled = !state.submitting && state.lockedSeconds == 0L,
            isError = state.errorMessage != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
        )
        if (state.lockedSeconds > 0L) {
            InlineError("验证已锁定，请在 ${state.lockedSeconds} 秒后重试", state.requestId)
        } else {
            state.errorMessage?.let { InlineError(it, state.requestId) }
        }
        Button(
            onClick = onSubmit,
            enabled = state.codeInput.length == 6 && state.secondsUntilExpiry > 0 &&
                state.lockedSeconds == 0L && !state.submitting,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            if (state.submitting) CircularProgressIndicator(Modifier.size(20.dp)) else Text(submitText)
        }
        TextButton(
            onClick = onResend,
            enabled = state.secondsUntilResend == 0L && !state.submitting,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(
                if (state.secondsUntilResend > 0) "${state.secondsUntilResend} 秒后重新获取"
                else "重新获取验证码"
            )
        }
    }
}

@Composable
private fun EmailCurrentPage(
    maskedEmail: String,
    state: AccountSecurityUiState,
    onChange: () -> Unit,
    onDelete: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    FormColumn {
        Text("当前邮箱", style = MaterialTheme.typography.headlineSmall)
        Text(maskedEmail, style = MaterialTheme.typography.titleLarge)
        Text("邮箱不作为 Android 登录方式。", color = MilingTextSecondary)
        state.errorMessage?.let { InlineError(it, state.requestId) }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onChange,
            enabled = !state.submitting,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) { Text("更换邮箱") }
        OutlinedButton(
            onClick = { confirmDelete = true },
            enabled = !state.submitting,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) { Text(if (state.submitting) "正在删除" else "删除邮箱") }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { if (!state.submitting) confirmDelete = false },
            title = { Text("确认删除邮箱？") },
            text = { Text("删除后该邮箱不再用于账号安全验证，但不会影响 Android 手机号登录。") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("确认删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun PaymentPasswordPage(
    state: AccountSecurityUiState,
    onSubmit: (String, String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var activeField by remember { mutableIntStateOf(0) }
    FormColumn {
        Text("修改支付密码", style = MaterialTheme.typography.headlineSmall)
        Text("请输入两次新的 6 位数字支付密码", color = MilingTextSecondary)
        SecurePasswordField(
            label = "新支付密码",
            length = password.length,
            selected = activeField == 0,
            onSelect = { activeField = 0 }
        )
        SecurePasswordField(
            label = "再次输入",
            length = confirmation.length,
            selected = activeField == 1,
            onSelect = { activeField = 1 }
        )
        state.errorMessage?.let { InlineError(it, state.requestId) }
        SecureNumericKeypad(
            enabled = !state.submitting,
            onDigit = { digit ->
                if (activeField == 0 && password.length < 6) password += digit
                if (activeField == 1 && confirmation.length < 6) confirmation += digit
            },
            onDelete = {
                if (activeField == 0) password = password.dropLast(1)
                else confirmation = confirmation.dropLast(1)
            }
        )
        Button(
            onClick = { onSubmit(password, confirmation) },
            enabled = password.length == 6 && confirmation.length == 6 && !state.submitting,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            if (state.submitting) CircularProgressIndicator(Modifier.size(20.dp)) else Text("确认修改")
        }
    }
}

@Composable
private fun SecurePasswordField(
    label: String,
    length: Int,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .semantics { contentDescription = "$label，已输入 $length 位" }
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MilingSurface
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label, color = MilingTextSecondary)
            Text("●".repeat(length), style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun SecureNumericKeypad(
    enabled: Boolean,
    onDigit: (String) -> Unit,
    onDelete: () -> Unit
) {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { digit ->
                    OutlinedButton(
                        onClick = { onDigit(digit) },
                        enabled = enabled,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) { Text(digit) }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.weight(1f))
            OutlinedButton(
                onClick = { onDigit("0") },
                enabled = enabled,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            ) { Text("0") }
            OutlinedButton(
                onClick = onDelete,
                enabled = enabled,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            ) { Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = "删除一位") }
        }
    }
}

@Composable
private fun ResultPage(title: String, message: String, done: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(message, color = MilingTextSecondary)
        Spacer(Modifier.height(36.dp))
        Button(onClick = done, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("完成")
        }
    }
}

@Composable
private fun FormColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        content = content
    )
}

@Composable
private fun AccountRow(label: String, value: String, onClick: (() -> Unit)?) {
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        shape = RoundedCornerShape(18.dp),
        color = MilingSurface,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, Modifier.weight(1f))
            Text(value, color = MilingTextSecondary)
            if (onClick != null) Icon(Icons.Outlined.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun InlineError(message: String, requestId: String?) {
    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Text(message, color = MilingError)
        RequestId(requestId)
    }
}

@Composable
private fun RequestId(requestId: String?) {
    requestId?.let { Text("请求编号：$it", color = MilingTextSecondary, style = MaterialTheme.typography.bodySmall) }
}

@Composable
internal fun SecureWindow(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        val window = view.context.findActivity()?.window
        val wasSecure = applySecureWindow(window, enabled)
        onDispose {
            restoreSecureWindow(window, enabled, wasSecure)
        }
    }
}

internal fun applySecureWindow(window: Window?, enabled: Boolean): Boolean {
    val wasSecure = window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_SECURE) ==
        WindowManager.LayoutParams.FLAG_SECURE
    if (enabled) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    return wasSecure
}

internal fun restoreSecureWindow(window: Window?, enabled: Boolean, wasSecure: Boolean) {
    if (enabled && !wasSecure) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
