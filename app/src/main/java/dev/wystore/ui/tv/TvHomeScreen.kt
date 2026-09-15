package dev.wystore.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.wystore.InstallQueueItem
import dev.wystore.R
import dev.wystore.data.InstalledApp
import dev.wystore.data.ManagedApp
import dev.wystore.data.PendingUpdate
import dev.wystore.data.StoreApp
import dev.wystore.settings.TvCatalog
import dev.wystore.ui.components.PackageUiState
import dev.wystore.ui.components.PackageUiStateReducer

/** Everything a TV screen needs to know about what is installed and moving. */
data class TvPackageContext(
    val installed: List<InstalledApp>,
    val managed: List<ManagedApp>,
    val queue: List<InstallQueueItem>,
    val pendingUpdates: List<PendingUpdate>
)

@Composable
fun rememberPackageState(app: StoreApp, packages: TvPackageContext): PackageUiState {
    val resources = LocalContext.current.resources
    return remember(app, packages) {
        PackageUiStateReducer.reduce(
            app = app,
            installed = packages.installed.firstOrNull { it.packageName == app.packageName },
            managed = packages.managed.firstOrNull { it.packageName == app.packageName },
            queueItem = packages.queue.firstOrNull { it.packageName == app.packageName },
            pendingUpdate = packages.pendingUpdates.firstOrNull { it.packageName == app.packageName },
            resources = resources
        )
    }
}

/** One row of Home: its title when it starts a group, and whether its apps are phone apps. */
private data class TvHomeRow(
    val key: String,
    val title: Int?,
    val apps: List<StoreApp>,
    val forPhone: Boolean
)

/**
 * Home on a TV: rows of cards, one screen of choices at a time.
 *
 * Up and down move between rows, left and right within one. Which rows there are follows the
 * catalogue setting: the TV catalogue, the phone one, or the TV one with the phone apps in rows of
 * their own, each card marked so a phone app is not taken for a TV one.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvHomeScreen(
    catalog: TvCatalogUiState,
    catalogMode: TvCatalog,
    packages: TvPackageContext,
    firstFocus: FocusRequester,
    /** False while the focus is up in the menu: arriving here must not pull it down. */
    takeFocus: Boolean,
    restoreFocusTo: String?,
    onOpenApp: (String) -> Unit,
    onRetry: () -> Unit,
    onOpenMyApps: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = remember(catalog.apps, catalog.phoneApps, catalogMode, packages) {
        buildHomeRows(catalog, catalogMode, packages)
    }
    val loading = if (catalogMode == TvCatalog.PHONE) catalog.phoneLoading else catalog.loading
    val empty = rows.isEmpty()
    val hasContent = !empty || !loading
    val restoreFocus = remember { FocusRequester() }
    val shouldTakeFocus by rememberUpdatedState(takeFocus)
    LaunchedEffect(hasContent) {
        if (!hasContent || !shouldTakeFocus) return@LaunchedEffect
        // Back from an app page returns to that app's card when it is still on screen.
        val restored = restoreFocusTo != null && runCatching { restoreFocus.requestFocus() }.isSuccess
        if (!restored) runCatching { firstFocus.requestFocus() }
    }

    val listState = rememberLazyListState()
    // Back from inside the screen sends the focus up to the menu; the screen goes back to its top
    // with it, so the menu is not left above a list scrolled halfway down.
    LaunchedEffect(takeFocus) { if (!takeFocus) listState.animateScrollToItem(0) }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = TvOverscanHorizontal,
            end = TvOverscanHorizontal,
            top = 8.dp,
            bottom = TvOverscanVertical + 48.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "hero") {
            if (catalog.stale) {
                Text(
                    stringResource(R.string.home_catalog_stale),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            TvUpdateHero(
                readyCount = (packages.pendingUpdates.map { it.packageName } +
                    packages.queue.map { it.packageName }).distinct().size,
                onClick = onOpenMyApps,
                // Home starts here, at the top, and when updates are waiting OK opens them.
                modifier = Modifier.focusRequester(firstFocus)
            )
            TvVerticalGap()
        }

        if (empty) {
            item(key = "empty") {
                TvEmptyState(
                    message = if (loading) stringResource(R.string.home_catalog_loading)
                    else catalog.error ?: stringResource(R.string.tv_catalog_empty),
                    actionLabel = if (loading) null else stringResource(R.string.common_retry),
                    onAction = if (loading) null else onRetry
                )
            }
            return@LazyColumn
        }

        // One card carries the restore requester: the first place the reopened app appears.
        val restoreRow = rows.indexOfFirst { row -> row.apps.any { it.packageName == restoreFocusTo } }
        rows.forEachIndexed { index, row ->
            item(key = row.key) {
                row.title?.let { TvSectionTitle(stringResource(it)) }
                TvAppRow(
                    apps = row.apps,
                    packages = packages,
                    onOpenApp = onOpenApp,
                    forPhone = row.forPhone,
                    restore = if (index == restoreRow) restoreFocusTo to restoreFocus else null
                )
            }
        }
    }
}

private fun buildHomeRows(
    catalog: TvCatalogUiState,
    mode: TvCatalog,
    packages: TvPackageContext
): List<TvHomeRow> {
    val tvApps = if (mode == TvCatalog.PHONE) emptyList() else catalog.apps
    val tvPackages = catalog.apps.map { it.packageName }.toSet()
    // Among TV apps a phone app is marked, and one published for both is shown once, as a TV app.
    // With the phone catalogue alone there is nothing to tell apart.
    val phoneApps = when (mode) {
        TvCatalog.TV -> emptyList()
        TvCatalog.PHONE -> catalog.phoneApps
        TvCatalog.BOTH -> catalog.phoneApps.filter { it.packageName !in tvPackages }
    }
    if (tvApps.isEmpty() && phoneApps.isEmpty()) return emptyList()
    val markPhone = mode == TvCatalog.BOTH

    // Apps with something to act on come first, because on a TV nothing else says they are there:
    // notifications are not shown.
    val attention = packages.pendingUpdates.map { it.packageName }.toSet() + packages.queue.map { it.packageName }
    val actionable = (tvApps + phoneApps).distinctBy { it.packageName }.filter { it.packageName in attention }

    return buildList {
        if (actionable.isNotEmpty()) {
            add(TvHomeRow("ready", R.string.tv_row_ready, actionable, forPhone = false))
        }
        if (tvApps.isNotEmpty()) {
            add(TvHomeRow("tv-popular", R.string.tv_row_popular, tvApps.take(ROW_SIZE), forPhone = false))
        }
        phoneApps.chunked(ROW_SIZE).forEachIndexed { index, chunk ->
            val title = when {
                index > 0 -> null
                mode == TvCatalog.PHONE -> R.string.tv_row_popular_phone
                else -> R.string.tv_row_phone
            }
            add(TvHomeRow("phone-$index", title, chunk, forPhone = markPhone))
        }
        tvApps.drop(ROW_SIZE).chunked(ROW_SIZE).forEachIndexed { index, chunk ->
            add(TvHomeRow("tv-all-$index", if (index == 0) R.string.tv_row_all else null, chunk, forPhone = false))
        }
    }
}

@Composable
private fun TvAppRow(
    apps: List<StoreApp>,
    packages: TvPackageContext,
    onOpenApp: (String) -> Unit,
    forPhone: Boolean,
    restore: Pair<String?, FocusRequester>? = null
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(TvRowHeight),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        // Room for the focused card to grow without being clipped at either end.
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
    ) {
        items(apps, key = { it.packageName }) { app ->
            val cardModifier = if (restore != null && restore.first == app.packageName) {
                Modifier.focusRequester(restore.second)
            } else {
                Modifier
            }
            TvAppCard(
                app = app,
                state = rememberPackageState(app, packages),
                onClick = { onOpenApp(app.packageName) },
                modifier = cardModifier,
                forPhone = forPhone
            )
        }
    }
}

private const val ROW_SIZE = 12
