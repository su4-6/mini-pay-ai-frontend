package com.minipay.mobile.food

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.minipay.mobile.ai.FoodOrderDetailDto
import com.minipay.mobile.ai.FoodOrderItemDto
import com.minipay.mobile.ai.FoodOrderSummaryDto
import com.minipay.mobile.ui.theme.MilingTheme
import org.junit.Rule
import org.junit.Test

class FoodOrderCenterUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun listCardShowsRealOrderDataWithoutLegacyActions() {
        composeRule.setContent {
            MilingTheme {
                FoodOrderCard(summary(), onOpenOrder = {}, onPay = {})
            }
        }

        composeRule.onNodeWithText("洛阳测试餐厅").assertIsDisplayed()
        composeRule.onNodeWithText("订单号 YS20260809001").assertIsDisplayed()
        composeRule.onNodeWithText("招牌套餐").assertIsDisplayed()
        composeRule.onNodeWithText("开发票").assertDoesNotExist()
        composeRule.onNodeWithText("评价").assertDoesNotExist()
        composeRule.onNodeWithText("再来一单").assertDoesNotExist()
    }

    @Test
    fun detailShowsRealOrderNumberWithoutAfterSalesQuestionsOrAds() {
        composeRule.setContent {
            MilingTheme {
                FoodOrderDetailContent(detail(), onBack = {}, onPay = {}, unavailable = {})
            }
        }

        composeRule.onNodeWithText("YS20260809001").assertIsDisplayed()
        composeRule.onNodeWithText("价格明细").assertIsDisplayed()
        composeRule.onNodeWithText("订单信息").assertIsDisplayed()
        composeRule.onNodeWithText("申请售后").assertDoesNotExist()
        composeRule.onNodeWithText("常见问题").assertDoesNotExist()
        composeRule.onNodeWithText("广告").assertDoesNotExist()
    }

    private fun item() = FoodOrderItemDto(
        productId = 9,
        name = "招牌套餐",
        sku = "默认",
        quantity = 1,
        unitPriceCent = 1390,
        lineAmountCent = 1390
    )

    private fun summary() = FoodOrderSummaryDto(
        orderRefId = "019f0000-0000-7000-8000-000000000001",
        externalOrderNo = "YS20260809001",
        amountCent = 1590,
        currency = "CNY",
        paymentStatus = "PAID",
        fulfillmentStatus = "PREPARING",
        refundStatus = "NONE",
        expiresAt = "2026-08-09T10:15:00Z",
        createdAt = "2026-08-09T10:00:00Z",
        storeName = "洛阳测试餐厅",
        fulfillmentType = "TAKEOUT",
        items = listOf(item()),
        totalQuantity = 1,
        subtotalCent = 1390,
        deliveryFeeCent = 200
    )

    private fun detail() = FoodOrderDetailDto(
        orderRefId = "019f0000-0000-7000-8000-000000000001",
        externalOrderNo = "YS20260809001",
        amountCent = 1590,
        currency = "CNY",
        paymentStatus = "PAID",
        fulfillmentStatus = "COMPLETED",
        refundStatus = "NONE",
        expiresAt = "2026-08-09T10:15:00Z",
        createdAt = "2026-08-09T10:00:00Z",
        storeName = "洛阳测试餐厅",
        fulfillmentType = "TAKEOUT",
        recipient = "测试用户",
        phone = "138****8000",
        address = "测试地址",
        items = listOf(item()),
        totalQuantity = 1,
        subtotalCent = 1390,
        deliveryFeeCent = 200
    )
}
