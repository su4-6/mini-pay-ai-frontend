package com.minipay.mobile.ui.home

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.minipay.mobile.chat.Conversation
import com.minipay.mobile.ui.theme.MilingTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ConversationListScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun contactsReminderAndConversationUnreadBadgeAreIndependent() {
        composeRule.setContent {
            MilingTheme {
                ConversationListScreen(
                    conversations = listOf(conversation(unreadCount = 3)),
                    showContactsReminder = true
                )
            }
        }

        composeRule.onNodeWithTag("contacts_friend_request_reminder", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("3").assertIsDisplayed()
    }

    @Test
    fun contactsReminderIsHiddenWithoutPendingRequests() {
        composeRule.setContent {
            MilingTheme { ConversationListScreen(conversations = emptyList()) }
        }

        composeRule.onAllNodesWithTag("contacts_friend_request_reminder", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun outlinedPlusMenuExposesSharedScanAction() {
        var action: String? = null
        composeRule.setContent {
            MilingTheme {
                ConversationListScreen(
                    conversations = emptyList(),
                    onPlusAction = { action = it }
                )
            }
        }

        composeRule.onNodeWithTag("conversation_plus_icon", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("conversation_plus_button").performClick()
        composeRule.onNodeWithTag("plus_scan").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals("plus_scan", action) }
    }

    private fun conversation(unreadCount: Int) = Conversation(
        id = "conversation-1",
        name = "好友",
        lastMessage = "你好",
        time = "15:00",
        lastMessageTime = 1L,
        unreadCount = unreadCount,
        isTransfer = false,
        avatarColorIndex = 0
    )
}
