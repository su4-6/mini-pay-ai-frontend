package com.minipay.mobile.ui.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.minipay.mobile.ui.theme.MilingTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatConversationNameScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun chatHeaderDisplaysResolvedConversationName() {
        composeRule.setContent {
            MilingTheme {
                ChatScreen(
                    conversationName = "路博宇",
                    messages = emptyList(),
                    onBack = {},
                    onSend = {}
                )
            }
        }

        composeRule.onNodeWithText("路博宇").assertIsDisplayed()
    }

    @Test
    fun remarkCanBeClearedToRestoreFriendNickname() {
        var savedRemark: String? = null
        composeRule.setContent {
            MilingTheme {
                RemarkSettingsScreen(
                    friendName = "同事小林",
                    onBack = {},
                    onSave = { savedRemark = it }
                )
            }
        }

        composeRule.onNodeWithText("同事小林").performTextClearance()
        composeRule.onNodeWithText("完成").performClick()

        composeRule.runOnIdle { assertEquals("", savedRemark) }
    }
}
