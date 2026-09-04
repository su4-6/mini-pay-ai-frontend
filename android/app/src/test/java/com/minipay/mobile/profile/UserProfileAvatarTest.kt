package com.minipay.mobile.profile

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserProfileAvatarTest {
    private val now = Instant.parse("2026-08-10T00:00:00Z")

    @Test
    fun `uses avatar while signed url is safely valid`() {
        assertEquals("https://example.test/avatar", profile("2026-08-10T00:01:00Z").usableAvatarUrl(now))
    }

    @Test
    fun `hides expired or nearly expired signed avatar`() {
        assertNull(profile("2026-08-10T00:00:20Z").usableAvatarUrl(now))
        assertNull(profile("2026-08-09T23:59:59Z").usableAvatarUrl(now))
    }

    @Test
    fun `keeps compatible avatar when expiry is absent`() {
        assertEquals("https://example.test/avatar", profile(null).usableAvatarUrl(now))
    }

    private fun profile(expiresAt: String?) = UserProfile(
        userId = "user-1",
        nickname = "收款人",
        miniPayNo = "100001",
        avatarUrl = "https://example.test/avatar",
        avatarUrlExpiresAt = expiresAt,
        version = 1
    )
}
