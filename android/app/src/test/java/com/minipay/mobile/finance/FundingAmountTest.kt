package com.minipay.mobile.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FundingAmountTest {
    @Test
    fun convertsYuanWithAtMostTwoFractionDigits() {
        assertEquals(1L, yuanToCent("0.01"))
        assertEquals(1200L, yuanToCent("12"))
        assertEquals(1234L, yuanToCent("12.34"))
    }

    @Test
    fun rejectsInvalidOrOutOfRangeAmountsWithoutTruncation() {
        assertNull(yuanToCent(""))
        assertNull(yuanToCent(".5"))
        assertNull(yuanToCent("1.234"))
        assertNull(yuanToCent("-1"))
        assertNull(yuanToCent("10000.01"))
    }
}
