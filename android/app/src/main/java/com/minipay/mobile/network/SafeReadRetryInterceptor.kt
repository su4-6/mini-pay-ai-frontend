package com.minipay.mobile.network

import java.io.EOFException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.ProtocolException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Retries one transport-level failure for read-only HTTP requests.
 *
 * OkHttp's built-in retry remains disabled because this application also uses the same client for
 * token rotation and financial writes. Those requests must only be repeated by their business
 * workflows with the appropriate idempotency guarantees.
 */
internal class SafeReadRetryInterceptor(
    private val retryDelayMillis: Long = DEFAULT_RETRY_DELAY_MILLIS
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.isRetryableRead()) return chain.proceed(request)

        return try {
            chain.proceed(request)
        } catch (firstFailure: IOException) {
            if (!firstFailure.isRetryableTransportFailure()) throw firstFailure
            waitBeforeRetry(firstFailure)
            try {
                chain.proceed(request)
            } catch (secondFailure: IOException) {
                secondFailure.addSuppressed(firstFailure)
                throw secondFailure
            }
        }
    }

    private fun waitBeforeRetry(firstFailure: IOException) {
        if (retryDelayMillis <= 0L) return
        try {
            Thread.sleep(retryDelayMillis)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException("Read retry interrupted").apply {
                initCause(firstFailure)
            }
        }
    }

    private fun okhttp3.Request.isRetryableRead(): Boolean {
        if (method != "GET" && method != "HEAD") return false
        if (header("Accept")?.contains("text/event-stream", ignoreCase = true) == true) return false
        if (header("Upgrade")?.equals("websocket", ignoreCase = true) == true) return false
        return true
    }

    internal companion object {
        const val DEFAULT_RETRY_DELAY_MILLIS = 200L

        fun IOException.isRetryableTransportFailure(): Boolean {
            if (this is SSLHandshakeException || this is SSLPeerUnverifiedException) return false
            if (this is ProtocolException) return false
            if (this is InterruptedIOException && this !is SocketTimeoutException) return false
            return this is ConnectException ||
                this is UnknownHostException ||
                this is SocketTimeoutException ||
                this is SocketException ||
                this is EOFException ||
                message?.contains("unexpected end of stream", ignoreCase = true) == true
        }
    }
}
