package dev.wystore.ui.components

import android.content.res.Resources
import dev.wystore.InstallQueueItem
import dev.wystore.InstallQueueStatus
import dev.wystore.data.AndroidSdkCompatibility
import dev.wystore.data.InstalledApp
import dev.wystore.data.ManagedApp
import dev.wystore.data.PendingUpdate
import dev.wystore.data.StoreApp

object PackageUiStateReducer {
    fun reduce(
        app: StoreApp? = null,
        installed: InstalledApp? = null,
        managed: ManagedApp? = null,
        queueItem: InstallQueueItem? = null,
        pendingUpdate: PendingUpdate? = null,
        deviceSdkInt: Int = android.os.Build.VERSION.SDK_INT,
        resources: Resources? = null
    ): PackageUiState {
        val packageName = app?.packageName
            ?: installed?.packageName
            ?: managed?.packageName
            ?: queueItem?.packageName
            ?: pendingUpdate?.packageName
            ?: ""

        val label = app?.name
            ?: installed?.label
            ?: managed?.label
            ?: queueItem?.label
            ?: pendingUpdate?.label
            ?: packageName

        val iconUrl = app?.iconUrl
        val publisher = app?.publisher

        // The source gives either an API level or a release number, and the two are not the same
        // scale: read as an API level, "Android 9" is SDK 9 and passes on every device there is.
        val minSdk = app?.minSdkVersion
            ?: app?.minAndroidVersion?.let(AndroidSdkCompatibility::sdkForRelease)
        val effectiveSdk = if (deviceSdkInt > 0) deviceSdkInt else 35
        val isCompatible = minSdk == null || effectiveSdk >= minSdk

        if (queueItem != null) {
            val progressFraction = queueItem.progress?.fraction
            val transferInfo = queueItem.progress?.let {
                "${formatSize(resources, it.downloadedBytes)} / ${formatSize(resources, it.totalBytes)}"
            }

            when (queueItem.status) {
                InstallQueueStatus.DOWNLOADING -> {
                    return PackageUiState(
                        packageName = packageName,
                        label = label,
                        versionName = app?.versionName ?: installed?.versionName,
                        versionCode = app?.versionCode ?: installed?.versionCode,
                        iconUrl = iconUrl,
                        publisher = publisher,
                        status = StatusMessage(
                            StatusCode.DOWNLOADING,
                            queueItem.progress?.let { progress ->
                                mapOf(
                                    "percent" to "${(progress.fraction * 100).toInt()}",
                                    "downloaded" to formatSize(resources, progress.downloadedBytes),
                                    "total" to formatSize(resources, progress.totalBytes)
                                )
                            } ?: emptyMap()
                        ),
                        primaryAction = PrimaryAction.Pause,
                        secondaryAction = SecondaryAction.Cancel,
                        progress = progressFraction,
                        transferInfo = transferInfo,
                        sourceProvenance = managed?.source?.name ?: app?.let { "RuStore" },
                        isCompatible = isCompatible
                    )
                }
                InstallQueueStatus.VERIFYING -> {
                    return PackageUiState(
                        packageName = packageName,
                        label = label,
                        versionName = app?.versionName ?: installed?.versionName,
                        versionCode = app?.versionCode ?: installed?.versionCode,
                        iconUrl = iconUrl,
                        publisher = publisher,
                        status = StatusMessage(StatusCode.VERIFYING),
                        primaryAction = PrimaryAction.Installing,
                        secondaryAction = null,
                        progress = null,
                        transferInfo = null,
                        sourceProvenance = managed?.source?.name ?: app?.let { "RuStore" },
                        isCompatible = isCompatible
                    )
                }
                InstallQueueStatus.INSTALLING -> {
                    return PackageUiState(
                        packageName = packageName,
                        label = label,
                        versionName = app?.versionName ?: installed?.versionName,
                        versionCode = app?.versionCode ?: installed?.versionCode,
                        iconUrl = iconUrl,
                        publisher = publisher,
                        status = StatusMessage(StatusCode.INSTALLING),
                        primaryAction = PrimaryAction.Installing,
                        secondaryAction = null,
                        progress = null,
                        transferInfo = null,
                        sourceProvenance = managed?.source?.name ?: app?.let { "RuStore" },
                        isCompatible = isCompatible
                    )
                }
                InstallQueueStatus.READY -> {
                    // Downloaded and verified: the only thing left is the user's confirmation, so the
                    // card offers Install. Falls through when the durable pending record is missing.
                    if (pendingUpdate != null) {
                        return PackageUiState(
                            packageName = packageName,
                            label = label,
                            versionName = pendingUpdate.versionName,
                            versionCode = pendingUpdate.versionCode,
                            iconUrl = iconUrl,
                            publisher = publisher,
                            status = StatusMessage(StatusCode.READY_TO_INSTALL),
                            primaryAction = PrimaryAction.Install,
                            secondaryAction = SecondaryAction.Cancel,
                            progress = null,
                            transferInfo = null,
                            sourceProvenance = pendingUpdate.source.name,
                            isCompatible = isCompatible
                        )
                    }
                }
                InstallQueueStatus.CANCELED -> {
                    return PackageUiState(
                        packageName = packageName,
                        label = label,
                        versionName = app?.versionName ?: installed?.versionName,
                        versionCode = app?.versionCode ?: installed?.versionCode,
                        iconUrl = iconUrl,
                        publisher = publisher,
                        status = StatusMessage(StatusCode.CANCELLED),
                        primaryAction = PrimaryAction.Retry,
                        secondaryAction = null,
                        progress = null,
                        transferInfo = null,
                        sourceProvenance = managed?.source?.name ?: app?.let { "RuStore" },
                        isCompatible = isCompatible
                    )
                }
                InstallQueueStatus.RESOLVING, InstallQueueStatus.QUEUED -> {
                    return PackageUiState(
                        packageName = packageName,
                        label = label,
                        versionName = app?.versionName ?: installed?.versionName,
                        versionCode = app?.versionCode ?: installed?.versionCode,
                        iconUrl = iconUrl,
                        publisher = publisher,
                        status = StatusMessage(StatusCode.QUEUED),
                        // Waiting in line, and the useful thing to offer is a way out of the
                        // line. This used to read "Continue" and do nothing at all: it asked
                        // Android to install a file that had not been downloaded, while the other
                        // path returns early because the package is already queued.
                        primaryAction = PrimaryAction.DownloadNow,
                        secondaryAction = SecondaryAction.Cancel,
                        progress = null,
                        transferInfo = null,
                        sourceProvenance = managed?.source?.name ?: app?.let { "RuStore" },
                        isCompatible = isCompatible
                    )
                }
                InstallQueueStatus.FAILED -> {
                    val code = if (queueItem.detail?.contains("network", ignoreCase = true) == true ||
                        queueItem.detail?.contains("timeout", ignoreCase = true) == true) {
                        StatusCode.FAILED_NETWORK
                    } else if (queueItem.detail?.contains("signature", ignoreCase = true) == true) {
                        StatusCode.FAILED_SIGNATURE
                    } else {
                        StatusCode.FAILED_GENERIC
                    }
                    return PackageUiState(
                        packageName = packageName,
                        label = label,
                        versionName = app?.versionName ?: installed?.versionName,
                        versionCode = app?.versionCode ?: installed?.versionCode,
                        iconUrl = iconUrl,
                        publisher = publisher,
                        status = StatusMessage(
                            code,
                            queueItem.detail?.takeIf { it.isNotBlank() }?.let { mapOf("detail" to it) } ?: emptyMap()
                        ),
                        primaryAction = PrimaryAction.Retry,
                        secondaryAction = null,
                        progress = null,
                        transferInfo = queueItem.detail,
                        sourceProvenance = managed?.source?.name ?: app?.let { "RuStore" },
                        isCompatible = isCompatible
                    )
                }
                InstallQueueStatus.COMPLETE -> {
                    // Fallthrough to installed handling
                }
            }
        }

        if (pendingUpdate != null) {
            return PackageUiState(
                packageName = packageName,
                label = label,
                versionName = pendingUpdate.versionName,
                versionCode = pendingUpdate.versionCode,
                iconUrl = iconUrl,
                publisher = publisher,
                status = StatusMessage(StatusCode.READY_TO_INSTALL),
                primaryAction = PrimaryAction.Install,
                secondaryAction = null,
                progress = null,
                transferInfo = null,
                sourceProvenance = pendingUpdate.source.name,
                isCompatible = isCompatible
            )
        }

        if (!isCompatible) {
            return PackageUiState(
                packageName = packageName,
                label = label,
                versionName = app?.versionName,
                versionCode = app?.versionCode,
                iconUrl = iconUrl,
                publisher = publisher,
                status = StatusMessage(StatusCode.SKIPPED),
                primaryAction = PrimaryAction.None,
                secondaryAction = null,
                progress = null,
                transferInfo = null,
                sourceProvenance = app?.let { "RuStore" },
                isCompatible = false
            )
        }

        if (installed != null) {
            val hasUpdate = app != null && app.versionCode > installed.versionCode
            if (hasUpdate) {
                return PackageUiState(
                    packageName = packageName,
                    label = label,
                    versionName = app.versionName,
                    versionCode = app.versionCode,
                    iconUrl = iconUrl,
                    publisher = publisher,
                    status = StatusMessage(StatusCode.OFFER_NEXT),
                    primaryAction = PrimaryAction.Update,
                    secondaryAction = null,
                    progress = null,
                    transferInfo = null,
                    sourceProvenance = managed?.source?.name ?: "RuStore",
                    isCompatible = true
                )
            } else {
                return PackageUiState(
                    packageName = packageName,
                    label = label,
                    versionName = installed.versionName,
                    versionCode = installed.versionCode,
                    iconUrl = iconUrl,
                    publisher = publisher,
                    status = StatusMessage(StatusCode.INSTALLED),
                    primaryAction = PrimaryAction.Open,
                    secondaryAction = null,
                    progress = null,
                    transferInfo = null,
                    sourceProvenance = managed?.source?.name ?: installed.source.name,
                    isCompatible = true
                )
            }
        }

        return PackageUiState(
            packageName = packageName,
            label = label,
            versionName = app?.versionName,
            versionCode = app?.versionCode,
            iconUrl = iconUrl,
            publisher = publisher,
            // Nothing has been downloaded for this one: it is a catalogue entry the device does
            // not have. Saying "ready to install" here read exactly like an update already waiting
            // on disk, which is a different state with the same words.
            status = StatusMessage(StatusCode.NOT_INSTALLED),
            primaryAction = PrimaryAction.Install,
            secondaryAction = null,
            progress = null,
            transferInfo = null,
            sourceProvenance = app?.let { "RuStore" },
            isCompatible = true
        )
    }
}
