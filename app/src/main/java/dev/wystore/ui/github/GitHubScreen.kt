package dev.wystore.ui.github

import dev.wystore.ui.components.bleedToScreenEdges
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.saveable.rememberSaveable
import dev.wystore.ui.components.CategoryLabels
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import dev.wystore.InstallQueueItem
import dev.wystore.R
import dev.wystore.data.GitHubCatalogEntry
import dev.wystore.data.GitHubRelease
import dev.wystore.data.GitHubRepository
import dev.wystore.ui.components.LocalBottomBarInset
import dev.wystore.ui.components.AppIcon
import dev.wystore.ui.components.CategoryPill
import dev.wystore.ui.components.EmptyState
import dev.wystore.ui.components.Loading
import dev.wystore.ui.components.OperationProgress
import dev.wystore.ui.components.ScreenPadding
import dev.wystore.ui.components.SectionHeader
import dev.wystore.ui.components.WyCard

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GitHubScreen(
    modifier: Modifier = Modifier,
    repositories: List<GitHubRepository>,
    activeRepository: GitHubRepository?,
    releases: List<GitHubRelease>,
    loading: Boolean,
    install: InstallQueueItem?,
    catalog: List<GitHubCatalogEntry> = emptyList(),
    onLoad: (String) -> Unit,
    onOpen: (GitHubRelease) -> Unit,
    onOpenCatalogEntry: (GitHubCatalogEntry) -> Unit = {},
    onRemove: (GitHubRepository) -> Unit,
    /** For an install waiting on Android's dialog off screen, by queue row. */
    onShowInstallDialog: (String) -> Unit = {},
    onCancelInstall: (String) -> Unit = {},
    /** What is on the phone, so a catalogue entry can say it is installed. */
    installed: List<dev.wystore.data.InstalledApp> = emptyList(),
    managed: List<dev.wystore.data.ManagedApp> = emptyList()
) {
    var input by remember(activeRepository) { mutableStateOf(activeRepository?.url.orEmpty()) }
    // The catalogue section shown, or null for all of them.
    var section by rememberSaveable { mutableStateOf<String?>(null) }
    var showAllReleases by rememberSaveable(activeRepository?.displayName) { mutableStateOf(false) }
    val installedPackages = remember(installed) { installed.mapTo(HashSet()) { it.packageName } }
    val haptic = LocalHapticFeedback.current
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("GitHub Releases") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.github_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.large),
                        singleLine = true,
                        label = { Text(stringResource(R.string.github_repo_url_label)) },
                        placeholder = { Text("https://github.com/owner/repository") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { if (input.isNotBlank()) onLoad(input) }),
                        shape = MaterialTheme.shapes.large,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        )
                    )
                    Button(
                        onClick = { haptic.performHapticFeedback(HapticFeedbackType.Confirm); onLoad(input) },
                        enabled = input.isNotBlank() && !loading,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.github_open_releases)) }
                }
            }
            if (repositories.isNotEmpty()) item {
                SectionHeader(title = stringResource(R.string.github_saved))
                Spacer(Modifier.padding(top = 4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    repositories.forEach { repo ->
                        FilterChip(
                            selected = repo == activeRepository,
                            onClick = { input = repo.url; onLoad(repo.url) },
                            label = { Text(repo.displayName) },
                            trailingIcon = {
                                IconButton(onClick = { onRemove(repo) }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = stringResource(R.string.github_remove_repo, repo.displayName)
                                    )
                                }
                            }
                        )
                    }
                }
            }
            // What the button just did, directly under the button. This block used to sit below
            // the whole built-in catalogue, so pressing "Open releases" scrolled nothing into
            // view and looked exactly like nothing happening.
            if (loading) item { Loading(stringResource(R.string.github_loading_releases)) }
            install?.let {
                item {
                    OperationProgress(it, onShowInstallDialog = onShowInstallDialog, onCancelInstall = onCancelInstall)
                }
            }
            if (!loading && activeRepository != null && releases.isEmpty()) item {
                EmptyState(
                    icon = Icons.Outlined.Info,
                    title = stringResource(R.string.github_no_releases)
                )
            }
            if (releases.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = activeRepository?.displayName
                            ?: stringResource(R.string.github_releases_section)
                    )
                }
                // The last repository opened kept all thirty of its releases here, above the
                // catalogue - which "All" on Home now leads to - so the catalogue and its filters
                // sat thirty cards down. The newest few are what is usually wanted.
                items(
                    if (showAllReleases) releases else releases.take(RELEASES_SHOWN),
                    key = { it.id }
                ) { release ->
                    GitHubReleaseCard(release, onOpen)
                }
                if (releases.size > RELEASES_SHOWN) item {
                    TextButton(onClick = { showAllReleases = !showAllReleases }) {
                        Text(
                            if (showAllReleases) stringResource(R.string.github_hide_releases)
                            else stringResource(R.string.github_all_releases, releases.size)
                        )
                    }
                }
            }

            // The catalogue is browsable without typing a link, and the same entries are what
            // search matches against.
            if (catalog.isNotEmpty()) {
                item { SectionHeader(title = stringResource(R.string.github_catalog_section)) }
                // Sixty entries in one column is a list nobody reads to the end; the sections the
                // entries actually use are offered as filters, most used first.
                val sections = catalog.flatMap { it.categories }
                    .groupingBy { it }.eachCount()
                    .entries.sortedByDescending { it.value }
                    .map { it.key }
                if (sections.size > 1) item {
                    LazyRow(
                        modifier = Modifier.bleedToScreenEdges(),
                        contentPadding = PaddingValues(horizontal = ScreenPadding),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = section == null,
                                onClick = { section = null },
                                label = { Text(stringResource(R.string.library_filter_all)) }
                            )
                        }
                        items(sections, key = { it }) { slug ->
                            FilterChip(
                                selected = section == slug,
                                onClick = { section = if (section == slug) null else slug },
                                label = {
                                    Text(
                                        CategoryLabels.stringRes(slug)?.let { stringResource(it) }
                                            ?: CategoryLabels.humanize(slug)
                                    )
                                }
                            )
                        }
                    }
                }
                val shown = catalog.filter { section == null || section in it.categories }
                items(shown, key = { "catalog:${it.slug}" }) { entry ->
                    WyCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onOpenCatalogEntry(entry) }
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(model = entry.iconUrl, contentDescription = null, size = 48.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        entry.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (entry.isInstalled(installedPackages, managed)) {
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            stringResource(R.string.github_fact_installed),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1
                                        )
                                    }
                                }
                                Text(
                                    entry.repository.displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val summary = entry.summaryIn(LocalConfiguration.current.locales[0].language)
                                if (summary.isNotBlank()) {
                                    Text(
                                        summary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // "Built-in" stood on nearly every entry once the catalogue
                                    // grew, so it told nothing apart; the sections do.
                                    entry.categories.forEach { CategoryPill(it) }
                                }
                            }
                        }
                    }
                }
            }

        }
    }
}

@Composable
fun GitHubReleaseCard(release: GitHubRelease, onOpen: (GitHubRelease) -> Unit) {
    // Desktop-only releases stay in the list, so the version seen on GitHub does not look lost,
    // but they are muted: there is nothing in them to install on this phone.
    val installable = release.assets.isNotEmpty()
    WyCard(modifier = Modifier.fillMaxWidth(), onClick = { onOpen(release) }) {
        Column(
            Modifier.padding(14.dp).alpha(if (installable) 1f else MUTED_ALPHA),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    release.title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (release.prerelease) CategoryPill("pre-release")
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    release.tagName,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (installable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                release.publishedAt?.substringBefore("T")?.let {
                    Text(
                        stringResource(R.string.github_published_at, it),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            val summary = remember(release.description) { ReleaseNotes.plainText(release.description) }
            Text(
                summary.ifBlank { stringResource(R.string.github_no_description) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (installable) stringResource(R.string.github_apk_count, release.assets.size)
                else stringResource(R.string.github_no_android_build),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** How far a release with nothing to install on Android recedes from the ones that have an APK. */
internal const val MUTED_ALPHA = 0.55f

/** Releases of the open repository listed before "All releases". */
private const val RELEASES_SHOWN = 3
