package dev.wystore.updates

import dev.wystore.data.ManagedSource
import dev.wystore.updates.model.QueueAction
import dev.wystore.updates.model.QueueErrorCode
import dev.wystore.updates.model.QueueItemSnapshot
import dev.wystore.updates.model.QueueMode
import dev.wystore.updates.model.QueueState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class QueueReducerTest {
    @Test
    fun availableItemStartsDownloading() {
        assertEquals(
            QueueState.DOWNLOADING,
            QueueReducer.reduce(item(QueueState.AVAILABLE), QueueAction.StartDownload).state
        )
    }

    @Test
    fun verifiedItemBecomesReadyToInstall() {
        assertEquals(
            QueueState.READY_TO_INSTALL,
            QueueReducer.reduce(item(QueueState.VERIFYING), QueueAction.Verified).state
        )
    }

    @Test
    fun successfulInstallOffersTheNextItem() {
        assertEquals(
            QueueState.OFFER_NEXT,
            QueueReducer.reduce(item(QueueState.INSTALLING), QueueAction.InstallSucceeded).state
        )
    }

    @Test
    fun failedItemRetriesFromAvailable() {
        assertEquals(
            QueueState.AVAILABLE,
            QueueReducer.reduce(item(QueueState.FAILED), QueueAction.Retry).state
        )
    }

    @Test
    fun installSuccessIsRejectedBeforeInstallationStarts() {
        assertThrows(IllegalStateException::class.java) {
            QueueReducer.reduce(item(QueueState.DOWNLOADING), QueueAction.InstallSucceeded)
        }
    }

    @Test
    fun installFailureIsRejectedBeforeInstallationStarts() {
        assertThrows(IllegalStateException::class.java) {
            QueueReducer.reduce(item(QueueState.DOWNLOADING), QueueAction.InstallFailed())
        }
    }

    @Test
    fun orderPrioritizesSecurityThenSelectedThenNormalUpdates() {
        val normal = item(QueueState.AVAILABLE, packageName = "normal", priority = 0, position = 0)
        val selected = item(QueueState.AVAILABLE, packageName = "selected", priority = 1, position = 2)
        val security = item(QueueState.AVAILABLE, packageName = "security", priority = 2, position = 3)

        assertEquals(
            listOf("security", "selected", "normal"),
            QueueReducer.order(listOf(normal, selected, security)).map { it.packageName }
        )
    }

    @Test
    fun manualModeNeverEmitsAnAutomaticNextItemAction() {
        assertNull(
            QueueReducer.automaticNextAction(
                mode = QueueMode.MANUAL_ONE_BY_ONE,
                current = item(QueueState.OFFER_NEXT)
            )
        )
    }

    @Test
    fun smartModeOffersTheNextItemAfterASkip() {
        val skipped = QueueReducer.reduce(item(QueueState.AVAILABLE), QueueAction.Skip)
        val nextAction = requireNotNull(
            QueueReducer.automaticNextAction(mode = QueueMode.SMART_PROMPTS, current = skipped)
        )

        assertEquals(
            QueueState.OFFER_NEXT,
            QueueReducer.reduce(skipped, nextAction).state
        )
    }

    @Test
    fun downloadProgressPersistsBeforeVerification() {
        val progressed = QueueReducer.reduce(
            item(QueueState.DOWNLOADING),
            QueueAction.DownloadProgress(downloadedBytes = 40, totalBytes = 100)
        )

        assertEquals(QueueState.DOWNLOADING, progressed.state)
        assertEquals(40, progressed.downloadedBytes)
        assertEquals(100, progressed.totalBytes)
    }

    @Test
    fun downloadedItemMovesThroughVerificationToReady() {
        val verifying = QueueReducer.reduce(item(QueueState.DOWNLOADING), QueueAction.StartVerification)

        assertEquals(
            QueueState.READY_TO_INSTALL,
            QueueReducer.reduce(verifying, QueueAction.Verified).state
        )
    }

    @Test
    fun unknownSourcesPermissionLeadsToUserConfirmation() {
        val waitingForPermission = QueueReducer.reduce(
            item(QueueState.READY_TO_INSTALL),
            QueueAction.AwaitUnknownSourcesPermission
        )

        assertEquals(
            QueueState.AWAITING_USER_CONFIRMATION,
            QueueReducer.reduce(waitingForPermission, QueueAction.UnknownSourcesPermissionGranted).state
        )
    }

    @Test
    fun readyItemCanRequestConfirmationAndStartInstallation() {
        val waitingForConfirmation = QueueReducer.reduce(
            item(QueueState.READY_TO_INSTALL),
            QueueAction.RequestInstallConfirmation
        )

        assertEquals(
            QueueState.INSTALLING,
            QueueReducer.reduce(waitingForConfirmation, QueueAction.StartInstall).state
        )
    }

    @Test
    fun failureMetadataIsClearedWhenRetryRestoresAvailability() {
        val failed = QueueReducer.reduce(
            item(QueueState.DOWNLOADING).copy(downloadedBytes = 40, totalBytes = 100),
            QueueAction.Failed(QueueErrorCode.NETWORK, "offline")
        )
        val retried = QueueReducer.reduce(failed, QueueAction.Retry)

        assertEquals(QueueState.AVAILABLE, retried.state)
        assertEquals(0, retried.downloadedBytes)
        assertEquals(0, retried.totalBytes)
        assertNull(retried.errorCode)
        assertNull(retried.errorDetail)
    }

    @Test
    fun orderingBreaksEqualPriorityAndPositionByPackageThenId() {
        val zeta = item(QueueState.AVAILABLE, packageName = "zeta", priority = 1, position = 4)
        val betaSecond = item(
            QueueState.AVAILABLE,
            id = "beta-second",
            packageName = "beta",
            priority = 1,
            position = 4
        )
        val betaFirst = item(
            QueueState.AVAILABLE,
            id = "beta-first",
            packageName = "beta",
            priority = 1,
            position = 4
        )
        val earlier = item(QueueState.AVAILABLE, packageName = "earlier", priority = 1, position = 3)

        assertEquals(
            listOf("earlier", "beta-first", "beta-second", "zeta"),
            QueueReducer.order(listOf(zeta, betaSecond, earlier, betaFirst)).map { it.id }
        )
    }

    @Test
    fun genericActionsCannotMutateAnOfferedNextItem() {
        assertThrows(IllegalStateException::class.java) {
            QueueReducer.reduce(item(QueueState.OFFER_NEXT), QueueAction.Failed(QueueErrorCode.NETWORK))
        }
        assertThrows(IllegalStateException::class.java) {
            QueueReducer.reduce(item(QueueState.OFFER_NEXT), QueueAction.Skip)
        }
        assertThrows(IllegalStateException::class.java) {
            QueueReducer.reduce(item(QueueState.OFFER_NEXT), QueueAction.Cancel)
        }
    }

    @Test
    fun genericSkipAndCancelCannotMutateAnInstallingItem() {
        assertThrows(IllegalStateException::class.java) {
            QueueReducer.reduce(item(QueueState.INSTALLING), QueueAction.Failed(QueueErrorCode.NETWORK))
        }
        assertThrows(IllegalStateException::class.java) {
            QueueReducer.reduce(item(QueueState.INSTALLING), QueueAction.Skip)
        }
        assertThrows(IllegalStateException::class.java) {
            QueueReducer.reduce(item(QueueState.INSTALLING), QueueAction.Cancel)
        }
    }

    private fun item(
        state: QueueState,
        packageName: String = "example.package",
        id: String = packageName,
        priority: Int = 0,
        position: Int = 0
    ) = QueueItemSnapshot(
        id = id,
        packageName = packageName,
        label = packageName,
        versionName = "1.0",
        versionCode = 1,
        source = ManagedSource.RUSTORE,
        state = state,
        priority = priority,
        position = position
    )
}
