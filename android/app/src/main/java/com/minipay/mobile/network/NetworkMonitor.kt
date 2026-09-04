package com.minipay.mobile.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

internal interface NetworkStatusProvider {
    val isOnline: StateFlow<Boolean>
    val reconnections: Flow<Unit>
}

@Singleton
internal class NetworkMonitor @Inject constructor(
    @ApplicationContext context: Context
) : NetworkStatusProvider {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val availability = NetworkAvailabilityState(currentlyOnline())
    private val mutableIsOnline = MutableStateFlow(availability.current)
    private val mutableReconnections = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override val isOnline: StateFlow<Boolean> = mutableIsOnline.asStateFlow()
    override val reconnections: Flow<Unit> = mutableReconnections.asSharedFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refreshFromSystem()

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
            refreshFromSystem()

        override fun onLost(network: Network) = refreshFromSystem()

        override fun onUnavailable() = updateAvailability(false)
    }

    init {
        connectivityManager.registerDefaultNetworkCallback(callback)
        refreshFromSystem()
    }

    private fun refreshFromSystem() {
        updateAvailability(currentlyOnline())
    }

    private fun currentlyOnline(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun updateAvailability(online: Boolean) {
        val recovered = availability.update(online)
        mutableIsOnline.value = online
        if (recovered) mutableReconnections.tryEmit(Unit)
    }
}

internal class NetworkAvailabilityState(initialOnline: Boolean) {
    @Volatile
    var current: Boolean = initialOnline
        private set

    @Synchronized
    fun update(online: Boolean): Boolean {
        val recovered = !current && online
        current = online
        return recovered
    }
}
