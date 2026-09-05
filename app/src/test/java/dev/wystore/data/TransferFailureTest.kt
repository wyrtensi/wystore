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

        // Integrity is no longer inferred from the wording of a message: the downloader raises a
        // typed failure, covered by the test below.
        val unknown = classifyThrowable(IllegalStateException("something the app did not raise"))
        assertFalse(unknown.retryable)
        assertEquals(QueueErrorCode.NETWORK, unknown.code)
    }

    /**
     * Classification used to work by looking for Russian substrings in the exception message, so
     * translating any one of them would silently have turned an integrity failure into a generic
     * retryable network error. These assertions go through the typed code instead.
     */
    @Test
    fun aTypedSourceFailureClassifiesWithoutReadingItsMessage() {
        val integrity = classifyThrowable(
            SourceFormatException(SourceError.ARTIFACT_INTEGRITY_MISMATCH, "SHA-256 mismatch")
        )
        assertEquals(QueueErrorCode.INTEGRITY, integrity.code)
        assertFalse(integrity.retryable)

        val tooLarge = classifyThrowable(
            SourceFormatException(SourceError.ARTIFACT_TOO_LARGE, "Exceeded limit")
        )
        assertEquals(QueueErrorCode.STORAGE_FULL, tooLarge.code)
        assertFalse(tooLarge.retryable)

        val rateLimited = classifyThrowable(
            SourceFormatException(SourceError.GITHUB_RATE_LIMITED, "GitHub 429")
        )
        assertEquals(QueueErrorCode.RATE_LIMITED, rateLimited.code)
        assertTrue(rateLimited.retryable)

        val untrusted = classifyThrowable(
            SourceFormatException(SourceError.UNTRUSTED_HOST, "Redirected elsewhere")
        )
        assertEquals(QueueErrorCode.SOURCE_CHANGED, untrusted.code)
        assertFalse("a rejected host never becomes acceptable by waiting", untrusted.retryable)
    }

    @Test
    fun anIncompatibleAndroidVersionIsNotANetworkProblem() {
        val incompatible = classifyThrowable(
            SourceFormatException(SourceError.INCOMPATIBLE_ANDROID, "Requires Android 10")
        )

        assertEquals(QueueErrorCode.INCOMPATIBLE, incompatible.code)
        assertFalse(incompatible.retryable)
    }
}
