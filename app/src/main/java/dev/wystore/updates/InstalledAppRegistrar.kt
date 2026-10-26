package dev.wystore.updates

import android.content.Context
import dev.wystore.data.GitHubRepository
import dev.wystore.data.ManagedApp
import dev.wystore.data.ManagedSource
import dev.wystore.data.StoreRepository
import dev.wystore.data.invalidateInstalledApps
import dev.wystore.data.local.UpdateQueueEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Records a finished install as a managed app, which is what makes it eligible for future update
 * checks.
 *
 * Installs that complete through [InstallResultReceiver] used to skip this entirely: only the
 * Activity callback path registered anything, so an app installed while Wy Store was in the
 * background — and every GitHub install — landed on the device and then never received an update.
 */
object InstalledAppRegistrar {

    suspend fun register(context: Context, entity: UpdateQueueEntity): Boolean =
        withContext(Dispatchers.IO) {
            val repository = StoreRepository(context)
            repository.withManagedAppsLock {
                // This runs immediately after an install, which is precisely when a cached package
                // list is wrong: the newly installed package is not in it yet.
                invalidateInstalledApps()
                val installed = repository.installedAppFresh(entity.packageName)
                    ?: return@withManagedAppsLock false

                // Only adopt what is actually on the device now: a callback can arrive for an install
                // Android ultimately refused, and pinning digests from a package that is not installed
                // would poison every later update check.
                val digests = entity.signingDigests.split(",").filter { it.isNotBlank() }.toSet()
                if (digests.isNotEmpty() && installed.signingDigests != digests) return@withManagedAppsLock false
                if (entity.versionCode > 0 && installed.versionCode < entity.versionCode) return@withManagedAppsLock false

                val source = runCatching { ManagedSource.valueOf(entity.source) }
                    .getOrDefault(ManagedSource.RUSTORE)
                val now = System.currentTimeMillis()
                // A regular update must not erase observed Play attribution. Only the explicitly
                // requested handover with Android confirming our update ownership resets that fact.
                val verifiedHandover = entity.requiresUpdateOwnership && digests.isNotEmpty() &&
                    installed.updateOwnerPackageName == context.packageName
                repository.updateManaged(entity.packageName, clearGooglePlayUpdate = verifiedHandover) { existing ->
                    ManagedApp(
                        packageName = entity.packageName,
                        label = entity.label.ifBlank { installed.label },
                        pinnedDigests = installed.signingDigests,
                        autoUpdate = existing?.autoUpdate ?: true,
                        forceWyStore = existing?.forceWyStore ?: false,
                        addedAt = existing?.addedAt ?: now,
                        lastUpdatedAt = now,
                        source = source,
                        githubRepository = entity.githubRepositoryOwner
                            ?.let { owner ->
                                entity.githubRepositoryName?.let { name -> GitHubRepository(owner, name) }
                            },
                        githubReleaseId = entity.githubReleaseId
                    )
                }
                if (verifiedHandover) {
                    dev.wystore.background.NotificationState(context).forgetGooglePlayPackage(entity.packageName)
                }
                true
            }
        }
}
