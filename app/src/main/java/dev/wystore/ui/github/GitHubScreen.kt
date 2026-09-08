package dev.wystore.ui.github

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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
    onRemove: (GitHubRepository) -> Unit
) {
    var input by remember(activeRepository) { mutableStateOf(activeRepository?.url.orEmpty()) }
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
            // The catalogue is browsable without typing a link, and the same entries are what
            // search matches against.
            if (catalog.isNotEmpty()) {
                item { SectionHeader(title = stringResource(R.string.github_catalog_section)) }
                items(catalog, key = { "catalog:${it.slug}" }) { entry ->
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
                                Text(entry.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    entry.repository.displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (entry.summary.isNotBlank()) {
                                    Text(
                                        entry.summary,
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
                                    if (entry.curated) CategoryPill(stringResource(R.string.github_curated_badge))
                                    entry.categories.forEach { CategoryPill(it) }
                                }
                            }
                        }
                    }
                }
            }

            if (loading) item { Loading(stringResource(R.string.github_loading_releases)) }
            install?.let { item { OperationProgress(it) } }
            if (!loading && activeRepository != null && releases.isEmpty()) item {
                EmptyState(
                    icon = Icons.Outlined.Info,
                    title = stringResource(R.string.github_no_releases)
                )
            }
            items(releases, key = { it.id }) { release ->
                GitHubReleaseCard(release, onOpen)
            }
        }
    }
}

@Composable
fun GitHubReleaseCard(release: GitHubRelease, onOpen: (GitHubRelease) -> Unit) {
    WyCard(modifier = Modifier.fillMaxWidth(), onClick = { onOpen(release) }) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    color = MaterialTheme.colorScheme.primary
                )
                release.publishedAt?.substringBefore("T")?.let {
                    Text(
                        stringResource(R.string.github_published_at, it),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                release.description.ifBlank { stringResource(R.string.github_no_description) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                stringResource(R.string.github_apk_count, release.assets.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
