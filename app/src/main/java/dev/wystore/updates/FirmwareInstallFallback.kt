package dev.wystore.updates

import android.content.Context
import dev.wystore.R
import dev.wystore.background.NotificationCoordinator
import dev.wystore.background.QueuePump
import dev.wystore.data.EventLog
import dev.wystore.updates.model.QueueErrorCode

/**
 * What happens to an install the firmware refused as a session - see [SessionInstallRejection].
 *
 * The user asked for an install, not for a lesson about developer options, so the answer is to
 * carry on another way. A single APK goes straight back to "ready" with a standing request to
 * install, and the open app picks that up and hands it to the system installer; a closed one
 * leaves it in the ready notification. Split parts cannot go that way, so the app is fetched
 * again as one APK and installs when it arrives.
 */
object FirmwareInstallFallback {

    suspend fun afterRefusal(context: Context, queueId: String, statusMessage: String?) {
        val appContext = context.applicationContext
        SessionInstallSupport(appContext).markSessionsRefused()
        val repository = QueueRepository.getInstance(appContext)
        val entity = repository.getEntityById(queueId) ?: return
        // Recorded under its own code: this is the evidence the diagnostics report reads.
        runCatching { EventLog(appContext).record(entity.packageName, EVENT_SESSION_REFUSED, statusMessage) }

        val artifactCount = repository.artifactFilesFor(queueId).size
        if (artifactCount <= 1) {
            runCatching { AutoInstallStore(appContext).request(entity.packageName) }
            repository.resetReadyToInstall(queueId)
            runCatching { NotificationCoordinator(appContext).publishReady(repository.readyToInstallSnapshots()) }
            QueuePump.startNext(appContext)
        } else {
            refetchWhole(appContext, queueId)
        }
    }

    /**
     * Downloads the app again as a single APK, once per version.
     *
     * Only a source that has such a build can supply one. Should the same version come back in
     * parts again, fetching it a third time would be a loop, so the second time is a real error.
     */
    suspend fun refetchWhole(context: Context, queueId: String) {
        val appContext = context.applicationContext
        val repository = QueueRepository.getInstance(appContext)
        val entity = repository.getEntityById(queueId) ?: return
        val marker = "${entity.packageName}:${entity.versionCode}"
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val refetched = prefs.getStringSet(KEY_REFETCHED, emptySet()).orEmpty()
        if (marker in refetched) {
            repository.markFailed(
                id = queueId,
                errorCode = QueueErrorCode.INSTALL_FAILED,
                errorDetail = appContext.getString(R.string.msg_install_split_refused)
            )
            QueuePump.startNext(appContext)
            return
        }
        prefs.edit().putStringSet(KEY_REFETCHED, refetched + marker).commit()
        runCatching { EventLog(appContext).record(entity.packageName, EVENT_WHOLE_APK_REFETCH, null) }
        runCatching { AutoInstallStore(appContext).request(entity.packageName) }
        repository.resetForRetry(queueId, errorCode = null, errorDetail = null)
        QueueCoordinator(appContext).download(queueId)
    }

    const val EVENT_SESSION_REFUSED = "SESSION_REFUSED_BY_FIRMWARE"
    const val EVENT_WHOLE_APK_REFETCH = "WHOLE_APK_REFETCH"
    private const val PREFS = "wystore_installer"
    private const val KEY_REFETCHED = "whole_apk_refetched"
}
