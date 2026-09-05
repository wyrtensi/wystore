package dev.wystore.ui.github

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
    onInstall: (GitHubAsset) -> Unit
) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.github_release_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.common_back)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )
    }) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            contentPadding = PaddingValues(
                start = ScreenPadding,
                end = ScreenPadding,
                top = 4.dp,
                bottom = 24.dp
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
                    Text(
                        release.description.ifBlank { stringResource(R.string.github_no_description_dev) },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            item { SectionHeader(title = stringResource(R.string.github_apk_to_install)) }
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
                        if (install?.packageName == asset.id.toString()) {
                            OperationProgress(install)
                        } else {
                            Button(onClick = { onInstall(asset) }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.github_install_apk))
                            }
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
