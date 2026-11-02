package dev.wystore.updates

import dev.wystore.data.local.UpdateQueueEntity
import dev.wystore.BuildConfig
import dev.wystore.selfupdate.SelfUpdateVersion
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
 * it goes on to install it. For Wy Store's own GitHub updates, a newer queued release also makes
 * older inactive rows obsolete; release IDs and verified APK version codes are not comparable.
 */
object SupersededDownloads {

    private val DOWNLOADED = setOf(
        QueueState.READY_TO_INSTALL.name,
        QueueState.AWAITING_UNKNOWN_SOURCES_PERMISSION.name,
        QueueState.AWAITING_USER_CONFIRMATION.name
    )

    private val IN_DIALOG = QueueState.AWAITING_USER_CONFIRMATION.name

    private val SELF_REMOVABLE = DOWNLOADED + setOf(
        QueueState.AVAILABLE.name, QueueState.PAUSED.name, QueueState.FAILED.name
    ) - IN_DIALOG

    fun isSelfUpdate(row: UpdateQueueEntity): Boolean =
        row.packageName == BuildConfig.APPLICATION_ID && row.source == "GITHUB" &&
            row.githubRepositoryOwner.equals("wyrtensi", ignoreCase = true) &&
            row.githubRepositoryName.equals("wystore", ignoreCase = true)

    fun superseded(rows: List<UpdateQueueEntity>, keepId: String? = null): List<UpdateQueueEntity> {
        val self = rows.filter(::isSelfUpdate)
        val oldSelf = self.filter { old ->
            old.id != keepId && old.state in SELF_REMOVABLE &&
                self.any { newer -> SelfUpdateVersion.isNewer(newer.versionName, old.versionName) }
        }
        val other = rows.filterNot(::isSelfUpdate).filter { it.state in DOWNLOADED }
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
        return oldSelf + other
    }
}
