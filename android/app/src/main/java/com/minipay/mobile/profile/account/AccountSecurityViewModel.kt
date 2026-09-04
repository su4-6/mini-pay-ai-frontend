package com.minipay.mobile.profile.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minipay.mobile.auth.IdentityApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class AccountSecurityViewModel @Inject constructor(
    private val gateway: AccountSecurityGateway
) : ViewModel() {
    private val mutableState = MutableStateFlow(AccountSecurityUiState())
    val state = mutableState.asStateFlow()

    private val mutableEffects = MutableSharedFlow<AccountSecurityEffect>(extraBufferCapacity = 1)
    val effects = mutableEffects.asSharedFlow()

    private var countdownJob: Job? = null
    private var phoneChallengeKey: String? = null
    private var phoneConfirmKey: String? = null
    private var emailChallengeKey: String? = null
    private var emailConfirmKey: String? = null
    private var emailDeleteKey: String? = null
    private var passwordChallengeKey: String? = null
    private var passwordVerifyKey: String? = null
    private var passwordChangeKey: String? = null

    init {
        refresh()
    }

    fun refresh() {
        if (mutableState.value.refreshing) return
        val hasOverview = mutableState.value.overview != null
        mutableState.value = mutableState.value.copy(
            loading = !hasOverview,
            refreshing = hasOverview,
            errorMessage = null,
            requestId = null
        )
        viewModelScope.launch {
            runCatching { gateway.loadOverview() }
                .onSuccess { overview ->
                    mutableState.value = mutableState.value.copy(
                        overview = overview,
                        loading = false,
                        refreshing = false,
                        errorMessage = null,
                        requestId = null
                    )
                }
                .onFailure { fail(it, preserveData = hasOverview) }
        }
    }

    fun openPhone() = show(AccountPage.PhoneInput)
    fun openEmail() = show(
        if (mutableState.value.overview?.maskedEmail == null) AccountPage.EmailInput
        else AccountPage.EmailCurrent
    )

    fun openEmailInput() = show(AccountPage.EmailInput)

    fun openPaymentPassword() {
        if (mutableState.value.overview?.paymentPasswordSet == true) requestPaymentChallenge()
    }

    fun returnToOverview() {
        countdownJob?.cancel()
        mutableState.value = mutableState.value.copy(
            page = AccountPage.Overview,
            targetInput = "",
            codeInput = "",
            secondsUntilResend = 0,
            secondsUntilExpiry = 0,
            lockedSeconds = 0,
            submitting = false,
            errorMessage = null,
            requestId = null
        )
    }

    fun backWithinAccount(): Boolean {
        if (mutableState.value.submitting) return true
        return when (mutableState.value.page) {
            AccountPage.Overview -> false
            is AccountPage.PhoneCode -> { show(AccountPage.PhoneInput, keepTarget = true); true }
            is AccountPage.EmailCode -> { show(AccountPage.EmailInput, keepTarget = true); true }
            is AccountPage.PaymentCode, is AccountPage.PaymentPassword -> { returnToOverview(); true }
            else -> { returnToOverview(); true }
        }
    }

    fun updateTarget(value: String) {
        if (mutableState.value.submitting) return
        val filtered = when (mutableState.value.page) {
            AccountPage.PhoneInput -> value.filter(Char::isDigit).take(11)
            AccountPage.EmailInput -> value.take(255)
            else -> value
        }
        mutableState.value = mutableState.value.copy(
            targetInput = filtered,
            errorMessage = null,
            requestId = null
        )
    }

    fun updateCode(value: String) {
        if (mutableState.value.submitting || mutableState.value.lockedSeconds > 0) return
        mutableState.value = mutableState.value.copy(
            codeInput = value.filter(Char::isDigit).take(6),
            errorMessage = null,
            requestId = null
        )
    }

    fun requestPhoneChallenge(isResend: Boolean = false) {
        val mobile = mutableState.value.targetInput
        val validation = AccountSecurityValidation.mobile(mobile)
            ?: if (mobile == gateway.currentMobile()) "新手机号不能与当前手机号相同" else null
        if (validation != null) return setLocalError(validation)
        if (isResend) phoneChallengeKey = null
        submit {
            val challenge = gateway.requestPhoneChange(mobile, key(::phoneChallengeKey))
            phoneChallengeKey = null
            phoneConfirmKey = null
            showChallenge(AccountPage.PhoneCode(challenge), challenge)
        }
    }

    fun confirmPhone() {
        val page = mutableState.value.page as? AccountPage.PhoneCode ?: return
        val error = codeError() ?: return
        if (error.isNotEmpty()) return setLocalError(error)
        submit {
            gateway.confirmPhoneChange(
                page.challenge.challengeId,
                mutableState.value.codeInput,
                key(::phoneConfirmKey)
            )
            phoneConfirmKey = null
            countdownJob?.cancel()
            mutableEffects.emit(AccountSecurityEffect.PhoneChanged)
        }
    }

    fun requestEmailChallenge(isResend: Boolean = false) {
        val normalized = AccountSecurityValidation.normalizeEmail(mutableState.value.targetInput)
        AccountSecurityValidation.email(normalized)?.let { return setLocalError(it) }
        if (isResend) emailChallengeKey = null
        mutableState.value = mutableState.value.copy(targetInput = normalized)
        submit {
            val challenge = gateway.requestEmailVerification(normalized, key(::emailChallengeKey))
            emailChallengeKey = null
            emailConfirmKey = null
            showChallenge(AccountPage.EmailCode(challenge), challenge)
        }
    }

    fun confirmEmail() {
        val page = mutableState.value.page as? AccountPage.EmailCode ?: return
        val error = codeError() ?: return
        if (error.isNotEmpty()) return setLocalError(error)
        submit {
            gateway.confirmEmail(
                page.challenge.challengeId,
                mutableState.value.codeInput,
                key(::emailConfirmKey)
            )
            emailConfirmKey = null
            refreshAfterMutation(
                AccountPage.Result("邮箱已更新", "邮箱绑定信息已安全更新"),
                mutableState.value.overview?.copy(maskedEmail = page.challenge.maskedTarget)
            )
        }
    }

    fun deleteEmail() {
        if (mutableState.value.submitting) return
        submit {
            gateway.deleteEmail(key(::emailDeleteKey))
            emailDeleteKey = null
            refreshAfterMutation(
                AccountPage.Result("邮箱已删除", "当前账号已不再绑定邮箱"),
                mutableState.value.overview?.copy(maskedEmail = null)
            )
        }
    }

    private fun requestPaymentChallenge(isResend: Boolean = false) {
        if (isResend) passwordChallengeKey = null
        submit {
            val challenge = gateway.requestPaymentPasswordChallenge(key(::passwordChallengeKey))
            passwordChallengeKey = null
            passwordVerifyKey = null
            showChallenge(AccountPage.PaymentCode(challenge), challenge)
        }
    }

    fun confirmPaymentCode() {
        val page = mutableState.value.page as? AccountPage.PaymentCode ?: return
        val error = codeError() ?: return
        if (error.isNotEmpty()) return setLocalError(error)
        submit {
            val verification = gateway.verifyPaymentPasswordChallenge(
                page.challenge.challengeId,
                mutableState.value.codeInput,
                key(::passwordVerifyKey)
            )
            passwordVerifyKey = null
            countdownJob?.cancel()
            mutableState.value = mutableState.value.copy(
                page = AccountPage.PaymentPassword(verification),
                codeInput = "",
                submitting = false,
                errorMessage = null,
                requestId = null
            )
        }
    }

    fun changePaymentPassword(password: String, confirmation: String) {
        val page = mutableState.value.page as? AccountPage.PaymentPassword ?: return
        AccountSecurityValidation.password(password)?.let { return setLocalError(it) }
        if (password != confirmation) return setLocalError("两次输入的支付密码不一致")
        if (!page.verification.expiresAt.isAfter(Instant.now())) {
            return setLocalError("身份验证已过期，请重新验证")
        }
        submit {
            gateway.changePaymentPassword(
                page.verification.verificationToken,
                password,
                key(::passwordChangeKey)
            )
            passwordChangeKey = null
            refreshAfterMutation(
                AccountPage.Result("修改成功", "支付密码已更新，本次登录保持有效"),
                mutableState.value.overview?.copy(paymentPasswordSet = true)
            )
        }
    }

    fun resendCode() {
        if (mutableState.value.secondsUntilResend > 0 || mutableState.value.submitting) return
        when (mutableState.value.page) {
            is AccountPage.PhoneCode -> requestPhoneChallenge(isResend = true)
            is AccountPage.EmailCode -> requestEmailChallenge(isResend = true)
            is AccountPage.PaymentCode -> requestPaymentChallenge(isResend = true)
            else -> Unit
        }
    }

    private fun codeError(): String? {
        if (mutableState.value.lockedSeconds > 0) return null
        if (mutableState.value.secondsUntilExpiry <= 0) return "验证码已过期，请重新获取"
        return AccountSecurityValidation.code(mutableState.value.codeInput).orEmpty()
    }

    private fun submit(block: suspend () -> Unit) {
        if (mutableState.value.submitting) return
        mutableState.value = mutableState.value.copy(
            submitting = true,
            errorMessage = null,
            requestId = null
        )
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess {
                    if (mutableState.value.submitting) {
                        mutableState.value = mutableState.value.copy(submitting = false)
                    }
                }
                .onFailure { fail(it, preserveData = true) }
        }
    }

    private suspend fun refreshAfterMutation(
        resultPage: AccountPage.Result,
        fallbackOverview: AccountSecurityOverview?
    ) {
        val overview = runCatching { gateway.loadOverview() }.getOrNull()
        mutableState.value = mutableState.value.copy(
            page = resultPage,
            overview = overview ?: fallbackOverview ?: mutableState.value.overview,
            loading = false,
            refreshing = false,
            submitting = false,
            targetInput = "",
            codeInput = "",
            errorMessage = if (overview == null) "操作成功，账号概览将在稍后刷新" else null,
            requestId = null
        )
    }

    private fun show(page: AccountPage, keepTarget: Boolean = false) {
        countdownJob?.cancel()
        mutableState.value = mutableState.value.copy(
            page = page,
            targetInput = if (keepTarget) mutableState.value.targetInput else "",
            codeInput = "",
            secondsUntilResend = 0,
            secondsUntilExpiry = 0,
            lockedSeconds = 0,
            submitting = false,
            errorMessage = null,
            requestId = null
        )
    }

    private fun showChallenge(page: AccountPage, challenge: VerificationChallenge) {
        mutableState.value = mutableState.value.copy(
            page = page,
            codeInput = "",
            secondsUntilResend = challenge.resendAfterSeconds,
            secondsUntilExpiry = remainingSeconds(challenge.expiresAt),
            lockedSeconds = 0,
            submitting = false,
            errorMessage = null,
            requestId = null
        )
        startCountdown(challenge.expiresAt)
    }

    private fun startCountdown(expiresAt: Instant) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (true) {
                delay(1_000)
                val state = mutableState.value
                val resend = (state.secondsUntilResend - 1).coerceAtLeast(0)
                val locked = (state.lockedSeconds - 1).coerceAtLeast(0)
                val expiry = remainingSeconds(expiresAt)
                mutableState.value = state.copy(
                    secondsUntilResend = resend,
                    secondsUntilExpiry = expiry,
                    lockedSeconds = locked
                )
                if (expiry <= 0 && resend <= 0 && locked <= 0) break
            }
        }
    }

    private fun remainingSeconds(expiresAt: Instant): Long =
        Duration.between(Instant.now(), expiresAt).seconds.coerceAtLeast(0)

    private fun setLocalError(message: String) {
        mutableState.value = mutableState.value.copy(errorMessage = message, requestId = null)
    }

    private fun fail(error: Throwable, preserveData: Boolean) {
        val apiError = error as? IdentityApiException
        if (apiError?.code in setOf(
                "TOKEN_INVALID",
                "INVALID_ACCESS_TOKEN",
                "LOGIN_SESSION_EXPIRED",
                "CURRENT_MOBILE_UNAVAILABLE"
            )
        ) {
            mutableEffects.tryEmit(AccountSecurityEffect.SessionInvalid)
            return
        }
        val retryAfter = apiError?.retryAfterSeconds?.coerceAtLeast(0) ?: 0
        val challengeExpired = apiError?.code == "SMS_EXPIRED" ||
            apiError?.code == "SMS_CODE_EXPIRED" ||
            apiError?.code == "EMAIL_CODE_EXPIRED"
        mutableState.value = mutableState.value.copy(
            loading = false,
            refreshing = false,
            submitting = false,
            lockedSeconds = if (apiError?.code in setOf(
                    "SMS_LOCKED", "SMS_CODE_LOCKED", "EMAIL_CODE_LOCKED"
                )
            ) retryAfter else mutableState.value.lockedSeconds,
            secondsUntilResend = when {
                challengeExpired -> 0
                apiError?.code in setOf("SMS_RESEND_TOO_SOON", "EMAIL_RESEND_TOO_SOON") -> retryAfter
                else -> mutableState.value.secondsUntilResend
            },
            secondsUntilExpiry = if (challengeExpired) 0 else mutableState.value.secondsUntilExpiry,
            errorMessage = message(error),
            requestId = apiError?.requestId
        )
        if (apiError?.code != "NETWORK_UNAVAILABLE") clearAttemptKeyForCurrentPage()
    }

    private fun clearAttemptKeyForCurrentPage() {
        when (mutableState.value.page) {
            AccountPage.PhoneInput -> phoneChallengeKey = null
            is AccountPage.PhoneCode -> phoneConfirmKey = null
            AccountPage.EmailInput -> emailChallengeKey = null
            AccountPage.EmailCurrent -> emailDeleteKey = null
            is AccountPage.EmailCode -> emailConfirmKey = null
            is AccountPage.PaymentCode -> passwordVerifyKey = null
            is AccountPage.PaymentPassword -> passwordChangeKey = null
            AccountPage.Overview -> passwordChallengeKey = null
            else -> Unit
        }
    }

    private fun message(error: Throwable): String = when ((error as? IdentityApiException)?.code) {
        "NETWORK_UNAVAILABLE" -> "网络连接失败，请稍后重试"
        "MOBILE_ALREADY_BOUND" -> "该手机号已被其他账号绑定"
        "EMAIL_ALREADY_BOUND" -> "该邮箱已被其他账号绑定"
        "EMAIL_DELIVERY_UNAVAILABLE" -> "邮箱服务暂不可用，请稍后重试"
        "SMS_INVALID", "SMS_CODE_INVALID", "EMAIL_CODE_INVALID" -> "验证码错误，请重新输入"
        "SMS_EXPIRED", "SMS_CODE_EXPIRED", "EMAIL_CODE_EXPIRED" -> "验证码已过期，请重新获取"
        "SMS_LOCKED", "SMS_CODE_LOCKED", "EMAIL_CODE_LOCKED" -> "验证次数过多，请稍后再试"
        "SMS_RESEND_TOO_SOON", "EMAIL_RESEND_TOO_SOON" -> "请等待倒计时结束后重发"
        "CURRENT_MOBILE_MISMATCH" -> "当前手机号校验失败，请重新登录"
        "DEVICE_MISMATCH" -> "验证设备不一致，请重新验证"
        "VERIFICATION_TOKEN_INVALID" -> "身份验证凭据无效，请重新验证"
        "VERIFICATION_TOKEN_EXPIRED" -> "身份验证已过期，请重新验证"
        "VERIFICATION_TOKEN_USED" -> "身份验证已使用，请重新验证"
        "PAYMENT_PASSWORD_NOT_SET" -> "尚未设置支付密码"
        "PAYMENT_PASSWORD_INVALID" -> "请输入 6 位数字支付密码"
        "IDEMPOTENCY_KEY_REUSED" -> "请求发生冲突，请稍后重新操作"
        "IDENTITY_NOT_CONFIGURED" -> "账号安全服务尚未配置"
        else -> "操作失败，请稍后重试"
    }

    private fun key(property: kotlin.reflect.KMutableProperty0<String?>): String =
        property.get() ?: UUID.randomUUID().toString().also(property::set)

    override fun onCleared() {
        countdownJob?.cancel()
        super.onCleared()
    }
}
