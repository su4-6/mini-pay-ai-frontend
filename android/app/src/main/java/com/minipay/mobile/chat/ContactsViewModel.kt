package com.minipay.mobile.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val repository: ChatRepository
) : ViewModel() {
    private var refreshJob: Job? = null
    private val mutableRequests = MutableStateFlow<List<ReceivedRequest>>(emptyList())
    val receivedRequests: StateFlow<List<ReceivedRequest>> = mutableRequests.asStateFlow()
    private val mutableRequestError = MutableStateFlow<String?>(null)
    val requestError: StateFlow<String?> = mutableRequestError.asStateFlow()

    val contacts: StateFlow<List<Contact>> = repository.observeContacts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val groupedContacts: StateFlow<Map<String, List<Contact>>> = contacts
        .map { list -> list.groupBy { it.firstLetter } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    init {
        refresh()
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            repository.syncContactsFromServer()
            mutableRequests.value = runCatching { repository.receivedFriendRequests() }.getOrDefault(emptyList())
        }
    }

    fun acceptRequest(requestId: String) {
        viewModelScope.launch {
            if (repository.acceptFriendRequest(requestId)) refresh()
            else mutableRequestError.value = "操作失败，请检查网络后重试"
        }
    }

    fun rejectRequest(requestId: String) {
        viewModelScope.launch {
            if (repository.rejectFriendRequest(requestId)) {
                mutableRequests.value = mutableRequests.value.filterNot { it.id == requestId }
            } else mutableRequestError.value = "操作失败，请检查网络后重试"
        }
    }

    fun clearRequestError() { mutableRequestError.value = null }

    fun openConversation(contactId: String, contactName: String, onReady: (conversationId: String, name: String) -> Unit) {
        viewModelScope.launch {
            val conversation = repository.ensureConversation(contactId, contactName)
            if (conversation != null) {
                onReady(conversation.id, conversation.name)
            }
        }
    }
}
