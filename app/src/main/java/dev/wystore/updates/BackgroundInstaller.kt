package dev.wystore.updates

import android.content.Context
import android.os.Build
import dev.wystore.data.StoreRepository
import dev.wystore.updates.model.QueueAction
import dev.wystore.updates.model.QueueErrorCode
import dev.wystore.updates.model.QueueState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Finishes an update while nobody is looking.
 *
 * Committing a PackageInstaller session needs no Activity - only Android's confirmation dialog
 * did. So an update Wy Store is allowed to install without that dialog can be installed the moment
 * its download finishes, instead of leaving a note for the next time the app happens to be opened.
 *
 * Everything this cannot do falls back to that note: a first install, an app another store owns,
 * or a system that declines the request after all.
 */
class BackgroundInstaller(context: Context) {

    private val appContext = context.applicationContext
    private val sessionWriter = InstallSessionWriter(appContext)
    private val queueRepository = QueueRepository.getInstance(appContext)
    private val storeRepository = StoreRepository(appContext)

    /** True when the install was handed to Android here and needs no further help. */
    suspend fun install(queueId: String, packageName: String): Boolean = withContext(Dispatchers.IO) {
        val settings = storeRepository.settings()
        if (!settings.autoInstallUpdates) return@withContext false
        if (!SilentUpdatePolicy.allows(
                enabled = settings.silentUpdatesEnabled,
                isUpdate = isInstalled(packageName),
                installerOfRecord = installerOfRecord(packageName),
                ownPackageName = appContext.packageName
            )
        ) {
            return@withContext false
        }

        queueRepository.transitionIfIn(
            id = queueId,
            allowedFrom = setOf(QueueState.READY_TO_INSTALL),
            action = QueueAction.RequestInstallConfirmation
        )

        val prepared = try {
            sessionWriter.prepare(queueId)
        } catch (error: Throwable) {
            queueRepository.resetReadyToInstall(queueId)
            return@withContext false
        }

        // The legacy route hands Android an Intent, and an Intent needs an Activity. Old enough
        // devices keep the behaviour they always had.
        if (prepared !is PreparedInstall.Session) {
            runCatching { sessionWriter.abandon(queueId) }
            queueRepository.resetReadyToInstall(queueId)
            return@withContext false
        }

        queueRepository.transitionIfIn(
            id = queueId,
            allowedFrom = setOf(QueueState.AWAITING_USER_CONFIRMATION),
            action = QueueAction.StartInstall
        )

        try {
            sessionWriter.commitSession(prepared)
            // Whether it actually installs is decided by InstallResultReceiver. If Android asks
            // for confirmation after all, that receiver puts the row back where the user can
            // reach it rather than leaving it mid-install.
            true
        } catch (error: Throwable) {
            runCatching { sessionWriter.abandon(queueId) }
            queueRepository.reconcileInstallResult(
                id = queueId,
                success = false,
                errorCode = QueueErrorCode.INSTALL_FAILED,
                errorDetail = error.message
            )
            false
        }
    }

    private fun isInstalled(packageName: String): Boolean = runCatching {
        appContext.packageManager.getPackageInfo(packageName, 0)
    }.isSuccess

    private fun installerOfRecord(packageName: String): String? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            appContext.packageManager.getInstallSourceInfo(packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            appContext.packageManager.getInstallerPackageName(packageName)
        }
    }.getOrNull()
}
