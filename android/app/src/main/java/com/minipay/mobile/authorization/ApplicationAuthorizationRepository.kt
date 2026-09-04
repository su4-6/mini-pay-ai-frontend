package com.minipay.mobile.authorization

import com.minipay.mobile.BuildConfig
import com.minipay.mobile.auth.AuthRepository
import com.minipay.mobile.auth.IdentityApiException
import com.minipay.mobile.auth.ProblemDetails
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
data class ApplicationAuthorizationDto(
    val authorizationId: String? = null,
    val applicationId: String,
    val displayName: String,
    val developerName: String,
    val iconUrl: String? = null,
    val privacyPolicyUrl: String? = null,
    val termsUrl: String? = null,
    val consentVersion: Int,
    val state: String,
    val grantedScopes: Set<String> = emptySet(),
    val nickname: String,
    val avatarUrl: String? = null,
    val phoneMasked: String? = null,
    val authorizedAt: String? = null,
    val lastUsedAt: String? = null,
    val revokedAt: String? = null
)

@Serializable data class AuthorizationGrantRequest(
    val scopes: Set<String>,
    val consentVersion: Int,
    val phoneChallengeId: String? = null,
    val verificationCode: String? = null
)
@Serializable data class PhoneDisclosureRequest(val mobile: String)
@Serializable data class PhoneDisclosureVerification(val challengeId: String, val code: String)
@Serializable data class VerificationChallengeDto(
    val challengeId: String,
    val expiresAt: String,
    val resendAfterSeconds: Int = 60,
    val maskedMobile: String? = null
)

@Singleton
class ApplicationAuthorizationRepository @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val auth: AuthRepository
) {
    private val baseUrl = BuildConfig.IDENTITY_BASE_URL.trimEnd('/')

    suspend fun list(): List<ApplicationAuthorizationDto> = get(
        "/api/v1/users/me/application-authorizations"
    )

    suspend fun detail(applicationId: String): ApplicationAuthorizationDto = get(
        "/api/v1/users/me/application-authorizations/$applicationId"
    )

    suspend fun grant(
        current: ApplicationAuthorizationDto,
        phoneChallengeId: String,
        verificationCode: String
    ): ApplicationAuthorizationDto {
        val scopes = setOf("profile.basic", "profile.phone", "location.current")
        return write(
            "/api/v1/users/me/application-authorizations/${current.applicationId}",
            "POST", AuthorizationGrantRequest(
                scopes, current.consentVersion, phoneChallengeId, verificationCode)
        )
    }

    suspend fun requestApplicationPhoneChallenge(
        applicationId: String,
        mobile: String
    ): VerificationChallengeDto = write(
        "/api/v1/users/me/application-authorizations/$applicationId/phone-challenges",
        "POST",
        PhoneDisclosureRequest(mobile)
    )

    suspend fun requestPhoneDisclosure(mobile: String): VerificationChallengeDto = write(
        "/api/v1/users/me/phone-disclosure-challenges", "POST",
        PhoneDisclosureRequest(mobile)
    )

    suspend fun verifyPhoneDisclosure(challengeId: String, code: String) {
        writeUnit(
            "/api/v1/users/me/phone-disclosure-verifications", "POST",
            PhoneDisclosureVerification(challengeId, code)
        )
    }

    suspend fun revoke(applicationId: String) {
        writeUnit<Unit>(
            "/api/v1/users/me/application-authorizations/$applicationId", "DELETE", null
        )
    }

    private suspend inline fun <reified T> get(path: String): T = execute(
        Request.Builder().url(url(path)).get()
    )

    private suspend inline fun <reified T, reified B> write(
        path: String, method: String, body: B
    ): T = execute(writeBuilder(path, method, body))

    private suspend inline fun <reified B> writeUnit(path: String, method: String, body: B?) {
        executeUnit(writeBuilder(path, method, body))
    }

    private inline fun <reified B> writeBuilder(path: String, method: String, body: B?): Request.Builder {
        val requestBody = body?.let {
            json.encodeToString(it).toRequestBody(JSON_MEDIA)
        }
        return Request.Builder().url(url(path)).method(method, requestBody)
            .header("Idempotency-Key", UUID.randomUUID().toString())
    }

    private suspend inline fun <reified T> execute(builder: Request.Builder): T =
        withContext(Dispatchers.IO) {
            executeResponse(builder).use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw problem(body, response.code)
                runCatching { json.decodeFromString<T>(body) }
                    .getOrElse { throw IdentityApiException("INVALID_RESPONSE", status = response.code, cause = it) }
            }
        }

    private suspend fun executeUnit(builder: Request.Builder) = withContext(Dispatchers.IO) {
        executeResponse(builder).use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw problem(body, response.code)
        }
    }

    private suspend fun executeResponse(builder: Request.Builder): okhttp3.Response {
        val token = auth.validAccessToken() ?: throw IdentityApiException("TOKEN_INVALID")
        val request = builder.header("Authorization", "Bearer $token")
            .header("X-Request-Id", UUID.randomUUID().toString()).build()
        return try {
            client.newCall(request).execute()
        } catch (error: IOException) {
            throw IdentityApiException("NETWORK_UNAVAILABLE", cause = error)
        }
    }

    private fun problem(body: String, status: Int): IdentityApiException {
        val details = runCatching { json.decodeFromString<ProblemDetails>(body) }.getOrNull()
        return IdentityApiException(details?.code ?: "REQUEST_FAILED", details?.requestId, status)
    }

    private fun url(path: String): String {
        if (baseUrl.isBlank()) throw IdentityApiException("IDENTITY_NOT_CONFIGURED")
        return "$baseUrl$path"
    }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
