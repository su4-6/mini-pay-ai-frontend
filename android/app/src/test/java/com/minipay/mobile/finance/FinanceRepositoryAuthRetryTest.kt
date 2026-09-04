package com.minipay.mobile.finance

import com.minipay.mobile.auth.AuthorizationCodeResponse
import com.minipay.mobile.auth.AuthRepository
import com.minipay.mobile.auth.CodeChallengeResponse
import com.minipay.mobile.auth.IdentityService
import com.minipay.mobile.auth.OAuthTokenResponse
import com.minipay.mobile.auth.SendCodeRequest
import com.minipay.mobile.auth.SessionStorage
import com.minipay.mobile.auth.VerifyCodeRequest
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

class FinanceRepositoryAuthRetryTest {
    @Test
    fun collectionReadRefreshesOnceAfterUnauthorizedResponse() = runTest {
        val authorizations = CopyOnWriteArrayList<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            authorizations += request.header("Authorization").orEmpty()
            val attempt = authorizations.size
            val body = if (attempt == 1) "" else SUCCESS_PAGE
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(if (attempt == 1) 401 else 200)
                .message(if (attempt == 1) "Unauthorized" else "OK")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        val storage = RetrySessionStorage()
        val auth = AuthRepository(RotatingIdentityService(), storage)
        val repository = FinanceRepository(client, Json { ignoreUnknownKeys = true }, auth, storage)

        val page = repository.collectionRecords(
            CollectionRecordType.ALL,
            CollectionRecordPeriod.TODAY
        )

        assertEquals(0L, page.total)
        assertEquals(listOf("Bearer access-token-1", "Bearer access-token-2"), authorizations)
    }

    private class RotatingIdentityService : IdentityService {
        private var calls = 0
        override suspend fun refresh(refreshToken: String): OAuthTokenResponse {
            calls += 1
            return OAuthTokenResponse(
                accessToken = "access-token-$calls",
                tokenType = "Bearer",
                expiresIn = 600,
                refreshToken = "refresh-token-$calls"
            )
        }
        override suspend fun revoke(refreshToken: String) = Unit
        override suspend fun sendCode(request: SendCodeRequest): CodeChallengeResponse = error("unused")
        override suspend fun verifyCode(request: VerifyCodeRequest): AuthorizationCodeResponse = error("unused")
        override suspend fun exchangeCode(code: String, verifier: String): OAuthTokenResponse = error("unused")
    }

    private class RetrySessionStorage : SessionStorage {
        private var refresh: String? = "refresh-token-0"
        override fun refreshToken(): String? = refresh
        override fun saveRefreshToken(value: String) { refresh = value }
        override fun userId(): String = "user-a"
        override fun saveUserId(value: String) = Unit
        override fun mobile(): String? = null
        override fun saveMobile(value: String) = Unit
        override fun payPasswordSet(): Boolean = false
        override fun savePayPasswordSet(value: Boolean) = Unit
        override fun onboardingRequired(): Boolean = false
        override fun saveOnboardingState(payPasswordSet: Boolean, onboardingRequired: Boolean) = Unit
        override fun clearSession() { refresh = null }
        override fun deviceId(): String = "device-a"
        override fun savePkceVerifier(value: String) = Unit
        override fun pkceVerifier(): String? = null
        override fun clearPkceVerifier() = Unit
    }

    private companion object {
        const val SUCCESS_PAGE = """{"items":[],"page":1,"size":20,"total":0,"type":"ALL","period":"TODAY","from":"2026-08-09T16:00:00Z","to":"2026-08-10T16:00:00Z","summary":{}}"""
    }
}
