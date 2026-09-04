package com.minipay.mobile.auth

import com.minipay.mobile.BuildConfig
import com.minipay.mobile.chat.ChatDatabase
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class AuthRepository @Inject constructor(
    private val identityApi: IdentityService,
    private val sessionStore: SessionStorage,
    private val chatDatabase: ChatDatabase? = null
) : AuthGateway {
    private val refreshMutex = Mutex()
    private val mutableCurrentUserId = MutableStateFlow(sessionStore.userId())
    /** The only in-process source of truth for user-bound UI and cache state. */
    val currentUserId: StateFlow<String?> = mutableCurrentUserId.asStateFlow()
    private val mutableSessionInvalidations = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val sessionInvalidations: Flow<Unit> = mutableSessionInvalidations

    @Volatile
    private var accessToken: String? = null

    @Volatile
    private var accessTokenExpiresAt: Instant? = null

    override suspend fun sendCode(mobile: String): CodeChallengeResponse {
        val pkce = Pkce.generate()
        sessionStore.savePkceVerifier(pkce.verifier)
        return try {
            identityApi.sendCode(SendCodeRequest(mobile))
        } catch (exception: Throwable) {
            sessionStore.clearPkceVerifier()
            throw exception
        }
    }

    override suspend fun verifyAndLogin(
        challengeId: String,
        code: String
    ): AuthorizationCodeResponse {
        val verifier = sessionStore.pkceVerifier()
            ?: throw IdentityApiException("LOGIN_SESSION_EXPIRED")
        val authorization = identityApi.verifyCode(
            VerifyCodeRequest(
                challengeId = challengeId,
                code = code,
                clientId = BuildConfig.OAUTH_CLIENT_ID,
                redirectUri = BuildConfig.OAUTH_REDIRECT_URI,
                codeChallenge = Pkce.challenge(verifier),
                deviceId = sessionStore.deviceId()
            )
        )
        val tokens = identityApi.exchangeCode(authorization.authorizationCode, verifier)
        val session = persistTokens(
            tokens,
            previousRefreshToken = null
        )
        sessionStore.clearPkceVerifier()
        return authorization.copy(
            userId = session.userId,
            payPasswordSet = session.payPasswordSet,
            onboardingRequired = session.onboardingRequired
        )
    }

    override suspend fun restoreSession(): RestoredSession? {
        val refreshToken = sessionStore.refreshToken() ?: return null
        return runCatching {
            refreshMutex.withLock {
                val tokens = identityApi.refresh(refreshToken)
                persistTokens(tokens, refreshToken)
            }
        }.getOrElse { error ->
            // Cold-starting offline is recoverable. Keep the encrypted refresh credential and
            // restore the local session shell; the first read will refresh it once connectivity
            // returns. Only an explicit identity rejection invalidates the local session.
            if ((error as? IdentityApiException)?.code == "NETWORK_UNAVAILABLE") {
                RestoredSession(
                    sessionStore.userId().orEmpty(),
                    sessionStore.payPasswordSet(),
                    sessionStore.onboardingRequired()
                )
            } else {
                clearSession()
                null
            }
        }
    }

    suspend fun validAccessToken(): String? {
        val token = accessToken
        val expiresAt = accessTokenExpiresAt
        if (token != null && expiresAt != null && expiresAt.isAfter(Instant.now().plusSeconds(30))) {
            return token
        }
        val refreshToken = sessionStore.refreshToken() ?: return null
        return refreshMutex.withLock {
            val current = accessToken
            val currentExpiry = accessTokenExpiresAt
            if (current != null
                && currentExpiry != null
                && currentExpiry.isAfter(Instant.now().plusSeconds(30))
            ) {
                current
            } else {
                runCatching {
                    identityApi.refresh(refreshToken).also { persistTokens(it, refreshToken) }.accessToken
                }.getOrElse { error ->
                    // A temporary transport failure must not destroy the only recoverable
                    // session credential or crash background/foreground sync jobs. Report that
                    // no bearer is currently available; the next read retries after recovery.
                    if ((error as? IdentityApiException)?.code == "NETWORK_UNAVAILABLE") return@withLock null
                    clearSession()
                    null
                }
            }
        }
    }

    /** Refreshes even when the locally cached JWT has not reached its client-side expiry. */
    suspend fun forceRefreshAccessToken(): String? = refreshMutex.withLock {
        val refreshToken = sessionStore.refreshToken() ?: return@withLock null
        runCatching {
            identityApi.refresh(refreshToken)
                .also { persistTokens(it, refreshToken) }
                .accessToken
        }.getOrElse { error ->
            if (!isDefinitiveRefreshRejection(error)) throw error
            clearSession()
            null
        }
    }

    override fun cancelChallenge() {
        sessionStore.clearPkceVerifier()
    }

    override fun saveCurrentMobile(mobile: String) {
        if (mobile.matches(Regex("^1[3-9]\\d{9}$"))) sessionStore.saveMobile(mobile)
    }

    fun currentMobile(): String? = sessionStore.mobile()

    override suspend fun logout() {
        val refreshToken = sessionStore.refreshToken()
        try {
            if (refreshToken != null) identityApi.revoke(refreshToken)
        } finally {
            clearSessionAndUserData()
        }
    }

    override suspend fun invalidateLocalSession() {
        clearSessionAndUserData()
    }

    suspend fun refreshAfterOnboarding() {
        refreshClaims()
    }

    suspend fun refreshClaims() {
        val refreshToken = sessionStore.refreshToken() ?: throw IdentityApiException("TOKEN_INVALID")
        refreshMutex.withLock {
            val tokens = identityApi.refresh(refreshToken)
            persistTokens(tokens, previousRefreshToken = refreshToken)
        }
    }

    fun clearSession() {
        val hadSession = accessToken != null || sessionStore.refreshToken() != null || mutableCurrentUserId.value != null
        accessToken = null
        accessTokenExpiresAt = null
        sessionStore.clearSession()
        mutableCurrentUserId.value = null
        if (hadSession) mutableSessionInvalidations.tryEmit(Unit)
    }

    private suspend fun clearSessionAndUserData() {
        // Credentials are discarded first so no authenticated request can race cache cleanup.
        clearSession()
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            chatDatabase?.clearAllTables()
        }
    }

    private fun persistTokens(
        tokens: OAuthTokenResponse,
        previousRefreshToken: String?
    ): RestoredSession {
        // Production access tokens carry the session claims. Tests and some OAuth adapters may
        // return opaque access tokens, in which case retain the already verified local identity.
        val claims = runCatching { AccessTokenClaims.parse(tokens.accessToken) }.getOrNull()
        accessToken = tokens.accessToken
        accessTokenExpiresAt = Instant.now().plusSeconds(tokens.expiresIn)
        val refreshToken = tokens.refreshToken ?: previousRefreshToken
        if (refreshToken.isNullOrBlank()) {
            throw IdentityApiException("INVALID_TOKEN_RESPONSE")
        }
        sessionStore.saveRefreshToken(refreshToken)
        claims?.let {
            sessionStore.saveUserId(it.userId)
            sessionStore.saveOnboardingState(it.payPasswordSet, it.onboardingRequired)
        }
        val userId = claims?.userId ?: sessionStore.userId().orEmpty()
        val payPasswordSet = claims?.payPasswordSet ?: sessionStore.payPasswordSet()
        val onboardingRequired = claims?.onboardingRequired ?: sessionStore.onboardingRequired()
        mutableCurrentUserId.value = userId
        return RestoredSession(
            userId = userId,
            payPasswordSet = payPasswordSet,
            onboardingRequired = onboardingRequired
        )
    }

    private fun isDefinitiveRefreshRejection(error: Throwable): Boolean {
        val apiError = error as? IdentityApiException ?: return false
        return apiError.code in setOf("invalid_grant", "invalid_token", "TOKEN_INVALID")
    }
}
