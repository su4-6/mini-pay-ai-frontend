package com.minipay.mobile.auth

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessTokenClaimsTest {
    @Test
    fun parsesAccountClaimsFromJwtPayload() {
        val claims = AccessTokenClaims.parse(jwt(onboardingCompleted = false))

        assertEquals(USER_ID, claims.userId)
        assertTrue(claims.payPasswordSet)
        assertTrue(claims.onboardingRequired)
    }

    @Test
    fun completedClaimRemovesOnboardingRequirement() {
        val claims = AccessTokenClaims.parse(jwt(onboardingCompleted = true))

        assertFalse(claims.onboardingRequired)
    }

    @Test
    fun rejectsMalformedOrUnownedTokenPayload() {
        assertThrows(IdentityApiException::class.java) {
            AccessTokenClaims.parse("not-a-jwt")
        }
    }

    private fun jwt(onboardingCompleted: Boolean): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("{\"alg\":\"none\"}".toByteArray())
        val payload = encoder.encodeToString((
            "{\"user_id\":\"$USER_ID\",\"pay_password_set\":true," +
                "\"onboarding_completed\":$onboardingCompleted}"
            ).toByteArray()
        )
        return "$header.$payload.signature"
    }

    private companion object {
        const val USER_ID = "018f0f5d-52c7-7b8d-9f22-6f858e711001"
    }
}
