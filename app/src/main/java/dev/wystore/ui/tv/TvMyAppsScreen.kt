package dev.wystore.ui.tv

import dev.wystore.data.ManagedSource
import dev.wystore.ui.components.sourceTint
import dev.wystore.ui.components.groupPosition
import dev.wystore.ui.components.HubTag
import dev.wystore.ui.components.HubGroupRow
import androidx.compose.foundation.lazy.itemsIndexed
import dev.wystore.ui.components.color
import dev.wystore.ui.components.fadingTopEdge
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
import dev.wystore.data.GitHubRepository
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
    modifier: Modifier = Modifier,
    /**
     * The source's notes for a waiting update: package, name and the repository a GitHub row
     * names. A row here had no way to its page, and so none to what the update brings.
     */
    onWhatsNew: (String, String, GitHubRepository?) -> Unit = { _, _, _ -> }
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
        // Rows scrolled up under the menu dissolve instead of ending on a hard line across a card.
            modifier = modifier.fillMaxSize().fadingTopEdge(TvTopFade),
        contentPadding = PaddingValues(
            start = TvOverscanHorizontal,
            end = TvOverscanHorizontal,
            top = TvTopFade,
            bottom = TvOverscanVertical + 48.dp
        ),
        // Rows of a group are separate items and must touch; sections keep their gap through
        // the titles' own padding.
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // The phone's opening panel: how things stand in a sentence, and the step to take. Two bare
        // buttons over a list was all the TV had.
        item(key = "header") {
            val ready = active.size + pendingOnly.size
            TvGlassPanel(modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
                Text(
                    if (ready > 0) stringResource(R.string.updates_hero_ready, ready)
                    else stringResource(R.string.home_all_up_to_date),
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    stringResource(R.string.home_background_checks),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    if (ready > 0) {
                        TvPrimaryButton(
                            text = stringResource(R.string.home_update_all),
                            onClick = onUpdateAll,
                            modifier = Modifier.focusRequester(firstFocus)
                        )
                        TvSecondaryButton(
                            text = stringResource(if (checking) R.string.status_checking else R.string.updates_check_now),
                            onClick = onCheckUpdates,
                            enabled = !checking
                        )
                    } else {
                        TvPrimaryButton(
                            text = stringResource(if (checking) R.string.status_checking else R.string.updates_check_now),
                            onClick = onCheckUpdates,
                            enabled = !checking,
                            modifier = Modifier.focusRequester(firstFocus)
                        )
                    }
                }
            }
        }

        if (active.isNotEmpty() || pendingOnly.isNotEmpty()) {
            item(key = "ready-title") { TvSectionTitle(stringResource(R.string.tv_row_ready), Modifier.padding(top = 20.dp)) }
            val readyCount = active.size + pendingOnly.size
            itemsIndexed(active, key = { _, it -> "q-${it.id}" }) { index, item ->
                val state = PackageUiStateReducer.reduce(
                    queueItem = item,
                    installed = packages.installed.firstOrNull { it.packageName == item.packageName },
                    managed = packages.managed.firstOrNull { it.packageName == item.packageName },
                    pendingUpdate = packages.pendingUpdates.firstOrNull { it.packageName == item.packageName },
                    resources = context.resources
                )
                HubGroupRow(position = groupPosition(index, readyCount)) {
                TvPackageRow(
                    state = state,
                    iconUrl = packageIcons[item.packageName],
                    actions = actions,
                    onCancel = { onCancel(item.id) }.takeIf { item.status != InstallQueueStatus.INSTALLING },
                    onWhatsNew = { onWhatsNew(item.packageName, item.label, item.githubRepository) }
                )
                }
            }
            itemsIndexed(pendingOnly, key = { _, it -> "p-${it.listKey}" }) { index, pending ->
                val state = PackageUiStateReducer.reduce(
                    pendingUpdate = pending,
                    installed = packages.installed.firstOrNull { it.packageName == pending.packageName },
                    managed = packages.managed.firstOrNull { it.packageName == pending.packageName },
                    resources = context.resources
                )
                HubGroupRow(position = groupPosition(active.size + index, readyCount)) {
                TvPackageRow(
                    state = state,
                    iconUrl = packageIcons[pending.packageName],
                    actions = actions,
                    onCancel = null,
                    onWhatsNew = { onWhatsNew(pending.packageName, pending.label, pending.githubRepository) }
                )
                }
            }
        }

        item(key = "managed-title") { TvSectionTitle(stringResource(R.string.tv_my_apps_managed), Modifier.padding(top = 28.dp)) }
        if (packages.managed.isEmpty()) {
            item(key = "managed-empty") { TvEmptyState(stringResource(R.string.tv_my_apps_empty)) }
        } else {
            val sorted = packages.managed.sortedBy { it.label.lowercase() }
            // One panel of rows, as the phone lists them; a card per app made a column of slabs.
            itemsIndexed(sorted, key = { _, app -> "m-${app.packageName}" }) { index, app ->
                val installed = packages.installed.firstOrNull { it.packageName == app.packageName }
                TvGroupRow(position = groupPosition(index, sorted.size), onClick = { onOpenManaged(app) }) {
                    AppIcon(
                        model = packageIcons[app.packageName],
                        contentDescription = null,
                        size = 48.dp,
                        fallbackPackageName = app.packageName
                    )
                    Column(Modifier.weight(1f).padding(start = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            app.label.ifBlank { app.packageName },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HubTag(installed?.versionName ?: stringResource(R.string.details_not_installed))
                            val source = if (app.source == ManagedSource.GITHUB) "GitHub" else "RuStore"
                            HubTag(source, tint = sourceTint(source))
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
    onCancel: (() -> Unit)?,
    onWhatsNew: (() -> Unit)? = null
) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 14.dp),
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
                // In the phone's tones: ready green, paused yellow, failed red, the rest grey.
                color = dev.wystore.ui.components.StatusTones.of(state.status.code).color()
                    ?: MaterialTheme.colorScheme.onSurfaceVariant,
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
        onWhatsNew?.let { showNotes ->
            TvSecondaryButton(
                text = stringResource(R.string.details_changelog),
                onClick = showNotes,
                modifier = Modifier.padding(start = 16.dp)
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
