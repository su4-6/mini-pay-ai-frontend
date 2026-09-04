package com.minipay.mobile.authorization

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minipay.mobile.ai.CommerceApi
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ApplicationAuthorizationState(
    val loading: Boolean = true,
    val applications: List<ApplicationAuthorizationDto> = emptyList(),
    val accountLabels: Map<String, String> = emptyMap(),
    val selected: ApplicationAuthorizationDto? = null,
    val revoking: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ApplicationAuthorizationViewModel @Inject constructor(
    private val repository: ApplicationAuthorizationRepository,
    private val commerce: CommerceApi
) : ViewModel() {
    private val mutableState = MutableStateFlow(ApplicationAuthorizationState())
    val state: StateFlow<ApplicationAuthorizationState> = mutableState.asStateFlow()

    init { refresh() }

    fun refresh() {
        mutableState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.list().filter {
                it.authorizationId != null && it.state != "NOT_AUTHORIZED" && it.state != "REVOKED"
            } }
                .onSuccess { apps ->
                    val labels = buildMap {
                        if (apps.any { it.applicationId == YSHOP_APPLICATION_ID }) {
                            val binding = runCatching { commerce.foodBinding() }.getOrNull()
                            put(
                                YSHOP_APPLICATION_ID,
                                if (binding?.active == true) maskedExternalUsername(binding.username)
                                else BOUND_FALLBACK
                            )
                        }
                    }
                    mutableState.update {
                    it.copy(loading = false, applications = apps, accountLabels = labels,
                        selected = it.selected?.let { selected ->
                            apps.firstOrNull { app -> app.applicationId == selected.applicationId }
                        })
                } }
                .onFailure { mutableState.update {
                    it.copy(loading = false, error = "授权信息加载失败，请重试")
                } }
        }
    }

    fun select(application: ApplicationAuthorizationDto?) {
        mutableState.update { it.copy(selected = application, error = null) }
    }

    fun revoke(onRevoked: () -> Unit) {
        val application = mutableState.value.selected ?: return
        if (mutableState.value.revoking) return
        mutableState.update { it.copy(revoking = true, error = null) }
        viewModelScope.launch {
            runCatching {
                repository.revoke(application.applicationId)
                if (application.applicationId == "yshop-food") commerce.unbindFood()
            }.onSuccess {
                mutableState.update { state ->
                    state.copy(
                        revoking = false,
                        applications = state.applications.filterNot {
                            it.applicationId == application.applicationId
                        },
                        accountLabels = state.accountLabels - application.applicationId,
                        selected = null
                    )
                }
                onRevoked()
            }.onFailure {
                mutableState.update { state ->
                    state.copy(revoking = false,
                        error = "MiniPay 已停止继续共享资料；外卖会话撤销正在重试")
                }
            }
        }
    }

    private companion object {
        const val YSHOP_APPLICATION_ID = "yshop-food"
        const val BOUND_FALLBACK = "已绑定"
    }
}

internal fun maskedExternalUsername(username: String?): String {
    val value = username?.trim().orEmpty()
    return when (value.length) {
        0 -> "已绑定"
        1 -> "*"
        else -> "${value.first()}***${value.last()}"
    }
}
