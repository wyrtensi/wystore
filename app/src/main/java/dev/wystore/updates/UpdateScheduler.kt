package dev.wystore.updates

import android.content.Context
import dev.wystore.background.UpdateWorkScheduler
import dev.wystore.data.StoreRepository
import dev.wystore.data.StoreSettings

object UpdateScheduler {
    /** The name the work is actually enqueued under, so an observer can find it. */
    const val MANUAL_CHECK_WORK_NAME = UpdateWorkScheduler.MANUAL_CHECK_WORK

    /**
     * (Re)schedules the recurring check. The managed-app count is read here rather than passed in
     * so that every caller — startup, settings changes, boot — gets the same answer to "is there
     * anything to check at all".
     */
    fun schedule(context: Context, settings: StoreSettings) {
        val managedCount = runCatching { StoreRepository(context).managedApps().size }.getOrDefault(1)
        UpdateWorkScheduler.schedulePeriodicCheck(context, settings, managedCount)
    }

    fun checkNow(context: Context, settings: StoreSettings, packageName: String? = null) {
        UpdateWorkScheduler.checkPackageNow(context, settings, packageName)
    }
}
