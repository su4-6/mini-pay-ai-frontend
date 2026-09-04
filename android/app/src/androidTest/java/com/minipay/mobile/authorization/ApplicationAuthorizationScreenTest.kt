package com.minipay.mobile.authorization

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import com.minipay.mobile.ui.theme.MilingTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ApplicationAuthorizationScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun authorizedFoodAccountUsesFlatRowWithoutLegacyAccountFields() {
        var selected: String? = null
        val application = authorization()
        composeRule.setContent {
            MilingTheme {
                AuthorizationAccountContent(
                    state = ApplicationAuthorizationState(
                        loading = false,
                        applications = listOf(application),
                        accountLabels = mapOf("yshop-food" to "m***f")
                    ),
                    onRetry = {},
                    onApplicationClick = { selected = it.applicationId }
                )
            }
        }

        composeRule.onNodeWithText("意向外卖").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("m***f").assertIsDisplayed()
        composeRule.onAllNodesWithText("手机号").assertCountEquals(0)
        composeRule.onAllNodesWithText("注销账号").assertCountEquals(0)
        composeRule.runOnIdle { assertEquals("yshop-food", selected) }
    }

    @Test
    fun unbindDialogMatchesRequiredCopyAndActions() {
        var dismissed = false
        var confirmed = false
        composeRule.setContent {
            MilingTheme {
                UnbindApplicationDialog(
                    revoking = false,
                    error = null,
                    onDismiss = { dismissed = true },
                    onConfirm = { confirmed = true }
                )
            }
        }

        composeRule.onNodeWithText("解除意向外卖绑定").assertIsDisplayed()
        composeRule.onNodeWithText("解绑后将无法继续通过 MiniPay 使用该意向外卖账号")
            .assertIsDisplayed()
        composeRule.onNodeWithText("解绑").performClick()
        composeRule.runOnIdle { assertEquals(true, confirmed) }
        composeRule.onNodeWithText("不同意").performClick()
        composeRule.runOnIdle { assertEquals(true, dismissed) }
    }

    private fun authorization() = ApplicationAuthorizationDto(
        authorizationId = "authorization-1",
        applicationId = "yshop-food",
        displayName = "意向点餐",
        developerName = "MiniPay 与意向点餐",
        consentVersion = 2,
        state = "ACTIVE",
        nickname = "MiniPay 用户"
    )
}
