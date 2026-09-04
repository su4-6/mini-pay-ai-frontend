package com.minipay.mobile.food

import com.minipay.mobile.ai.FoodOrderSummaryDto
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodOrderCenterTest {
    @Test
    fun filtersByStoreNameAndRealExternalOrderNumber() {
        val orders = listOf(
            order("ref-1", "YS20260809001", "洛阳测试餐厅"),
            order("ref-2", "YS20260809002", "MiniPay 食堂")
        )

        assertEquals(listOf("ref-1"), filterFoodOrders(orders, "洛阳", FoodOrderTab.ALL).map { it.orderRefId })
        assertEquals(listOf("ref-2"), filterFoodOrders(orders, "002", FoodOrderTab.ALL).map { it.orderRefId })
    }

    @Test
    fun categorizesActiveCompletedAndRefundOrders() {
        val active = order("active", payment = "PAID", fulfillment = "PREPARING")
        val completed = order("complete", payment = "PAID", fulfillment = "COMPLETED")
        val refunded = order("refund", payment = "PAID", fulfillment = "COMPLETED", refund = "REFUNDED")

        assertEquals(listOf("active"), filterFoodOrders(listOf(active, completed, refunded), "", FoodOrderTab.ACTIVE).map { it.orderRefId })
        assertEquals(listOf("complete"), filterFoodOrders(listOf(active, completed, refunded), "", FoodOrderTab.COMPLETED).map { it.orderRefId })
        assertEquals(listOf("refund"), filterFoodOrders(listOf(active, completed, refunded), "", FoodOrderTab.REFUND).map { it.orderRefId })
    }

    @Test
    fun onlyAllowsUnpaidOrFailedOrdersBeforeExpiryToContinuePayment() {
        val now = Instant.parse("2026-08-09T10:00:00Z")
        assertTrue(isFoodOrderPayable("UNPAID", "PLACED", "2026-08-09T10:01:00Z", now))
        assertTrue(isFoodOrderPayable("FAILED", "PLACED", "2026-08-09T10:01:00Z", now))
        assertFalse(isFoodOrderPayable("PAID", "PREPARING", "2026-08-09T10:01:00Z", now))
        assertFalse(isFoodOrderPayable("UNPAID", "PLACED", "2026-08-09T09:59:00Z", now))
    }

    @Test
    fun formatsCentWithoutFloatingPointRounding() {
        assertEquals("¥15.90", formatCent(1590))
        assertEquals("¥0.01", formatCent(1))
    }

    private fun order(
        id: String,
        externalNo: String = "YS-$id",
        store: String = "测试门店",
        payment: String = "UNPAID",
        fulfillment: String = "PLACED",
        refund: String = "NONE"
    ) = FoodOrderSummaryDto(
        orderRefId = id,
        externalOrderNo = externalNo,
        amountCent = 1590,
        currency = "CNY",
        paymentStatus = payment,
        fulfillmentStatus = fulfillment,
        refundStatus = refund,
        expiresAt = "2026-08-09T11:00:00Z",
        createdAt = "2026-08-09T09:00:00Z",
        storeName = store
    )
}
