package dev.wystore.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.Text
import dev.wystore.R
import dev.wystore.StoreViewModel
import dev.wystore.data.ManagedSource
import dev.wystore.ui.components.LocalBottomBarInset
import dev.wystore.ui.components.PackageUiStateReducer
import dev.wystore.ui.components.launchUninstall
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
 * The TV interface: a side menu and four screens, driven by the same ViewModel as the phone.
 *
 * Nothing here decides what installing, updating or checking means - it calls exactly what the
 * phone screens call. What differs is how it is reached: by a remote, from a sofa.
 *
 * Back closes whatever is on top (an app page), then returns to Home, then leaves the app, which
 * is what system TV apps do.
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
    var destination by rememberSaveable { mutableStateOf(TvDestination.HOME) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var voiceRequested by remember { mutableStateOf(false) }
    var sourceFailureHelp by rememberSaveable { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val contentFocus = remember { FocusRequester() }
    val selectedItemFocus = remember { FocusRequester() }
    // The app whose page was last opened, so Back lands on its card rather than on the first one.
    var lastOpened by rememberSaveable { mutableStateOf<String?>(null) }
    val openApp: (String) -> Unit = { packageName ->
        lastOpened = packageName
        viewModel.openDetails(packageName)
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
    WyStoreTheme(settings = tvSettings) {
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

        BackHandler(enabled = selected != null || destination != TvDestination.HOME) {
            if (selected != null) viewModel.clearDetails() else destination = TvDestination.HOME
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
                    destination = TvDestination.SEARCH
                    voiceRequested = true
                    true
                }
        ) {
            if (selected != null) {
                TvDetailsScreen(
                    app = selected,
                    state = PackageUiStateReducer.reduce(
                        app = selected,
                        installed = state.installed.firstOrNull { it.packageName == selected.packageName },
                        managed = state.managed.firstOrNull { it.packageName == selected.packageName },
                        queueItem = state.installQueue.firstOrNull { it.packageName == selected.packageName },
                        pendingUpdate = state.pendingUpdates.firstOrNull { it.packageName == selected.packageName },
                        resources = context.resources
                    ),
                    installed = state.installed.firstOrNull { it.packageName == selected.packageName },
                    loading = state.detailsLoading,
                    actions = actions,
                    onUninstall = { launchUninstall(context, it) }
                )
            } else {
                NavigationDrawer(
                    drawerContent = {
                        Column(
                            Modifier
                                .fillMaxHeight()
                                .padding(vertical = TvOverscanVertical, horizontal = 12.dp)
                                // Entering the menu lands on the section that is open, so one
                                // press of up or down is always relative to where the user is.
                                .focusProperties { onEnter = { selectedItemFocus.requestFocus() } }
                                .focusGroup()
                                .selectableGroup(),
                            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
                        ) {
                            TvDestination.entries.forEach { item ->
                                NavigationDrawerItem(
                                    selected = destination == item,
                                    onClick = { destination = item },
                                    modifier = Modifier
                                        .then(if (destination == item) Modifier.focusRequester(selectedItemFocus) else Modifier)
                                        .tvPointerClick { destination = item },
                                    leadingContent = { Icon(item.icon, contentDescription = null) }
                                ) {
                                    Text(stringResource(item.labelRes))
                                }
                            }
                        }
                    }
                ) {
                    when (destination) {
                        TvDestination.HOME -> TvHomeScreen(
                            catalog = catalog,
                            packages = packages,
                            firstCardFocus = contentFocus,
                            restoreFocusTo = lastOpened,
                            onOpenApp = openApp,
                            onRetry = catalogViewModel::retry
                        )
                        TvDestination.SEARCH -> TvSearchScreen(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            catalogApps = catalog.apps,
                            rustoreResults = state.search?.apps.orEmpty(),
                            rustoreSearching = state.searching,
                            packages = packages,
                            fieldFocus = contentFocus,
                            onSearchRustore = viewModel::search,
                            onOpenApp = openApp,
                            startVoice = voiceRequested,
                            onVoiceStarted = { voiceRequested = false }
                        )
                        TvDestination.MY_APPS -> TvMyAppsScreen(
                            packages = packages,
                            packageIcons = state.packageIcons,
                            checking = state.updateCheckTask?.active == true,
                            firstFocus = contentFocus,
                            actions = actions,
                            onCheckUpdates = { viewModel.checkForUpdates() },
                            onUpdateAll = viewModel::updateAll,
                            onCancel = viewModel::queueCancel,
                            onOpenManaged = { app ->
                                if (app.source == ManagedSource.GITHUB) viewModel.openManagedGitHubRepository(app)
                                else viewModel.openDetails(app.packageName)
                            }
                        )
                        // The phone's settings, as they are: every option stays reachable on a TV,
                        // including the device type for anyone the detection got wrong.
                        TvDestination.SETTINGS -> CompositionLocalProvider(LocalBottomBarInset provides TvOverscanVertical) {
                            SettingsScreen(
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
        }
        }
    }
    }
}

private const val MESSAGE_MILLIS = 4_000L
