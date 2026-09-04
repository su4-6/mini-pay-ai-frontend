package com.minipay.mobile.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.minipay.mobile.ui.theme.MilingTheme
import com.minipay.mobile.RootTabRequestEffect
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RootBottomNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun recommendationLogoTabRemainsClickableAndSelectable() {
        var selected by mutableStateOf(RootTab.MESSAGES)
        composeRule.setContent {
            MilingTheme {
                RootBottomNavigation(
                    selected = selected,
                    onSelect = { selected = it },
                    onOpenMiling = {}
                )
            }
        }

        composeRule.onNodeWithTag("home_tab_recommend")
            .assertHasClickAction()
            .performClick()
            .assertIsSelected()
        composeRule.onNodeWithText("首页").assertExists()
    }

    @Test
    fun selectedMessagesTabStillDispatchesSelection() {
        var selections = 0
        composeRule.setContent {
            MilingTheme {
                RootBottomNavigation(
                    selected = RootTab.MESSAGES,
                    onSelect = { if (it == RootTab.MESSAGES) selections++ },
                    onOpenMiling = {}
                )
            }
        }

        composeRule.onNodeWithTag("home_tab_messages").performClick()

        assertEquals(1, selections)
    }

    @Test
    fun rootMessagesRequestRestoresSelectedMessagesBottomTab() {
        var selected by mutableStateOf(RootTab.RECOMMENDATION)
        var consumed = false
        composeRule.setContent {
            MilingTheme {
                RootTabRequestEffect(
                    rootTabRequest = "messages",
                    onOpenMessages = { selected = RootTab.MESSAGES },
                    onConsumed = { consumed = true }
                )
                RootBottomNavigation(
                    selected = selected,
                    onSelect = { selected = it },
                    onOpenMiling = {}
                )
            }
        }

        composeRule.onNodeWithTag("home_tab_messages").assertIsSelected()
        assertEquals(true, consumed)
    }
}
