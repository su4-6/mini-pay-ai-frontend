package com.minipay.mobile.merchant

import android.content.ActivityNotFoundException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MerchantCameraConfigurationTest {
    @Test
    fun cameraTargetUsesTheScopedCacheDirectoryAndFileProvider() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val target = createMerchantCameraTarget(context)

        try {
            assertEquals(
                File(context.cacheDir, MERCHANT_CAMERA_DIRECTORY).canonicalFile,
                target.file.parentFile?.canonicalFile
            )
            assertEquals("content", target.uri.scheme)
            context.contentResolver.openFileDescriptor(target.uri, "rw").use { descriptor ->
                assertNotNull(descriptor)
            }
        } finally {
            assertTrue(target.file.delete() || !target.file.exists())
        }
    }

    @Test
    fun missingCameraApplicationHasAnActionableMessage() {
        assertEquals(
            "设备上没有可用的相机应用，请从相册选择",
            merchantCameraErrorMessage(ActivityNotFoundException())
        )
    }
}
