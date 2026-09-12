package dev.wystore.ui.components

import dev.wystore.InstallQueueItem
import dev.wystore.InstallQueueStatus
import dev.wystore.data.DownloadProgress
import dev.wystore.data.InstallSource
import dev.wystore.data.InstalledApp
import dev.wystore.data.ManagedSource
import dev.wystore.data.PendingUpdate
import dev.wystore.data.StoreApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageUiStateReducerTest {

    private val catalogApp = StoreApp(
        appId = 1L,
        packageName = "ru.vk.store",
        name = "RuStore",
        publisher = "VK",
        categories = listOf("Инструменты"),
        shortDescription = "Store",
        fullDescription = "Official store",
        iconUrl = "https://example.com/icon.png",
        screenshots = emptyList(),
        rating = 4.5,
        ratingCount = 100,
        downloadsText = "1M+",
        versionName = "2.0.0",
        versionCode = 200L,
        updatedAt = "2026-09-01",
        sizeBytes = 10_000_000L,
        minAndroidVersion = "24",
        minSdkVersion = 24,
        signatureHint = "sha256",
        sourceVersionId = 1L
    )

    @Test
    fun installedUpToDateProducesOpenAction() {
        val installed = InstalledApp(
            packageName = "ru.vk.store",
            label = "RuStore",
            versionName = "2.0.0",
            versionCode = 200L,
            lastUpdateTime = 1000L,
            source = InstallSource.WY_STORE,
            signingDigests = setOf("sha256")
        )
        val state = PackageUiStateReducer.reduce(
            app = catalogApp,
            installed = installed,
            managed = null,
            queueItem = null,
            pendingUpdate = null
        )
        assertEquals(PrimaryAction.Open, state.primaryAction)
        assertEquals(StatusCode.INSTALLED, state.status.code)
    }

    @Test
    fun updateAvailableProducesUpdateAction() {
        val installed = InstalledApp(
            packageName = "ru.vk.store",
            label = "RuStore",
            versionName = "1.0.0",
            versionCode = 100L,
            lastUpdateTime = 1000L,
            source = InstallSource.WY_STORE,
            signingDigests = setOf("sha256")
        )
        val state = PackageUiStateReducer.reduce(
            app = catalogApp,
            installed = installed,
            managed = null,
            queueItem = null,
            pendingUpdate = null
        )
        assertEquals(PrimaryAction.Update, state.primaryAction)
        assertEquals(StatusCode.OFFER_NEXT, state.status.code)
    }

    @Test
    fun downloadingProducesPauseAndCancelActions() {
        val queueItem = InstallQueueItem(
            packageName = "ru.vk.store",
            label = "RuStore",
            status = InstallQueueStatus.DOWNLOADING,
            progress = DownloadProgress(
                downloadedBytes = 5_000_000L,
                totalBytes = 10_000_000L,
                bytesPerSecond = 1_000_000L,
                artifactIndex = 1,
                artifactCount = 1,
                etaSeconds = 5L
            )
        )
        val state = PackageUiStateReducer.reduce(
            app = catalogApp,
            installed = null,
            managed = null,
            queueItem = queueItem,
            pendingUpdate = null
        )
        assertEquals(PrimaryAction.Pause, state.primaryAction)
        assertEquals(SecondaryAction.Cancel, state.secondaryAction)
        assertEquals(StatusCode.DOWNLOADING, state.status.code)
        assertNotNull(state.progress)
        assertEquals(0.5f, state.progress ?: 0f, 0.01f)
    }

    @Test
    fun readyArtifactProducesInstallAction() {
        val pending = PendingUpdate(
            packageName = "ru.vk.store",
            label = "RuStore",
            versionName = "2.0.0",
            versionCode = 200L,
            filePaths = listOf("/cache/rustore.apk"),
            signingDigests = setOf("abc"),
            source = ManagedSource.RUSTORE
        )
        val state = PackageUiStateReducer.reduce(
            app = catalogApp,
            installed = null,
            managed = null,
            queueItem = null,
            pendingUpdate = pending
        )
        assertEquals(PrimaryAction.Install, state.primaryAction)
        assertEquals(StatusCode.READY_TO_INSTALL, state.status.code)
    }

    /**
     * A row waiting its turn offers the way out of the queue, and nothing else.
     *
     * It used to offer "Continue", which did nothing: the handler behind it asked Android to
     * install a downloaded file, and this row has not downloaded one - while enqueueing it again
     * returns early, because it is already in the queue.
     */
    @Test
    fun aRowWaitingItsTurnOffersToTakeTheSlot() {
        val queueItem = InstallQueueItem(
            packageName = "ru.vk.store",
            label = "RuStore",
            status = InstallQueueStatus.RESOLVING,
            detail = "Awaiting permission"
        )
        val state = PackageUiStateReducer.reduce(
            app = catalogApp,
            installed = null,
            managed = null,
            queueItem = queueItem,
            pendingUpdate = null
        )
        assertEquals(PrimaryAction.DownloadNow, state.primaryAction)
        assertEquals(StatusCode.QUEUED, state.status.code)
        assertEquals(SecondaryAction.Cancel, state.secondaryAction)
        assertEquals(
            RowAction.DownloadNow,
            RowActionPolicy.actionFor(state.primaryAction, state.status.code)
        )
    }

    @Test
    fun installingProducesDisabledInstallingAction() {
        val queueItem = InstallQueueItem(
            packageName = "ru.vk.store",
            label = "RuStore",
            status = InstallQueueStatus.INSTALLING
        )
        val state = PackageUiStateReducer.reduce(
            app = catalogApp,
            installed = null,
            managed = null,
            queueItem = queueItem,
            pendingUpdate = null
        )
        assertEquals(PrimaryAction.Installing, state.primaryAction)
        assertEquals(StatusCode.INSTALLING, state.status.code)
    }

    @Test
    fun failedProducesRetryAction() {
        val queueItem = InstallQueueItem(
            packageName = "ru.vk.store",
            label = "RuStore",
            status = InstallQueueStatus.FAILED,
            detail = "Network timeout"
        )
        val state = PackageUiStateReducer.reduce(
            app = catalogApp,
            installed = null,
            managed = null,
            queueItem = queueItem,
            pendingUpdate = null
        )
        assertEquals(PrimaryAction.Retry, state.primaryAction)
        assertEquals(StatusCode.FAILED_NETWORK, state.status.code)
    }

    @Test
    fun aCatalogueEntryThatWasNeverDownloadedIsNotReportedAsReadyToInstall() {
        // Home and Search rows used the ready-to-install line for any app the device does not have,
        // so a catalogue entry nobody had touched read exactly like an update already downloaded and
        // waiting. The two are different things and must not share a sentence.
        val state = PackageUiStateReducer.reduce(
            app = catalogApp,
            installed = null,
            managed = null,
            queueItem = null,
            pendingUpdate = null
        )
        assertEquals(PrimaryAction.Install, state.primaryAction)
        assertEquals(StatusCode.NOT_INSTALLED, state.status.code)
    }

    @Test
    fun aDownloadedUpdateStillReportsReadyToInstall() {
        val state = PackageUiStateReducer.reduce(
            app = catalogApp,
            installed = null,
            managed = null,
            queueItem = null,
            pendingUpdate = PendingUpdate(
                packageName = catalogApp.packageName,
                label = catalogApp.name,
                versionName = catalogApp.versionName,
                versionCode = catalogApp.versionCode,
                filePaths = listOf("/data/artifact_0.apk"),
                signingDigests = setOf("abc"),
                source = ManagedSource.RUSTORE
            )
        )
        assertEquals(PrimaryAction.Install, state.primaryAction)
        assertEquals(StatusCode.READY_TO_INSTALL, state.status.code)
    }

    @Test
    fun incompatibleAppProducesNonePrimaryAction() {
        val incompatibleApp = catalogApp.copy(minSdkVersion = 999)
        val state = PackageUiStateReducer.reduce(
            app = incompatibleApp,
            installed = null,
            managed = null,
            queueItem = null,
            pendingUpdate = null,
            deviceSdkInt = 35
        )
        assertEquals(PrimaryAction.None, state.primaryAction)
        assertEquals(false, state.isCompatible)
    }
}
