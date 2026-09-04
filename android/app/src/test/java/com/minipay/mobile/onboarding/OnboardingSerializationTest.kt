package com.minipay.mobile.onboarding

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingSerializationTest {
    @Test
    fun requestContainsOnlyProfileFields() {
        val encoded = Json.encodeToString(
            CompleteOnboardingRequest("Mini User", "018f0f57-7b8c-7000-8000-000000000001")
        )
        assertTrue(encoded.contains("\"avatarUploadId\""))
        assertFalse(encoded.contains("Password", ignoreCase = true))
        assertFalse(encoded.contains("avatarObjectKey"))
    }
}
