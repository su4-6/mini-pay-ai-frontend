package com.minipay.mobile.ui.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniPayQrCodeTest {
    @Test
    fun generatedFriendCardRoundTripsThroughSharedParser() {
        val value = friendCardQrValue("MP20260808001")

        val parsed = parseMiniPayQrCode(value)

        assertEquals("minipay://friend/MP20260808001", value)
        assertTrue(parsed is MiniPayQrCode.FriendCard)
        assertEquals("MP20260808001", (parsed as MiniPayQrCode.FriendCard).miniPayNo)
    }

    @Test
    fun parserAcceptsPersonalCollectionCode() {
        val value = "minipay://collect/personal?token=token-value"

        assertEquals(MiniPayQrCode.PersonalCollection(value), parseMiniPayQrCode(value))
    }

    @Test
    fun parserAcceptsMerchantCollectionCode() {
        val value = "minipay://collect/merchant?token=merchant-token"

        assertEquals(MiniPayQrCode.MerchantCollection(value), parseMiniPayQrCode(value))
    }

    @Test
    fun parserRejectsBlankMalformedAndUnsupportedCodes() {
        assertNull(parseMiniPayQrCode(""))
        assertNull(parseMiniPayQrCode("https://friend/MP001"))
        assertNull(parseMiniPayQrCode("minipay://friend/"))
        assertNull(parseMiniPayQrCode("minipay://friend/MP001?source=other"))
        assertNull(parseMiniPayQrCode("minipay://collect/personal"))
    }
}
