package com.minipay.mobile.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class AuthErrorMapperTest {
    @Test
    fun mapsStableBackendCodesToActionableChineseMessages() {
        assertEquals(
            "验证码错误，请重新输入",
            AuthErrorMapper.messageFor(IdentityApiException("SMS_CODE_INVALID"))
        )
        assertEquals(
            "操作过于频繁，请稍后重试",
            AuthErrorMapper.messageFor(IdentityApiException("SMS_RESEND_TOO_SOON"))
        )
        assertEquals(
            "网络连接失败，请检查网络后重试",
            AuthErrorMapper.messageFor(IdentityApiException("NETWORK_UNAVAILABLE"))
        )
    }
}
