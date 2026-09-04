package com.minipay.mobile.ui.profile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileValidationTest {
    @Test
    fun acceptsProductNicknameCharacterSet() {
        assertTrue(isNicknameValid("小满_2026"))
        assertTrue(isNicknameValid("MiniPay用户"))
    }

    @Test
    fun rejectsPunctuationAndInvalidLength() {
        assertFalse(isNicknameValid("a"))
        assertFalse(isNicknameValid("小满!"))
        assertFalse(isNicknameValid("a".repeat(21)))
    }
}
