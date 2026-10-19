package dev.wystore.ui.search

import androidx.compose.material.icons.outlined.Warning
import dev.wystore.ui.components.fadingTopEdge
import dev.wystore.ui.components.HubChoiceChip
import dev.wystore.ui.components.HubTextAction
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import dev.wystore.ui.components.GroupPosition
import dev.wystore.ui.components.HubGroupRow
import dev.wystore.ui.components.HubHeader
import dev.wystore.ui.components.HubNote
import dev.wystore.ui.components.HubTag
import dev.wystore.ui.components.HubWideButton
import dev.wystore.ui.components.groupPosition
import dev.wystore.ui.theme.LocalHubStyle
import dev.wystore.rowFor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.wystore.ui.components.WySpinner
import dev.wystore.InstallQueueItem
import dev.wystore.InstallQueueStatus
import dev.wystore.updates.UnverifiedSourceConsent
import dev.wystore.isInFlight
import dev.wystore.R
import dev.wystore.data.SearchSources
import dev.wystore.data.GitHubCatalogEntry
import dev.wystore.data.InstalledApp
import dev.wystore.data.PendingUpdate
import dev.wystore.data.StoreApp
import dev.wystore.ui.components.LocalBottomBarInset
import dev.wystore.ui.components.AppIcon
import dev.wystore.ui.components.rowButtonPadding
import dev.wystore.ui.components.isNarrowScreen
import dev.wystore.ui.components.listingLine
import dev.wystore.ui.components.CategoryPill
import dev.wystore.ui.components.EmptyState
import dev.wystore.ui.components.Loading
import dev.wystore.ui.components.ScreenPadding
import dev.wystore.ui.components.SectionHeader
import dev.wystore.ui.components.WyCard
import dev.wystore.ui.components.formatDuration
import dev.wystore.ui.components.formatSize
import dev.wystore.ui.components.formatSpeed
import dev.wystore.ui.components.queueStatusLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    query: String,
    apps: List<StoreApp>,
    total: Int?,
    busy: Boolean,
    operation: String?,
    installed: List<InstalledApp>,
    queue: List<InstallQueueItem>,
    pendingUpdates: List<PendingUpdate> = emptyList(),
    githubResults: List<GitHubCatalogEntry> = emptyList(),
    onOpenGitHubApp: (GitHubCatalogEntry) -> Unit = {},
    sources: SearchSources = SearchSources.ALL,
    /** The GitHub source in settings; switched off, search is RuStore's alone and says so. */
    githubEnabled: Boolean = true,
    onSourcesChange: (SearchSources) -> Unit = {},
    /** Matches the bundled catalogue without asking RuStore anything; see [SearchSources]. */
    onSearchLocal: (String) -> Unit = {},
    onSearch: (String) -> Unit,
    onLoadMore: () -> Unit = {},
    onOpen: (StoreApp) -> Unit,
    onQuickInstall: (String) -> Unit,
    onInstallPending: (String) -> Unit = {},
    onConfirmUnverifiedSource: (String) -> Unit = {},
    onLaunch: (String) -> Unit,
    /** Why the last search came back with nothing, when it failed rather than found nothing. */
    error: String? = null,
    /** Forgets the query along with the field, so Home does not go on showing it. */
    onClear: () -> Unit = {},
    /** True when arrived here by tapping the search pill on Home. */
    autoFocus: Boolean = false,
    onAutoFocusConsumed: () -> Unit = {}
) {
    @Suppress("NAME_SHADOWING")
    val sources = sources.within(githubEnabled)
    var fieldValue by remember(query) {
        mutableStateOf(TextFieldValue(text = query, selection = TextRange(query.length)))
    }
    val text = fieldValue.text
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val runSearch = {
        val requested = text.trim()
        if (requested.isNotEmpty() && !busy) onSearch(requested)
    }
    // With RuStore out of the picture there is nothing to wait for and nothing to press: the
    // bundled catalogue is already here, so the list follows the typing.
    LaunchedEffect(text, sources) {
        if (!sources.includesRuStore) onSearchLocal(text)
    }
    val visibleGitHub = if (sources.includesGitHub) githubResults else emptyList()
    val visibleApps = if (sources.includesRuStore) apps else emptyList()
    val hasResults = visibleApps.isNotEmpty() || visibleGitHub.isNotEmpty()

    // The Wy Hub screen, scrolling as one: the header and the field go up with the results, so a
    // long list of them has the whole screen.
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
            item {
                HubHeader(
                    title = stringResource(R.string.search_title),
                    eyebrow = if (githubEnabled) "RuStore · GitHub" else "RuStore",
                    subtitle = stringResource(R.string.search_subtitle),
                    horizontalPadding = 0.dp,
                    insetStatusBar = false,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            item {
                LaunchedEffect(autoFocus) {
                    if (!autoFocus) return@LaunchedEffect
                    // The field sits inside a LazyColumn item and only opens its IME input session
                    // once layout and focus have settled; asking on the first frame is ignored.
                    repeat(5) {
                        withFrameNanos { }
                        if (runCatching { focusRequester.requestFocus() }.getOrDefault(false)) {
                            withFrameNanos { }
                            kotlinx.coroutines.delay(50)
                            keyboardController?.show()
                            onAutoFocusConsumed()
                            return@LaunchedEffect
                        }
                    }
                    onAutoFocusConsumed()
                }
                Column(
                    modifier = Modifier.padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // A filled field with no visible box outline, so the search input reads like the
                    // pill on Home rather than like a form control.
                    TextField(
                        value = fieldValue,
                        onValueChange = { fieldValue = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .clip(RoundedCornerShape(percent = 50))
                            .then(
                                if (LocalHubStyle.current) Modifier.border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(percent = 50)
                                ) else Modifier
                            ),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.search_field_placeholder)) },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        trailingIcon = {
                            when {
                                text.isNotEmpty() -> IconButton(onClick = {
                                    fieldValue = TextFieldValue("")
                                    onClear()
                                }) {
                                    Icon(
                                        Icons.Outlined.Clear,
                                        contentDescription = stringResource(R.string.search_clear)
                                    )
                                }
                                busy -> WySpinner(size = 20.dp, strokeWidth = 2.dp)
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                        shape = RoundedCornerShape(percent = 50),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        )
                    )
                    // One source left is no choice to offer.
                    if (githubEnabled) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SearchSources.entries.forEach { option ->
                            HubChoiceChip(
                                selected = sources == option,
                                onClick = { onSourcesChange(option) },
                                label = {
                                    Text(
                                        stringResource(
                                            when (option) {
                                                SearchSources.ALL -> R.string.search_sources_all
                                                SearchSources.RUSTORE -> R.string.search_sources_rustore
                                                SearchSources.GITHUB -> R.string.search_sources_github
                                            }
                                        )
                                    )
                                }
                            )
                        }
                    }
                    // GitHub alone has no button: its results follow the typing (see above). With both
                    // sources the one press searches both, and the label says so.
                    if (sources.includesRuStore) {
                        Button(
                            onClick = runSearch,
                            enabled = text.isNotBlank() && !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                stringResource(
                                    if (sources.includesGitHub) R.string.search_action_all else R.string.search_action
                                )
                            )
                        }
                        HubNote(text = stringResource(R.string.search_disclaimer), icon = Icons.Outlined.Info)
                    }
                }
            }

            if (busy) item { Box(Modifier.padding(bottom = 14.dp)) { Loading(operation) } }

            if (!busy && !hasResults) {
                item {
                    if (error != null && sources.includesRuStore) {
                        // A failure is not "nothing found": RuStore was not reached, and the way
                        // out is to try again, not to shorten the query.
                        EmptyState(
                            icon = Icons.Outlined.Warning,
                            title = stringResource(R.string.search_failed_title),
                            message = error,
                            actionLabel = stringResource(R.string.common_retry),
                            onActionClick = runSearch
                        )
                    } else if (query.isBlank()) {
                        EmptyState(
                            icon = Icons.Outlined.Search,
                            // It said "two sources at once" whichever one was chosen.
                            title = stringResource(
                                when (sources) {
                                    SearchSources.ALL -> R.string.search_start_title
                                    SearchSources.RUSTORE -> R.string.search_start_rustore_title
                                    SearchSources.GITHUB -> R.string.search_start_github_title
                                }
                            ),
                            message = stringResource(
                                when (sources) {
                                    SearchSources.ALL -> R.string.search_start_message
                                    SearchSources.RUSTORE -> R.string.search_start_rustore_message
                                    SearchSources.GITHUB -> R.string.search_start_github_message
                                }
                            )
                        )
                    } else {
                        EmptyState(
                            icon = Icons.Outlined.Search,
                            title = stringResource(R.string.search_empty_title),
                            message = stringResource(R.string.search_empty_message)
                        )
                    }
                }
            }

            // GitHub entries come from a local catalogue, so they can be shown before the RuStore
            // request finishes rather than being hidden behind it.
            if (visibleGitHub.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = stringResource(R.string.github_catalog_section),
                        subtitle = stringResource(R.string.github_catalog_hint),
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }
                itemsIndexed(visibleGitHub, key = { _, it -> "github:${it.slug}" }) { index, entry ->
                    GitHubCatalogCard(
                        entry = entry,
                        onOpen = { onOpenGitHubApp(entry) },
                        position = groupPosition(index, visibleGitHub.size)
                    )
                }
                item { Spacer(Modifier.height(18.dp)) }
            }

            total?.takeIf { sources.includesRuStore }?.let {
                item {
                    SectionHeader(
                        title = stringResource(R.string.search_results_count, it),
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }
            }
            itemsIndexed(visibleApps, key = { _, it -> it.packageName }) { index, app ->
                SearchCard(
                    position = groupPosition(index, visibleApps.size),
                    app = app,
                    installed = installed.firstOrNull { it.packageName == app.packageName },
                    queueItem = queue.rowFor(app.packageName),
                    pendingUpdate = pendingUpdates.firstOrNull { it.packageName == app.packageName },
                    onOpen = onOpen,
                    onQuickInstall = onQuickInstall,
                    onInstallPending = onInstallPending,
                    onConfirmUnverifiedSource = onConfirmUnverifiedSource,
                    onLaunch = onLaunch
                )
            }

            // The source returns one page at a time; without this the list silently stopped at the
            // first page even when the result count said there was much more.
            if (apps.isNotEmpty() && total != null && apps.size < total) {
                item {
                    HubWideButton(
                        text = stringResource(R.string.search_load_more),
                        onClick = { if (!busy) onLoadMore() },
                        modifier = Modifier.padding(top = 14.dp)
                    )
                }
            } else if (apps.isNotEmpty() && total != null) {
                item {
                    Text(
                        stringResource(R.string.search_all_loaded),
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** A caption the eye can skip but the user can find: quiet, boxed, with an icon to anchor it. */
@Composable
private fun DisclaimerNote(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchCard(
    app: StoreApp,
    /** Where the card stands in a list drawn as one panel of rows; null for a card of its own. */
    position: GroupPosition? = null,
    installed: InstalledApp?,
    queueItem: InstallQueueItem?,
    pendingUpdate: PendingUpdate?,
    onOpen: (StoreApp) -> Unit,
    onQuickInstall: (String) -> Unit,
    onInstallPending: (String) -> Unit,
    onConfirmUnverifiedSource: (String) -> Unit,
    onLaunch: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val inFlight = queueItem?.status?.isInFlight == true

    Framed(position = position, onClick = { onOpen(app) }) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(
                    model = app.iconUrl,
                    contentDescription = stringResource(R.string.common_app_icon, app.name),
                    size = if (position != null) 48.dp else 58.dp
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    // Two lines, as on Home: catalogue names run long and one line cut them mid-word.
                    Text(
                        app.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    app.publisher.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    // The same line Home shows under a name. The coloured rating pill and the boxed
                    // section here made one app look like two different things on two screens.
                    if (app.rating != null || app.categories.isNotEmpty()) Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        app.rating?.let {
                            Text(
                                "★ " + String.format(java.util.Locale.getDefault(), "%.1f", it),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        app.categories.firstOrNull()?.let { CategoryPill(it) }
                    }
                }
                // The action stays on the title line: the card keeps one row of chrome instead of
                // growing a button strip under every result.
                if (!inFlight && queueItem?.status != InstallQueueStatus.FAILED) {
                    Spacer(Modifier.width(10.dp))
                    // A downloaded APK waiting for confirmation is the step the user is in the
                    // middle of; the card used to ignore it and offer "Open", which opened the old
                    // version and left the new one sitting on disk.
                    if (pendingUpdate != null) {
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                onInstallPending(app.packageName)
                            },
                            contentPadding = rowButtonPadding()
                        ) {
                            Text(
                                stringResource(
                                    if (installed == null) R.string.common_install else R.string.common_update
                                ),
                                maxLines = 1
                            )
                        }
                    } else if (installed == null) {
                        // Outlined, as in every other list: see AppRow.
                        OutlinedButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                onQuickInstall(app.packageName)
                            },
                            contentPadding = rowButtonPadding()
                        ) { Text(stringResource(R.string.common_install), maxLines = 1) }
                    } else {
                        FilledTonalButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                onLaunch(installed.packageName)
                            },
                            contentPadding = rowButtonPadding()
                        ) { Text(stringResource(R.string.common_open), maxLines = 1) }
                    }
                }
            }

            app.shortDescription.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            when {
                inFlight -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            queueStatusLabel(requireNotNull(queueItem)),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        queueItem.progress?.let { progress ->
                            LinearProgressIndicator(
                                progress = { progress.fraction.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.extraSmall)
                            )
                            Text(
                                stringResource(
                                    R.string.search_progress_line,
                                    formatSize(progress.downloadedBytes),
                                    formatSize(progress.totalBytes),
                                    formatSpeed(progress.bytesPerSecond)
                                ) + progress.etaSeconds
                                    ?.let { " · " + stringResource(R.string.progress_eta, formatDuration(it)) }
                                    .orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                queueItem?.status == InstallQueueStatus.FAILED -> {
                    // The source refusing to vouch for its own file is a question, not a verdict:
                    // retrying fetches the same file and fails the same way, so the answer is the
                    // button and the retry keeps its place beside it.
                    val unconfirmed = UnverifiedSourceConsent
                        .isAnswerable(queueItem.errorCode, queueItem.detail)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            queueStatusLabel(queueItem),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                onQuickInstall(app.packageName)
                            }
                        ) { Text(stringResource(R.string.common_retry), maxLines = 1) }
                        if (unconfirmed) {
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                    onConfirmUnverifiedSource(app.packageName)
                                }
                            ) {
                                Text(stringResource(R.string.unverified_source_confirm), maxLines = 1)
                            }
                        }
                    }
                }
                installed != null -> {
                    val versionLine: @Composable (Modifier) -> Unit = { m ->
                        Text(
                            stringResource(R.string.search_installed_version, installed.versionName),
                            modifier = m,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val checkAction: @Composable () -> Unit = {
                        HubTextAction(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                onQuickInstall(app.packageName)
                            }
                        ) { Text(stringResource(R.string.search_check_update), maxLines = 1) }
                    }
                    if (isNarrowScreen()) {
                        // Beside the button the version line was left a few characters and broke
                        // mid-word; on a narrow screen it keeps the full width of the row and the
                        // button sits under it.
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                            versionLine(Modifier.fillMaxWidth())
                            checkAction()
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            versionLine(Modifier.weight(1f))
                            checkAction()
                        }
                    }
                }
            }
        }
    }
}

/**
 * A GitHub repository rendered with the same weight as a RuStore result: icon, title, publisher,
 * summary and an action, so the two sources read as one list instead of GitHub looking like a stub.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GitHubCatalogCard(entry: GitHubCatalogEntry, onOpen: () -> Unit, position: GroupPosition? = null) {
    Framed(position = position, onClick = onOpen) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(model = entry.iconUrl, contentDescription = null, size = if (position != null) 48.dp else 58.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        entry.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        entry.repository.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            val summary = entry.summaryIn(LocalConfiguration.current.locales[0].language)
            if (summary.isNotBlank()) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CategoryPill("GitHub")
                entry.categories.forEach { CategoryPill(it) }
            }
            // In a panel of rows the row itself opens the page; a full-width button under every
            // result made each one a form. A card of its own keeps it.
            if (position == null) {
                FilledTonalButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.github_view_releases))
                }
            }
        }
    }
}

/** A card of its own, or a row of a panel when it has a [position] in one. */
@Composable
private fun Framed(position: GroupPosition?, onClick: () -> Unit, content: @Composable () -> Unit) {
    if (position != null) HubGroupRow(position = position, onClick = onClick) { content() }
    else WyCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) { content() }
}
