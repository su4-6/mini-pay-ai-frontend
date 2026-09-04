package com.minipay.mobile.onboarding

import android.net.Uri
import com.minipay.mobile.profile.PreparedAvatar

interface OnboardingGateway {
    suspend fun prepareAvatar(uri: Uri): PreparedAvatar
    suspend fun uploadAvatar(avatar: PreparedAvatar): String
    suspend fun complete(
        nickname: String,
        avatarUploadId: String?,
        idempotencyKey: String
    ): OnboardingResponse
    fun message(error: Throwable): String
}
