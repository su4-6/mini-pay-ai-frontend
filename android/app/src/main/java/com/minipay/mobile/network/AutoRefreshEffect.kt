package com.minipay.mobile.network

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce

/** Refreshes only the currently resumed destination after reconnection or a later resume. */
@OptIn(FlowPreview::class)
@Composable
internal fun AutoRefreshEffect(
    enabled: Boolean = true,
    statusProvider: NetworkStatusProvider? = null,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val monitor = statusProvider ?: remember(context) {
        EntryPoints.get(context, NetworkMonitorEntryPoint::class.java).networkMonitor()
    }
    val latestEnabled by rememberUpdatedState(enabled)
    val latestRefresh by rememberUpdatedState(onRefresh)
    val triggers = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }

    DisposableEffect(lifecycleOwner, triggers) {
        var hasResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (hasResumed) triggers.tryEmit(Unit) else hasResumed = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(lifecycleOwner, monitor, triggers) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            monitor.reconnections.collect { triggers.emit(Unit) }
        }
    }

    LaunchedEffect(lifecycleOwner, triggers) {
        triggers.debounce(REFRESH_COALESCE_MILLIS).collect {
            if (latestEnabled && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                latestRefresh()
            }
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface NetworkMonitorEntryPoint {
    fun networkMonitor(): NetworkMonitor
}

private const val REFRESH_COALESCE_MILLIS = 300L
