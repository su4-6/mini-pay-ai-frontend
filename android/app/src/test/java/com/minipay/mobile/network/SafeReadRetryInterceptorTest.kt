package com.minipay.mobile.network

import java.io.EOFException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.ProtocolException
import javax.net.ssl.SSLHandshakeException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SafeReadRetryInterceptorTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        client = OkHttpClient.Builder()
            .addInterceptor(SafeReadRetryInterceptor(retryDelayMillis = 0L))
            .retryOnConnectionFailure(false)
            .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun getRetriesOneTransportFailureAndKeepsRequestId() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        val request = Request.Builder()
            .url(server.url("/resource"))
            .header("X-Request-Id", "request-1")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            assertEquals(200, response.code)
            assertEquals("ok", response.body?.string())
        }

        assertEquals(2, server.requestCount)
        // DISCONNECT_AT_START may close before MockWebServer parses the first request headers.
        server.takeRequest()
        assertEquals("request-1", server.takeRequest().getHeader("X-Request-Id"))
    }

    @Test
    fun getStopsAfterOneRetry() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val request = Request.Builder().url(server.url("/resource")).get().build()

        assertThrows(Exception::class.java) {
            client.newCall(request).execute().close()
        }
        assertEquals(2, server.requestCount)
    }

    @Test
    fun postIsNeverRetried() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(MockResponse().setResponseCode(200))
        val request = Request.Builder()
            .url(server.url("/write"))
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()

        assertThrows(Exception::class.java) {
            client.newCall(request).execute().close()
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun httpFailureIsReturnedWithoutRetry() {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(200))
        val request = Request.Builder().url(server.url("/resource")).get().build()

        client.newCall(request).execute().use { response ->
            assertEquals(503, response.code)
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun eventStreamIsNeverRetried() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(MockResponse().setResponseCode(200))
        val request = Request.Builder()
            .url(server.url("/events"))
            .header("Accept", "text/event-stream")
            .get()
            .build()

        assertThrows(Exception::class.java) {
            client.newCall(request).execute().close()
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun retryableFailureClassificationExcludesSecurityAndCancellationFailures() {
        with(SafeReadRetryInterceptor) {
            assertTrue(ConnectException().isRetryableTransportFailure())
            assertTrue(EOFException().isRetryableTransportFailure())
            assertFalse(SSLHandshakeException("bad certificate").isRetryableTransportFailure())
            assertFalse(ProtocolException().isRetryableTransportFailure())
            assertFalse(InterruptedIOException().isRetryableTransportFailure())
        }
    }
}
