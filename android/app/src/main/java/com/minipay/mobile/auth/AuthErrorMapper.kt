package com.minipay.mobile.auth

object AuthErrorMapper {
    fun messageFor(throwable: Throwable): String {
        val code = (throwable as? IdentityApiException)?.code
        return when (code) {
            "MOBILE_INVALID" -> "请输入正确的手机号"
            "SMS_CODE_INVALID" -> "验证码错误，请重新输入"
            "SMS_CODE_EXPIRED", "LOGIN_SESSION_EXPIRED" -> "验证码已过期，请重新获取"
            "SMS_CODE_LOCKED" -> "尝试次数过多，请十分钟后重试"
            "AUTH_RATE_LIMITED", "SMS_RESEND_TOO_SOON" -> "操作过于频繁，请稍后重试"
            "ACCOUNT_DISABLED" -> "账号当前不可用，请联系支持"
            "OAUTH_REQUEST_INVALID", "invalid_grant" -> "登录已失效，请重新获取验证码"
            "IDENTITY_NOT_CONFIGURED" -> "身份服务尚未配置"
            "NETWORK_UNAVAILABLE" -> "网络连接失败，请检查网络后重试"
            "INVALID_RESPONSE", "INVALID_TOKEN_RESPONSE" -> "服务响应异常，请稍后重试"
            else -> "登录失败，请稍后重试"
        }
    }
}
