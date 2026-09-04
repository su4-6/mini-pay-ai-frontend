package com.minipay.mobile.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

@HiltViewModel
class ConversationListViewModel @Inject constructor(
    private val repository: ChatRepository
) : ViewModel() {
    private val refreshMutex = Mutex()
    private val mutablePendingFriendRequestCount = MutableStateFlow(0)
    val pendingFriendRequestCount: StateFlow<Int> = mutablePendingFriendRequestCount.asStateFlow()
    private val mutableDeleteError = MutableStateFlow<String?>(null)
    val deleteError: StateFlow<String?> = mutableDeleteError.asStateFlow()

    val conversations: StateFlow<List<Conversation>> = repository.observeConversations()
        .map { it.sortedByDescending { conversation -> conversation.lastMessageTime } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            refreshData()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            refreshData()
        }
    }

    private suspend fun refreshData() {
        if (!refreshMutex.tryLock()) return
        try {
            repository.syncConversations()
            runCatching { repository.receivedFriendRequests().size }
                .onSuccess { mutablePendingFriendRequestCount.value = it }
        } finally {
            refreshMutex.unlock()
        }
    }

    fun clearUnread(conversationId: String) {
        viewModelScope.launch {
            repository.clearUnread(conversationId)
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            mutableDeleteError.value = null
            if (!repository.deleteConversation(conversationId)) {
                mutableDeleteError.value = "删除失败，请检查网络后重试"
            }
        }
    }

    fun clearDeleteError() {
        mutableDeleteError.value = null
    }
}
