package dev.wystore.data

import dev.wystore.updates.model.QueueErrorCode
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Instant
import java.time.format.DateTimeFormatter

data class TransferFailure(
    val retryable: Boolean,
    val code: QueueErrorCode,
    val retryAfterMillis: Long? = null,
    val detail: String? = null
) : RuntimeException(detail)

fun classifyHttpFailure(statusCode: Int, retryAfterHeader: String? = null): TransferFailure {
    val retryAfterMillis = parseRetryAfterHeader(retryAfterHeader)
    return when (statusCode) {
        408 -> TransferFailure(
            retryable = true,
            code = QueueErrorCode.TIMEOUT,
            detail = "HTTP 408: Request Timeout"
        )
        425 -> TransferFailure(
            retryable = true,
            code = QueueErrorCode.NETWORK,
            detail = "HTTP 425: Too Early"
        )
        429 -> TransferFailure(
            retryable = true,
            code = QueueErrorCode.RATE_LIMITED,
            retryAfterMillis = retryAfterMillis,
            detail = "HTTP 429: Rate limited"
        )
        504 -> TransferFailure(
            retryable = true,
            code = QueueErrorCode.TIMEOUT,
            detail = "HTTP 504: Gateway Timeout"
        )
        in 500..599 -> TransferFailure(
            retryable = true,
            code = QueueErrorCode.NETWORK,
            detail = "HTTP $statusCode: Server Error"
        )
        else -> TransferFailure(
            retryable = false,
            code = QueueErrorCode.NETWORK,
            detail = "HTTP $statusCode: Permanent Client Failure"
        )
    }
}

fun classifyThrowable(throwable: Throwable): TransferFailure {
    if (throwable is CancellationException) throw throwable
    if (throwable is TransferFailure) return throwable

    // A typed source failure answers this outright. The message matching below is the fallback for
    // everything that is still a plain exception, and it used to be the only path: whether a
    // failure counted as retryable was decided by looking for Russian substrings in the message,
    // so translating any one of them would have silently reclassified it.
    if (throwable is SourceFormatException) {
        return TransferFailure(
            retryable = throwable.error.retryable,
            code = throwable.error.queueCode,
            detail = throwable.message
        )
    }

    val message = throwable.message.orEmpty()

    // Message matching survives only for failures the app did not raise itself: the filesystem
    // reports a full disk through a plain IOException. Everything Wy Store throws now carries a
    // code, so the Russian-substring branches that used to live here are gone with the sentences
    // they were matching.
    if (message.contains("No space left on device", ignoreCase = true) ||
        message.contains("ENOSPC", ignoreCase = true)) {
        return TransferFailure(
            retryable = false,
            code = QueueErrorCode.STORAGE_FULL,
            detail = message
        )
    }

    return when (throwable) {
        is SocketTimeoutException -> TransferFailure(
            retryable = true,
            code = QueueErrorCode.TIMEOUT,
            detail = message.ifBlank { "Connection timed out" }
        )
        is UnknownHostException,
        is ConnectException,
        is SocketException,
        is IOException -> TransferFailure(
            retryable = true,
            code = QueueErrorCode.NETWORK,
            detail = message.ifBlank { "Network I/O failure" }
        )
        // Not an I/O failure and not one the app raised on purpose: this is the app itself going
        // wrong. It used to be reported as a network error, which sent the user to check their
        // connection over a bug in the queue.
        else -> TransferFailure(
            retryable = false,
            code = QueueErrorCode.INTERNAL,
            detail = message.ifBlank { throwable.javaClass.simpleName }
        )
    }
}

private fun parseRetryAfterHeader(header: String?): Long? {
    if (header.isNullOrBlank()) return null
    header.toLongOrNull()?.let { seconds ->
        return (seconds * 1000L).coerceAtLeast(0L)
    }
    return runCatching {
        val instant = DateTimeFormatter.RFC_1123_DATE_TIME.parse(header, Instant::from)
        (instant.toEpochMilli() - System.currentTimeMillis()).coerceAtLeast(0L)
    }.getOrNull()
}

/**
 * How a source failure maps onto the queue's own vocabulary.
 *
 * Retryability is a property of the failure, not of the message it happened to carry: a rate limit
 * clears on its own, a rejected host never will.
 */
val SourceError.queueCode: QueueErrorCode
    get() = when (this) {
        SourceError.GITHUB_RATE_LIMITED -> QueueErrorCode.RATE_LIMITED
        SourceError.GITHUB_UNAVAILABLE,
        SourceError.RUSTORE_UNAVAILABLE,
        SourceError.DOWNLOAD_FAILED -> QueueErrorCode.NETWORK
        SourceError.ARTIFACT_INTEGRITY_MISMATCH,
        SourceError.ARTIFACT_SIZE_MISMATCH -> QueueErrorCode.INTEGRITY
        SourceError.ARTIFACT_TOO_LARGE,
        SourceError.ARTIFACT_WRITE_FAILED -> QueueErrorCode.STORAGE_FULL
        SourceError.INCOMPATIBLE_ANDROID -> QueueErrorCode.INCOMPATIBLE
        else -> QueueErrorCode.SOURCE_CHANGED
    }

/** Whether waiting and trying again could plausibly succeed. */
val SourceError.retryable: Boolean
    get() = when (this) {
        SourceError.GITHUB_RATE_LIMITED,
        SourceError.GITHUB_UNAVAILABLE,
        SourceError.RUSTORE_UNAVAILABLE,
        SourceError.RUSTORE_EMPTY_RESPONSE,
        SourceError.DOWNLOAD_FAILED -> true
        else -> false
    }
