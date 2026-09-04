package com.minipay.mobile.ui.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minipay.mobile.chat.Conversation
import com.minipay.mobile.chat.ConversationListViewModel
import com.minipay.mobile.ui.components.UserAvatar
import com.minipay.mobile.ui.theme.MilingBackground
import com.minipay.mobile.ui.theme.MilingPrimary
import com.minipay.mobile.ui.theme.MilingRadii
import com.minipay.mobile.ui.theme.MilingSpacing
import com.minipay.mobile.ui.theme.MilingSurface
import com.minipay.mobile.ui.theme.MilingTextMuted
import com.minipay.mobile.ui.theme.MilingTextPrimary

@Composable
fun GroupConversationListRoute(
    onBack: () -> Unit,
    onOpenChat: (Conversation) -> Unit,
    viewModel: ConversationListViewModel = hiltViewModel()
) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }
    GroupConversationListScreen(
        groups = conversations.filter { it.id.startsWith("group_") },
        onBack = onBack,
        onOpenChat = onOpenChat
    )
}

@Composable
private fun GroupConversationListScreen(
    groups: List<Conversation>,
    onBack: () -> Unit,
    onOpenChat: (Conversation) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val displayed = groups.filter { it.name.contains(query.trim(), ignoreCase = true) }
    Column(
        Modifier
            .fillMaxSize()
            .background(MilingBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = MilingSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
            }
            Text("群聊", style = MaterialTheme.typography.titleLarge, color = MilingTextPrimary)
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            placeholder = { Text("搜索") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = MilingSpacing.Xl)
        )
        if (displayed.isEmpty()) {
            Text("暂无群聊", color = MilingTextMuted, modifier = Modifier.padding(32.dp))
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(top = MilingSpacing.Lg)) {
                items(displayed, key = { it.id }) { group ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenChat(group) }
                            .padding(horizontal = MilingSpacing.Xl, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UserAvatar(
                            name = group.name,
                            avatarUrl = group.avatarUrl,
                            colorIndex = group.avatarColorIndex,
                            size = 44.dp,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(MilingRadii.Medium)
                        )
                        Spacer(Modifier.width(MilingSpacing.Md))
                        Text(group.name, style = MaterialTheme.typography.titleMedium, color = MilingTextPrimary)
                    }
                }
            }
        }
    }
}
