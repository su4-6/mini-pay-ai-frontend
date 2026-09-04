package com.minipay.mobile.finance

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionReceiptSseClientTest {
    @Test
    fun `receipt stream disables finite API read timeout`() {
        val apiClient = OkHttpClient.Builder()
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val streamClient = collectionReceiptSseClient(apiClient)

        assertEquals(15_000, apiClient.readTimeoutMillis)
        assertEquals(0, streamClient.readTimeoutMillis)
        assertEquals(apiClient.connectionPool, streamClient.connectionPool)
        assertEquals(apiClient.dispatcher, streamClient.dispatcher)
    }
}
