package dev.wystore.updates

import android.content.Context
import dev.wystore.data.GitHubRepository
import dev.wystore.data.ManagedSource
import dev.wystore.data.PendingUpdate
import dev.wystore.data.VerifiedInstallPlan
import dev.wystore.root.RootInstaller

data class InstallDisposition(
    val installedSilently: Boolean,
    val pendingUpdate: PendingUpdate? = null,
    val rootError: String? = null
)

class VerifiedUpdateInstaller(
    context: Context,
    private val queueRepository: QueueRepository = QueueRepository.getInstance(context)
) {
    private val rootInstaller = RootInstaller()
    private val notifier = PendingUpdateNotifier(context)

    suspend fun installOrQueue(
        plan: VerifiedInstallPlan,
        label: String,
        update: Boolean,
        silentRootInstallEnabled: Boolean,
        source: ManagedSource,
        githubRepository: GitHubRepository? = null,
        githubReleaseId: Long? = null
    ): InstallDisposition {
        val rootAvailable = silentRootInstallEnabled && rootInstaller.isAvailable()
        if (InstallModePolicy.choose(silentRootInstallEnabled, rootAvailable) == InstallMode.SILENT_ROOT) {
            val result = rootInstaller.install(plan, update)
            if (result.success) return InstallDisposition(installedSilently = true)
            return queue(plan, label, source, githubRepository, githubReleaseId, result.output)
        }
        return queue(plan, label, source, githubRepository, githubReleaseId)
    }

    private suspend fun queue(
        plan: VerifiedInstallPlan,
        label: String,
        source: ManagedSource,
        githubRepository: GitHubRepository?,
        githubReleaseId: Long?,
        rootError: String? = null
    ): InstallDisposition {
        val pending = queueRepository.enqueueReadyUpdate(plan, label, source, githubRepository, githubReleaseId)
        notifier.refresh()
        return InstallDisposition(installedSilently = false, pendingUpdate = pending, rootError = rootError)
    }
}
