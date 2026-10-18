package dev.wystore.ui.home

import dev.wystore.ui.components.fadingTopEdge
import dev.wystore.ui.components.bleedToScreenEdges
import dev.wystore.rowFor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import dev.wystore.ui.components.HubHeader
import dev.wystore.ui.components.groupPosition
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.wystore.ui.components.WySpinner
import dev.wystore.InstallQueueItem
import dev.wystore.R
import dev.wystore.data.GitHubCatalog
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
    categoryPreviews: Map<String, List<String>> = emptyMap(),
    onNeedCategoryPreview: (String) -> Unit = {},
    onOpenGitHub: () -> Unit = {},
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
    /** Take the transfer slot for a queued app now, displacing whatever is running. */
    onDownloadNow: (String) -> Unit = {},
    /** Stop a running transfer and keep the bytes it has. */
    onPauseDownload: (String) -> Unit = {},
    /** Carry a paused transfer on from the bytes on disk. */
    onResumeDownload: (String) -> Unit = {},
    onConfirmUnverifiedSource: (String) -> Unit = {},
    onLaunch: (String) -> Unit = {}
) {
    // The Wy Hub screen: the wordmark as a large plain title with the tagline under it, no top bar;
    // then the search pill, the panel saying whether anything waits, and the shelves.
    // Clear of the status bar as a whole, so the shelves scroll under nothing rather than under
    // the clock and the icons.
    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        LazyColumn(
            // Shelves scrolled up under the status bar dissolve rather than end on a cut.
            modifier = Modifier.fillMaxSize().fadingTopEdge(12.dp),
            contentPadding = PaddingValues(
                start = ScreenPadding,
                end = ScreenPadding,
                top = 6.dp,
                bottom = 24.dp + LocalBottomBarInset.current
            )
        ) {
            // Part of the list, so on the longest screen of the app the wordmark scrolls away with
            // the shelves instead of holding the top of every one of them.
            item {
                HubHeader(
                    title = "Wy Store",
                    subtitle = stringResource(R.string.home_tagline),
                    horizontalPadding = 0.dp,
                    insetStatusBar = false,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }
            item {
                PersistentSearchBar(
                    query = searchQuery,
                    onSearchClick = onSearchClick,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            item {
                UpdateHeroSection(
                    pendingUpdates = pendingUpdates,
                    onUpdateAll = onUpdateAll,
                    onOpenUpdates = onUpdatesClick,
                    modifier = Modifier.padding(bottom = SectionSpacing)
                )
            }

            item {
                CategoriesSection(
                    categories = categories,
                    loading = catalogLoading,
                    onCategoryClick = onCategoryClick,
                    onAllCategoriesClick = onAllCategoriesClick,
                    previews = categoryPreviews,
                    onNeedPreview = onNeedCategoryPreview,
                    githubEnabled = githubPicks.isNotEmpty(),
                    onGitHubClick = onOpenGitHub,
                    modifier = Modifier.padding(bottom = SectionSpacing)
                )
            }

            // GitHub picks sit above the RuStore rail so the second source is discoverable from
            // Home instead of only from Settings.
            if (githubPicks.isNotEmpty()) {
                item {
                    val installedPackages = remember(installed) { installed.mapTo(HashSet()) { it.packageName } }
                    Column(
                        modifier = Modifier.padding(bottom = SectionSpacing),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SectionHeader(
                            title = stringResource(R.string.home_github_title),
                            subtitle = stringResource(R.string.home_github_hint),
                            actionLabel = stringResource(R.string.home_categories_all),
                            onActionClick = onOpenGitHub
                        )
                        // The catalogue is eighty entries now; the rail is a taste of it and "All"
                        // opens the whole list with its sections.
                        val rail = remember(githubPicks) {
                            GitHubCatalog.homeRail(githubPicks, HOME_GITHUB_PICKS)
                        }
                        LazyRow(
                            modifier = Modifier.bleedToScreenEdges(),
                            contentPadding = PaddingValues(horizontal = ScreenPadding),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(rail, key = { it.slug }) { entry ->
                                GitHubPickTile(
                                    iconUrl = entry.iconUrl,
                                    title = entry.title,
                                    // A repository the user added has no summary of its own.
                                    summary = entry.summaryIn(LocalConfiguration.current.locales[0].language)
                                        .ifBlank { entry.repository.displayName },
                                    category = entry.categories.firstOrNull(),
                                    installed = entry.isInstalled(installedPackages, managed),
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
                    subtitle = if (catalogStale) stringResource(R.string.home_catalog_stale) else null,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }

            if (featuredApps.isNotEmpty()) {
                itemsIndexed(featuredApps, key = { _, app -> app.packageName }) { index, app ->
                    // Home used to reduce with the catalogue entry alone, so an app that was already
                    // installed, downloading or ready to install still offered "Install" here while
                    // every other screen showed its real state.
                    val uiState = PackageUiStateReducer.reduce(
                        app = app,
                        installed = installed.firstOrNull { it.packageName == app.packageName },
                        managed = managed.firstOrNull { it.packageName == app.packageName },
                        queueItem = queue.rowFor(app.packageName),
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
                                RowAction.DownloadNow -> onDownloadNow(app.packageName)
                                RowAction.Pause -> onPauseDownload(app.packageName)
                                RowAction.Resume -> onResumeDownload(app.packageName)
                                RowAction.ConfirmSource -> onConfirmUnverifiedSource(app.packageName)
                                RowAction.OpenApp -> onLaunch(app.packageName)
                                RowAction.Nothing -> Unit
                            }
                        },
                        implicitSource = "RuStore",
                        // One panel of rows, as in Wy Hub, instead of a card per app.
                        groupPosition = groupPosition(index, featuredApps.size)
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

/** How many GitHub entries the Home rail shows before "All". */
private const val HOME_GITHUB_PICKS = 12
