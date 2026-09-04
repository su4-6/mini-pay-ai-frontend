package com.minipay.mobile.ui.contacts

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.minipay.mobile.chat.Contact
import com.minipay.mobile.ui.theme.MilingTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ContactsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun allPrimaryActionsDispatchToTheirDedicatedCallbacks() {
        var back = false
        var addFriend = false
        var search = false
        var groups = false
        var friendRequests = false
        var recentTransfers = false
        var selectedContact: Contact? = null
        val alice = Contact("alice-id", "Alice", "A", 0)

        composeRule.setContent {
            MilingTheme {
                ContactsScreen(
                    groupedContacts = mapOf("A" to listOf(alice)),
                    onBack = { back = true },
                    onAddFriend = { addFriend = true },
                    onSearchClick = { search = true },
                    onGroupsClick = { groups = true },
                    pendingRequestCount = 2,
                    onFriendRequestsClick = { friendRequests = true },
                    onRecentTransfersClick = { recentTransfers = true },
                    onContactClick = { selectedContact = it }
                )
            }
        }

        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.onNodeWithText("添加朋友").performClick()
        composeRule.onNodeWithContentDescription("搜索好友").performClick()
        composeRule.onNodeWithText("新的朋友（2）").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("群聊").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("最近转账联系人").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Alice").assertIsDisplayed().performClick()

        composeRule.runOnIdle {
            assertTrue(back)
            assertTrue(addFriend)
            assertTrue(search)
            assertTrue(groups)
            assertTrue(friendRequests)
            assertTrue(recentTransfers)
            assertEquals(alice, selectedContact)
        }
    }
}
