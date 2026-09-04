package com.minipay.mobile.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SendCodeRequest(
    val mobile: String,
    val purpose: String = "LOGIN"
)

@Serializable
data class CodeChallengeResponse(
    val challengeId: String,
    val maskedMobile: String,
    val expiresAt: String,
    val resendAfterSeconds: Long
)

@Serializable
data class VerifyCodeRequest(
    val challengeId: String,
    val code: String,
    val clientId: String,
    val redirectUri: String,
    val codeChallenge: String,
    val codeChallengeMethod: String = "S256",
    val deviceId: String
)

@Serializable
data class AuthorizationCodeResponse(
    val authorizationCode: String,
    val expiresAt: String,
    val userId: String = "",
    val payPasswordSet: Boolean,
    val onboardingRequired: Boolean,
    val realNameStatus: String = "UNVERIFIED",
    val realNameVerified: Boolean = false
)

@Serializable
data class OAuthTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("refresh_token") val refreshToken: String? = null,
    val scope: String? = null
)

@Serializable
data class ProblemDetails(
    val status: Int? = null,
    val code: String? = null,
    val requestId: String? = null,
    val retryAfterSeconds: Long? = null
)

@Serializable
data class OAuthProblem(
    val error: String? = null
)

data class PkcePair(
    val verifier: String,
    val challenge: String
)

enum class CodeDeliveryStatus {
    SENDING,
    SENT,
    FAILED
}

sealed interface AuthUiState {
    data object CheckingSession : AuthUiState

    data class PhoneEntry(
        val mobile: String = "",
        val agreementAccepted: Boolean = false,
        val submitting: Boolean = false,
        val errorMessage: String? = null
    ) : AuthUiState

    data class CodeEntry(
        val mobile: String,
        val maskedMobile: String,
        val challengeId: String?,
        val deliveryStatus: CodeDeliveryStatus = CodeDeliveryStatus.SENT,
        val code: String = "",
        val secondsUntilResend: Long,
        val submitting: Boolean = false,
        val errorMessage: String? = null
    ) : AuthUiState

    data class Session(
        val userId: String,
        val payPasswordSet: Boolean,
        val onboardingRequired: Boolean,
        val sessionKey: Long = 0L
    ) : AuthUiState
}
