package dev.wystore.updates

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.wystore.data.GitHubAsset
import dev.wystore.data.GitHubRelease
import dev.wystore.data.ManagedSource
import dev.wystore.data.TransferFailure
import dev.wystore.data.local.UpdateQueueEntity
import dev.wystore.data.local.WyStoreDatabase
import dev.wystore.updates.model.QueueErrorCode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/** Exercises the database row and id-only SharedPreferences that version 0.5.1 left on disk. */
class LegacyGitHubReplacementTest {
    private val context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            super.getSharedPreferences("legacy_replacement_test_$name", mode)
    }
    private lateinit var database: WyStoreDatabase
    private val selected = GitHubAsset(22, "universal.apk", 200,
        "https://github.com/owner/app/releases/download/v1/universal.apk", "sha256:chosen")
    private val release = GitHubRelease(10, "v1", "v1", "", null, false, listOf(selected))
    private val row = UpdateQueueEntity("legacy-queue", "example.app", "App", "v1", 10,
        ManagedSource.GITHUB.name, "FAILED", githubRepositoryOwner = "owner",
        githubRepositoryName = "app", githubReleaseId = 10)

    @Before fun seedVersion051State() = runBlocking {
        context.getSharedPreferences("github_asset_choice", Context.MODE_PRIVATE).edit()
            .clear().putLong(row.id, 22).commit()
        PendingReinstallStore(context).clear()
        database = Room.inMemoryDatabaseBuilder(context, WyStoreDatabase::class.java).build()
        database.updateQueueDao.insert(row)
    }

    @After fun close() {
        database.close()
        context.getSharedPreferences("github_asset_choice", Context.MODE_PRIVATE).edit().clear().commit()
        PendingReinstallStore(context).clear()
    }

    @Test fun anUpgradedRowHasItsExactFileOnDiskBeforeRemovalIsAuthorized() = runBlocking {
        assertNull(GitHubAssetChoice(context).exact(row.id))
        var removalAuthorized = false
        prepare(listOf(release)) {
            assertEquals(selected, GitHubAssetChoice(context).exact(row.id))
            assertEquals(row.id, PendingReinstallStore(context).read(row.packageName)?.queueId)
            removalAuthorized = true
        }
        assertEquals(true, removalAuthorized)
        val reopened = GitHubAssetChoice(context)
        assertEquals(selected, reopened.prepareDownload(row.id, true) { error("Must use the saved file") })
    }

    @Test fun aMissingLegacyFileLeavesTheInstalledAppAndPendingStoreAlone() {
        var removalAuthorized = false
        val failure = assertThrows(TransferFailure::class.java) {
            runBlocking { prepare(listOf(release.copy(assets = emptyList()))) { removalAuthorized = true } }
        }
        assertEquals(QueueErrorCode.SOURCE_CHANGED, failure.code)
        assertNull(PendingReinstallStore(context).read(row.packageName))
        assertNull(GitHubAssetChoice(context).exact(row.id))
        assertFalse(removalAuthorized)
    }

    private suspend fun prepare(releases: List<GitHubRelease>, authorizeRemoval: () -> Unit) {
        val choices = GitHubAssetChoice(context)
        QueueReplacementPreparation.prepare(row, { database.updateQueueDao.getById(row.id) },
            { choices.chosen(row.id) }, { choices.exact(row.id) }, { releases },
            { choices.rememberExact(row.id, it) }, {
                PendingReinstallStore(context).save(PendingReinstall(row.packageName, row.packageName,
                    row.label, 1, queueId = row.id, requiresUpdateOwnership = true))
            })
        authorizeRemoval()
    }
}
