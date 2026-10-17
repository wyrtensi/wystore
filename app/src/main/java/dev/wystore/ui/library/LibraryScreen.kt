package dev.wystore.ui.library

import dev.wystore.rowFor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.annotation.StringRes
import dev.wystore.ui.components.WySpinner
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import dev.wystore.AdoptionBatch
import dev.wystore.InstallQueueItem
import dev.wystore.R
import dev.wystore.UpdateCheckTask
import dev.wystore.data.InstalledApp
import dev.wystore.data.ManagedApp
import dev.wystore.data.ManagedSource
import dev.wystore.data.PendingUpdate
import dev.wystore.ui.components.LocalBottomBarInset
import dev.wystore.ui.components.EmptyState
import dev.wystore.ui.components.ScreenPadding
import dev.wystore.ui.components.WyCard
import java.text.DateFormat
import java.util.Date

/**
 * Library filters. These used only to float matches to the top of the full installed list, which
 * with a couple of hundred apps meant picking "GitHub" still showed Google Play apps first. They
 * now narrow the list, with [ALL] as the unfiltered default.
 */
enum class LibrarySort(@StringRes val labelRes: Int) {
    ALL(R.string.library_filter_all),
    NOT_ADOPTED(R.string.library_filter_not_adopted),
    WY_STORE(R.string.library_sort_wystore),
    GITHUB(R.string.library_sort_github),
    RUSTORE(R.string.library_sort_rustore),
    AUTO_ON(R.string.library_sort_auto_on),
    AUTO_OFF(R.string.library_sort_auto_off)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    installed: List<InstalledApp>,
    managed: List<ManagedApp>,
    pendingUpdates: List<PendingUpdate>,
    updateCheckTask: UpdateCheckTask?,
    onCheckUpdates: () -> Unit,
    /** Whether a check will also fetch and install what it finds; see the Updates screen. */
    installsWhatItFinds: Boolean = false,
    onUpdateAll: () -> Unit,
    onAdopt: (InstalledApp) -> Unit,
    /** Several apps accepted at once; see [dev.wystore.StoreViewModel.adoptMany]. */
    onAdoptMany: (List<InstalledApp>) -> Unit = {},
    /** How far accepting several apps has got, while it runs. */
    adoptionBatch: AdoptionBatch? = null,
    /** Packages RuStore has said it does not carry. */
    missingFromSource: Set<String> = emptySet(),
    /** Starts the kept-current backup file; see [dev.wystore.data.BackupLocation]. */
    onExportBackup: (android.net.Uri) -> Unit = {},
    /** Restores a backup file, merged into what is there. */
    onImportBackup: (android.net.Uri) -> Unit = {},
    onUpdateManaged: (ManagedApp) -> Unit,
    onRequestForce: (ManagedApp) -> Unit,
    onRemoveManaged: (InstalledApp) -> Unit,
    onUninstall: (InstalledApp) -> Unit,
    onCheck: (ManagedApp) -> Unit,
    onOpenDetails: (ManagedApp) -> Unit,
    onOpenStorePage: (String) -> Unit,
    onLaunch: (String) -> Unit,
    onInstallPending: (String) -> Unit,
    /** The queue, so a row can say a check found something that is not downloaded yet. */
    queue: List<InstallQueueItem> = emptyList(),
    onDownloadUpdate: (String) -> Unit = {}
) {
    val managedByPackage = remember(managed) { managed.associateBy { it.packageName } }
    val pendingByPackage = remember(pendingUpdates) { pendingUpdates.associateBy { it.packageName } }
    val queueByPackage = remember(queue) { queue.map { it.packageName }.distinct().associateWith { queue.rowFor(it) } }
    var query by remember { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(LibrarySort.ALL) }
    // Which rows are open lives here rather than in the rows themselves. A row that is scrolled
    // out of a LazyColumn leaves the composition and restores its own saved value when it comes
    // back, so "collapse all" reached the handful of rows on screen and nothing else. The screen
    // holds the whole answer; the row only reports taps.
    var expandedPackages by rememberSaveable(
        stateSaver = listSaver<Set<String>, String>(
            save = { it.toList() },
            restore = { it.toSet() }
        )
    ) { mutableStateOf(emptySet<String>()) }
    val orderedInstalled = remember(installed, managedByPackage, sort) {
        val sourceOf: (InstalledApp) -> ManagedSource? = { managedByPackage[it.packageName]?.source }
        val selected: (InstalledApp) -> Boolean = { app ->
            val management = managedByPackage[app.packageName]
            when (sort) {
                LibrarySort.ALL -> true
                LibrarySort.NOT_ADOPTED -> management == null
                LibrarySort.WY_STORE -> management?.lastUpdatedAt != null
                LibrarySort.GITHUB -> management != null && sourceOf(app) == ManagedSource.GITHUB
                LibrarySort.RUSTORE -> management != null && sourceOf(app) == ManagedSource.RUSTORE
                LibrarySort.AUTO_ON -> management?.autoUpdate == true
                LibrarySort.AUTO_OFF -> management != null && !management.autoUpdate
            }
        }
        installed
            .filter(selected)
            .sortedWith(
                compareByDescending<InstalledApp> { managedByPackage[it.packageName]?.lastUpdatedAt ?: 0L }
                    .thenByDescending { managedByPackage.containsKey(it.packageName) }
                    .thenBy { it.label.lowercase() }
            )
    }
    val filteredInstalled = remember(orderedInstalled, query) {
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isEmpty()) orderedInstalled else orderedInstalled.filter {
            it.label.lowercase().contains(normalizedQuery) || it.packageName.lowercase().contains(normalizedQuery)
        }
    }
    val packageSetSaver = listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() })
    var collapsedGroups by rememberSaveable(stateSaver = packageSetSaver) { mutableStateOf(emptySet<String>()) }
    // Selecting is for accepting, so only apps that are not accepted yet can be picked.
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selectedPackages by rememberSaveable(stateSaver = packageSetSaver) { mutableStateOf(emptySet<String>()) }
    fun stopSelecting() {
        selecting = false
        selectedPackages = emptySet()
    }
    BackHandler(enabled = selecting) { stopSelecting() }
    val groups = remember(filteredInstalled, managedByPackage, sort, selecting) {
        LibraryGrouping.groups(filteredInstalled, managedByPackage.keys, sort, selecting)
    }
    // Rows hidden in a collapsed group are not on screen, so open-everything leaves them alone.
    val visibleInstalled = remember(groups, collapsedGroups) {
        groups.filterNot { it.key in collapsedGroups }.flatMap { it.apps }
    }
    // An app accepted, removed or filtered away since it was picked is no longer selected.
    val selectable = remember(groups) {
        groups.filter { it.key == LibraryGrouping.NOT_ADOPTED }.flatMap { it.apps }
    }
    val selectedApps = selectable.filter { it.packageName in selectedPackages }
    Scaffold(modifier = modifier, topBar = {
        if (selecting) {
            // The check button gives way rather than gaining a neighbour: two text buttons and a
            // title do not fit the compact phone at its font scale.
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = ::stopSelecting) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.common_cancel))
                    }
                },
                title = {
                    Text(
                        stringResource(R.string.library_selected_count, selectedApps.size),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
                    Button(
                        onClick = {
                            onAdoptMany(selectedApps)
                            stopSelecting()
                        },
                        enabled = selectedApps.isNotEmpty() && adoptionBatch == null,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(stringResource(R.string.library_adopt_selected, selectedApps.size), maxLines = 1)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        } else TopAppBar(
            title = {
                Text(
                    stringResource(R.string.library_title),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            actions = {
                // "Install (5)" used to run a check: the label counted downloads waiting and the
                // press went looking for more. Once something is downloaded, the button does what
                // it says instead.
                val installsWaiting = pendingUpdates.isNotEmpty()
                TextButton(
                    onClick = if (installsWaiting) onUpdateAll else onCheckUpdates,
                    enabled = installsWaiting || updateCheckTask?.running != true
                ) {
                    Text(
                        when {
                            installsWaiting -> stringResource(R.string.library_install_count, pendingUpdates.size)
                            updateCheckTask?.running == true -> stringResource(R.string.library_checking)
                            // The label used to promise an install unconditionally, on a phone
                            // where the switch for it may well be off.
                            installsWhatItFinds -> stringResource(R.string.library_check_and_install)
                            else -> stringResource(R.string.updates_check_now)
                        }
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )
    }) { contentPadding ->
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
            if (!selecting) item {
                Text(
                    stringResource(R.string.library_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!selecting && installed.isNotEmpty()) item {
                BackupNudge(hasAccepted = managed.isNotEmpty(), onExportUri = onExportBackup, onImportUri = onImportBackup)
            }
            updateCheckTask?.takeIf { it.active }?.let { task ->
                item { UpdateCheckStatusCard(task) }
            }
            adoptionBatch?.let { batch ->
                item { AdoptionBatchCard(batch) }
            }
            item {
                val allExpanded = visibleInstalled.isNotEmpty() &&
                    visibleInstalled.all { expandedPackages.contains(it.packageName) }
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.extraLarge),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.library_search_placeholder)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    // Open-everything rides in the search field rather than beside the filters,
                    // where it was a bare chevron the filter rail scrolled underneath. Here it
                    // lines up with the chevron on each row, which is what it operates, and costs
                    // the screen no height of its own. While something is typed the slot does the
                    // more obvious job and clears the query.
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.library_search_clear)
                                )
                            }
                        } else if (visibleInstalled.isNotEmpty() && !selecting) {
                            IconButton(
                                onClick = {
                                    expandedPackages = if (allExpanded) {
                                        emptySet()
                                    } else {
                                        visibleInstalled.map { it.packageName }.toSet()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (allExpanded) Icons.Outlined.KeyboardArrowUp
                                    else Icons.Outlined.KeyboardArrowDown,
                                    contentDescription = stringResource(
                                        if (allExpanded) R.string.library_collapse_all
                                        else R.string.library_expand_all
                                    )
                                )
                            }
                        }
                    },
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    )
                )
            }
            item {
                // The filter rail scrolls sideways instead of wrapping onto three lines and
                // pushing the list itself below the fold. It gets the whole width: sharing the row
                // with a button cut the last chip in half at that button's edge.
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(LibrarySort.entries, key = { it.name }) { option ->
                        FilterChip(
                            selected = sort == option,
                            onClick = { sort = option },
                            label = { Text(stringResource(option.labelRes)) }
                        )
                    }
                }
            }
            if (filteredInstalled.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.AutoMirrored.Outlined.List,
                        title = stringResource(R.string.library_empty_title),
                        message = stringResource(R.string.library_empty_message)
                    )
                }
            }
            groups.forEach { group ->
                val collapsed = group.key in collapsedGroups
                if (group.titled) item(key = "group:${group.key}") {
                    val notAdopted = group.key == LibraryGrouping.NOT_ADOPTED
                    val allSelected = notAdopted && group.apps.all { it.packageName in selectedPackages }
                    LibraryGroupHeader(
                        title = stringResource(
                            if (notAdopted) R.string.library_group_not_adopted else R.string.library_group_adopted,
                            group.apps.size
                        ),
                        collapsed = collapsed,
                        onToggle = {
                            collapsedGroups = if (collapsed) collapsedGroups - group.key else collapsedGroups + group.key
                        },
                        action = when {
                            !notAdopted || adoptionBatch != null -> null
                            !selecting -> stringResource(R.string.library_select) to {
                                selecting = true
                                collapsedGroups = collapsedGroups - group.key
                            }
                            allSelected -> stringResource(R.string.library_select_none) to { selectedPackages = emptySet() }
                            else -> stringResource(R.string.library_select_all) to {
                                selectedPackages = group.apps.mapTo(mutableSetOf()) { it.packageName }
                            }
                        }
                    )
                }
                if (!collapsed) items(group.apps, key = { it.packageName }) { app ->
                    val managedApp = managedByPackage[app.packageName]
                    val toggle = {
                        selectedPackages = if (app.packageName in selectedPackages) selectedPackages - app.packageName
                        else selectedPackages + app.packageName
                    }
                    LibraryAppRow(app, managedApp, pendingByPackage[app.packageName], queueByPackage[app.packageName], onAdopt, onUpdateManaged, onRequestForce, onRemoveManaged, onUninstall, onCheck, onOpenDetails, onOpenStorePage, onLaunch, onInstallPending, onDownloadUpdate,
                        expanded = !selecting && expandedPackages.contains(app.packageName),
                        onExpandedChange = { open ->
                            expandedPackages = if (open) expandedPackages + app.packageName
                            else expandedPackages - app.packageName
                        },
                        selected = if (selecting) app.packageName in selectedPackages else null,
                        missingFromSource = app.packageName in missingFromSource,
                        onToggleSelected = toggle,
                        // A long press on an app not accepted yet starts choosing several at once.
                        onStartSelection = if (managedApp == null && !selecting && adoptionBatch == null) {
                            {
                                selecting = true
                                selectedPackages = setOf(app.packageName)
                            }
                        } else null
                    )
                }
            }
        }
    }
}

/** A group's title, which opens and closes it, and the one action the group offers. */
@Composable
private fun LibraryGroupHeader(
    title: String,
    collapsed: Boolean,
    onToggle: () -> Unit,
    action: Pair<String, () -> Unit>?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onToggle)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                imageVector = if (collapsed) Icons.Outlined.KeyboardArrowDown else Icons.Outlined.KeyboardArrowUp,
                contentDescription = stringResource(
                    if (collapsed) R.string.library_group_expand else R.string.library_group_collapse
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        action?.let { (label, onClick) ->
            TextButton(onClick = onClick) { Text(label, maxLines = 1) }
        }
    }
}

@Composable
private fun AdoptionBatchCard(batch: AdoptionBatch) {
    WyCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.library_adopt_progress, batch.checked, batch.total),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            WySpinner(size = 18.dp, strokeWidth = 2.dp)
        }
    }
}

@Composable
fun UpdateCheckStatusCard(task: UpdateCheckTask) {
    val title = stringResource(
        when (task.status) {
            "QUEUED" -> R.string.update_check_queued
            "CHECKING" -> R.string.update_check_checking
            "FOUND" -> R.string.update_check_found
            "READY" -> R.string.update_check_ready
            "DOWNLOADING" -> R.string.update_check_downloading
            "VERIFYING" -> R.string.queue_status_verifying
            "DOWNLOADED" -> R.string.update_check_downloaded
            "INSTALLING" -> R.string.update_check_installing
            "COMPLETE" -> R.string.update_check_complete
            "CURRENT" -> R.string.update_check_current
            "INSTALLED" -> R.string.update_check_installed
            "SKIPPED" -> R.string.update_check_skipped
            "ERROR", "FAILED" -> R.string.update_check_error
            else -> R.string.update_check_current
        }
    )
    WyCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (task.active) WySpinner(size = 18.dp, strokeWidth = 2.dp)
            }
            task.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (task.total > 0) {
                Text(stringResource(R.string.library_check_progress, task.checked, task.total, task.updates), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun InstalledAppIcon(app: InstalledApp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val icon = remember(app.packageName, context) {
        dev.wystore.data.InstalledPackageLookup.icon(context.packageManager, app.packageName)
    }
    AsyncImage(
        model = icon,
        contentDescription = stringResource(R.string.common_app_icon, app.label),
        modifier = modifier.clip(MaterialTheme.shapes.medium)
    )
}

@Composable
fun formatLastUpdated(time: Long): String =
    if (time <= 0) stringResource(R.string.common_no_data) else DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(time))
