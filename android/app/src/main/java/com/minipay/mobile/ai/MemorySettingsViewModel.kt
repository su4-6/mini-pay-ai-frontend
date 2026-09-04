package com.minipay.mobile.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class MemorySettingsUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val settings: MemorySettingDto? = null,
    val items: List<MemoryItemDto> = emptyList(),
    val error: String? = null,
    val operationError: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class MemorySettingsViewModel @Inject constructor(
    private val repository: AiConversationRepository
) : ViewModel() {
    private var mutationInFlight = false
    private var refreshJob: Job? = null
    private val mutableState = MutableStateFlow(MemorySettingsUiState())
    val state: StateFlow<MemorySettingsUiState> = mutableState.asStateFlow()

    init { refresh() }

    fun refresh() {
        if (refreshJob?.isActive == true || mutationInFlight) return
        refreshJob = viewModelScope.launch {
            mutableState.update { it.copy(loading = it.settings == null, error = null) }
            runCatching { repository.memorySettings() to repository.memoryItems() }
                .onSuccess { (settings, items) ->
                    mutableState.value = MemorySettingsUiState(
                        loading = false, settings = settings, items = items
                    )
                }.onFailure(::showError)
        }
    }

    fun updateSettings(request: UpdateMemorySettingsRequest) = mutate {
        val updated = repository.updateMemorySettings(request)
        mutableState.update { it.copy(settings = updated) }
    }

    fun addCustom(value: String, onAdded: () -> Unit = {}) {
        val normalized = value.trim()
        if (normalized.isBlank() || normalized.codePointCount() > 256) {
            mutableState.update { it.copy(operationError = "记忆内容必须为 1 到 256 个字符", successMessage = null) }
            return
        }
        mutate(successMessage = "已保存到长期记忆") {
            val created = repository.createMemoryItem(normalized, UUID.randomUUID().toString())
            mutableState.update { it.copy(items = listOf(created) + it.items) }
            onAdded()
        }
    }

    fun updateItem(item: MemoryItemDto, value: String) = mutate {
        val updated = repository.updateMemoryItem(item, value.trim())
        mutableState.update { state ->
            state.copy(items = state.items.map { if (it.id == updated.id) updated else it })
        }
    }

    fun deleteItem(item: MemoryItemDto) = mutate {
        repository.deleteMemoryItem(item)
        mutableState.update { state -> state.copy(items = state.items.filterNot { it.id == item.id }) }
    }

    fun clearError() = mutableState.update { it.copy(error = null) }

    private fun mutate(successMessage: String? = null, block: suspend () -> Unit) {
        if (mutationInFlight) return
        mutationInFlight = true
        viewModelScope.launch {
            mutableState.update { it.copy(saving = true, operationError = null, successMessage = null) }
            try {
                runCatching { block() }
                    .onSuccess { mutableState.update { state -> state.copy(successMessage = successMessage) } }
                    .onFailure(::showOperationError)
            } finally {
                mutationInFlight = false
                mutableState.update { it.copy(saving = false) }
            }
        }
    }

    private fun showError(error: Throwable) {
        mutableState.update {
            it.copy(loading = false, saving = false, error = repository.message(error))
        }
    }

    private fun showOperationError(error: Throwable) {
        mutableState.update {
            it.copy(saving = false, operationError = repository.message(error), successMessage = null)
        }
    }

    private fun String.codePointCount(): Int = codePointCount(0, length)
}
