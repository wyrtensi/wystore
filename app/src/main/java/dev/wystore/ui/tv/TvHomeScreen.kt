package dev.wystore.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
    val title: String?,
    val apps: List<StoreApp>,
    val forPhone: Boolean,
    /** Set on a category's row, which a category tile scrolls to. */
    val category: String? = null
)

/**
 * Home on a TV: the update card, the categories as coloured tiles, and rows of cards.
 *
 * Up and down move between rows, left and right within one. Which rows there are follows the
 * catalogue setting: the TV catalogue, the phone one, or the TV one with the phone apps in rows of
 * their own, each card marked so a phone app is not taken for a TV one. The TV catalogue is laid
 * out by category, the way the phone's Home is entered by category, rather than as one long list
 * cut into rows of twelve.
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
    val popularTitle = stringResource(R.string.tv_row_popular)
    val popularPhoneTitle = stringResource(R.string.tv_row_popular_phone)
    val phoneTitle = stringResource(R.string.tv_row_phone)
    val readyTitle = stringResource(R.string.tv_row_ready)
    val otherTitle = stringResource(R.string.tv_row_other)
    val titles = HomeRowTitles(readyTitle, popularTitle, popularPhoneTitle, phoneTitle, otherTitle)
    val rows = remember(catalog.apps, catalog.phoneApps, catalogMode, packages, titles) {
        buildHomeRows(catalog, catalogMode, packages, titles)
    }
    val categoryRows = remember(rows) { rows.filter { it.category != null } }
    val loading = if (catalogMode == TvCatalog.PHONE) catalog.phoneLoading else catalog.loading
    val empty = rows.isEmpty()
    val hasContent = !empty || !loading
    val restoreFocus = remember { FocusRequester() }
    val rowFocus = remember(rows) { rows.associate { it.key to FocusRequester() } }
    val shouldTakeFocus by rememberUpdatedState(takeFocus)
    val scope = rememberCoroutineScope()
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

    val showCategories = categoryRows.size >= MIN_CATEGORY_TILES
    // Items before the first row: the update card, and the category rail when it is there.
    val rowsStart = 1 + if (showCategories) 1 else 0

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
                    style = MaterialTheme.typography.bodyMedium,
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
            Spacer(Modifier.height(16.dp))
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

        if (showCategories) {
            item(key = "categories") {
                TvSectionTitle(stringResource(R.string.home_categories_title))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    itemsIndexed(categoryRows, key = { _, row -> row.key }) { index, row ->
                        TvCategoryTile(
                            title = row.title.orEmpty(),
                            accent = index,
                            previewIcons = row.apps.mapNotNull { it.iconUrl?.takeIf(String::isNotBlank) },
                            onClick = {
                                val target = rowsStart + rows.indexOf(row)
                                scope.launch {
                                    listState.animateScrollToItem(target)
                                    runCatching { rowFocus.getValue(row.key).requestFocus() }
                                }
                            }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        // One card carries the restore requester: the first place the reopened app appears.
        val restoreRow = rows.indexOfFirst { row -> row.apps.any { it.packageName == restoreFocusTo } }
        rows.forEachIndexed { index, row ->
            item(key = row.key) {
                row.title?.let { TvSectionTitle(it) }
                TvAppRow(
                    apps = row.apps,
                    packages = packages,
                    onOpenApp = onOpenApp,
                    forPhone = row.forPhone,
                    firstCardFocus = rowFocus.getValue(row.key),
                    restore = if (index == restoreRow) restoreFocusTo to restoreFocus else null
                )
            }
        }
    }
}

private data class HomeRowTitles(
    val ready: String,
    val popular: String,
    val popularPhone: String,
    val phone: String,
    val other: String
)

private fun buildHomeRows(
    catalog: TvCatalogUiState,
    mode: TvCatalog,
    packages: TvPackageContext,
    titles: HomeRowTitles
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
            add(TvHomeRow("ready", titles.ready, actionable, forPhone = false))
        }
        if (tvApps.isNotEmpty()) {
            add(TvHomeRow("tv-popular", titles.popular, tvApps.take(ROW_SIZE), forPhone = false))
        }
        phoneApps.chunked(ROW_SIZE).forEachIndexed { index, chunk ->
            val title = when {
                index > 0 -> null
                mode == TvCatalog.PHONE -> titles.popularPhone
                else -> titles.phone
            }
            add(TvHomeRow("phone-$index", title, chunk, forPhone = markPhone))
        }
        // The catalogue by category, the largest first. A category of one or two apps is not a
        // row worth scrolling past; those are gathered at the end.
        val byCategory = tvApps.groupBy { it.primaryCategory() }
        val (large, small) = byCategory.entries.partition { (name, apps) -> name != null && apps.size >= MIN_CATEGORY_SIZE }
        large.sortedByDescending { it.value.size }.forEach { (name, apps) ->
            add(TvHomeRow("category-$name", name, apps, forPhone = false, category = name))
        }
        val rest = small.flatMap { it.value }
        if (rest.isNotEmpty()) {
            add(TvHomeRow("category-other", titles.other, rest, forPhone = false, category = OTHER_CATEGORY))
        }
    }
}

@Composable
private fun TvAppRow(
    apps: List<StoreApp>,
    packages: TvPackageContext,
    onOpenApp: (String) -> Unit,
    forPhone: Boolean,
    firstCardFocus: FocusRequester,
    restore: Pair<String?, FocusRequester>? = null
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(TvRowHeight),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        // Room for the focused card to grow without being clipped at either end.
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
    ) {
        itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
            var cardModifier: Modifier = Modifier
            if (index == 0) cardModifier = cardModifier.focusRequester(firstCardFocus)
            if (restore != null && restore.first == app.packageName) cardModifier = cardModifier.focusRequester(restore.second)
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
private const val MIN_CATEGORY_SIZE = 3
private const val MIN_CATEGORY_TILES = 2
private const val OTHER_CATEGORY = " other"
