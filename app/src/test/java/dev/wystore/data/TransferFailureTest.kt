package dev.wystore.data

import dev.wystore.updates.model.QueueErrorCode
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class TransferFailureTest {

    @Test
    fun classifiesHttpFailuresCorrectly() {
        val rateLimited = classifyHttpFailure(429, "120")
        assertTrue(rateLimited.retryable)
        assertEquals(QueueErrorCode.RATE_LIMITED, rateLimited.code)
        assertEquals(120_000L, rateLimited.retryAfterMillis)

        val serverUnavailable = classifyHttpFailure(503, null)
        assertTrue(serverUnavailable.retryable)
        assertEquals(QueueErrorCode.NETWORK, serverUnavailable.code)

        val notFound = classifyHttpFailure(404, null)
        assertFalse(notFound.retryable)

        val gatewayTimeout = classifyHttpFailure(504, null)
        assertTrue(gatewayTimeout.retryable)
        assertEquals(QueueErrorCode.TIMEOUT, gatewayTimeout.code)

        val requestTimeout = classifyHttpFailure(408, null)
        assertTrue(requestTimeout.retryable)
        assertEquals(QueueErrorCode.TIMEOUT, requestTimeout.code)

        val tooEarly = classifyHttpFailure(425, null)
        assertTrue(tooEarly.retryable)
    }

    @Test
    fun rethrowsCancellationExceptionWithoutWrapping() {
        val cancellation = CancellationException("Job was cancelled")
        assertThrows(CancellationException::class.java) {
            classifyThrowable(cancellation)
        }
    }

    @Test
    fun classifiesIoAndNetworkExceptions() {
        val timeout = classifyThrowable(SocketTimeoutException("Read timed out"))
        assertTrue(timeout.retryable)
        assertEquals(QueueErrorCode.TIMEOUT, timeout.code)

        val dnsFailure = classifyThrowable(UnknownHostException("rustore.ru"))
        assertTrue(dnsFailure.retryable)
        assertEquals(QueueErrorCode.NETWORK, dnsFailure.code)

        val diskFull = classifyThrowable(IOException("No space left on device"))
        assertFalse(diskFull.retryable)
        assertEquals(QueueErrorCode.STORAGE_FULL, diskFull.code)

        val hashMismatch = classifyThrowable(IllegalStateException("Хеш APK не совпадает"))
        assertFalse(hashMismatch.retryable)
        assertEquals(QueueErrorCode.INTEGRITY, hashMismatch.code)
    }
}
