package com.minipay.mobile.auth

import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Rule
import org.junit.Test

class AuthSessionInvalidationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun invalidatedRepositorySessionReturnsUiToLogin() {
        val gateway = SessionGateway()
        val viewModel = AuthViewModel(gateway)
        composeRule.setContent {
            val state by viewModel.uiState.collectAsState()
            Text(if (state is AuthUiState.Session) "session" else "login")
        }
        composeRule.waitUntil(5_000) {
            runCatching {
                composeRule.onNodeWithText("session").fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }

        gateway.invalidate()

        composeRule.onNodeWithText("login").assertIsDisplayed()
    }

    private class SessionGateway : AuthGateway {
        private val invalidations = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        override val sessionInvalidations = invalidations

        override suspend fun restoreSession() = RestoredSession(
            userId = "018f0f5d-52c7-7b8d-9f22-6f858e711001",
            payPasswordSet = true,
            onboardingRequired = false
        )

        override suspend fun sendCode(mobile: String) = error("not used")
        override suspend fun verifyAndLogin(challengeId: String, code: String) = error("not used")
        override suspend fun logout() = Unit
        override fun cancelChallenge() = Unit

        fun invalidate() {
            invalidations.tryEmit(Unit)
        }
    }
}
