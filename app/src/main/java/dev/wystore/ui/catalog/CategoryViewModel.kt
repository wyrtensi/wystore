package dev.wystore.ui.catalog

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.wystore.data.CatalogRepository
import dev.wystore.data.CuratedCategories
import dev.wystore.R
import dev.wystore.data.StoreApp
import dev.wystore.data.StoreCategory
import dev.wystore.localization.SourceTextResolver
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CategoryUiState(
    val slug: String = "",
    val title: String = "",
    val apps: List<StoreApp> = emptyList(),
    val page: Int = 1,
    val lastPage: Int? = null,
    val loading: Boolean = false,
    val stale: Boolean = false,
    val error: String? = null
) {
    val canGoBack: Boolean get() = page > 1 && !loading
    val canGoForward: Boolean get() = !loading && (lastPage == null || page < lastPage)
}

class CategoryViewModel(
    application: Application,
    private val catalogRepository: CatalogRepository
) : AndroidViewModel(application) {

    /** Spelled out for the reflective default AndroidViewModelFactory; see [HomeViewModel]. */
    constructor(application: Application) : this(application, CatalogRepository.getInstance(application))

    private val _uiState = MutableStateFlow(CategoryUiState())
    val uiState: StateFlow<CategoryUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    fun open(category: StoreCategory) {
        if (_uiState.value.slug == category.slug && _uiState.value.apps.isNotEmpty()) return
        // Same rule as the tile on Home.
        val title = dev.wystore.ui.components.CategoryLabels.titleRes(category)
            ?.let { getApplication<Application>().getString(it) }
            ?: category.title
        _uiState.value = CategoryUiState(slug = category.slug, title = title)
        loadPage(1)
    }

    fun nextPage() {
        val state = _uiState.value
        if (!state.canGoForward) return
        loadPage(state.page + 1)
    }

    fun previousPage() {
        val state = _uiState.value
        if (!state.canGoBack) return
        loadPage(state.page - 1)
    }

    fun retry() = loadPage(_uiState.value.page, forceRefresh = true)

    private fun loadPage(page: Int, forceRefresh: Boolean = false) {
        // Paging taps arrive faster than a page loads; only the newest request may write state.
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val slug = _uiState.value.slug
            val curated = CuratedCategories.find(slug)
            // Plain sections have a cached page on disk; showing it first turns a section reopened
            // from Home into an instant paint with a refresh behind it.
            if (!forceRefresh && curated == null && _uiState.value.apps.isEmpty()) {
                runCatching { catalogRepository.cachedCatalog(slug, page) }.getOrNull()?.let { cached ->
                    _uiState.update {
                        it.copy(apps = cached.apps, page = cached.page, lastPage = cached.lastPage)
                    }
                }
            }
            _uiState.update { it.copy(loading = true, error = null) }
            runCatching {
                if (curated != null) catalogRepository.curatedCatalog(curated, forceRefresh)
                else catalogRepository.catalog(slug, page, forceRefresh)
            }
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(
                            apps = result.value.apps,
                            page = result.value.page,
                            lastPage = result.value.lastPage,
                            loading = false,
                            stale = result.stale,
                            error = null
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            error = SourceTextResolver.describe(getApplication(), error)
                                ?: getApplication<Application>().getString(R.string.catalog_load_failed)
                        )
                    }
                }
        }
    }
}
