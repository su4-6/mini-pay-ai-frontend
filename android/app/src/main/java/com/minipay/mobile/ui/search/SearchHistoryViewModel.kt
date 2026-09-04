package com.minipay.mobile.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minipay.mobile.chat.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SearchHistoryViewModel @Inject constructor(
    private val repository: ChatRepository
) : ViewModel() {
    val history: StateFlow<List<String>> = repository.observeSearchHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun record(query: String) {
        viewModelScope.launch { repository.recordSearchHistory(query) }
    }

    fun clear() {
        viewModelScope.launch { repository.clearSearchHistory() }
    }
}
