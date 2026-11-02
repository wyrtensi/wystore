package dev.wystore.updates

import dev.wystore.data.ManagedSource

object WholeApkFallbackPolicy {
    fun mayRefetch(source: ManagedSource): Boolean = source != ManagedSource.WY_STORE
}
