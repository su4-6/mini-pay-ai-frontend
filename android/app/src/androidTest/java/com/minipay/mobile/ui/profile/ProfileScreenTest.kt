package com.minipay.mobile.ui.profile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.minipay.mobile.profile.ProfileLoadState
import com.minipay.mobile.profile.UserProfile
import com.minipay.mobile.ui.theme.MilingTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProfileScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun readyProfileShowsProductInformationArchitecture() {
        composeRule.setContent {
            MilingTheme {
                ProfileScreen(
                    state = ProfileLoadState.Ready(
                        UserProfile("user", "小满", "MP001", version = 1)
                    ),
                    onBack = {}, onEdit = {}, onOpenFeature = {}, onLogout = {}, onRetry = {}
                )
            }
        }

        composeRule.onNodeWithText("小满").assertIsDisplayed()
        composeRule.onNodeWithText("MiniPay 号 MP001").assertIsDisplayed()
        composeRule.onNodeWithText("钱包").assertIsDisplayed()
        composeRule.onNodeWithText("订单").assertIsDisplayed()
        composeRule.onNodeWithText("记忆").assertIsDisplayed()
        composeRule.onNodeWithText("银行卡").assertDoesNotExist()
    }

    @Test
    fun personalCardOpensProfileEditor() {
        var opened = false
        composeRule.setContent {
            MilingTheme {
                ProfileScreen(
                    state = ProfileLoadState.Ready(UserProfile("user", "小满", "MP001", version = 1)),
                    onBack = {}, onEdit = { opened = true }, onOpenFeature = {}, onLogout = {}, onRetry = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("编辑个人信息").performClick()
        composeRule.runOnIdle { assertTrue(opened) }
    }
}
