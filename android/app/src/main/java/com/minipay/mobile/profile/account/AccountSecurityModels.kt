package com.minipay.mobile.profile.account

import java.time.Instant

data class AccountSecurityOverview(
    val maskedMobile: String,
    val maskedEmail: String?,
    val paymentPasswordSet: Boolean
)

data class VerificationChallenge(
    val challengeId: String,
    val maskedTarget: String,
    val expiresAt: Instant,
    val resendAfterSeconds: Long
)

data class PaymentPasswordVerification(
    val verificationToken: String,
    val expiresAt: Instant
)

enum class VerificationPurpose {
    PHONE_CHANGE,
    EMAIL_BIND,
    PAYMENT_PASSWORD_CHANGE
}

sealed interface AccountPage {
    data object Overview : AccountPage
    data object PhoneInput : AccountPage
    data class PhoneCode(val challenge: VerificationChallenge) : AccountPage
    data object EmailInput : AccountPage
    data object EmailCurrent : AccountPage
    data class EmailCode(val challenge: VerificationChallenge) : AccountPage
    data class PaymentCode(val challenge: VerificationChallenge) : AccountPage
    data class PaymentPassword(val verification: PaymentPasswordVerification) : AccountPage
    data class Result(val title: String, val message: String) : AccountPage
}

data class AccountSecurityUiState(
    val page: AccountPage = AccountPage.Overview,
    val overview: AccountSecurityOverview? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val submitting: Boolean = false,
    val targetInput: String = "",
    val codeInput: String = "",
    val secondsUntilResend: Long = 0,
    val secondsUntilExpiry: Long = 0,
    val lockedSeconds: Long = 0,
    val errorMessage: String? = null,
    val requestId: String? = null
)

sealed interface AccountSecurityEffect {
    data object PhoneChanged : AccountSecurityEffect
    data object SessionInvalid : AccountSecurityEffect
}

object AccountSecurityValidation {
    private val mainlandMobile = Regex("^1[3-9]\\d{9}$")
    private val verificationCode = Regex("^\\d{6}$")
    private val paymentPassword = Regex("^\\d{6}$")
    private val simpleEmail = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun mobile(value: String): String? = when {
        value.isBlank() -> "请输入新手机号"
        !mainlandMobile.matches(value) -> "请输入正确的 11 位手机号"
        else -> null
    }

    fun normalizeEmail(value: String): String {
        val trimmed = value.trim()
        val separator = trimmed.lastIndexOf('@')
        if (separator <= 0 || separator == trimmed.lastIndex) return trimmed
        return trimmed.substring(0, separator) + "@" + trimmed.substring(separator + 1).lowercase()
    }

    fun email(value: String): String? {
        val normalized = normalizeEmail(value)
        return when {
            normalized.isBlank() -> "请输入邮箱地址"
            normalized.length > 254 -> "邮箱地址不能超过 254 个字符"
            !simpleEmail.matches(normalized) -> "请输入正确的邮箱地址"
            else -> null
        }
    }

    fun code(value: String): String? =
        if (verificationCode.matches(value)) null else "请输入 6 位数字验证码"

    fun password(value: String): String? =
        if (paymentPassword.matches(value)) null else "请输入 6 位数字支付密码"
}
