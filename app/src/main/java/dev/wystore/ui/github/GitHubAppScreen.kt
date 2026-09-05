package dev.wystore.ui.github

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.wystore.R
import dev.wystore.data.GitHubAsset
import dev.wystore.data.GitHubCatalogEntry
import dev.wystore.data.GitHubRelease
import dev.wystore.data.GitHubReleasePolicy
import dev.wystore.data.GitHubRepositoryInfo
import dev.wystore.data.InstalledApp
import dev.wystore.ui.components.AppIcon
import dev.wystore.ui.components.CategoryPill
import dev.wystore.ui.components.EmptyState
import dev.wystore.ui.components.ScreenPadding
import dev.wystore.ui.components.SectionHeader
import dev.wystore.ui.components.shareLink
import dev.wystore.ui.components.SourceDisclaimer
import dev.wystore.ui.components.WyCard
import dev.wystore.ui.components.WyDivider
import dev.wystore.ui.components.formatSize

data class GitHubAppUiState(
    val entry: GitHubCatalogEntry? = null,
    val info: GitHubRepositoryInfo? = null,
    val releases: List<GitHubRelease> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
) {
    private val assetPattern: Regex? get() = entry?.assetPattern()

    /** Newest release that can actually be installed; see [GitHubReleasePolicy]. */
    val latestRelease: GitHubRelease?
        get() = GitHubReleasePolicy.selectRelease(releases, assetPattern)

    val apkAssets: List<GitHubAsset>
        get() = latestRelease?.let { GitHubReleasePolicy.apkAssets(it, assetPattern) }.orEmpty()

    /**
     * The build this device should get. A GitHub app installs with one tap like a RuStore app;
     * picking between per-ABI builds is a deliberate detour, not the default.
     */
    fun recommendedAsset(supportedAbis: List<String>): GitHubAsset? =
        GitHubReleasePolicy.preferredAsset(apkAssets, supportedAbis)
}

/**
 * A GitHub repository shown the way a store listing is: icon, title, publisher, facts row,
 * description, release notes and an install action — rather than a raw list of release tags.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GitHubAppScreen(
    state: GitHubAppUiState,
    installedApp: InstalledApp? = null,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onInstallAsset: (GitHubAsset) -> Unit = {},
    onOpenInstalled: (String) -> Unit = {},
    onRetry: () -> Unit = {},
    onOpenAllReleases: () -> Unit = {}
) {
    val entry = state.entry
    var showAllAssets by rememberSaveable(entry?.slug) { mutableStateOf(false) }
    val deviceAbis = remember { android.os.Build.SUPPORTED_ABIS.toList() }
    val context = LocalContext.current
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(entry?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                actions = {
                    // The same affordance as a RuStore page: both sources behave alike.
                    entry?.let { catalogEntry ->
                        val subject = stringResource(R.string.github_share_subject, catalogEntry.title)
                        val chooserTitle = stringResource(R.string.details_share_chooser)
                        IconButton(
                            onClick = {
                                shareLink(
                                    context = context,
                                    subject = subject,
                                    url = "https://github.com/${catalogEntry.repository.owner}/${catalogEntry.repository.name}",
                                    chooserTitle = chooserTitle
                                )
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = stringResource(R.string.details_share)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentPadding = PaddingValues(
                start = ScreenPadding,
                end = ScreenPadding,
                top = 4.dp,
                bottom = 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (entry == null) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.Warning,
                        title = stringResource(R.string.github_app_missing)
                    )
                }
                return@LazyColumn
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(
                        model = state.info?.avatarUrl ?: entry.iconUrl,
                        contentDescription = null,
                        size = 72.dp
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            entry.title,
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            entry.repository.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CategoryPill("GitHub")
                    state.info?.license?.let { CategoryPill(it) }
                    entry.categories.forEach { CategoryPill(it) }
                }
            }

            // Facts row: the same "what am I about to install" summary the RuStore page shows.
            item {
                WyCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val release = state.latestRelease
                        GitHubFact(
                            stringResource(R.string.github_fact_version),
                            release?.tagName ?: stringResource(R.string.github_fact_unknown)
                        )
                        GitHubFact(
                            stringResource(R.string.github_fact_published),
                            release?.publishedAt?.take(10) ?: stringResource(R.string.github_fact_unknown)
                        )
                        // The size shown must be the build the Install button will actually fetch.
                        GitHubFact(
                            stringResource(R.string.github_fact_size),
                            state.recommendedAsset(deviceAbis)?.let { formatSize(it.sizeBytes) }
                                ?: stringResource(R.string.github_fact_unknown)
                        )
                        state.info?.stars?.let { GitHubFact(stringResource(R.string.github_fact_stars), it.toString()) }
                        installedApp?.let {
                            GitHubFact(stringResource(R.string.github_fact_installed), it.versionName)
                        }

                        if (state.info?.archived == true) {
                            Text(
                                stringResource(R.string.github_archived),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            when {
                state.loading -> item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.github_loading), style = MaterialTheme.typography.bodySmall)
                    }
                }
                state.error != null -> item {
                    EmptyState(
                        icon = Icons.Outlined.Warning,
                        title = stringResource(R.string.github_load_failed),
                        message = state.error,
                        actionLabel = stringResource(R.string.common_retry),
                        onActionClick = onRetry
                    )
                }
            }

            // One primary action, like a RuStore card. Choosing a specific build is a small
            // secondary control rather than a list of every asset in the release.
            val recommended = state.recommendedAsset(deviceAbis)
            if (recommended != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onInstallAsset(recommended) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (installedApp != null) stringResource(R.string.github_update_primary)
                                else stringResource(R.string.github_install_primary)
                            )
                        }
                        Text(
                            stringResource(
                                R.string.github_selected_asset,
                                recommended.name,
                                formatSize(recommended.sizeBytes)
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (state.apkAssets.size > 1) {
                            TextButton(
                                onClick = { showAllAssets = !showAllAssets },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    if (showAllAssets) stringResource(R.string.github_hide_versions)
                                    else stringResource(R.string.github_other_version),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }

                if (showAllAssets) {
                    items(state.apkAssets.filter { it.id != recommended.id }, key = { it.id }) { asset ->
                        WyCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(asset.name, style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        formatSize(asset.sizeBytes),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(onClick = { onInstallAsset(asset) }) {
                                    Text(stringResource(R.string.common_install))
                                }
                            }
                        }
                    }
                }
            } else if (!state.loading && state.error == null) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.Warning,
                        title = stringResource(R.string.github_no_apk)
                    )
                }
            }

            if (installedApp != null) {
                item {
                    OutlinedButton(
                        onClick = { onOpenInstalled(installedApp.packageName) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.common_open)) }
                }
            }

            val description = state.info?.description?.takeIf { it.isNotBlank() }
                ?: entry.summary.takeIf { it.isNotBlank() }
            if (description != null) {
                item { WyDivider() }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader(title = stringResource(R.string.github_about_section))
                        Text(description, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            val screenshots = state.info?.screenshots.orEmpty()
            if (screenshots.isNotEmpty()) {
                item { WyDivider() }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader(title = stringResource(R.string.github_screenshots))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(screenshots, key = { it }) { url ->
                                AsyncImage(
                                    model = url,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .height(320.dp)
                                        .clip(MaterialTheme.shapes.large)
                                )
                            }
                        }
                    }
                }
            }

            state.latestRelease?.description?.takeIf { it.isNotBlank() }?.let { notes ->
                item { WyDivider() }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionHeader(title = stringResource(R.string.github_changelog_section))
                        WyCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                notes.take(4000),
                                modifier = Modifier.padding(14.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            if (state.releases.size > 1) {
                item {
                    OutlinedButton(onClick = onOpenAllReleases, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.github_all_releases, state.releases.size))
                    }
                }
            }

            item { SourceDisclaimer(sourceLine = stringResource(R.string.disclaimer_source_github)) }
        }
    }
}

@Composable
private fun GitHubFact(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.End
        )
    }
}
