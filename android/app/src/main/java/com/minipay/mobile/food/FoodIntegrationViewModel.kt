package com.minipay.mobile.food

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minipay.mobile.ai.AiAgentApiException
import com.minipay.mobile.ai.CommerceApi
import com.minipay.mobile.ai.FoodHandoffDto
import com.minipay.mobile.ai.FoodLocationContextDto
import com.minipay.mobile.auth.IdentityApiException
import com.minipay.mobile.authorization.ApplicationAuthorizationDto
import com.minipay.mobile.authorization.ApplicationAuthorizationRepository
import com.minipay.mobile.finance.FinanceRepository
import com.minipay.mobile.home.LocationWeatherProvider
import com.minipay.mobile.home.isFresh
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FoodIntegrationState(
    val checkingEntry: Boolean = false,
    val authorizing: Boolean = false,
    val authorized: Boolean = false,
    val authorization: ApplicationAuthorizationDto? = null,
    val phoneChallengeId: String? = null,
    val sendingCode: Boolean = false,
    val resendAfterSeconds: Int = 0,
    val codeSentToMaskedMobile: String? = null,
    val handoff: FoodHandoffDto? = null,
    val error: String? = null
)

enum class FoodLocationContextStatus {
    PERMISSION_DENIED,
    AUTHORIZATION_REQUIRED,
    LOCATION_FAILED,
    NETWORK_UNAVAILABLE
}

@HiltViewModel
class FoodIntegrationViewModel @Inject constructor(
    private val commerce: CommerceApi,
    private val authorizations: ApplicationAuthorizationRepository,
    private val locations: LocationWeatherProvider,
    private val finance: FinanceRepository
) : ViewModel() {
    private val mutableState = MutableStateFlow(FoodIntegrationState())
    val state: StateFlow<FoodIntegrationState> = mutableState.asStateFlow()
    private var phoneChallengeGeneration = 0L

    fun checkEntry(onReady: () -> Unit, onConsentRequired: () -> Unit) {
        if (mutableState.value.checkingEntry) return
        mutableState.update { it.copy(checkingEntry = true, error = null) }
        viewModelScope.launch {
            runCatching {
                var status = foodEntryStatusWithTransientRetry()
                if (status.state == "SYNC_FAILED") {
                    commerce.bindFood()
                    status = foodEntryStatusWithTransientRetry()
                }
                status
            }.onSuccess { status ->
                mutableState.update { it.copy(checkingEntry = false) }
                if (status.state == "READY") onReady() else onConsentRequired()
            }.onFailure { error ->
                logSafeFailure("entry", error)
                mutableState.update { it.copy(checkingEntry = false, error = message(error)) }
            }
        }
    }

    fun loadConsent() {
        viewModelScope.launch {
            runCatching { authorizations.detail(APPLICATION_ID) }
                .onSuccess { authorization ->
                    mutableState.update { it.copy(authorization = authorization, error = null) }
                }
                .onFailure { error -> mutableState.update { it.copy(error = message(error)) } }
        }
    }

    fun requestPhoneCode(mobile: String) {
        if (mobile.length != 11 || mutableState.value.sendingCode
            || mutableState.value.resendAfterSeconds > 0) return
        val generation = phoneChallengeGeneration
        mutableState.update {
            it.copy(sendingCode = true, error = null, codeSentToMaskedMobile = null)
        }
        viewModelScope.launch {
            runCatching {
                authorizations.requestApplicationPhoneChallenge(APPLICATION_ID, mobile)
            }.onSuccess { challenge ->
                if (generation != phoneChallengeGeneration) return@onSuccess
                mutableState.update {
                    it.copy(
                        sendingCode = false,
                        phoneChallengeId = challenge.challengeId,
                        resendAfterSeconds = challenge.resendAfterSeconds,
                        codeSentToMaskedMobile = challenge.maskedMobile ?: maskMobile(mobile),
                        error = null
                    )
                }
                while (generation == phoneChallengeGeneration &&
                    mutableState.value.resendAfterSeconds > 0) {
                    delay(1_000)
                    mutableState.update {
                        it.copy(resendAfterSeconds = (it.resendAfterSeconds - 1).coerceAtLeast(0))
                    }
                }
            }.onFailure { error ->
                if (generation != phoneChallengeGeneration) return@onFailure
                logSafeFailure("phone-challenge", error)
                mutableState.update { it.copy(sendingCode = false, error = message(error)) }
            }
        }
    }

    fun clearPhoneChallenge() {
        phoneChallengeGeneration += 1
        mutableState.update {
            it.copy(
                phoneChallengeId = null,
                sendingCode = false,
                resendAfterSeconds = 0,
                codeSentToMaskedMobile = null,
                error = null
            )
        }
    }

    fun authorize(code: String, onAuthorized: () -> Unit) {
        val authorization = mutableState.value.authorization ?: return
        val challengeId = mutableState.value.phoneChallengeId ?: run {
            mutableState.update { it.copy(error = "请先获取短信验证码") }
            return
        }
        if (code.length < 4 || mutableState.value.authorizing) return
        mutableState.update { it.copy(authorizing = true, error = null) }
        viewModelScope.launch {
            runCatching {
                authorizations.grant(authorization, challengeId, code)
                // Granting the application authorization publishes an event that binds the
                // YShop account in Commerce. Calling bindFood immediately races that consumer:
                // both requests resolve the same external identity and one can time out waiting
                // for YShop's per-account lock. Wait for the event-driven binding first and only
                // use the direct call as a recovery path when the event was not processed.
                synchronizeFoodBinding(
                    isReady = {
                        runCatching { foodEntryStatusWithTransientRetry().state == "READY" }
                            .getOrDefault(false)
                    },
                    bindDirectly = { commerce.bindFood() }
                )
            }.onSuccess {
                mutableState.update { it.copy(authorizing = false, authorized = true, error = null) }
                onAuthorized()
            }.onFailure { error ->
                logSafeFailure("bind", error)
                mutableState.update { it.copy(authorizing = false, error = message(error)) }
            }
        }
    }

    fun requestHandoff(onReady: (FoodHandoffDto) -> Unit) {
        viewModelScope.launch {
            runCatching { commerce.issueFoodHandoff() }
                .onSuccess { handoff ->
                    mutableState.update { it.copy(handoff = handoff, error = null) }
                    onReady(handoff)
                }
                .onFailure { error -> mutableState.update { it.copy(error = message(error)) } }
        }
    }

    fun requestLocationContext(
        onReady: (FoodLocationContextDto) -> Unit,
        onUnavailable: (FoodLocationContextStatus) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                val cached = locations.cachedLocation()?.takeIf { it.isFresh() }
                val location = cached ?: locations.locate().getOrThrow()
                commerce.createFoodLocationContext(location)
            }.onSuccess(onReady).onFailure { error ->
                val status = when ((error as? AiAgentApiException)?.code) {
                    "NETWORK_UNAVAILABLE" -> FoodLocationContextStatus.NETWORK_UNAVAILABLE
                    "NOT_AUTHENTICATED", "COMMERCE_LOCATION_SCOPE_REQUIRED" ->
                        FoodLocationContextStatus.AUTHORIZATION_REQUIRED
                    else -> FoodLocationContextStatus.LOCATION_FAILED
                }
                onUnavailable(status)
            }
        }
    }

    fun requestWalletBalance(
        onReady: (availableAmountCent: Long, currency: String) -> Unit,
        onUnavailable: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching { finance.wallet() }
                .onSuccess { wallet -> onReady(wallet.availableAmountCent, wallet.currency) }
                .onFailure { error ->
                    logSafeFailure("wallet-balance", error)
                    onUnavailable((error as? IdentityApiException)?.code
                        ?: (error as? AiAgentApiException)?.code
                        ?: "WALLET_BALANCE_UNAVAILABLE")
                }
        }
    }

    private suspend fun foodEntryStatusWithTransientRetry() = try {
        commerce.foodEntryStatus()
    } catch (error: Throwable) {
        if (!isTransient(error)) throw error
        delay(250)
        commerce.foodEntryStatus()
    }

    private fun isTransient(error: Throwable): Boolean {
        val identity = error as? IdentityApiException
        val commerceError = error as? AiAgentApiException
        return identity?.code == "NETWORK_UNAVAILABLE" ||
            commerceError?.code == "NETWORK_UNAVAILABLE" ||
            (identity?.status?.let { it >= 500 } == true) ||
            (commerceError?.status?.let { it >= 500 } == true)
    }

    private fun message(error: Throwable): String {
        val identity = error as? IdentityApiException
        val code = identity?.code ?: (error as? AiAgentApiException)?.code
        return when (code) {
            "NETWORK_UNAVAILABLE" -> "网络连接失败，请稍后重试"
            "NOT_AUTHENTICATED" -> "登录状态已失效，请重新登录"
            "TOKEN_INVALID", "INVALID_ACCESS_TOKEN" -> "登录状态已失效，请重新登录"
            "MOBILE_INVALID" -> "请输入正确的中国大陆手机号"
            "SMS_CODE_INVALID", "SMS_INVALID" -> "验证码错误，请重新输入"
            "SMS_CODE_EXPIRED", "SMS_EXPIRED" -> "验证码已过期，请重新获取"
            "SMS_CODE_LOCKED", "SMS_LOCKED" -> "验证码错误次数过多，请稍后重试"
            "SMS_RESEND_TOO_SOON" -> "验证码发送过于频繁，请稍后重试"
            "SMS_RATE_LIMITED", "AUTH_RATE_LIMITED" -> "验证码请求过于频繁，请稍后重试"
            "SMS_DELIVERY_UNAVAILABLE" -> "短信服务暂不可用，请稍后重试"
            "COMMERCE_YSHOP_UNAVAILABLE" -> "意向点餐服务暂不可用，请稍后重试"
            "YSHOP_ACCOUNT_ALREADY_BOUND" -> "该意向点餐账号已绑定其他 MiniPay，请先在原账号解除绑定"
            "YSHOP_ACCOUNT_DISABLED" -> "该意向点餐账号已停用，请联系平台处理"
            "YSHOP_PHONE_ACCOUNT_CONFLICT" -> "该手机号对应多个意向点餐账号，请联系平台处理"
            "APPLICATION_PHONE_VERIFICATION_REQUIRED", "PHONE_UPGRADE_REQUIRED" ->
                "请先完成手机号验证"
            else -> if (identity?.status == 404) {
                "验证码服务正在更新，请稍后重试"
            } else {
                "账号绑定暂时失败，请稍后重试"
            }
        }
    }

    private fun maskMobile(mobile: String): String = when {
        mobile.length == 11 -> mobile.take(3) + "****" + mobile.takeLast(4)
        else -> mobile
    }

    private fun logSafeFailure(stage: String, error: Throwable) {
        val identity = error as? IdentityApiException
        val commerceError = error as? AiAgentApiException
        Log.w(TAG, "food integration failed stage=$stage code=" +
            "${identity?.code ?: commerceError?.code ?: "UNKNOWN"} requestId=" +
            "${identity?.requestId ?: commerceError?.requestId ?: "-"}")
    }

    private companion object {
        const val APPLICATION_ID = "yshop-food"
        const val TAG = "MiniPayFood"
    }
}

internal suspend fun synchronizeFoodBinding(
    isReady: suspend () -> Boolean,
    bindDirectly: suspend () -> Unit,
    attempts: Int = 30,
    retryDelayMillis: Long = 300,
    pause: suspend (Long) -> Unit = { delay(it) }
) {
    repeat(attempts) { attempt ->
        if (isReady()) return
        if (attempt < attempts - 1) pause(retryDelayMillis)
    }
    bindDirectly()
}
