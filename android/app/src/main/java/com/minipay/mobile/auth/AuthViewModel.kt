package com.minipay.mobile.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minipay.mobile.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthGateway
) : ViewModel() {
    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.CheckingSession)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()
    private var countdownJob: Job? = null
    private var codeDeliveryJob: Job? = null
    private var verificationJob: Job? = null
    private var deliveryRequestId = 0L
    private var verificationRequestId = 0L
    private var sessionKey = 0L

    init {
        viewModelScope.launch {
            val restored = repository.restoreSession()
            _uiState.value = restored?.let {
                AuthUiState.Session(
                    userId = it.userId,
                    payPasswordSet = it.payPasswordSet,
                    onboardingRequired = it.onboardingRequired,
                    sessionKey = ++sessionKey
                )
            } ?: AuthUiState.PhoneEntry()
        }
        viewModelScope.launch {
            repository.sessionInvalidations.collect {
                if (_uiState.value is AuthUiState.Session) {
                    _uiState.value = AuthUiState.PhoneEntry()
                }
            }
        }
    }

    fun updateMobile(value: String) {
        val state = _uiState.value as? AuthUiState.PhoneEntry ?: return
        _uiState.value = state.copy(
            mobile = value.filter(Char::isDigit).take(11),
            errorMessage = null
        )
    }

    fun clearMobile() = updateMobile("")

    fun toggleAgreement() {
        val state = _uiState.value as? AuthUiState.PhoneEntry ?: return
        _uiState.value = state.copy(
            agreementAccepted = !state.agreementAccepted,
            errorMessage = null
        )
    }

    fun sendCode() {
        val state = _uiState.value as? AuthUiState.PhoneEntry ?: return
        if (!state.mobile.matches(Regex("^1[3-9]\\d{9}$"))) {
            _uiState.value = state.copy(errorMessage = "请输入正确的手机号")
            return
        }
        if (!state.agreementAccepted) {
            _uiState.value = state.copy(errorMessage = "请先阅读并同意用户协议和隐私政策")
            return
        }
        requestCode(state.mobile, maskMobile(state.mobile))
    }

    fun updateCode(value: String) {
        val state = _uiState.value as? AuthUiState.CodeEntry ?: return
        if (state.submitting || state.deliveryStatus != CodeDeliveryStatus.SENT) return
        val code = value.filter(Char::isDigit).take(6)
        _uiState.value = state.copy(code = code, errorMessage = null)
        if (code.length == 6) {
            verifyCode()
        }
    }

    fun verifyCode() {
        val state = _uiState.value as? AuthUiState.CodeEntry ?: return
        val challengeId = state.challengeId ?: return
        if (state.submitting || state.deliveryStatus != CodeDeliveryStatus.SENT || state.code.length != 6) return
        _uiState.value = state.copy(submitting = true, errorMessage = null)
        val requestId = ++verificationRequestId
        verificationJob?.cancel()
        verificationJob = viewModelScope.launch {
            runCatching { repository.verifyAndLogin(challengeId, state.code) }
                .onSuccess { authorization ->
                    val current = _uiState.value as? AuthUiState.CodeEntry
                    if (requestId != verificationRequestId || current?.challengeId != challengeId || !current.submitting) {
                        return@onSuccess
                    }
                    repository.saveCurrentMobile(state.mobile)
                    countdownJob?.cancel()
                    _uiState.value = AuthUiState.Session(
                        userId = authorization.userId,
                        payPasswordSet = authorization.payPasswordSet,
                        onboardingRequired = authorization.onboardingRequired,
                        sessionKey = ++sessionKey
                    )
                }
                .onFailure { exception ->
                    val current = _uiState.value as? AuthUiState.CodeEntry
                    if (requestId != verificationRequestId || current?.challengeId != challengeId) {
                        return@onFailure
                    }
                    _uiState.value = state.copy(
                        code = "",
                        submitting = false,
                        errorMessage = AuthErrorMapper.messageFor(exception)
                    )
                }
        }
    }

    fun resendCode() {
        val state = _uiState.value as? AuthUiState.CodeEntry ?: return
        if (state.submitting || state.deliveryStatus == CodeDeliveryStatus.SENDING) return
        if (state.deliveryStatus == CodeDeliveryStatus.SENT && state.secondsUntilResend > 0) return
        requestCode(state.mobile, state.maskedMobile)
    }

    fun backToPhone() {
        val state = _uiState.value as? AuthUiState.CodeEntry ?: return
        if (state.submitting) return
        countdownJob?.cancel()
        codeDeliveryJob?.cancel()
        deliveryRequestId += 1
        repository.cancelChallenge()
        _uiState.value = AuthUiState.PhoneEntry(
            mobile = state.mobile,
            agreementAccepted = true
        )
    }

    fun logout() {
        verificationRequestId += 1
        verificationJob?.cancel()
        viewModelScope.launch {
            runCatching { repository.logout() }
            _uiState.value = AuthUiState.PhoneEntry()
        }
    }

    fun forceLocalLogout() {
        viewModelScope.launch {
            runCatching { repository.invalidateLocalSession() }
            _uiState.value = AuthUiState.PhoneEntry()
        }
    }

    fun completeOnboarding() {
        val state = _uiState.value as? AuthUiState.Session ?: return
        if (!state.onboardingRequired) return
        _uiState.value = state.copy(onboardingRequired = false)
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (true) {
                delay(1_000)
                val state = _uiState.value as? AuthUiState.CodeEntry ?: break
                if (state.secondsUntilResend <= 0) break
                _uiState.value = state.copy(secondsUntilResend = state.secondsUntilResend - 1)
            }
        }
    }

    override fun onCleared() {
        countdownJob?.cancel()
        codeDeliveryJob?.cancel()
        verificationJob?.cancel()
        super.onCleared()
    }

    private fun requestCode(mobile: String, maskedMobile: String) {
        countdownJob?.cancel()
        codeDeliveryJob?.cancel()
        val requestId = ++deliveryRequestId
        _uiState.value = AuthUiState.CodeEntry(
            mobile = mobile,
            maskedMobile = maskedMobile,
            challengeId = null,
            deliveryStatus = CodeDeliveryStatus.SENDING,
            secondsUntilResend = 0
        )
        codeDeliveryJob = viewModelScope.launch {
            try {
                val challenge = repository.sendCode(mobile)
                if (requestId != deliveryRequestId) return@launch
                _uiState.value = AuthUiState.CodeEntry(
                    mobile = mobile,
                    maskedMobile = challenge.maskedMobile,
                    challengeId = challenge.challengeId,
                    deliveryStatus = CodeDeliveryStatus.SENT,
                    secondsUntilResend = challenge.resendAfterSeconds
                )
                startCountdown()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Throwable) {
                if (requestId != deliveryRequestId) return@launch
                logDebugFailure("sendCode", exception)
                _uiState.value = AuthUiState.CodeEntry(
                    mobile = mobile,
                    maskedMobile = maskedMobile,
                    challengeId = null,
                    deliveryStatus = CodeDeliveryStatus.FAILED,
                    secondsUntilResend = 0,
                    errorMessage = AuthErrorMapper.messageFor(exception)
                )
            }
        }
    }

    private fun maskMobile(mobile: String): String =
        if (mobile.length == 11) "${mobile.take(3)}****${mobile.takeLast(4)}" else mobile

    private fun logDebugFailure(operation: String, exception: Throwable) {
        if (!BuildConfig.DEBUG) return
        val apiException = exception as? IdentityApiException
        runCatching {
            Log.e(
                DEBUG_TAG,
                "$operation failed: type=${exception.javaClass.simpleName}, "
                    + "code=${apiException?.code}, status=${apiException?.status}"
            )
        }
    }

    private companion object {
        const val DEBUG_TAG = "MiniPayAuth"
    }
}
