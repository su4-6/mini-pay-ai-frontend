package com.minipay.mobile.merchant

import org.junit.Assert.assertEquals
import org.junit.Test

class MerchantMapErrorTest {
    @Test
    fun `amap platform mismatch is reported as a credential error`() {
        assertEquals(
            "高德地图 Key 与应用包名、签名或服务平台不匹配",
            merchantMapErrorMessage(1008)
        )
        assertEquals(
            "高德地图 Key 与应用包名、签名或服务平台不匹配",
            merchantMapErrorMessage(1009)
        )
        assertEquals(
            "高德地图 Key 与应用包名、签名或服务平台不匹配",
            merchantMapErrorMessage(10009)
        )
    }

    @Test
    fun `other amap failures retain their error code`() {
        assertEquals("无法获取所选位置地址（错误码 1103）", merchantMapErrorMessage(1103))
    }
}
