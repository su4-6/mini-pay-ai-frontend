package com.minipay.mobile.auth

import java.util.Base64
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class AccessTokenPayload(
    @SerialName("user_id") val userId: String,
    @SerialName("pay_password_set") val payPasswordSet: Boolean,
    @SerialName("onboarding_completed") val onboardingCompleted: Boolean
)

internal data class SessionClaims(
    val userId: String,
    val payPasswordSet: Boolean,
    val onboardingRequired: Boolean
)

internal object AccessTokenClaims {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(accessToken: String): SessionClaims {
        val parts = accessToken.split('.')
        if (parts.size != 3) throw IdentityApiException("INVALID_TOKEN_RESPONSE")
        val payload = runCatching {
            val decoded = Base64.getUrlDecoder().decode(parts[1])
            json.decodeFromString<AccessTokenPayload>(decoded.toString(Charsets.UTF_8))
        }.getOrElse { cause ->
            throw IdentityApiException("INVALID_TOKEN_RESPONSE", cause = cause)
        }
        runCatching { UUID.fromString(payload.userId) }.getOrElse { cause ->
            throw IdentityApiException("INVALID_TOKEN_RESPONSE", cause = cause)
        }
        return SessionClaims(
            userId = payload.userId,
            payPasswordSet = payload.payPasswordSet,
            onboardingRequired = !payload.onboardingCompleted
        )
    }
}
