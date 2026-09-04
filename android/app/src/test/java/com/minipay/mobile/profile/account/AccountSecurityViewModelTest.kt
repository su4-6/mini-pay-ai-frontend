package com.minipay.mobile.profile.account

import com.minipay.mobile.auth.IdentityApiException
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AccountSecurityViewModelTest {
    @Before
    fun installMainDispatcherBeforeRunTestCreatesItsScope() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun overviewUsesServerTruthAndRefreshFailureKeepsOldData() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val gateway = FakeGateway()
            val viewModel = AccountSecurityViewModel(gateway)
            runCurrent()
            assertEquals("138****8000", viewModel.state.value.overview?.maskedMobile)
            assertFalse(viewModel.state.value.overview?.paymentPasswordSet ?: true)

            gateway.loadFailure = IdentityApiException("NETWORK_UNAVAILABLE", requestId = "req-1")
            viewModel.refresh()
            runCurrent()

            assertEquals("138****8000", viewModel.state.value.overview?.maskedMobile)
            assertEquals("网络连接失败，请稍后重试", viewModel.state.value.errorMessage)
            assertEquals("req-1", viewModel.state.value.requestId)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun phoneValidationRunsBeforeNetworkAndSuccessEmitsLogoutEffectOnce() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val gateway = FakeGateway()
            val viewModel = AccountSecurityViewModel(gateway)
            runCurrent()
            viewModel.openPhone()
            viewModel.updateTarget("12800138000")
            viewModel.requestPhoneChallenge()
            assertEquals(0, gateway.phoneChallengeCalls)

            viewModel.updateTarget("13900139000")
            viewModel.requestPhoneChallenge()
            runCurrent()
            assertTrue(viewModel.state.value.page is AccountPage.PhoneCode)
            viewModel.updateCode("123456")
            val effect = async { viewModel.effects.first() }
            viewModel.confirmPhone()
            runCurrent()

            assertEquals(AccountSecurityEffect.PhoneChanged, effect.await())
            assertEquals(1, gateway.phoneConfirmCalls)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun deletingEmailFailureRetainsDisplayedEmail() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val gateway = FakeGateway().apply {
                deleteFailure = IdentityApiException("NETWORK_UNAVAILABLE")
            }
            val viewModel = AccountSecurityViewModel(gateway)
            runCurrent()
            viewModel.openEmail()
            viewModel.deleteEmail()
            runCurrent()

            assertEquals("u***@example.com", viewModel.state.value.overview?.maskedEmail)
            assertTrue(viewModel.state.value.page is AccountPage.EmailCurrent)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun paymentPasswordRequiresPhoneVerificationAndMatchingSixDigits() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val gateway = FakeGateway(
                overviewValue = AccountSecurityOverview("138****8000", null, true)
            )
            val viewModel = AccountSecurityViewModel(gateway)
            runCurrent()
            viewModel.openPaymentPassword()
            runCurrent()
            assertTrue(viewModel.state.value.page is AccountPage.PaymentCode)

            viewModel.updateCode("123456")
            viewModel.confirmPaymentCode()
            runCurrent()
            assertTrue(viewModel.state.value.page is AccountPage.PaymentPassword)

            viewModel.changePaymentPassword("123456", "654321")
            assertEquals("两次输入的支付密码不一致", viewModel.state.value.errorMessage)
            assertEquals(0, gateway.passwordChangeCalls)

            viewModel.changePaymentPassword("123456", "123456")
            runCurrent()
            assertEquals(1, gateway.passwordChangeCalls)
            assertTrue(viewModel.state.value.page is AccountPage.Result)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class FakeGateway(
        private var overviewValue: AccountSecurityOverview = AccountSecurityOverview(
            "138****8000",
            "u***@example.com",
            false
        )
    ) : AccountSecurityGateway {
        var loadFailure: Throwable? = null
        var deleteFailure: Throwable? = null
        var phoneChallengeCalls = 0
        var phoneConfirmCalls = 0
        var passwordChangeCalls = 0

        private fun challenge(masked: String) = VerificationChallenge(
            "challenge",
            masked,
            Instant.now().plusSeconds(300),
            60
        )

        override suspend fun loadOverview(): AccountSecurityOverview {
            loadFailure?.let { throw it }
            return overviewValue
        }

        override suspend fun requestPhoneChange(mobile: String, idempotencyKey: String): VerificationChallenge {
            phoneChallengeCalls += 1
            return challenge("139****9000")
        }

        override suspend fun confirmPhoneChange(challengeId: String, code: String, idempotencyKey: String) {
            phoneConfirmCalls += 1
        }

        override suspend fun requestEmailVerification(email: String, idempotencyKey: String) =
            challenge("u***@example.com")

        override suspend fun confirmEmail(challengeId: String, code: String, idempotencyKey: String) = Unit

        override suspend fun deleteEmail(idempotencyKey: String) {
            deleteFailure?.let { throw it }
            overviewValue = overviewValue.copy(maskedEmail = null)
        }

        override suspend fun requestPaymentPasswordChallenge(idempotencyKey: String) =
            challenge("138****8000")

        override suspend fun verifyPaymentPasswordChallenge(
            challengeId: String,
            code: String,
            idempotencyKey: String
        ) = PaymentPasswordVerification("single-use-token", Instant.now().plusSeconds(300))

        override suspend fun changePaymentPassword(
            verificationToken: String,
            newPassword: String,
            idempotencyKey: String
        ) {
            passwordChangeCalls += 1
            overviewValue = overviewValue.copy(paymentPasswordSet = true)
        }

        override fun currentMobile(): String = "13800138000"
    }
}
