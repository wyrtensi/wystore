package dev.wystore.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.runtime.withFrameNanos
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
import dev.wystore.InstallQueueStatus
import dev.wystore.R
import dev.wystore.data.InstalledApp
import dev.wystore.data.ManagedApp
import dev.wystore.data.PendingUpdate
import dev.wystore.data.StoreApp
import dev.wystore.data.GitHubCatalogEntry
import dev.wystore.settings.TvCatalog
import dev.wystore.ui.components.PackageUiState
import dev.wystore.ui.components.PackageUiStateReducer

/** Everything a TV screen needs to know about what is installed and moving. */
data class TvPackageContext(
    val installed: List<InstalledApp>,
    val managed: List<ManagedApp>,
    val queue: List<InstallQueueItem>,
    val pendingUpdates: List<PendingUpdate>
) {
    /**
     * Queue rows still on their way somewhere. A finished or cancelled row stays in the queue for a
     * while as history; counted as waiting, it kept an installed app under "ready to install".
     */
    val activeQueue: List<InstallQueueItem>
        get() = queue.filter { it.status != InstallQueueStatus.COMPLETE && it.status != InstallQueueStatus.CANCELED }
}

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
    /** The GitHub catalogue, empty when GitHub is switched off in the source settings. */
    githubEntries: List<GitHubCatalogEntry>,
    onOpenGitHub: (GitHubCatalogEntry) -> Unit,
    /**
     * Held by the caller: an app page replaces Home while it is open, and a list state of Home's
     * own would come back scrolled to the top - with the card to return to not even composed.
     */
    listState: LazyListState,
    /**
     * The same for every row across, by row key. Without them a row came back scrolled to its
     * start, a card further along was not composed, and Back from its page lost the focus to the
     * menu - which then scrolled Home to the top.
     */
    rowStates: MutableMap<String, LazyListState>,
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
        // Back from an app page returns to that app's card. The rows are composed but may not be
        // laid out on the first frame, so the request is repeated for a few; a request to a card
        // that is not there fails rather than throws, and only then does Home start at the top.
        repeat(FOCUS_ATTEMPTS) {
            if (restoreFocusTo != null && runCatching { restoreFocus.requestFocus() }.getOrDefault(false)) {
                return@LaunchedEffect
            }
            withFrameNanos { }
        }
        runCatching { firstFocus.requestFocus() }
    }

    // Back from inside the screen sends the focus up to the menu; the screen goes back to its top
    // with it, so the menu is not left above a list scrolled halfway down.
    LaunchedEffect(takeFocus) { if (!takeFocus) listState.animateScrollToItem(0) }

    val showCategories = categoryRows.size >= MIN_CATEGORY_TILES
    // GitHub follows the first row of the chosen catalogue, as it follows the categories on the
    // phone: close enough to the top to be found, after what the device is mostly for.
    val githubAfter = if (githubEntries.isEmpty()) -1 else rows.indexOfFirst { it.key == "tv-popular" || it.key == "phone-popular" || it.key == "phone-0" }
    val githubFocus = remember { FocusRequester() }
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
                    packages.activeQueue.map { it.packageName }).distinct().size,
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
                    state = rowStates.getOrPut("categories") { LazyListState() },
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    itemsIndexed(categoryRows, key = { _, row -> row.key }) { index, row ->
                        TvCategoryTile(
                            title = row.title.orEmpty(),
                            accent = index,
                            previewIcons = row.apps.mapNotNull { it.iconUrl?.takeIf(String::isNotBlank) },
                            onClick = {
                                val rowIndex = rows.indexOf(row)
                                val target = rowsStart + rowIndex + if (githubAfter in 0 until rowIndex) 1 else 0
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

        // Every card of the reopened app carries the restore requester: an app is often in more
        // than one row, and only the cards actually composed can take the focus. With one requester
        // on the first row the app appeared in, Back from a card further down aimed at a row
        // scrolled away and lost the focus.
        rows.forEachIndexed { index, row ->
            item(key = row.key) {
                row.title?.let { TvSectionTitle(it) }
                TvAppRow(
                    state = rowStates.getOrPut(row.key) { LazyListState() },
                    apps = row.apps,
                    packages = packages,
                    onOpenApp = onOpenApp,
                    forPhone = row.forPhone,
                    firstCardFocus = rowFocus.getValue(row.key),
                    restore = restoreFocusTo to restoreFocus
                )
            }
            if (index == githubAfter) {
                item(key = "github") {
                    TvSectionTitle(
                        stringResource(R.string.home_github_title),
                        subtitle = stringResource(R.string.home_github_hint)
                    )
                    LazyRow(
                        state = rowStates.getOrPut("github") { LazyListState() },
                        modifier = Modifier.fillMaxWidth().height(TvGitHubRowHeight),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        itemsIndexed(githubEntries, key = { _, entry -> entry.slug }) { _, entry ->
                            TvGitHubCard(
                                entry = entry,
                                onClick = { onOpenGitHub(entry) },
                                modifier = if (restoreFocusTo == entry.tvKey()) Modifier.focusRequester(restoreFocus) else Modifier
                            )
                        }
                    }
                }
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
    val attention = packages.pendingUpdates.map { it.packageName }.toSet() + packages.activeQueue.map { it.packageName }
    val actionable = (tvApps + phoneApps).distinctBy { it.packageName }.filter { it.packageName in attention }

    return buildList {
        if (actionable.isNotEmpty()) {
            add(TvHomeRow("ready", titles.ready, actionable, forPhone = false))
        }
        if (tvApps.isNotEmpty()) {
            add(TvHomeRow("tv-popular", titles.popular, tvApps.take(ROW_SIZE), forPhone = false))
        }
        if (mode == TvCatalog.PHONE) {
            add(TvHomeRow("phone-popular", titles.popularPhone, phoneApps.take(ROW_SIZE), forPhone = false))
        } else {
            phoneApps.chunked(ROW_SIZE).forEachIndexed { index, chunk ->
                add(TvHomeRow("phone-$index", if (index == 0) titles.phone else null, chunk, forPhone = markPhone))
            }
        }
        // The chosen catalogue by category, the largest first: the TV one, or the phone one when it
        // is browsed alone. Mixed in with TV apps the phone apps keep rows of their own above, so a
        // category never holds both. A category of one or two apps is not a row worth scrolling
        // past; those are gathered at the end.
        val categorised = if (mode == TvCatalog.PHONE) phoneApps else tvApps
        val byCategory = categorised.groupBy { it.primaryCategory() }
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
    state: LazyListState,
    apps: List<StoreApp>,
    packages: TvPackageContext,
    onOpenApp: (String) -> Unit,
    forPhone: Boolean,
    firstCardFocus: FocusRequester,
    restore: Pair<String?, FocusRequester>? = null
) {
    LazyRow(
        state = state,
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
private const val FOCUS_ATTEMPTS = 5

/** A GitHub card has no status line, so its row is a little shorter than an app row. */
private val TvGitHubRowHeight = 196.dp
private const val MIN_CATEGORY_SIZE = 3
private const val MIN_CATEGORY_TILES = 2
private const val OTHER_CATEGORY = "\u0000other"
