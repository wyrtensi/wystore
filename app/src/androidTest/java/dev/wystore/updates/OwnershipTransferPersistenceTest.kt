package dev.wystore.updates

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wystore.data.GitHubAsset
import dev.wystore.data.GitHubRepository
import dev.wystore.data.ManagedSource
import dev.wystore.data.local.WyStoreDatabase
import dev.wystore.data.local.UpdateArtifactEntity
import dev.wystore.updates.model.QueueAction
import dev.wystore.updates.model.QueueErrorCode
import dev.wystore.updates.model.QueueState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OwnershipTransferPersistenceTest {
    private lateinit var context: Context
    private lateinit var database: WyStoreDatabase
    private lateinit var repository: QueueRepository

    @Before
    fun setUp() {
        context = object : ContextWrapper(ApplicationProvider.getApplicationContext()) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                super.getSharedPreferences("ownership_transfer_test_$name", mode)
        }
        database = Room.inMemoryDatabaseBuilder(context, WyStoreDatabase::class.java).build()
        repository = QueueRepository(context, database)
    }

    @After
    fun tearDown() {
        PendingReinstallStore(context).clear()
        database.close()
    }

    @Test
    fun pendingHandoverRetainsTheSelectedSourceAcrossStoreRecreation() {
        val pending = PendingReinstall(
            removedPackageName = "missing.ownership.transfer.test",
            installPackageName = "replacement.ownership.transfer.test",
            label = "Replacement",
            startedAt = 42L,
            requiresUpdateOwnership = true,
            githubRepository = GitHubRepository("example", "app"),
            githubAsset = GitHubAsset(12L, "app.apk", 100L, "https://example.test/app.apk", "sha256:abc"),
            githubReleaseId = 8L
        )

        PendingReinstallStore(context).save(pending)

        assertEquals(pending, PendingReinstallStore(context).read())
    }

    @Test
    fun packageBroadcastSuccessUsesTheSameOwnershipVerificationAsTheInstallerCallback() = runBlocking {
        val row = repository.enqueueManualInstall("missing.ownership.transfer.test", "Example", ManagedSource.RUSTORE)!!
        repository.requireUpdateOwnership(row.id)
        assertTrue(repository.getById(row.id)!!.requiresUpdateOwnership)

        val observed = repository.transition(row.id, QueueAction.InstallSucceeded)
        val duplicate = repository.reconcileInstallResult(row.id, success = true)
        repository.removeSettled(row.packageName, row.versionCode)

        assertEquals(QueueState.INSTALLED, observed.state)
        assertEquals(QueueErrorCode.UPDATE_OWNERSHIP_UNCONFIRMED, observed.errorCode)
        assertEquals(observed, duplicate)
        assertEquals(QueueErrorCode.UPDATE_OWNERSHIP_UNCONFIRMED, repository.getById(row.id)?.errorCode)
    }

    @Test
    fun explicitHandoverCanContinueAnExistingQueueRowThatAnOrdinaryTapLeavesAlone() = runBlocking {
        val row = repository.enqueueManualInstall("missing.ownership.transfer.test", "Example", ManagedSource.RUSTORE)!!
        repository.transition(row.id, QueueAction.StartDownload)

        val ordinary = repository.enqueueManualInstall(row.packageName, row.label, ManagedSource.RUSTORE)
        val handover = repository.enqueueManualInstall(
            row.packageName, row.label, ManagedSource.RUSTORE, requiresUpdateOwnership = true
        )

        assertEquals(null, ordinary)
        assertEquals(row.id, handover?.id)
        assertEquals(QueueState.DOWNLOADING.name, handover?.state)
    }

    @Test
    fun anotherExplicitHandoverRestartsAPreviousInstalledWarning() = runBlocking {
        val row = repository.enqueueManualInstall("missing.ownership.transfer.test", "Example", ManagedSource.RUSTORE)!!
        repository.requireUpdateOwnership(row.id)
        repository.reconcileInstallResult(row.id, success = true)

        val replacement = repository.enqueueManualInstall(
            row.packageName, row.label, ManagedSource.RUSTORE, requiresUpdateOwnership = true
        )

        assertEquals(row.id, replacement?.id)
        assertEquals(QueueState.AVAILABLE.name, replacement?.state)
        assertTrue(replacement!!.requiresUpdateOwnership)
    }

    @Test
    fun aStaleQueueWriterCannotEraseTheExplicitOwnershipRequirement() = runBlocking {
        val stale = repository.enqueueManualInstall("missing.ownership.transfer.test", "Example", ManagedSource.RUSTORE)!!
        repository.requireUpdateOwnership(stale.id)

        database.updateQueueDao.update(stale.copy(label = "Downloaded name"))

        assertTrue(database.updateQueueDao.getById(stale.id)!!.requiresUpdateOwnership)
    }

    @Test
    fun replacingAQueueRowCannotEraseTheExplicitOwnershipRequirement() = runBlocking {
        val stale = repository.enqueueManualInstall("missing.ownership.transfer.test", "Example", ManagedSource.RUSTORE)!!
        repository.requireUpdateOwnership(stale.id)

        database.updateQueueDao.insertOrReplace(stale.copy(id = "replacement-row"))

        assertTrue(database.updateQueueDao.getById("replacement-row")!!.requiresUpdateOwnership)
    }

    @Test
    fun theExactGitHubSelectionSurvivesARecreatedChoiceStore() {
        val selected = GitHubAsset(12L, "universal.apk", 100L, "https://github.com/o/r/releases/download/v1/universal.apk", "sha256:abc")
        GitHubAssetChoice(context).rememberExact("handover", selected)

        assertEquals(selected, GitHubAssetChoice(context).exact("handover"))
        GitHubAssetChoice(context).forget("handover")
    }

    @Test
    fun aPlaceholderThatInheritsOwnershipAlsoKeepsItsSelectedAssetForRetry() = runBlocking {
        val old = repository.enqueueManualInstall("missing.ownership.transfer.test", "Example", ManagedSource.GITHUB)!!
        repository.requireUpdateOwnership(old.id)
        val github = GitHubRepository("example", "app")
        val replacement = repository.enqueueAvailableUpdate(
            packageName = GitHubInstallScheduler.placeholderPackageName(github),
            label = "Example",
            versionName = "",
            versionCode = 17,
            source = ManagedSource.GITHUB,
            githubRepository = github,
            githubReleaseId = 17
        )
        val selected = GitHubAsset(12L, "universal.apk", 100L, "https://github.com/o/r/releases/download/v1/universal.apk", null)
        val choices = GitHubAssetChoice(context)

        choices.prepareDownload(replacement.id, requiresExactSelection = false) { selected }
        database.updateQueueDao.saveVerifiedArtifacts(
            queueId = replacement.id,
            packageName = old.packageName,
            versionName = "1",
            versionCode = old.versionCode,
            signingDigests = "abc",
            artifacts = listOf(UpdateArtifactEntity(replacement.id, "unused-test-artifact", 100))
        )
        val inherited = repository.getById(replacement.id)!!
        val retried = GitHubAssetChoice(context).prepareDownload(replacement.id, inherited.requiresUpdateOwnership) {
            error("An inherited handover must not resolve another release")
        }

        assertTrue(inherited.requiresUpdateOwnership)
        assertEquals(null, repository.getById(old.id))
        assertEquals(selected, retried)
        choices.forget(replacement.id)
    }
}
