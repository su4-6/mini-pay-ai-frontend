package com.minipay.mobile.chat

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AvatarReferenceTest {
    private val now = Instant.parse("2026-08-09T04:00:00Z")

    @Test
    fun `latest usable signed avatar wins`() {
        val selected = selectUsableAvatar(
            AvatarReference("https://example.test/new.jpg", "2026-08-09T04:30:00Z"),
            AvatarReference("https://example.test/cached.jpg", "2026-08-09T04:20:00Z"),
            now = now
        )

        assertEquals("https://example.test/new.jpg", selected?.url)
    }

    @Test
    fun `usable cache survives temporary signing failure`() {
        val selected = selectUsableAvatar(
            AvatarReference(null, null),
            AvatarReference("https://example.test/cached.jpg", "2026-08-09T04:20:00Z"),
            now = now
        )

        assertEquals("https://example.test/cached.jpg", selected?.url)
    }

    @Test
    fun `expired and malformed signed urls fall back to default avatar`() {
        val selected = selectUsableAvatar(
            AvatarReference("https://example.test/expired.jpg", "2026-08-09T03:59:59Z"),
            AvatarReference("https://example.test/malformed.jpg", "not-an-instant"),
            now = now
        )

        assertNull(selected)
    }

    @Test
    fun `non expiring avatar remains backward compatible`() {
        val selected = selectUsableAvatar(
            AvatarReference("https://example.test/static.jpg", null),
            now = now
        )

        assertEquals("https://example.test/static.jpg", selected?.url)
    }

    @Test
    fun `same oss object keeps usable cached signature`() {
        val cached = AvatarReference(
            "https://bucket.oss-cn-beijing.aliyuncs.com/avatars/user/photo.jpg" +
                "?OSSAccessKeyId=old&Expires=1&Signature=old",
            "2026-08-09T04:20:00Z"
        )
        val remote = AvatarReference(
            "https://bucket.oss-cn-beijing.aliyuncs.com/avatars/user/photo.jpg" +
                "?OSSAccessKeyId=new&Expires=2&Signature=new",
            "2026-08-09T04:30:00Z"
        )

        assertEquals(cached, selectStableAvatar(remote, cached, now))
    }

    @Test
    fun `near expiry cached signature is replaced`() {
        val cached = AvatarReference(
            "https://bucket.oss-cn-beijing.aliyuncs.com/avatars/user/photo.jpg" +
                "?OSSAccessKeyId=old&Expires=1&Signature=old",
            "2026-08-09T04:00:20Z"
        )
        val remote = AvatarReference(
            "https://bucket.oss-cn-beijing.aliyuncs.com/avatars/user/photo.jpg" +
                "?OSSAccessKeyId=new&Expires=2&Signature=new",
            "2026-08-09T04:30:00Z"
        )

        assertEquals(remote, selectStableAvatar(remote, cached, now))
    }

    @Test
    fun `different object path replaces cached avatar immediately`() {
        val cached = AvatarReference(
            "https://bucket.oss-cn-beijing.aliyuncs.com/avatars/user/old.jpg?OSSAccessKeyId=old",
            "2026-08-09T04:20:00Z"
        )
        val remote = AvatarReference(
            "https://bucket.oss-cn-beijing.aliyuncs.com/avatars/user/new.jpg?OSSAccessKeyId=new",
            "2026-08-09T04:30:00Z"
        )

        assertEquals(remote, selectStableAvatar(remote, cached, now))
    }

    @Test
    fun `usable cache survives missing remote signature`() {
        val cached = AvatarReference(
            "https://bucket.oss-cn-beijing.aliyuncs.com/avatars/user/photo.jpg?OSSAccessKeyId=old",
            "2026-08-09T04:20:00Z"
        )

        assertEquals(cached, selectStableAvatar(null, cached, now))
    }
}
