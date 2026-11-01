package dev.wystore.data.library

import dev.wystore.data.TransferFailure
import dev.wystore.updates.model.QueueErrorCode

data class LibraryDownload<T>(val version: ResolvedLibraryVersion, val data: T)

/** A removed immutable asset permits one explicit publication refresh, never a store fallback. */
object LibraryTransfer {
    suspend fun <T> download(
        initial: ResolvedLibraryVersion,
        resolveLatest: suspend () -> ResolvedLibraryVersion,
        allowed: suspend (ResolvedLibraryVersion) -> Boolean,
        onResolved: suspend (ResolvedLibraryVersion) -> Unit,
        transfer: suspend (ResolvedLibraryVersion) -> T
    ): LibraryDownload<T>? {
        var version = initial
        var refreshed = false
        while (true) {
            if (!allowed(version)) return null
            onResolved(version)
            if (!allowed(version)) return null
            try {
                return LibraryDownload(version, transfer(version))
            } catch (error: TransferFailure) {
                if (refreshed || error.httpStatus !in listOf(404, 410)) throw error
                refreshed = true
                val latest = resolveLatest()
                if (latest.packageName != initial.packageName || latest.publicationHash == version.publicationHash ||
                    latest.versionCode < version.versionCode) {
                    throw TransferFailure(false, QueueErrorCode.SOURCE_CHANGED, detail = "Library publication is unavailable")
                }
                version = latest
            }
        }
    }
}
