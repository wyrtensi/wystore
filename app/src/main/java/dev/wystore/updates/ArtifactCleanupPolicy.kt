package dev.wystore.updates

import dev.wystore.updates.model.QueueState

data class ArtifactCleanupCandidate(
    val id: String,
    val packageName: String,
    val versionCode: Long,
    val sizeBytes: Long,
    val state: QueueState,
    val lastAccessedAt: Long,
    val isCurrent: Boolean = true
)

class ArtifactCleanupPolicy(
    val defaultRetentionMillis: Long = 7 * 24 * 60 * 60 * 1000L
) {
    fun select(
        items: List<ArtifactCleanupCandidate>,
        nowMillis: Long,
        quotaBytes: Long,
        retentionMillis: Long = defaultRetentionMillis
    ): List<ArtifactCleanupCandidate> {
        val selected = mutableSetOf<ArtifactCleanupCandidate>()

        // 1. Group items by package name to detect superseded items
        val byPackage = items.groupBy { it.packageName }
        for ((_, pkgItems) in byPackage) {
            val maxVersion = pkgItems.maxOfOrNull { it.versionCode } ?: continue
            for (item in pkgItems) {
                if (isActive(item.state)) continue
                if (!item.isCurrent || item.versionCode < maxVersion) {
                    selected.add(item)
                }
            }
        }

        // 2. Select canceled, failed, or expired items
        for (item in items) {
            if (isActive(item.state)) continue
            if (item.state in TERMINAL_CLEANUP_STATES) {
                selected.add(item)
            } else if (nowMillis - item.lastAccessedAt > retentionMillis) {
                selected.add(item)
            }
        }

        // 3. Handle quota enforcement for remaining items
        val retained = items.filterNot { it in selected }.toMutableList()
        var currentTotalBytes = retained.sumOf { it.sizeBytes }

        if (currentTotalBytes > quotaBytes) {
            // Evict non-active items in LRU order (oldest lastAccessedAt first)
            val evictable = retained.filterNot { isActive(it.state) }
                .sortedWith(compareBy<ArtifactCleanupCandidate> { it.lastAccessedAt }.thenBy { it.id })

            for (candidate in evictable) {
                if (currentTotalBytes <= quotaBytes) break
                selected.add(candidate)
                retained.remove(candidate)
                currentTotalBytes -= candidate.sizeBytes
            }
        }

        return items.filter { it in selected }
    }

    private fun isActive(state: QueueState): Boolean = state in ACTIVE_STATES

    companion object {
        val ACTIVE_STATES = setOf(
            QueueState.DOWNLOADING,
            QueueState.VERIFYING,
            QueueState.READY_TO_INSTALL,
            QueueState.AWAITING_UNKNOWN_SOURCES_PERMISSION,
            QueueState.AWAITING_USER_CONFIRMATION,
            QueueState.INSTALLING
        )

        val TERMINAL_CLEANUP_STATES = setOf(
            QueueState.CANCELED,
            QueueState.FAILED
        )
    }
}
