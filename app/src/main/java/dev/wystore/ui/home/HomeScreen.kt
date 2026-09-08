package dev.wystore.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.wystore.ui.components.WySpinner
import dev.wystore.InstallQueueItem
import dev.wystore.R
import dev.wystore.data.GitHubCatalogEntry
import dev.wystore.data.InstalledApp
import dev.wystore.data.ManagedApp
import dev.wystore.data.PendingUpdate
import dev.wystore.data.StoreApp
import dev.wystore.data.StoreCategory
import dev.wystore.ui.components.RowActionPolicy
import dev.wystore.ui.components.RowAction
import dev.wystore.ui.components.LocalBottomBarInset
import dev.wystore.ui.components.AppRow
import dev.wystore.ui.components.EmptyState
import dev.wystore.ui.components.PackageUiStateReducer
import dev.wystore.ui.components.ScreenPadding
import dev.wystore.ui.components.SectionHeader
import dev.wystore.ui.components.SectionSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    searchQuery: String = "",
    pendingUpdates: List<PendingUpdate> = emptyList(),
    featuredApps: List<StoreApp> = emptyList(),
    categories: List<StoreCategory> = emptyList(),
    catalogLoading: Boolean = false,
    catalogStale: Boolean = false,
    catalogError: String? = null,
    onSearchClick: () -> Unit = {},
    onAppClick: (String) -> Unit = {},
    onCategoryClick: (StoreCategory) -> Unit = {},
    onAllCategoriesClick: () -> Unit = {},
    onUpdatesClick: () -> Unit = {},
    installed: List<InstalledApp> = emptyList(),
    managed: List<ManagedApp> = emptyList(),
    queue: List<InstallQueueItem> = emptyList(),
    githubPicks: List<GitHubCatalogEntry> = emptyList(),
    onGitHubPickClick: (GitHubCatalogEntry) -> Unit = {},
    onUpdateAll: () -> Unit = {},
    onRetryCatalog: () -> Unit = {},
    onInstall: (String) -> Unit = {},
    onInstallDownloaded: (String) -> Unit = {},
    onLaunch: (String) -> Unit = {}
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                // The wordmark sits left with its tagline under it, the way a store front reads,
                // rather than as a lone centred word.
                title = {
                    Column {
                        Text("Wy Store", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            stringResource(R.string.home_tagline),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding()),
            contentPadding = PaddingValues(
                start = ScreenPadding,
                end = ScreenPadding,
                top = 4.dp,
                bottom = 24.dp + LocalBottomBarInset.current
            ),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing)
        ) {
            item {
                PersistentSearchBar(
                    query = searchQuery,
                    onSearchClick = onSearchClick
                )
            }

            item {
                UpdateHeroSection(
                    pendingUpdates = pendingUpdates,
                    onUpdateAll = onUpdateAll,
                    onOpenUpdates = onUpdatesClick
                )
            }

            item {
                CategoriesSection(
                    categories = categories,
                    loading = catalogLoading,
                    onCategoryClick = onCategoryClick,
                    onAllCategoriesClick = onAllCategoriesClick
                )
            }

            // GitHub picks sit above the RuStore rail so the second source is discoverable from
            // Home instead of only from Settings.
            if (githubPicks.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader(
                            title = stringResource(R.string.home_github_title),
                            subtitle = stringResource(R.string.home_github_hint)
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(githubPicks, key = { it.slug }) { entry ->
                                GitHubPickTile(
                                    iconUrl = entry.iconUrl,
                                    title = entry.title,
                                    publisher = entry.publisher,
                                    onClick = { onGitHubPickClick(entry) }
                                )
                            }
                        }
                    }
                }
            }

            item {
                SectionHeader(
                    title = stringResource(R.string.home_featured_title),
                    subtitle = if (catalogStale) stringResource(R.string.home_catalog_stale) else null
                )
            }

            if (featuredApps.isNotEmpty()) {
                items(featuredApps, key = { it.packageName }) { app ->
                    // Home used to reduce with the catalogue entry alone, so an app that was already
                    // installed, downloading or ready to install still offered "Install" here while
                    // every other screen showed its real state.
                    val uiState = PackageUiStateReducer.reduce(
                        app = app,
                        installed = installed.firstOrNull { it.packageName == app.packageName },
                        managed = managed.firstOrNull { it.packageName == app.packageName },
                        queueItem = queue.firstOrNull { it.packageName == app.packageName },
                        pendingUpdate = pendingUpdates.firstOrNull { it.packageName == app.packageName },
                        resources = LocalContext.current.resources
                    )
                    AppRow(
                        state = uiState,
                        onRowClick = { onAppClick(app.packageName) },
                        onPrimaryActionClick = {
                            when (RowActionPolicy.actionFor(uiState.primaryAction, uiState.status.code)) {
                                RowAction.Enqueue -> onInstall(app.packageName)
                                RowAction.InstallDownloaded -> onInstallDownloaded(app.packageName)
                                RowAction.OpenApp -> onLaunch(app.packageName)
                                RowAction.Nothing -> Unit
                            }
                        }
                    )
                }
            } else {
                // Distinguish "still fetching" from "the fetch failed", so the screen can never sit
                // on a loading line that never resolves.
                item {
                    if (catalogLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            WySpinner(size = 18.dp, strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                stringResource(R.string.home_catalog_loading),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    } else {
                        EmptyState(
                            icon = Icons.Outlined.Warning,
                            title = stringResource(R.string.home_empty_featured_title),
                            message = catalogError ?: stringResource(R.string.home_catalog_unavailable),
                            actionLabel = stringResource(R.string.common_retry),
                            onActionClick = onRetryCatalog
                        )
                    }
                }
            }
        }
    }
}
