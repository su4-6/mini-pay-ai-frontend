package com.minipay.mobile.profile.account

import com.minipay.mobile.BuildConfig
import com.minipay.mobile.auth.AuthRepository
import com.minipay.mobile.auth.IdentityApiException
import com.minipay.mobile.auth.ProblemDetails
import com.minipay.mobile.auth.SessionStorage
import java.io.IOException
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
private data class AccountOverviewResponse(
    val maskedMobile: String,
    val maskedEmail: String? = null,
    val paymentPasswordSet: Boolean? = null
)

@Serializable
private data class AccountCapabilitiesResponse(
    val payPasswordSet: Boolean
)

@Serializable
private data class ChallengeResponse(
    val challengeId: String,
    val maskedMobile: String? = null,
    val maskedEmail: String? = null,
    val maskedTarget: String? = null,
    val expiresAt: String,
    val resendAfterSeconds: Long
)

@Serializable private data class PhoneRequest(val mobile: String)
@Serializable private data class EmailRequest(val email: String)
@Serializable private data class CodeRequest(val challengeId: String, val code: String)
@Serializable private data class CurrentMobileDeviceRequest(
    val mobile: String,
    val deviceId: String
)
@Serializable private data class PaymentCodeRequest(
    val code: String,
    val deviceId: String
)
@Serializable private data class PaymentPasswordRequest(
    val verificationToken: String,
    @SerialName("newPassword") val password: String,
    val deviceId: String
)
@Serializable private data class VerificationResponse(
    val verificationToken: String,
    val expiresAt: String
)

@Singleton
class AccountSecurityRepository private constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val auth: AuthRepository,
    private val sessionStorage: SessionStorage,
    private val baseUrl: String
) : AccountSecurityGateway {
    @Inject
    constructor(
        client: OkHttpClient,
        json: Json,
        auth: AuthRepository,
        sessionStorage: SessionStorage
    ) : this(
        client,
        json,
        auth,
        sessionStorage,
        BuildConfig.IDENTITY_BASE_URL.trimEnd('/')
    )

    internal constructor(
        client: OkHttpClient,
        json: Json,
        auth: AuthRepository,
        sessionStorage: SessionStorage,
        baseUrl: HttpUrl
    ) : this(client, json, auth, sessionStorage, baseUrl.toString().trimEnd('/'))

    override suspend fun loadOverview(): AccountSecurityOverview {
        val response = read<AccountOverviewResponse>("/api/v1/users/me/account-security")
        val paymentPasswordSet = response.paymentPasswordSet
            ?: read<AccountCapabilitiesResponse>("/api/v1/users/me/capabilities").payPasswordSet
        return AccountSecurityOverview(
            maskedMobile = response.maskedMobile,
            maskedEmail = response.maskedEmail,
            paymentPasswordSet = paymentPasswordSet
        )
    }

    override suspend fun requestPhoneChange(mobile: String, idempotencyKey: String) =
        challenge(
            "/api/v1/users/me/phone-change-challenges",
            "POST",
            PhoneRequest(mobile),
            idempotencyKey
        )

    override suspend fun confirmPhoneChange(
        challengeId: String,
        code: String,
        idempotencyKey: String
    ) {
        write<Unit>("/api/v1/users/me/phone", "PUT", CodeRequest(challengeId, code), idempotencyKey)
    }

    override suspend fun requestEmailVerification(email: String, idempotencyKey: String) =
        challenge(
            "/api/v1/users/me/email-verification-challenges",
            "POST",
            EmailRequest(email),
            idempotencyKey
        )

    override suspend fun confirmEmail(challengeId: String, code: String, idempotencyKey: String) {
        write<Unit>("/api/v1/users/me/email", "PUT", CodeRequest(challengeId, code), idempotencyKey)
    }

    override suspend fun deleteEmail(idempotencyKey: String) {
        write<Unit>("/api/v1/users/me/email", "DELETE", null, idempotencyKey)
    }

    override suspend fun requestPaymentPasswordChallenge(idempotencyKey: String) =
        challenge(
            "/api/v1/users/me/payment-password-change-challenges",
            "POST",
            body = CurrentMobileDeviceRequest(
                mobile = currentMobile()
                    ?: throw IdentityApiException("CURRENT_MOBILE_UNAVAILABLE"),
                deviceId = sessionStorage.deviceId()
            ),
            idempotencyKey = idempotencyKey
        )

    override suspend fun verifyPaymentPasswordChallenge(
        challengeId: String,
        code: String,
        idempotencyKey: String
    ): PaymentPasswordVerification {
        val response = write<VerificationResponse>(
            "/api/v1/users/me/payment-password-change-challenges/$challengeId/verifications",
            "POST",
            PaymentCodeRequest(code, sessionStorage.deviceId()),
            idempotencyKey
        )
        return PaymentPasswordVerification(response.verificationToken, parseInstant(response.expiresAt))
    }

    override suspend fun changePaymentPassword(
        verificationToken: String,
        newPassword: String,
        idempotencyKey: String
    ) {
        write<Unit>(
            "/api/v1/users/me/payment-password-changes",
            "POST",
            PaymentPasswordRequest(verificationToken, newPassword, sessionStorage.deviceId()),
            idempotencyKey
        )
    }

    override fun currentMobile(): String? = auth.currentMobile()

    private suspend fun challenge(
        path: String,
        method: String,
        body: Any,
        idempotencyKey: String
    ): VerificationChallenge {
        val response = write<ChallengeResponse>(path, method, body, idempotencyKey)
        return VerificationChallenge(
            challengeId = response.challengeId,
            maskedTarget = response.maskedTarget
                ?: response.maskedMobile
                ?: response.maskedEmail
                ?: throw IdentityApiException("INVALID_RESPONSE"),
            expiresAt = parseInstant(response.expiresAt),
            resendAfterSeconds = response.resendAfterSeconds.coerceAtLeast(0)
        )
    }

    private suspend inline fun <reified T> read(path: String): T {
        repeat(2) { attempt ->
            try {
                return execute(path, "GET", null, null)
            } catch (error: IdentityApiException) {
                val retryable = error.code == "NETWORK_UNAVAILABLE" ||
                    (error.status != null && error.status >= 500)
                if (!retryable || attempt == 1) throw error
                delay(180L * (attempt + 1))
            }
        }
        error("unreachable")
    }

    private suspend inline fun <reified T> write(
        path: String,
        method: String,
        body: Any?,
        idempotencyKey: String
    ): T = execute(path, method, body, idempotencyKey)

    private suspend inline fun <reified T> execute(
        path: String,
        method: String,
        body: Any?,
        idempotencyKey: String?
    ): T = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank()) throw IdentityApiException("IDENTITY_NOT_CONFIGURED")
        val token = auth.validAccessToken() ?: throw IdentityApiException("TOKEN_INVALID")
        val builder = Request.Builder()
            .url(baseUrl + path)
            .header("Authorization", "Bearer $token")
            .header("X-Request-Id", UUID.randomUUID().toString())
        idempotencyKey?.let { builder.header("Idempotency-Key", it) }
        val requestBody = body?.let {
            val encoded = encodeBody(it)
            encoded.toRequestBody(JSON)
        }
        when (method) {
            "GET" -> builder.get()
            "DELETE" -> if (requestBody == null) builder.delete() else builder.delete(requestBody)
            "PUT" -> builder.put(requireNotNull(requestBody))
            else -> builder.post(requireNotNull(requestBody))
        }
        val response = try {
            client.newCall(builder.build()).execute()
        } catch (error: IOException) {
            throw IdentityApiException("NETWORK_UNAVAILABLE", cause = error)
        }
        response.use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                val problem = runCatching { json.decodeFromString<ProblemDetails>(text) }.getOrNull()
                throw IdentityApiException(
                    problem?.code ?: "REQUEST_FAILED",
                    problem?.requestId,
                    it.code,
                    retryAfterSeconds = problem?.retryAfterSeconds
                )
            }
            if (T::class == Unit::class) return@withContext Unit as T
            runCatching { json.decodeFromString<T>(text) }
                .getOrElse { cause -> throw IdentityApiException("INVALID_RESPONSE", status = it.code, cause = cause) }
        }
    }

    private fun parseInstant(value: String): Instant = runCatching { Instant.parse(value) }
        .getOrElse { throw IdentityApiException("INVALID_RESPONSE", cause = it) }

    private fun encodeBody(body: Any): String = when (body) {
        is PhoneRequest -> json.encodeToString(body)
        is EmailRequest -> json.encodeToString(body)
        is CodeRequest -> json.encodeToString(body)
        is CurrentMobileDeviceRequest -> json.encodeToString(body)
        is PaymentCodeRequest -> json.encodeToString(body)
        is PaymentPasswordRequest -> json.encodeToString(body)
        else -> throw IdentityApiException("INVALID_REQUEST")
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
