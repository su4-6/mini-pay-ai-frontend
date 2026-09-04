package com.minipay.mobile.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AvatarCacheKeyTest {
    @Test
    fun signedUrlRefreshKeepsStableCacheKey() {
        val first = avatarCacheKey("https://oss.example.com/avatar/u1/v2.jpg?Expires=1&Signature=old")
        val refreshed = avatarCacheKey("https://oss.example.com/avatar/u1/v2.jpg?Expires=2&Signature=new")

        assertEquals(first, refreshed)
    }

    @Test
    fun replacedObjectChangesCacheKey() {
        val old = avatarCacheKey("https://oss.example.com/avatar/u1/v2.jpg?Signature=a")
        val replacement = avatarCacheKey("https://oss.example.com/avatar/u1/v3.jpg?Signature=b")

        assertNotEquals(old, replacement)
    }
}
