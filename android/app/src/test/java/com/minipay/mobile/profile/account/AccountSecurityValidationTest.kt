package com.minipay.mobile.profile.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountSecurityValidationTest {
    @Test
    fun mainlandMobileRequiresElevenValidDigits() {
        assertNull(AccountSecurityValidation.mobile("13800138000"))
        assertEquals("请输入正确的 11 位手机号", AccountSecurityValidation.mobile("12800138000"))
        assertEquals("请输入正确的 11 位手机号", AccountSecurityValidation.mobile("1380013800"))
    }

    @Test
    fun emailTrimsAndNormalizesDomainOnly() {
        assertEquals(
            "User.Name@example.com",
            AccountSecurityValidation.normalizeEmail("  User.Name@Example.COM  ")
        )
        assertNull(AccountSecurityValidation.email("User.Name@example.com"))
        assertEquals("请输入正确的邮箱地址", AccountSecurityValidation.email("missing-domain@"))
        assertEquals(
            "邮箱地址不能超过 254 个字符",
            AccountSecurityValidation.email("a".repeat(250) + "@example.com")
        )
    }

    @Test
    fun verificationCodeAndPaymentPasswordAreSixDigits() {
        assertNull(AccountSecurityValidation.code("123456"))
        assertNull(AccountSecurityValidation.password("654321"))
        assertEquals("请输入 6 位数字验证码", AccountSecurityValidation.code("12345a"))
        assertEquals("请输入 6 位数字支付密码", AccountSecurityValidation.password("12345"))
    }
}
