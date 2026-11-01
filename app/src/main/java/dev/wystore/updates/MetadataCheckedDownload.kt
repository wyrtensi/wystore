package dev.wystore.updates

import dev.wystore.data.InstalledApp
import dev.wystore.data.GitHubInstallState
import dev.wystore.data.GitHubInstallStatePolicy

sealed interface AvailableUpdateVersion {
    data class RuStore(val versionCode: Long) : AvailableUpdateVersion
    data class Library(val versionCode: Long) : AvailableUpdateVersion
    data class GitHub(val releaseTag: String?) : AvailableUpdateVersion
}

object MetadataCheckedDownload {
    /** Both transports enter here immediately before the callback can request APK bytes. */
    suspend fun <T> download(
        userRequested: Boolean,
        version: AvailableUpdateVersion,
        readInstalled: suspend () -> InstalledApp?,
        isPaused: suspend (InstalledApp?) -> Boolean,
        transfer: suspend () -> T
    ): T? {
        if (userRequested) return transfer()
        val installed = readInstalled() ?: return null
        if (isPaused(installed)) return null
        val newer = when (version) {
            is AvailableUpdateVersion.RuStore ->
                version.versionCode > 0 && version.versionCode > installed.versionCode
            is AvailableUpdateVersion.Library ->
                version.versionCode > 0 && version.versionCode > installed.versionCode
            is AvailableUpdateVersion.GitHub ->
                GitHubInstallStatePolicy.stateFor(installed.versionName, version.releaseTag) ==
                    GitHubInstallState.UpdateAvailable
        }
        return if (newer) transfer() else null
    }
}
