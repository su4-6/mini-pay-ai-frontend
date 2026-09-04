package com.minipay.mobile.ui.group

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.minipay.mobile.chat.GroupInfoViewModel
import com.minipay.mobile.chat.GroupMemberInput
import com.minipay.mobile.chat.GroupMemberResponse
import com.minipay.mobile.ui.components.UserAvatar
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.minipay.mobile.avatar.avatarDiskCacheKey
import com.minipay.mobile.avatar.avatarMemoryCacheKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupInfoRoute(
    onBack: () -> Unit,
    onExitToMessages: () -> Unit,
    onPickMembers: (Boolean) -> Unit,
    viewModel: GroupInfoViewModel = hiltViewModel()
) {
    val detail by viewModel.detail.collectAsState()
    val avatarPreviewUrl by viewModel.avatarPreviewUrl.collectAsState()
    val context = LocalContext.current
    val isOwner = detail?.ownerId == viewModel.currentUserId
    var nicknameEditor by remember { mutableStateOf<String?>(null) }
    var groupNameEditor by remember { mutableStateOf<String?>(null) }
    var avatarUploading by remember { mutableStateOf(false) }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            avatarUploading = true
            viewModel.updateAvatar(uri) { avatarUploading = false }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("群聊信息") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.foundation.layout.Box(
                        Modifier.size(88.dp).clip(RoundedCornerShape(18.dp))
                            .clickable(enabled = isOwner && !avatarUploading) { avatarPicker.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        val displayedAvatarUrl = avatarPreviewUrl ?: detail?.avatarUrl
                        val avatarRequest = displayedAvatarUrl?.let { url ->
                            ImageRequest.Builder(context)
                                .data(url)
                                .diskCacheKey(avatarDiskCacheKey(url))
                                .memoryCacheKey(avatarMemoryCacheKey(url, 88))
                                .build()
                        }
                        AsyncImage(
                            model = avatarRequest,
                            contentDescription = "群头像",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(88.dp)
                        )
                        if (avatarUploading) CircularProgressIndicator(Modifier.size(32.dp))
                    }
                    if (isOwner) Text("点击更换群头像", style = MaterialTheme.typography.labelMedium)
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    detail?.members?.take(8)?.forEach { MemberChip(it, it.userId == detail?.ownerId) }
                    FilledTonalIconButton(onClick = { onPickMembers(false) }) { Icon(Icons.Outlined.Add, "添加成员") }
                    if (isOwner) FilledTonalIconButton(onClick = { onPickMembers(true) }) { Icon(Icons.Outlined.Remove, "删除成员") }
                }
            }
            item { Divider() }
            item {
                InfoRow(
                    "群聊名称",
                    detail?.name.orEmpty(),
                    onClick = if (isOwner) ({ groupNameEditor = detail?.name.orEmpty() }) else null
                )
            }
            item {
                val mine = detail?.members?.firstOrNull { it.userId == viewModel.currentUserId }
                InfoRow("我在本群的昵称", mine?.nickname.orEmpty(), onClick = { nicknameEditor = mine?.nickname.orEmpty() })
            }
            item {
                Spacer(Modifier.height(24.dp))
                TextButton(
                    onClick = {
                        if (isOwner) viewModel.disband { if (it) onExitToMessages() }
                        else viewModel.leave { if (it) onExitToMessages() }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (isOwner) "解散群聊" else "退出群聊", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    nicknameEditor?.let { nickname ->
        AlertDialog(
            onDismissRequest = { nicknameEditor = null },
            title = { Text("我在本群的昵称") },
            text = {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nicknameEditor = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val value = nicknameEditor.orEmpty().trim()
                    if (value.isNotBlank()) viewModel.updateNickname(value) { }
                    nicknameEditor = null
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { nicknameEditor = null }) { Text("取消") } }
        )
    }
    groupNameEditor?.let { currentName ->
        val normalized = currentName.trim()
        AlertDialog(
            onDismissRequest = { groupNameEditor = null },
            title = { Text("修改群聊名称") },
            text = {
                OutlinedTextField(
                    value = currentName,
                    onValueChange = { if (it.length <= 128) groupNameEditor = it },
                    singleLine = true,
                    supportingText = { Text("${currentName.length}/128") }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = normalized.isNotBlank() && normalized.length <= 128,
                    onClick = {
                        viewModel.rename(normalized) { success ->
                            if (success) groupNameEditor = null
                        }
                    }
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { groupNameEditor = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun MemberChip(member: GroupMemberResponse, isOwner: Boolean) {
    AssistChip(onClick = {}, label = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val name = member.displayName ?: member.nickname ?: member.originalNickname ?: "群成员"
            UserAvatar(name, member.avatarUrl, member.userId.hashCode(), size = 28.dp)
            Column {
                Text(name, maxLines = 1)
                if (isOwner) Text("群主", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
            }
        }
    })
}

@Composable
private fun InfoRow(title: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)).padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title)
        if (value.isNotBlank()) Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupMemberPickerRoute(
    removing: Boolean,
    onBack: () -> Unit,
    viewModel: GroupInfoViewModel = hiltViewModel(),
    contactsViewModel: com.minipay.mobile.chat.ContactsViewModel = hiltViewModel()
) {
    val detail by viewModel.detail.collectAsState()
    val contacts by contactsViewModel.contacts.collectAsState()
    var selected by remember { mutableStateOf(setOf<String>()) }
    val members = detail?.members.orEmpty()
    val candidates = if (removing) members.filter { it.userId != detail?.ownerId } else {
        contacts.filter { contact -> members.none { it.userId == contact.id } }
            .map {
                GroupMemberResponse(
                    userId = it.id,
                    originalNickname = it.name,
                    displayName = it.name,
                    avatarUrl = it.avatarUrl,
                    avatarUrlExpiresAt = it.avatarUrlExpiresAt
                )
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (removing) "删除成员" else "选择朋友") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") } },
                actions = {
                    TextButton(
                        enabled = selected.isNotEmpty(),
                        onClick = {
                            if (removing) selected.forEach { viewModel.removeMember(it) {} }
                            else viewModel.addMembers(candidates.filter { it.userId in selected }.map {
                                GroupMemberInput(it.userId, it.originalNickname ?: it.displayName.orEmpty())
                            }) {}
                            onBack()
                        }
                    ) { Text("确定") }
                }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            items(candidates, key = { it.userId }) { member ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        selected = if (member.userId in selected) selected - member.userId else selected + member.userId
                    }.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(member.userId in selected, onCheckedChange = null)
                    Spacer(Modifier.padding(6.dp))
                    Text(member.displayName ?: member.nickname ?: "群成员")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupTransferMemberPickerRoute(
    onBack: () -> Unit,
    onSelect: (GroupMemberResponse) -> Unit,
    viewModel: GroupInfoViewModel = hiltViewModel()
) {
    val detail by viewModel.detail.collectAsState()
    val candidates = detail?.members.orEmpty().filter { it.userId != viewModel.currentUserId }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("选择收款群成员") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") } }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            items(candidates, key = { it.userId }) { member ->
                Row(
                    Modifier.fillMaxWidth().clickable { onSelect(member) }.padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val name = member.displayName ?: member.nickname ?: member.originalNickname ?: "群成员"
                    UserAvatar(name, member.avatarUrl, member.userId.hashCode(), size = 40.dp)
                    Spacer(Modifier.padding(6.dp))
                    Text(name)
                    if (member.userId == detail?.ownerId) {
                        Spacer(Modifier.padding(4.dp))
                        Text("群主", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
