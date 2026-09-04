package com.minipay.mobile.ai

import com.minipay.mobile.auth.IdentityApiException
import org.junit.Assert.assertEquals
import org.junit.Test

class AiPaymentErrorMessageTest {
    @Test
    fun `missing payment password points to setup flow`() {
        assertEquals(
            "尚未设置支付密码，请先打开“转账”完成设置后再付款",
            aiTransferConfirmationError(IdentityApiException("PAYMENT_PASSWORD_NOT_SET"))
        )
    }

    @Test
    fun `invalid and locked payment passwords have precise messages`() {
        assertEquals(
            "支付密码错误，转账未提交，请重新输入",
            aiTransferConfirmationError(IdentityApiException("PAYMENT_PASSWORD_INVALID"))
        )
        assertEquals(
            "支付密码已锁定，转账未提交，请稍后再试",
            aiTransferConfirmationError(IdentityApiException("PAYMENT_PASSWORD_LOCKED"))
        )
    }

    @Test
    fun `unknown confirmation error keeps uncertainty warning`() {
        assertEquals(
            "暂时无法确认转账结果，请先查看账单，切勿重复付款",
            aiTransferConfirmationError(IllegalStateException("unexpected"))
        )
    }
}
