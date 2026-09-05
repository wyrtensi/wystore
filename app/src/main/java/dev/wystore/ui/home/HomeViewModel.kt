package dev.wystore.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.wystore.data.CatalogRepository
import dev.wystore.data.StoreApp
import dev.wystore.data.StoreCategory
import dev.wystore.localization.SourceTextResolver
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val featuredApps: List<StoreApp> = emptyList(),
    val categories: List<StoreCategory> = emptyList(),
    val loading: Boolean = false,
    /** Content is being shown from cache because the last refresh could not reach the source. */
    val stale: Boolean = false,
    val error: String? = null
) {
    /** Nothing to show and nothing in flight: the screen needs a retry affordance, not a spinner. */
    val isEmpty: Boolean get() = featuredApps.isEmpty() && categories.isEmpty()
}

/**
 * Loads the source catalogue for Home. The previous version held no data at all, which is why Home
 * showed its loading placeholders forever.
 */
class HomeViewModel(
    application: Application,
    private val catalogRepository: CatalogRepository
) : AndroidViewModel(application) {

    /**
     * The single-argument form the default [androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory]
     * reflects on. A constructor with a defaulted parameter does not produce it, so it is spelled out.
     */
    constructor(application: Application) : this(application, CatalogRepository.getInstance(application))

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun load(forceRefresh: Boolean = false) {
        // A tab switch during a slow fetch must not stack a second load onto the first.
        if (loadJob?.isActive == true && !forceRefresh) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (!forceRefresh) showCachedContent()
            _uiState.update { it.copy(loading = true, error = null) }

            // Two independent HTML fetches. Running them one after the other made Home take as
            // long as both together, with the second not even started while the first was in
            // flight.
            val categoriesDeferred = async { runCatching { catalogRepository.categories(forceRefresh) } }
            val featuredDeferred = async {
                runCatching { catalogRepository.catalog(slug = "", page = 1, forceRefresh = forceRefresh) }
            }
            awaitAll(categoriesDeferred, featuredDeferred)
            val categories = categoriesDeferred.await()
            val featured = featuredDeferred.await()

            val failure = categories.exceptionOrNull() ?: featured.exceptionOrNull()
            _uiState.update { current ->
                current.copy(
                    categories = categories.getOrNull()?.value ?: current.categories,
                    featuredApps = featured.getOrNull()?.value?.apps?.take(FEATURED_LIMIT)
                        ?: current.featuredApps,
                    loading = false,
                    stale = categories.getOrNull()?.stale == true || featured.getOrNull()?.stale == true,
                    error = SourceTextResolver.describe(getApplication(), failure)
                )
            }
        }
    }

    /**
     * Paints whatever was cached before the network is consulted.
     *
     * The cache reads existed but nothing called them, so a returning user watched a spinner and an
     * empty screen for the length of a full fetch even though the content was already on disk.
     */
    private suspend fun showCachedContent() {
        if (!_uiState.value.isEmpty) return
        val cachedCategories = runCatching { catalogRepository.cachedCategories() }.getOrDefault(emptyList())
        val cachedFeatured = runCatching { catalogRepository.cachedCatalog(slug = "", page = 1) }.getOrNull()
        if (cachedCategories.isEmpty() && cachedFeatured == null) return
        _uiState.update { current ->
            current.copy(
                categories = cachedCategories.ifEmpty { current.categories },
                featuredApps = cachedFeatured?.apps?.take(FEATURED_LIMIT) ?: current.featuredApps
            )
        }
    }

    fun retry() = load(forceRefresh = true)

    private companion object {
        const val FEATURED_LIMIT = 20
    }
}
