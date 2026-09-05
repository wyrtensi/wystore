package dev.wystore

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import dev.wystore.data.CatalogRepository
import dev.wystore.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WyStoreApplication : Application(), ImageLoaderFactory {

    /** Lives for the process; used only to keep the synchronous settings cache warm. */
    private val applicationScope = CoroutineScope(SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        // Workers and Activity startup read settings synchronously; warming the cache here keeps
        // those reads off disk regardless of which component starts the process.
        SettingsRepository(this).warmUp(applicationScope)

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
