package com.minipay.mobile.chat

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceMessageFailureTest {
    @Test fun mapsEveryVoiceSendStageToAnActionableMessage() {
        assertEquals(
            "无法读取录音文件，请重新录制",
            voiceSendErrorMessage(failure(VoiceSendStage.PREPARE_RECORDING))
        )
        assertEquals(
            "无法申请语音上传凭证，请检查语音服务配置",
            voiceSendErrorMessage(failure(VoiceSendStage.REQUEST_UPLOAD))
        )
        assertEquals(
            "语音上传失败，请重试",
            voiceSendErrorMessage(failure(VoiceSendStage.UPLOAD_BYTES))
        )
        assertEquals(
            "语音文件校验失败，请重新录制",
            voiceSendErrorMessage(failure(VoiceSendStage.COMPLETE_UPLOAD))
        )
        assertEquals(
            "语音已上传，但消息发送失败，请重试",
            voiceSendErrorMessage(failure(VoiceSendStage.SEND_MESSAGE))
        )
    }

    @Test fun authenticationAndNetworkFailuresTakePriorityOverStage() {
        assertEquals(
            "登录已失效，请重新登录",
            VoiceSendException(
                VoiceSendStage.REQUEST_UPLOAD,
                ChatApiException("NOT_AUTHENTICATED")
            ).let(::voiceSendErrorMessage)
        )
        assertEquals(
            "网络不可用，请检查连接后重试",
            VoiceSendException(
                VoiceSendStage.UPLOAD_BYTES,
                IOException("offline")
            ).let(::voiceSendErrorMessage)
        )
    }

    private fun failure(stage: VoiceSendStage) =
        VoiceSendException(stage, ChatApiException("REQUEST_FAILED"))
}
