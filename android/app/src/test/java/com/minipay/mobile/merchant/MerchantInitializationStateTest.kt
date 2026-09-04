package com.minipay.mobile.merchant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MerchantInitializationStateTest {
    @Test
    fun prefersTopLevelQrContentWhenEnabled() {
        val initialization = MerchantInitialization(
            merchant = MerchantSummary("merchant-1", "测试商户"),
            collectionCode = MerchantCollectionCode("minipay://nested", "ENABLED"),
            qrContent = " minipay://top-level "
        )

        assertEquals("minipay://top-level", merchantQrContent(initialization))
    }

    @Test
    fun fallsBackToNestedQrContentForCompatibleResponses() {
        val initialization = MerchantInitialization(
            merchant = MerchantSummary("merchant-1", "测试商户"),
            collectionCode = MerchantCollectionCode(" minipay://nested ", "ENABLED")
        )

        assertEquals("minipay://nested", merchantQrContent(initialization))
    }

    @Test
    fun rejectsBlankOrDisabledCollectionCodes() {
        assertNull(
            merchantQrContent(
                MerchantInitialization(
                    MerchantSummary("merchant-1", "测试商户"),
                    MerchantCollectionCode(" ", "ENABLED")
                )
            )
        )
        assertNull(
            merchantQrContent(
                MerchantInitialization(
                    MerchantSummary("merchant-1", "测试商户"),
                    MerchantCollectionCode("minipay://disabled", "DISABLED"),
                    "minipay://disabled"
                )
            )
        )
    }
}
