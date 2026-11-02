package dev.wystore.data

import android.content.Context
import dev.wystore.background.NotificationCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Observes while the process lives, and catches missed changes on foreground/periodic refresh. */
class GooglePlayUpdateMonitor(context: Context) {
    private val appContext = context.applicationContext
    private val repository = StoreRepository(appContext)

    suspend fun refresh(installed: List<InstalledApp>): List<ManagedApp> = withContext(Dispatchers.IO) {
        REFRESH_LOCK.withLock {
            repository.withManagedAppsLock {
                val managed = repository.observeGooglePlayUpdates(installed)
                val settings = repository.settings()
                NotificationCoordinator(appContext).publishGooglePlayPause(managed.filter {
                    it.autoUpdate &&
                        (it.source != ManagedSource.GITHUB || settings.githubEnabled) &&
                        (it.source != ManagedSource.WY_STORE || settings.libraryEnabled) &&
                        GooglePlayUpdatePolicy.isPaused(it, null, settings.continueChecksAfterGooglePlayUpdate)
                })
                managed
            }
        }
    }

    companion object {
        private val REFRESH_LOCK = Mutex()
    }
}
