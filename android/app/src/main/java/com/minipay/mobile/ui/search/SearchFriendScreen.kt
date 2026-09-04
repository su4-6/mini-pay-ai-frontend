package com.minipay.mobile.ui.search

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minipay.mobile.chat.FriendApiService
import com.minipay.mobile.chat.FriendResponse
import com.minipay.mobile.chat.SearchHit
import com.minipay.mobile.ui.components.UserAvatar
import com.minipay.mobile.ui.theme.MilingBackground
import com.minipay.mobile.ui.theme.MilingBorder
import com.minipay.mobile.ui.theme.MilingIconPrimary
import com.minipay.mobile.ui.theme.MilingIconSecondary
import com.minipay.mobile.ui.theme.MilingPrimary
import com.minipay.mobile.ui.theme.MilingPrimarySoft
import com.minipay.mobile.ui.theme.MilingRadii
import com.minipay.mobile.ui.theme.MilingSpacing
import com.minipay.mobile.ui.theme.MilingSurface
import com.minipay.mobile.ui.theme.MilingTextMuted
import com.minipay.mobile.ui.theme.MilingTextPrimary
import com.minipay.mobile.ui.theme.MilingTextSecondary
import kotlinx.coroutines.launch

private val avatarColors = listOf(
    Color(0xFFE57373), Color(0xFF64B5F6), Color(0xFF81C784),
    Color(0xFFFFB74D), Color(0xFFBA68C8), Color(0xFF4DB6AC),
    Color(0xFFF06292), Color(0xFF7986CB)
)

@Composable
fun SearchFriendRoute(
    onBack: () -> Unit,
    friendApi: FriendApiService,
    initialQuery: String = "",
    historyViewModel: SearchHistoryViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val history by historyViewModel.history.collectAsStateWithLifecycle()

    var searchResults by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var pendingRequestIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var friends by remember { mutableStateOf<List<FriendResponse>>(emptyList()) }

    LaunchedEffect(Unit) {
        friends = runCatching { friendApi.listFriends() }.getOrDefault(emptyList())
    }

    SearchFriendScreen(
        onBack = onBack,
        searchResults = searchResults,
        isSearching = isSearching,
        hasSearched = hasSearched,
        searchError = searchError,
        pendingRequestIds = pendingRequestIds,
        onSearch = { query ->
            scope.launch {
                isSearching = true
                hasSearched = true
                searchError = null
                try {
                searchResults = friendApi.searchUsers(query)
                friends = runCatching { friendApi.listFriends() }.getOrDefault(friends)
                } catch (e: Exception) {
                    searchResults = emptyList()
                    searchError = when {
                        e.message?.contains("NOT_AUTHENTICATED") == true -> "请先登录"
                        e.message?.contains("NETWORK") == true -> "网络连接失败"
                        else -> "搜索失败: ${e.message ?: "未知错误"}"
                    }
                }
                isSearching = false
            }
        },
        onAddFriend = { hit ->
            scope.launch {
                try {
                    friendApi.sendFriendRequest(hit.userId)
                    pendingRequestIds = pendingRequestIds + hit.userId
                    Toast.makeText(context, "好友请求已发送", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                    Toast.makeText(context, "发送失败，请重试", Toast.LENGTH_SHORT).show()
                }
            }
        },
        friendIds = friends.mapTo(mutableSetOf()) { it.userId },
        history = history,
        onRecordSearch = historyViewModel::record,
        onClearHistory = historyViewModel::clear,
        initialQuery = initialQuery
    )
}

@Composable
fun SearchFriendScreen(
    onBack: () -> Unit,
    searchResults: List<SearchHit>,
    isSearching: Boolean,
    hasSearched: Boolean,
    searchError: String?,
    pendingRequestIds: Set<String>,
    onSearch: (String) -> Unit,
    onAddFriend: (SearchHit) -> Unit,
    friendIds: Set<String> = emptySet(),
    history: List<String> = emptyList(),
    onRecordSearch: (String) -> Unit = {},
    onClearHistory: () -> Unit = {},
    initialQuery: String = ""
) {
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(initialQuery) {
        val normalized = initialQuery.trim()
        if (normalized.isNotEmpty()) {
            onRecordSearch(normalized)
            onSearch(normalized)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MilingBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        SearchFriendTopBar(
            query = query,
            onQueryChange = { query = it },
            focusRequester = focusRequester,
            onBack = onBack,
            onSearch = {
                val trimmed = query.trim()
                if (trimmed.isNotEmpty()) {
                    onRecordSearch(trimmed)
                    onSearch(trimmed)
                }
            }
        )

        Spacer(Modifier.height(MilingSpacing.Lg))

        if (isSearching) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MilingPrimary, modifier = Modifier.size(32.dp))
            }
        } else if (searchError != null) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(searchError, style = MaterialTheme.typography.bodyMedium, color = MilingPrimary)
            }
        } else if (hasSearched && searchResults.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("未找到相关用户", style = MaterialTheme.typography.bodyLarge, color = MilingTextMuted)
            }
        } else if (hasSearched && searchResults.isNotEmpty()) {
            SearchResultsSection(
                results = searchResults,
                pendingRequestIds = pendingRequestIds,
                friendIds = friendIds,
                onAddFriend = onAddFriend
            )
        } else {
            HistorySection(
                history = history,
                onClear = onClearHistory,
                onChipClick = { chip ->
                    query = chip
                    onRecordSearch(chip)
                    onSearch(chip)
                }
            )
        }
    }
}

@Composable
private fun SearchFriendTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onBack: () -> Unit,
    onSearch: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = MilingSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MilingSpacing.Sm)
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "返回",
                tint = MilingIconPrimary,
                modifier = Modifier.size(28.dp)
            )
        }

        SearchInput(
            query = query,
            onQueryChange = onQueryChange,
            focusRequester = focusRequester,
            onSearch = onSearch,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SearchInput(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(MilingSurface)
            .border(1.dp, MilingBorder, CircleShape)
            .padding(horizontal = MilingSpacing.Md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = MilingTextMuted,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(MilingSpacing.Sm))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .semantics { contentDescription = "搜索好友等" },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MilingTextPrimary),
            cursorBrush = SolidColor(MilingPrimary),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = "搜索好友等",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MilingTextMuted
                        )
                    }
                    inner()
                }
            }
        )
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
private fun HistorySection(
    history: List<String>,
    onClear: () -> Unit,
    onChipClick: (String) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = MilingSpacing.Xl)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "搜索历史", style = MaterialTheme.typography.titleMedium, color = MilingTextPrimary)
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = "清空搜索历史",
                tint = MilingIconSecondary,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClear)
            )
        }
        Spacer(Modifier.height(MilingSpacing.Md))
        if (history.isEmpty()) {
            Text(text = "暂无搜索历史", style = MaterialTheme.typography.bodyMedium, color = MilingTextMuted)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(MilingSpacing.Md)) {
                history.forEach { chip ->
                    HistoryChip(text = chip, onClick = { onChipClick(chip) })
                }
            }
        }
    }
}

@Composable
private fun HistoryChip(text: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(MilingRadii.Small),
        color = MilingSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MilingBorder),
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = MilingSpacing.Md, vertical = MilingSpacing.Sm),
            contentAlignment = Alignment.Center
        ) {
            Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MilingTextPrimary)
        }
    }
}

@Composable
private fun SearchResultsSection(
    results: List<SearchHit>,
    pendingRequestIds: Set<String>,
    friendIds: Set<String>,
    onAddFriend: (SearchHit) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = MilingSpacing.Xl)) {
        Text(text = "搜索结果", style = MaterialTheme.typography.titleMedium, color = MilingTextPrimary)
        Spacer(Modifier.height(MilingSpacing.Md))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            items(results, key = { it.userId }) { hit ->
                SearchResultItem(
                    hit = hit,
                    requestSent = pendingRequestIds.contains(hit.userId) || hit.friendStatus == "PENDING",
                    isFriend = friendIds.contains(hit.userId),
                    onAddFriend = { onAddFriend(hit) }
                )
            }
        }
    }
}

@Composable
private fun SearchResultItem(
    hit: SearchHit,
    requestSent: Boolean,
    isFriend: Boolean,
    onAddFriend: () -> Unit
) {
    if (isFriend) {
        FriendCard(hit)
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MilingSpacing.Md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(
            name = hit.nickname,
            avatarUrl = hit.avatarUrl,
            colorIndex = hit.userId.hashCode(),
            size = 48.dp
        )

        Spacer(Modifier.width(MilingSpacing.Lg))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = hit.nickname,
                style = MaterialTheme.typography.bodyLarge,
                color = MilingTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            val subtitle = when {
                !hit.phoneMasked.isNullOrBlank() -> "手机号: ${hit.phoneMasked}"
                else -> "米灵号: ${hit.minipayNo}"
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MilingTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (requestSent) {
            Text(
                text = "已发送",
                style = MaterialTheme.typography.labelMedium,
                color = MilingTextMuted
            )
        } else {
            TextButton(onClick = onAddFriend) {
                Icon(
                    imageVector = Icons.Outlined.PersonAdd,
                    contentDescription = null,
                    tint = MilingPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("添加", color = MilingPrimary, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun FriendCard(hit: SearchHit) {
    Surface(
        shape = RoundedCornerShape(MilingRadii.Large),
        color = MilingSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MilingBorder),
        modifier = Modifier.fillMaxWidth().padding(bottom = MilingSpacing.Md)
    ) {
        Column(Modifier.padding(MilingSpacing.Lg)) {
            Text("联系人", style = MaterialTheme.typography.titleMedium, color = MilingTextPrimary)
            Spacer(Modifier.height(MilingSpacing.Md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(
                    name = hit.nickname,
                    avatarUrl = hit.avatarUrl,
                    colorIndex = hit.userId.hashCode(),
                    size = 56.dp
                )
                Spacer(Modifier.width(MilingSpacing.Md))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(hit.nickname, style = MaterialTheme.typography.titleMedium, color = MilingTextPrimary)
                        Spacer(Modifier.width(8.dp))
                        Surface(shape = RoundedCornerShape(4.dp), border = androidx.compose.foundation.BorderStroke(1.dp, MilingBorder)) { Text("好友", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = MilingTextSecondary) }
                    }
                    Text(hit.phoneMasked?.let { "账号：$it" } ?: "MiniPay：${hit.minipayNo}", color = MilingPrimary, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(MilingSpacing.Md))
            TextButton(onClick = {}, modifier = Modifier.fillMaxWidth().background(MilingPrimarySoft, RoundedCornerShape(MilingRadii.Medium))) { Text("转账", color = MilingPrimary) }
        }
    }
}
