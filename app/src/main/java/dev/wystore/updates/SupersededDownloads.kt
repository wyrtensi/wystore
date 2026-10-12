package dev.wystore.updates

import dev.wystore.data.local.UpdateQueueEntity
import dev.wystore.updates.model.QueueState

/**
 * Which downloads of a package a newer one has made pointless.
 *
 * Verification only ever replaced a row of the same version, so an update downloaded and never
 * installed stayed on disk when the next version was downloaded beside it. Every screen treats the
 * downloads as one per app, and the Updates list keyed its items that way: two ready versions of
 * one app crashed the app as soon as the tab was opened.
 *
 * Per package the newest download is kept - on a tie, the one already in Android's dialog, then the
 * most recent. A row in the dialog is never removed, nor is [keepId]: the worker that just verified
 * it goes on to install it.
 */
object SupersededDownloads {

    private val DOWNLOADED = setOf(
        QueueState.READY_TO_INSTALL.name,
        QueueState.AWAITING_UNKNOWN_SOURCES_PERMISSION.name,
        QueueState.AWAITING_USER_CONFIRMATION.name
    )

    private val IN_DIALOG = QueueState.AWAITING_USER_CONFIRMATION.name

    fun superseded(rows: List<UpdateQueueEntity>, keepId: String? = null): List<UpdateQueueEntity> =
        rows.filter { it.state in DOWNLOADED }
            .groupBy { it.packageName }
            .values
            .flatMap { downloads ->
                val kept = downloads.maxWith(
                    compareBy<UpdateQueueEntity> { it.versionCode }
                        .thenBy { it.state == IN_DIALOG }
                        .thenBy { it.updatedAt }
                )
                downloads.filter { it.id != kept.id && it.id != keepId && it.state != IN_DIALOG }
            }
}
