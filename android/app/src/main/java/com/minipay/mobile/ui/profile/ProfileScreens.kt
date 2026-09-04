package com.minipay.mobile.ui.profile

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.minipay.mobile.network.AutoRefreshEffect
import com.minipay.mobile.profile.PreparedAvatar
import com.minipay.mobile.profile.ProfileLoadState
import com.minipay.mobile.profile.ProfileViewModel
import com.minipay.mobile.profile.UserProfile
import com.minipay.mobile.ui.home.MilingMascot
import com.minipay.mobile.ui.components.AvatarImage
import com.minipay.mobile.ui.theme.MilingError
import com.minipay.mobile.ui.theme.MilingIconSecondary
import com.minipay.mobile.ui.theme.MilingPrimary
import com.minipay.mobile.ui.theme.MilingRadii
import com.minipay.mobile.ui.theme.MilingSpacing
import com.minipay.mobile.ui.theme.MilingSurface
import com.minipay.mobile.ui.theme.MilingSurfaceSubtle
import com.minipay.mobile.ui.theme.MilingTextPrimary
import com.minipay.mobile.ui.theme.MilingTextSecondary
import kotlinx.coroutines.launch

@Composable
fun ProfileRoute(
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenAccountSecurity: () -> Unit = {},
    onOpenFeature: (String) -> Unit,
    onLogout: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
    embeddedInRoot: Boolean = false
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AutoRefreshEffect(onRefresh = viewModel::refresh)
    ProfileScreen(state, onBack, onEdit, onOpenAccountSecurity, onOpenFeature, onLogout, viewModel::refresh, embeddedInRoot)
}

@Composable
internal fun ProfileScreen(
    state: ProfileLoadState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenAccountSecurity: () -> Unit = {},
    onOpenFeature: (String) -> Unit,
    onLogout: () -> Unit,
    onRetry: () -> Unit,
    embeddedInRoot: Boolean = false
) {
    var showLogout by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(MilingSurfaceSubtle).statusBarsPadding()
            .then(if (embeddedInRoot) Modifier else Modifier.navigationBarsPadding())
    ) {
        ProfileTopBar("我的", if (embeddedInRoot) null else onBack)
        when (state) {
            ProfileLoadState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is ProfileLoadState.Failed -> ErrorState(state.message, onRetry)
            is ProfileLoadState.Ready -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = MilingSpacing.Lg, vertical = MilingSpacing.Md),
                verticalArrangement = Arrangement.spacedBy(MilingSpacing.Lg)
            ) {
                ProfileCard(state.profile, onEdit)
                ProfileGroup(listOf(
                    ProfileItem("账号管理", Icons.Outlined.Lock, action = onOpenAccountSecurity),
                    ProfileItem("应用授权管理", Icons.Outlined.VerifiedUser) { onOpenFeature("应用授权管理") }
                ))
                ProfileGroup(listOf(
                    ProfileItem("钱包", Icons.Outlined.AccountBalanceWallet) { onOpenFeature("钱包") },
                    ProfileItem("订单", Icons.Outlined.History) { onOpenFeature("订单") },
                    ProfileItem("记忆", Icons.Outlined.Memory) { onOpenFeature("记忆") }
                ))
                ProfileGroup(listOf(
                    ProfileItem("退出登录", null, MilingPrimary) { showLogout = true },
                    ProfileItem("注销账号", null, MilingError) { onOpenFeature("注销账号") }
                ))
            }
        }
    }
    if (showLogout) AlertDialog(
        onDismissRequest = { showLogout = false },
        title = { Text("退出登录") },
        text = { Text("确认退出当前账号吗？") },
        confirmButton = { TextButton(onClick = { showLogout = false; onLogout() }) { Text("退出") } },
        dismissButton = { TextButton(onClick = { showLogout = false }) { Text("取消") } }
    )
}

@Composable
fun EditProfileRoute(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val profile = (state as? ProfileLoadState.Ready)?.profile
    if (profile == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else {
        EditProfileScreen(profile, viewModel, onBack, onSaved)
    }
}

@Composable
private fun LegacyEditProfileScreen(
    profile: UserProfile,
    viewModel: ProfileViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    var nickname by remember(profile.version) { mutableStateOf(profile.nickname) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var preparedAvatar by remember { mutableStateOf<PreparedAvatar?>(null) }
    var processing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val dirty = nickname != profile.nickname || preparedAvatar != null
    val nicknameValid = isNicknameValid(nickname)
    val requestBack = { if (dirty && !saving) confirmDiscard = true else onBack() }
    BackHandler(onBack = requestBack)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            processing = true; error = null
            viewModel.runCatchingPrepare(uri)
                .onSuccess { selectedUri = uri; preparedAvatar = it }
                .onFailure { error = it.message ?: "无法处理所选图片" }
            processing = false
        }
    }

    Column(Modifier.fillMaxSize().background(MilingSurface).statusBarsPadding().navigationBarsPadding()) {
        ProfileTopBar("编辑个人信息", requestBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(MilingSpacing.Xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.size(112.dp).clip(CircleShape).background(MilingSurfaceSubtle), Alignment.Center) {
                when {
                    selectedUri != null -> AsyncImage(selectedUri, "头像预览", Modifier.fillMaxSize())
                    profile.avatarUrl != null -> AvatarImage(
                        avatarUrl = profile.avatarUrl,
                        contentDescription = "头像预览",
                        modifier = Modifier.fillMaxSize()
                    )
                    else -> MilingMascot(96.dp)
                }
                IconButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !processing && !saving,
                    modifier = Modifier.align(Alignment.BottomEnd).size(40.dp).background(MilingPrimary, CircleShape)
                ) { Icon(Icons.Outlined.Edit, "选择头像", tint = Color.White) }
            }
            if (processing) { Spacer(Modifier.height(8.dp)); Text("正在处理图片…", color = MilingTextSecondary) }
            Spacer(Modifier.height(32.dp))
            OutlinedTextField(
                value = nickname,
                onValueChange = { if (it.codePointCount() <= 20) nickname = it; error = null },
                modifier = Modifier.fillMaxWidth().testTag("profile_nickname_input"),
                label = { Text("昵称") },
                supportingText = { Text(if (nicknameValid) "${nickname.codePointCount()}/20" else "仅支持 2～20 个中文、字母、数字或下划线") },
                isError = nickname.isNotEmpty() && !nicknameValid,
                singleLine = true
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = profile.miniPayNo,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                label = { Text("MiniPay 号") },
                enabled = false,
                singleLine = true
            )
            error?.let { Text(it, color = MilingError, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) }
            Spacer(Modifier.height(32.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = requestBack, modifier = Modifier.weight(1f), enabled = !saving) { Text("取消") }
                Button(
                    onClick = {
                        scope.launch {
                            saving = true; error = null
                            viewModel.save(nickname.trim(), preparedAvatar)
                                .onSuccess { onSaved() }
                                .onFailure { error = viewModel.errorMessage(it) }
                            saving = false
                        }
                    },
                    modifier = Modifier.weight(1f).testTag("profile_save_button"),
                    enabled = dirty && nicknameValid && !processing && !saving
                ) { if (saving) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White) else Text("保存") }
            }
        }
    }
    if (confirmDiscard) AlertDialog(
        onDismissRequest = { confirmDiscard = false },
        title = { Text("放弃修改？") },
        text = { Text("当前修改尚未保存。") },
        confirmButton = { TextButton(onClick = onBack) { Text("放弃") } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("继续编辑") } }
    )
}

@Composable
private fun EditProfileScreen(
    profile: UserProfile,
    viewModel: ProfileViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    var nickname by remember(profile.version) { mutableStateOf(profile.nickname) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var preparedAvatar by remember { mutableStateOf<PreparedAvatar?>(null) }
    var processing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val dirty = nickname != profile.nickname || preparedAvatar != null
    val nicknameValid = isNicknameValid(nickname)
    val requestBack = { if (dirty && !saving) confirmDiscard = true else onBack() }
    BackHandler(onBack = requestBack)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            processing = true
            error = null
            viewModel.runCatchingPrepare(uri)
                .onSuccess { selectedUri = uri; preparedAvatar = it }
                .onFailure { error = it.message ?: "无法处理所选图片" }
            processing = false
        }
    }

    Column(Modifier.fillMaxSize().background(MilingSurface).statusBarsPadding().navigationBarsPadding()) {
        ProfileTopBar("编辑个人信息", requestBack)
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(124.dp))
            Box(Modifier.size(144.dp).clip(CircleShape).background(MilingSurfaceSubtle), Alignment.Center) {
                when {
                    selectedUri != null -> AsyncImage(selectedUri, "头像预览", Modifier.fillMaxSize())
                    profile.avatarUrl != null -> AvatarImage(
                        avatarUrl = profile.avatarUrl,
                        contentDescription = "头像预览",
                        modifier = Modifier.fillMaxSize()
                    )
                    else -> MilingMascot(118.dp)
                }
                IconButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !processing && !saving,
                    modifier = Modifier.align(Alignment.BottomEnd).size(54.dp).background(Color(0xFF4138FF), CircleShape)
                ) { Icon(Icons.Outlined.Add, "选择头像", tint = Color.White, modifier = Modifier.size(32.dp)) }
            }
            if (processing) Text("正在处理图片…", color = MilingTextSecondary, modifier = Modifier.padding(top = 10.dp))
            Spacer(Modifier.height(78.dp))
            Text("账号名称", modifier = Modifier.fillMaxWidth(), color = Color(0xFF919191), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))
            Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFF7F7F7), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.height(72.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = nickname,
                        onValueChange = { if (it.codePointCount() <= 20) nickname = it; error = null },
                        modifier = Modifier.weight(1f).testTag("profile_nickname_input"),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium.copy(color = MilingTextPrimary)
                    )
                    Text("${nickname.codePointCount()}/20", color = Color(0xFFB6B6B6), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(
                if (nickname.isNotEmpty() && !nicknameValid) "仅支持 2–20 个中文、字母、数字或下划线" else "支持中文、数字、下划线或减号",
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                color = if (nickname.isNotEmpty() && !nicknameValid) MilingError else Color(0xFFC7C7C7),
                style = MaterialTheme.typography.bodyMedium
            )
            error?.let { Text(it, color = MilingError, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier.weight(1f).height(58.dp).clickable(enabled = !saving, onClick = requestBack),
                color = Color(0xFFF7F7F7), shape = RoundedCornerShape(16.dp)
            ) { Box(contentAlignment = Alignment.Center) { Text("取消", style = MaterialTheme.typography.titleMedium) } }
            Button(
                onClick = {
                    scope.launch {
                        saving = true
                        error = null
                        viewModel.save(nickname.trim(), preparedAvatar)
                            .onSuccess { onSaved() }
                            .onFailure { error = viewModel.errorMessage(it) }
                        saving = false
                    }
                },
                modifier = Modifier.weight(1f).height(58.dp).testTag("profile_save_button"),
                enabled = dirty && nicknameValid && !processing && !saving,
                shape = RoundedCornerShape(16.dp)
            ) { if (saving) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White) else Text("确认", style = MaterialTheme.typography.titleMedium) }
        }
    }
    if (confirmDiscard) AlertDialog(
        onDismissRequest = { confirmDiscard = false },
        title = { Text("放弃修改？") },
        text = { Text("当前修改尚未保存。") },
        confirmButton = { TextButton(onClick = onBack) { Text("放弃") } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("继续编辑") } }
    )
}

@Composable
fun FeaturePlaceholderScreen(feature: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MilingSurface).statusBarsPadding().navigationBarsPadding()) {
        ProfileTopBar(feature, onBack)
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                if (feature == "钱包") "钱包功能建设中，银行卡管理将在钱包内提供。"
                else "$feature 功能建设中",
                color = MilingTextSecondary
            )
        }
    }
}

@Composable
private fun ProfileTopBar(title: String, onBack: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(onClick = onBack, Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
        else Spacer(Modifier.size(48.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ProfileCard(profile: UserProfile, onEdit: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(MilingRadii.Large)).background(MilingSurface)
            .clickable(role = Role.Button, onClick = onEdit)
            .semantics { contentDescription = "编辑个人信息" }
            .padding(20.dp).testTag("profile_summary"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(Modifier.size(68.dp), CircleShape, color = MilingSurfaceSubtle) {
            if (profile.avatarUrl != null) AvatarImage(
                avatarUrl = profile.avatarUrl,
                contentDescription = "${profile.nickname}的头像",
                modifier = Modifier.fillMaxSize()
            )
            else Box(contentAlignment = Alignment.Center) { MilingMascot(58.dp) }
        }
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(profile.nickname, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("MiniPay 号 ${profile.miniPayNo}", color = MilingTextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private data class ProfileItem(val title: String, val icon: ImageVector?, val color: Color = MilingTextPrimary, val action: () -> Unit)

@Composable
private fun ProfileGroup(items: List<ProfileItem>) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(MilingRadii.Large)).background(MilingSurface)) {
        items.forEachIndexed { index, item ->
            Row(
                Modifier.fillMaxWidth().height(60.dp).clickable(role = Role.Button, onClick = item.action)
                    .padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                item.icon?.let { Icon(it, null, tint = MilingIconSecondary); Spacer(Modifier.size(14.dp)) }
                Text(item.title, color = item.color, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.ChevronRight, null, tint = MilingIconSecondary)
            }
            if (index < items.lastIndex) HorizontalDivider(Modifier.padding(start = 18.dp), color = Color(0xFFEEF1F5))
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = MilingTextSecondary)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("重试") }
    }
}

private fun String.codePointCount(): Int = codePointCount(0, length)

internal fun isNicknameValid(value: String): Boolean {
    val length = value.codePointCount()
    return length in 2..20 && value.codePoints().allMatch {
        it == '_'.code || Character.isLetterOrDigit(it) || Character.UnicodeScript.of(it) == Character.UnicodeScript.HAN
    }
}

private suspend fun ProfileViewModel.runCatchingPrepare(uri: Uri): Result<PreparedAvatar> =
    runCatching { prepareAvatar(uri) }
