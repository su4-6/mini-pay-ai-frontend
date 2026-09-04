package com.minipay.mobile.ui.group

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minipay.mobile.chat.Contact
import com.minipay.mobile.chat.CreateGroupViewModel
import com.minipay.mobile.ui.components.UserAvatar
import com.minipay.mobile.ui.theme.MilingBackground
import com.minipay.mobile.ui.theme.MilingBorder
import com.minipay.mobile.ui.theme.MilingIconPrimary
import com.minipay.mobile.ui.theme.MilingPrimary
import com.minipay.mobile.ui.theme.MilingPrimarySoft
import com.minipay.mobile.ui.theme.MilingRadii
import com.minipay.mobile.ui.theme.MilingSpacing
import com.minipay.mobile.ui.theme.MilingSurface
import com.minipay.mobile.ui.theme.MilingSurfaceSubtle
import com.minipay.mobile.ui.theme.MilingTextMuted
import com.minipay.mobile.ui.theme.MilingTextPrimary
import com.minipay.mobile.ui.theme.MilingTextSecondary

@Composable
fun CreateGroupRoute(
    onBack: () -> Unit,
    onGroupCreated: (String, String) -> Unit,
    viewModel: CreateGroupViewModel = hiltViewModel()
) {
    val grouped by viewModel.groupedContacts.collectAsStateWithLifecycle()

    CreateGroupScreen(
        groupedContacts = grouped,
        onBack = onBack,
        onConfirm = { selectedIds ->
            viewModel.createGroup(
                memberIds = selectedIds,
                onCreated = { conversationId, name ->
                    onGroupCreated(conversationId, name)
                }
            )
        }
    )
}
@Composable
fun CreateGroupScreen(
    groupedContacts: Map<String, List<Contact>>,
    onBack: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedIds by rememberSaveable { mutableStateOf(setOf<String>()) }

    val displayedGroups = remember(searchQuery, groupedContacts) {
        if (searchQuery.isBlank()) {
            groupedContacts
        } else {
            groupedContacts
                .mapValues { (_, contacts) ->
                    contacts.filter { it.name.contains(searchQuery, ignoreCase = true) }
                }
                .filterValues { it.isNotEmpty() }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MilingBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        CreateGroupTopBar(
            selectedCount = selectedIds.size,
            onBack = onBack,
            onConfirm = { onConfirm(selectedIds.toList()) }
        )

        Spacer(Modifier.height(MilingSpacing.Lg))

        SearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            modifier = Modifier.padding(horizontal = MilingSpacing.Xl)
        )

        Spacer(Modifier.height(MilingSpacing.Lg))

        ContactList(
            groupedContacts = displayedGroups,
            selectedIds = selectedIds,
            onToggle = { contactId ->
                selectedIds = if (selectedIds.contains(contactId)) {
                    selectedIds - contactId
                } else {
                    selectedIds + contactId
                }
            }
        )
    }
}

@Composable
private fun CreateGroupTopBar(
    selectedCount: Int,
    onBack: () -> Unit,
    onConfirm: () -> Unit
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
                contentDescription = "返回",
                tint = MilingIconPrimary,
                modifier = Modifier.size(28.dp)
            )
        }

        Text(
            text = "选择朋友",
            style = MaterialTheme.typography.titleLarge,
            color = MilingTextPrimary,
            modifier = Modifier.semantics { heading() }
        )

        Spacer(Modifier.weight(1f))

        Surface(
            shape = RoundedCornerShape(MilingRadii.Medium),
            color = if (selectedCount > 0) MilingPrimary else MilingSurfaceSubtle,
            modifier = Modifier.clickable(
                enabled = selectedCount > 0,
                onClick = onConfirm
            )
        ) {
            Box(
                modifier = Modifier.padding(horizontal = MilingSpacing.Lg, vertical = MilingSpacing.Sm),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "确定",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selectedCount > 0) MilingBackground else MilingTextMuted
                )
            }
        }

        Spacer(Modifier.width(MilingSpacing.Sm))
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(CircleShape)
            .background(MilingSurface)
            .border(1.dp, MilingBorder, CircleShape)
            .padding(horizontal = MilingSpacing.Md)
            .clickable(onClick = { focusRequester.requestFocus() }),
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
                .focusRequester(focusRequester),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MilingTextPrimary),
            cursorBrush = SolidColor(MilingPrimary),
            singleLine = true,
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = "搜索",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MilingTextMuted
                        )
                    }
                    inner()
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactList(
    groupedContacts: Map<String, List<Contact>>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        groupedContacts.toSortedMap().forEach { (letter, contacts) ->
            stickyHeader(key = "header-$letter") {
                SectionHeader(letter = letter)
            }
            items(
                items = contacts,
                key = { it.id }
            ) { contact ->
                ContactRow(
                    contact = contact,
                    isSelected = selectedIds.contains(contact.id),
                    onToggle = { onToggle(contact.id) }
                )
            }
        }
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
            style = MaterialTheme.typography.bodyMedium,
            color = MilingTextSecondary
        )
    }
}

@Composable
private fun ContactRow(
    contact: Contact,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = MilingSpacing.Xl, vertical = MilingSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MilingSpacing.Md)
    ) {
        UserAvatar(
            name = contact.name,
            avatarUrl = contact.avatarUrl,
            colorIndex = contact.avatarColorIndex,
            size = 48.dp
        )

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = contact.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MilingTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = MilingPrimary,
                checkmarkColor = MilingBackground,
                uncheckedColor = MilingBorder
            )
        )
    }
}
