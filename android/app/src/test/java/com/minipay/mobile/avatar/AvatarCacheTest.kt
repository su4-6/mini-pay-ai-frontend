package com.minipay.mobile.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AvatarCacheTest {
    @Test
    fun `legacy aliyun signatures share a stable identity`() {
        val first = "https://bucket.oss-cn-beijing.aliyuncs.com/avatars/u/photo.jpg" +
            "?OSSAccessKeyId=first&Expires=100&Signature=one"
        val second = "https://bucket.oss-cn-beijing.aliyuncs.com/avatars/u/photo.jpg" +
            "?OSSAccessKeyId=second&Expires=200&Signature=two"

        assertEquals(avatarContentIdentity(first), avatarContentIdentity(second))
        assertEquals(avatarDiskCacheKey(first), avatarDiskCacheKey(second))
        assertEquals(avatarMemoryCacheKey(first, 144), avatarMemoryCacheKey(second, 144))
    }

    @Test
    fun `aliyun v4 signatures share a stable identity`() {
        val first = "https://bucket.oss-cn-beijing.aliyuncs.com/avatars/u/photo.jpg" +
            "?x-oss-signature=one&x-oss-date=20260809T040000Z"
        val second = "https://bucket.oss-cn-beijing.aliyuncs.com/avatars/u/photo.jpg" +
            "?x-oss-signature=two&x-oss-date=20260809T041000Z"

        assertEquals(avatarContentIdentity(first), avatarContentIdentity(second))
    }

    @Test
    fun `different object paths and image sizes remain distinct`() {
        val first = "https://bucket.oss-cn-beijing.aliyuncs.com/avatars/u/first.jpg?OSSAccessKeyId=a"
        val second = "https://bucket.oss-cn-beijing.aliyuncs.com/avatars/u/second.jpg?OSSAccessKeyId=a"

        assertNotEquals(avatarDiskCacheKey(first), avatarDiskCacheKey(second))
        assertNotEquals(avatarMemoryCacheKey(first, 96), avatarMemoryCacheKey(first, 144))
    }

    @Test
    fun `ordinary query versions remain part of the identity`() {
        val first = "https://cdn.example.test/avatar.jpg?v=1"
        val second = "https://cdn.example.test/avatar.jpg?v=2"

        assertNotEquals(avatarContentIdentity(first), avatarContentIdentity(second))
    }
}
