package com.minipay.mobile.chat

import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ChatMediaSupportTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun calculatesDigestFromFileContents() {
        val bytes = "streamed-media".encodeToByteArray()
        val file = temporaryFolder.newFile("media.bin").apply { writeBytes(bytes) }
        val expected = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

        assertEquals(expected, sha256(file))
    }

    @Test fun mapsPhotoSendStagesAndUploadFailures() {
        assertEquals(
            "无法读取所选照片，请重新选择",
            chatMediaSendErrorMessage(ChatMediaSendException(ChatMediaSendStage.PREPARE_FILE, RuntimeException()))
        )
        assertEquals(
            "照片上传超时，请检查网络后重试",
            chatMediaSendErrorMessage(
                ChatMediaSendException(
                    ChatMediaSendStage.UPLOAD_FILE,
                    ChatApiException("CHAT_MEDIA_UPLOAD_TIMEOUT")
                )
            )
        )
        assertEquals(
            "上传凭证已失效，请重新发送",
            chatMediaSendErrorMessage(
                ChatMediaSendException(
                    ChatMediaSendStage.UPLOAD_FILE,
                    ChatApiException("CHAT_MEDIA_UPLOAD_EXPIRED")
                )
            )
        )
        assertEquals(
            "媒体已上传，但消息发送失败，请重新发送",
            chatMediaSendErrorMessage(ChatMediaSendException(ChatMediaSendStage.SEND_MESSAGE, RuntimeException()))
        )
    }
}
