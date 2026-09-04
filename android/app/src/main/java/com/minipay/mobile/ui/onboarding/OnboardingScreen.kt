package com.minipay.mobile.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.minipay.mobile.onboarding.OnboardingStep
import com.minipay.mobile.onboarding.OnboardingUiState
import com.minipay.mobile.onboarding.OnboardingViewModel
import com.minipay.mobile.profile.isNicknameValid
import com.minipay.mobile.ui.home.MilingMascot
import com.minipay.mobile.ui.theme.*

@Composable
fun OnboardingRoute(
    onCompleted: () -> Unit,
    onLogout: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmExit by remember { mutableStateOf(false) }
    val busy = state.processingAvatar || state.submitting
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::selectAvatar)
    }
    BackHandler(state.step == OnboardingStep.PROFILE) {
        if (!busy) confirmExit = true
    }
    OnboardingScreen(
        state = state,
        onNicknameChange = viewModel::updateNickname,
        onChooseAvatar = {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onRemoveAvatar = viewModel::removeAvatar,
        onBack = { confirmExit = true },
        onSubmit = viewModel::submit,
        onCompleted = onCompleted
    )
    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("退出初始化？") },
            text = { Text("昵称和头像尚未保存。") },
            confirmButton = { TextButton(onClick = onLogout) { Text("退出登录", color = MilingError) } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("继续填写") } }
        )
    }
}

@Composable
internal fun OnboardingScreen(
    state: OnboardingUiState,
    onNicknameChange: (String) -> Unit,
    onChooseAvatar: () -> Unit,
    onRemoveAvatar: () -> Unit,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
    onCompleted: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().background(MilingSurface).statusBarsPadding()
            .navigationBarsPadding().padding(horizontal = MilingSpacing.Xl)
    ) {
        Row(
            Modifier.fillMaxWidth().height(64.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.step == OnboardingStep.PROFILE) {
                IconButton(
                    onClick = onBack,
                    enabled = !state.processingAvatar && !state.submitting,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Outlined.Close, "退出初始化")
                }
            } else Spacer(Modifier.size(48.dp))
            Text(
                if (state.step == OnboardingStep.PROFILE) "完善资料" else "设置完成",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            Text(if (state.step == OnboardingStep.PROFILE) "1 / 2" else "2 / 2", color = MilingTextSecondary)
        }
        if (state.step == OnboardingStep.PROFILE) {
            ProfileStep(state, onNicknameChange, onChooseAvatar, onRemoveAvatar, onSubmit)
        } else {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(Modifier.size(96.dp).semantics { contentDescription = "初始化成功" }, CircleShape, color = MilingSuccessSoft) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Check, null, tint = MilingSuccess, modifier = Modifier.size(48.dp))
                    }
                }
                Spacer(Modifier.height(MilingSpacing.Section))
                Text("欢迎你，${state.nickname}", style = MaterialTheme.typography.headlineMedium)
                Text("资料已保存，开始探索 MiniPay 吧", color = MilingTextSecondary)
                Spacer(Modifier.height(48.dp))
                Button(onClick = onCompleted, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("开始使用") }
            }
        }
    }
}

@Composable
private fun ProfileStep(
    state: OnboardingUiState,
    onNicknameChange: (String) -> Unit,
    onChooseAvatar: () -> Unit,
    onRemoveAvatar: () -> Unit,
    onSubmit: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Text("先认识一下你", style = MaterialTheme.typography.headlineMedium)
        Text("设置昵称，头像可以稍后添加", color = MilingTextSecondary)
        Spacer(Modifier.height(40.dp))
        Box(
            Modifier.size(120.dp).clip(CircleShape).background(MilingSurfaceSubtle)
                .border(1.dp, MilingBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (state.selectedAvatarUri != null) {
                AsyncImage(state.selectedAvatarUri, "所选头像预览", Modifier.fillMaxSize().clip(CircleShape))
            } else MilingMascot(96.dp)
            IconButton(
                onClick = onChooseAvatar,
                enabled = !state.processingAvatar && !state.submitting,
                modifier = Modifier.align(Alignment.BottomEnd).size(48.dp).background(MilingPrimary, CircleShape)
            ) {
                if (state.processingAvatar) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White)
                else Icon(Icons.Outlined.Edit, "选择头像", tint = Color.White)
            }
        }
        if (state.selectedAvatarUri != null) TextButton(onClick = onRemoveAvatar) { Text("使用默认头像") }
        else Spacer(Modifier.height(48.dp))
        OutlinedTextField(
            value = state.nickname,
            onValueChange = onNicknameChange,
            label = { Text("昵称") },
            supportingText = { Text("${state.nickname.codePointCount(0, state.nickname.length)}/20") },
            isError = state.nickname.isNotEmpty() && !isNicknameValid(state.nickname.trim()),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("onboarding_nickname")
        )
        state.errorMessage?.let { Text(it, color = MilingError, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) }
        Spacer(Modifier.height(MilingSpacing.Xxl))
        Button(
            onClick = onSubmit,
            enabled = isNicknameValid(state.nickname.trim()) && !state.processingAvatar && !state.submitting,
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("onboarding_submit"),
            shape = RoundedCornerShape(MilingRadii.Medium)
        ) {
            if (state.submitting) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White)
            else Text("完成初始化")
        }
        Spacer(Modifier.height(24.dp))
    }
}
