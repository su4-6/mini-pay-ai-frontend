package com.minipay.mobile.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minipay.mobile.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: ProfileRepository,
    private val avatarPreparer: AvatarPreparer,
    auth: AuthRepository
) : ViewModel() {
    val state: StateFlow<ProfileLoadState> = repository.state
    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            auth.currentUserId.collectLatest { userId ->
                repository.clear()
                if (userId != null) repository.refresh(userId)
            }
        }
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch { repository.refresh() }
    }

    suspend fun prepareAvatar(uri: Uri): PreparedAvatar = avatarPreparer.prepare(uri)

    suspend fun save(nickname: String, avatar: PreparedAvatar?): Result<UserProfile> =
        runCatching { repository.save(nickname, avatar) }

    fun errorMessage(error: Throwable): String = repository.message(error)
}
