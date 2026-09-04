package com.minipay.bridge

import java.net.URI

enum class FoodBridgeMessageType {
    BRIDGE_READY,
    REQUEST_AUTHORIZATION,
    CLOSE,
    REQUEST_NATIVE_PAYMENT,
    AUTHORIZATION_CODE,
    AUTHORIZATION_CHANGED,
    PAYMENT_RESULT,
    NAVIGATION_STATE,
    REQUEST_LOCATION_CONTEXT,
    NAVIGATE_BACK,
    LOCATION_CONTEXT,
    REQUEST_WALLET_BALANCE,
    WALLET_BALANCE,
}

/** Rules shared by the Android WebView host and unit tests; no Android SDK dependency. */
object FoodBridgePolicy {
    fun isTrustedFoodOrigin(
        url: String,
        configuredOrigin: String,
        allowLoopbackHttp: Boolean = false
    ): Boolean {
        if (configuredOrigin.isBlank()) return false

        return runCatching {
            val candidate = URI(url)
            val expected = URI(configuredOrigin)
            val isHttps = candidate.scheme.equals("https", ignoreCase = true) &&
                expected.scheme.equals("https", ignoreCase = true)
            val isDebugLoopbackHttp = allowLoopbackHttp &&
                candidate.scheme.equals("http", ignoreCase = true) &&
                expected.scheme.equals("http", ignoreCase = true) &&
                isLoopback(candidate.host) && isLoopback(expected.host)
            (isHttps || isDebugLoopbackHttp) &&
                candidate.host.equals(expected.host, ignoreCase = true) &&
                candidate.port == expected.port &&
                candidate.userInfo == null
        }.getOrDefault(false)
    }

    private fun isLoopback(host: String?): Boolean =
        host.equals("localhost", ignoreCase = true) || host == "127.0.0.1" || host == "::1"

    fun isSupportedMessage(type: String): Boolean =
        type in FoodBridgeMessageType.entries.map(FoodBridgeMessageType::name)

    fun isSupportedIncomingMessage(type: String): Boolean = type in setOf(
        FoodBridgeMessageType.BRIDGE_READY.name,
        FoodBridgeMessageType.REQUEST_AUTHORIZATION.name,
        FoodBridgeMessageType.REQUEST_NATIVE_PAYMENT.name,
        FoodBridgeMessageType.NAVIGATION_STATE.name,
        FoodBridgeMessageType.REQUEST_LOCATION_CONTEXT.name,
        FoodBridgeMessageType.REQUEST_WALLET_BALANCE.name,
        FoodBridgeMessageType.CLOSE.name
    )

    fun allowsSensitiveField(payload: String): Boolean =
        listOf("token", "password", "amount", "paymentAuthToken").none { blocked ->
            payload.contains(blocked, ignoreCase = true)
        }
}
