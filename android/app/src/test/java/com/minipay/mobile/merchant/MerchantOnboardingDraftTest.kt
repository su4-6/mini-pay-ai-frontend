package com.minipay.mobile.merchant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MerchantOnboardingDraftTest {
    @Test
    fun `confirmed selection atomically replaces location and keeps other draft fields`() {
        val original = MerchantOnboardingDraft(
            shopName = "测试店铺",
            address = "旧地址",
            latitude = 34.0,
            longitude = 112.0,
            imageKeys = listOf("merchant/shop/one.jpg")
        )

        val selected = original.withSelectedLocation(35.0, 113.0, "新地址")

        assertEquals(35.0, selected.latitude)
        assertEquals(113.0, selected.longitude)
        assertEquals("新地址", selected.address)
        assertEquals("测试店铺", selected.shopName)
        assertEquals(listOf("merchant/shop/one.jpg"), selected.imageKeys)
    }

    @Test
    fun `location changes retain uploaded photos and reject stale address callbacks`() {
        val original = MerchantOnboardingDraft(
            shopName = "测试店铺",
            address = "旧地址",
            latitude = 34.0,
            longitude = 112.0,
            imageKeys = listOf("merchant/shop/one.jpg")
        )

        val moved = original.withLocation(35.0, 113.0)
        val staleResult = moved.withResolvedAddress(34.0, 112.0, "过期地址")
        val resolved = staleResult.withResolvedAddress(35.0, 113.0, "新地址")

        assertNull(moved.address)
        assertEquals(listOf("merchant/shop/one.jpg"), moved.imageKeys)
        assertNull(staleResult.address)
        assertEquals("新地址", resolved.address)
    }

    @Test
    fun `uploaded image keys are unique limited and removable without clearing location`() {
        val point = MerchantOnboardingDraft().withLocation(34.6197, 112.4540)
        val withImages = (1..6).fold(point) { draft, index ->
            draft.withImage("merchant/shop/$index.jpg")
        }.withImage("merchant/shop/1.jpg")

        assertEquals(5, withImages.imageKeys.size)
        assertEquals(34.6197, withImages.latitude)
        assertEquals(112.4540, withImages.longitude)

        val removed = withImages.withoutImage("merchant/shop/3.jpg")
        assertEquals(4, removed.imageKeys.size)
        assertEquals(34.6197, removed.latitude)
        assertEquals(112.4540, removed.longitude)
    }
}
