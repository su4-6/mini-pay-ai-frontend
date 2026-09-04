package com.minipay.mobile.merchant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MerchantPaymentTest {
    @Test fun acceptsAmountBoundaries() {
        assertEquals(1L, merchantYuanToCent("0.01"))
        assertEquals(1_000_000L, merchantYuanToCent("10000"))
    }

    @Test fun rejectsOutOfRangeOrOverPrecisionAmounts() {
        assertNull(merchantYuanToCent("0"))
        assertNull(merchantYuanToCent("10000.01"))
        assertNull(merchantYuanToCent("1.001"))
    }
}
