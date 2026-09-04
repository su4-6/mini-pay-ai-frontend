package com.minipay.mobile.ui.auth

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.minipay.mobile.auth.AuthUiState
import com.minipay.mobile.auth.CodeDeliveryStatus
import com.minipay.mobile.ui.theme.MilingTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AuthScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loginScreenShowsBrandLogoFixedCountryCodeAndAgreementControls() {
        var mobile = ""
        var agreementClicks = 0
        composeRule.setContent {
            MilingTheme {
                LoginScreen(
                    state = AuthUiState.PhoneEntry(mobile = mobile),
                    onMobileChange = { mobile = it },
                    onClearMobile = { mobile = "" },
                    onToggleAgreement = { agreementClicks += 1 },
                    onSendCode = {},
                    onOpenUserAgreement = {},
                    onOpenPrivacyPolicy = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("Mini Pay 品牌标志").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("收起键盘").assertDoesNotExist()
        composeRule.onNodeWithText("Mini Pay").assertIsDisplayed()
        composeRule.onNodeWithText("你的智能支付与生活助手").assertIsDisplayed()
        composeRule.onNodeWithText("+86").assertIsDisplayed()
        composeRule.onNodeWithTag("mobile-field").assertIsDisplayed()
        composeRule.onNodeWithTag("agreement-checkbox").performClick()
        assertEquals(1, agreementClicks)
    }

    @Test
    fun keyboardLayoutKeepsCompactLogoAgreementAndPhoneInputUsable() {
        var mobile = ""
        var dismissClicks = 0
        composeRule.setContent {
            MilingTheme {
                LoginScreenContent(
                    state = AuthUiState.PhoneEntry(mobile = mobile),
                    keyboardVisible = true,
                    onDismissKeyboard = { dismissClicks += 1 },
                    onMobileChange = { mobile = it },
                    onClearMobile = { mobile = "" },
                    onToggleAgreement = {},
                    onSendCode = {},
                    onOpenUserAgreement = {},
                    onOpenPrivacyPolicy = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("Mini Pay 品牌标志").assertIsDisplayed()
        composeRule.onNodeWithText("Mini Pay").assertDoesNotExist()
        composeRule.onNodeWithText("你的智能支付与生活助手").assertDoesNotExist()
        composeRule.onNodeWithTag("agreement-checkbox").assertIsDisplayed()
        composeRule.onNodeWithTag("mobile-input").performTextInput("13800138000")
        assertEquals("13800138000", mobile)

        val logoBounds = composeRule.onNodeWithTag("login-logo").getUnclippedBoundsInRoot()
        val dismissButtonBounds = composeRule.onNodeWithTag("dismiss-keyboard-button")
            .getUnclippedBoundsInRoot()
        assertEquals(logoBounds.top.value, dismissButtonBounds.top.value, 0.5f)
        assertEquals(logoBounds.bottom.value, dismissButtonBounds.bottom.value, 0.5f)
        composeRule.onNodeWithTag("dismiss-keyboard-button").performClick()
        assertEquals(1, dismissClicks)
    }

    @Test
    fun keyboardTransitionMovesAndShrinksTheSameLogo() {
        val keyboardVisible = mutableStateOf(false)
        composeRule.setContent {
            MilingTheme {
                LoginScreenContent(
                    state = AuthUiState.PhoneEntry(),
                    keyboardVisible = keyboardVisible.value,
                    onDismissKeyboard = {},
                    onMobileChange = {},
                    onClearMobile = {},
                    onToggleAgreement = {},
                    onSendCode = {},
                    onOpenUserAgreement = {},
                    onOpenPrivacyPolicy = {}
                )
            }
        }

        val initialBounds = composeRule.onNodeWithTag("login-logo").getUnclippedBoundsInRoot()
        val initialWidth = initialBounds.right.value - initialBounds.left.value
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread { keyboardVisible.value = true }
        composeRule.mainClock.advanceTimeBy(110)
        composeRule.waitForIdle()

        val middleBounds = composeRule.onNodeWithTag("login-logo").getUnclippedBoundsInRoot()
        val middleWidth = middleBounds.right.value - middleBounds.left.value
        assertTrue(middleWidth < initialWidth)
        assertTrue(middleWidth > 48f)
        assertTrue(middleBounds.top.value < initialBounds.top.value)

        composeRule.mainClock.advanceTimeBy(220)
        composeRule.waitForIdle()
        val finalBounds = composeRule.onNodeWithTag("login-logo").getUnclippedBoundsInRoot()
        val dismissButtonBounds = composeRule.onNodeWithTag("dismiss-keyboard-button")
            .getUnclippedBoundsInRoot()
        assertEquals(48f, finalBounds.right.value - finalBounds.left.value, 0.5f)
        assertEquals(dismissButtonBounds.top.value, finalBounds.top.value, 0.5f)
    }

    @Test
    fun verificationScreenExposesSixDigitFieldAndBackAction() {
        var code = ""
        var backClicks = 0
        composeRule.setContent {
            MilingTheme {
                VerificationCodeScreen(
                    state = AuthUiState.CodeEntry(
                        mobile = "13800138000",
                        maskedMobile = "138****8000",
                        challengeId = "challenge",
                        code = code,
                        secondsUntilResend = 57
                    ),
                    onCodeChange = { code = it },
                    onResend = {},
                    onBack = { backClicks += 1 }
                )
            }
        }

        composeRule.onNodeWithText("输入验证码").assertIsDisplayed()
        composeRule.onNodeWithText("57 秒后重新发送").assertIsDisplayed()
        composeRule.onNodeWithTag("otp-field").performTextInput("123456")
        composeRule.onNodeWithTag("verification-back").performClick()
        assertEquals("123456", code)
        assertEquals(1, backClicks)
    }

    @Test
    fun verificationScreenShowsSendingAndFailureDeliveryStates() {
        val state = mutableStateOf(
            AuthUiState.CodeEntry(
                mobile = "13800138000",
                maskedMobile = "138****8000",
                challengeId = null,
                deliveryStatus = CodeDeliveryStatus.SENDING,
                secondsUntilResend = 0
            )
        )
        composeRule.setContent {
            MilingTheme {
                VerificationCodeScreen(
                    state = state.value,
                    onCodeChange = {},
                    onResend = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithText("正在向 +86 138****8000 发送验证码").assertIsDisplayed()
        composeRule.onNodeWithText("验证码发送中，请稍候").assertIsDisplayed()

        composeRule.runOnUiThread {
            state.value = state.value.copy(
                deliveryStatus = CodeDeliveryStatus.FAILED,
                errorMessage = "网络连接不可用，请稍后重试"
            )
        }

        composeRule.onNodeWithText("网络连接不可用，请稍后重试").assertIsDisplayed()
        composeRule.onNodeWithText("重新发送").assertIsDisplayed()
    }
}
