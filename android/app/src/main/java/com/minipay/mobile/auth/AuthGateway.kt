package com.minipay.mobile.auth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface AuthGateway {
    /** Emitted whenever credentials are cleared outside the explicit UI logout flow. */
    val sessionInvalidations: Flow<Unit>
        get() = emptyFlow()

    suspend fun sendCode(mobile: String): CodeChallengeResponse

    suspend fun verifyAndLogin(
        challengeId: String,
        code: String
    ): AuthorizationCodeResponse

    suspend fun restoreSession(): RestoredSession?

    suspend fun logout()

    /** Clears local credentials and user-owned caches without making another revoke request. */
    suspend fun invalidateLocalSession() = logout()

    fun cancelChallenge()

    fun saveCurrentMobile(mobile: String) = Unit
}

data class RestoredSession(
    val userId: String = "",
    val payPasswordSet: Boolean,
    val onboardingRequired: Boolean
)
