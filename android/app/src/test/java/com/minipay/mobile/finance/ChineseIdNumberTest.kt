package com.minipay.mobile.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChineseIdNumberTest {
    @Test
    fun acceptsNationalChecksumAndNormalizesLowercaseX() {
        val value = ChineseIdNumber.normalize("11010519491231002x")

        assertEquals("11010519491231002X", value)
        assertTrue(ChineseIdNumber.isValid(value))
    }

    @Test
    fun rejectsInvalidStructureAndChecksum() {
        assertFalse(ChineseIdNumber.isValid("110105194912310021"))
        assertFalse(ChineseIdNumber.isValid("01010519491231002X"))
        assertFalse(ChineseIdNumber.isValid("11010519491231002"))
    }

    @Test
    fun stripsUnsupportedCharactersAndLimitsLength() {
        assertEquals("11010519491231002X", ChineseIdNumber.normalize("110105-19491231002x123"))
    }
}
