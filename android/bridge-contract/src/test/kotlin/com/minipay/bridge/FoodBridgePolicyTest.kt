package com.minipay.bridge

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FoodBridgePolicyTest {
    @Test
    fun onlyAllowsTheConfiguredHttpsOrigin() {
        assertTrue(FoodBridgePolicy.isTrustedFoodOrigin("https://food.example.com/menu", "https://food.example.com"))
        assertFalse(FoodBridgePolicy.isTrustedFoodOrigin("http://food.example.com/menu", "https://food.example.com"))
        assertFalse(FoodBridgePolicy.isTrustedFoodOrigin("https://attacker.example.com", "https://food.example.com"))
        assertFalse(FoodBridgePolicy.isTrustedFoodOrigin("https://user@food.example.com", "https://food.example.com"))
    }

    @Test
    fun debugOnlyAllowsLoopbackHttpOrigin() {
        assertTrue(FoodBridgePolicy.isTrustedFoodOrigin(
            "http://127.0.0.1:4173/", "http://127.0.0.1:4173", allowLoopbackHttp = true))
        assertFalse(FoodBridgePolicy.isTrustedFoodOrigin(
            "http://127.0.0.1:4173/", "http://127.0.0.1:4173"))
        assertFalse(FoodBridgePolicy.isTrustedFoodOrigin(
            "http://food.example.com/", "http://food.example.com", allowLoopbackHttp = true))
    }

    /**
     * 回归：开发环境用的是 food.minipay.localhost 这类 *.localhost 域名（RFC 6761 回环）。
     * 修复前 isLoopback 只认字面量 localhost/127.0.0.1/::1，会把它判为不可信来源，
     * 导致 App 丢弃 H5 的全部桥消息、H5 永远停在"游客"。
     */
    @Test
    fun debugAllowsSubdomainLocalhostHttpOrigin() {
        assertTrue(FoodBridgePolicy.isTrustedFoodOrigin(
            "http://food.minipay.localhost:18080/", "http://food.minipay.localhost:18080",
            allowLoopbackHttp = true))
        assertTrue(FoodBridgePolicy.isTrustedFoodOrigin(
            "http://food.minipay.localhost:18080/#/pages/index/index",
            "http://food.minipay.localhost:18080", allowLoopbackHttp = true))
        // 端口不一致仍然必须拒绝
        assertFalse(FoodBridgePolicy.isTrustedFoodOrigin(
            "http://food.minipay.localhost:9999/", "http://food.minipay.localhost:18080",
            allowLoopbackHttp = true))
        // 非回环域名即便开启 debug 也不放行
        assertFalse(FoodBridgePolicy.isTrustedFoodOrigin(
            "http://food.minipay.example.com/", "http://food.minipay.example.com",
            allowLoopbackHttp = true))
        // 伪装的 .localhost 后缀必须拒绝
        assertFalse(FoodBridgePolicy.isTrustedFoodOrigin(
            "http://evil-localhost.attacker.com/", "http://evil-localhost.attacker.com",
            allowLoopbackHttp = true))
    }

    @Test
    fun rejectsSensitiveBridgePayloadsAndUnknownMessages() {
        assertTrue(FoodBridgePolicy.isSupportedMessage("REQUEST_NATIVE_PAYMENT"))
        assertTrue(FoodBridgePolicy.isSupportedIncomingMessage("BRIDGE_READY"))
        assertTrue(FoodBridgePolicy.isSupportedIncomingMessage("NAVIGATION_STATE"))
        assertTrue(FoodBridgePolicy.isSupportedIncomingMessage("REQUEST_LOCATION_CONTEXT"))
        assertTrue(FoodBridgePolicy.isSupportedIncomingMessage("REQUEST_WALLET_BALANCE"))
        assertFalse(FoodBridgePolicy.isSupportedIncomingMessage("PAYMENT_RESULT"))
        assertFalse(FoodBridgePolicy.isSupportedIncomingMessage("WALLET_BALANCE"))
        assertFalse(FoodBridgePolicy.isSupportedMessage("EXECUTE_TRANSFER"))
        assertFalse(FoodBridgePolicy.allowsSensitiveField("{\"paymentAuthToken\":\"secret\"}"))
    }
}
