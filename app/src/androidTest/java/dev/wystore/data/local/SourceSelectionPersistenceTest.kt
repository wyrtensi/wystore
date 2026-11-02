package dev.wystore.data.local

import androidx.test.platform.app.InstrumentationRegistry
import dev.wystore.data.*
import dev.wystore.updates.*
import dev.wystore.updates.model.QueueState
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

class SourceSelectionPersistenceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun sourceSwitchPreservesUserSettingsAndRejectsLateRegistration() = runBlocking {
        val repository = StoreRepository(context)
        val original = repository.createBackup()
        val pkg = context.packageName
        val local = requireNotNull(repository.installedAppFresh(pkg))
        val start = repository.sourceRevision(pkg) + 1
        val dao = WyStoreDatabase.getInstance(context).updateQueueDao
        val id = "source-selection-test"
        try {
            repository.saveManaged(ManagedApp(pkg, "Test", local.signingDigests, autoUpdate = false,
                source = ManagedSource.RUSTORE, googlePlayUpdateDetected = true, sourceRevision = start))
            val old = UpdateQueueEntity(id, pkg, "Test", local.versionName, local.versionCode, ManagedSource.RUSTORE.name,
                QueueState.READY_TO_INSTALL.name, sourceRevision = start, signingDigests = local.signingDigests.joinToString(","))
            dao.insertOrReplace(old)
            val result = SourceSelectionCoordinator(context).select(pkg, AppSourceChoice.GitHub(GitHubRepository("example", "app"), pkg))
            assertEquals(SourceSwitchResult.Saved(start + 1), result)
            val selected = repository.managedApps().single { it.packageName == pkg }
            assertEquals(ManagedSource.GITHUB, selected.source)
            assertFalse(selected.autoUpdate); assertTrue(selected.googlePlayUpdateDetected)
            assertEquals(QueueState.CANCELED.name, dao.getById(id)!!.state)
            assertFalse(InstalledAppRegistrar.register(context, old))
            repository.removeManaged(pkg)
            assertEquals(start + 1, repository.sourceRevision(pkg))
        } finally {
            dao.deleteById(id)
            repository.restoreBackup(original)
        }
    }

    @Test fun committedConfirmationDefersAChoiceAfterProcessStateIsReloaded() = runBlocking {
        val pkg = context.packageName
        val dao = WyStoreDatabase.getInstance(context).updateQueueDao
        val id = "source-committed-test"
        try {
            dao.insertOrReplace(UpdateQueueEntity(id, pkg, "Test", "1", 0, ManagedSource.RUSTORE.name,
                QueueState.AWAITING_USER_CONFIRMATION.name))
            dao.insertOrReplaceSession(InstallSessionEntity(id, pkg, 123456, "test", InstallSessionWriter.SESSION_COMMITTED))
            assertEquals(SourceSwitchResult.InstallInProgress,
                SourceSelectionCoordinator(context).select(pkg, AppSourceChoice.GitHub(GitHubRepository("example", "app"), pkg)))
        } finally { dao.deleteById(id) }
    }

    @Test fun pendingRemovalBlocksAChoiceAndAnInstallUntilThePackageIsGone() = runBlocking {
        val pkg = context.packageName
        val repository = StoreRepository(context)
        val store = PendingReinstallStore(context)
        val previous = store.read(pkg)
        val row = UpdateQueueEntity("pending-removal-test", pkg, "Test", "1", 0, ManagedSource.WY_STORE.name,
            QueueState.READY_TO_INSTALL.name, sourceRevision = repository.sourceRevision(pkg), sourceReplacement = true)
        try {
            store.save(PendingReinstall(pkg, pkg, "Test", System.currentTimeMillis(), queueId = row.id,
                sourceRevision = row.sourceRevision))
            assertEquals(SourceSwitchResult.InstallInProgress,
                SourceSelectionCoordinator(context).select(pkg, AppSourceChoice.GitHub(GitHubRepository("example", "app"), pkg)))
            assertThrows(TransferFailure::class.java) { SourceSelectionCoordinator.requireCurrent(context, row) }
            SourceSelectionCoordinator.requireCurrent(context, row, pendingRemoval = true)
        } finally {
            store.clear(pkg)
            previous?.let(store::save)
        }
    }

    @Test fun aVerifiedReadyReplacementRequestsInstallationWithoutAnotherDownload() = runBlocking {
        val pkg = "example.library.replacement"
        val id = "ready-replacement-test"
        val dao = WyStoreDatabase.getInstance(context).updateQueueDao
        val requests = AutoInstallStore(context)
        val wasRequested = pkg in requests.requested()
        try {
            dao.insertOrReplace(UpdateQueueEntity(id, pkg, "Test", "1", 1, ManagedSource.WY_STORE.name,
                QueueState.READY_TO_INSTALL.name, sourceReplacement = true,
                sourceRevision = StoreRepository(context).sourceRevision(pkg)))
            QueueCoordinator(context).resumeReplacement(id)
            assertEquals(QueueState.READY_TO_INSTALL.name, dao.getById(id)!!.state)
            assertTrue(pkg in requests.requested())
        } finally {
            dao.deleteById(id)
            if (!wasRequested) requests.clear(pkg)
        }
    }

    @Test fun aCanceledSourceChoiceCannotPersistAfterAnotherOperationReleasesTheGate() = runBlocking {
        val repository = StoreRepository(context)
        val before = repository.sourceRevision(context.packageName)
        SourceSelectionGate.locked {
            val pending = launch(Dispatchers.IO) {
                SourceSelectionCoordinator(context).select(context.packageName,
                    AppSourceChoice.GitHub(GitHubRepository("example", "canceled"), context.packageName))
            }
            pending.cancelAndJoin()
        }
        assertEquals(before, repository.sourceRevision(context.packageName))
    }
}
