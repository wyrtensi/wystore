package dev.wystore.ui.catalog

import dev.wystore.ui.components.groupPosition
import dev.wystore.ui.components.color
import dev.wystore.ui.components.StatusTone
import dev.wystore.ui.components.HubNote
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.foundation.layout.height
import dev.wystore.ui.components.fadingTopEdge
import dev.wystore.ui.components.HubPageHeader
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.wystore.ui.components.WySpinner
import dev.wystore.data.InstalledApp
import dev.wystore.data.ManagedApp
import dev.wystore.data.PendingUpdate
import dev.wystore.InstallQueueItem
import dev.wystore.rowFor
import dev.wystore.ui.components.RowActionPolicy
import dev.wystore.ui.components.RowAction
import dev.wystore.ui.components.LocalBottomBarInset
import dev.wystore.ui.components.AppRow
import dev.wystore.ui.components.EmptyState
import dev.wystore.ui.components.ScreenPadding
import dev.wystore.ui.components.PackageUiStateReducer
import dev.wystore.R

/**
 * Browses one catalog section page by page. Paging is explicit rather than infinite-scroll because
 * every page is a full HTML fetch against the source.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    state: CategoryUiState,
    installed: List<InstalledApp> = emptyList(),
    // Without this the same app is badged "RuStore" on Home and "Другое" here: the reducer falls
    // back to how the APK got onto the device when nobody tells it which source manages the app.
    managed: List<ManagedApp> = emptyList(),
    // The same queue and downloads every other app list reads, so a row here shows its download,
    // its progress and "ready to install" rather than a bare Install button.
    queue: List<InstallQueueItem> = emptyList(),
    pendingUpdates: List<PendingUpdate> = emptyList(),
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onAppClick: (String) -> Unit = {},
    onPreviousPage: () -> Unit = {},
    onNextPage: () -> Unit = {},
    onRetry: () -> Unit = {},
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
    val listState = rememberLazyListState()
    // A new page is a new list, so it starts at its top. Only on a change of page: coming back from
    // an app's details recomposes this screen too, and that must keep the place in the list.
    var shownPage by rememberSaveable { mutableIntStateOf(state.page) }
    LaunchedEffect(state.page) {
        if (state.page != shownPage) {
            shownPage = state.page
            listState.scrollToItem(0)
        }
    }
    // The note arrives after the saved list, once the refresh has failed, and a lazy list keeps its
    // first row in place when something is inserted above it - so the note landed above the screen
    // and was never seen. Still at the top, the list moves up to show it.
    LaunchedEffect(state.stale) {
        if (state.stale && listState.firstVisibleItemIndex <= 1) listState.scrollToItem(0)
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            // The concept's page header: a round back button, where the page belongs as the
            // accent line, the title in plain weight.
            HubPageHeader(
                title = state.title,
                eyebrow = "RuStore",
                onBack = onBack
            )
        }
    ) { contentPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding()).fadingTopEdge(),
            contentPadding = PaddingValues(
                start = ScreenPadding,
                end = ScreenPadding,
                top = 20.dp,
                bottom = 24.dp + LocalBottomBarInset.current
            )
        ) {
            if (state.stale) {
                // A grey line of small print at the top of the list sat in the fade under the header,
                // so the one thing saying these apps may be out of date was the hardest to read.
                item {
                    HubNote(
                        text = stringResource(R.string.catalog_stale),
                        icon = Icons.Outlined.Warning,
                        tint = StatusTone.PAUSED.color() ?: MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
            }

            // One panel of rows, as on Home: a card per app made a section of forty apps forty boxes.
            itemsIndexed(state.apps, key = { _, app -> app.packageName }) { index, app ->
                val uiState = PackageUiStateReducer.reduce(
                    app = app,
                    installed = installed.firstOrNull { it.packageName == app.packageName },
                    managed = managed.firstOrNull { it.packageName == app.packageName },
                    queueItem = queue.rowFor(app.packageName),
                    pendingUpdate = pendingUpdates.firstOrNull { it.packageName == app.packageName },
                    resources = LocalResources.current
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
                    // Every section here is RuStore's, and the page header names it.
                    implicitSource = "RuStore",
                    groupPosition = groupPosition(index, state.apps.size)
                )
            }

            item {
                if (state.apps.isNotEmpty()) Spacer(Modifier.height(14.dp))
                // "Next" with no network left the page as it was and said nothing: the failure was
                // only shown for an empty list, and this one still held the previous page.
                if (state.failedOverList) {
                    HubNote(
                        text = state.error.orEmpty(),
                        icon = Icons.Outlined.Warning,
                        tint = StatusTone.FAILED.color() ?: MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                    dev.wystore.ui.components.HubWideButton(
                        text = stringResource(R.string.common_retry),
                        onClick = onRetry,
                        chevron = false,
                        modifier = Modifier.padding(bottom = 14.dp)
                    )
                }
                when {
                    state.loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        WySpinner(size = 18.dp, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.catalog_loading), style = MaterialTheme.typography.bodySmall)
                    }
                    state.apps.isEmpty() -> EmptyState(
                        icon = Icons.AutoMirrored.Outlined.List,
                        title = stringResource(R.string.catalog_empty),
                        message = state.error,
                        actionLabel = state.error?.let { stringResource(R.string.common_retry) },
                        onActionClick = if (state.error != null) onRetry else null
                    )
                    else -> Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(onClick = onPreviousPage, enabled = state.canGoBack) { Text(stringResource(R.string.common_back)) }
                        Text(
                            state.lastPage?.let { stringResource(R.string.catalog_page_of, state.page, it) } ?: stringResource(R.string.catalog_page, state.page),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FilledTonalButton(onClick = onNextPage, enabled = state.canGoForward) { Text(stringResource(R.string.common_next)) }
                    }
                }
            }
        }
    }
}
