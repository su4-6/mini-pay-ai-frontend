package com.minipay.mobile.home

import android.content.Context
import android.content.SharedPreferences
import com.minipay.mobile.auth.AuthRepository
import com.minipay.mobile.ui.home.AppService
import com.minipay.mobile.ui.home.appServices
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

const val MAX_COMMON_APPS = 5

val defaultCommonAppIds = listOf("bills", "add_friend", "wallet", "receive", "scan")

fun sanitizeCommonAppIds(ids: Iterable<String>): List<String> {
    val availableIds = appServices.asSequence().filter(AppService::available).map(AppService::id).toSet()
    return ids.asSequence()
        .filter(availableIds::contains)
        .distinct()
        .take(MAX_COMMON_APPS)
        .toList()
}

fun resolveCommonApps(ids: Iterable<String>): List<AppService> {
    val byId = appServices.associateBy(AppService::id)
    return sanitizeCommonAppIds(ids).mapNotNull(byId::get)
}

internal class CommonAppsPreferenceStore(
    private val preferences: SharedPreferences
) {
    fun read(userId: String?): List<AppService> {
        if (userId == null) return resolveCommonApps(defaultCommonAppIds)
        val storageKey = key(userId)
        if (!preferences.contains(storageKey)) return resolveCommonApps(defaultCommonAppIds)
        val stored = preferences.getString(storageKey, "").orEmpty()
        return resolveCommonApps(stored.split(',').filter(String::isNotBlank))
    }

    fun write(userId: String?, apps: List<AppService>): Boolean {
        if (userId == null) return false
        preferences.edit()
            .putString(key(userId), apps.take(MAX_COMMON_APPS).joinToString(",", transform = AppService::id))
            .apply()
        return true
    }

    private fun key(userId: String) = "ordered_ids_$userId"
}

@Singleton
class CommonAppsRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val authRepository: AuthRepository
) {
    private val store = CommonAppsPreferenceStore(
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutableApps = MutableStateFlow(defaultApps())
    val apps: StateFlow<List<AppService>> = mutableApps.asStateFlow()

    init {
        scope.launch {
            authRepository.currentUserId.collect { userId ->
                mutableApps.value = store.read(userId)
            }
        }
    }

    fun add(serviceId: String) {
        update { current ->
            if (current.size >= MAX_COMMON_APPS || current.any { it.id == serviceId }) current
            else resolveCommonApps(current.map(AppService::id) + serviceId)
        }
    }

    fun remove(serviceId: String) {
        update { current -> current.filterNot { it.id == serviceId } }
    }

    fun move(fromIndex: Int, toIndex: Int) {
        update { current ->
            if (fromIndex !in current.indices || toIndex !in current.indices || fromIndex == toIndex) {
                current
            } else {
                current.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
            }
        }
    }

    private fun update(transform: (List<AppService>) -> List<AppService>) {
        val userId = authRepository.currentUserId.value ?: return
        val next = transform(mutableApps.value).take(MAX_COMMON_APPS)
        mutableApps.value = next
        store.write(userId, next)
    }

    private fun defaultApps(): List<AppService> = resolveCommonApps(defaultCommonAppIds)

    private companion object {
        const val PREFERENCES_NAME = "minipay_common_apps"
    }
}

@HiltViewModel
class CommonAppsViewModel @Inject constructor(
    private val repository: CommonAppsRepository
) : androidx.lifecycle.ViewModel() {
    val apps: StateFlow<List<AppService>> = repository.apps

    fun add(serviceId: String) = repository.add(serviceId)
    fun remove(serviceId: String) = repository.remove(serviceId)
    fun move(fromIndex: Int, toIndex: Int) = repository.move(fromIndex, toIndex)
}
