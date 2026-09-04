package com.minipay.mobile.authorization

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplicationAuthorizationContractTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun yshopGrantCarriesChallengeWithoutChangingMiniPayPhone() {
        val encoded = json.encodeToString(
            AuthorizationGrantRequest(
                scopes = setOf("profile.basic", "profile.phone", "location.current"),
                consentVersion = 2,
                phoneChallengeId = "019f0000-0000-7000-8000-000000000001",
                verificationCode = "123456"
            )
        )

        assertTrue(encoded.contains("location.current"))
        assertTrue(encoded.contains("phoneChallengeId"))
        assertTrue(encoded.contains("verificationCode"))
    }

    @Test
    fun applicationChallengeAcceptsMaskedMobile() {
        val challenge = json.decodeFromString<VerificationChallengeDto>(
            """{"challengeId":"c1","expiresAt":"2026-08-09T16:00:00Z","resendAfterSeconds":60,"maskedMobile":"138****8000"}"""
        )

        assertEquals("138****8000", challenge.maskedMobile)
        assertEquals(60, challenge.resendAfterSeconds)
    }

    @Test
    fun foodBindingUsernameIsOptionalForBackwardCompatibility() {
        val oldResponse = json.decodeFromString<com.minipay.mobile.ai.FoodBindingDto>(
            """{"provider":"YSHOP","subject":"subject-1","active":true}"""
        )
        val newResponse = json.decodeFromString<com.minipay.mobile.ai.FoodBindingDto>(
            """{"provider":"YSHOP","subject":"subject-1","active":true,"username":"taobao7"}"""
        )

        assertEquals(null, oldResponse.username)
        assertEquals("taobao7", newResponse.username)
    }

    @Test
    fun externalUsernameUsesReferenceStyleMasking() {
        assertEquals("t***7", maskedExternalUsername("taobao7"))
        assertEquals("m***f", maskedExternalUsername("minipay_019fea85f"))
        assertEquals("已绑定", maskedExternalUsername(null))
        assertEquals("*", maskedExternalUsername("a"))
    }
}
