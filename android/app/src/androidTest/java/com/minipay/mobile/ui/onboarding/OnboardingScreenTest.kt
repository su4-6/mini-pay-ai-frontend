package com.minipay.mobile.ui.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.minipay.mobile.onboarding.OnboardingStep
import com.minipay.mobile.onboarding.OnboardingUiState
import com.minipay.mobile.ui.theme.MilingTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OnboardingScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun profileStepShowsOptionalAvatarAndValidatedNickname() {
        var continueClicks = 0
        composeRule.setContent {
            MilingTheme {
                OnboardingScreen(
                    state = OnboardingUiState(nickname = "小满"),
                    onNicknameChange = {},
                    onChooseAvatar = {},
                    onRemoveAvatar = {},
                    onBack = {},
                    onSubmit = { continueClicks += 1 },
                    onCompleted = {}
                )
            }
        }

        composeRule.onNodeWithText("头像可以稍后添加", substring = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("选择头像").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_submit").assertIsEnabled().performClick()
        assertEquals(1, continueClicks)
    }

    @Test
    fun profileStepDisplaysNearbySubmissionError() {
        composeRule.setContent {
            MilingTheme {
                OnboardingScreen(
                    state = OnboardingUiState(
                        nickname = "小满",
                        errorMessage = "资料提交失败，请重试"
                    ),
                    onNicknameChange = {},
                    onChooseAvatar = {},
                    onRemoveAvatar = {},
                    onBack = {},
                    onSubmit = {},
                    onCompleted = {}
                )
            }
        }

        composeRule.onNodeWithText("资料提交失败，请重试").assertIsDisplayed()
    }

    @Test
    fun completionStepAppearsOnlyForCompleteState() {
        composeRule.setContent {
            MilingTheme {
                OnboardingScreen(
                    state = OnboardingUiState(step = OnboardingStep.COMPLETE, nickname = "小满"),
                    onNicknameChange = {},
                    onChooseAvatar = {},
                    onRemoveAvatar = {},
                    onBack = {},
                    onSubmit = {},
                    onCompleted = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("初始化成功").assertIsDisplayed()
        composeRule.onNodeWithText("欢迎你，小满").assertIsDisplayed()
        composeRule.onNodeWithText("开始使用").assertIsDisplayed()
    }

    @Test
    fun submittingProfileDisablesExitAndSubmitActions() {
        composeRule.setContent {
            MilingTheme {
                OnboardingScreen(
                    state = OnboardingUiState(nickname = "小满", submitting = true),
                    onNicknameChange = {},
                    onChooseAvatar = {},
                    onRemoveAvatar = {},
                    onBack = {},
                    onSubmit = {},
                    onCompleted = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("退出初始化").assertIsNotEnabled()
        composeRule.onNodeWithTag("onboarding_submit").assertIsNotEnabled()
    }
}
