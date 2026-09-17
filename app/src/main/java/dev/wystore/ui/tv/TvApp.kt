package dev.wystore.ui.tv

import dev.wystore.rowFor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import dev.wystore.settings.TvCatalog
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import dev.wystore.settings.ThemeMode
import dev.wystore.settings.toAppSettings
import dev.wystore.ui.theme.WyStoreTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.wystore.R
import dev.wystore.StoreViewModel
import dev.wystore.data.ManagedSource
import dev.wystore.data.GitHubAsset
import androidx.compose.foundation.lazy.rememberLazyListState
import dev.wystore.data.GitHubCatalog
import dev.wystore.data.GitHubCatalogEntry
import dev.wystore.ui.components.LocalBottomBarInset
import dev.wystore.ui.components.ScreenPadding
import dev.wystore.ui.components.PackageUiStateReducer
import dev.wystore.ui.navigation.SharedRootDialogs
import dev.wystore.ui.settings.SettingsScreen
import dev.wystore.ui.theme.ThemePolicy
import kotlinx.coroutines.delay

private enum class TvDestination(val labelRes: Int, val icon: ImageVector) {
    HOME(R.string.nav_home, Icons.Outlined.Home),
    SEARCH(R.string.nav_search, Icons.Outlined.Search),
    MY_APPS(R.string.tv_nav_my_apps, Icons.AutoMirrored.Outlined.List),
    SETTINGS(R.string.nav_settings, Icons.Outlined.Settings)
}

/**
 * The TV interface: a menu along the top and four screens, driven by the same ViewModel as the
 * phone.
 *
 * Nothing here decides what installing, updating or checking means - it calls exactly what the
 * phone screens call. What differs is how it is reached: by a remote, from a sofa.
 *
 * The menu is a row of tabs, as on the TV's own home screen: moving along it switches the screen
 * below, and OK or down goes into that screen.
 *
 * Back walks outwards one step at a time, the way system TV apps do: from a screenshot to the app
 * page, from the app page to the card it was opened from, from anywhere in a screen up to its tab,
 * from a tab to Home, and from Home out of the app.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvApp(
    viewModel: StoreViewModel,
    onInstallPending: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val catalogViewModel: TvCatalogViewModel = viewModel()
    val catalog by catalogViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var destination by rememberSaveable { mutableStateOf(TvDestination.HOME) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var voiceRequested by remember { mutableStateOf(false) }
    var sourceFailureHelp by rememberSaveable { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val contentFocus = remember { FocusRequester() }
    // One requester per tab, looked up by the current destination when it is used. A single
    // requester moved onto the selected tab lags a frame behind a change of destination: when the
    // focused item vanished with the old screen, Android put the focus back into the menu, the
    // requester still sat on the old screen's tab, that tab switched the screen back, and the two
    // screens swapped places every frame.
    val tabFocus = remember { TvDestination.entries.associateWith { FocusRequester() } }
    var tabsFocused by remember { mutableStateOf(false) }
    var focusTabPending by remember { mutableStateOf(false) }
    // The app whose page was last opened, so Back lands on its card rather than on the first one.
    var lastOpened by rememberSaveable { mutableStateOf<String?>(null) }
    val openApp: (String) -> Unit = { packageName ->
        lastOpened = packageName
        viewModel.openDetails(packageName)
    }
    val catalogMode = state.settings.tvCatalog
    // The GitHub catalogue on a TV as on a phone, and gone from both when switched off in the
    // source settings.
    val githubEnabled = state.settings.githubEnabled
    val githubEntries = remember(githubEnabled, state.githubRepositories) {
        if (githubEnabled) GitHubCatalog.entries(state.githubRepositories) else emptyList()
    }
    val githubResults = remember(githubEnabled, searchQuery, state.githubRepositories) {
        if (githubEnabled) GitHubCatalog.search(searchQuery, state.githubRepositories) else emptyList()
    }
    var githubInstallDialog by remember { mutableStateOf<GitHubAsset?>(null) }
    val openGitHub: (GitHubCatalogEntry) -> Unit = { entry ->
        lastOpened = entry.tvKey()
        viewModel.openGitHubApp(entry)
    }
    val githubPage = state.githubApp.entry
    val homeListState = rememberLazyListState()
    val homeRowStates = remember { mutableMapOf<String, androidx.compose.foundation.lazy.LazyListState>() }
    var fullScreen by remember { mutableStateOf(false) }
    val closePages = {
        viewModel.clearDetails()
        viewModel.closeGitHubApp()
    }
    LaunchedEffect(catalogMode) {
        if (catalogMode != TvCatalog.TV) catalogViewModel.requirePhoneCatalog()
    }

    val packages = remember(state.installed, state.managed, state.installQueue, state.pendingUpdates) {
        TvPackageContext(state.installed, state.managed, state.installQueue, state.pendingUpdates)
    }
    val selected = state.selected
    val actions = TvPackageActions(
        onEnqueue = { packageName ->
            // On the app page the page's own flow runs, which asks before taking over an app that
            // came from somewhere else; everywhere else it is the quick install the phone rows use.
            if (selected?.packageName == packageName) viewModel.installSelected() else viewModel.quickInstall(packageName)
        },
        onInstallDownloaded = onInstallPending,
        onDownloadNow = viewModel::downloadNow,
        onPause = viewModel::pauseDownload,
        onResume = viewModel::resumeDownload,
        onConfirmSource = viewModel::askAboutUnverifiedSource,
        onLaunch = viewModel::launchInstalledApp
    )

    // A TV is watched in a dark room, and TV firmware rarely reports a dark system theme: on a TV
    // "follow the system" means dark. A light or dark choice made by hand is kept as it is.
    val tvSettings = state.settings.toAppSettings().let { settings ->
        if (settings.themeMode == ThemeMode.SYSTEM) settings.copy(themeMode = ThemeMode.DARK) else settings
    }
    WyStoreTheme(settings = tvSettings, tv = true) {
    TvTheme(dark = ThemePolicy.isDark(tvSettings.themeMode, isSystemInDarkTheme())) {
        SharedRootDialogs(
            viewModel = viewModel,
            state = state,
            sourceFailureHelp = sourceFailureHelp,
            onSourceFailureHelpDismiss = { sourceFailureHelp = false }
        )

        LaunchedEffect(state.message) {
            state.message?.let {
                message = it
                viewModel.consumeMessage()
            }
        }
        LaunchedEffect(message) {
            if (message != null) {
                delay(MESSAGE_MILLIS)
                message = null
            }
        }

        BackHandler(enabled = selected != null || githubPage != null) {
            if (githubPage != null) viewModel.closeGitHubApp() else viewModel.clearDetails()
        }
        // Registered after the app page's handler would be, but that page replaces the menu, so
        // the two are never enabled together. A settings page keeps its own, registered later.
        BackHandler(enabled = selected == null && githubPage == null && (!tabsFocused || destination != TvDestination.HOME)) {
            if (!tabsFocused) {
                runCatching { tabFocus.getValue(destination).requestFocus() }
            } else {
                destination = TvDestination.HOME
                focusTabPending = true
            }
        }
        LaunchedEffect(destination, focusTabPending) {
            if (focusTabPending) {
                runCatching { tabFocus.getValue(destination).requestFocus() }
                focusTabPending = false
            }
        }

        // A TV Surface rather than a plain background: it is what gives every Text below the
        // theme's content colour, which a bare Box leaves at black on a dark screen.
        androidx.tv.material3.Surface(
            modifier = Modifier.fillMaxSize(),
            colors = androidx.tv.material3.SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground
            )
        ) {
        Box(
            Modifier
                .fillMaxSize()
                // The remote's search key opens search and listens, as it does in system TV apps.
                .onPreviewKeyEvent { event ->
                    val isSearchKey = event.key == Key.Search || event.key == Key.VoiceAssist
                    if (!isSearchKey || event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent isSearchKey
                    viewModel.clearDetails()
                    viewModel.closeGitHubApp()
                    destination = TvDestination.SEARCH
                    voiceRequested = true
                    true
                }
        ) {
                Column(Modifier.fillMaxSize()) {
                    // The menu stays on an app page too, as the phone's bar does; only a screenshot
                    // opened full screen covers it.
                    if (!(fullScreen && selected != null)) Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                start = TvOverscanHorizontal,
                                end = TvOverscanHorizontal,
                                top = TvOverscanVertical,
                                bottom = 12.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TvBrand()
                        Spacer(Modifier.width(40.dp))
                        Box(
                            Modifier
                                // Coming up from a screen lands on that screen's own tab; landing
                                // on whichever tab is nearest would switch to another screen.
                                .focusProperties { onEnter = { tabFocus.getValue(destination).requestFocus() } }
                                .focusGroup()
                                .onFocusChanged { tabsFocused = it.hasFocus }
                        ) {
                            TabRow(
                                selectedTabIndex = destination.ordinal,
                                containerColor = Color.Transparent
                            ) {
                                TvDestination.entries.forEach { item ->
                                    Tab(
                                        selected = destination == item,
                                        onFocus = {
                                            // Moving onto another tab leaves an open page for that
                                            // tab's screen; coming up onto this one keeps it.
                                            if (destination != item) {
                                                closePages()
                                                destination = item
                                            }
                                        },
                                        onClick = { focusManager.moveFocus(FocusDirection.Down) },
                                        modifier = Modifier
                                            .focusRequester(tabFocus.getValue(item))
                                            .tvPointerClick {
                                                closePages()
                                                destination = item
                                                focusManager.moveFocus(FocusDirection.Down)
                                            }
                                    ) {
                                        Row(
                                            Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(item.icon, contentDescription = null, modifier = Modifier.size(22.dp))
                                            Spacer(Modifier.width(10.dp))
                                            Text(stringResource(item.labelRes), style = MaterialTheme.typography.titleSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val takeFocus = !tabsFocused
                    Box(Modifier.weight(1f)) {
                        if (githubPage != null) {
                            TvGitHubAppScreen(
                                state = state.githubApp,
                                // Recognised as the phone page recognises it: through the record of what Wy Store
                                // installed, or by the package a curated entry states.
                                installedApp = state.installed.firstOrNull { installed ->
                                    state.managed.any { managed ->
                                        managed.githubRepository == githubPage.repository && managed.packageName == installed.packageName
                                    } || githubPage.packageName == installed.packageName
                                },
                                onInstallAsset = { githubInstallDialog = it },
                                onOpenInstalled = viewModel::launchInstalledApp,
                                onUninstall = viewModel::uninstall,
                                onRetry = viewModel::retryGitHubApp
                            )
                        } else if (selected != null) {
                            TvDetailsScreen(
                                app = selected,
                                state = PackageUiStateReducer.reduce(
                                    app = selected,
                                    installed = state.installed.firstOrNull { it.packageName == selected.packageName },
                                    managed = state.managed.firstOrNull { it.packageName == selected.packageName },
                                    queueItem = state.installQueue.rowFor(selected.packageName),
                                    pendingUpdate = state.pendingUpdates.firstOrNull { it.packageName == selected.packageName },
                                    resources = context.resources
                                ),
                                installed = state.installed.firstOrNull { it.packageName == selected.packageName },
                                loading = state.detailsLoading,
                                // Marked the way its card was: a phone app opened from among TV apps.
                                forPhone = catalogMode == TvCatalog.BOTH && catalog.apps.none { it.packageName == selected.packageName },
                                actions = actions,
                                onUninstall = viewModel::uninstall,
                                onFullScreenChange = { fullScreen = it }
                            )
                        } else when (destination) {
                            TvDestination.HOME -> TvHomeScreen(
                                catalog = catalog,
                                catalogMode = catalogMode,
                                packages = packages,
                                firstFocus = contentFocus,
                                takeFocus = takeFocus,
                                restoreFocusTo = lastOpened,
                                onOpenApp = openApp,
                                onRetry = catalogViewModel::retry,
                                onOpenMyApps = { destination = TvDestination.MY_APPS },
                                githubEntries = githubEntries,
                                onOpenGitHub = openGitHub,
                                listState = homeListState,
                                rowStates = homeRowStates
                            )
                            TvDestination.SEARCH -> TvSearchScreen(
                                restoreFocusTo = lastOpened,
                                query = searchQuery,
                                onQueryChange = { searchQuery = it },
                                catalogMode = catalogMode,
                                catalogApps = catalog.apps,
                                rustoreQuery = state.query,
                                rustoreResults = state.search?.apps.orEmpty(),
                                rustoreSearching = state.searching,
                                packages = packages,
                                fieldFocus = contentFocus,
                                takeFocus = takeFocus,
                                onSearchRustore = viewModel::search,
                                onOpenApp = openApp,
                                githubResults = githubResults,
                                onOpenGitHub = openGitHub,
                                startVoice = voiceRequested,
                                onVoiceStarted = { voiceRequested = false }
                            )
                            TvDestination.MY_APPS -> TvMyAppsScreen(
                                packages = packages,
                                packageIcons = state.packageIcons,
                                checking = state.updateCheckTask?.active == true,
                                firstFocus = contentFocus,
                                takeFocus = takeFocus,
                                actions = actions,
                                onCheckUpdates = { viewModel.checkForUpdates() },
                                onUpdateAll = viewModel::updateAll,
                                onCancel = viewModel::queueCancel,
                                onOpenManaged = { app ->
                                    lastOpened = app.packageName
                                    val repository = app.githubRepository
                                    if (app.source == ManagedSource.GITHUB && repository != null) {
                                        // The GitHub page, as from Home: a repository added by hand
                                        // has no catalogue entry, so one is made from the record.
                                        viewModel.openGitHubApp(
                                            GitHubCatalog.entries(state.githubRepositories).firstOrNull { it.repository == repository }
                                                ?: GitHubCatalogEntry(
                                                    repository = repository,
                                                    title = app.label.ifBlank { repository.name },
                                                    publisher = repository.owner,
                                                    summary = ""
                                                )
                                        )
                                    } else {
                                        viewModel.openDetails(app.packageName)
                                    }
                                }
                            )
                            // The phone's settings, as they are: every option stays reachable on a
                            // TV, including the device type for anyone the detection got wrong.
                            TvDestination.SETTINGS -> CompositionLocalProvider(
                                LocalBottomBarInset provides TvOverscanVertical,
                                // The phone widgets show focus as a faint state layer, made for a
                                // keyboard on a phone; across a room it did not show where the
                                // remote was. The same layer in the accent colour, several times stronger.
                                LocalRippleConfiguration provides RippleConfiguration(
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                    rippleAlpha = TvSettingsFocusAlpha
                                )
                            ) {
                                // The phone's settings keep a phone's margin; a TV crops its edges,
                                // so the rest of the overscan margin is added around them.
                                SettingsScreen(
                                    modifier = Modifier.padding(horizontal = TvOverscanHorizontal - ScreenPadding),
                                    settings = state.settings,
                                    rootAvailable = state.rootAvailable,
                                    managedCount = state.managed.size,
                                    githubCount = state.githubRepositories.size,
                                    onSave = viewModel::saveSettings,
                                    onCheckRoot = viewModel::checkRoot,
                                    onExportUri = viewModel::exportBackupToUri,
                                    onImportUri = { uri, merge -> viewModel.importBackupFromUri(uri, merge) },
                                    onExportJson = viewModel::exportBackupJson,
                                    onRestoreJson = viewModel::restoreBackupJson,
                                    selfUpdate = state.selfUpdate,
                                    onCheckSelfUpdate = viewModel::checkSelfUpdate,
                                    onInstallSelfUpdate = viewModel::installSelfUpdate
                                )
                            }
                        }
                    }
                }

            message?.let {
                TvMessageBanner(it, Modifier.align(Alignment.BottomCenter))
            }

            githubInstallDialog?.let { asset ->
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { githubInstallDialog = null },
                    title = { androidx.compose.material3.Text(stringResource(R.string.dialog_github_install_title)) },
                    text = { androidx.compose.material3.Text(stringResource(R.string.dialog_github_install_text, asset.name)) },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            githubInstallDialog = null
                            viewModel.installGitHubAsset(asset)
                        }) { androidx.compose.material3.Text(stringResource(R.string.common_install)) }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { githubInstallDialog = null }) {
                            androidx.compose.material3.Text(stringResource(R.string.common_cancel))
                        }
                    }
                )
            }
        }
        }
    }
    }
}

private const val MESSAGE_MILLIS = 4_000L

/** The app's mark, its name and a "TV" label, so a screenshot says which version this is. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvBrand() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(40.dp)
                .clip(androidx.compose.material3.MaterialTheme.shapes.medium)
                // The launcher icon's own background, so the mark looks like the app's icon.
                .background(Color(0xFF0E141F)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_wy_store_foreground),
                contentDescription = null,
                modifier = Modifier.size(66.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(10.dp))
        TvBadge(
            stringResource(R.string.tv_brand_badge),
            container = MaterialTheme.colorScheme.primary,
            content = MaterialTheme.colorScheme.onPrimary
        )
    }
}

private val TvSettingsFocusAlpha = RippleAlpha(
    draggedAlpha = 0.16f,
    focusedAlpha = 0.36f,
    hoveredAlpha = 0.16f,
    pressedAlpha = 0.24f
)
