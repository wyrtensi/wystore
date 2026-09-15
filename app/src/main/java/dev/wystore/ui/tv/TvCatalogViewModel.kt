package dev.wystore.ui.tv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.wystore.R
import dev.wystore.data.CatalogRepository
import dev.wystore.data.StoreApp
import dev.wystore.localization.SourceTextResolver
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TvCatalogUiState(
    /** Every app of the TV catalogue loaded so far, in the source's order. */
    val apps: List<StoreApp> = emptyList(),
    val loading: Boolean = false,
    val stale: Boolean = false,
    val error: String? = null
)

/**
 * The whole RuStore TV catalogue, page after page.
 *
 * It is a few hundred apps across about ten pages, which is small enough to hold whole. Holding it
 * whole is what lets Home build its rows and Search filter as the user types without a request per
 * keystroke - on a remote every keystroke is expensive, and the source's own search does not find
 * TV-only apps at all. Pages come through the same cache as every other catalogue section.
 */
class TvCatalogViewModel(
    application: Application,
    private val catalogRepository: CatalogRepository
) : AndroidViewModel(application) {

    /** Spelled out for the reflective default AndroidViewModelFactory; see HomeViewModel. */
    constructor(application: Application) : this(application, CatalogRepository.getInstance(application))

    private val _uiState = MutableStateFlow(TvCatalogUiState())
    val uiState: StateFlow<TvCatalogUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        load(forceRefresh = false)
    }

    fun retry() = load(forceRefresh = true)

    private fun load(forceRefresh: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val collected = LinkedHashMap<String, StoreApp>()
            var stale = false
            var page = 1
            var lastPage: Int? = null
            while (page <= MAX_PAGES && (lastPage == null || page <= lastPage)) {
                val result = runCatching { catalogRepository.catalog(SLUG, page, forceRefresh) }
                val value = result.getOrNull()
                if (value == null) {
                    // The first page failing means there is nothing to show; a later one failing
                    // still leaves a usable catalogue, so it is kept and marked stale.
                    if (collected.isEmpty()) {
                        _uiState.update {
                            it.copy(
                                loading = false,
                                error = result.exceptionOrNull()
                                    ?.let { error -> SourceTextResolver.describe(getApplication(), error) }
                                    ?: getApplication<Application>().getString(R.string.tv_catalog_empty)
                            )
                        }
                        return@launch
                    }
                    stale = true
                    break
                }
                stale = stale || value.stale
                value.value.apps.forEach { app -> collected.putIfAbsent(app.packageName, app) }
                lastPage = value.value.lastPage ?: page
                _uiState.update { it.copy(apps = collected.values.toList(), stale = stale) }
                page++
            }
            _uiState.update { it.copy(apps = collected.values.toList(), loading = false, stale = stale) }
        }
    }

    companion object {
        const val SLUG = "tv"

        /** A guard against a pager that never ends, far above the catalogue's real size. */
        private const val MAX_PAGES = 30
    }
}
