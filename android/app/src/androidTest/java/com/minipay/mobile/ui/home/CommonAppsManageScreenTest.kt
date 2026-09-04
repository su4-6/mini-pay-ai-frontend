package com.minipay.mobile.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.minipay.mobile.home.defaultCommonAppIds
import com.minipay.mobile.home.resolveCommonApps
import com.minipay.mobile.ui.theme.MilingTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CommonAppsManageScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun fullSelectionDisablesUnselectedAddUntilAnItemIsRemoved() {
        var selected by mutableStateOf(resolveCommonApps(defaultCommonAppIds))
        composeRule.setContent {
            MilingTheme {
                CommonAppsManageScreen(
                    selectedApps = selected,
                    onBack = {},
                    onAdd = { id -> selected = resolveCommonApps(selected.map(AppService::id) + id) },
                    onRemove = { id -> selected = selected.filterNot { it.id == id } },
                    onMove = { from, to -> selected = selected.toMutableList().apply { add(to, removeAt(from)) } }
                )
            }
        }

        composeRule.onNodeWithText("5/5 · 长按拖动排序").assertIsDisplayed()
        composeRule.onNodeWithTag("common_toggle_transfer").assertIsNotEnabled()
        composeRule.onNodeWithTag("common_toggle_bills").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("common_toggle_transfer").assertIsEnabled().performClick()

        assertEquals("transfer", selected.last().id)
    }

    @Test
    fun emptySelectionShowsGuidanceAndAvailableApplications() {
        composeRule.setContent {
            MilingTheme {
                CommonAppsManageScreen(emptyList(), {}, {}, {}, { _, _ -> })
            }
        }

        composeRule.onNodeWithTag("common_apps_empty").assertIsDisplayed()
        composeRule.onNodeWithTag("common_available_scan").assertIsDisplayed()
        composeRule.onNodeWithText("0/5 · 长按拖动排序").assertIsDisplayed()
    }
}
