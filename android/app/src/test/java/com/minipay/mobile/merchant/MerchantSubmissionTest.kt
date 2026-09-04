package com.minipay.mobile.merchant

import org.junit.Assert.assertEquals
import org.junit.Test

class MerchantSubmissionTest {
    @Test
    fun `new and resubmitted applications always use individual merchant type`() {
        listOf<Long?>(null, 7L).forEach { version ->
            val submission = consumerMerchantSubmission(
                version = version,
                shopName = " 示例店铺 ",
                address = "上海市浦东新区",
                latitude = 31.23,
                longitude = 121.47,
                imageKeys = listOf("merchant/apply/shop.jpg"),
                contactName = "张*",
                contactMobile = "13800000000"
            )

            assertEquals("INDIVIDUAL", submission.merchantType)
            assertEquals(version, submission.version)
            assertEquals("示例店铺", submission.shopName)
        }
    }
}
