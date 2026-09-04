package com.minipay.mobile.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import coil.compose.AsyncImage
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.minipay.mobile.chat.Conversation
import com.minipay.mobile.ui.components.UserAvatar
import com.minipay.mobile.ui.theme.MilingBackground
import com.minipay.mobile.ui.theme.MilingBorder
import com.minipay.mobile.ui.theme.MilingDivider
import com.minipay.mobile.ui.theme.MilingIconPrimary
import com.minipay.mobile.ui.theme.MilingPrimary
import com.minipay.mobile.ui.theme.MilingRadii
import com.minipay.mobile.ui.theme.MilingSpacing
import com.minipay.mobile.ui.theme.MilingSurface
import com.minipay.mobile.ui.theme.MilingTextMuted
import com.minipay.mobile.ui.theme.MilingTextPrimary
import com.minipay.mobile.ui.theme.MilingTextSecondary

private data class PlusMenuItem(
    val label: String,
    val icon: ImageVector,
    val testTag: String
)

private val plusMenuItems = listOf(
    PlusMenuItem("发起群聊", Icons.Outlined.Groups, "plus_create_group"),
    PlusMenuItem("添加朋友", Icons.Outlined.PersonAdd, "plus_add_friend"),
    PlusMenuItem("扫一扫", Icons.Outlined.CropFree, "plus_scan")
)

@Composable
fun ConversationListScreen(
    conversations: List<Conversation>,
    showContactsReminder: Boolean = false,
    onBack: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onConversationClick: (Conversation) -> Unit = {},
    onDeleteConversation: (String) -> Unit = {},
    onContactsClick: () -> Unit = {},
    onPlusAction: (String) -> Unit = {}
) {
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Conversation?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MilingBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            TopBar(
                menuExpanded = menuExpanded,
                showContactsReminder = showContactsReminder,
                onBack = onBack,
                onSearchClick = onSearchClick,
                onContactsClick = onContactsClick,
                onToggleMenu = { menuExpanded = !menuExpanded }
            )

            Spacer(Modifier.height(MilingSpacing.Xxl))

            Text(
                text = "全部会话",
                style = MaterialTheme.typography.titleMedium,
                color = MilingTextPrimary,
                modifier = Modifier
                    .padding(horizontal = MilingSpacing.Xl)
                    .semantics { heading() }
            )

            Spacer(Modifier.height(MilingSpacing.Md))

            ConversationList(
                conversations = conversations,
                onConversationClick = onConversationClick,
                onConversationLongClick = { deleteTarget = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = MilingSpacing.Xl)
            )
        }

        if (menuExpanded) {
            PlusMenuOverlay(
                onDismiss = { menuExpanded = false },
                onAction = { tag ->
                    menuExpanded = false
                    onPlusAction(tag)
                }
            )
        }
        deleteTarget?.let { conversation ->
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text("删除会话") },
                text = { Text("删除与“${conversation.name}”的会话？") },
                confirmButton = { TextButton(onClick = { onDeleteConversation(conversation.id); deleteTarget = null }) { Text("删除") } },
                dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } }
            )
        }
    }
}

@Composable
private fun TopBar(
    menuExpanded: Boolean,
    showContactsReminder: Boolean,
    onBack: () -> Unit,
    onSearchClick: () -> Unit,
    onContactsClick: () -> Unit,
    onToggleMenu: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = MilingSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MilingSpacing.Sm)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "返回主页",
                tint = MilingIconPrimary,
                modifier = Modifier.size(28.dp)
            )
        }

        Text(
            text = "消息",
            style = MaterialTheme.typography.titleLarge,
            color = MilingTextPrimary,
            modifier = Modifier.semantics { heading() }
        )

        SearchBar(
            onClick = onSearchClick,
            modifier = Modifier.weight(1f)
        )

        IconButton(
            onClick = onContactsClick,
            modifier = Modifier
                .size(48.dp)
                .semantics {
                    contentDescription = if (showContactsReminder) "联系人，有新的好友申请" else "联系人"
                }
        ) {
            Box {
                Icon(
                    imageVector = Icons.Outlined.Contacts,
                    contentDescription = null,
                    tint = MilingIconPrimary,
                    modifier = Modifier.size(27.dp)
                )
                if (showContactsReminder) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF3B30))
                            .border(1.dp, MilingSurface, CircleShape)
                            .testTag("contacts_friend_request_reminder")
                    )
                }
            }
        }

        IconButton(
            onClick = onToggleMenu,
            modifier = Modifier
                .size(48.dp)
                .testTag("conversation_plus_button")
                .semantics {
                    contentDescription = if (menuExpanded) "关闭更多功能" else "打开更多功能"
                }
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .testTag("conversation_plus_icon"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.AddCircleOutline,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
    }
}

@Composable
private fun SearchBar(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(MilingSurface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .border(1.dp, MilingBorder, CircleShape)
            .padding(horizontal = MilingSpacing.Md)
            .semantics { contentDescription = "搜索好友" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = MilingTextMuted,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(MilingSpacing.Sm))
        Text(
            text = "搜索好友",
            style = MaterialTheme.typography.bodyMedium,
            color = MilingTextMuted,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ConversationList(
    conversations: List<Conversation>,
    onConversationClick: (Conversation) -> Unit,
    onConversationLongClick: (Conversation) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState())
    ) {
        if (conversations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = MilingSpacing.Section),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无会话",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MilingTextMuted
                )
            }
        } else {
            Surface(
                shape = RoundedCornerShape(MilingRadii.Large),
                color = MilingSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MilingBorder)
            ) {
                Column {
                    conversations.forEachIndexed { index, conversation ->
                        ConversationItem(
                            conversation = conversation,
                            onClick = { onConversationClick(conversation) },
                            onLongClick = { onConversationLongClick(conversation) }
                        )
                        if (index < conversations.lastIndex) {
                            ConversationDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 76.dp)
            .height(1.dp)
            .background(MilingDivider)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationItem(
    conversation: Conversation,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = MilingSpacing.Lg, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(
            name = conversation.name,
            colorIndex = conversation.avatarColorIndex,
            avatarUrl = conversation.avatarUrl,
            size = 52.dp,
            shape = RoundedCornerShape(MilingRadii.Medium)
        )

        Spacer(Modifier.width(MilingSpacing.Md))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conversation.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MilingTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (conversation.time != null) {
                    Spacer(Modifier.width(MilingSpacing.Sm))
                    Text(
                        text = conversation.time,
                        style = MaterialTheme.typography.bodySmall,
                        color = MilingTextMuted,
                        maxLines = 1
                    )
                }
            }

            Spacer(Modifier.height(MilingSpacing.Xs))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conversation.lastMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (conversation.isTransfer) MilingPrimary else MilingTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (conversation.unreadCount > 0) {
                    Spacer(Modifier.width(MilingSpacing.Sm))
                    UnreadBadge(count = conversation.unreadCount)
                }
            }
        }
    }
}

@Composable
private fun UnreadBadge(count: Int) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(MilingPrimary),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White
        )
    }
}

@Composable
private fun PlusMenuOverlay(
    onDismiss: () -> Unit,
    onAction: (String) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onDismiss
            )
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 56.dp, end = MilingSpacing.Xl)
                .width(152.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(MilingRadii.Medium),
                color = MilingSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MilingBorder),
                shadowElevation = 2.dp
            ) {
                Column {
                    plusMenuItems.forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clickable(role = Role.Button) { onAction(item.testTag) }
                                .padding(horizontal = MilingSpacing.Md)
                                .testTag(item.testTag),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MilingSpacing.Sm)
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = MilingPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = MilingTextPrimary,
                                maxLines = 1
                            )
                        }
                        if (index < plusMenuItems.lastIndex) {
                            Spacer(
                                modifier = Modifier
                                    .padding(horizontal = MilingSpacing.Md)
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(MilingDivider)
                            )
                        }
                    }
                }
            }
        }
    }
}
