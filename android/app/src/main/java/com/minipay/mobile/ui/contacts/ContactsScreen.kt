package com.minipay.mobile.ui.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PersonPin
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minipay.mobile.chat.Contact
import com.minipay.mobile.chat.ContactsViewModel
import com.minipay.mobile.network.AutoRefreshEffect
import com.minipay.mobile.ui.components.UserAvatar
import com.minipay.mobile.ui.theme.MilingBackground
import com.minipay.mobile.ui.theme.MilingBorder
import com.minipay.mobile.ui.theme.MilingIconPrimary
import com.minipay.mobile.ui.theme.MilingIconSecondary
import com.minipay.mobile.ui.theme.MilingPrimary
import com.minipay.mobile.ui.theme.MilingRadii
import com.minipay.mobile.ui.theme.MilingSpacing
import com.minipay.mobile.ui.theme.MilingSurface
import com.minipay.mobile.ui.theme.MilingSurfaceSubtle
import com.minipay.mobile.ui.theme.MilingTextMuted
import com.minipay.mobile.ui.theme.MilingTextPrimary
import com.minipay.mobile.ui.theme.MilingTextSecondary
import kotlinx.coroutines.launch

@Composable
fun ContactsRoute(
    onBack: () -> Unit,
    onAddFriend: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onGroupsClick: () -> Unit = {},
    onFriendRequestsClick: () -> Unit = {},
    onRecentTransfersClick: () -> Unit = {},
    onContactClick: (Contact) -> Unit = {},
    viewModel: ContactsViewModel = hiltViewModel()
) {
    val grouped by viewModel.groupedContacts.collectAsStateWithLifecycle()
    val requests by viewModel.receivedRequests.collectAsStateWithLifecycle()
    AutoRefreshEffect(onRefresh = viewModel::refresh)

    ContactsScreen(
        groupedContacts = grouped,
        onBack = onBack,
        onAddFriend = onAddFriend,
        onSearchClick = onSearchClick,
        onGroupsClick = onGroupsClick,
        pendingRequestCount = requests.size,
        onFriendRequestsClick = onFriendRequestsClick,
        onRecentTransfersClick = onRecentTransfersClick,
        onContactClick = { contact ->
            viewModel.openConversation(contact.id, contact.name) { conversationId, name ->
                onContactClick(Contact(id = conversationId, name = name, firstLetter = contact.firstLetter, avatarColorIndex = contact.avatarColorIndex))
            }
        }
    )
}

@Composable
fun ContactsScreen(
    groupedContacts: Map<String, List<Contact>>,
    onBack: () -> Unit,
    onAddFriend: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onGroupsClick: () -> Unit = {},
    pendingRequestCount: Int = 0,
    onFriendRequestsClick: () -> Unit = {},
    onRecentTransfersClick: () -> Unit = {},
    onContactClick: (Contact) -> Unit = {}
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val indexLetters = remember { ('A'..'Z').map { it.toString() } + "#" }

    val displayedGroups = groupedContacts

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
            ContactsTopBar(
                onBack = onBack,
                onAddFriend = onAddFriend
            )

            Spacer(Modifier.height(MilingSpacing.Lg))

            SearchBar(
                onClick = onSearchClick,
                modifier = Modifier.padding(horizontal = MilingSpacing.Xl)
            )

            Spacer(Modifier.height(MilingSpacing.Lg))

            ContactList(
                groupedContacts = displayedGroups,
                onContactClick = onContactClick,
                onGroupsClick = onGroupsClick,
                pendingRequestCount = pendingRequestCount,
                onFriendRequestsClick = onFriendRequestsClick,
                onRecentTransfersClick = onRecentTransfersClick,
                listState = listState
            )
        }

        AlphabetIndex(
            letters = indexLetters,
            onLetterClick = { letter ->
                if (displayedGroups.containsKey(letter)) {
                    coroutineScope.launch {
                        val index = computeIndexForSection(displayedGroups, letter)
                        listState.animateScrollToItem(index)
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(vertical = MilingSpacing.Section)
        )
    }
}

@Composable
private fun ContactsTopBar(
    onBack: () -> Unit,
    onAddFriend: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = MilingSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "返回",
                tint = MilingIconPrimary,
                modifier = Modifier.size(28.dp)
            )
        }

        Text(
            text = "通讯录",
            style = MaterialTheme.typography.titleLarge,
            color = MilingTextPrimary,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() }
        )

        Text(
            text = "添加朋友",
            style = MaterialTheme.typography.labelLarge,
            color = MilingPrimary,
            modifier = Modifier
                .clickable(role = Role.Button) { onAddFriend() }
                .padding(horizontal = MilingSpacing.Md, vertical = MilingSpacing.Sm)
        )
    }
}

@Composable
private fun SearchBar(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactList(
    groupedContacts: Map<String, List<Contact>>,
    onContactClick: (Contact) -> Unit,
    onGroupsClick: () -> Unit,
    pendingRequestCount: Int,
    onFriendRequestsClick: () -> Unit,
    onRecentTransfersClick: () -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        item(key = "top-actions") {
            TopActionCard(onGroupsClick, pendingRequestCount, onFriendRequestsClick, onRecentTransfersClick)
            Spacer(Modifier.height(MilingSpacing.Lg))
        }

        groupedContacts.toSortedMap().forEach { (letter, contacts) ->
            stickyHeader(key = "header-$letter") {
                SectionHeader(letter = letter)
            }
            items(contacts.size, key = { index -> "${letter}-${contacts[index].id}-$index" }) { index ->
                ContactItem(
                    contact = contacts[index],
                    onClick = { onContactClick(contacts[index]) },
                    showDivider = index < contacts.lastIndex
                )
            }
        }
    }
}

@Composable
private fun TopActionCard(
    onGroupsClick: () -> Unit,
    pendingRequestCount: Int,
    onFriendRequestsClick: () -> Unit,
    onRecentTransfersClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(MilingRadii.Large),
        color = MilingSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MilingBorder),
        modifier = Modifier.padding(horizontal = MilingSpacing.Xl)
    ) {
        Column {
            TopActionRow(
                label = if (pendingRequestCount > 0) "新的朋友（$pendingRequestCount）" else "新的朋友",
                icon = Icons.Outlined.PersonAdd,
                onClick = onFriendRequestsClick
            )
            Spacer(
                modifier = Modifier.padding(start = 64.dp).fillMaxWidth().height(1.dp).background(MilingBorder)
            )
            TopActionRow(
                label = "群聊",
                icon = Icons.Outlined.Groups,
                onClick = onGroupsClick
            )
            Spacer(
                modifier = Modifier
                    .padding(start = 64.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MilingBorder)
            )
            TopActionRow(
                label = "最近转账联系人",
                icon = Icons.Outlined.PersonPin,
                onClick = onRecentTransfersClick
            )
        }
    }
}

@Composable
private fun TopActionRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = MilingSpacing.Lg, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MilingSpacing.Md)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MilingSurfaceSubtle),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MilingPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MilingTextPrimary,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MilingIconSecondary,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun SectionHeader(letter: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MilingBackground)
            .padding(horizontal = MilingSpacing.Xl, vertical = MilingSpacing.Sm)
    ) {
        Text(
            text = letter,
            style = MaterialTheme.typography.titleSmall,
            color = MilingTextMuted
        )
    }
}

@Composable
private fun ContactItem(
    contact: Contact,
    onClick: () -> Unit,
    showDivider: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MilingSurface)
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MilingSpacing.Xl, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MilingSpacing.Md)
        ) {
            UserAvatar(
                name = contact.name,
                colorIndex = contact.avatarColorIndex,
                avatarUrl = contact.avatarUrl
            )
            Text(
                text = contact.name,
                style = MaterialTheme.typography.titleMedium,
                color = MilingTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        if (showDivider) {
            Spacer(
                modifier = Modifier
                    .padding(start = 80.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MilingBorder)
            )
        }
    }
}

@Composable
private fun AlphabetIndex(
    letters: List<String>,
    onLetterClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(end = 4.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        letters.forEach { letter ->
            Text(
                text = letter,
                style = MaterialTheme.typography.labelSmall,
                color = MilingTextMuted,
                modifier = Modifier
                    .size(18.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onLetterClick(letter) }
                    )
                    .wrapContentSize(Alignment.Center)
            )
        }
    }
}

private fun computeIndexForSection(
    groupedContacts: Map<String, List<Contact>>,
    targetLetter: String
): Int {
    var index = 1 // account for top-actions item
    groupedContacts.toSortedMap().forEach { (letter, contacts) ->
        if (letter == targetLetter) return index
        index += 1 + contacts.size
    }
    return 0
}
