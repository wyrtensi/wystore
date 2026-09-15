package dev.wystore.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.wystore.InstallQueueStatus
import dev.wystore.R
import dev.wystore.data.ManagedApp
import dev.wystore.localization.StatusTextResolver
import dev.wystore.ui.components.AppIcon
import dev.wystore.ui.components.PackageUiState
import dev.wystore.ui.components.PackageUiStateReducer
import dev.wystore.ui.components.PrimaryAction

/**
 * Updates, the queue and the managed apps on one screen.
 *
 * The phone splits them across two tabs; on a remote every tab is more presses, and on a TV this
 * screen is also where anything waiting is found, since notifications are not shown. What needs
 * doing is at the top, the list of apps below.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvMyAppsScreen(
    packages: TvPackageContext,
    packageIcons: Map<String, String>,
    checking: Boolean,
    firstFocus: FocusRequester,
    /** False while the focus is up in the menu: arriving here must not pull it down. */
    takeFocus: Boolean,
    actions: TvPackageActions,
    onCheckUpdates: () -> Unit,
    onUpdateAll: () -> Unit,
    onCancel: (String) -> Unit,
    onOpenManaged: (ManagedApp) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val active = remember(packages) { packages.activeQueue }
    val pendingOnly = remember(packages, active) {
        packages.pendingUpdates.filter { pending -> active.none { it.packageName == pending.packageName } }
    }
    LaunchedEffect(Unit) { if (takeFocus) runCatching { firstFocus.requestFocus() } }

    val listState = rememberLazyListState()
    // Back from inside the screen sends the focus up to the menu; the screen goes back to its top
    // with it, so the menu is not left above a list scrolled halfway down.
    LaunchedEffect(takeFocus) { if (!takeFocus) listState.animateScrollToItem(0) }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = TvOverscanHorizontal,
            end = TvOverscanHorizontal,
            top = 8.dp,
            bottom = TvOverscanVertical + 48.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "header") {
            Row(
                Modifier.padding(top = 4.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                TvPrimaryButton(
                    text = stringResource(if (checking) R.string.status_checking else R.string.updates_check_now),
                    onClick = onCheckUpdates,
                    enabled = !checking,
                    modifier = Modifier.focusRequester(firstFocus)
                )
                if (active.isNotEmpty() || pendingOnly.isNotEmpty()) {
                    TvSecondaryButton(text = stringResource(R.string.home_update_all), onClick = onUpdateAll)
                }
            }
        }

        if (active.isNotEmpty() || pendingOnly.isNotEmpty()) {
            item(key = "ready-title") { TvSectionTitle(stringResource(R.string.tv_row_ready)) }
            items(active, key = { "q-${it.id}" }) { item ->
                val state = PackageUiStateReducer.reduce(
                    queueItem = item,
                    installed = packages.installed.firstOrNull { it.packageName == item.packageName },
                    managed = packages.managed.firstOrNull { it.packageName == item.packageName },
                    pendingUpdate = packages.pendingUpdates.firstOrNull { it.packageName == item.packageName },
                    resources = context.resources
                )
                TvPackageRow(
                    state = state,
                    iconUrl = packageIcons[item.packageName],
                    actions = actions,
                    onCancel = { onCancel(item.id) }.takeIf { item.status != InstallQueueStatus.INSTALLING }
                )
            }
            items(pendingOnly, key = { "p-${it.packageName}" }) { pending ->
                val state = PackageUiStateReducer.reduce(
                    pendingUpdate = pending,
                    installed = packages.installed.firstOrNull { it.packageName == pending.packageName },
                    managed = packages.managed.firstOrNull { it.packageName == pending.packageName },
                    resources = context.resources
                )
                TvPackageRow(state = state, iconUrl = packageIcons[pending.packageName], actions = actions, onCancel = null)
            }
        }

        item(key = "managed-title") { TvSectionTitle(stringResource(R.string.tv_my_apps_managed)) }
        if (packages.managed.isEmpty()) {
            item(key = "managed-empty") { TvEmptyState(stringResource(R.string.tv_my_apps_empty)) }
        } else {
            items(packages.managed.sortedBy { it.label.lowercase() }, key = { "m-${it.packageName}" }) { app ->
                val installed = packages.installed.firstOrNull { it.packageName == app.packageName }
                Surface(
                    onClick = { onOpenManaged(app) },
                    modifier = Modifier.fillMaxWidth().tvPointerClick { onOpenManaged(app) },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                    border = ClickableSurfaceDefaults.border(focusedBorder = tvFocusBorder()),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(
                            model = packageIcons[app.packageName],
                            contentDescription = null,
                            size = 48.dp,
                            fallbackPackageName = app.packageName
                        )
                        Column(Modifier.padding(start = 20.dp)) {
                            Text(app.label.ifBlank { app.packageName }, style = MaterialTheme.typography.titleMedium)
                            Text(
                                installed?.versionName ?: stringResource(R.string.details_not_installed),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One package with work in progress: the step it needs as its one main button, and a way out. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvPackageRow(
    state: PackageUiState,
    iconUrl: String?,
    actions: TvPackageActions,
    onCancel: (() -> Unit)?
) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(model = iconUrl, contentDescription = null, size = 56.dp, fallbackPackageName = state.packageName)
        Column(Modifier.weight(1f).padding(horizontal = 20.dp)) {
            Text(
                state.label,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOfNotNull(StatusTextResolver.resolve(context, state.status), state.transferInfo).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        tvPrimaryLabel(state.primaryAction)?.let { label ->
            TvPrimaryButton(
                text = label,
                onClick = { actions.perform(state) },
                enabled = state.primaryAction != PrimaryAction.Installing
            )
        }
        if (onCancel != null) {
            TvSecondaryButton(
                text = stringResource(R.string.common_cancel),
                onClick = onCancel,
                modifier = Modifier.padding(start = 16.dp)
            )
        }
    }
}
