package dev.wystore

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dev.wystore.data.DownloadProgress
import dev.wystore.data.GitHubAsset
import dev.wystore.data.GitHubCatalog
import dev.wystore.data.GitHubCatalogEntry
import dev.wystore.data.GitHubRelease
import dev.wystore.data.GitHubReleaseSource
import dev.wystore.data.GitHubRepository
import dev.wystore.data.InstalledApp
import dev.wystore.data.ManagedApp
import dev.wystore.data.RuStoreSource
import dev.wystore.data.SearchPage
import dev.wystore.data.SearchResultPolicy
import dev.wystore.data.StoreApp
import dev.wystore.data.StoreRepository
import dev.wystore.data.StoreSettings
import dev.wystore.data.UpdateCheckSummary
import dev.wystore.R
import dev.wystore.data.AdoptionCandidate
import dev.wystore.data.GoogleAdoptionPolicy
import dev.wystore.data.BackupLocation
import dev.wystore.data.InstallSource
import dev.wystore.data.MeteredDownloadConsent
import dev.wystore.data.MeteredDownloadPolicy
import dev.wystore.data.isActiveNetworkMetered
import dev.wystore.data.PendingUpdate
import dev.wystore.root.RootInstaller
import dev.wystore.updates.UpdateScheduler
import dev.wystore.updates.ManualInstallScheduler
import dev.wystore.updates.GitHubInstallScheduler
import dev.wystore.updates.InstalledUpdateMatcher
import dev.wystore.ui.github.GitHubAppUiState
import dev.wystore.data.CatalogRepository
import dev.wystore.localization.RootTextResolver
import dev.wystore.localization.SourceTextResolver
import dev.wystore.selfupdate.SelfUpdateChecker
import dev.wystore.selfupdate.SelfUpdateStatus
import dev.wystore.updates.PendingUpdateNotifier
import dev.wystore.updates.PendingUpdateCatalog
import dev.wystore.updates.PendingReinstall
import dev.wystore.updates.PendingReinstallStore
import dev.wystore.updates.QueueRepository
import dev.wystore.updates.UnverifiedSourceConsent
import dev.wystore.updates.UnverifiedSourceStore
import dev.wystore.updates.model.QueueState
import dev.wystore.settings.toStoreSettings
import dev.wystore.settings.toAppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class InstallQueueStatus {
    RESOLVING,
    QUEUED,
    DOWNLOADING,
    PAUSED,
    VERIFYING,
    READY,
    INSTALLING,
    COMPLETE,
    CANCELED,
    FAILED
}

/**
 * How a durable queue row reads on screen.
 *
 * OFFER_NEXT is here for rows written by older versions: an install used to end there and stay,
 * which is why an updated app went on showing a queued, disabled button. Those rows are finished
 * installs and are reported as such.
 */
fun QueueState.toInstallQueueStatus(): InstallQueueStatus = when (this) {
    QueueState.AVAILABLE,
    QueueState.CHECKING -> InstallQueueStatus.QUEUED
    QueueState.DOWNLOADING -> InstallQueueStatus.DOWNLOADING
    QueueState.PAUSED -> InstallQueueStatus.PAUSED
    QueueState.VERIFYING -> InstallQueueStatus.VERIFYING
    // The download is finished and the item is waiting for the user to start the install, so cards
    // must offer Install rather than keep saying queued.
    QueueState.READY_TO_INSTALL,
    QueueState.AWAITING_UNKNOWN_SOURCES_PERMISSION,
    QueueState.AWAITING_USER_CONFIRMATION -> InstallQueueStatus.READY
    QueueState.INSTALLING -> InstallQueueStatus.INSTALLING
    QueueState.INSTALLED,
    QueueState.OFFER_NEXT -> InstallQueueStatus.COMPLETE
    QueueState.CANCELED,
    QueueState.SKIPPED -> InstallQueueStatus.CANCELED
    QueueState.FAILED -> InstallQueueStatus.FAILED
}

/**
 * Whether the queue is actively working on the item, so the UI shows progress and holds its actions.
 *
 * Screens used to spell this out as "anything that is not COMPLETE or FAILED", which also caught
 * states in which nothing is running: a canceled download, or an install that had already finished,
 * left the app page with a disabled "Queued" button forever.
 */
val InstallQueueStatus.isInFlight: Boolean
    get() = when (this) {
        InstallQueueStatus.RESOLVING,
        InstallQueueStatus.QUEUED,
        InstallQueueStatus.DOWNLOADING,
        InstallQueueStatus.VERIFYING,
        InstallQueueStatus.INSTALLING -> true
        // Paused is a row with nothing running: the button that resumes it has to be live.
        InstallQueueStatus.PAUSED,
        InstallQueueStatus.READY,
        InstallQueueStatus.COMPLETE,
        InstallQueueStatus.CANCELED,
        InstallQueueStatus.FAILED -> false
    }

/**
 * Whether the queue already owns this package, so a second transfer must not be started for it.
 *
 * Wider than [isInFlight] by one state: a downloaded item waiting for the user is idle, but its
 * artifact is on disk and re-downloading it would be pure waste.
 */
val InstallQueueStatus.occupiesQueue: Boolean
    get() = isInFlight || this == InstallQueueStatus.READY

data class InstallQueueItem(
    /** Durable queue row id; queue actions are addressed by it, not by package name. */
    val id: String = "",
    val packageName: String,
    val label: String,
    val status: InstallQueueStatus,
    /** What the source offers, so a row can say what is waiting rather than only that something is. */
    val versionName: String = "",
    val progress: DownloadProgress? = null,
    /** Typed reason, so the UI can render a localized message instead of the raw detail text. */
    val errorCode: dev.wystore.updates.model.QueueErrorCode? = null,
    val detail: String? = null
)

/**
 * The question about a file the source would not vouch for, and where it was raised.
 *
 * A refused download names its row; an app already on the phone names the two fingerprints that
 * disagree, because there is no row and nothing has been fetched yet.
 */
data class UnverifiedSourcePrompt(
    val packageName: String,
    val label: String,
    val queueId: String? = null,
    val advertisedDigest: String? = null,
    val installedDigest: String? = null
)

data class UpdateCheckTask(
    val status: String,
    val detail: String? = null,
    val checked: Int = 0,
    val total: Int = 0,
    val updates: Int = 0,
    val active: Boolean = false,
    /**
     * Whether the check is on a thread right now rather than waiting for its constraints.
     *
     * The check button is disabled while a check is under way, and it used to read [active], which
     * is also true for a run that is merely queued. A check that could not start - no network it
     * was allowed to use, say - therefore disabled the only control that could enqueue a new one,
     * and the screen stayed on "queued" with nothing the user could do about it.
     */
    val running: Boolean = false
)

/**
 * What the store found when asked to take over an app that came from Google.
 *
 * Such an app cannot be updated in place at all, so "adopt" means removing it and installing the
 * same app from a source Wy Store can update. Which app that is has to be shown and chosen, never
 * guessed silently - it ends in an uninstall.
 */
data class GoogleAdoptionPrompt(
    val app: InstalledApp,
    val loading: Boolean = true,
    val candidates: List<AdoptionCandidate> = emptyList()
)

data class StoreUiState(
    val query: String = "",
    val search: SearchPage? = null,
    val selected: StoreApp? = null,
    val installed: List<InstalledApp> = emptyList(),
    val managed: List<ManagedApp> = emptyList(),
    val settings: StoreSettings = StoreSettings(),
    val updateCheckTask: UpdateCheckTask? = null,
    val googleAdoption: GoogleAdoptionPrompt? = null,
    val lastUpdateCheck: UpdateCheckSummary? = null,
    val rootAvailable: Boolean? = null,
    val busy: Boolean = false,
    val searching: Boolean = false,
    val detailsLoading: Boolean = false,
    val operation: String? = null,
    val downloadProgress: DownloadProgress? = null,
    val installQueue: List<InstallQueueItem> = emptyList(),
    val pendingUpdates: List<PendingUpdate> = emptyList(),
    val message: String? = null,
    val githubRepositories: List<GitHubRepository> = emptyList(),
    val githubActiveRepository: GitHubRepository? = null,
    val githubReleases: List<GitHubRelease> = emptyList(),
    val githubSelectedRelease: GitHubRelease? = null,
    val githubLoading: Boolean = false,
    val githubInstall: InstallQueueItem? = null,
    /** GitHub catalogue entries matching the current search query. */
    val githubSearchResults: List<GitHubCatalogEntry> = emptyList(),
    val githubApp: GitHubAppUiState = GitHubAppUiState(),
    val selfUpdate: SelfUpdateStatus = SelfUpdateStatus.Idle,
    /** The full review list is being fetched for the open app page. */
    val reviewsLoading: Boolean = false,
    /** Packages whose full review list has already been fetched in this session. */
    val fullReviewsLoaded: Set<String> = emptySet(),
    /** Packages left in an "update everything" run, in the order they will be installed. */
    /** Set while the "this is mobile data" question is on screen; see [StoreViewModel.askOnMeteredNetwork]. */
    val meteredDownloadPrompt: Boolean = false,
    /**
     * The queue row whose "the source could not confirm this file" question is on screen.
     *
     * Held here rather than on a screen because the row it belongs to shows up on Home, Search,
     * the app page, the library and the queue, and the question is the same one everywhere.
     */
    val unverifiedSource: UnverifiedSourcePrompt? = null,
    val installAllRemaining: List<String> = emptyList(),
    /** The app whose confirmation dialog is up, if the queue is working through a batch. */
    val installAllCurrent: String? = null,
    /**
     * Catalogue icons for packages the queue is carrying.
     *
     * Queue rows hold no icon and an app that is not installed has none on the device either, so
     * "ready to install" showed a package name next to an empty square.
     */
    val packageIcons: Map<String, String> = emptyMap()
)

class StoreViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StoreRepository(application)
    val settingsRepository = dev.wystore.settings.SettingsRepository(application)
    private val source = RuStoreSource.getInstance(application)
    private val githubSource = GitHubReleaseSource(application)
    private val installer = RootInstaller()
    private val queueRepository = QueueRepository.getInstance(application)
    private val selfUpdateChecker = SelfUpdateChecker(application)
    private val autoInstallStore = dev.wystore.updates.AutoInstallStore(application)

    /** The app an "update all" run is waiting on right now, or null when nothing is in flight. */
    private var installAllCurrent: String? = null

    /** Whether [installAllCurrent] has already been handed to Android's installer. */
    private var installAllHandedOver: Boolean = false
    private val catalogRepository = CatalogRepository.getInstance(application)
    val queueCoordinator = dev.wystore.updates.QueueCoordinator(application)
    val offeredNext = queueCoordinator.offeredNext
    private val workManager = WorkManager.getInstance(application)

    /**
     * Held as fields on purpose.
     *
     * `getWorkInfosForUniqueWorkLiveData` builds a new LiveData on every call. Observing the
     * result of an inline call left nothing holding it, so it could be collected together with its
     * observer — and `onCleared` then removed the observer from a third, freshly built instance
     * that never had it. In practice the check card stayed on "queued" forever and the check
     * button, disabled while a check is active, never came back.
     */
    private val manualCheckWorkInfos =
        workManager.getWorkInfosForUniqueWorkLiveData(UpdateScheduler.MANUAL_CHECK_WORK_NAME)
    private var lastUpdateCheckTerminalId: UUID? = null
    private val updateCheckObserver = Observer<List<WorkInfo>> { infos ->
        // REPLACE leaves the superseded runs in this list, so a run still in flight is what the UI
        // should follow; the newest finished one is the fallback.
        val info = infos.firstOrNull { !it.state.isFinished } ?: infos.lastOrNull() ?: return@Observer
        val progress = info.progress
        val terminal = info.state in setOf(WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED, WorkInfo.State.CANCELLED)
        val status = progress.getString("status") ?: when (info.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> "QUEUED"
            WorkInfo.State.RUNNING -> "CHECKING"
            WorkInfo.State.SUCCEEDED -> "COMPLETE"
            WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> "FAILED"
        }
        val detail = progress.getString("detail")
            ?: info.outputData.getString("detail")
            ?: if (info.state == WorkInfo.State.FAILED) string(R.string.vm_update_check_failed) else null
        val task = UpdateCheckTask(
            status = status,
            detail = detail,
            checked = progress.getInt("checked", info.outputData.getInt("checked", 0)),
            total = progress.getInt("total", info.outputData.getInt("total", 0)),
            updates = progress.getInt("updates", info.outputData.getInt("updates", 0)),
            active = !terminal,
            running = info.state == WorkInfo.State.RUNNING
        )
        _state.update { it.copy(updateCheckTask = task) }
        if (terminal && lastUpdateCheckTerminalId != info.id) {
            lastUpdateCheckTerminalId = info.id
            refreshLibrary()
            detail?.let { summary -> _state.update { it.copy(message = summary) } }
        }
    }
    /** Shorthand for the many user-facing messages this ViewModel puts into the snackbar. */
    private fun string(@androidx.annotation.StringRes id: Int, vararg args: Any): String =
        getApplication<Application>().getString(id, *args)

    private val _state = MutableStateFlow(StoreUiState())
    val state: StateFlow<StoreUiState> = _state.asStateFlow()

    /**
     * The package the Activity should hand to the system installer next.
     *
     * Installing needs an Activity for Android's confirmation dialog, so the ViewModel asks rather
     * than installs. This is what makes "update everything" run through the whole list instead of
     * stopping after the first one.
     */
    private val _installRequest = MutableStateFlow<String?>(null)
    val installRequest: StateFlow<String?> = _installRequest.asStateFlow()

    fun consumeInstallRequest() {
        _installRequest.value = null
    }

    /**
     * An install asked for by a notification's own button rather than from a screen.
     *
     * The pending list is filled by a flow, so opened cold from the shade the state is still empty
     * when the intent arrives and asking straight away would report the APK as missing. This waits
     * for the item to turn up instead, and gives up quietly if it never does.
     */
    fun requestInstallFromNotification(packageName: String?) {
        viewModelScope.launch {
            val pending = withTimeoutOrNull(NOTIFICATION_INSTALL_WAIT_MILLIS) {
                queueRepository.observePendingUpdates().first { list ->
                    if (packageName == null) list.isNotEmpty() else list.any { it.packageName == packageName }
                }
            } ?: return@launch
            _state.update { it.copy(pendingUpdates = pending) }
            // Through the queue, not straight at the Activity: a batch may already be working, and
            // Android confirms one install at a time - two dialogs at once loses one of them.
            if (packageName == null) updateAll() else requestInstall(packageName)
        }
    }

    init {
        viewModelScope.launch {
            queueRepository.observePendingUpdates().collect { pending ->
                _state.update { it.copy(pendingUpdates = pending) }
                resolvePendingIcons(pending)
                requestAutoInstalls(pending)
            }
        }
        viewModelScope.launch {
            queueRepository.observeAll().collect { items ->
                val uiQueue = items.map { item ->
                    InstallQueueItem(
                        id = item.id,
                        packageName = item.packageName,
                        label = item.label,
                        status = item.state.toInstallQueueStatus(),
                        versionName = item.versionName,
                        // Byte counters survive the transfer; showing them past DOWNLOADING leaves a
                        // full progress bar stuck under a finished item.
                        progress = if (
                            item.totalBytes > 0 &&
                            item.state == QueueState.DOWNLOADING
                        ) DownloadProgress(
                            downloadedBytes = item.downloadedBytes,
                            totalBytes = item.totalBytes,
                            artifactIndex = 1,
                            artifactCount = 1
                        ) else null,
                        errorCode = item.errorCode,
                        detail = item.errorDetail
                    )
                }
                // The whole queue is kept: truncating hid older rows from Updates, Search and
                // Details entirely, so a failed item could become unreachable.
                _state.update { it.copy(installQueue = uiQueue) }
                resolveIcons(uiQueue.map { row -> row.packageName })
                advanceBatchIfSettled(uiQueue)
                // A batch that stood down because the slot was busy picks up again once it frees.
                if (installAllCurrent == null &&
                    _state.value.installAllRemaining.isNotEmpty() &&
                    uiQueue.none { row -> row.status.isInFlight }
                ) {
                    startNextBatchInstall()
                }
            }
        }
        viewModelScope.launch {
            settingsRepository.settings.collect { appSettings ->
                _state.update { it.copy(settings = appSettings.toStoreSettings()) }
            }
        }
        // The smart prompt is derived from durable queue states, so it survives process death.
        viewModelScope.launch { runCatching { queueCoordinator.restoreOfferedNext() } }
        // Installs the user declined are still downloaded and verified; they belong in the ready
        // list, not behind an error that offers to fetch them all over again.
        viewModelScope.launch { runCatching { queueRepository.restoreDeclinedInstalls() } }
        refreshLibrary()
        _state.update { it.copy(githubRepositories = repository.githubRepositories()) }
        UpdateScheduler.schedule(application, repository.settings())
        manualCheckWorkInfos.observeForever(updateCheckObserver)
        if (repository.settings().backgroundRootUpdates) checkRoot()
    }

    override fun onCleared() {
        manualCheckWorkInfos.removeObserver(updateCheckObserver)
        super.onCleared()
    }

    private var searchJob: Job? = null

    /**
     * Runs one search at a time.
     *
     * Each call used to start an independent coroutine that wrote its result whenever it happened
     * to finish, so a slow earlier query could land on top of a newer one. The previous request is
     * now cancelled, and only the newest may write state.
     */
    fun search(query: String) {
        val trimmed = query.trim()
        searchJob?.cancel()
        if (trimmed.isBlank()) {
            _state.update {
                it.copy(query = query, searching = false, operation = null, githubSearchResults = emptyList())
            }
            return
        }
        searchJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    query = trimmed,
                    searching = true,
                    operation = string(R.string.vm_searching),
                    message = null,
                    // Local, so GitHub matches appear immediately rather than after the network.
                    githubSearchResults = GitHubCatalog.search(trimmed, it.githubRepositories)
                )
            }
            runCatching { source.search(trimmed) }
                .onSuccess { page ->
                    // Most apps queued by hand were found here, and a queue row has no icon of its
                    // own; search never wrote to the page cache the icons used to come from.
                    catalogRepository.rememberIcons(page.apps)
                    _state.update {
                        it.copy(search = SearchResultPolicy.promoteExactPackage(page, trimmed), searching = false, operation = null)
                    }
                }
                .onFailure { error ->
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    _state.update {
                        it.copy(
                            searching = false,
                            operation = null,
                            message = error.message ?: string(R.string.vm_search_failed)
                        )
                    }
                }
        }
    }

    /**
     * Matches the bundled GitHub catalogue and nothing else.
     *
     * For the source filter set to GitHub: those entries ship with the app, so there is no request
     * to make and no button to press - results follow the typing. Any RuStore page still on screen
     * from an earlier search is cleared, because it is no longer part of what was asked for.
     */
    fun searchLocal(query: String) {
        val trimmed = query.trim()
        searchJob?.cancel()
        _state.update {
            it.copy(
                query = trimmed,
                searching = false,
                operation = null,
                search = null,
                githubSearchResults = if (trimmed.isBlank()) {
                    emptyList()
                } else {
                    GitHubCatalog.search(trimmed, it.githubRepositories)
                }
            )
        }
    }

    /** Appends the next page of the current search. */
    fun searchMore() {
        val current = _state.value
        val page = current.search ?: return
        if (current.searching || current.query.isBlank()) return
        if (SearchResultPolicy.isExhausted(page)) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.update { it.copy(searching = true, operation = string(R.string.vm_searching), message = null) }
            runCatching { source.search(current.query, page.page + 1) }
                .onSuccess { next ->
                    catalogRepository.rememberIcons(next.apps)
                    _state.update {
                        it.copy(
                            search = SearchResultPolicy.appendPage(page, next),
                            searching = false,
                            operation = null
                        )
                    }
                }
                .onFailure { error ->
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    _state.update {
                        it.copy(
                            searching = false,
                            operation = null,
                            message = error.message ?: string(R.string.vm_search_more_failed)
                        )
                    }
                }
        }
    }



    fun openDetails(packageName: String) = viewModelScope.launch {
        _state.value = _state.value.copy(detailsLoading = true, operation = string(R.string.vm_opening_details), message = null)
        runCatching { catalogRepository.details(packageName) }
            .onSuccess { _state.value = _state.value.copy(selected = it, detailsLoading = false, operation = null) }
            .onFailure { _state.value = _state.value.copy(detailsLoading = false, operation = null, message = SourceTextResolver.describe(getApplication(), it) ?: string(R.string.vm_open_details_failed)) }
    }

    /**
     * Fetches every review the source publishes for the open app.
     *
     * The app page embeds a fixed five, so "show more" had nothing to page through. The rest live
     * on a page of their own and are fetched only when the user asks for them.
     */
    fun loadAllReviews() {
        val app = _state.value.selected ?: return
        if (_state.value.reviewsLoading || app.packageName in _state.value.fullReviewsLoaded) return
        _state.update { it.copy(reviewsLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val fetched = runCatching { source.reviews(app.packageName) }
            _state.update { state ->
                val open = state.selected
                if (open == null || open.packageName != app.packageName) {
                    return@update state.copy(reviewsLoading = false)
                }
                val all = fetched.getOrNull()
                state.copy(
                    selected = if (all.isNullOrEmpty()) open else open.copy(
                        // The page's own five come first and are already on screen; the rest are
                        // appended so nothing the user is looking at jumps.
                        reviews = (open.reviews + all).distinctBy {
                            listOf(it.author, it.publishedAt, it.text)
                        }
                    ),
                    reviewsLoading = false,
                    fullReviewsLoaded = state.fullReviewsLoaded + app.packageName,
                    message = fetched.exceptionOrNull()
                        ?.let { SourceTextResolver.describe(getApplication(), it) }
                        ?: state.message
                )
            }
        }
    }

    fun clearDetails() {
        _state.value = _state.value.copy(selected = null)
    }

    /**
     * Re-reads the device state. The package scan itself runs off the main thread: it is a full
     * `getInstalledPackages` with a label lookup and a certificate digest per app, and this used to
     * run inline on every resume and every broadcast.
     */
    fun refreshLibrary(reportConfirmed: Boolean = false) {
        viewModelScope.launch {
            val installed = withContext(Dispatchers.IO) { repository.installedApps() }
            val managed = withContext(Dispatchers.IO) {
                repository.retainManagedInstalled(installed.mapTo(mutableSetOf()) { it.packageName })
            }
            val pending = _state.value.pendingUpdates
            val confirmedPackages = PendingUpdateCatalog.confirmedPackages(pending, installed)
            val confirmed = pending.filter { it.packageName in confirmedPackages }
            confirmed.forEach(::finalizePendingUpdate)
            _state.value = _state.value.copy(
                installed = installed,
                managed = managed,
                settings = repository.settings(),
                lastUpdateCheck = repository.lastUpdateCheck(),
                message = if (reportConfirmed && confirmed.isNotEmpty()) {
                    string(R.string.vm_installed, confirmed.last().label)
                } else {
                    _state.value.message
                }
            )
            refreshPendingUpdates()
        }
    }

    fun checkForUpdates(packageName: String? = null) {
        UpdateScheduler.checkNow(getApplication(), repository.settings(), packageName)
        _state.update {
            it.copy(
                updateCheckTask = UpdateCheckTask(
                    "QUEUED",
                    if (packageName == null) string(R.string.vm_check_queued_all) else string(R.string.vm_check_queued_one),
                    active = true
                ),
                message = null
            )
        }
    }

    fun checkRoot() = viewModelScope.launch {
        val available = installer.isAvailable()
        if (!available && repository.settings().backgroundRootUpdates) {
            val settings = repository.settings().copy(backgroundRootUpdates = false)
            repository.saveSettings(settings)
            UpdateScheduler.schedule(getApplication(), settings)
            _state.value = _state.value.copy(rootAvailable = false, settings = settings)
        } else {
            _state.value = _state.value.copy(rootAvailable = available)
        }
    }

    fun loadGitHubRepository(input: String) = viewModelScope.launch {
        runCatching { githubSource.parseRepository(input) }
            .onSuccess { repo ->
                repository.saveGithubRepository(repo)
                _state.value = _state.value.copy(
                    githubRepositories = repository.githubRepositories(),
                    githubSearchResults = GitHubCatalog.search(_state.value.query, repository.githubRepositories()),
                    githubActiveRepository = repo,
                    githubLoading = true,
                    githubReleases = emptyList(),
                    message = null
                )
                runCatching { githubSource.releases(repo) }
                    .onSuccess { releases -> _state.value = _state.value.copy(githubLoading = false, githubReleases = releases) }
                    .onFailure { error -> _state.value = _state.value.copy(githubLoading = false, message = error.message ?: string(R.string.vm_github_releases_failed)) }
            }
            .onFailure { error -> _state.value = _state.value.copy(message = error.message ?: string(R.string.vm_github_bad_url)) }
    }

    /**
     * Opens the store-style page for a catalogue entry: repository metadata and releases are
     * fetched together so the page can show version, size and release notes like a store listing.
     */
    fun openGitHubApp(entry: GitHubCatalogEntry) {
        _state.update {
            it.copy(githubApp = GitHubAppUiState(entry = entry, loading = true), githubActiveRepository = entry.repository)
        }
        viewModelScope.launch {
            val info = runCatching { githubSource.repositoryInfo(entry.repository) }
            val releases = runCatching { githubSource.releases(entry.repository) }
            // Optional: most projects publish these under fastlane metadata, many do not.
            val screenshots = runCatching { githubSource.screenshots(entry.repository) }
                .getOrDefault(emptyList())
            val failure = info.exceptionOrNull() ?: releases.exceptionOrNull()
            _state.update {
                it.copy(
                    githubApp = it.githubApp.copy(
                        info = info.getOrNull()?.copy(screenshots = screenshots),
                        releases = releases.getOrDefault(emptyList()),
                        loading = false,
                        // Metadata alone failing is not fatal: releases are what the install needs.
                        error = if (releases.isFailure) {
                            failure?.message ?: string(R.string.vm_github_releases_failed)
                        } else null
                    ),
                    githubReleases = releases.getOrDefault(emptyList())
                )
            }
        }
    }

    fun retryGitHubApp() {
        _state.value.githubApp.entry?.let(::openGitHubApp)
    }

    fun closeGitHubApp() {
        _state.update { it.copy(githubApp = GitHubAppUiState()) }
    }

    fun openGitHubRelease(release: GitHubRelease) {
        _state.value = _state.value.copy(githubSelectedRelease = release)
    }

    fun closeGitHubRelease() {
        _state.value = _state.value.copy(githubSelectedRelease = null)
    }

    fun removeGitHubRepository(repositoryToRemove: GitHubRepository) {
        repository.removeGithubRepository(repositoryToRemove)
        _state.value = _state.value.copy(
            githubRepositories = repository.githubRepositories(),
            githubActiveRepository = _state.value.githubActiveRepository?.takeUnless { it == repositoryToRemove },
            githubReleases = if (_state.value.githubActiveRepository == repositoryToRemove) emptyList() else _state.value.githubReleases
        )
    }

    fun installGitHubAsset(asset: GitHubAsset) {
        val repositorySource = _state.value.githubActiveRepository
        if (repositorySource == null) {
            _state.value = _state.value.copy(message = getApplication<Application>().getString(R.string.msg_github_open_repo_first))
            return
        }
        askOnMeteredNetwork {
            GitHubInstallScheduler.enqueue(getApplication(), asset, repositorySource, _state.value.githubSelectedRelease?.id)
            _state.value = _state.value.copy(
                githubInstall = InstallQueueItem(
                    id = asset.id.toString(),
                    packageName = "",
                    label = asset.name,
                    status = InstallQueueStatus.QUEUED
                ),
                message = string(R.string.vm_github_queued, asset.name)
            )
        }
    }

    fun saveSettings(settings: StoreSettings) {
        val effective = settings.copy(
            backgroundRootUpdates = settings.backgroundRootUpdates && _state.value.rootAvailable == true,
            rootSilentInstallEnabled = settings.rootSilentInstallEnabled && _state.value.rootAvailable == true
        )
        repository.saveSettings(effective)
        // The yes was given about one setting; changing it makes the yes meaningless either way.
        MeteredDownloadConsent.forget()
        viewModelScope.launch {
            settingsRepository.update { effective.toAppSettings() }
        }
        UpdateScheduler.schedule(getApplication(), effective)
        val announce = dev.wystore.settings.SettingsSaveAnnouncement
            .announces(_state.value.settings, effective)
        _state.value = _state.value.copy(
            settings = effective,
            message = if (announce) {
                getApplication<Application>().getString(R.string.msg_settings_saved)
            } else {
                null
            }
        )
    }

    fun pendingUpdate(packageName: String): PendingUpdate? = _state.value.pendingUpdates.firstOrNull { it.packageName == packageName }

    fun installedApp(packageName: String): InstalledApp? = repository.installedApps().firstOrNull { it.packageName == packageName }

    /**
     * Throws away a downloaded APK the user does not want after all.
     *
     * The only thing offered on a "ready to install" row was Install, so downloading something by
     * mistake left it on the device with no way out except installing it.
     */
    fun discardPendingUpdate(packageName: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                queueRepository.remove(packageName)
                PendingUpdateNotifier(getApplication()).refresh()
            }
            val pending = withContext(Dispatchers.IO) { queueRepository.getPendingUpdates() }
            _state.update { it.copy(pendingUpdates = pending) }
        }
    }

    fun refreshPendingUpdates() {
        viewModelScope.launch(Dispatchers.IO) {
            val pending = queueRepository.getPendingUpdates()
            _state.update { it.copy(pendingUpdates = pending) }
        }
    }

    fun reportInstallPermissionRequired(packageName: String? = null) {
        // Every other install in the batch would stop at the same permission, so the batch ends
        // rather than marching through the list raising the same message for each app.
        cancelInstallAll()
        // Recorded durably as well, so the install can resume after the user returns from Android
        // Settings even if the process was killed while they were away.
        if (packageName != null) {
            viewModelScope.launch { queueRepository.markAwaitingUnknownSources(packageName) }
        }
        _state.update { it.copy(message = getApplication<Application>().getString(R.string.msg_install_permission_required)) }
    }

    /** Packages parked waiting for install-from-unknown-sources, restored from the durable queue. */
    suspend fun packagesAwaitingUnknownSources(): List<String> =
        queueRepository.awaitingUnknownSources().map { it.packageName }

    suspend fun clearAwaitingUnknownSources(packageName: String) {
        queueRepository.clearAwaitingUnknownSources(packageName)
    }

    /**
     * The install never reached Android because the queue's single slot was busy.
     *
     * The row is back to waiting, so the app it was queued behind must not keep waiting on it: the
     * package goes to the head of the batch and the batch stands down until the slot frees.
     */
    fun reportInstallDeferred(packageName: String) {
        installAllCurrent = null
        installAllHandedOver = false
        _state.update {
            it.copy(
                installAllCurrent = null,
                installAllRemaining = listOf(packageName) + it.installAllRemaining.filterNot { p -> p == packageName }
            )
        }
    }

    /**
     * The install was never handed over and never will be for this app - the archive did not
     * verify, or Android refused to take it. The rest of the batch is not its fault.
     */
    private fun abandonCurrentBatchInstall() {
        if (installAllCurrent == null) return
        installAllCurrent = null
        installAllHandedOver = false
        _state.update { it.copy(installAllCurrent = null) }
        startNextBatchInstall()
    }

    fun reportInstallStarted(packageName: String) {
        val label = pendingUpdate(packageName)?.label ?: packageName
        _state.update { it.copy(message = getApplication<Application>().getString(R.string.msg_install_started, label)) }
    }

    fun reportInstallLaunchFailure(message: String) {
        abandonCurrentBatchInstall()
        _state.update { it.copy(message = message) }
    }

    /**
     * Drops a downloaded update whose verified identity was never persisted. Such an item cannot be
     * re-checked against what verification actually read at download time, so it is removed and the
     * user is told to download it again instead of being offered an unverifiable install.
     */
    fun discardUnverifiablePendingUpdate(packageName: String) {
        abandonCurrentBatchInstall()
        val label = pendingUpdate(packageName)?.label ?: packageName
        viewModelScope.launch {
            withContext(Dispatchers.IO) { queueRepository.remove(packageName) }
            withContext(Dispatchers.IO) { PendingUpdateNotifier(getApplication()).refresh() }
            val pending = withContext(Dispatchers.IO) { queueRepository.getPendingUpdates() }
            _state.update {
                it.copy(
                    pendingUpdates = pending,
                    message = getApplication<Application>().getString(
                        R.string.msg_install_needs_redownload,
                        label
                    )
                )
            }
        }
    }

    fun permissionSnapshot() = queueCoordinator.permissionSnapshot()

    fun startQueue() {
        askOnMeteredNetwork { viewModelScope.launch { queueCoordinator.startQueue() } }
    }

    /**
     * Acts on everything the queue holds, in order.
     *
     * "Update all" used to call [startQueue], which looks for the next item to *download*. Once the
     * updates were already downloaded — which is exactly when the button appears — there was
     * nothing to download and the button did nothing at all. Downloaded updates are installed one
     * after another; anything still waiting to be fetched starts fetching.
     */
    fun updateAll() {
        // Added to what the batch is already carrying rather than replacing it: a batch running
        // when this is pressed would otherwise lose its remaining list and rebuild it from every
        // downloaded update - including the one whose dialog the user had just dismissed.
        _state.update { state ->
            val queued = state.installAllRemaining
            state.copy(
                installAllRemaining = queued + state.pendingUpdates
                    .map { it.packageName }
                    .filter { it !in queued && it != installAllCurrent }
            )
        }
        startNextBatchInstall()
        // Installing what is already downloaded costs nothing; only the fetching part is asked
        // about, and only when there is in fact something left to fetch.
        askOnMeteredNetwork {
            viewModelScope.launch { runCatching { queueCoordinator.startQueue() } }
        }
    }

    /**
     * Looks up catalogue icons for packages the queue mentions.
     *
     * Only the ones not resolved yet, and only from what the catalogue cache already holds: this
     * runs on every queue change and must not turn into a request per row.
     */
    /**
     * Icons for the downloaded-and-waiting rows.
     *
     * A GitHub release adopts the package name written inside its APK, which is in no catalogue
     * this app caches and belongs to nothing installed yet - so those rows were the only ones in
     * "ready to install" with an empty grey tile. The row still remembers which repository it came
     * from, and the repository has a picture.
     */
    private fun resolvePendingIcons(pending: List<PendingUpdate>) {
        resolveIcons(pending.map { update -> update.packageName })
        val fromGitHub = pending.mapNotNull { update ->
            val repository = update.githubRepository ?: return@mapNotNull null
            dev.wystore.data.GitHubCatalog.find(repository.displayName)
                ?.iconUrl
                ?.let { update.packageName to it }
        }
        if (fromGitHub.isEmpty()) return
        // Whatever the catalogue cache already knows wins; this only fills the gaps.
        _state.update { it.copy(packageIcons = fromGitHub.toMap() + it.packageIcons) }
    }

    /**
     * Packages already looked up, however that ended.
     *
     * This runs on every queue change, and a download reports progress twice a second: without it,
     * a package nothing has an icon for was looked up again on every one of those - and looked up
     * again forever, since the answer never changed.
     */
    private val iconLookupsAttempted = mutableSetOf<String>()

    private fun resolveIcons(packages: List<String>) {
        val known = _state.value.packageIcons
        val missing = packages.distinct()
            .filter { it.isNotBlank() && it !in known && it !in iconLookupsAttempted }
        if (missing.isEmpty()) return
        iconLookupsAttempted += missing
        viewModelScope.launch(Dispatchers.IO) {
            val found = missing.mapNotNull { packageName ->
                val icon = catalogRepository.cachedIcon(packageName)
                    ?: dev.wystore.data.GitHubCatalog.findByPlaceholder(packageName)?.iconUrl
                    ?: fetchIcon(packageName)
                icon?.let { packageName to it }
            }
            if (found.isEmpty()) return@launch
            _state.update { it.copy(packageIcons = it.packageIcons + found) }
        }
    }

    /**
     * The source's own picture for a package no cache knows.
     *
     * Every app in the catalogue has one, so a blank tile means nobody has asked yet rather than
     * that there is nothing to show. Asked once per package per run of the app, and remembered, so
     * this is a single request for a row that would otherwise stay blank for good.
     */
    private suspend fun fetchIcon(packageName: String): String? = runCatching {
        val app = source.details(packageName, includeReviews = false)
        app.iconUrl?.takeIf { it.isNotBlank() }?.also { catalogRepository.rememberIcons(listOf(app)) }
    }.getOrNull()

    /** Hands the Activity the next install of a batch, or ends the batch. */
    private fun startNextBatchInstall() {
        if (installAllCurrent != null) return
        val stillPending = _state.value.pendingUpdates.mapTo(mutableSetOf()) { it.packageName }
        val remaining = _state.value.installAllRemaining
        val next = InstallBatchPolicy.next(remaining, stillPending)
        _state.update { it.copy(installAllRemaining = InstallBatchPolicy.remainingAfter(remaining, next)) }
        if (next == null) return
        installAllCurrent = next
        installAllHandedOver = false
        _state.update { it.copy(installAllCurrent = next) }
        // Spent the moment the installer is asked. Cleared here rather than for the whole batch at
        // once, because the batch list itself lives in memory: whatever has not been offered yet
        // must still be there if the app is closed before its turn comes.
        runCatching { autoInstallStore.clear(next) }
        // The user already answered "all of them", so the per-item prompt is not asked again.
        viewModelScope.launch { runCatching { queueCoordinator.dismissOfferedNext() } }
        _installRequest.value = next
    }

    /**
     * A tap on "Install" for something already downloaded.
     *
     * It used to go straight to the Activity, so pressing Install on several cards in a row threw
     * several confirmation dialogs at Android at once and all but one were lost. Taps join the same
     * queue "update all" walks: one dialog at a time, and the next is handed over as soon as the
     * system is free again.
     */
    fun requestInstall(packageName: String) {
        if (packageName.isBlank()) return
        if (packageName == installAllCurrent || packageName in _state.value.installAllRemaining) return
        // Only something actually downloaded joins the batch. Taking a package the queue does not
        // have would clear the whole list on the next turn, since a batch with nothing installable
        // in it is a finished batch - one stray tap would throw away every install queued behind.
        if (_state.value.pendingUpdates.none { it.packageName == packageName }) {
            _state.update { it.copy(message = string(R.string.msg_install_artifact_missing)) }
            return
        }
        _state.update { it.copy(installAllRemaining = it.installAllRemaining + packageName) }
        startNextBatchInstall()
    }

    /**
     * Moves the batch on once the app it is waiting for has settled.
     *
     * Driven by the queue rather than by the Activity's install callback: a session install reports
     * to [dev.wystore.updates.InstallResultReceiver], so that callback never fires for it and the
     * batch stopped after the first app.
     */
    private fun advanceBatchIfSettled(queue: List<InstallQueueItem>) {
        val current = installAllCurrent ?: return
        val statuses = queue.filter { it.packageName == current }.map { it.status }
        if (InstallBatchPolicy.isHandedOver(statuses)) installAllHandedOver = true
        if (!InstallBatchPolicy.isSettled(statuses, installAllHandedOver)) return
        installAllCurrent = null
        installAllHandedOver = false
        _state.update { it.copy(installAllCurrent = null) }
        startNextBatchInstall()
    }

    fun cancelInstallAll() {
        installAllCurrent = null
        installAllHandedOver = false
        _state.update { it.copy(installAllRemaining = emptyList(), installAllCurrent = null) }
    }

    /**
     * Carries out the installs the download worker recorded for "install as soon as it is
     * downloaded".
     *
     * The download usually finishes with the app in the background, where Android's confirmation
     * dialog cannot be shown, so the worker only writes the request down. This is where it is
     * turned into an actual install, the next time the app is on screen.
     */
    private fun requestAutoInstalls(pending: List<PendingUpdate>) {
        if (_installRequest.value != null) return
        val requested = runCatching { autoInstallStore.requested() }.getOrDefault(emptySet())
        if (requested.isEmpty()) return
        val additions = InstallBatchPolicy.autoInstallAdditions(
            requested = requested,
            pending = pending.map { it.packageName },
            alreadyQueued = _state.value.installAllRemaining,
            current = installAllCurrent
        )
        if (additions.isEmpty()) return
        _state.update { it.copy(installAllRemaining = it.installAllRemaining + additions) }
        // Not handed to the Activity directly: that left the batch with no current item, so
        // nothing ever advanced it and only the first download was offered for installing.
        startNextBatchInstall()
    }

    /**
     * Looks for a newer Wy Store on its own public repository.
     *
     * The result is only a statement about versions; whether the build is installable is decided
     * later by the same signature check every other app goes through.
     */
    fun checkSelfUpdate() {
        _state.update { it.copy(selfUpdate = SelfUpdateStatus.Checking) }
        viewModelScope.launch {
            val status = withContext(Dispatchers.IO) { selfUpdateChecker.check() }
            _state.update { it.copy(selfUpdate = status) }
        }
    }

    /** Queues the release found by [checkSelfUpdate] and starts the queue working on it. */
    fun installSelfUpdate() {
        val available = _state.value.selfUpdate as? SelfUpdateStatus.Available ?: return
        askOnMeteredNetwork {
            viewModelScope.launch {
                withContext(Dispatchers.IO) { selfUpdateChecker.enqueue(available.release) }
                queueCoordinator.startQueue()
                _state.update { it.copy(message = string(R.string.about_update_queued)) }
            }
        }
    }

    /**
     * The download the "this is mobile data" question is holding, or null when nothing is asked.
     *
     * Held rather than described: every caller phrases its own download differently, and the answer
     * has to start exactly what the button would have started.
     */
    private var meteredDownloadAction: (() -> Unit)? = null

    /**
     * Runs [start], or asks first when the connection charges for it and the settings say Wi-Fi.
     *
     * A transfer the user asked for has always run on whatever connection there was - waiting for
     * Wi-Fi turns "Install" into a row that sits in the queue - so the Wi-Fi-only setting was
     * quietly spent instead of honoured. Both can be true if the question is put.
     */
    private fun askOnMeteredNetwork(start: () -> Unit) {
        // Read rather than taken from the UI state: the state is refreshed by screens, and a
        // question about the network settings must be asked of the settings as they are now.
        val settings = repository.settings()
        if (!MeteredDownloadPolicy.requiresConsent(
                wifiOnly = settings.wifiOnly,
                allowMobileData = settings.allowMobileData,
                isMetered = isActiveNetworkMetered(getApplication()),
                allowedThisSession = MeteredDownloadConsent.isAllowedThisSession()
            )
        ) {
            start()
            return
        }
        meteredDownloadAction = start
        _state.update { it.copy(meteredDownloadPrompt = true) }
    }

    /**
     * "Download anyway". [always] is the checkbox: it moves the setting itself to "mobile data is
     * allowed", which is what makes background updates use it too. Without it the yes lasts as long
     * as the app is running.
     */
    fun confirmMeteredDownload(always: Boolean) {
        val start = meteredDownloadAction
        meteredDownloadAction = null
        _state.update { it.copy(meteredDownloadPrompt = false) }
        if (always) {
            saveSettings(repository.settings().copy(wifiOnly = false, allowMobileData = true))
        }
        MeteredDownloadConsent.allowForThisSession()
        start?.invoke()
    }

    /** "Not now": the download is not started, and the next one asks again. */
    fun cancelMeteredDownload() {
        meteredDownloadAction = null
        _state.update { it.copy(meteredDownloadPrompt = false) }
    }

    fun queueDownload(id: String) {
        askOnMeteredNetwork { viewModelScope.launch { queueCoordinator.download(id) } }
    }

    fun queueSkip(id: String) {
        viewModelScope.launch { queueCoordinator.skip(id) }
    }

    /** Stops the transfer on this row and keeps its bytes. */
    fun queuePause(id: String) {
        viewModelScope.launch { runCatching { queueCoordinator.pause(id) } }
    }

    /** Carries this row on from the bytes it already has. */
    fun queueResume(id: String) {
        askOnMeteredNetwork {
            viewModelScope.launch { runCatching { queueCoordinator.resume(id) } }
        }
    }

    /**
     * Retries a stopped queue item. The consolidated error notification is rebuilt from what is
     * still failing, so acting on the notification makes it go away instead of leaving a stale
     * entry in the shade.
     */
    fun queueRetry(id: String) {
        askOnMeteredNetwork {
            viewModelScope.launch {
                queueCoordinator.retry(id)
                refreshErrorNotification()
            }
        }
    }

    /**
     * Drops a stopped row and whatever it downloaded.
     *
     * Skip and cancel both leave the row on the Updates screen as "canceled" with Retry as the only
     * offer, so an item the user had finished with stayed in the queue for good and its archive
     * stayed on disk with it.
     */
    fun queueDiscard(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { queueRepository.discard(id) }
            refreshErrorNotification()
        }
    }

    fun queueCancel(id: String) {
        viewModelScope.launch {
            queueCoordinator.cancel(id)
            refreshErrorNotification()
        }
    }

    /**
     * Rebuilds the consolidated error notification from what is still failing.
     *
     * Acting on a failure inside the app has to make its notification go away; otherwise the shade
     * keeps advertising a problem the user has already dealt with.
     */
    private suspend fun refreshErrorNotification() {
        withContext(Dispatchers.IO) {
            runCatching {
                dev.wystore.background.NotificationCoordinator(getApplication())
                    .publishErrors(queueRepository.failedSnapshots())
            }
        }
    }

    fun queueAcceptNext(installer: dev.wystore.updates.UserConfirmedInstaller) {
        viewModelScope.launch { queueCoordinator.acceptNext(installer) }
    }

    fun queueDismissOfferedNext() {
        viewModelScope.launch { queueCoordinator.dismissOfferedNext() }
    }

    fun handlePackageInstallResult(packageName: String, success: Boolean, message: String?) {
        viewModelScope.launch {
            val dao = dev.wystore.data.local.WyStoreDatabase.getInstance(getApplication()).updateQueueDao
            val entity = dao.getByPackage(packageName).firstOrNull()
            if (entity != null) {
                queueCoordinator.onInstallResult(entity.id, success, message)
            }
        }
        val pending = pendingUpdate(packageName)
        val installed = repository.installedApps().firstOrNull { it.packageName == packageName }
        val confirmed = pending?.takeIf { success && InstalledUpdateMatcher.matches(it, installed) }
        if (confirmed != null) {
            finalizePendingUpdate(confirmed)
            refreshLibrary()
            _state.update { it.copy(message = string(R.string.vm_installed, confirmed.label)) }
        } else {
            refreshLibrary()
            val alreadyConfirmed = success && pending == null && installed != null &&
                repository.managedApps().any { it.packageName == packageName && it.pinnedDigests == installed.signingDigests }
            val detail = when {
                alreadyConfirmed -> string(R.string.vm_installed, installed.label)
                success -> string(R.string.vm_install_mismatch)
                else -> message
            }
            _state.update { it.copy(message = detail?.takeIf { text -> text.isNotBlank() } ?: string(R.string.vm_install_incomplete)) }
        }
    }

    private fun finalizePendingUpdate(confirmed: PendingUpdate) {
        val current = repository.managedApps().firstOrNull { it.packageName == confirmed.packageName }
        val now = System.currentTimeMillis()
        repository.saveManaged(
            ManagedApp(
                packageName = confirmed.packageName,
                label = confirmed.label,
                pinnedDigests = confirmed.signingDigests,
                // On, whatever the app was installed from. Adopting is asking Wy Store to look
                // after the app; starting it switched off meant a Google app was adopted and then
                // never checked, so its page never learned there was a version to move to.
                autoUpdate = current?.autoUpdate ?: true,
                forceWyStore = current?.forceWyStore ?: false,
                addedAt = current?.addedAt ?: now,
                lastUpdatedAt = now,
                source = confirmed.source,
                githubRepository = confirmed.githubRepository,
                githubReleaseId = confirmed.githubReleaseId
            )
        )
        viewModelScope.launch(Dispatchers.IO) {
            queueRepository.remove(confirmed.packageName)
            PendingUpdateNotifier(getApplication()).refresh()
        }
    }

    fun exportBackupJson(): String = repository.exportBackupJson()

    fun exportBackupToUri(uri: android.net.Uri) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            val json = repository.exportBackupJson()
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use { output ->
                output.write(json.toByteArray(Charsets.UTF_8))
            } ?: error(string(R.string.vm_backup_write_failed))
        }.onSuccess {
            // Remembered, so every later change to the library reaches this file by itself. A copy
            // that is only as current as the last time someone thought to export is not a copy.
            BackupLocation(getApplication()).remember(uri)
            _state.update { it.copy(message = string(R.string.vm_backup_saved)) }
        }.onFailure { error ->
            _state.update { it.copy(message = error.message ?: string(R.string.vm_backup_export_failed)) }
        }
    }

    fun importBackupFromUri(uri: android.net.Uri, merge: Boolean = false) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            val json = getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader(Charsets.UTF_8).readText()
            } ?: error(string(R.string.vm_backup_read_failed))
            repository.restoreBackupJson(json, merge)
        }.onSuccess { summary ->
            refreshLibrary()
            _state.update { it.copy(
                githubRepositories = repository.githubRepositories(),
                settings = repository.settings(),
                message = summary.message + restoreCaveat()
            ) }
        }.onFailure { error ->
            _state.update { it.copy(message = error.message ?: string(R.string.vm_backup_import_failed)) }
        }
    }

    /**
     * What a backup cannot carry, said where the person is when they find out.
     *
     * A backup restores what the app knows. It cannot restore what Android granted the app that
     * wrote it: an exemption from battery optimisation belongs to an application id, and the whole
     * reason to be restoring is usually that the id changed. Without it the background check still
     * runs, but when the system feels like it rather than on the schedule the settings promise -
     * and nothing on screen would have said so.
     */
    private fun restoreCaveat(): String {
        val granted = runCatching { queueCoordinator.permissionSnapshot().batteryOptimizationsIgnored }
            .getOrDefault(true)
        return if (granted) "" else " " + string(R.string.msg_backup_restore_battery)
    }

    fun restoreBackupJson(json: String, merge: Boolean = false) {
        runCatching { repository.restoreBackupJson(json, merge) }
            .onSuccess { summary ->
                refreshLibrary()
                _state.update { it.copy(
                    githubRepositories = repository.githubRepositories(),
                    settings = repository.settings(),
                    message = summary.message
                ) }
            }
            .onFailure { error ->
                _state.update { it.copy(message = error.message ?: string(R.string.vm_backup_restore_failed)) }
            }
    }

    fun setManaged(app: InstalledApp, enabled: Boolean) {
        if (enabled) {
            if (app.signingDigests.isEmpty()) {
                _state.value = _state.value.copy(message = string(R.string.vm_signature_read_failed))
                return
            }
            repository.saveManaged(
                ManagedApp(app.packageName, app.label, app.signingDigests, autoUpdate = true)
            )
        } else {
            repository.removeManaged(app.packageName)
        }
        refreshLibrary()
    }

    /**
     * Looks for the app Wy Store could install in place of a Google-installed one.
     *
     * The package name is asked for first, because the store carrying that exact package is an
     * identity rather than a resemblance and needs no choosing. Only when it does not is the name
     * searched, and then the user picks from what came back.
     */
    fun beginGoogleAdoption(app: InstalledApp) {
        _state.update { it.copy(googleAdoption = GoogleAdoptionPrompt(app = app), message = null) }
        viewModelScope.launch {
            val exact = withContext(Dispatchers.IO) {
                runCatching { source.details(app.packageName, includeReviews = false) }.getOrNull()
            }
            val byName = if (exact != null) emptyList() else withContext(Dispatchers.IO) {
                runCatching { source.search(app.label, 1).apps }.getOrDefault(emptyList())
            }
            val candidates = GoogleAdoptionPolicy.candidates(app.packageName, exact, byName)
            _state.update { state ->
                val prompt = state.googleAdoption?.takeIf { it.app.packageName == app.packageName }
                    ?: return@update state
                state.copy(googleAdoption = prompt.copy(loading = false, candidates = candidates))
            }
        }
    }

    fun dismissGoogleAdoption() {
        _state.update { it.copy(googleAdoption = null) }
    }

    /**
     * Records the handover and closes the prompt. The caller then opens Android's uninstall dialog;
     * the install follows from the package-removed receiver, because the app may not survive the
     * trip through that dialog.
     */
    fun confirmGoogleAdoption(candidate: AdoptionCandidate) {
        val prompt = _state.value.googleAdoption ?: return
        PendingReinstallStore(getApplication()).save(
            PendingReinstall(
                removedPackageName = prompt.app.packageName,
                installPackageName = candidate.packageName,
                label = candidate.label,
                startedAt = System.currentTimeMillis()
            )
        )
        _state.update { it.copy(googleAdoption = null) }
    }

    /**
     * Records a handover that has to go through a removal: the app on screen is uninstalled and
     * the catalogue's copy of the same package takes its place.
     *
     * The same store the Google handover writes to, for the same reason - the caller then opens
     * Android's uninstall dialog, and the intent to install has to outlive the trip through it.
     * The caller opens that dialog; this only writes the note, so a cancelled removal installs
     * nothing.
     */
    fun confirmReplaceInstall(packageName: String, label: String) {
        PendingReinstallStore(getApplication()).save(
            PendingReinstall(
                removedPackageName = packageName,
                installPackageName = packageName,
                label = label,
                startedAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * Records a handover that starts from a stopped queue row, so the same source is used again
     * once the app has been removed. The caller opens Android's uninstall dialog.
     */
    fun confirmReplaceFromQueue(item: InstallQueueItem) {
        PendingReinstallStore(getApplication()).save(
            PendingReinstall(
                removedPackageName = item.packageName,
                installPackageName = item.packageName,
                label = item.label.ifBlank { item.packageName },
                startedAt = System.currentTimeMillis(),
                queueId = item.id
            )
        )
    }

    fun updateManaged(app: ManagedApp) {
        repository.saveManaged(app)
        refreshLibrary()
    }

    /**
     * "Check" pressed on one app's row.
     *
     * An app with auto-update off used to be refused outright here, which read as the button being
     * broken: the switch is about what happens unasked, and this is someone asking. The check runs,
     * and whatever it finds is offered to be downloaded and installed by hand - it is still never
     * fetched on its own.
     */
    fun checkManagedApp(app: ManagedApp) {
        if (hasActiveUpdateCheck()) {
            _state.update { it.copy(message = string(R.string.vm_check_already_running)) }
            return
        }
        checkForUpdates(app.packageName)
    }

    fun openManagedGitHubRepository(app: ManagedApp) {
        val repositorySource = app.githubRepository
        if (app.source != dev.wystore.data.ManagedSource.GITHUB || repositorySource == null) {
            _state.update { it.copy(message = string(R.string.vm_no_github_repo)) }
            return
        }
        viewModelScope.launch {
            _state.update {
                it.copy(
                    githubActiveRepository = repositorySource,
                    githubLoading = true,
                    githubReleases = emptyList(),
                    githubSelectedRelease = null,
                    message = null
                )
            }
            runCatching { githubSource.releases(repositorySource) }
                .onSuccess { releases ->
                    _state.update { it.copy(githubLoading = false, githubReleases = releases) }
                }
                .onFailure { error ->
                    _state.update { it.copy(githubLoading = false, message = error.message ?: string(R.string.vm_github_releases_failed)) }
                }
        }
    }

    fun installSelected() {
        _state.value.selected?.let { app ->
            askOnMeteredNetwork {
                clearTransientFailure(app.packageName)
                ManualInstallScheduler.enqueue(getApplication(), app.packageName, app.name, repository.settings())
                _state.value = _state.value.copy(message = string(R.string.vm_queued_app, app.name))
            }
        }
    }

    /**
     * Takes the transfer slot for an app that is waiting in the queue.
     *
     * Addressed by package because that is all a catalogue row knows; the row it belongs to is
     * looked up here. Nothing happens for an app that is not queued - "download now" is an answer
     * to being in a queue, and [quickInstall] is what puts it there.
     */
    fun downloadNow(packageName: String) = withQueueRow(packageName) { id ->
        queueCoordinator.downloadNow(id)
    }

    /** Stops the transfer for this app and keeps the bytes it already has. */
    fun pauseDownload(packageName: String) = withQueueRow(packageName) { id ->
        queueCoordinator.pause(id)
    }

    /** Carries a paused transfer on from the bytes on disk. */
    fun resumeDownload(packageName: String) = withQueueRow(packageName) { id ->
        queueCoordinator.resume(id)
    }

    /**
     * Raises the question about a file the source would not vouch for.
     *
     * Addressed by package, like every other row action: whichever screen the tap came from, the
     * row is found here and the question is answered in one place.
     */
    fun askAboutUnverifiedSource(packageName: String) {
        val row = _state.value.installQueue.firstOrNull { it.packageName == packageName } ?: return
        if (!UnverifiedSourceConsent.isAnswerable(row.errorCode, row.detail)) return
        _state.update {
            it.copy(
                unverifiedSource = UnverifiedSourcePrompt(
                    packageName = row.packageName,
                    label = row.label.ifBlank { row.packageName },
                    queueId = row.id
                )
            )
        }
    }

    /**
     * The same question, asked about an app that is already installed.
     *
     * Nothing has to be fetched to see this one: the source states the fingerprint in its catalogue
     * and the phone knows the signature it installed under. The answer is what lets the app be
     * fetched at all - without it the check skips this app for good, because an update that cannot
     * install is not worth two hundred megabytes.
     */
    fun askAboutUnverifiedInstalled(packageName: String) {
        val app = _state.value.selected?.takeIf { it.packageName == packageName } ?: return
        val installed = _state.value.installed.firstOrNull { it.packageName == packageName } ?: return
        _state.update {
            it.copy(
                unverifiedSource = UnverifiedSourcePrompt(
                    packageName = packageName,
                    label = app.name.ifBlank { packageName },
                    advertisedDigest = app.signatureHint,
                    installedDigest = installed.signingDigests.firstOrNull()
                )
            )
        }
    }

    fun dismissUnverifiedSource() {
        _state.update { it.copy(unverifiedSource = null) }
    }

    /**
     * Records the answer and fetches the file again without that one check.
     *
     * The answer is written against the two fingerprints the refusal was about, not against the
     * app's version, so it covers the file it was given for and nothing else - see
     * [UnverifiedSourceStore].
     */
    fun confirmUnverifiedSource() {
        val prompt = _state.value.unverifiedSource ?: return
        _state.update { it.copy(unverifiedSource = null) }
        val store = UnverifiedSourceStore(getApplication())
        val queueId = prompt.queueId
        if (queueId == null) {
            // Answered from the app's page, before anything was fetched: what is accepted is the
            // signature the phone already carries, and the download is what the button promises.
            store.accept(prompt.packageName, prompt.advertisedDigest, prompt.installedDigest)
            quickInstall(prompt.packageName)
            return
        }
        viewModelScope.launch {
            runCatching {
                // The refusal is written down where the file is refused, so a row that failed under
                // a build that did not do that has nothing to accept yet. The download starts
                // either way rather than leaving a button that does nothing: it is refused again,
                // this time with both fingerprints recorded, and the question comes back answerable.
                store.accept(prompt.packageName)
                queueCoordinator.retry(queueId)
            }
        }
    }

    private fun withQueueRow(packageName: String, block: suspend (String) -> Unit) {
        val id = _state.value.installQueue
            .firstOrNull { it.packageName == packageName }?.id
            ?.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch { runCatching { block(id) } }
    }

    fun quickInstall(packageName: String) {
        if (hasActiveQueueItem(packageName)) return
        askOnMeteredNetwork {
            clearTransientFailure(packageName)
            val label = labelFor(packageName)
            ManualInstallScheduler.enqueue(getApplication(), packageName, label, repository.settings())
            _state.value = _state.value.copy(
                message = if (label == packageName) string(R.string.vm_queued_generic)
                else string(R.string.vm_queued_app, label)
            )
        }
    }

    /**
     * The best name the app already knows for a package.
     *
     * Cards call in with a package name only, and that name is what ended up on the queue row and
     * on every "ready to install" card. Whatever screen the tap came from usually holds the real
     * one already.
     */
    private fun labelFor(packageName: String): String {
        val state = _state.value
        return state.selected?.takeIf { it.packageName == packageName }?.name?.takeIf { it.isNotBlank() }
            ?: state.search?.apps?.firstOrNull { it.packageName == packageName }?.name?.takeIf { it.isNotBlank() }
            ?: state.installed.firstOrNull { it.packageName == packageName }?.label?.takeIf { it.isNotBlank() }
            ?: state.managed.firstOrNull { it.packageName == packageName }?.label?.takeIf { it.isNotBlank() }
            ?: packageName
    }

    fun launchInstalledApp(packageName: String) {
        val intent = getApplication<Application>().packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            _state.update { it.copy(message = string(R.string.vm_no_launch_activity)) }
            return
        }
        runCatching {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            getApplication<Application>().startActivity(intent)
        }.onFailure {
            _state.update { state -> state.copy(message = string(R.string.vm_open_app_failed)) }
        }
    }

    private fun hasActiveUpdateCheck(): Boolean = _state.value.updateCheckTask?.active == true

    private fun clearTransientFailure(packageName: String) {
        _state.update { state -> state.copy(installQueue = state.installQueue.filterNot { it.packageName == packageName && it.status == InstallQueueStatus.FAILED }) }
    }

    private fun hasActiveQueueItem(packageName: String): Boolean = _state.value.installQueue.any {
        it.packageName == packageName && it.status.occupiesQueue
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private companion object {
        /** Long enough for the queue to be read from disk after a cold start, short enough to fail. */
        const val NOTIFICATION_INSTALL_WAIT_MILLIS = 5_000L
    }
}
