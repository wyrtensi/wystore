package dev.wystore.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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

/**
 * Home on a TV: rows of cards, one screen of choices at a time.
 *
 * Up and down move between rows, left and right within one, and the first card of the first row
 * holds the focus on arrival. Rows are what a remote is good at; the phone's long list with a
 * button on every line is not.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvHomeScreen(
    catalog: TvCatalogUiState,
    packages: TvPackageContext,
    firstCardFocus: FocusRequester,
    restoreFocusTo: String?,
    onOpenApp: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Apps with something to act on - a download that finished, an update found - come first,
    // because on a TV nothing else says they are there: notifications are not shown.
    val actionable = remember(catalog.apps, packages) {
        val attention = packages.pendingUpdates.map { it.packageName }.toSet() +
            packages.queue.map { it.packageName }.toSet()
        catalog.apps.filter { it.packageName in attention }
    }
    val popular = remember(catalog.apps) { catalog.apps.take(POPULAR_COUNT) }
    val rest = remember(catalog.apps) { catalog.apps.drop(POPULAR_COUNT).chunked(ROW_SIZE) }
    // The first card takes the focus once there is one, so the remote works from the first press
    // instead of the first press having to find something to focus.
    val hasContent = catalog.apps.isNotEmpty() || !catalog.loading
    val restoreFocus = remember { FocusRequester() }
    LaunchedEffect(hasContent) {
        if (!hasContent) return@LaunchedEffect
        // Back from an app page returns to that app's card when it is still on screen.
        val restored = restoreFocusTo != null && runCatching { restoreFocus.requestFocus() }.isSuccess
        if (!restored) runCatching { firstCardFocus.requestFocus() }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = TvOverscanHorizontal,
            end = TvOverscanHorizontal,
            top = TvOverscanVertical,
            bottom = TvOverscanVertical + 48.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                stringResource(if (catalog.stale) R.string.home_catalog_stale else R.string.home_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TvVerticalGap()
        }

        if (catalog.apps.isEmpty()) {
            item {
                TvEmptyState(
                    message = if (catalog.loading) stringResource(R.string.home_catalog_loading)
                    else catalog.error ?: stringResource(R.string.tv_catalog_empty),
                    actionLabel = if (catalog.loading) null else stringResource(R.string.common_retry),
                    onAction = if (catalog.loading) null else onRetry,
                    modifier = Modifier.focusRequester(firstCardFocus)
                )
            }
            return@LazyColumn
        }

        val rows = buildList {
            if (actionable.isNotEmpty()) add(R.string.tv_row_ready to actionable)
            add(R.string.tv_row_popular to popular)
        }
        // One card carries the restore requester: the first place the reopened app appears.
        val allRows = rows.map { it.second } + rest
        val restoreRow = allRows.indexOfFirst { row -> row.any { it.packageName == restoreFocusTo } }
        rows.forEachIndexed { rowIndex, (title, apps) ->
            item(key = "row-$title") {
                TvSectionTitle(stringResource(title))
                TvAppRow(
                    apps = apps,
                    packages = packages,
                    onOpenApp = onOpenApp,
                    firstCardFocus = if (rowIndex == 0) firstCardFocus else null,
                    restore = if (rowIndex == restoreRow) restoreFocusTo to restoreFocus else null
                )
            }
        }
        if (rest.isNotEmpty()) {
            item(key = "all-title") { TvSectionTitle(stringResource(R.string.tv_row_all)) }
            items(rest.size, key = { "all-$it" }) { index ->
                TvAppRow(
                    apps = rest[index],
                    packages = packages,
                    onOpenApp = onOpenApp,
                    restore = if (rows.size + index == restoreRow) restoreFocusTo to restoreFocus else null
                )
            }
        }
    }
}

@Composable
private fun TvAppRow(
    apps: List<StoreApp>,
    packages: TvPackageContext,
    onOpenApp: (String) -> Unit,
    firstCardFocus: FocusRequester? = null,
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
            val index = apps.indexOf(app)
            var cardModifier: Modifier = Modifier
            if (index == 0 && firstCardFocus != null) cardModifier = cardModifier.focusRequester(firstCardFocus)
            if (restore != null && restore.first == app.packageName) cardModifier = cardModifier.focusRequester(restore.second)
            TvAppCard(
                app = app,
                state = rememberPackageState(app, packages),
                onClick = { onOpenApp(app.packageName) },
                modifier = cardModifier
            )
        }
    }
}

private const val POPULAR_COUNT = 12
private const val ROW_SIZE = 12
