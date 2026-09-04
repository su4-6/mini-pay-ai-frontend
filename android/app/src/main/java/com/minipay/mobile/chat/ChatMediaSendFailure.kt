package com.minipay.mobile.chat

enum class ChatMediaSendStage {
    PREPARE_FILE,
    REQUEST_UPLOAD,
    UPLOAD_FILE,
    COMPLETE_UPLOAD,
    SEND_MESSAGE
}

class ChatMediaSendException(
    val stage: ChatMediaSendStage,
    cause: Throwable
) : RuntimeException("Chat media send failed at $stage", cause)

internal fun chatMediaSendErrorMessage(error: Throwable): String {
    if (error is ChatMediaPreparationException) {
        return error.message ?: "照片处理失败，请重新选择"
    }
    val causes = generateSequence(error) { it.cause }.toList()
    val apiError = causes.filterIsInstance<ChatApiException>().firstOrNull()
    return when (apiError?.code) {
        "NOT_AUTHENTICATED" -> "登录已失效，请重新登录"
        "CHAT_MEDIA_FILE_UNREADABLE" -> "无法读取所选照片，请重新选择"
        "CHAT_MEDIA_FILE_TOO_LARGE" -> "所选照片超过10MB限制"
        "CHAT_MEDIA_TYPE_UNSUPPORTED" -> "聊天页面已不支持发送视频"
        "CHAT_MEDIA_UPLOAD_NETWORK_FAILED", "NETWORK_UNAVAILABLE" -> "网络连接失败，请检查网络后重试"
        "CHAT_MEDIA_UPLOAD_TIMEOUT" -> "照片上传超时，请检查网络后重试"
        "CHAT_MEDIA_UPLOAD_EXPIRED" -> "上传凭证已失效，请重新发送"
        else -> when ((error as? ChatMediaSendException)?.stage) {
            ChatMediaSendStage.PREPARE_FILE -> "无法读取所选照片，请重新选择"
            ChatMediaSendStage.REQUEST_UPLOAD -> "无法申请媒体上传凭证，请稍后重试"
            ChatMediaSendStage.UPLOAD_FILE -> "照片上传失败，请重试"
            ChatMediaSendStage.COMPLETE_UPLOAD -> "媒体文件校验失败，请重新选择后发送"
            ChatMediaSendStage.SEND_MESSAGE -> "媒体已上传，但消息发送失败，请重新发送"
            null -> "照片发送失败，请重试"
        }
    }
}
