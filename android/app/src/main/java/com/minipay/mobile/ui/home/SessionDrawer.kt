package com.minipay.mobile.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.minipay.mobile.ui.components.AvatarImage
import com.minipay.mobile.ui.theme.MilingBorder
import com.minipay.mobile.ui.theme.MilingIconPrimary
import com.minipay.mobile.ui.theme.MilingIconSecondary
import com.minipay.mobile.ui.theme.MilingPrimary
import com.minipay.mobile.ui.theme.MilingRadii
import com.minipay.mobile.ui.theme.MilingSpacing
import com.minipay.mobile.ui.theme.MilingSurface
import com.minipay.mobile.ui.theme.MilingSurfaceBlue
import com.minipay.mobile.ui.theme.MilingSurfaceSubtle
import com.minipay.mobile.ui.theme.MilingTextMuted
import com.minipay.mobile.ui.theme.MilingTextPrimary
import com.minipay.mobile.ui.theme.MilingTextSecondary
import com.minipay.mobile.ai.AiConversationRunState
import kotlinx.coroutines.launch

internal data class DrawerConversation(
    val id: String,
    val title: String,
    val version: Long = 0,
    val runState: AiConversationRunState? = null
)

internal data class DrawerConversationGroup(
    val label: String,
    val conversations: List<DrawerConversation>
)

internal val sampleConversationGroups = listOf(
    DrawerConversationGroup(
        label = "最近一周",
        conversations = listOf(
            DrawerConversation(id = "milk-tea", title = "米灵帮我点奶茶")
        )
    )
)

@Composable
internal fun SessionDrawer(
    drawerState: DrawerState,
    groups: List<DrawerConversationGroup>,
    selectedConversationId: String?,
    isSearching: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onEnterSearch: () -> Unit,
    onExitSearch: () -> Unit,
    onStartNewConversation: () -> Unit,
    onSelectConversation: (DrawerConversation) -> Unit,
    onRenameConversation: (DrawerConversation, String) -> Unit,
    onDeleteConversation: (DrawerConversation) -> Unit,
    onOpenProfile: () -> Unit,
    profileName: String,
    avatarUrl: String?,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var renameTarget by remember { mutableStateOf<DrawerConversation?>(null) }
    var deleteTarget by remember { mutableStateOf<DrawerConversation?>(null) }
    var renameTitle by remember { mutableStateOf("") }

    BackHandler(enabled = drawerState.isOpen) {
        if (isSearching) {
            onExitSearch()
        } else {
            coroutineScope.launch { drawerState.close() }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth(0.84f)
            .fillMaxHeight()
            .shadow(12.dp, RectangleShape)
            .testTag("session_drawer"),
        shape = RectangleShape,
        color = MilingSurface,
        tonalElevation = 0.dp
    ) {
        if (isSearching) {
            DrawerSearchPage(
                groups = groups,
                selectedConversationId = selectedConversationId,
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                onBack = onExitSearch,
                onSelectConversation = onSelectConversation,
                onRenameConversation = { renameTarget = it; renameTitle = it.title },
                onDeleteConversation = { deleteTarget = it }
            )
        } else {
            DrawerOverview(
                groups = groups,
                selectedConversationId = selectedConversationId,
                onEnterSearch = onEnterSearch,
                onStartNewConversation = onStartNewConversation,
                onSelectConversation = onSelectConversation,
                onRenameConversation = { renameTarget = it; renameTitle = it.title },
                onDeleteConversation = { deleteTarget = it },
                onOpenProfile = onOpenProfile,
                profileName = profileName,
                avatarUrl = avatarUrl
            )
        }
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名会话") },
            text = {
                OutlinedTextField(
                    value = renameTitle,
                    onValueChange = { renameTitle = it.take(128) },
                    label = { Text("会话名称") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    enabled = renameTitle.isNotBlank(),
                    onClick = {
                        onRenameConversation(target, renameTitle)
                        renameTarget = null
                    }
                ) { Text("保存") }
            },
            dismissButton = { OutlinedButton(onClick = { renameTarget = null }) { Text("取消") } }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除会话？") },
            text = { Text("删除后，该会话将不再显示。此操作不会执行任何交易。") },
            confirmButton = {
                Button(onClick = {
                    onDeleteConversation(target)
                    deleteTarget = null
                }) { Text("删除") }
            },
            dismissButton = { OutlinedButton(onClick = { deleteTarget = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun DrawerOverview(
    groups: List<DrawerConversationGroup>,
    selectedConversationId: String?,
    onEnterSearch: () -> Unit,
    onStartNewConversation: () -> Unit,
    onSelectConversation: (DrawerConversation) -> Unit,
    onRenameConversation: (DrawerConversation) -> Unit,
    onDeleteConversation: (DrawerConversation) -> Unit,
    onOpenProfile: () -> Unit,
    profileName: String,
    avatarUrl: String?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MilingSurface)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        DrawerHeader(onSearch = onEnterSearch)
        NewConversationButton(onClick = onStartNewConversation)
        Spacer(Modifier.height(MilingSpacing.Section))
        ConversationGroups(
            groups = groups,
            selectedConversationId = selectedConversationId,
            onSelectConversation = onSelectConversation,
            onRenameConversation = onRenameConversation,
            onDeleteConversation = onDeleteConversation,
            modifier = Modifier.weight(1f)
        )
        ProfileEntry(onClick = onOpenProfile, profileName = profileName, avatarUrl = avatarUrl)
    }
}

@Composable
private fun DrawerHeader(onSearch: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .padding(start = MilingSpacing.Xxl, end = MilingSpacing.Lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "米灵",
            color = MilingTextPrimary,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() }
        )
        IconButton(
            onClick = onSearch,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(MilingRadii.Medium))
                .border(1.dp, MilingBorder, RoundedCornerShape(MilingRadii.Medium))
                .testTag("drawer_search_button")
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = "搜索会话",
                tint = MilingIconPrimary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun NewConversationButton(onClick: () -> Unit) {
    val shape = RoundedCornerShape(MilingRadii.Medium)
    Row(
        modifier = Modifier
            .padding(horizontal = MilingSpacing.Lg)
            .fillMaxWidth()
            .height(60.dp)
            .clip(shape)
            .border(1.dp, MilingBorder, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = MilingSpacing.Xl)
            .testTag("drawer_new_conversation"),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.AddComment,
            contentDescription = null,
            tint = MilingPrimary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(MilingSpacing.Md))
        Text(
            text = "新建对话",
            color = MilingTextPrimary,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun ConversationGroups(
    groups: List<DrawerConversationGroup>,
    selectedConversationId: String?,
    onSelectConversation: (DrawerConversation) -> Unit,
    onRenameConversation: (DrawerConversation) -> Unit,
    onDeleteConversation: (DrawerConversation) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = MilingSpacing.Md,
            end = MilingSpacing.Md,
            bottom = MilingSpacing.Lg
        )
    ) {
        groups.filter { it.conversations.isNotEmpty() }.forEach { group ->
            item(key = "header-${group.label}") {
                Text(
                    text = group.label,
                    color = MilingTextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(
                        start = MilingSpacing.Sm,
                        end = MilingSpacing.Sm,
                        top = MilingSpacing.Sm,
                        bottom = MilingSpacing.Md
                    )
                )
            }
            items(group.conversations, key = DrawerConversation::id) { conversation ->
                ConversationEntry(
                    conversation = conversation,
                    selected = conversation.id == selectedConversationId,
                    onClick = { onSelectConversation(conversation) },
                    onRename = { onRenameConversation(conversation) },
                    onDelete = { onDeleteConversation(conversation) }
                )
                Spacer(Modifier.height(MilingSpacing.Sm))
            }
            item(key = "spacer-${group.label}") {
                Spacer(Modifier.height(MilingSpacing.Lg))
            }
        }
    }
}

@Composable
private fun ConversationEntry(
    conversation: DrawerConversation,
    selected: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val shape = RoundedCornerShape(MilingRadii.Large)
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clip(shape)
            .background(if (selected) MilingSurfaceBlue else MilingSurface)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = MilingSpacing.Lg, vertical = MilingSpacing.Md)
            .testTag("drawer_conversation_${conversation.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = conversation.title,
                color = MilingTextPrimary,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            conversation.runState?.let { state ->
                Text(
                    text = when (state) {
                        AiConversationRunState.RUNNING -> "生成中"
                        AiConversationRunState.COMPLETED -> "已完成"
                        AiConversationRunState.FAILED -> "生成失败"
                    },
                    color = if (state == AiConversationRunState.FAILED) MaterialTheme.colorScheme.error
                    else MilingTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("drawer_conversation_state_${conversation.id}")
                )
            }
        }
        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(48.dp).testTag("drawer_conversation_menu_${conversation.id}")
            ) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "会话操作", tint = MilingIconSecondary)
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("重命名") },
                    leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                    onClick = { menuExpanded = false; onRename() }
                )
                DropdownMenuItem(
                    text = { Text("删除") },
                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                    enabled = conversation.runState != AiConversationRunState.RUNNING,
                    onClick = { menuExpanded = false; onDelete() }
                )
            }
        }
    }
}

@Composable
private fun ProfileEntry(onClick: () -> Unit, profileName: String, avatarUrl: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 88.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = MilingSpacing.Xl, vertical = MilingSpacing.Sm)
            .testTag("drawer_profile"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MilingSurfaceBlue),
            contentAlignment = Alignment.Center
        ) {
            if (avatarUrl != null) {
            AvatarImage(
                avatarUrl = avatarUrl,
                contentDescription = "$profileName 的头像",
                modifier = Modifier.fillMaxSize()
                )
            } else {
                MilingMascot(size = 44.dp)
            }
        }
        Spacer(Modifier.width(MilingSpacing.Lg))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = profileName,
                color = MilingTextPrimary,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "个人中心",
                color = MilingTextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MilingIconSecondary,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun DrawerSearchPage(
    groups: List<DrawerConversationGroup>,
    selectedConversationId: String?,
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onSelectConversation: (DrawerConversation) -> Unit,
    onRenameConversation: (DrawerConversation) -> Unit,
    onDeleteConversation: (DrawerConversation) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val filteredGroups = remember(groups, query) {
        val normalized = query.trim()
        if (normalized.isEmpty()) {
            groups
        } else {
            groups.map { group ->
                group.copy(
                    conversations = group.conversations.filter {
                        it.title.contains(normalized, ignoreCase = true)
                    }
                )
            }.filter { it.conversations.isNotEmpty() }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MilingSurface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("drawer_search_page")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .padding(horizontal = MilingSpacing.Md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    keyboardController?.hide()
                    onBack()
                },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("drawer_search_back")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回会话列表",
                    tint = MilingIconPrimary
                )
            }
            SearchField(
                query = query,
                onQueryChange = onQueryChange,
                onClear = { onQueryChange("") },
                focusRequester = focusRequester,
                modifier = Modifier.weight(1f)
            )
        }

        if (filteredGroups.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("drawer_search_empty"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "未找到相关对话",
                    color = MilingTextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            ConversationGroups(
                groups = filteredGroups,
                selectedConversationId = selectedConversationId,
                onSelectConversation = onSelectConversation,
                onRenameConversation = onRenameConversation,
                onDeleteConversation = onDeleteConversation,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(MilingRadii.Medium)
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .height(52.dp)
            .clip(shape)
            .background(MilingSurfaceSubtle)
            .border(1.dp, MilingBorder, shape)
            .focusRequester(focusRequester)
            .testTag("drawer_search_input"),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MilingTextPrimary),
        cursorBrush = SolidColor(MilingPrimary),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {}),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = MilingSpacing.Lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = MilingIconSecondary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(MilingSpacing.Sm))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = "搜索会话",
                            color = MilingTextMuted,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    innerTextField()
                }
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("drawer_search_clear")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Clear,
                            contentDescription = "清除搜索内容",
                            tint = MilingIconSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    Spacer(Modifier.width(MilingSpacing.Md))
                }
            }
        }
    )
}
