package dev.wystore.ui.tv

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import dev.wystore.R
import dev.wystore.data.GitHubAsset
import dev.wystore.data.GitHubCatalogEntry
import dev.wystore.data.GitHubInstallState
import dev.wystore.data.GitHubInstallStatePolicy
import dev.wystore.data.InstalledApp
import dev.wystore.ui.components.AppIcon
import dev.wystore.ui.components.formatSize
import dev.wystore.ui.github.GitHubAppUiState
import androidx.compose.material3.MaterialTheme as M3Theme

/** Prefix that tells a GitHub entry apart from a package name where the two share a field. */
const val TV_GITHUB_KEY_PREFIX = "github:"

fun GitHubCatalogEntry.tvKey(): String = TV_GITHUB_KEY_PREFIX + slug

/** A GitHub project in a row, laid out as [TvAppCard] is so the two sources sit side by side. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvGitHubCard(
    entry: GitHubCatalogEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = M3Theme.shapes.large
    Card(
        onClick = onClick,
        modifier = modifier
            .width(TvCardWidth)
            .tvPointerClick(onClick = onClick)
            .semantics { contentDescription = entry.title },
        shape = CardDefaults.shape(shape),
        scale = CardDefaults.scale(focusedScale = 1.08f),
        border = CardDefaults.border(focusedBorder = tvFocusBorder(shape)),
        colors = CardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppIcon(model = entry.iconUrl, contentDescription = null, size = 52.dp)
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            TvBadge(GITHUB_LABEL)
        }
    }
}

/**
 * A GitHub project's page on a TV: the phone's GitHub page, laid out as the TV app page is.
 *
 * The one step that fits the device's state - install, update, open - holds the focus, the facts
 * sit in a card beside it, and the description and release notes are below, each reachable by
 * pressing down.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvGitHubAppScreen(
    state: GitHubAppUiState,
    installedApp: InstalledApp?,
    onInstallAsset: (GitHubAsset) -> Unit,
    onOpenInstalled: (String) -> Unit,
    onUninstall: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val entry = state.entry ?: return
    val context = LocalContext.current
    val deviceAbis = remember { android.os.Build.SUPPORTED_ABIS.toList() }
    val recommended = state.recommendedAsset(deviceAbis)
    val installState = GitHubInstallStatePolicy.stateFor(
        installedVersionName = installedApp?.versionName,
        releaseTag = state.latestRelease?.tagName
    )
    val ownedByUs = installedApp?.installerPackageName == context.packageName
    val primaryFocus = remember { FocusRequester() }
    val hasButton = recommended != null || installedApp != null
    LaunchedEffect(entry.slug, hasButton, state.loading) {
        runCatching { primaryFocus.requestFocus() }
    }

    // Pages keep their header in place; see TvMinimalBringIntoViewSpec.
    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides TvMinimalBringIntoViewSpec
    ) {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = TvOverscanHorizontal,
                end = TvOverscanHorizontal,
                top = 8.dp,
                bottom = TvOverscanVertical + 48.dp
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item(key = "header") {
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(model = state.info?.avatarUrl ?: entry.iconUrl, contentDescription = null, size = 112.dp)
                            Column(Modifier.padding(start = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    entry.title,
                                    style = MaterialTheme.typography.headlineMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    entry.repository.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TvBadge(GITHUB_LABEL)
                                    state.info?.license?.let { TvBadge(it) }
                                }
                            }
                        }
                        when {
                            state.loading -> Text(
                                stringResource(R.string.github_loading),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            state.error != null -> Text(
                                stringResource(R.string.github_load_failed),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            recommended == null && installedApp == null -> Text(
                                stringResource(R.string.github_no_apk),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            GitHubActions(
                                installState = installState,
                                recommended = recommended,
                                installedApp = installedApp,
                                ownedByUs = ownedByUs,
                                primaryFocus = primaryFocus,
                                onInstallAsset = onInstallAsset,
                                onOpenInstalled = onOpenInstalled,
                                onUninstall = onUninstall,
                                onRetry = onRetry.takeIf { state.error != null }
                            )
                        }
                        if (installState == GitHubInstallState.InstalledNewer) {
                            Text(
                                stringResource(R.string.github_installed_newer, installedApp?.versionName.orEmpty()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = M3Theme.shapes.large,
                        colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            val unknown = stringResource(R.string.github_fact_unknown)
                            TvGitHubFact(stringResource(R.string.github_fact_version), state.latestRelease?.tagName ?: unknown)
                            TvGitHubFact(stringResource(R.string.github_fact_published), state.latestRelease?.publishedAt?.take(10) ?: unknown)
                            // The size of the build the button will actually fetch.
                            TvGitHubFact(stringResource(R.string.github_fact_size), recommended?.let { formatSize(it.sizeBytes) } ?: unknown)
                            state.info?.stars?.let { TvGitHubFact(stringResource(R.string.github_fact_stars), it.toString()) }
                            installedApp?.let { TvGitHubFact(stringResource(R.string.github_fact_installed), it.versionName) }
                        }
                    }
                }
            }

            val description = state.info?.description?.takeIf { it.isNotBlank() } ?: entry.summary.takeIf { it.isNotBlank() }
            if (description != null) {
                item(key = "description") {
                    TvSectionTitle(stringResource(R.string.github_about_section))
                    TvTextCard(description, maxLines = 10)
                }
            }

            val screenshots = state.info?.screenshots.orEmpty()
            if (screenshots.isNotEmpty()) {
                item(key = "screenshots") {
                    TvSectionTitle(stringResource(R.string.github_screenshots))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        contentPadding = PaddingValues(12.dp),
                        modifier = Modifier.height(300.dp)
                    ) {
                        items(screenshots, key = { it }) { url ->
                            val shape = M3Theme.shapes.large
                            // Focusable only so pressing right scrolls the row; there is nothing to open.
                            Card(
                                onClick = {},
                                modifier = Modifier.height(260.dp),
                                shape = CardDefaults.shape(shape),
                                scale = CardDefaults.scale(focusedScale = 1.05f),
                                border = CardDefaults.border(focusedBorder = tvFocusBorder(shape))
                            ) {
                                AsyncImage(
                                    model = url,
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.height(260.dp).clip(shape)
                                )
                            }
                        }
                    }
                }
            }

            state.latestRelease?.description?.takeIf { it.isNotBlank() }?.let { notes ->
                item(key = "notes") {
                    TvSectionTitle(stringResource(R.string.github_changelog_section))
                    TvTextCard(notes.take(MAX_NOTES), maxLines = 14)
                }
            }

            item(key = "disclaimer") {
                Text(
                    stringResource(R.string.disclaimer_source_github),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** The page's buttons: the same choices the phone page offers for each install state. */
@Composable
private fun GitHubActions(
    installState: GitHubInstallState,
    recommended: GitHubAsset?,
    installedApp: InstalledApp?,
    ownedByUs: Boolean,
    primaryFocus: FocusRequester,
    onInstallAsset: (GitHubAsset) -> Unit,
    onOpenInstalled: (String) -> Unit,
    onUninstall: (String) -> Unit,
    onRetry: (() -> Unit)?
) {
    val focus = Modifier.focusRequester(primaryFocus)
    if (onRetry != null && recommended == null) {
        TvPrimaryButton(stringResource(R.string.common_retry), onRetry, focus)
        return
    }
    val open = installedApp?.let { app -> { onOpenInstalled(app.packageName) } }
    when (installState) {
        GitHubInstallState.NotInstalled -> recommended?.let {
            TvPrimaryButton(stringResource(R.string.github_install_primary), { onInstallAsset(it) }, focus)
        }
        GitHubInstallState.UpdateAvailable -> {
            recommended?.let { TvPrimaryButton(stringResource(R.string.github_update_primary), { onInstallAsset(it) }, focus) }
            open?.let {
                TvSecondaryButton(
                    stringResource(R.string.common_open),
                    it,
                    if (recommended == null) focus else Modifier
                )
            }
        }
        GitHubInstallState.InstalledNewer -> open?.let { TvPrimaryButton(stringResource(R.string.common_open), it, focus) }
        GitHubInstallState.Current, GitHubInstallState.Unknown -> {
            open?.let { TvPrimaryButton(stringResource(R.string.common_open), it, focus) }
            // Still reachable for a broken build; when another installer owns the app it is also
            // the only way to hand its updates to Wy Store, and says so.
            recommended?.let {
                TvSecondaryButton(
                    stringResource(if (ownedByUs) R.string.github_reinstall else R.string.details_take_over),
                    { onInstallAsset(it) },
                    if (open == null) focus else Modifier
                )
            }
        }
    }
    installedApp?.let { app ->
        TvSecondaryButton(stringResource(R.string.details_uninstall), { onUninstall(app.packageName) })
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvTextCard(text: String, maxLines: Int) {
    // Focusable so pressing down reaches it and brings it into view.
    Surface(
        modifier = Modifier.fillMaxWidth().focusable(),
        shape = M3Theme.shapes.large,
        colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, maxLines = maxLines, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(24.dp))
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvGitHubFact(label: String, value: String) {
    if (value.isBlank()) return
    Row {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(160.dp)
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** The source's own name, the same in every language, as the phone's badge shows it. */
private const val GITHUB_LABEL = "GitHub"

private const val MAX_NOTES = 2_000
