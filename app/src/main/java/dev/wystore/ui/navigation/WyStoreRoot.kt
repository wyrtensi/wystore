package dev.wystore.ui.navigation

import androidx.activity.compose.BackHandler
import dev.wystore.background.UpdateCheckPolicy
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.wystore.BuildConfig
import dev.wystore.R
import dev.wystore.StoreViewModel
import dev.wystore.data.GitHubAsset
import dev.wystore.data.InstalledApp
import dev.wystore.data.ManagedApp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.wystore.data.StoreApp
import dev.wystore.data.StoreCategory
import androidx.compose.ui.platform.LocalContext
import dev.wystore.ui.catalog.CategoryScreen
import dev.wystore.ui.catalog.CategoryViewModel
import dev.wystore.ui.components.LocalBottomBarInset
import dev.wystore.ui.components.launchUninstall
import dev.wystore.ui.library.GoogleAdoptionDialog
import dev.wystore.ui.components.WySnackbarHost
import dev.wystore.ui.details.AppDetailsScreen
import dev.wystore.ui.home.HomeViewModel
import dev.wystore.ui.github.GitHubAppScreen
import dev.wystore.ui.github.GitHubReleaseDetailsScreen
import dev.wystore.ui.github.GitHubScreen
import dev.wystore.ui.library.LibraryScreen
import dev.wystore.ui.search.SearchScreen
import dev.wystore.ui.settings.SettingsScreen
import dev.wystore.updates.UserConfirmedInstaller
import dev.wystore.ui.updates.UpdatesScreen

@Composable
fun WyStoreRoot(
    viewModel: StoreViewModel,
    onInstallPending: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    var destination by rememberSaveable(stateSaver = WyStoreDestination.Saver) {
        mutableStateOf<WyStoreDestination>(WyStoreDestination.Home)
    }
    // The open app page lives in the ViewModel, which does not survive process death: backgrounding
    // the app on a store page and coming back after Android reclaimed the process dropped the user
    // on a bare tab. The package name is small and saveable, so the page is reopened from it.
    var openedPackage by rememberSaveable { mutableStateOf<String?>(null) }
    // Captured during the very first composition, before any effect can run. Reading it later
    // would be too late: the effect that tracks the open page runs first and would have written
    // the current (empty) selection over the value that was just restored.
    val restoredPackage = remember { openedPackage }
    LaunchedEffect(Unit) {
        restoredPackage?.takeIf { state.selected == null }?.let(viewModel::openDetails)
    }
    // Only ever records an open page. Closing one clears the record explicitly, so a transient
    // null while details load cannot wipe it.
    LaunchedEffect(state.selected?.packageName) {
        state.selected?.packageName?.let { openedPackage = it }
    }

    val homeViewModel: HomeViewModel = viewModel()
    val homeState by homeViewModel.uiState.collectAsState()
    val categoryViewModel: CategoryViewModel = viewModel()
    val categoryState by categoryViewModel.uiState.collectAsState()
    val openCategory: (StoreCategory) -> Unit = { category ->
        categoryViewModel.open(category)
        destination = WyStoreDestination.Category(category.slug)
    }
    var installDialog by remember { mutableStateOf(false) }
    var adoptDialog by remember { mutableStateOf<InstalledApp?>(null) }
    var forceDialog by remember { mutableStateOf<ManagedApp?>(null) }
    // What is on the phone and what the catalogue offers in its place, for a handover that cannot
    // happen as an install and has to go through a removal.
    var replaceDialog by remember { mutableStateOf<Pair<InstalledApp, StoreApp>?>(null) }
    // The same offer made from a stopped queue row, where the signature is what stands in the way.
    var queueReplaceDialog by remember { mutableStateOf<dev.wystore.InstallQueueItem?>(null) }
    var githubInstallDialog by remember { mutableStateOf<GitHubAsset?>(null) }
    val snackbars = remember { SnackbarHostState() }
    val context = LocalContext.current
    val allCategoriesTitle = stringResource(R.string.home_all_apps)
    // Whose sections to browse by. Both sets come back merged from the catalogue; which of them is
    // shown is a setting rather than a fetch, so switching is instant and costs no request.
    val visibleCategories = remember(homeState.categories, state.settings.sourceCategories) {
        val curated = { slug: String -> slug.startsWith("wy-") }
        homeState.categories.filter { curated(it.slug) != state.settings.sourceCategories }
            .ifEmpty { homeState.categories }
    }

    BackHandler(
        enabled = state.selected != null ||
            state.githubSelectedRelease != null ||
            state.githubApp.entry != null ||
            destination != WyStoreDestination.Home
    ) {
        when {
            state.githubSelectedRelease != null -> viewModel.closeGitHubRelease()
            state.githubApp.entry != null -> viewModel.closeGitHubApp()
            state.selected != null -> {
                openedPackage = null
                viewModel.clearDetails()
            }
            else -> destination = WyStoreDestination.Home
        }
    }

    // A transfer the user asked for runs on whatever connection there is, so "Wi-Fi only" was
    // spent rather than honoured whenever someone pressed Install on mobile data. Asking is what
    // lets the button stay responsive and the setting stay true.
    if (state.meteredDownloadPrompt) {
        var always by rememberSaveable { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = viewModel::cancelMeteredDownload,
            title = { Text(stringResource(R.string.metered_download_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.metered_download_body))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { always = !always },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = always, onCheckedChange = { always = it })
                        Text(stringResource(R.string.metered_download_always))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmMeteredDownload(always) }) {
                    Text(stringResource(R.string.metered_download_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelMeteredDownload) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // The queue coordinator has always computed the next item after an install finishes; nothing
    // in the UI ever read it, so smart mode silently did nothing.
    val offeredNext by viewModel.offeredNext.collectAsState()
    offeredNext?.let { next ->
        AlertDialog(
            onDismissRequest = viewModel::queueDismissOfferedNext,
            title = { Text(stringResource(R.string.offer_next_title)) },
            text = { Text(stringResource(R.string.offer_next_body, next.label.ifBlank { next.packageName })) },
            confirmButton = {
                TextButton(onClick = {
                    val activity = context as? android.app.Activity
                    if (activity != null) {
                        viewModel.queueAcceptNext(UserConfirmedInstaller(activity))
                    } else {
                        viewModel.queueDismissOfferedNext()
                    }
                }) { Text(stringResource(R.string.offer_next_accept)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::queueDismissOfferedNext) {
                    Text(stringResource(R.string.offer_next_dismiss))
                }
            }
        )
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbars.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val selected = state.selected
    val githubSelectedRelease = state.githubSelectedRelease

    val githubApp = state.githubApp
    // A detail screen replaces the Scaffold below rather than sitting inside it, and that Scaffold
    // is what hosts the snackbar. So everything the app had to say while an app page, a release
    // page or a GitHub page was open - queued, failed, permission needed - was shown into a host
    // that was not on screen, and the button that raised it looked like it had done nothing.
    val detailScreenShown = githubApp.entry != null || githubSelectedRelease != null || selected != null
    Box(Modifier.fillMaxSize()) {
    if (githubApp.entry != null && githubSelectedRelease == null) {
        GitHubAppScreen(
            state = githubApp,
            installedApp = state.installed.firstOrNull { installed ->
                // What Wy Store installed itself carries the link in its managed record; a curated
                // entry that states its package is recognised however the app got onto the phone.
                state.managed.any { managed ->
                    managed.githubRepository == githubApp.entry.repository &&
                        managed.packageName == installed.packageName
                } || githubApp.entry.packageName == installed.packageName
            },
            onBack = viewModel::closeGitHubApp,
            onInstallAsset = { githubInstallDialog = it },
            onOpenInstalled = viewModel::launchInstalledApp,
            onRetry = viewModel::retryGitHubApp,
            onOpenRelease = viewModel::openGitHubRelease
        )
    } else if (githubSelectedRelease != null) {
        GitHubReleaseDetailsScreen(
            release = githubSelectedRelease,
            install = state.githubInstall,
            onBack = viewModel::closeGitHubRelease,
            onInstall = { githubInstallDialog = it }
        )
    } else if (selected != null) {
        val selectedInstalled = state.installed.firstOrNull { it.packageName == selected.packageName }
        val selectedManaged = state.managed.firstOrNull { it.packageName == selected.packageName }
        AppDetailsScreen(
            app = selected,
            installed = selectedInstalled,
            pendingUpdate = state.pendingUpdates.firstOrNull { it.packageName == selected.packageName },
            rootAvailable = state.rootAvailable,
            busy = state.detailsLoading,
            queueItem = state.installQueue.firstOrNull { it.packageName == selected.packageName },
            reviewsLoading = state.reviewsLoading,
            canLoadMoreReviews = selected.packageName !in state.fullReviewsLoaded,
            onLoadMoreReviews = viewModel::loadAllReviews,
            onBack = {
                openedPackage = null
                viewModel.clearDetails()
            },
            onLaunch = viewModel::launchInstalledApp,
            onInstallPending = onInstallPending,
            onInstall = {
                if (selectedInstalled != null && selectedManaged == null) installDialog = true
                else viewModel.installSelected()
            },
            onReplace = { selectedInstalled?.let { replaceDialog = it to selected } }
        )
    } else {
        Scaffold(
            // Every screen carries its own Scaffold and top bar, and those already stand clear of
            // the status bar. Letting this one reserve the system bars too pushed each title down
            // by a second status bar's worth - an empty strip above every screen.
            contentWindowInsets = WindowInsets(0),
            snackbarHost = {
                // Both, stacked: the install queue outlasts any snackbar and needs somewhere to
                // live, but replacing the host with it would swallow every message raised during
                // exactly the stretch when something is most likely to go wrong.
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    dev.wystore.ui.components.InstallQueueBanner(
                        current = state.installAllCurrent,
                        waiting = state.installAllRemaining.size
                    )
                    WySnackbarHost(snackbars)
                }
            },
            bottomBar = {
                FloatingNavigationBar(
                    currentDestination = destination,
                    onNavigate = { destination = it }
                )
            }
        ) { padding ->
            // The bar's height reaches the screens as content padding rather than as layout
            // padding: reserving it made the bar a band the list stopped above, with an empty
            // strip of background underneath.
            val screenModifier = Modifier.fillMaxSize()
            CompositionLocalProvider(LocalBottomBarInset provides padding.calculateBottomPadding()) {
                when (destination) {
                    WyStoreDestination.Home -> dev.wystore.ui.home.HomeScreen(
                        modifier = screenModifier,
                        searchQuery = state.query,
                        pendingUpdates = state.pendingUpdates,
                        featuredApps = homeState.featuredApps,
                        categories = visibleCategories,
                        catalogLoading = homeState.loading,
                        catalogStale = homeState.stale,
                        catalogError = homeState.error,
                        installed = state.installed,
                        managed = state.managed,
                        queue = state.installQueue,
                        onSearchClick = { destination = WyStoreDestination.Search },
                        onAppClick = { packageName -> viewModel.openDetails(packageName) },
                        onCategoryClick = openCategory,
                        // Running out of rail widens the choice; it used to replace it with one
                        // long list of every app the source has, which is a different question.
                        onAllCategoriesClick = { destination = WyStoreDestination.Categories },
                        categoryPreviews = homeState.categoryPreviews,
                        onNeedCategoryPreview = homeViewModel::requestCategoryPreview,
                        onOpenGitHub = { destination = WyStoreDestination.GitHub },
                        onUpdatesClick = { destination = WyStoreDestination.Updates },
                        // Downloading what is already verified is the point of the queue; a metadata
                        // re-check here would leave ready updates untouched.
                        githubPicks = if (state.settings.githubEnabled) {
                            dev.wystore.data.GitHubCatalog.entries(state.githubRepositories)
                        } else {
                            emptyList()
                        },
                        onGitHubPickClick = viewModel::openGitHubApp,
                        onUpdateAll = { viewModel.updateAll() },
                        onRetryCatalog = homeViewModel::retry,
                        // The one button that says what it will do used to open the app's page
                        // instead of doing it.
                        onInstall = viewModel::quickInstall,
                        onInstallDownloaded = onInstallPending,
                        onLaunch = viewModel::launchInstalledApp
                    )
                    is WyStoreDestination.Category -> CategoryScreen(
                        state = categoryState,
                        installed = state.installed,
                        managed = state.managed,
                        modifier = screenModifier,
                        onBack = { destination = WyStoreDestination.Home },
                        onAppClick = { packageName -> viewModel.openDetails(packageName) },
                        onPreviousPage = categoryViewModel::previousPage,
                        onNextPage = categoryViewModel::nextPage,
                        onRetry = categoryViewModel::retry,
                        onInstall = viewModel::quickInstall,
                        onInstallDownloaded = onInstallPending,
                        onLaunch = viewModel::launchInstalledApp
                    )
                    WyStoreDestination.Search -> SearchScreen(
                        modifier = screenModifier,
                        query = state.query,
                        apps = state.search?.apps ?: emptyList(),
                        total = state.search?.total,
                        busy = state.searching,
                        operation = state.operation,
                        installed = state.installed,
                        queue = state.installQueue,
                        pendingUpdates = state.pendingUpdates,
                        onInstallPending = onInstallPending,
                        githubResults = state.githubSearchResults,
                        onOpenGitHubApp = viewModel::openGitHubApp,
                        onSearch = viewModel::search,
                        onLoadMore = viewModel::searchMore,
                        onOpen = { app -> viewModel.openDetails(app.packageName) },
                        onQuickInstall = viewModel::quickInstall,
                        onLaunch = viewModel::launchInstalledApp
                    )
                    WyStoreDestination.GitHub -> GitHubScreen(
                        modifier = screenModifier,
                        repositories = state.githubRepositories,
                        activeRepository = state.githubActiveRepository,
                        releases = state.githubReleases,
                        loading = state.githubLoading,
                        install = state.githubInstall,
                        catalog = dev.wystore.data.GitHubCatalog.entries(state.githubRepositories),
                        onLoad = viewModel::loadGitHubRepository,
                        onOpen = viewModel::openGitHubRelease,
                        onOpenCatalogEntry = viewModel::openGitHubApp,
                        onRemove = viewModel::removeGitHubRepository
                    )
                    WyStoreDestination.Updates -> UpdatesScreen(
                        modifier = screenModifier,
                        managed = state.managed,
                        installed = state.installed,
                        pendingUpdates = state.pendingUpdates,
                        updateCheckTask = state.updateCheckTask,
                        lastUpdateCheck = state.lastUpdateCheck,
                        queue = state.installQueue,
                        packageIcons = state.packageIcons,
                        installsWhatItFinds = UpdateCheckPolicy.checkWillInstallWhatItFinds(
                            autoDownloadEnabled = state.settings.autoDownloadUpdates,
                            autoInstallEnabled = state.settings.autoInstallUpdates
                        ),
                        onCheckUpdates = { viewModel.checkForUpdates() },
                        onUpdateAll = { viewModel.updateAll() },
                        onOpen = { managedApp ->
                            if (managedApp.source == dev.wystore.data.ManagedSource.GITHUB) {
                                viewModel.openManagedGitHubRepository(managedApp)
                            } else {
                                viewModel.openDetails(managedApp.packageName)
                            }
                        },
                        onInstallPending = onInstallPending,
                        onDiscardPending = viewModel::discardPendingUpdate,
                        onQueueRetry = viewModel::queueRetry,
                        onQueueCancel = viewModel::queueCancel,
                        onQueueSkip = viewModel::queueSkip,
                        onQueueDownload = viewModel::queueDownload,
                        onQueueDiscard = viewModel::queueDiscard,
                        onQueueReplace = { queueReplaceDialog = it },
                        onStartQueue = viewModel::startQueue
                    )
                    WyStoreDestination.Categories -> dev.wystore.ui.home.AllCategoriesScreen(
                        modifier = screenModifier,
                        categories = visibleCategories,
                        githubEnabled = state.settings.githubEnabled,
                        onBack = { destination = WyStoreDestination.Home },
                        onCategoryClick = openCategory,
                        previews = homeState.categoryPreviews,
                        onNeedPreview = homeViewModel::requestCategoryPreview,
                        onGitHubClick = { destination = WyStoreDestination.GitHub },
                        onAllAppsClick = {
                            openCategory(StoreCategory(slug = "all", title = allCategoriesTitle))
                        }
                    )
                    WyStoreDestination.Library -> LibraryScreen(
                        modifier = screenModifier,
                        installed = state.installed,
                        managed = state.managed,
                        pendingUpdates = state.pendingUpdates,
                        updateCheckTask = state.updateCheckTask,
                        onCheckUpdates = { viewModel.checkForUpdates() },
                        installsWhatItFinds = UpdateCheckPolicy.checkWillInstallWhatItFinds(
                            autoDownloadEnabled = state.settings.autoDownloadUpdates,
                            autoInstallEnabled = state.settings.autoInstallUpdates
                        ),
                        onUpdateAll = { viewModel.updateAll() },
                        onOpenStorePage = viewModel::openDetails,
                        onAdopt = { app ->
                            if (app.source == dev.wystore.data.InstallSource.GOOGLE_PLAY) {
                                viewModel.beginGoogleAdoption(app)
                            } else {
                                adoptDialog = app
                            }
                        },
                        onUpdateManaged = viewModel::updateManaged,
                        onRequestForce = { forceDialog = it },
                        onRemoveManaged = { viewModel.setManaged(it, false) },
                        onUninstall = { launchUninstall(context, it.packageName) },
                        onCheck = viewModel::checkManagedApp,
                        queue = state.installQueue,
                        onDownloadUpdate = viewModel::queueDownload,
                        onOpenDetails = { managedApp ->
                            if (managedApp.source == dev.wystore.data.ManagedSource.GITHUB) {
                                viewModel.openManagedGitHubRepository(managedApp)
                            } else {
                                viewModel.openDetails(managedApp.packageName)
                            }
                        },
                        onLaunch = viewModel::launchInstalledApp,
                        onInstallPending = onInstallPending
                    )
                    WyStoreDestination.Settings -> SettingsScreen(
                        modifier = screenModifier,
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
                        onOpenGitHub = { destination = WyStoreDestination.GitHub },
                        selfUpdate = state.selfUpdate,
                        // Wy Store's own row in the queue, so its page can show the download instead
                        // of sending the user to Updates to watch it.
                        selfUpdateQueueItem = state.installQueue.firstOrNull {
                            it.packageName == BuildConfig.APPLICATION_ID
                        },
                        selfUpdatePending = state.pendingUpdates.firstOrNull {
                            it.packageName == BuildConfig.APPLICATION_ID
                        },
                        onCheckSelfUpdate = viewModel::checkSelfUpdate,
                        onInstallSelfUpdate = viewModel::installSelfUpdate,
                        onInstallDownloadedSelfUpdate = { onInstallPending(BuildConfig.APPLICATION_ID) },
                        onCancelSelfUpdateDownload = viewModel::queueCancel
                    )
                    is WyStoreDestination.AppDetails -> Unit
                }
            }
        }
    }
    if (detailScreenShown) {
        WySnackbarHost(
            snackbars,
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 16.dp)
        )
    }
    }

    if (installDialog) {
        AlertDialog(
            onDismissRequest = { installDialog = false },
            title = { Text(stringResource(R.string.dialog_install_existing_title)) },
            text = { Text(stringResource(R.string.dialog_install_existing_text)) },
            confirmButton = {
                TextButton(onClick = {
                    installDialog = false
                    viewModel.installSelected()
                }) { Text(stringResource(R.string.common_continue)) }
            },
            dismissButton = { TextButton(onClick = { installDialog = false }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }

    state.googleAdoption?.let { prompt ->
        GoogleAdoptionDialog(
            prompt = prompt,
            onPick = { candidate ->
                // Recorded first: the uninstall dialog takes the screen and may outlive this
                // process, and the install has to survive that trip.
                viewModel.confirmGoogleAdoption(candidate)
                launchUninstall(context, prompt.app.packageName)
            },
            onDismiss = viewModel::dismissGoogleAdoption
        )
    }

    replaceDialog?.let { (installedApp, catalogApp) ->
        AlertDialog(
            onDismissRequest = { replaceDialog = null },
            title = { Text(stringResource(R.string.dialog_replace_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.dialog_replace_text,
                        installedApp.label,
                        catalogApp.versionName,
                        installedApp.versionName
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    // Recorded first: the uninstall dialog takes the screen and may outlive this
                    // process, and the install has to survive that trip.
                    viewModel.confirmReplaceInstall(installedApp.packageName, catalogApp.name)
                    replaceDialog = null
                    launchUninstall(context, installedApp.packageName)
                }) { Text(stringResource(R.string.dialog_replace_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { replaceDialog = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    queueReplaceDialog?.let { item ->
        val installedVersion = state.installed.firstOrNull { it.packageName == item.packageName }?.versionName
        AlertDialog(
            onDismissRequest = { queueReplaceDialog = null },
            title = { Text(stringResource(R.string.dialog_replace_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.dialog_replace_text,
                        item.label.ifBlank { item.packageName },
                        item.versionName.ifBlank { stringResource(R.string.common_no_data) },
                        installedVersion ?: stringResource(R.string.common_no_data)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.confirmReplaceFromQueue(item)
                    queueReplaceDialog = null
                    launchUninstall(context, item.packageName)
                }) { Text(stringResource(R.string.dialog_replace_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { queueReplaceDialog = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    adoptDialog?.let { target ->
        AlertDialog(
            onDismissRequest = { adoptDialog = null },
            title = { Text(stringResource(R.string.dialog_adopt_title)) },
            text = { Text(stringResource(R.string.dialog_adopt_text, target.label)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setManaged(target, true)
                    adoptDialog = null
                }) { Text(stringResource(R.string.dialog_adopt_confirm)) }
            },
            dismissButton = { TextButton(onClick = { adoptDialog = null }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }

    forceDialog?.let { target ->
        AlertDialog(
            onDismissRequest = { forceDialog = null },
            title = { Text(stringResource(R.string.dialog_force_title)) },
            text = { Text(stringResource(R.string.dialog_force_text, target.label)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateManaged(target.copy(forceWyStore = true))
                    forceDialog = null
                }) { Text(stringResource(R.string.dialog_force_confirm)) }
            },
            dismissButton = { TextButton(onClick = { forceDialog = null }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }

    githubInstallDialog?.let { asset ->
        AlertDialog(
            onDismissRequest = { githubInstallDialog = null },
            title = { Text(stringResource(R.string.dialog_github_install_title)) },
            text = { Text(stringResource(R.string.dialog_github_install_text, asset.name)) },
            confirmButton = {
                TextButton(onClick = {
                    githubInstallDialog = null
                    viewModel.installGitHubAsset(asset)
                }) { Text(stringResource(R.string.common_install)) }
            },
            dismissButton = { TextButton(onClick = { githubInstallDialog = null }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
}
