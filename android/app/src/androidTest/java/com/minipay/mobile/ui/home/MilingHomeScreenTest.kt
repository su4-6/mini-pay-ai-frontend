package com.minipay.mobile.ui.home

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.centerRight
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.minipay.mobile.ai.AiHomeConversation
import com.minipay.mobile.ai.AiHomeUiState
import com.minipay.mobile.ai.AiInputMode
import com.minipay.mobile.voice.VoiceInputState
import com.minipay.mobile.ui.theme.MilingTheme
import androidx.compose.ui.geometry.Offset
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.Espresso.closeSoftKeyboard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MilingHomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun plusMenu_isCollapsedByDefault_andDispatchesSelectionOnce() {
        val actions = mutableListOf<MilingHomeAction>()
        setHomeContent(onAction = actions::add)

        composeRule.onAllNodesWithTag("plus_menu").assertCountEquals(0)
        composeRule.onNodeWithTag("plus_menu_button").performClick()
        composeRule.onNodeWithTag("plus_menu").assertIsDisplayed()

        composeRule.onNodeWithTag("plus_scan").performClick()

        composeRule.onAllNodesWithTag("plus_menu").assertCountEquals(0)
        assertEquals(listOf(MilingHomeAction.Scan), actions)
    }

    @Test
    fun plusMenu_dismissesWhenScrimIsClicked() {
        setHomeContent()

        composeRule.onNodeWithTag("plus_menu_button").performClick()
        composeRule.onNodeWithTag("plus_menu").assertIsDisplayed()
        composeRule.onNodeWithTag("plus_menu_scrim").performClick()

        composeRule.onAllNodesWithTag("plus_menu").assertCountEquals(0)
    }

    @Test
    fun suggestionPopulatesComposer_andSendDispatchesPrompt() {
        val actions = mutableListOf<MilingHomeAction>()
        setHomeContent(onAction = actions::add)

        composeRule.onNodeWithTag("suggestion_transfer").performClick()
        composeRule.onNodeWithTag("message_input").assertTextEquals("帮我给小李转 50 元")
        composeRule.onNodeWithTag("send_prompt_button").assertIsDisplayed().performClick()

        assertEquals(
            listOf(MilingHomeAction.SubmitPrompt("帮我给小李转 50 元")),
            actions
        )
        composeRule.onAllNodesWithTag("send_prompt_button").assertCountEquals(0)
    }

    @Test
    fun soundToggle_updatesSemantics_andDispatchesState() {
        val actions = mutableListOf<MilingHomeAction>()
        setHomeContent(onAction = actions::add)

        composeRule.onNodeWithTag("sound_toggle").performClick()

        composeRule.onNode(hasContentDescription("开启声音")).assertIsDisplayed()
        assertEquals(listOf(MilingHomeAction.SetSoundEnabled(false)), actions)
    }

    @Test
    fun quickAction_isClickableAndDispatchesAction() {
        val actions = mutableListOf<MilingHomeAction>()
        setHomeContent(onAction = actions::add)

        composeRule.onNodeWithTag("quick_transfer")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        assertEquals(listOf(MilingHomeAction.Transfer), actions)
    }

    @Test
    fun rightSwipe_opensSessionDrawer() {
        setHomeContent()

        composeRule.onNodeWithTag("miling_home").performTouchInput { swipeRight() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("session_drawer").assertIsDisplayed()
    }

    @Test
    fun leftSwipe_closesOpenSessionDrawer() {
        setHomeContent()
        openSessionDrawer()

        composeRule.onRoot().performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("session_drawer").assertIsNotDisplayed()
    }

    @Test
    fun iconButtonsExposeAccessibleDescriptions() {
        setHomeContent()

        composeRule.onNodeWithContentDescription("打开会话列表")
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithContentDescription("打开对话")
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithContentDescription("打开更多功能")
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithContentDescription("切换到语音输入")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun voiceModeShowsHoldToTalkAndListeningSemantics() {
        composeRule.setContent {
            MilingTheme {
                MilingHomeScreen(
                    aiState = AiHomeUiState(
                        loading = false,
                        inputMode = AiInputMode.VOICE,
                        voiceInputState = VoiceInputState.Listening("帮我查询余额")
                    ),
                    onAction = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("切换到文字输入").assertIsDisplayed()
        composeRule.onNodeWithText("松手完成，上滑取消").assertIsDisplayed()
        composeRule.onAllNodesWithTag("send_prompt_button").assertCountEquals(0)
    }

    @Test
    fun inputModeToggleSwitchesBetweenKeyboardAndHoldToTalk() {
        val actions = mutableListOf<MilingHomeAction>()
        setHomeContent(onAction = actions::add)

        composeRule.onNodeWithContentDescription("切换到语音输入").performClick()
        composeRule.onNodeWithTag("voice_input_hold").assertIsDisplayed()
        composeRule.onNodeWithText("按住说话").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("切换到文字输入").performClick()
        composeRule.onNodeWithTag("message_input").assertIsDisplayed()
        assertEquals(
            listOf(
                MilingHomeAction.SetInputMode(AiInputMode.VOICE),
                MilingHomeAction.SetInputMode(AiInputMode.KEYBOARD)
            ),
            actions
        )
    }

    @Test
    fun holdToTalkDispatchesStartAndRelease() {
        val actions = mutableListOf<MilingHomeAction>()
        setHomeContent(
            initialState = AiHomeUiState(loading = false, inputMode = AiInputMode.VOICE),
            onAction = actions::add
        )

        composeRule.onNodeWithTag("voice_input_hold").performTouchInput {
            down(center)
            advanceEventTime(200)
            up()
        }

        assertEquals(
            listOf(MilingHomeAction.VoiceHoldStarted, MilingHomeAction.VoiceHoldReleased),
            actions
        )
    }

    @Test
    fun recognitionStateChangeDoesNotCancelActiveHoldGesture() {
        val actions = mutableListOf<MilingHomeAction>()
        composeRule.setContent {
            var state by remember {
                mutableStateOf(AiHomeUiState(loading = false, inputMode = AiInputMode.VOICE))
            }
            MilingTheme {
                MilingHomeScreen(
                    aiState = state,
                    onAction = { action ->
                        actions += action
                        if (action == MilingHomeAction.VoiceHoldStarted) {
                            state = state.copy(voiceInputState = VoiceInputState.Processing)
                        }
                    }
                )
            }
        }

        composeRule.onNodeWithTag("voice_input_hold").performTouchInput {
            down(center)
            advanceEventTime(200)
            up()
        }

        assertEquals(
            listOf(MilingHomeAction.VoiceHoldStarted, MilingHomeAction.VoiceHoldReleased),
            actions
        )
    }

    @Test
    fun slidingUpBeforeReleaseCancelsVoiceInput() {
        val actions = mutableListOf<MilingHomeAction>()
        setHomeContent(
            initialState = AiHomeUiState(loading = false, inputMode = AiInputMode.VOICE),
            onAction = actions::add
        )

        composeRule.onNodeWithTag("voice_input_hold").performTouchInput {
            down(center)
            moveBy(Offset(0f, -400f))
            advanceEventTime(100)
            up()
        }

        assertEquals(
            listOf(MilingHomeAction.VoiceHoldStarted, MilingHomeAction.VoiceHoldCancelled),
            actions
        )
    }

    @Test
    fun horizontalMovementWhileHoldingStaysOwnedByVoiceButton() {
        val actions = mutableListOf<MilingHomeAction>()
        setHomeContent(
            initialState = AiHomeUiState(loading = false, inputMode = AiInputMode.VOICE),
            onAction = actions::add
        )

        composeRule.onNodeWithTag("voice_input_hold").performTouchInput {
            down(center)
            moveBy(Offset(-400f, 0f))
            advanceEventTime(100)
            up()
        }

        assertEquals(
            listOf(MilingHomeAction.VoiceHoldStarted, MilingHomeAction.VoiceHoldReleased),
            actions
        )
    }

    @Test
    fun sessionDrawer_opensFromMenu_andDismissesFromScrim() {
        setHomeContent()

        composeRule.onNodeWithTag("session_drawer").assertIsNotDisplayed()
        openSessionDrawer()

        composeRule.onNodeWithTag("session_drawer").assertIsDisplayed()
        composeRule.onNodeWithTag("drawer_new_conversation").assertIsDisplayed()
        composeRule.onNodeWithTag("drawer_conversation_milk-tea")
            .assertIsDisplayed()
            .assertIsSelected()
        composeRule.onNodeWithTag("drawer_profile").assertIsDisplayed()

        composeRule.onRoot().performTouchInput {
            click(centerRight)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("session_drawer").assertIsNotDisplayed()
    }

    @Test
    fun sessionDrawer_actionsCloseDrawer_andDispatchOnce() {
        val actions = mutableListOf<MilingHomeAction>()
        setHomeContent(onAction = actions::add)

        openSessionDrawer()
        composeRule.onNodeWithTag("drawer_new_conversation").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("session_drawer").assertIsNotDisplayed()
        assertEquals(listOf(MilingHomeAction.StartNewConversation), actions)

        openSessionDrawer()
        composeRule.onNodeWithTag("drawer_conversation_milk-tea").performClick()
        composeRule.waitForIdle()

        assertEquals(
            listOf(
                MilingHomeAction.StartNewConversation,
                MilingHomeAction.SelectSession("milk-tea")
            ),
            actions
        )

        openSessionDrawer()
        composeRule.onNodeWithTag("drawer_profile").performClick()
        composeRule.waitForIdle()

        assertEquals(
            listOf(
                MilingHomeAction.StartNewConversation,
                MilingHomeAction.SelectSession("milk-tea"),
                MilingHomeAction.OpenProfile
            ),
            actions
        )
    }

    @Test
    fun sessionSearch_filtersClears_andReturnsToOverview() {
        setHomeContent()
        openSessionDrawer()

        composeRule.onNodeWithTag("drawer_search_button").performClick()
        composeRule.onNodeWithTag("drawer_search_page").assertIsDisplayed()
        composeRule.onNodeWithTag("drawer_search_input").performTextInput("不存在")
        composeRule.onNodeWithTag("drawer_search_empty").assertIsDisplayed()

        composeRule.onNodeWithTag("drawer_search_clear").performClick()
        composeRule.onNodeWithTag("drawer_conversation_milk-tea").assertIsDisplayed()

        composeRule.onNodeWithTag("drawer_search_back").performClick()
        composeRule.onNodeWithTag("drawer_new_conversation").assertIsDisplayed()
    }

    @Test
    fun drawerIconButtonsExposeAccessibleDescriptions() {
        setHomeContent()
        openSessionDrawer()

        composeRule.onNodeWithContentDescription("搜索会话")
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithTag("drawer_search_button").performClick()
        composeRule.onNodeWithContentDescription("返回会话列表")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun systemBack_exitsSearchBeforeClosingDrawer() {
        setHomeContent()
        openSessionDrawer()
        composeRule.onNodeWithTag("drawer_search_button").performClick()
        closeSoftKeyboard()

        pressBack()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("drawer_new_conversation").assertIsDisplayed()

        pressBack()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("session_drawer").assertIsNotDisplayed()
    }

    private fun openSessionDrawer() {
        composeRule.onNodeWithTag("session_drawer_button").performClick()
        composeRule.waitForIdle()
    }

    private fun setHomeContent(
        initialState: AiHomeUiState = AiHomeUiState(loading = false),
        onAction: (MilingHomeAction) -> Unit = {}
    ) {
        composeRule.setContent {
            var state by remember { mutableStateOf(initialState) }
            MilingTheme {
                MilingHomeScreen(
                    aiState = state,
                    onAction = { action ->
                        when (action) {
                            is MilingHomeAction.UpdateDraft -> state = state.copy(draftText = action.value)
                            is MilingHomeAction.SubmitPrompt -> {
                                state = state.copy(draftText = "")
                                onAction(action)
                            }
                            is MilingHomeAction.SetSoundEnabled -> {
                                state = state.copy(aiSpeechEnabled = action.enabled)
                                onAction(action)
                            }
                            is MilingHomeAction.SetInputMode -> {
                                state = state.copy(inputMode = action.mode)
                                onAction(action)
                            }
                            else -> onAction(action)
                        }
                    }
                )
            }
        }
    }
}
