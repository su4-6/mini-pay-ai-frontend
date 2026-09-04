package com.minipay.mobile.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneLayoutTest {
    @Test
    fun classifiesSupportedPhoneWidthsAndOrientations() {
        assertEquals(PhoneWidthClass.NARROW, phoneLayoutSpec(320, 568).widthClass)
        assertEquals(PhoneWidthClass.STANDARD, phoneLayoutSpec(393, 873).widthClass)
        assertEquals(PhoneWidthClass.WIDE, phoneLayoutSpec(480, 800).widthClass)
        assertTrue(phoneLayoutSpec(640, 360).landscape)
        assertTrue(phoneLayoutSpec(360, 568).shortHeight)
        assertFalse(phoneLayoutSpec(412, 915).shortHeight)
    }
}
