package com.minipay.mobile.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    @Test
    fun agreementIsRequiredBeforeSendingCode() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val gateway = FakeGateway()
            val viewModel = AuthViewModel(gateway)
            runCurrent()
            viewModel.updateMobile("13800138000")

            viewModel.sendCode()

            val state = viewModel.uiState.value as AuthUiState.PhoneEntry
            assertFalse(state.submitting)
            assertEquals("请先阅读并同意用户协议和隐私政策", state.errorMessage)
            assertEquals(0, gateway.sendCalls)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun sixDigitsAutomaticallyVerifyAndNavigateAuthenticated() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val gateway = FakeGateway()
            val viewModel = AuthViewModel(gateway)
            runCurrent()
            viewModel.updateMobile("13800138000")
            viewModel.toggleAgreement()
            viewModel.sendCode()
            runCurrent()

            val codeState = viewModel.uiState.value as AuthUiState.CodeEntry
            assertEquals("138****8000", codeState.maskedMobile)
            assertEquals(60, codeState.secondsUntilResend)

            viewModel.updateCode("123456")
            runCurrent()

            assertEquals(1, gateway.verifyCalls)
            assertTrue(
                (viewModel.uiState.value as AuthUiState.Session).payPasswordSet
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun sendingCodeNavigatesImmediatelyThenEnablesCodeEntryWhenDeliveryCompletes() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val deliveryGate = CompletableDeferred<Unit>()
            val gateway = FakeGateway(sendGate = deliveryGate)
            val viewModel = AuthViewModel(gateway)
            runCurrent()
            viewModel.updateMobile("13800138000")
            viewModel.toggleAgreement()

            viewModel.sendCode()

            val sending = viewModel.uiState.value as AuthUiState.CodeEntry
            assertEquals(CodeDeliveryStatus.SENDING, sending.deliveryStatus)
            assertEquals(null, sending.challengeId)
            assertEquals("138****8000", sending.maskedMobile)

            runCurrent()
            deliveryGate.complete(Unit)
            runCurrent()

            val sent = viewModel.uiState.value as AuthUiState.CodeEntry
            assertEquals(CodeDeliveryStatus.SENT, sent.deliveryStatus)
            assertEquals("challenge", sent.challengeId)
            assertEquals(60, sent.secondsUntilResend)
            viewModel.backToPhone()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun deliveryFailureStaysOnCodeEntryAndCanBeRetried() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val gateway = FakeGateway(sendFailure = IdentityApiException("NETWORK_UNAVAILABLE"))
            val viewModel = AuthViewModel(gateway)
            runCurrent()
            viewModel.updateMobile("13800138000")
            viewModel.toggleAgreement()

            viewModel.sendCode()
            runCurrent()

            val failed = viewModel.uiState.value as AuthUiState.CodeEntry
            assertEquals(CodeDeliveryStatus.FAILED, failed.deliveryStatus)
            assertTrue(failed.challengeId == null)
            assertEquals(1, gateway.sendCalls)

            gateway.sendFailure = null
            viewModel.resendCode()
            runCurrent()

            assertEquals(CodeDeliveryStatus.SENT, (viewModel.uiState.value as AuthUiState.CodeEntry).deliveryStatus)
            assertEquals(2, gateway.sendCalls)
            viewModel.backToPhone()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun backingOutCancelsPendingDeliveryAndIgnoresLateResult() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val deliveryGate = CompletableDeferred<Unit>()
            val gateway = FakeGateway(sendGate = deliveryGate)
            val viewModel = AuthViewModel(gateway)
            runCurrent()
            viewModel.updateMobile("13800138000")
            viewModel.toggleAgreement()
            viewModel.sendCode()
            runCurrent()

            viewModel.backToPhone()
            deliveryGate.complete(Unit)
            runCurrent()

            assertTrue(viewModel.uiState.value is AuthUiState.PhoneEntry)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun newConsumerNavigatesToRequiredOnboarding() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val gateway = FakeGateway(onboardingRequired = true)
            val viewModel = AuthViewModel(gateway)
            runCurrent()
            viewModel.updateMobile("13800138000")
            viewModel.toggleAgreement()
            viewModel.sendCode()
            runCurrent()
            viewModel.updateCode("123456")
            runCurrent()

            val onboarding = viewModel.uiState.value as AuthUiState.Session
            assertTrue(onboarding.onboardingRequired)
            viewModel.completeOnboarding()
            assertFalse((viewModel.uiState.value as AuthUiState.Session).onboardingRequired)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun submittingVerificationConsumesBackAndIgnoresResultAfterLogout() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val verificationGate = CompletableDeferred<Unit>()
            val gateway = FakeGateway(verifyGate = verificationGate)
            val viewModel = AuthViewModel(gateway)
            runCurrent()
            beginVerification(viewModel)

            viewModel.backToPhone()
            assertTrue((viewModel.uiState.value as AuthUiState.CodeEntry).submitting)

            viewModel.logout()
            runCurrent()
            verificationGate.complete(Unit)
            runCurrent()

            assertTrue(viewModel.uiState.value is AuthUiState.PhoneEntry)
            advanceUntilIdle()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun consecutiveAccountsReceiveDifferentSessionKeys() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val gateway = FakeGateway(userIds = ArrayDeque(listOf(USER_A, USER_B)), onboardingRequired = true)
            val viewModel = AuthViewModel(gateway)
            runCurrent()

            beginVerification(viewModel)
            val first = viewModel.uiState.value as AuthUiState.Session
            viewModel.logout()
            runCurrent()
            beginVerification(viewModel)
            val second = viewModel.uiState.value as AuthUiState.Session

            assertEquals(USER_A, first.userId)
            assertEquals(USER_B, second.userId)
            assertTrue(second.sessionKey > first.sessionKey)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun repositorySessionInvalidationReturnsToPhoneEntry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val gateway = FakeGateway()
            val viewModel = AuthViewModel(gateway)
            runCurrent()
            beginVerification(viewModel)
            assertTrue(viewModel.uiState.value is AuthUiState.Session)

            gateway.invalidateSession()
            runCurrent()

            assertTrue(viewModel.uiState.value is AuthUiState.PhoneEntry)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun TestScope.beginVerification(viewModel: AuthViewModel) {
        viewModel.updateMobile("13800138000")
        viewModel.toggleAgreement()
        viewModel.sendCode()
        runCurrent()
        viewModel.updateCode("123456")
        runCurrent()
    }

    private class FakeGateway(
        private val onboardingRequired: Boolean = false,
        var sendFailure: Throwable? = null,
        private val sendGate: CompletableDeferred<Unit>? = null,
        private val verifyGate: CompletableDeferred<Unit>? = null,
        private val userIds: ArrayDeque<String> = ArrayDeque(listOf(USER_A))
    ) : AuthGateway {
        private val invalidations = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        override val sessionInvalidations = invalidations
        override suspend fun logout() = Unit
        var sendCalls = 0
        var verifyCalls = 0

        override suspend fun sendCode(mobile: String): CodeChallengeResponse {
            sendCalls += 1
            sendGate?.await()
            sendFailure?.let { throw it }
            return CodeChallengeResponse(
                challengeId = "challenge",
                maskedMobile = "138****8000",
                expiresAt = "2026-07-30T10:05:00Z",
                resendAfterSeconds = 60
            )
        }

        override suspend fun verifyAndLogin(
            challengeId: String,
            code: String
        ): AuthorizationCodeResponse {
            verifyCalls += 1
            verifyGate?.await()
            return AuthorizationCodeResponse(
                authorizationCode = "one-time-code",
                expiresAt = "2026-07-30T10:01:00Z",
                userId = userIds.removeFirst(),
                payPasswordSet = true,
                onboardingRequired = onboardingRequired
            )
        }

        override suspend fun restoreSession(): RestoredSession? = null
        override fun cancelChallenge() = Unit

        fun invalidateSession() {
            invalidations.tryEmit(Unit)
        }
    }

    private companion object {
        const val USER_A = "018f0f5d-52c7-7b8d-9f22-6f858e711001"
        const val USER_B = "018f0f5d-52c7-7b8d-9f22-6f858e711002"
    }
}
