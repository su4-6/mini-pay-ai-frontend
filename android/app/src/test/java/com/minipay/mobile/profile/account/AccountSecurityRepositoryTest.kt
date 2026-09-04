package com.minipay.mobile.profile.account

import com.minipay.mobile.auth.AuthorizationCodeResponse
import com.minipay.mobile.auth.AuthRepository
import com.minipay.mobile.auth.CodeChallengeResponse
import com.minipay.mobile.auth.IdentityService
import com.minipay.mobile.auth.IdentityApiException
import com.minipay.mobile.auth.OAuthTokenResponse
import com.minipay.mobile.auth.SendCodeRequest
import com.minipay.mobile.auth.SessionStorage
import com.minipay.mobile.auth.VerifyCodeRequest
import java.util.Base64
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class AccountSecurityRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: AccountSecurityRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val storage = RepositorySessionStorage()
        repository = AccountSecurityRepository(
            client = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true; explicitNulls = false },
            auth = AuthRepository(RepositoryIdentityService(), storage),
            sessionStorage = storage,
            baseUrl = server.url("/")
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun overviewGetCarriesRequestIdButNoIdempotencyKey() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"maskedMobile":"138****8000","maskedEmail":null,"paymentPasswordSet":true}"""
            )
        )

        val overview = repository.loadOverview()
        val request = server.takeRequest()

        assertEquals("138****8000", overview.maskedMobile)
        assertEquals("/api/v1/users/me/account-security", request.path)
        assertEquals("Bearer $ACCESS_TOKEN", request.getHeader("Authorization"))
        assertNotNull(request.getHeader("X-Request-Id"))
        assertNull(request.getHeader("Idempotency-Key"))
    }

    @Test
    fun overviewFallsBackToExistingCapabilitiesContractForPaymentPasswordState() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"maskedMobile":"138****8000","maskedEmail":"w***@example.com"}"""
            )
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"onboardingCompleted":true,"realNameStatus":"VERIFIED","realNameVerified":true,"payPasswordSet":false}"""
            )
        )

        val overview = repository.loadOverview()
        val overviewRequest = server.takeRequest()
        val capabilitiesRequest = server.takeRequest()

        assertEquals("138****8000", overview.maskedMobile)
        assertEquals("w***@example.com", overview.maskedEmail)
        assertEquals(false, overview.paymentPasswordSet)
        assertEquals("/api/v1/users/me/account-security", overviewRequest.path)
        assertEquals("/api/v1/users/me/capabilities", capabilitiesRequest.path)
        assertNotNull(capabilitiesRequest.getHeader("X-Request-Id"))
        assertNull(capabilitiesRequest.getHeader("Idempotency-Key"))
    }

    @Test
    fun phoneChallengeCarriesStableIdempotencyKeyAndExpectedBody() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"challengeId":"c1","maskedMobile":"139****9000","expiresAt":"2026-08-08T12:00:00Z","resendAfterSeconds":60}"""
            )
        )

        repository.requestPhoneChange("13900139000", "attempt-key")
        val request = server.takeRequest()

        assertEquals("POST", request.method)
        assertEquals("attempt-key", request.getHeader("Idempotency-Key"))
        assertEquals("/api/v1/users/me/phone-change-challenges", request.path)
        assertEquals("{\"mobile\":\"13900139000\"}", request.body.readUtf8())
    }

    @Test
    fun paymentChallengeBindsDeviceAndParsesProblemRetryMetadata() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(429).setBody(
                """{"status":429,"code":"SMS_LOCKED","requestId":"req-42","retryAfterSeconds":600}"""
            )
        )

        try {
            repository.requestPaymentPasswordChallenge("payment-attempt")
            fail("Expected IdentityApiException")
        } catch (error: IdentityApiException) {
            assertEquals("SMS_LOCKED", error.code)
            assertEquals("req-42", error.requestId)
            assertEquals(600L, error.retryAfterSeconds)
        }
        val request = server.takeRequest()
        assertEquals("payment-attempt", request.getHeader("Idempotency-Key"))
        assertEquals(
            "{\"mobile\":\"13800138000\",\"deviceId\":\"device-id\"}",
            request.body.readUtf8()
        )
    }

    @Test
    fun paymentVerificationAndChangeCarryOnlyBoundInMemorySecrets() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"verificationToken":"one-time-token","expiresAt":"2026-08-08T12:05:00Z"}"""
            )
        )
        server.enqueue(MockResponse().setResponseCode(204))

        val verification = repository.verifyPaymentPasswordChallenge(
            "challenge-1", "123456", "verify-attempt-key"
        )
        repository.changePaymentPassword(
            verification.verificationToken, "654321", "change-attempt-key"
        )

        val verifyRequest = server.takeRequest()
        val changeRequest = server.takeRequest()
        assertEquals(
            "/api/v1/users/me/payment-password-change-challenges/challenge-1/verifications",
            verifyRequest.path
        )
        assertEquals("verify-attempt-key", verifyRequest.getHeader("Idempotency-Key"))
        assertEquals(
            "{\"code\":\"123456\",\"deviceId\":\"device-id\"}",
            verifyRequest.body.readUtf8()
        )
        assertEquals("/api/v1/users/me/payment-password-changes", changeRequest.path)
        assertEquals("change-attempt-key", changeRequest.getHeader("Idempotency-Key"))
        assertEquals(
            "{\"verificationToken\":\"one-time-token\",\"newPassword\":\"654321\",\"deviceId\":\"device-id\"}",
            changeRequest.body.readUtf8()
        )
    }

    private class RepositoryIdentityService : IdentityService {
        override suspend fun refresh(refreshToken: String) = OAuthTokenResponse(
            accessToken = ACCESS_TOKEN,
            tokenType = "Bearer",
            expiresIn = 600,
            refreshToken = "refresh-token"
        )
        override suspend fun revoke(refreshToken: String) = Unit
        override suspend fun sendCode(request: SendCodeRequest): CodeChallengeResponse = error("not used")
        override suspend fun verifyCode(request: VerifyCodeRequest): AuthorizationCodeResponse = error("not used")
        override suspend fun exchangeCode(code: String, verifier: String): OAuthTokenResponse = error("not used")
    }

    private companion object {
        val ACCESS_TOKEN: String = run {
            val encoder = Base64.getUrlEncoder().withoutPadding()
            val header = encoder.encodeToString("{\"alg\":\"none\"}".toByteArray())
            val payload = encoder.encodeToString(
                (
                    "{\"user_id\":\"018f0f5d-52c7-7b8d-9f22-6f858e711001\"," +
                        "\"pay_password_set\":true,\"onboarding_completed\":true}"
                ).toByteArray()
            )
            "$header.$payload.signature"
        }
    }

    private class RepositorySessionStorage : SessionStorage {
        private var refresh: String? = "refresh-token"
        override fun refreshToken(): String? = refresh
        override fun mobile(): String = "13800138000"
        override fun saveRefreshToken(value: String) { refresh = value }
        override fun payPasswordSet(): Boolean = true
        override fun savePayPasswordSet(value: Boolean) = Unit
        override fun onboardingRequired(): Boolean = false
        override fun saveOnboardingState(payPasswordSet: Boolean, onboardingRequired: Boolean) = Unit
        override fun clearSession() { refresh = null }
        override fun deviceId(): String = "device-id"
        override fun savePkceVerifier(value: String) = Unit
        override fun pkceVerifier(): String? = null
        override fun clearPkceVerifier() = Unit
    }
}
