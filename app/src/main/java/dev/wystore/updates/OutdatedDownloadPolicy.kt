package dev.wystore.updates

import dev.wystore.updates.model.QueueState

/**
 * Whether a download waiting to be installed has been overtaken by what is on the phone.
 *
 * Rows were settled only after Wy Store's own install. An app another store updated meanwhile -
 * Google Play took OZON to the very version Wy Store had downloaded - stayed on "ready to install"
 * and in the notification until someone pressed Install and the install refused itself.
 *
 * The same version is kept when a person asked for it: handing updates to Wy Store downloads the
 * installed version on purpose, so that Wy Store becomes its installer.
 */
object OutdatedDownloadPolicy {

    private val WAITING = setOf(
        QueueState.READY_TO_INSTALL.name,
        QueueState.AWAITING_UNKNOWN_SOURCES_PERMISSION.name
    )

    fun isOutdated(state: String, rowVersionCode: Long, installedVersionCode: Long?, userRequested: Boolean): Boolean {
        if (state !in WAITING || installedVersionCode == null) return false
        return if (userRequested) rowVersionCode < installedVersionCode else rowVersionCode <= installedVersionCode
    }
}
