package com.minipay.mobile.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommonAppsTest {
    @Test
    fun defaultsResolveToFiveAvailableAppsInConfiguredOrder() {
        val apps = resolveCommonApps(defaultCommonAppIds)

        assertEquals(listOf("bills", "add_friend", "wallet", "receive", "scan"), apps.map { it.id })
        assertTrue(apps.all { it.available })
    }

    @Test
    fun sanitizingPreservesOrderAndRejectsDuplicatesUnknownAndUnavailableIds() {
        val ids = sanitizeCommonAppIds(
            listOf("transfer", "unknown", "transfer", "coupon", "wallet", "scan")
        )

        assertEquals(listOf("transfer", "wallet", "scan"), ids)
    }

    @Test
    fun sanitizingLimitsSelectionToFiveAndKeepsAnIntentionalEmptyList() {
        val allAvailable = listOf("scan", "receive", "transfer", "wallet", "bills", "add_friend")

        assertEquals(5, sanitizeCommonAppIds(allAvailable).size)
        assertTrue(sanitizeCommonAppIds(emptyList()).isEmpty())
    }
}
