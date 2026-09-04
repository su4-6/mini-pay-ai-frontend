package com.minipay.mobile.merchant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MerchantLocationPickerTest {
    @Test
    fun `resolved address prefers amap formatted address`() {
        assertEquals(
            "河南省洛阳市洛龙区开元大道",
            merchantPreferredAddress(
                " 河南省洛阳市洛龙区开元大道 ",
                "洛龙区开元大道"
            )
        )
    }

    @Test
    fun `poi address is used when reverse geocoding has no address`() {
        assertEquals(
            "洛龙区开元大道",
            merchantPreferredAddress(null, "洛龙区开元大道")
        )
        assertNull(merchantPreferredAddress(" ", null))
    }

    @Test
    fun `district is only prefixed when address does not already contain it`() {
        assertEquals("洛龙区开元大道", merchantCombinedAddress("洛龙区", "开元大道"))
        assertEquals("洛龙区开元大道", merchantCombinedAddress("洛龙区", "洛龙区开元大道"))
        assertEquals("洛龙区", merchantCombinedAddress("洛龙区", null))
    }

    @Test
    fun `coordinate label is locale stable`() {
        assertEquals("34.619700, 112.454000", merchantCoordinateLabel(34.6197, 112.4540))
    }
}
