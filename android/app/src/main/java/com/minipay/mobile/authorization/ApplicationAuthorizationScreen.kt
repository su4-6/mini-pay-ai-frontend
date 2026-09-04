package com.minipay.mobile.authorization

import android.webkit.CookieManager
import android.webkit.WebStorage
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val PageBackground = Color.White
private val SecondaryText = Color(0xFF8D9199)
private val DividerColor = Color(0xFFF0F1F3)
private val CancelBackground = Color(0xFFF7F7F8)
private val UnbindBackground = Color(0xFFE8EEFF)
private val UnbindText = Color(0xFF1748D1)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplicationAuthorizationScreen(
    onBack: () -> Unit,
    viewModel: ApplicationAuthorizationViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showUnbindDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = PageBackground,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PageBackground),
                title = {
                    Text(
                        "应用授权管理",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        AuthorizationAccountContent(
            state = state,
            onRetry = viewModel::refresh,
            onApplicationClick = { application ->
                viewModel.select(application)
                showUnbindDialog = true
            },
            modifier = Modifier.padding(padding)
        )
    }

    if (showUnbindDialog && state.selected != null) {
        UnbindApplicationDialog(
            revoking = state.revoking,
            error = state.error,
            onDismiss = {
                if (!state.revoking) {
                    showUnbindDialog = false
                    viewModel.select(null)
                }
            },
            onConfirm = {
                viewModel.revoke {
                    CookieManager.getInstance().removeAllCookies(null)
                    CookieManager.getInstance().flush()
                    WebStorage.getInstance().deleteAllData()
                    showUnbindDialog = false
                }
            }
        )
    }
}

@Composable
internal fun AuthorizationAccountContent(
    state: ApplicationAuthorizationState,
    onRetry: () -> Unit,
    onApplicationClick: (ApplicationAuthorizationDto) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxSize().background(PageBackground)) {
        when {
            state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            state.error != null && state.applications.isEmpty() -> Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(state.error, color = MaterialTheme.colorScheme.error)
                Button(onClick = onRetry) { Text("重试") }
            }
            state.applications.isEmpty() -> Text(
                "暂无已授权应用",
                modifier = Modifier.align(Alignment.Center),
                color = SecondaryText,
                fontSize = 16.sp
            )
            else -> Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp)) {
                state.applications.forEachIndexed { index, application ->
                    AuthorizationAccountRow(
                        name = if (application.applicationId == "yshop-food") {
                            "意向外卖"
                        } else application.displayName,
                        account = state.accountLabels[application.applicationId] ?: "已绑定",
                        onClick = { onApplicationClick(application) }
                    )
                    if (index < state.applications.lastIndex) {
                        HorizontalDivider(color = DividerColor)
                    }
                }
                state.error?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun AuthorizationAccountRow(name: String, account: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(82.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, modifier = Modifier.weight(1f), fontSize = 19.sp, color = Color(0xFF15171A))
        Text(account, color = SecondaryText, fontSize = 17.sp)
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = "解除$name 绑定",
            modifier = Modifier.padding(start = 8.dp).size(24.dp),
            tint = Color(0xFFB0B3B8)
        )
    }
}

@Composable
internal fun UnbindApplicationDialog(
    revoking: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = !revoking,
            dismissOnClickOutside = !revoking,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.78f),
            shape = RoundedCornerShape(30.dp),
            color = Color.White
        ) {
            Column(
                Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "解除意向外卖绑定",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF17191C)
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    "解绑后将无法继续通过 MiniPay 使用该意向外卖账号",
                    modifier = Modifier.fillMaxWidth(),
                    color = SecondaryText,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    textAlign = TextAlign.Center
                )
                error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(24.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        enabled = !revoking,
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(15.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CancelBackground,
                            contentColor = Color(0xFF202124),
                            disabledContainerColor = CancelBackground,
                            disabledContentColor = SecondaryText
                        ),
                        elevation = ButtonDefaults.buttonElevation(0.dp)
                    ) { Text("不同意", fontSize = 17.sp) }
                    Button(
                        onClick = onConfirm,
                        enabled = !revoking,
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(15.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = UnbindBackground,
                            contentColor = UnbindText,
                            disabledContainerColor = UnbindBackground,
                            disabledContentColor = UnbindText
                        ),
                        elevation = ButtonDefaults.buttonElevation(0.dp)
                    ) {
                        if (revoking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = UnbindText
                            )
                            Spacer(Modifier.size(8.dp))
                            Text("解绑中…", fontSize = 16.sp)
                        } else Text("解绑", fontSize = 17.sp)
                    }
                }
            }
        }
    }
}
