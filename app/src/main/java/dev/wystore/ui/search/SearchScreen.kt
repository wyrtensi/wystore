package dev.wystore.ui.search

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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
import dev.wystore.ui.components.CategoryPill
import dev.wystore.ui.components.EmptyState
import dev.wystore.ui.components.Loading
import dev.wystore.ui.components.RatingPill
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
    onSourcesChange: (SearchSources) -> Unit = {},
    /** Matches the bundled catalogue without asking RuStore anything; see [SearchSources]. */
    onSearchLocal: (String) -> Unit = {},
    onSearch: (String) -> Unit,
    onLoadMore: () -> Unit = {},
    onOpen: (StoreApp) -> Unit,
    onQuickInstall: (String) -> Unit,
    onInstallPending: (String) -> Unit = {},
    onConfirmUnverifiedSource: (String) -> Unit = {},
    onLaunch: (String) -> Unit
) {
    var text by remember(query) { mutableStateOf(query) }
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

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_title)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding()),
            contentPadding = PaddingValues(
                start = ScreenPadding,
                end = ScreenPadding,
                top = 4.dp,
                bottom = 24.dp + LocalBottomBarInset.current
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.search_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // A filled field with no visible box outline, so the search input reads like the
                    // pill on Home rather than like a form control.
                    TextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.extraLarge),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.search_field_placeholder)) },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        trailingIcon = {
                            when {
                                text.isNotEmpty() -> IconButton(onClick = { text = "" }) {
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
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        )
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SearchSources.entries.forEach { option ->
                            FilterChip(
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
                    if (sources.includesRuStore) {
                        Button(
                            onClick = runSearch,
                            enabled = text.isNotBlank() && !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.search_action)) }
                        DisclaimerNote(stringResource(R.string.search_disclaimer))
                    }
                }
            }

            if (busy) item { Loading(operation) }

            if (!busy && !hasResults) {
                item {
                    if (query.isBlank()) {
                        EmptyState(
                            icon = Icons.Outlined.Search,
                            title = stringResource(R.string.search_start_title),
                            message = stringResource(R.string.search_start_message)
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
                        subtitle = stringResource(R.string.github_catalog_hint)
                    )
                }
                items(visibleGitHub, key = { "github:${it.slug}" }) { entry ->
                    GitHubCatalogCard(entry = entry, onOpen = { onOpenGitHubApp(entry) })
                }
            }

            total?.takeIf { sources.includesRuStore }?.let {
                item {
                    SectionHeader(title = stringResource(R.string.search_results_count, it))
                }
            }
            items(visibleApps, key = { it.packageName }) { app ->
                SearchCard(
                    app = app,
                    installed = installed.firstOrNull { it.packageName == app.packageName },
                    queueItem = queue.firstOrNull { it.packageName == app.packageName },
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
                    OutlinedButton(
                        onClick = onLoadMore,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.search_load_more))
                    }
                }
            } else if (apps.isNotEmpty() && total != null) {
                item {
                    Text(
                        stringResource(R.string.search_all_loaded),
                        modifier = Modifier.fillMaxWidth(),
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

    WyCard(modifier = Modifier.fillMaxWidth(), onClick = { onOpen(app) }) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(
                    model = app.iconUrl,
                    contentDescription = stringResource(R.string.common_app_icon, app.name),
                    size = 58.dp
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        app.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
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
                    // A flow rather than a row: on a narrow screen the category drops under the
                    // rating instead of being squeezed down to "Объ...".
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        app.rating?.let { RatingPill(it, app.ratingCount) }
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
                            }
                        ) {
                            Text(
                                stringResource(
                                    if (installed == null) R.string.common_install else R.string.common_update
                                ),
                                maxLines = 1
                            )
                        }
                    } else if (installed == null) {
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                onQuickInstall(app.packageName)
                            }
                        ) { Text(stringResource(R.string.common_install), maxLines = 1) }
                    } else {
                        FilledTonalButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                onLaunch(installed.packageName)
                            }
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
                        OutlinedButton(
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.search_installed_version, installed.versionName),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                onQuickInstall(app.packageName)
                            }
                        ) { Text(stringResource(R.string.search_check_update), maxLines = 1) }
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
private fun GitHubCatalogCard(entry: GitHubCatalogEntry, onOpen: () -> Unit) {
    WyCard(modifier = Modifier.fillMaxWidth(), onClick = onOpen) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(model = entry.iconUrl, contentDescription = null, size = 58.dp)
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

            if (entry.summary.isNotBlank()) {
                Text(
                    entry.summary,
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
                if (entry.curated) CategoryPill(stringResource(R.string.github_curated_badge))
                entry.categories.forEach { CategoryPill(it) }
            }

            FilledTonalButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.github_view_releases))
            }
        }
    }
}
