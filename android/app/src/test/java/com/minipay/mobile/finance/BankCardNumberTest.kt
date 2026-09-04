package com.minipay.mobile.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BankCardNumberTest {
    @Test
    fun normalizesAndAcceptsSandboxFormatCardNumbers() {
        assertEquals("4532015112830366", BankCardNumber.normalize("4532 0151 1283 0366"))
        assertTrue(BankCardNumber.isValid("4532 0151 1283 0366"))
        assertTrue(BankCardNumber.isValid("6222021234567890125"))
    }

    @Test
    fun rejectsInvalidLengthAndNormalizesNonAsciiCharactersOut() {
        assertFalse(BankCardNumber.isValid("123456789111111"))
        assertEquals("6222021234567890125", BankCardNumber.normalize("6222021234567890125A"))
        assertFalse(BankCardNumber.isValid("６２２２０２１２３４５６７８９０１２５"))
    }
}
