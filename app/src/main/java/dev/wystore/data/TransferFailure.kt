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

    val message = throwable.message.orEmpty()

    if (message.contains("No space left on device", ignoreCase = true) ||
        message.contains("ENOSPC", ignoreCase = true) ||
        message.contains("превышает допустимый размер", ignoreCase = true)) {
        return TransferFailure(
            retryable = false,
            code = QueueErrorCode.STORAGE_FULL,
            detail = message
        )
    }

    if (message.contains("Хеш APK не совпадает", ignoreCase = true) ||
        message.contains("integrity", ignoreCase = true)) {
        return TransferFailure(
            retryable = false,
            code = QueueErrorCode.INTEGRITY,
            detail = message
        )
    }

    if (throwable is SourceFormatException ||
        message.contains("недопустим", ignoreCase = true) ||
        message.contains("недоверенного домена", ignoreCase = true) ||
        message.contains("некорректную ссылку", ignoreCase = true) ||
        message.contains("слишком много перенаправлений", ignoreCase = true)) {
        return TransferFailure(
            retryable = false,
            code = QueueErrorCode.SOURCE_CHANGED,
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
        else -> TransferFailure(
            retryable = false,
            code = QueueErrorCode.NETWORK,
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
