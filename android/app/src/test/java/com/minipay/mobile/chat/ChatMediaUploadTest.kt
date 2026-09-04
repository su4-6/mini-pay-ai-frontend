package com.minipay.mobile.chat

import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ChatMediaUploadTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private lateinit var server: MockWebServer

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After fun tearDown() {
        server.shutdown()
    }

    @Test fun streamsFileWithSignedHeadersAndExactContentLength() = runTest {
        val bytes = ByteArray(128 * 1024) { (it % 251).toByte() }
        val file = temporaryFolder.newFile("photo.jpg").apply { writeBytes(bytes) }
        server.enqueue(MockResponse().setResponseCode(200))

        uploadSignedMediaFile(
            client = OkHttpClient(),
            uploadUrl = server.url("/signed-upload").toString(),
            requiredHeaders = mapOf("x-oss-meta-sha256" to "digest", "Content-Type" to "image/jpeg"),
            contentType = "image/jpeg",
            file = file
        )

        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("PUT", request.method)
        assertEquals("image/jpeg", request.getHeader("Content-Type"))
        assertEquals("digest", request.getHeader("x-oss-meta-sha256"))
        assertEquals(bytes.size.toLong(), request.bodySize)
        assertArrayEquals(bytes, request.body.readByteArray())
    }

    @Test fun mapsExpiredSignatureWithoutExposingResponseBody() {
        val file = temporaryFolder.newFile("photo.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        server.enqueue(MockResponse().setResponseCode(403).setBody("signed-url-secret"))

        val error = assertThrows(ChatApiException::class.java) {
            runTest {
                uploadSignedMediaFile(
                    OkHttpClient(),
                    server.url("/expired").toString(),
                    emptyMap(),
                    "image/jpeg",
                    file
                )
            }
        }

        assertEquals("CHAT_MEDIA_UPLOAD_EXPIRED", error.code)
        assertEquals(403, error.status)
        assertEquals(null, error.responseBody)
    }

    @Test fun mapsSocketTimeoutToActionableCode() {
        val file = temporaryFolder.newFile("photo.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val client = OkHttpClient.Builder().readTimeout(100, TimeUnit.MILLISECONDS).build()

        val error = assertThrows(ChatApiException::class.java) {
            runTest {
                uploadSignedMediaFile(
                    client,
                    server.url("/timeout").toString(),
                    emptyMap(),
                    "image/jpeg",
                    file
                )
            }
        }

        assertEquals("CHAT_MEDIA_UPLOAD_TIMEOUT", error.code)
    }
}
