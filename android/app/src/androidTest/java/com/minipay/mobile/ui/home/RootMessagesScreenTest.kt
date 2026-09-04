package com.minipay.mobile.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.minipay.mobile.ui.theme.MilingTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RootMessagesScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun originalConversationPageKeepsItsControlsAboveSelectedBottomNavigation() {
        composeRule.setContent {
            MilingTheme {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().padding(bottom = 76.dp)) {
                        ConversationListScreen(conversations = emptyList())
                    }
                    RootBottomNavigation(
                        selected = RootTab.MESSAGES,
                        onSelect = {},
                        onOpenMiling = {},
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }

        composeRule.onAllNodesWithText("消息").assertCountEquals(2)
        composeRule.onNodeWithText("全部会话").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("返回主页").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("搜索好友").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("联系人").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("打开更多功能").assertIsDisplayed()
        composeRule.onNodeWithTag("home_bottom_navigation").assertIsDisplayed()
        composeRule.onNodeWithTag("home_tab_messages").assertIsSelected()
    }

    @Test
    fun paymentConversationTabDelegatesToOriginalConversationPage() {
        var openedConversations = false
        composeRule.setContent {
            MilingTheme {
                MessageCenterScreen(
                    state = PaymentMessagesUiState(),
                    onOpenConversations = { openedConversations = true },
                    onContacts = {},
                    onCreateGroup = {},
                    onAddFriend = {},
                    onScan = {},
                    onOpenBill = {},
                    onRetry = {},
                    onLoadMore = {}
                )
            }
        }

        composeRule.onNodeWithTag("message_tab_conversation").performClick()

        assertTrue(openedConversations)
    }
}
