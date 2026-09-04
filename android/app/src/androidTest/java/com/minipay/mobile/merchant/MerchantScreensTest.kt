package com.minipay.mobile.merchant

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.minipay.mobile.ui.theme.MilingTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MerchantScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun merchantTypeIsReadOnlyIndividualBusiness() {
        composeRule.setContent {
            MilingTheme { ConsumerMerchantTypeField() }
        }

        composeRule.onNodeWithText("商户类型").assertIsDisplayed()
        composeRule.onNodeWithText("个体工商户").assertIsDisplayed()
        composeRule.onNodeWithText("个人", substring = false).assertDoesNotExist()
        composeRule.onNodeWithText("企业", substring = false).assertDoesNotExist()
    }

    @Test
    fun merchantLocationSummaryOpensPickerAndShowsCurrentSelection() {
        var selections = 0
        composeRule.setContent {
            MilingTheme {
                MerchantLocationSummaryCard(
                    latitude = 34.6197,
                    longitude = 112.4540,
                    address = "河南省洛阳市洛龙区开元大道",
                    onClick = { selections += 1 }
                )
            }
        }

        composeRule.onNodeWithText("河南省洛阳市洛龙区开元大道").assertIsDisplayed()
        composeRule.onNodeWithText("34.619700, 112.454000").assertIsDisplayed()
        composeRule.onNodeWithText("重新选择").performClick()

        assertEquals(1, selections)
    }

    @Test
    fun merchantLocationSummaryPromptsForFirstSelection() {
        composeRule.setContent {
            MilingTheme {
                MerchantLocationSummaryCard(
                    latitude = null,
                    longitude = null,
                    address = null,
                    onClick = {}
                )
            }
        }

        composeRule.onNodeWithText("请选择经营位置").assertIsDisplayed()
        composeRule.onNodeWithText("选择位置").assertIsDisplayed()
    }

    @Test
    fun approvedMerchantShowsInitializationProgress() {
        composeRule.setContent {
            MilingTheme {
                ApprovedMerchant(
                    state = approvedState(initializationLoading = true),
                    modifier = androidx.compose.ui.Modifier,
                    onRetryInitialization = {}
                )
            }
        }

        composeRule.onNodeWithTag("merchant_qr_loading").assertIsDisplayed()
        composeRule.onNodeWithText("正在生成收款码…").assertIsDisplayed()
    }

    @Test
    fun approvedMerchantStopsLoadingAndAllowsRetryAfterFailure() {
        var retries = 0
        composeRule.setContent {
            MilingTheme {
                ApprovedMerchant(
                    state = approvedState(initializationError = "收款码生成服务暂不可用"),
                    modifier = androidx.compose.ui.Modifier,
                    onRetryInitialization = { retries += 1 }
                )
            }
        }

        composeRule.onNodeWithText("收款码生成服务暂不可用").assertIsDisplayed()
        composeRule.onNodeWithTag("merchant_qr_retry").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun merchantQrEncodingFailureIsRenderedInsteadOfThrown() {
        composeRule.setContent { MilingTheme { MerchantQr("") } }

        composeRule.onNodeWithTag("merchant_qr_render_error").assertIsDisplayed()
    }

    private fun approvedState(
        initializationLoading: Boolean = false,
        initializationError: String? = null
    ) = MerchantPortalState(
        loading = false,
        loaded = true,
        application = MerchantApplication(
            id = 1,
            merchantType = "INDIVIDUAL",
            shopName = "测试商户",
            contactName = "张*",
            contactMobile = "138****0000",
            applyStatus = "APPROVED",
            resultantMerchantId = "merchant-1",
            applyTime = "2026-08-09T08:00:00Z",
            version = 1
        ),
        initializationLoading = initializationLoading,
        initializationError = initializationError
    )
}
