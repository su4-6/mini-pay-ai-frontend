package com.minipay.mobile.onboarding

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minipay.mobile.auth.IdentityApiException
import com.minipay.mobile.profile.isNicknameValid
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repository: OnboardingGateway
) : ViewModel() {
    private val mutableState = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = mutableState.asStateFlow()
    private val idempotencyKey = UUID.randomUUID().toString()

    fun updateNickname(value: String) {
        val codePoints = value.codePoints().limit(20).toArray()
        mutableState.value = mutableState.value.copy(
            nickname = String(codePoints, 0, codePoints.size), errorMessage = null
        )
    }

    fun selectAvatar(uri: Uri) {
        if (mutableState.value.processingAvatar || mutableState.value.submitting) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(processingAvatar = true, errorMessage = null)
            runCatching { repository.prepareAvatar(uri) }
                .onSuccess {
                    mutableState.value = mutableState.value.copy(
                        selectedAvatarUri = uri, preparedAvatar = it,
                        avatarUploadId = null, processingAvatar = false
                    )
                }
                .onFailure {
                    mutableState.value = mutableState.value.copy(
                        processingAvatar = false, errorMessage = "无法处理所选图片"
                    )
                }
        }
    }

    fun removeAvatar() {
        if (mutableState.value.submitting) return
        mutableState.value = mutableState.value.copy(
            selectedAvatarUri = null, preparedAvatar = null,
            avatarUploadId = null, errorMessage = null
        )
    }

    fun submit() {
        val current = mutableState.value
        if (current.submitting) return
        if (!isNicknameValid(current.nickname.trim())) {
            mutableState.value = current.copy(errorMessage = "昵称需为 2–20 个中文、字母、数字或下划线")
            return
        }
        mutableState.value = current.copy(submitting = true, errorMessage = null)
        viewModelScope.launch {
            runCatching {
                val latest = mutableState.value
                val uploadId = latest.avatarUploadId ?: latest.preparedAvatar?.let {
                    repository.uploadAvatar(it).also { id ->
                        mutableState.value = mutableState.value.copy(avatarUploadId = id)
                    }
                }
                repository.complete(
                    nickname = latest.nickname.trim(),
                    avatarUploadId = uploadId,
                    idempotencyKey = idempotencyKey
                )
            }.onSuccess {
                mutableState.value = mutableState.value.copy(
                    step = OnboardingStep.COMPLETE, submitting = false, errorMessage = null
                )
            }.onFailure {
                val code = (it as? IdentityApiException)?.code
                mutableState.value = if (code == "ONBOARDING_ALREADY_COMPLETED") {
                    mutableState.value.copy(
                        step = OnboardingStep.COMPLETE, submitting = false, errorMessage = null
                    )
                } else {
                    mutableState.value.copy(
                        submitting = false, errorMessage = repository.message(it)
                    )
                }
            }
        }
    }
}
