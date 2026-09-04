package com.minipay.mobile.auth

import com.minipay.mobile.BuildConfig
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Singleton
class IdentityApi @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json
) : IdentityService {
    private val baseUrl = BuildConfig.IDENTITY_BASE_URL.trimEnd('/')

    override suspend fun sendCode(request: SendCodeRequest): CodeChallengeResponse =
        postJson("/api/v1/auth/consumer/code/send", request)

    override suspend fun verifyCode(request: VerifyCodeRequest): AuthorizationCodeResponse =
        postJson("/api/v1/auth/consumer/code/verify", request)

    override suspend fun exchangeCode(code: String, verifier: String): OAuthTokenResponse {
        val body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("client_id", BuildConfig.OAUTH_CLIENT_ID)
            .add("redirect_uri", BuildConfig.OAUTH_REDIRECT_URI)
            .add("code", code)
            .add("code_verifier", verifier)
            .build()
        return execute(Request.Builder().url(url("/oauth2/token")).post(body).build())
    }

    override suspend fun refresh(refreshToken: String): OAuthTokenResponse {
        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("client_id", BuildConfig.OAUTH_CLIENT_ID)
            .add("refresh_token", refreshToken)
            .build()
        return execute(Request.Builder().url(url("/oauth2/token")).post(body).build())
    }

    override suspend fun revoke(refreshToken: String) {
        val body = FormBody.Builder()
            .add("client_id", BuildConfig.OAUTH_CLIENT_ID)
            .add("token", refreshToken)
            .add("token_type_hint", "refresh_token")
            .build()
        executeEmpty(Request.Builder().url(url("/oauth2/revoke")).post(body).build())
    }

    private suspend fun executeEmpty(request: Request) = withContext(Dispatchers.IO) {
        val response = try {
            client.newCall(request).execute()
        } catch (exception: IOException) {
            throw IdentityApiException("NETWORK_UNAVAILABLE", null, null, exception)
        }
        response.use {
            if (!it.isSuccessful) throw IdentityApiException("LOGOUT_REVOKE_FAILED", null, it.code)
        }
    }

    private suspend inline fun <reified T, reified R> postJson(path: String, body: T): R {
        val requestBody = json.encodeToString(body)
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url(path))
            .header("X-Request-Id", UUID.randomUUID().toString())
            .post(requestBody)
            .build()
        return execute(request)
    }

    private suspend inline fun <reified T> execute(request: Request): T = withContext(Dispatchers.IO) {
        val response = try {
            client.newCall(request).execute()
        } catch (exception: IOException) {
            throw IdentityApiException("NETWORK_UNAVAILABLE", null, null, exception)
        }
        response.use {
            val responseBody = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                val contentType = it.header("Content-Type").orEmpty()
                if (contentType.contains("application/problem+json")) {
                    val problem = runCatching { json.decodeFromString<ProblemDetails>(responseBody) }
                        .getOrNull()
                    throw IdentityApiException(
                        problem?.code ?: "REQUEST_FAILED",
                        problem?.requestId,
                        it.code
                    )
                }
                val oauth = runCatching { json.decodeFromString<OAuthProblem>(responseBody) }
                    .getOrNull()
                throw IdentityApiException(oauth?.error ?: "REQUEST_FAILED", null, it.code)
            }
            runCatching { json.decodeFromString<T>(responseBody) }
                .getOrElse { cause ->
                    throw IdentityApiException("INVALID_RESPONSE", null, it.code, cause)
                }
        }
    }

    private fun url(path: String): String {
        if (baseUrl.isBlank()) {
            throw IdentityApiException("IDENTITY_NOT_CONFIGURED")
        }
        return "$baseUrl$path"
    }
}

class IdentityApiException(
    val code: String,
    val requestId: String? = null,
    val status: Int? = null,
    cause: Throwable? = null,
    val retryAfterSeconds: Long? = null
) : RuntimeException(code, cause)
