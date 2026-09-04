package com.minipay.mobile.auth

import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class AuthRepositoryTest {
    @Test
    fun concurrentAccessTokenRequestsUseOneRefreshCall() = runTest {
        val service = FakeIdentityService()
        val storage = FakeSessionStorage(refresh = "refresh-token")
        val repository = AuthRepository(service, storage)

        val tokens = List(12) {
            async { repository.validAccessToken() }
        }.awaitAll()

        assertTrue(tokens.all { it == service.accessToken })
        assertEquals(1, service.refreshCalls.get())
        assertEquals("rotated-refresh-token", storage.refreshToken())
    }

    @Test
    fun restoredSessionRetainsPaymentPasswordState() = runTest {
        val storage = FakeSessionStorage(
            refresh = "refresh-token",
            paymentPasswordSet = true
        )
        val repository = AuthRepository(
            FakeIdentityService(payPasswordSet = true, onboardingCompleted = true),
            storage
        )

        assertEquals(
            RestoredSession(USER_A, payPasswordSet = true, onboardingRequired = false),
            repository.restoreSession()
        )
    }

    @Test
    fun onboardingCompletionRefreshesClaimsAndPersistsCompletedState() = runTest {
        val service = FakeIdentityService(onboardingCompleted = true)
        val storage = FakeSessionStorage(
            refresh = "refresh-token",
            paymentPasswordSet = false,
            requiresOnboarding = true
        )
        val repository = AuthRepository(service, storage)

        repository.refreshAfterOnboarding()

        assertEquals(1, service.refreshCalls.get())
        assertFalse(storage.payPasswordSet())
        assertEquals(false, storage.onboardingRequired())
        assertEquals("rotated-refresh-token", storage.refreshToken())
    }

    @Test
    fun clearingSessionPublishesNoCurrentUser() {
        val storage = FakeSessionStorage(user = "user-a")
        val repository = AuthRepository(FakeIdentityService(), storage)

        assertEquals("user-a", repository.currentUserId.value)

        repository.clearSession()

        assertEquals(null, repository.currentUserId.value)
    }

    @Test
    fun onlineRefreshRepairsStaleLocalOnboardingStateFromJwt() = runTest {
        val storage = FakeSessionStorage(
            refresh = "refresh-token",
            requiresOnboarding = false,
            user = USER_A
        )
        val repository = AuthRepository(
            FakeIdentityService(userId = USER_B, onboardingCompleted = false),
            storage
        )

        val restored = repository.restoreSession()

        assertEquals(RestoredSession(USER_B, false, true), restored)
        assertEquals(USER_B, storage.userId())
        assertTrue(storage.onboardingRequired())
    }

    @Test
    fun localInvalidationClearsCredentialsButRetainsDeviceIdentity() = runTest {
        val storage = FakeSessionStorage(refresh = "refresh-token", user = "user-a")
        val repository = AuthRepository(FakeIdentityService(), storage)

        repository.invalidateLocalSession()

        assertEquals(null, storage.refreshToken())
        assertEquals(null, repository.currentUserId.value)
        assertEquals("device-id", storage.deviceId())
    }

    @Test
    fun temporaryRefreshFailureKeepsSessionAndReturnsNoAccessToken() = runTest {
        val storage = FakeSessionStorage(refresh = "refresh-token", user = USER_A)
        val repository = AuthRepository(
            FakeIdentityService(refreshFailure = IdentityApiException("NETWORK_UNAVAILABLE")),
            storage
        )

        assertEquals(null, repository.validAccessToken())
        assertEquals("refresh-token", storage.refreshToken())
        assertEquals(USER_A, repository.currentUserId.value)
    }

    private class FakeIdentityService(
        userId: String = USER_A,
        payPasswordSet: Boolean = false,
        onboardingCompleted: Boolean = true,
        private val refreshFailure: Throwable? = null
    ) : IdentityService {
        override suspend fun revoke(refreshToken: String) = Unit
        val refreshCalls = AtomicInteger()
        val accessToken = jwt(userId, payPasswordSet, onboardingCompleted)

        override suspend fun sendCode(request: SendCodeRequest): CodeChallengeResponse =
            error("not used")

        override suspend fun verifyCode(request: VerifyCodeRequest): AuthorizationCodeResponse =
            error("not used")

        override suspend fun exchangeCode(code: String, verifier: String): OAuthTokenResponse =
            error("not used")

        override suspend fun refresh(refreshToken: String): OAuthTokenResponse {
            refreshCalls.incrementAndGet()
            refreshFailure?.let { throw it }
            return OAuthTokenResponse(
                accessToken = accessToken,
                tokenType = "Bearer",
                expiresIn = 600,
                refreshToken = "rotated-refresh-token"
            )
        }
    }

    private companion object {
        const val USER_A = "018f0f5d-52c7-7b8d-9f22-6f858e711001"
        const val USER_B = "018f0f5d-52c7-7b8d-9f22-6f858e711002"

        fun jwt(userId: String, payPasswordSet: Boolean, onboardingCompleted: Boolean): String {
            val encoder = Base64.getUrlEncoder().withoutPadding()
            val header = encoder.encodeToString("{\"alg\":\"none\"}".toByteArray())
            val payload = encoder.encodeToString((
                "{\"user_id\":\"$userId\",\"pay_password_set\":$payPasswordSet," +
                    "\"onboarding_completed\":$onboardingCompleted}"
                ).toByteArray()
            )
            return "$header.$payload.signature"
        }
    }
}

private class FakeSessionStorage(
    private var refresh: String? = null,
    private var paymentPasswordSet: Boolean = false,
    private var requiresOnboarding: Boolean = false,
    private var user: String? = null
) : SessionStorage {
    private var verifier: String? = null

    override fun refreshToken(): String? = refresh
    override fun userId(): String? = user
    override fun saveUserId(value: String) { user = value }
    override fun saveRefreshToken(value: String) {
        refresh = value
    }

    override fun payPasswordSet(): Boolean = paymentPasswordSet
    override fun savePayPasswordSet(value: Boolean) {
        paymentPasswordSet = value
    }
    override fun onboardingRequired(): Boolean = requiresOnboarding
    override fun saveOnboardingState(payPasswordSet: Boolean, onboardingRequired: Boolean) {
        paymentPasswordSet = payPasswordSet
        requiresOnboarding = onboardingRequired
    }

    override fun clearSession() {
        refresh = null
        user = null
        verifier = null
        paymentPasswordSet = false
        requiresOnboarding = false
    }

    override fun deviceId(): String = "device-id"
    override fun savePkceVerifier(value: String) {
        verifier = value
    }

    override fun pkceVerifier(): String? = verifier
    override fun clearPkceVerifier() {
        verifier = null
    }
}
