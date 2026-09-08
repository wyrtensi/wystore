package dev.wystore.updates

import android.content.Context
import androidx.core.content.edit

/** A handover the user started: this app is being removed so that one can take its place. */
data class PendingReinstall(
    val removedPackageName: String,
    val installPackageName: String,
    val label: String,
    val startedAt: Long
)

/**
 * Remembers a handover across the uninstall.
 *
 * Removing an app hands the screen to Android's dialog and can outlive the process, so the intent
 * to install the replacement cannot live in a composable or a ViewModel: by the time the package is
 * gone, whatever held it may be gone too. It is written before the dialog is shown and read by the
 * package-removed receiver.
 *
 * One at a time on purpose. Two handovers in flight would mean a queue and a decision about what to
 * do when only one of them completes, and nothing about this flow needs that.
 */
class PendingReinstallStore(context: Context) {

    private val preferences =
        context.applicationContext.getSharedPreferences("wy_store_pending_reinstall", Context.MODE_PRIVATE)

    fun save(pending: PendingReinstall) {
        // commit, not apply: the very next thing that happens is Android taking over the screen for
        // the uninstall dialog, and the write has to be on disk before the process may be killed.
        preferences.edit(commit = true) {
            putString(KEY_REMOVED, pending.removedPackageName)
            putString(KEY_INSTALL, pending.installPackageName)
            putString(KEY_LABEL, pending.label)
            putLong(KEY_STARTED_AT, pending.startedAt)
        }
    }

    fun read(): PendingReinstall? {
        val removed = preferences.getString(KEY_REMOVED, null) ?: return null
        val install = preferences.getString(KEY_INSTALL, null) ?: return null
        return PendingReinstall(
            removedPackageName = removed,
            installPackageName = install,
            label = preferences.getString(KEY_LABEL, null).orEmpty().ifBlank { install },
            startedAt = preferences.getLong(KEY_STARTED_AT, 0L)
        )
    }

    fun clear() {
        preferences.edit(commit = true) { clear() }
    }

    private companion object {
        const val KEY_REMOVED = "removed_package"
        const val KEY_INSTALL = "install_package"
        const val KEY_LABEL = "label"
        const val KEY_STARTED_AT = "started_at"
    }
}
