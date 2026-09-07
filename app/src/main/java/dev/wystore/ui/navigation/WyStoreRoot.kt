package dev.wystore.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.wystore.R
import dev.wystore.StoreViewModel
import dev.wystore.data.GitHubAsset
import dev.wystore.data.InstalledApp
import dev.wystore.data.ManagedApp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.wystore.data.StoreCategory
import androidx.compose.ui.platform.LocalContext
import dev.wystore.ui.catalog.CategoryScreen
import dev.wystore.ui.catalog.CategoryViewModel
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
    var uninstallDialog by remember { mutableStateOf<InstalledApp?>(null) }
    var githubInstallDialog by remember { mutableStateOf<GitHubAsset?>(null) }
    val snackbars = remember { SnackbarHostState() }
    val context = LocalContext.current
    val allCategoriesTitle = stringResource(R.string.home_all_apps)

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
    if (githubApp.entry != null && githubSelectedRelease == null) {
        GitHubAppScreen(
            state = githubApp,
            installedApp = state.installed.firstOrNull { installed ->
                state.managed.any { managed ->
                    managed.githubRepository == githubApp.entry.repository &&
                        managed.packageName == installed.packageName
                }
            },
            onBack = viewModel::closeGitHubApp,
            onInstallAsset = { githubInstallDialog = it },
            onOpenInstalled = viewModel::launchInstalledApp,
            onRetry = viewModel::retryGitHubApp,
            onOpenAllReleases = { destination = WyStoreDestination.GitHub }
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
            }
        )
    } else {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbars) },
            bottomBar = {
                FloatingNavigationBar(
                    currentDestination = destination,
                    onNavigate = { destination = it }
                )
            }
        ) { padding ->
            val screenModifier = Modifier.fillMaxSize().padding(padding)
            when (destination) {
                WyStoreDestination.Home -> dev.wystore.ui.home.HomeScreen(
                    modifier = screenModifier,
                    searchQuery = state.query,
                    pendingUpdates = state.pendingUpdates,
                    featuredApps = homeState.featuredApps,
                    categories = homeState.categories,
                    catalogLoading = homeState.loading,
                    catalogStale = homeState.stale,
                    catalogError = homeState.error,
                    installed = state.installed,
                    managed = state.managed,
                    queue = state.installQueue,
                    onSearchClick = { destination = WyStoreDestination.Search },
                    onAppClick = { packageName -> viewModel.openDetails(packageName) },
                    onCategoryClick = openCategory,
                    onAllCategoriesClick = {
                        openCategory(StoreCategory(slug = "all", title = allCategoriesTitle))
                    },
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
                    onRetryCatalog = homeViewModel::retry
                )
                is WyStoreDestination.Category -> CategoryScreen(
                    state = categoryState,
                    installed = state.installed,
                    modifier = screenModifier,
                    onBack = { destination = WyStoreDestination.Home },
                    onAppClick = { packageName -> viewModel.openDetails(packageName) },
                    onPreviousPage = categoryViewModel::previousPage,
                    onNextPage = categoryViewModel::nextPage,
                    onRetry = categoryViewModel::retry
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
                    onCheckUpdates = { viewModel.checkForUpdates() },
                    onOpen = { managedApp ->
                        if (managedApp.source == dev.wystore.data.ManagedSource.GITHUB) {
                            viewModel.openManagedGitHubRepository(managedApp)
                        } else {
                            viewModel.openDetails(managedApp.packageName)
                        }
                    },
                    onInstallPending = onInstallPending,
                    onQueueRetry = viewModel::queueRetry,
                    onQueueCancel = viewModel::queueCancel,
                    onQueueSkip = viewModel::queueSkip,
                    onQueueDownload = viewModel::queueDownload,
                    onStartQueue = viewModel::startQueue
                )
                WyStoreDestination.Library -> LibraryScreen(
                    modifier = screenModifier,
                    installed = state.installed,
                    managed = state.managed,
                    pendingUpdates = state.pendingUpdates,
                    updateCheckTask = state.updateCheckTask,
                    onCheckUpdates = { viewModel.checkForUpdates() },
                    onOpenStorePage = viewModel::openDetails,
                    onAdopt = { adoptDialog = it },
                    onUpdateManaged = viewModel::updateManaged,
                    onRequestForce = { forceDialog = it },
                    onRemoveManaged = { viewModel.setManaged(it, false) },
                    onUninstall = { uninstallDialog = it },
                    onCheck = viewModel::checkManagedApp,
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
                    ruStoreCompatibility = state.ruStoreCompatibility,
                    ruStoreCompatibilityTask = state.ruStoreCompatibilityTask,
                    rootAvailable = state.rootAvailable,
                    managedCount = state.managed.size,
                    githubCount = state.githubRepositories.size,
                    onSave = viewModel::saveSettings,
                    onCheckRoot = viewModel::checkRoot,
                    onCheckRuStore = viewModel::checkRuStoreCompatibility,
                    onSetRuStoreVersionCode = viewModel::setRuStoreVersionCode,
                    onExportUri = viewModel::exportBackupToUri,
                    onImportUri = { uri, merge -> viewModel.importBackupFromUri(uri, merge) },
                    onExportJson = viewModel::exportBackupJson,
                    onRestoreJson = viewModel::restoreBackupJson,
                    onOpenGitHub = { destination = WyStoreDestination.GitHub },
                    selfUpdate = state.selfUpdate,
                    onCheckSelfUpdate = viewModel::checkSelfUpdate,
                    onInstallSelfUpdate = viewModel::installSelfUpdate
                )
                is WyStoreDestination.AppDetails -> Unit
            }
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

    uninstallDialog?.let { target ->
        AlertDialog(
            onDismissRequest = { uninstallDialog = null },
            title = { Text(stringResource(R.string.dialog_uninstall_title)) },
            text = { Text(stringResource(R.string.dialog_uninstall_text, target.label)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.uninstall(target)
                    uninstallDialog = null
                }) { Text(stringResource(R.string.dialog_uninstall_confirm)) }
            },
            dismissButton = { TextButton(onClick = { uninstallDialog = null }) { Text(stringResource(R.string.common_cancel)) } }
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
