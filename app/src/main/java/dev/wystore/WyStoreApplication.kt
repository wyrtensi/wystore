package dev.wystore

import android.app.Application
import dev.wystore.data.invalidateInstalledApps
import android.content.IntentFilter
import android.content.Intent
import android.content.Context
import android.content.BroadcastReceiver
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import dev.wystore.data.BackupLocation
import dev.wystore.data.CatalogRepository
import dev.wystore.data.StoreRepository
import dev.wystore.data.GoogleAdoptionPolicy
import dev.wystore.settings.SettingsRepository
import dev.wystore.settings.toStoreSettings
import dev.wystore.updates.ManualInstallScheduler
import dev.wystore.updates.QueueCoordinator
import dev.wystore.updates.PendingReinstallStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class WyStoreApplication : Application(), ImageLoaderFactory {

    /** Lives for the process; used only to keep the synchronous settings cache warm. */
    private val applicationScope = CoroutineScope(SupervisorJob())
    private val pendingReinstallMutex = Mutex()

    /** Registered for the life of the process, so no package change is missed. */
    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            invalidateInstalledApps()
            observeGooglePlayUpdates()
            if (intent?.action == Intent.ACTION_PACKAGE_FULLY_REMOVED ||
                (intent?.action == Intent.ACTION_PACKAGE_REMOVED && !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false))) {
                intent.data?.schemeSpecificPart?.let(::finishPendingReinstall)
            }
            if (intent?.action == Intent.ACTION_PACKAGE_ADDED || intent?.action == Intent.ACTION_PACKAGE_REPLACED) {
                intent.data?.schemeSpecificPart?.let(::discardOutdatedDownloads)
            }
        }
    }

    private fun observeGooglePlayUpdates() {
        applicationScope.launch(Dispatchers.IO) {
            val repository = StoreRepository(this@WyStoreApplication)
            dev.wystore.data.GooglePlayUpdateMonitor(this@WyStoreApplication)
                .refresh(repository.installedApps())
        }
    }

    /**
     * The second half of taking over an app that came from Google.
     *
     * Google signs its own builds, so nothing else can update such an app in place; the store can
     * only offer to remove it and install the same app from a source it can update. The removal
     * happens in Android's dialog, outside this process and possibly outliving it, so the intent
     * was written to disk beforehand - this is where it is picked up, once, for the package that
     * actually went away.
     */
    private fun finishPendingReinstall(removedPackage: String) {
        // onReceive is the main thread, and both reads below go to disk - the settings one can
        // even block on a cold cache when the broadcast is what started the process.
        applicationScope.launch(Dispatchers.IO) {
            pendingReinstallMutex.withLock {
                val store = PendingReinstallStore(this@WyStoreApplication)
                val pending = store.read(removedPackage) ?: return@withLock
                if (pending.removedPackageName != removedPackage) return@withLock
                if (!GoogleAdoptionPolicy.isPendingFresh(pending.startedAt, System.currentTimeMillis())) {
                    store.clear(removedPackage)
                    return@withLock
                }
                try {
                    val queued = if (pending.queueId != null) {
                        val queue = dev.wystore.updates.QueueRepository.getInstance(this@WyStoreApplication)
                        checkNotNull(queue.getById(pending.queueId)) { "Replacement queue item is missing" }
                        if (pending.requiresUpdateOwnership) queue.requireUpdateOwnership(pending.queueId)
                        QueueCoordinator(this@WyStoreApplication).retry(pending.queueId)
                        pending.queueId
                    } else if (pending.githubRepository != null) {
                        val asset = checkNotNull(pending.githubAsset) { "Replacement GitHub asset is missing" }
                        dev.wystore.updates.GitHubInstallScheduler.enqueuePrepared(
                            context = this@WyStoreApplication, asset = asset,
                            repository = pending.githubRepository, releaseId = pending.githubReleaseId,
                            requiresUpdateOwnership = pending.requiresUpdateOwnership,
                            expectedPackageName = pending.installPackageName
                        )
                    } else {
                        ManualInstallScheduler.enqueuePrepared(
                            context = this@WyStoreApplication, packageName = pending.installPackageName,
                            label = pending.label,
                            settings = SettingsRepository(this@WyStoreApplication).currentSettings().toStoreSettings(),
                            requiresUpdateOwnership = pending.requiresUpdateOwnership,
                            requiresUserConfirmation = true
                        )
                    }
                    // Both removal broadcasts can arrive. Clear only once the replacement has a
                    // durable row, and never erase a newer handover recorded while it was queued.
                    if (queued != null && store.read(removedPackage) == pending) store.clear(removedPackage)
                } catch (error: Exception) {
                    // Keep the request for startup recovery; never substitute a different source.
                    dev.wystore.data.EventLog(this@WyStoreApplication).record(
                        pending.installPackageName, "HANDOVER_QUEUE_FAILED", error.message
                    )
                }
            }
        }
    }

    /**
     * Another store, or adb, may have just installed what Wy Store had downloaded for this app -
     * see [dev.wystore.updates.OutdatedDownloadPolicy].
     */
    private fun discardOutdatedDownloads(changedPackage: String) {
        applicationScope.launch(Dispatchers.IO) {
            runCatching {
                val discarded = dev.wystore.updates.QueueRepository.getInstance(this@WyStoreApplication)
                    .discardOutdated(changedPackage) { packageName ->
                        dev.wystore.data.InstalledPackageLookup.versionCode(packageManager, packageName)
                    }
                if (discarded) dev.wystore.updates.PendingUpdateNotifier(this@WyStoreApplication).refresh()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        observeGooglePlayUpdates()
        // Workers and Activity startup read settings synchronously; warming the cache here keeps
        // those reads off disk regardless of which component starts the process.
        // Invalidating the installed-app cache belongs to the process, not to a screen. The
        // Activity registers its own receiver in onStart and drops it in onStop - and Android's
        // uninstall dialog is exactly what sends the Activity to onStop, so the one broadcast that
        // says "this app is gone" arrived while nobody was listening. The next onResume then read
        // a cache nothing had invalidated and showed the app as still installed.
        ContextCompat.registerReceiver(
            this,
            packageChangeReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
                addDataScheme("package")
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        val settingsRepository = SettingsRepository(this)
        settingsRepository.warmUp(applicationScope)
        applicationScope.launch(Dispatchers.IO) {
            dev.wystore.settings.LauncherIconSwitcher.sync(
                this@WyStoreApplication,
                settingsRepository.currentSettings().accent
            )
        }

        // The receiver above lives only as long as the process. A low-memory phone or TV can kill
        // Wy Store behind the uninstall dialog, the "removed" broadcast then reaches nobody, and the
        // replacement was never installed - after the user had already lost the app and its data.
        // A handover still on disk whose app is gone is finished here instead.
        applicationScope.launch(Dispatchers.IO) {
            val pending = PendingReinstallStore(this@WyStoreApplication).readAll()
            for (request in pending) {
                val stillInstalled = dev.wystore.data.InstalledPackageLookup.isInstalled(packageManager, request.removedPackageName)
                if (!stillInstalled) finishPendingReinstall(request.removedPackageName)
            }
        }

        // Android clears the installer of record on every app Wy Store installed the moment Wy
        // Store is uninstalled, so nothing here can be rebuilt from the system afterwards. The
        // user's own backup file is the only thing that survives, and it is only worth having if
        // it is current - so it is rewritten here rather than whenever someone remembers to.
        applicationScope.launch(Dispatchers.IO) {
            runCatching {
                BackupLocation(this@WyStoreApplication)
                    .refresh(StoreRepository(this@WyStoreApplication).exportBackupJson())
            }
        }

        // The first screen the user sees is Home, and its content is already on disk from the last
        // run. Pulling it into the in-memory cache while the Activity is still being created means
        // Home paints from memory instead of waiting on a database read of its own.
        applicationScope.launch(Dispatchers.IO) {
            runCatching {
                val catalog = CatalogRepository.getInstance(this@WyStoreApplication)
                catalog.cachedCategories()
                catalog.cachedCatalog(slug = "", page = 1)
            }
        }
    }

    /**
     * RuStore serves catalog category icons as SVG, which Coil's default decoders cannot read, so
     * those icons silently rendered as blank tiles.
     *
     * The caches are sized explicitly: a store screen is mostly images, and Coil's defaults gave
     * app icons a disk cache shared with nothing and a memory cache small enough that scrolling
     * back up re-decoded every icon.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(SvgDecoder.Factory()) }
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.25)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(96L * 1024 * 1024)
                .build()
        }
        .respectCacheHeaders(false)
        .networkCachePolicy(CachePolicy.ENABLED)
        .crossfade(true)
        .build()
}
