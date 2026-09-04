package com.minipay.mobile.onboarding

import android.net.Uri
import com.minipay.mobile.profile.PreparedAvatar
import kotlinx.serialization.Serializable

@Serializable
data class CompleteOnboardingRequest(
    val nickname: String,
    val avatarUploadId: String? = null
)

@Serializable
data class OnboardingResponse(
    val userId: String,
    val nickname: String,
    val payPasswordSet: Boolean,
    val onboardingCompleted: Boolean
)

enum class OnboardingStep { PROFILE, COMPLETE }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.PROFILE,
    val nickname: String = "",
    val selectedAvatarUri: Uri? = null,
    val preparedAvatar: PreparedAvatar? = null,
    val avatarUploadId: String? = null,
    val processingAvatar: Boolean = false,
    val submitting: Boolean = false,
    val errorMessage: String? = null
)
