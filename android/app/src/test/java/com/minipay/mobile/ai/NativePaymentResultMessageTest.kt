package com.minipay.mobile.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativePaymentResultMessageTest {
    @Test
    fun `transfer result cards are owned by the native payment result screen`() {
        val message = AiHomeMessage(
            id = "message-id",
            role = AiHomeMessageRole.ASSISTANT,
            text = "这是 Payment 返回的权威转账结果。",
            cardType = "payment.transfer-order"
        )

        assertTrue(message.isNativePaymentResultMessage())
    }

    @Test
    fun `transfer confirmation remains visible in the ai conversation`() {
        val message = AiHomeMessage(
            id = "message-id",
            role = AiHomeMessageRole.ASSISTANT,
            text = "请核对收款人和金额，然后在原生安全页面确认。",
            cardType = "payment.transfer-intent"
        )

        assertFalse(message.isNativePaymentResultMessage())
    }

    @Test
    fun `only a waiting confirmation run can open the payment password flow`() {
        assertTrue(isTransferConfirmationAllowed("WAITING_CONFIRMATION"))
        assertFalse(isTransferConfirmationAllowed("COMPLETED"))
        assertFalse(isTransferConfirmationAllowed("FAILED"))
        assertFalse(isTransferConfirmationAllowed("UNDERSTANDING"))
    }
}
