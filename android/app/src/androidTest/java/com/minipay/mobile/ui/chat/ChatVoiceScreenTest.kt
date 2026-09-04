package com.minipay.mobile.ui.chat

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import com.minipay.mobile.ui.theme.MilingTheme
import org.junit.Rule
import org.junit.Test

class ChatVoiceScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun voiceButtonSwitchesComposerToHoldToTalk() {
        composeRule.setContent { MilingTheme { ChatScreen("好友", emptyList(), "conv_1", {}, onSend = {}) } }
        composeRule.onNodeWithContentDescription("语音输入").performClick()
        composeRule.onNodeWithText("按住 说话").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("切换键盘").assertIsDisplayed()
    }

    @Test fun directChatShowsVoiceCallAction() {
        composeRule.setContent { MilingTheme { ChatScreen("好友", emptyList(), "conv_1", {}, onSend = {}) } }
        composeRule.onNodeWithContentDescription("打开更多功能").performClick()
        composeRule.onNodeWithText("语音通话").assertIsDisplayed()
    }

    @Test fun groupChatHidesVoiceCallAction() {
        var transfer = false
        composeRule.setContent {
            MilingTheme {
                ChatScreen(
                    "群聊",
                    emptyList(),
                    "group_1",
                    {},
                    onTransfer = { transfer = true },
                    onSend = {}
                )
            }
        }
        composeRule.onNodeWithContentDescription("打开更多功能").performClick()
        composeRule.onAllNodesWithText("语音通话").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("转账").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assert(transfer) }
        composeRule.onAllNodesWithContentDescription("与该好友的转账记录").assertCountEquals(0)
    }

    @Test fun directChatWalletAndTransferUseDedicatedCallbacks() {
        var records = false
        var transfer = false
        composeRule.setContent {
            MilingTheme {
                ChatScreen("好友", emptyList(), "conv_1", {},
                    onOpenTransferRecords = { records = true }, onTransfer = { transfer = true }, onSend = {})
            }
        }
        composeRule.onNodeWithContentDescription("与该好友的转账记录").performClick()
        composeRule.runOnIdle { assert(records) }
        composeRule.onNodeWithContentDescription("打开更多功能").performClick()
        composeRule.onNodeWithContentDescription("转账").performClick()
        composeRule.runOnIdle { assert(transfer) }
    }
}
