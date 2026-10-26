package dev.wystore.data

enum class TransferVersionWarning { NONE, APK_DOWNGRADE, OLDER_RELEASE }

/** A release tag describes the selection; only a known APK version code proves a downgrade. */
object OwnershipTransferVersionPolicy {
    fun warning(
        installedVersionName: String,
        installedVersionCode: Long,
        targetVersion: String?,
        targetVersionCode: Long?,
        targetIsReleaseTag: Boolean
    ): TransferVersionWarning = when {
        targetVersionCode != null && targetVersionCode > 0 && installedVersionCode > 0 ->
            if (targetVersionCode < installedVersionCode) TransferVersionWarning.APK_DOWNGRADE
            else TransferVersionWarning.NONE
        targetIsReleaseTag && GitHubInstallStatePolicy.stateFor(installedVersionName, targetVersion) ==
            GitHubInstallState.InstalledNewer -> TransferVersionWarning.OLDER_RELEASE
        else -> TransferVersionWarning.NONE
    }
}
