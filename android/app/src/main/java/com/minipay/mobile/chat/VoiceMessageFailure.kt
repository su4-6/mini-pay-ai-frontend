package com.minipay.mobile.chat

import java.io.IOException

enum class VoiceSendStage {
    PREPARE_RECORDING,
    REQUEST_UPLOAD,
    UPLOAD_BYTES,
    COMPLETE_UPLOAD,
    SEND_MESSAGE
}

class VoiceSendException(
    val stage: VoiceSendStage,
    cause: Throwable
) : RuntimeException("Voice send failed at $stage", cause)

internal fun voiceSendErrorMessage(error: Throwable): String {
    val causes = generateSequence(error) { it.cause }.toList()
    val apiError = causes.filterIsInstance<ChatApiException>().firstOrNull()
    if (apiError?.code == "NOT_AUTHENTICATED") return "登录已失效，请重新登录"
    if (causes.any { it is IOException } || apiError?.code == "NETWORK_UNAVAILABLE") {
        return "网络不可用，请检查连接后重试"
    }
    return when ((error as? VoiceSendException)?.stage) {
        VoiceSendStage.PREPARE_RECORDING -> "无法读取录音文件，请重新录制"
        VoiceSendStage.REQUEST_UPLOAD -> "无法申请语音上传凭证，请检查语音服务配置"
        VoiceSendStage.UPLOAD_BYTES -> "语音上传失败，请重试"
        VoiceSendStage.COMPLETE_UPLOAD -> "语音文件校验失败，请重新录制"
        VoiceSendStage.SEND_MESSAGE -> "语音已上传，但消息发送失败，请重试"
        null -> "语音发送失败，请重试"
    }
}
