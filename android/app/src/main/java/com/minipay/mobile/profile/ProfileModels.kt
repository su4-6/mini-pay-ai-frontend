package com.minipay.mobile.profile

import java.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val userId: String,
    val nickname: String,
    val miniPayNo: String,
    val avatarUrl: String? = null,
    val avatarUrlExpiresAt: String? = null,
    val version: Long,
    val legalNameMasked: String? = null
)

fun UserProfile.usableAvatarUrl(
    now: Instant = Instant.now(),
    expirySafetySeconds: Long = 30
): String? {
    val url = avatarUrl?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val expiry = avatarUrlExpiresAt
        ?.let { runCatching { Instant.parse(it) }.getOrNull() }
        ?: return url
    return url.takeIf { expiry.isAfter(now.plusSeconds(expirySafetySeconds)) }
}

@Serializable
internal data class AvatarUploadRequest(val contentType: String, val sizeBytes: Long, val sha256: String)

@Serializable
internal data class AvatarUploadGrant(
    val uploadId: String,
    val uploadUrl: String,
    val requiredHeaders: Map<String, String>,
    val expiresAt: String
)

@Serializable
internal data class UpdateProfileRequest(
    val nickname: String,
    val avatarUploadId: String? = null,
    val version: Long
)

sealed interface ProfileLoadState {
    data object Loading : ProfileLoadState
    data class Ready(val profile: UserProfile) : ProfileLoadState
    data class Failed(val message: String) : ProfileLoadState
}

data class PreparedAvatar(val bytes: ByteArray, val sha256: String)

fun isNicknameValid(value: String): Boolean {
    val length = value.codePointCount(0, value.length)
    return length in 2..20 && value.codePoints().allMatch {
        it == '_'.code || Character.isLetterOrDigit(it) ||
            Character.UnicodeScript.of(it) == Character.UnicodeScript.HAN
    }
}
