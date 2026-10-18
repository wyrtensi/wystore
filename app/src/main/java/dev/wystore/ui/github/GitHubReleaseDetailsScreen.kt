package dev.wystore.ui.github

import dev.wystore.ui.components.fadingTopEdge
import dev.wystore.ui.components.HubPageHeader
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.wystore.InstallQueueItem
import dev.wystore.R
import dev.wystore.occupiesQueue
import dev.wystore.ui.components.LocalBottomBarInset
import dev.wystore.data.GitHubAsset
import dev.wystore.data.GitHubRelease
import dev.wystore.ui.components.EmptyState
import dev.wystore.ui.components.OperationProgress
import dev.wystore.ui.components.ScreenPadding
import dev.wystore.ui.components.SectionHeader
import dev.wystore.ui.components.WyCard
import dev.wystore.ui.components.formatSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubReleaseDetailsScreen(
    release: GitHubRelease,
    install: InstallQueueItem?,
    onBack: () -> Unit,
    onInstall: (GitHubAsset) -> Unit,
    /** For an install waiting on Android's dialog off screen, by queue row. */
    onShowInstallDialog: (String) -> Unit = {},
    onCancelInstall: (String) -> Unit = {}
) {
    Scaffold(topBar = {
        // The concept's page header: a round back button, where the page belongs as the
        // accent line, the title in plain weight.
        HubPageHeader(
            title = stringResource(R.string.github_release_title),
            eyebrow = "GitHub",
            onBack = onBack
        )
    }) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding()).fadingTopEdge(),
            contentPadding = PaddingValues(
                start = ScreenPadding,
                end = ScreenPadding,
                top = 20.dp,
                bottom = 24.dp + LocalBottomBarInset.current
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(release.title, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        release.tagName,
                        style = MaterialTheme.typography.titleSmall,
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
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(title = stringResource(R.string.github_release_description))
                    ReleaseNotesText(
                        release.description,
                        empty = stringResource(R.string.github_no_description_dev),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            item { SectionHeader(title = stringResource(R.string.github_apk_to_install)) }
            // The row's package name is a placeholder until the APK is verified, so it cannot be
            // matched to one file; it is the repository's install, shown once above the files.
            install?.let {
                item {
                    OperationProgress(it, onShowInstallDialog = onShowInstallDialog, onCancelInstall = onCancelInstall)
                }
            }
            items(release.assets, key = { it.id }) { asset ->
                WyCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(asset.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            formatSize(asset.sizeBytes),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        asset.digest?.takeIf { it.startsWith("sha256:") }?.let {
                            Text(
                                stringResource(R.string.github_sha256_published),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = { onInstall(asset) },
                            enabled = install?.status?.occupiesQueue != true,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.github_install_apk))
                        }
                    }
                }
            }
            if (release.assets.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.Warning,
                        title = stringResource(R.string.github_release_no_apk)
                    )
                }
            }
        }
    }
}
