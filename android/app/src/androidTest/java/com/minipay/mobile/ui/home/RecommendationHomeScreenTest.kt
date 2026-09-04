package com.minipay.mobile.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.minipay.mobile.finance.FinanceDestination
import com.minipay.mobile.home.defaultCommonAppIds
import com.minipay.mobile.home.resolveCommonApps
import com.minipay.mobile.ui.theme.MilingTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RecommendationHomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeShowsHighFidelitySectionsAndRoutesPrimaryActions() {
        var action: RecommendationHomeAction? = null
        composeRule.setContent {
            MilingTheme { RecommendationHomeScreen(onAction = { action = it }) }
        }

        composeRule.onNodeWithText("定位中").assertIsDisplayed()
        composeRule.onNodeWithText("最近消息").assertIsDisplayed()
        composeRule.onNodeWithText("为你推荐").assertIsDisplayed()

        composeRule.onNodeWithTag("home_scan").performClick()
        assertEquals(RecommendationHomeAction.OpenFinance(FinanceDestination.SCAN), action)
        composeRule.onNodeWithTag("home_receive").performClick()
        assertEquals(RecommendationHomeAction.OpenFinance(FinanceDestination.RECEIVE), action)
        composeRule.onNodeWithTag("home_transfer").performClick()
        assertEquals(RecommendationHomeAction.OpenFinance(FinanceDestination.TRANSFER), action)
        composeRule.onNodeWithTag("home_wallet").performClick()
        assertEquals(RecommendationHomeAction.OpenFinance(FinanceDestination.WALLET), action)
    }

    @Test
    fun bottomNavigationRoutesToExistingScreens() {
        var action: RecommendationHomeAction? = null
        composeRule.setContent {
            MilingTheme { RecommendationHomeScreen(onAction = { action = it }) }
        }

        composeRule.onNodeWithTag("home_tab_miling").performClick()
        assertEquals(RecommendationHomeAction.OpenMiling, action)
        composeRule.onNodeWithTag("home_tab_messages").performClick()
        assertEquals(RecommendationHomeAction.OpenMessages, action)
        composeRule.onNodeWithTag("home_tab_profile").performClick()
        assertEquals(RecommendationHomeAction.OpenProfile, action)
    }

    @Test
    fun messageReminderAppearsOnlyWhenRequested() {
        var showReminder by mutableStateOf(true)
        composeRule.setContent {
            MilingTheme { RecommendationHomeScreen(onAction = {}, showMessageReminder = showReminder) }
        }

        composeRule.onNodeWithTag("home_message_reminder", useUnmergedTree = true).assertIsDisplayed()

        composeRule.runOnIdle { showReminder = false }
        composeRule.onAllNodesWithTag("home_message_reminder", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun unavailableServiceUsesExplicitPlaceholderAction() {
        var action: RecommendationHomeAction? = null
        composeRule.setContent {
            MilingTheme { RecommendationHomeScreen(onAction = { action = it }) }
        }

        composeRule.onNodeWithTag("service_coupon").performClick()
        assertEquals(RecommendationHomeAction.ShowUnavailable("神券"), action)
    }

    @Test
    fun compactHomeKeepsEveryServiceAndPromotionInTheScreenHierarchy() {
        composeRule.setContent {
            MilingTheme { RecommendationHomeScreen(onAction = {}) }
        }

        listOf(
            "service_coupon",
            "service_life",
            "service_travel",
            "service_health",
            "service_credit",
            "service_cards",
            "service_mobile",
            "service_balance",
            "service_delivery",
            "service_more",
            "home_promo_payment",
            "home_promo_weekend",
            "home_promo_beverage",
            "home_promo_digital",
            "home_promo_life"
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag).assertExists()
        }
    }

    @Test
    fun compactHeaderAndPromotionActionsKeepTheirExistingContracts() {
        var action: RecommendationHomeAction? = null
        composeRule.setContent {
            MilingTheme { RecommendationHomeScreen(onAction = { action = it }) }
        }

        composeRule.onNodeWithTag("home_add_service").performClick()
        composeRule.onNodeWithTag("plus_menu").assertIsDisplayed()
        composeRule.onNodeWithTag("plus_receive").performClick()
        assertEquals(RecommendationHomeAction.OpenFinance(FinanceDestination.RECEIVE), action)

        composeRule.onNodeWithTag("home_promo_digital").performClick()
        assertEquals(RecommendationHomeAction.ShowUnavailable("数码好物"), action)

        composeRule.onNodeWithTag("home_promo_life").performClick()
        assertEquals(RecommendationHomeAction.ShowUnavailable("生活缴费领优惠"), action)
    }

    @Test
    fun searchAndRecentMessagesUseRealDestinations() {
        var action: RecommendationHomeAction? = null
        composeRule.setContent {
            MilingTheme { RecommendationHomeScreen(onAction = { action = it }) }
        }

        composeRule.onNodeWithTag("home_search").performClick()
        assertEquals(RecommendationHomeAction.OpenServiceSearch, action)
        composeRule.onNodeWithTag("home_recent_messages").performClick()
        assertEquals(RecommendationHomeAction.OpenPaymentMessages, action)
    }

    @Test
    fun commonAppsShowConfiguredOrderAndUseExistingRoutes() {
        var action: RecommendationHomeAction? = null
        composeRule.setContent {
            MilingTheme {
                RecommendationHomeScreen(
                    onAction = { action = it },
                    commonApps = resolveCommonApps(defaultCommonAppIds)
                )
            }
        }

        composeRule.onNodeWithTag("home_common_apps").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("home_common_app_bills").performClick()
        assertEquals(RecommendationHomeAction.OpenFinance(FinanceDestination.BILLS), action)
        composeRule.onNodeWithTag("home_common_apps_all").performClick()
        assertEquals(RecommendationHomeAction.OpenCommonApps, action)
    }

    @Test
    fun primaryActionsKeepTheirLabelsAboveTheServiceGrid() {
        composeRule.setContent {
            MilingTheme { RecommendationHomeScreen(onAction = {}) }
        }

        listOf(
            "home_scan_label",
            "home_receive_label",
            "home_transfer_label",
            "home_wallet_label"
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed()
        }
        composeRule.onNodeWithTag("home_primary_actions").assertHeightIsAtLeast(80.dp)
        composeRule.onNodeWithTag("home_service_grid").assertIsDisplayed()
    }

    @Test
    fun tallHomeSharesAvailableHeightAcrossTheWholeLayout() {
        composeRule.setContent {
            MilingTheme {
                Box(Modifier.requiredSize(360.dp, 840.dp)) {
                    RecommendationHomeScreen(onAction = {})
                }
            }
        }

        composeRule.onNodeWithTag("home_header_services").assertHeightIsAtLeast(320.dp)
        composeRule.onNodeWithTag("home_recent_messages").assertHeightIsAtLeast(95.dp)
        composeRule.onNodeWithTag("home_recommendation_section").performScrollTo().assertHeightIsAtLeast(270.dp)
        composeRule.onNodeWithTag("home_bottom_navigation").assertIsDisplayed()
        composeRule.onNodeWithTag("home_promo_life").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun primaryServiceCommonAndBottomTargetsMeetAndroidTouchMinimum() {
        composeRule.setContent {
            MilingTheme {
                RecommendationHomeScreen(
                    onAction = {},
                    commonApps = resolveCommonApps(defaultCommonAppIds)
                )
            }
        }

        listOf("home_scan", "service_coupon", "home_common_app_bills", "home_tab_messages").forEach { tag ->
            composeRule.onNodeWithTag(tag).assertHeightIsAtLeast(48.dp)
        }
    }
}
