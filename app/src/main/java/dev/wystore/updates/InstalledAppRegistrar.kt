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
            // This runs immediately after an install, which is precisely when a cached package
            // list is wrong: the newly installed package is not in it yet.
            invalidateInstalledApps()
            val installed = repository.installedApps()
                .firstOrNull { it.packageName == entity.packageName }
                ?: return@withContext false

            // Only adopt what is actually on the device now: a callback can arrive for an install
            // Android ultimately refused, and pinning digests from a package that is not installed
            // would poison every later update check.
            val digests = entity.signingDigests.split(",").filter { it.isNotBlank() }.toSet()
            if (digests.isNotEmpty() && installed.signingDigests != digests) return@withContext false
            if (entity.versionCode > 0 && installed.versionCode < entity.versionCode) return@withContext false

            val source = runCatching { ManagedSource.valueOf(entity.source) }
                .getOrDefault(ManagedSource.RUSTORE)
            val existing = repository.managedApps().firstOrNull { it.packageName == entity.packageName }
            val now = System.currentTimeMillis()
            repository.saveManaged(
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
            )
            true
        }
}
