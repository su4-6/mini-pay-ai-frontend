package com.minipay.mobile.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PkceTest {
    @Test
    fun challengeMatchesRfc7636Vector() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"

        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            Pkce.challenge(verifier)
        )
    }

    @Test
    fun generatedPairUsesUrlSafeVerifierAndS256Challenge() {
        val pair = Pkce.generate()

        assertTrue(pair.verifier.length in 43..128)
        assertTrue(pair.verifier.matches(Regex("^[A-Za-z0-9_-]+$")))
        assertEquals(Pkce.challenge(pair.verifier), pair.challenge)
    }
}
