package com.minipay.mobile.ui.home

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.SIMPLIFIED_CHINESE)
private val dateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.SIMPLIFIED_CHINESE)
private val yearDateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", Locale.SIMPLIFIED_CHINESE)

internal fun shouldShowAiMessageTime(current: Instant?, previous: Instant?): Boolean {
    if (current == null) return false
    if (previous == null) return true
    return current.epochSecond / 60 != previous.epochSecond / 60
}

internal fun formatAiMessageTime(
    timestamp: Instant,
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault()
): String {
    val messageDateTime = timestamp.atZone(zoneId)
    val today = now.atZone(zoneId).toLocalDate()
    val messageDate = messageDateTime.toLocalDate()
    return when {
        messageDate == today -> timeFormatter.format(messageDateTime)
        messageDate == today.minusDays(1) -> "昨天 ${timeFormatter.format(messageDateTime)}"
        messageDate.year == today.year -> dateTimeFormatter.format(messageDateTime)
        else -> yearDateTimeFormatter.format(messageDateTime)
    }
}
