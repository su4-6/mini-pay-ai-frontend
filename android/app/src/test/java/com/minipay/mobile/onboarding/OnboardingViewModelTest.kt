package com.minipay.mobile.onboarding

import android.net.Uri
import com.minipay.mobile.profile.PreparedAvatar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    @Test
    fun validProfileCompletesWithoutPaymentPassword() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val gateway = FakeGateway()
            val viewModel = OnboardingViewModel(gateway)
            viewModel.updateNickname("MiniUser")
            viewModel.submit()
            viewModel.submit()
            runCurrent()
            assertEquals(1, gateway.completeCalls)
            assertEquals(OnboardingStep.COMPLETE, viewModel.state.value.step)
            assertFalse(viewModel.state.value.submitting)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun invalidNicknameDoesNotSubmit() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val gateway = FakeGateway()
            val viewModel = OnboardingViewModel(gateway)
            viewModel.updateNickname("!")
            viewModel.submit()
            assertEquals(OnboardingStep.PROFILE, viewModel.state.value.step)
            assertEquals(0, gateway.completeCalls)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun retryKeepsSameIdempotencyKey() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val gateway = FakeGateway(failuresRemaining = 1)
            val viewModel = OnboardingViewModel(gateway)
            viewModel.updateNickname("MiniUser")
            viewModel.submit(); runCurrent()
            assertTrue(viewModel.state.value.errorMessage != null)
            viewModel.submit(); runCurrent()
            assertEquals(gateway.keys.first(), gateway.keys.last())
            assertEquals(OnboardingStep.COMPLETE, viewModel.state.value.step)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun separateSessionViewModelsUseDifferentIdempotencyKeys() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val gateway = FakeGateway()
            val first = OnboardingViewModel(gateway)
            first.updateNickname("账号A")
            first.submit()
            runCurrent()

            val second = OnboardingViewModel(gateway)
            second.updateNickname("账号B")
            second.submit()
            runCurrent()

            assertEquals(2, gateway.keys.distinct().size)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class FakeGateway(var failuresRemaining: Int = 0) : OnboardingGateway {
        var completeCalls = 0
        val keys = mutableListOf<String>()
        override suspend fun prepareAvatar(uri: Uri): PreparedAvatar = error("not used")
        override suspend fun uploadAvatar(avatar: PreparedAvatar): String = "upload-id"
        override suspend fun complete(
            nickname: String, avatarUploadId: String?, idempotencyKey: String
        ): OnboardingResponse {
            completeCalls += 1; keys += idempotencyKey
            if (failuresRemaining-- > 0) error("network")
            return OnboardingResponse("user-id", nickname, false, true)
        }
        override fun message(error: Throwable): String = "初始化失败"
    }
}
