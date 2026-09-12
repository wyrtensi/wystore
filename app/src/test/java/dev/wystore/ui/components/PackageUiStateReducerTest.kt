package dev.wystore.ui.components

import dev.wystore.InstallQueueItem
import dev.wystore.data.VerificationError
import dev.wystore.updates.model.QueueErrorCode
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
            errorCode = QueueErrorCode.NETWORK,
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

    /**
     * The failure detail is an exception message from the data layer, untranslated and written for
     * a log. It reached the card twice - as the failure line and again under a progress bar that
     * went on animating over a download that had stopped.
     */
    @Test
    fun `a failed row carries no raw text and no transfer of its own`() {
        val queueItem = InstallQueueItem(
            packageName = "ru.vk.store",
            label = "RuStore",
            status = InstallQueueStatus.FAILED,
            errorCode = QueueErrorCode.INTERNAL,
            detail = "Action is not allowed while queue item 8f2c1cc7 is DOWNLOADING."
        )
        val state = PackageUiStateReducer.reduce(
            app = catalogApp,
            installed = null,
            managed = null,
            queueItem = queueItem,
            pendingUpdate = null
        )

        assertEquals(StatusCode.FAILED_GENERIC, state.status.code)
        assertEquals(null, state.transferInfo)
        assertEquals(null, state.progress)
        assertEquals(
            "only code names travel to the screen",
            mapOf(StatusMessage.ARG_ERROR_CODE to "INTERNAL"),
            state.status.args
        )
    }

    /**
     * The one refusal the user can answer: RuStore's own metadata disagreeing with the file
     * RuStore served. Retrying fetches the same file, so the row asks instead - and keeps the
     * retry, because a download can also simply have gone wrong on the way.
     */
    @Test
    fun `a file the source would not vouch for is a question with both answers`() {
        val queueItem = InstallQueueItem(
            packageName = "ru.gdemoideti.parent",
            label = "Где мои дети",
            status = InstallQueueStatus.FAILED,
            errorCode = QueueErrorCode.SIGNATURE,
            detail = VerificationError.SOURCE_FINGERPRINT_MISMATCH.name
        )
        val state = PackageUiStateReducer.reduce(
            app = catalogApp,
            installed = null,
            managed = null,
            queueItem = queueItem,
            pendingUpdate = null
        )

        assertEquals(StatusCode.FAILED_SOURCE_UNCONFIRMED, state.status.code)
        assertEquals(PrimaryAction.ConfirmSource, state.primaryAction)
        assertEquals(SecondaryAction.Retry, state.secondaryAction)
    }

    /** Any other signature failure stays a verdict: there is nothing there for a user to answer. */
    @Test
    fun `a signature that does not match the installed app is not offered as a choice`() {
        val queueItem = InstallQueueItem(
            packageName = "ru.vk.store",
            label = "RuStore",
            status = InstallQueueStatus.FAILED,
            errorCode = QueueErrorCode.SIGNATURE,
            detail = VerificationError.SIGNATURE_MISMATCH.name
        )
        val state = PackageUiStateReducer.reduce(
            app = catalogApp,
            installed = null,
            managed = null,
            queueItem = queueItem,
            pendingUpdate = null
        )

        assertEquals(StatusCode.FAILED_SIGNATURE, state.status.code)
        assertEquals(PrimaryAction.Retry, state.primaryAction)
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
