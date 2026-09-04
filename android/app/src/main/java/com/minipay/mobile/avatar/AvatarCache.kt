package com.minipay.mobile.avatar

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

private const val AVATAR_CACHE_PREFIX = "avatar:"

/**
 * Returns the stable identity of the object behind an avatar URL.
 *
 * Aliyun OSS read URLs are re-signed whenever chat data is refreshed. Their query string changes
 * even though the object does not, so it must not participate in Coil's cache key. Query strings
 * on ordinary URLs remain significant because they can carry a real content version.
 */
internal fun avatarContentIdentity(url: String): String {
    val normalized = url.trim().substringBefore('#')
    val queryStart = normalized.indexOf('?')
    if (queryStart < 0) return normalized

    val queryKeys = normalized.substring(queryStart + 1)
        .split('&')
        .asSequence()
        .mapNotNull { parameter ->
            parameter.substringBefore('=').takeIf(String::isNotBlank)?.let { encoded ->
                runCatching { URLDecoder.decode(encoded, StandardCharsets.UTF_8.name()) }
                    .getOrDefault(encoded)
                    .lowercase(Locale.ROOT)
            }
        }
        .toSet()

    val isAliyunSignedUrl = "ossaccesskeyid" in queryKeys ||
        queryKeys.any { it.startsWith("x-oss-") }
    return if (isAliyunSignedUrl) normalized.substring(0, queryStart) else normalized
}

internal fun avatarDiskCacheKey(url: String): String =
    AVATAR_CACHE_PREFIX + avatarContentIdentity(url)

internal fun avatarMemoryCacheKey(url: String, sizePx: Int): String =
    "${avatarDiskCacheKey(url)}:${sizePx}px"
