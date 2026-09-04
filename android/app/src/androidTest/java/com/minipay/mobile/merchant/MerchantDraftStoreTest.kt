package com.minipay.mobile.merchant

import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MerchantDraftStoreTest {
    @Test
    fun draftSurvivesStoreRecreationAndIsIsolatedByUser() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val firstUser = "draft-test-${UUID.randomUUID()}"
        val secondUser = "draft-test-${UUID.randomUUID()}"
        val draft = MerchantOnboardingDraft(
            shopName = "持久化店铺",
            address = "河南省洛阳市",
            latitude = 34.6197,
            longitude = 112.4540,
            imageKeys = listOf("merchant/shop/persisted.jpg")
        )

        val firstStore = MerchantDraftStore(context)
        try {
            firstStore.save(firstUser, draft)

            val recreatedStore = MerchantDraftStore(context)
            assertEquals(draft, recreatedStore.load(firstUser))
            assertNull(recreatedStore.load(secondUser))

            recreatedStore.clear(firstUser)
            assertNull(recreatedStore.load(firstUser))
        } finally {
            firstStore.clear(firstUser)
            firstStore.clear(secondUser)
        }
    }
}
