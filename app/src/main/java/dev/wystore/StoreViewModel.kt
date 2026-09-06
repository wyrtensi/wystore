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
import dev.wystore.data.RuStoreCompatibility
import dev.wystore.data.UpdateCheckSummary
import dev.wystore.R
import dev.wystore.data.InstallSource
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
import dev.wystore.updates.QueueRepository
import dev.wystore.updates.model.QueueState
import dev.wystore.updates.RuStoreCompatibilityScheduler
import dev.wystore.settings.toStoreSettings
import dev.wystore.settings.toAppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class InstallQueueStatus {
    RESOLVING,
    QUEUED,
    DOWNLOADING,
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
    val progress: DownloadProgress? = null,
    /** Typed reason, so the UI can render a localized message instead of the raw detail text. */
    val errorCode: dev.wystore.updates.model.QueueErrorCode? = null,
    val detail: String? = null
)

data class RuStoreCompatibilityTask(
    val status: String,
    val detail: String? = null,
    val progress: DownloadProgress? = null
)

data class UpdateCheckTask(
    val status: String,
    val detail: String? = null,
    val checked: Int = 0,
    val total: Int = 0,
    val updates: Int = 0,
    val active: Boolean = false
)

data class StoreUiState(
    val query: String = "",
    val search: SearchPage? = null,
    val selected: StoreApp? = null,
    val installed: List<InstalledApp> = emptyList(),
    val managed: List<ManagedApp> = emptyList(),
    val settings: StoreSettings = StoreSettings(),
    val ruStoreCompatibility: RuStoreCompatibility = RuStoreCompatibility(),
    val ruStoreCompatibilityTask: RuStoreCompatibilityTask? = null,
    val updateCheckTask: UpdateCheckTask? = null,
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
    val selfUpdate: SelfUpdateStatus = SelfUpdateStatus.Idle
)

class StoreViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StoreRepository(application)
    val settingsRepository = dev.wystore.settings.SettingsRepository(application)
    private val source = RuStoreSource.getInstance(application)
    private val githubSource = GitHubReleaseSource(application)
    private val installer = RootInstaller()
    private val queueRepository = QueueRepository.getInstance(application)
    private val selfUpdateChecker = SelfUpdateChecker(application)
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
    private val ruStoreCompatibilityWorkInfos =
        workManager.getWorkInfosForUniqueWorkLiveData(RuStoreCompatibilityScheduler.WORK_NAME)
    private var lastUpdateCheckTerminalId: UUID? = null
    private val ruStoreCompatibilityObserver = Observer<List<WorkInfo>> { infos ->
        val info = infos.lastOrNull() ?: return@Observer
        val progress = info.progress
        val status = progress.getString("status") ?: when (info.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> "QUEUED"
            WorkInfo.State.RUNNING -> "PREPARING"
            WorkInfo.State.SUCCEEDED -> "COMPLETE"
            WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> "FAILED"
        }
        val total = progress.getLong("total", 0L)
        val download = if (total > 0L) DownloadProgress(
            downloadedBytes = progress.getLong("downloaded", 0L),
            totalBytes = total,
            artifactIndex = 1,
            artifactCount = 1,
            bytesPerSecond = progress.getLong("speed", 0L)
        ) else null
        val detail = progress.getString("detail") ?: info.outputData.getString("detail")
        _state.update {
            it.copy(
                ruStoreCompatibility = repository.ruStoreCompatibility(),
                ruStoreCompatibilityTask = RuStoreCompatibilityTask(status, detail, download)
            )
        }
    }
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
            active = !terminal
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

    init {
        viewModelScope.launch {
            queueRepository.observePendingUpdates().collect { pending ->
                _state.update { it.copy(pendingUpdates = pending) }
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
            }
        }
        viewModelScope.launch {
            settingsRepository.settings.collect { appSettings ->
                _state.update { it.copy(settings = appSettings.toStoreSettings()) }
            }
        }
        // The smart prompt is derived from durable queue states, so it survives process death.
        viewModelScope.launch { runCatching { queueCoordinator.restoreOfferedNext() } }
        refreshLibrary()
        _state.update { it.copy(githubRepositories = repository.githubRepositories(), ruStoreCompatibility = repository.ruStoreCompatibility()) }
        UpdateScheduler.schedule(application, repository.settings())
        ruStoreCompatibilityWorkInfos.observeForever(ruStoreCompatibilityObserver)
        manualCheckWorkInfos.observeForever(updateCheckObserver)
        if (repository.settings().backgroundRootUpdates) checkRoot()
    }

    override fun onCleared() {
        ruStoreCompatibilityWorkInfos.removeObserver(ruStoreCompatibilityObserver)
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

    fun saveSettings(settings: StoreSettings) {
        val effective = settings.copy(
            backgroundRootUpdates = settings.backgroundRootUpdates && _state.value.rootAvailable == true,
            rootSilentInstallEnabled = settings.rootSilentInstallEnabled && _state.value.rootAvailable == true
        )
        repository.saveSettings(effective)
        viewModelScope.launch {
            settingsRepository.update { effective.toAppSettings() }
        }
        UpdateScheduler.schedule(getApplication(), effective)
        _state.value = _state.value.copy(settings = effective, message = getApplication<Application>().getString(R.string.msg_settings_saved))
    }

    fun pendingUpdate(packageName: String): PendingUpdate? = _state.value.pendingUpdates.firstOrNull { it.packageName == packageName }

    fun installedApp(packageName: String): InstalledApp? = repository.installedApps().firstOrNull { it.packageName == packageName }

    fun refreshPendingUpdates() {
        viewModelScope.launch(Dispatchers.IO) {
            val pending = queueRepository.getPendingUpdates()
            _state.update { it.copy(pendingUpdates = pending) }
        }
    }

    fun reportInstallPermissionRequired(packageName: String? = null) {
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

    fun reportInstallStarted(packageName: String) {
        val label = pendingUpdate(packageName)?.label ?: packageName
        _state.update { it.copy(message = getApplication<Application>().getString(R.string.msg_install_started, label)) }
    }

    fun reportInstallLaunchFailure(message: String) {
        _state.update { it.copy(message = message) }
    }

    /**
     * Drops a downloaded update whose verified identity was never persisted. Such an item cannot be
     * re-checked against what verification actually read at download time, so it is removed and the
     * user is told to download it again instead of being offered an unverifiable install.
     */
    fun discardUnverifiablePendingUpdate(packageName: String) {
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
        viewModelScope.launch { queueCoordinator.startQueue() }
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
        viewModelScope.launch {
            withContext(Dispatchers.IO) { selfUpdateChecker.enqueue(available.release) }
            queueCoordinator.startQueue()
            _state.update { it.copy(message = string(R.string.about_update_queued)) }
        }
    }

    fun queueDownload(id: String) {
        viewModelScope.launch { queueCoordinator.download(id) }
    }

    fun queueSkip(id: String) {
        viewModelScope.launch { queueCoordinator.skip(id) }
    }

    /**
     * Retries a stopped queue item. The consolidated error notification is rebuilt from what is
     * still failing, so acting on the notification makes it go away instead of leaving a stale
     * entry in the shade.
     */
    fun queueRetry(id: String) {
        viewModelScope.launch {
            queueCoordinator.retry(id)
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
                autoUpdate = current?.autoUpdate ?: (confirmed.source == dev.wystore.data.ManagedSource.RUSTORE),
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
                ruStoreCompatibility = repository.ruStoreCompatibility(),
                message = summary.message
            ) }
        }.onFailure { error ->
            _state.update { it.copy(message = error.message ?: string(R.string.vm_backup_import_failed)) }
        }
    }

    fun restoreBackupJson(json: String, merge: Boolean = false) {
        runCatching { repository.restoreBackupJson(json, merge) }
            .onSuccess { summary ->
                refreshLibrary()
                _state.update { it.copy(
                    githubRepositories = repository.githubRepositories(),
                    settings = repository.settings(),
                    ruStoreCompatibility = repository.ruStoreCompatibility(),
                    message = summary.message
                ) }
            }
            .onFailure { error ->
                _state.update { it.copy(message = error.message ?: string(R.string.vm_backup_restore_failed)) }
            }
    }

    fun checkRuStoreCompatibility() {
        RuStoreCompatibilityScheduler.enqueue(getApplication())
        _state.update {
            it.copy(
                ruStoreCompatibilityTask = RuStoreCompatibilityTask("QUEUED", string(R.string.vm_check_queued)),
                message = null
            )
        }
    }

    fun setRuStoreVersionCode(versionCode: Long) {
        if (versionCode <= 0L) {
            _state.update { it.copy(message = string(R.string.vm_version_code_positive)) }
            return
        }
        val compatibility = repository.ruStoreCompatibility().copy(apiVersionCode = versionCode)
        repository.saveRuStoreCompatibility(compatibility)
        _state.update { it.copy(ruStoreCompatibility = compatibility, message = string(R.string.vm_rustore_api_changed)) }
    }

    fun setManaged(app: InstalledApp, enabled: Boolean) {
        if (enabled) {
            if (app.signingDigests.isEmpty()) {
                _state.value = _state.value.copy(message = string(R.string.vm_signature_read_failed))
                return
            }
            repository.saveManaged(ManagedApp(app.packageName, app.label, app.signingDigests, autoUpdate = app.source != dev.wystore.data.InstallSource.GOOGLE_PLAY))
        } else {
            repository.removeManaged(app.packageName)
        }
        refreshLibrary()
    }

    fun updateManaged(app: ManagedApp) {
        repository.saveManaged(app)
        refreshLibrary()
    }

    fun uninstall(app: InstalledApp) = viewModelScope.launch {
        if (!installer.isAvailable()) {
            _state.value = _state.value.copy(message = string(R.string.vm_root_denied))
            return@launch
        }
        _state.value = _state.value.copy(message = null)
        val result = installer.uninstall(app.packageName)
        if (result.success) {
            repository.removeManaged(app.packageName)
            refreshLibrary()
            _state.value = _state.value.copy(message = string(R.string.vm_uninstalled, app.label))
        } else {
            _state.value = _state.value.copy(
                message = RootTextResolver.describe(getApplication(), result)
                    .ifBlank { string(R.string.vm_uninstall_failed, app.label) }
            )
        }
    }

    fun checkManagedApp(app: ManagedApp) {
        if (!app.autoUpdate) {
            _state.update { it.copy(message = string(R.string.vm_autoupdate_off, app.label)) }
            return
        }
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
            clearTransientFailure(app.packageName)
            ManualInstallScheduler.enqueue(getApplication(), app.packageName, app.name, repository.settings())
            _state.value = _state.value.copy(message = string(R.string.vm_queued_app, app.name))
        }
    }

    fun quickInstall(packageName: String) {
        if (hasActiveQueueItem(packageName)) return
        clearTransientFailure(packageName)
        ManualInstallScheduler.enqueue(getApplication(), packageName, packageName, repository.settings())
        _state.value = _state.value.copy(message = string(R.string.vm_queued_generic))
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

}
