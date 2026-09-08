package dev.wystore.ui.catalog

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.wystore.data.InstalledApp
import dev.wystore.data.ManagedApp
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
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onAppClick: (String) -> Unit = {},
    onPreviousPage: () -> Unit = {},
    onNextPage: () -> Unit = {},
    onRetry: () -> Unit = {},
    onInstall: (String) -> Unit = {},
    onInstallDownloaded: (String) -> Unit = {},
    onLaunch: (String) -> Unit = {}
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.title) },
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
        }
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding()),
            contentPadding = PaddingValues(
                start = ScreenPadding,
                end = ScreenPadding,
                top = 4.dp,
                bottom = 24.dp + LocalBottomBarInset.current
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.stale) {
                item {
                    Text(
                        stringResource(R.string.catalog_stale),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(state.apps, key = { it.packageName }) { app ->
                val uiState = PackageUiStateReducer.reduce(
                    app = app,
                    installed = installed.firstOrNull { it.packageName == app.packageName },
                    managed = managed.firstOrNull { it.packageName == app.packageName },
                    resources = LocalResources.current
                )
                AppRow(
                    state = uiState,
                    onRowClick = { onAppClick(app.packageName) },
                    onPrimaryActionClick = {
                        when (RowActionPolicy.actionFor(uiState.primaryAction, uiState.status.code)) {
                            RowAction.Enqueue -> onInstall(app.packageName)
                            RowAction.InstallDownloaded -> onInstallDownloaded(app.packageName)
                            RowAction.OpenApp -> onLaunch(app.packageName)
                            RowAction.Nothing -> Unit
                        }
                    }
                )
            }

            item {
                when {
                    state.loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
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
                        OutlinedButton(onClick = onPreviousPage, enabled = state.canGoBack) { Text(stringResource(R.string.common_back)) }
                        Text(
                            state.lastPage?.let { stringResource(R.string.catalog_page_of, state.page, it) } ?: stringResource(R.string.catalog_page, state.page),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(onClick = onNextPage, enabled = state.canGoForward) { Text(stringResource(R.string.common_next)) }
                    }
                }
            }
        }
    }
}
