package dev.wystore.updates

import dev.wystore.data.SourceError
import dev.wystore.data.TransferFailure
import dev.wystore.updates.model.QueueErrorCode

/** A handover approves one asset, so a removed or replaced file cannot become another choice. */
object GitHubExactDownloadPolicy {
    fun failureFor(statusCode: Int): TransferFailure? =
        if (statusCode == 404 || statusCode == 410) changed("GitHub HTTP $statusCode").copy(httpStatus = statusCode) else null

    fun failureFor(error: SourceError, detail: String? = null): TransferFailure? = when (error) {
        SourceError.ARTIFACT_SIZE_MISMATCH,
        SourceError.ARTIFACT_INTEGRITY_MISMATCH -> TransferFailure(false, QueueErrorCode.INTEGRITY, detail = detail ?: error.name)
        else -> null
    }

    private fun changed(detail: String) = TransferFailure(
        retryable = false,
        code = QueueErrorCode.SOURCE_CHANGED,
        detail = detail
    )
}
