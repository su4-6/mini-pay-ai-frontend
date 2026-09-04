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
