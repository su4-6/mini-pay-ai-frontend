package com.minipay.mobile.ui.contacts

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.minipay.mobile.chat.FriendApiService
import com.minipay.mobile.chat.PublicCardResponse
import com.minipay.mobile.network.AutoRefreshEffect
import com.minipay.mobile.ui.theme.MilingBackground
import com.minipay.mobile.ui.theme.MilingPrimary
import com.minipay.mobile.ui.theme.MilingSurface
import com.minipay.mobile.ui.theme.MilingTextMuted
import com.minipay.mobile.ui.theme.MilingTextPrimary
import com.minipay.mobile.ui.components.UserAvatar
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun PublicFriendCardRoute(minipayNo: String, onBack: () -> Unit, friendApi: FriendApiService) {
    var card by remember { mutableStateOf<PublicCardResponse?>(null) }
    var failed by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    fun refresh() {
        if (loading) return
        loading = true
        failed = false
        scope.launch {
            card = runCatching { friendApi.resolveQrCard(minipayNo) }
                .getOrElse { failed = true; card }
            loading = false
        }
    }
    LaunchedEffect(minipayNo) { refresh() }
    AutoRefreshEffect(enabled = !loading, onRefresh = ::refresh)
    if (card == null) {
        Box(Modifier.fillMaxSize().background(MilingBackground), contentAlignment = Alignment.Center) {
            Text(if (failed) "未找到有效的 MiniPay 名片" else "正在读取名片…", color = MilingTextMuted)
        }
        return
    }
    PublicFriendCardScreen(card = card!!, onBack = onBack, onAddFriend = {
        scope.launch {
            val result = runCatching { friendApi.sendFriendRequest(card!!.userId) }
            Toast.makeText(context, if (result.isSuccess) "好友请求已发送" else "发送失败，请稍后重试", Toast.LENGTH_SHORT).show()
        }
    }, onDeveloping = { Toast.makeText(context, "正在开发该功能", Toast.LENGTH_SHORT).show() })
}

@Composable
private fun PublicFriendCardScreen(
    card: PublicCardResponse,
    onBack: () -> Unit,
    onAddFriend: () -> Unit,
    onDeveloping: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().background(MilingBackground)
            .statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        IconButton(onClick = onBack, modifier = Modifier.padding(12.dp).size(48.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
        }
        Card(Modifier.fillMaxWidth().padding(20.dp), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UserAvatar(
                        name = card.nickname,
                        avatarUrl = card.avatarUrl,
                        colorIndex = card.userId.hashCode(),
                        size = 72.dp
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(card.nickname, style = MaterialTheme.typography.headlineSmall, color = MilingTextPrimary)
                        Text("MiniPay 账号：${card.phoneMasked ?: card.minipayNo}", color = MilingTextMuted)
                    }
                }
                Spacer(Modifier.height(22.dp))
                CardRow("更多资料", onDeveloping)
            }
        }
        Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = RoundedCornerShape(20.dp)) { CardRow("动态", onDeveloping) }
        Card(Modifier.fillMaxWidth().padding(20.dp), shape = RoundedCornerShape(20.dp)) { CardRow("资金往来记录", onDeveloping) }
        Spacer(Modifier.height(16.dp))
        PublicFriendActions(card.friendStatus, onDeveloping, onAddFriend)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PublicFriendActions(friendStatus: String, onDeveloping: () -> Unit, onAddFriend: () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(onClick = onDeveloping, modifier = Modifier.defaultMinSize(minWidth = 92.dp)) { Text("发红包") }
        Button(onClick = onDeveloping, modifier = Modifier.defaultMinSize(minWidth = 92.dp)) { Text("去转账") }
        if (friendStatus == "NONE") {
            Button(onClick = onAddFriend, modifier = Modifier.defaultMinSize(minWidth = 92.dp)) { Text("加好友") }
        } else {
            Text(
                if (friendStatus == "ACCEPTED") "已是好友" else "请求处理中",
                modifier = Modifier.padding(14.dp),
                color = MilingTextMuted
            )
        }
    }
}

@Composable
private fun CardRow(title: String, onClick: () -> Unit) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MilingTextPrimary,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(22.dp))
}
