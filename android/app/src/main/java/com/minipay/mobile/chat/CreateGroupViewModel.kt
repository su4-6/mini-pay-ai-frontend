package com.minipay.mobile.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class CreateGroupViewModel @Inject constructor(
    private val repository: ChatRepository
) : ViewModel() {

    val groupedContacts: StateFlow<Map<String, List<Contact>>> = repository.observeContacts()
        .map { list -> list.groupBy { it.firstLetter } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    fun createGroup(
        memberIds: List<String>,
        onCreated: (conversationId: String, name: String) -> Unit,
        onError: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val conversation = repository.createGroup(memberIds)
            if (conversation != null) {
                onCreated(conversation.id, conversation.name)
            } else {
                onError()
            }
        }
    }
}
