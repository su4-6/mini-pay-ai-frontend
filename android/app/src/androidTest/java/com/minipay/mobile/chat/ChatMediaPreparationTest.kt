package com.minipay.mobile.chat

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMediaPreparationTest {
    @Test fun preparesImageFromProviderThatAllowsOnlyOneOpen() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val backingFile = File(app.cacheDir, "single-open-source.jpg")
        val bitmap = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888)
        FileOutputStream(backingFile).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        SingleOpenMediaProvider.configure(backingFile, "image/jpeg")

        val prepared = prepareChatImage(app, Uri.parse("content://$AUTHORITY/photo"))
        try {
            assertEquals(1, SingleOpenMediaProvider.openCount)
            assertEquals(MessageType.Image, prepared.messageType)
            assertEquals("image/jpeg", prepared.contentType)
            assertEquals(32, prepared.width)
            assertEquals(24, prepared.height)
            assertTrue(prepared.file.isFile)
            assertTrue(prepared.file.length() > 0)
            assertFalse(
                File(app.cacheDir, "chat-media").listFiles().orEmpty()
                    .any { it.name.startsWith("selected-") }
            )
        } finally {
            prepared.file.delete()
            backingFile.delete()
        }
    }

    private companion object {
        const val AUTHORITY = "com.minipay.mobile.debug.single-open-media"
    }
}
