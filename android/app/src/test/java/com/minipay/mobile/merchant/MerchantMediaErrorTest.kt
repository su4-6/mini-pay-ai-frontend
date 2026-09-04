package com.minipay.mobile.merchant

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

class MerchantMediaErrorTest {
    @Test
    fun `camera preparation failures have actionable messages`() {
        assertEquals(
            "无法准备拍照文件，请稍后重试",
            merchantCameraErrorMessage(IOException("disk unavailable"))
        )
        assertEquals(
            "无法准备拍照文件，请稍后重试",
            merchantCameraErrorMessage(IllegalArgumentException("provider path mismatch"))
        )
        assertEquals(
            "无法启动相机，请检查相机权限后重试",
            merchantCameraErrorMessage(SecurityException("camera permission denied"))
        )
    }

    @Test
    fun `image processing failures distinguish the size limit`() {
        assertEquals(
            "图片压缩后仍超过 5MB，请选择较小的图片",
            merchantImageErrorMessage(IllegalArgumentException("图片压缩后仍超过 5MB"))
        )
        assertEquals(
            "照片处理失败，请重新选择或拍摄",
            merchantImageErrorMessage(IllegalStateException("cannot decode"))
        )
    }
}
