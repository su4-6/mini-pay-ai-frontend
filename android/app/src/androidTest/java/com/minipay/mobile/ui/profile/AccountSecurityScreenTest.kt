package com.minipay.mobile.ui.profile

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.minipay.mobile.profile.account.AccountPage
import com.minipay.mobile.profile.account.AccountSecurityOverview
import com.minipay.mobile.profile.account.AccountSecurityUiState
import com.minipay.mobile.ui.theme.MilingTheme
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AccountSecurityScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun overviewShowsOnlyServerProvidedSecurityState() {
        setScreen(
            AccountSecurityUiState(
                loading = false,
                overview = AccountSecurityOverview("138****8000", null, false)
            )
        )

        composeRule.onNodeWithText("138****8000").assertIsDisplayed()
        composeRule.onNodeWithText("未绑定").assertIsDisplayed()
        composeRule.onNodeWithText("未设置").assertIsDisplayed()
        composeRule.onNodeWithText("已绑定").assertDoesNotExist()
    }

    @Test
    fun deletingEmailRequiresExplicitConfirmation() {
        var deleted = false
        setScreen(
            AccountSecurityUiState(
                page = AccountPage.EmailCurrent,
                loading = false,
                overview = AccountSecurityOverview("138****8000", "u***@example.com", true)
            ),
            onDeleteEmail = { deleted = true }
        )

        composeRule.onNodeWithText("删除邮箱").performClick()
        composeRule.onNodeWithText("确认删除邮箱？").assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(!deleted) }
        composeRule.onNodeWithText("确认删除").performClick()
        composeRule.runOnIdle { assertTrue(deleted) }
    }

    @Test
    fun paymentPasswordLifecycleEnablesAndRestoresSecureWindow() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val window = composeRule.activity.window
            val wasSecure = applySecureWindow(window, enabled = true)
            assertTrue(
                window.attributes.flags
                    .and(WindowManager.LayoutParams.FLAG_SECURE) != 0
            )
            restoreSecureWindow(window, enabled = true, wasSecure = wasSecure)
            assertEquals(
                0,
                window.attributes.flags
                    .and(WindowManager.LayoutParams.FLAG_SECURE)
            )
        }
    }

    private fun setScreen(
        state: AccountSecurityUiState,
        onDeleteEmail: () -> Unit = {}
    ) {
        composeRule.setContent {
            MilingTheme {
                AccountSecurityScreen(
                    state = state,
                    onBack = {},
                    onRetry = {},
                    onOpenPhone = {},
                    onOpenEmail = {},
                    onOpenEmailInput = {},
                    onOpenPayment = {},
                    onTargetChange = {},
                    onCodeChange = {},
                    onSendPhone = {},
                    onConfirmPhone = {},
                    onSendEmail = {},
                    onConfirmEmail = {},
                    onDeleteEmail = onDeleteEmail,
                    onConfirmPaymentCode = {},
                    onChangePaymentPassword = { _, _ -> },
                    onResend = {},
                    onDone = {}
                )
            }
        }
    }
}
