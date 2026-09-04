package com.minipay.mobile.auth

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthRequestSerializationTest {
    private val json = AuthModule.provideJson()

    @Test
    fun `send code includes required purpose`() {
        val body = json.parseToJsonElement(
            json.encodeToString(SendCodeRequest(mobile = "13800138000"))
        ).jsonObject

        assertEquals("LOGIN", body.getValue("purpose").jsonPrimitive.content)
    }

    @Test
    fun `verify code includes required PKCE method`() {
        val body = json.parseToJsonElement(
            json.encodeToString(
                VerifyCodeRequest(
                    challengeId = "challenge",
                    code = "123456",
                    clientId = "minipay-android",
                    redirectUri = "com.minipay.mobile:/oauth2redirect",
                    codeChallenge = "challenge-value",
                    deviceId = "device"
                )
            )
        ).jsonObject

        assertEquals("S256", body.getValue("codeChallengeMethod").jsonPrimitive.content)
    }
}
