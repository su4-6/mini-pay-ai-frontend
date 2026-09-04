package com.minipay.mobile.ui.scan

import java.net.URI

sealed interface MiniPayQrCode {
    val rawValue: String

    data class PersonalCollection(override val rawValue: String) : MiniPayQrCode

    data class MerchantCollection(override val rawValue: String) : MiniPayQrCode

    data class FriendCard(
        val miniPayNo: String,
        override val rawValue: String
    ) : MiniPayQrCode
}

fun parseMiniPayQrCode(value: String): MiniPayQrCode? = runCatching {
    val raw = value.trim()
    val uri = URI.create(raw)
    if (!uri.scheme.equals("minipay", ignoreCase = true) || uri.userInfo != null || uri.port != -1) {
        return@runCatching null
    }

    when {
        uri.host.equals("collect", ignoreCase = true) &&
            uri.path == "/personal" &&
            uri.fragment == null &&
            uri.rawQuery?.split('&')?.any { pair ->
                pair.substringBefore('=') == "token" && pair.substringAfter('=', "").isNotBlank()
            } == true -> MiniPayQrCode.PersonalCollection(raw)

        uri.host.equals("collect", ignoreCase = true) &&
            uri.path == "/merchant" &&
            uri.fragment == null &&
            uri.rawQuery?.split('&')?.any { pair ->
                pair.substringBefore('=') == "token" && pair.substringAfter('=', "").isNotBlank()
            } == true -> MiniPayQrCode.MerchantCollection(raw)

        uri.host.equals("friend", ignoreCase = true) &&
            uri.rawQuery == null &&
            uri.fragment == null -> {
            val segments = uri.path.orEmpty().split('/').filter { it.isNotBlank() }
            segments.singleOrNull()?.let { MiniPayQrCode.FriendCard(it, raw) }
        }

        else -> null
    }
}.getOrNull()

fun friendCardQrValue(miniPayNo: String): String {
    val normalized = miniPayNo.trim()
    require(normalized.isNotEmpty()) { "MiniPay number must not be blank" }
    return URI("minipay", "friend", "/$normalized", null).toASCIIString()
}
