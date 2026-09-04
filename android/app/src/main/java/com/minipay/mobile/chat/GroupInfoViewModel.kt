package com.minipay.mobile.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minipay.mobile.auth.AuthRepository
import com.minipay.mobile.profile.AvatarPreparer
import android.net.Uri
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class GroupInfoViewModel @Inject constructor(
    private val repository: ChatRepository,
    private val authRepository: AuthRepository,
    private val avatarPreparer: AvatarPreparer,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    val groupId: String = savedStateHandle.get<String>("conversationId") ?: ""
    private val _detail = MutableStateFlow<GroupDetailResponse?>(null)
    val detail: StateFlow<GroupDetailResponse?> = _detail.asStateFlow()
    private val _avatarPreviewUrl = MutableStateFlow<String?>(null)
    val avatarPreviewUrl: StateFlow<String?> = _avatarPreviewUrl.asStateFlow()
    val currentUserId: String? get() = authRepository.currentUserId.value
    private var refreshJob: Job? = null

    init { refresh() }
    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch { _detail.value = repository.getGroupDetail(groupId) }
    }
    fun addMembers(members: List<GroupMemberInput>, done: (Boolean) -> Unit) = viewModelScope.launch {
        done(repository.addGroupMembers(groupId, members))
        refresh()
    }
    fun removeMember(id: String, done: (Boolean) -> Unit) = viewModelScope.launch { done(repository.removeGroupMember(groupId, id)); refresh() }
    fun rename(name: String, done: (Boolean) -> Unit) = viewModelScope.launch { done(repository.renameGroup(groupId, name)); refresh() }
    fun updateNickname(name: String, done: (Boolean) -> Unit) = viewModelScope.launch { done(repository.updateMyGroupNickname(groupId, name)); refresh() }
    fun updateAvatar(uri: Uri, done: (Boolean) -> Unit) = viewModelScope.launch {
        val result = runCatching { avatarPreparer.prepare(uri) }
            .mapCatching { repository.updateGroupAvatar(groupId, it.bytes).getOrThrow() }
        result.getOrNull()?.let { avatar ->
            _avatarPreviewUrl.value = uri.toString()
            _detail.value = _detail.value?.copy(
                avatarUrl = avatar.avatarUrl,
                avatarUrlExpiresAt = avatar.avatarUrlExpiresAt
            )
        }
        done(result.isSuccess)
    }
    fun disband(done: (Boolean) -> Unit) = viewModelScope.launch { done(repository.disbandGroup(groupId)) }
    fun leave(done: (Boolean) -> Unit) = viewModelScope.launch { done(repository.leaveGroup(groupId)) }
}
