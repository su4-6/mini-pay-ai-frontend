package com.minipay.mobile.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import coil.imageLoader
import coil.memory.MemoryCache
import com.minipay.mobile.avatar.avatarMemoryCacheKey
import com.minipay.mobile.ui.theme.MilingTheme
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class UserAvatarCacheTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val server = MockWebServer()

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun leavingAndReturningWithANewSignatureReusesTheCachedAvatar() {
        server.start()
        server.enqueue(imageResponse(Color.BLUE))
        server.enqueue(imageResponse(Color.RED))
        val firstUrl = server.url("/avatars/user/photo.png").newBuilder()
            .addQueryParameter("OSSAccessKeyId", "first")
            .addQueryParameter("Expires", "100")
            .addQueryParameter("Signature", "one")
            .build()
            .toString()
        val refreshedUrl = server.url("/avatars/user/photo.png").newBuilder()
            .addQueryParameter("OSSAccessKeyId", "second")
            .addQueryParameter("Expires", "200")
            .addQueryParameter("Signature", "two")
            .build()
            .toString()
        val changedAvatarUrl = server.url("/avatars/user/replaced.png").newBuilder()
            .addQueryParameter("OSSAccessKeyId", "third")
            .addQueryParameter("Expires", "300")
            .addQueryParameter("Signature", "three")
            .build()
            .toString()
        val visible = mutableStateOf(true)
        val avatarUrl = mutableStateOf(firstUrl)

        composeRule.setContent {
            MilingTheme {
                if (visible.value) UserAvatar("Alice", avatarUrl.value, colorIndex = 0)
            }
        }

        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sizePx = (48 * context.resources.displayMetrics.density).roundToInt()
        val memoryKey = MemoryCache.Key(avatarMemoryCacheKey(firstUrl, sizePx))
        composeRule.waitUntil(timeoutMillis = 5_000) {
            context.imageLoader.memoryCache?.get(memoryKey) != null
        }

        composeRule.runOnUiThread { visible.value = false }
        composeRule.waitForIdle()
        composeRule.runOnUiThread {
            avatarUrl.value = refreshedUrl
            visible.value = true
        }
        composeRule.waitForIdle()

        assertNull(server.takeRequest(1, TimeUnit.SECONDS))
        assertTrue(context.imageLoader.memoryCache?.get(memoryKey) != null)

        composeRule.runOnUiThread { avatarUrl.value = changedAvatarUrl }
        composeRule.waitForIdle()
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
    }

    private fun imageResponse(color: Int): MockResponse {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        val bytes = ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
        bitmap.recycle()
        return MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "image/png")
            .setBody(Buffer().write(bytes))
    }
}
