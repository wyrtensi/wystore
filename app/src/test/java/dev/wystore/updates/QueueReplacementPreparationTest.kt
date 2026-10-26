package dev.wystore.updates

import dev.wystore.data.GitHubAsset
import dev.wystore.data.GitHubRelease
import dev.wystore.data.ManagedSource
import dev.wystore.data.TransferFailure
import dev.wystore.data.local.UpdateQueueEntity
import dev.wystore.updates.model.QueueErrorCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class QueueReplacementPreparationTest {
    private val selected = GitHubAsset(22, "universal.apk", 200, "https://github.com/owner/app/releases/download/v1/universal.apk", null)
    private val other = selected.copy(id = 11, name = "arm64.apk")
    private val row = UpdateQueueEntity("queue", "example.app", "App", "v1", 10, ManagedSource.GITHUB.name,
        "FAILED", githubRepositoryOwner = "owner", githubRepositoryName = "app", githubReleaseId = 10)
    private val release = GitHubRelease(10, "v1", "v1", "", null, false, listOf(other, selected))

    @Test fun aLegacyChoiceIsDurableBeforeThePendingRemovalIsSaved() = runBlocking {
        var durable: GitHubAsset? = null
        var pending = false
        QueueReplacementPreparation.prepare(row, { row }, { 22 }, { durable }, { listOf(release) },
            persistExact = { durable = it }, savePending = {
                assertEquals(selected, durable)
                pending = true
            })
        assertEquals(selected, durable)
        assertEquals(true, pending)
    }

    @Test fun aMissingAssetCannotFallBackToAnotherFileOrRelease() {
        var pending = false
        var persisted = false
        val failure = assertThrows(TransferFailure::class.java) {
            runBlocking {
                QueueReplacementPreparation.prepare(row, { row }, { 22 }, { null },
                    { listOf(release.copy(assets = listOf(other)), release.copy(id = 20)) },
                    { persisted = true }, { pending = true })
            }
        }
        assertEquals(QueueErrorCode.SOURCE_CHANGED, failure.code)
        assertFalse(pending)
        assertFalse(persisted)
    }

    @Test fun aMissingReleaseCannotUseTheSameAssetFromAnotherRelease() {
        assertNoPending(releases = { listOf(release.copy(id = 20)) })
    }

    @Test fun aNetworkFailureCannotAuthorizeRemoval() {
        assertNoPending(releases = { throw java.io.IOException("offline") })
    }

    @Test fun aFailedExactWriteCannotAuthorizeRemoval() {
        assertNoPending(persist = { throw java.io.IOException("disk full") })
    }

    @Test fun aLegacyRowWithoutTheOriginallyChosenIdCannotAuthorizeRemoval() {
        assertNoPending(chosen = { null })
    }

    @Test fun aFailedPendingWriteCannotAuthorizeRemoval() {
        var removalAuthorized = false
        assertThrows(java.io.IOException::class.java) {
            runBlocking {
                QueueReplacementPreparation.prepare(row, { row }, { 22 }, { selected },
                    { error("No lookup") }, {}, { throw java.io.IOException("disk full") })
                removalAuthorized = true
            }
        }
        assertFalse(removalAuthorized)
    }

    @Test fun anExactFileFromAnotherManualChoiceCannotAuthorizeRemoval() {
        var pending = false
        val failure = assertThrows(TransferFailure::class.java) {
            runBlocking {
                QueueReplacementPreparation.prepare(row, { row }, { 22 }, { other },
                    { error("Must not resolve a conflicting choice") }, {}, { pending = true })
            }
        }
        assertEquals(QueueErrorCode.SOURCE_CHANGED, failure.code)
        assertFalse(pending)
    }

    @Test fun aChangedRowCannotAuthorizeRemoval() {
        var current = row
        assertNoPending(loadRow = { current }, releases = {
            current = row.copy(githubReleaseId = 20)
            listOf(release)
        })
    }

    @Test fun aChangedManualSelectionCannotAuthorizeRemoval() {
        var choice = 22L
        assertNoPending(chosen = { choice }, releases = {
            choice = 11
            listOf(release)
        })
    }

    @Test fun aManualChoiceChangedAfterTheExactWriteCannotAuthorizeRemoval() {
        var choice = 22L
        var exact: GitHubAsset? = null
        var pending = false
        var exactWritten = false
        assertThrows(TransferFailure::class.java) {
            runBlocking {
                QueueReplacementPreparation.prepare(row, {
                    if (exactWritten) {
                        choice = 11
                        exact = other
                    }
                    row
                }, { choice }, { exact }, { listOf(release) }, {
                    exact = it
                    exactWritten = true
                }, { pending = true })
            }
        }
        assertFalse(pending)
    }

    @Test fun anExactChoiceNeedsNoNetworkLookup() = runBlocking {
        var pending = false
        QueueReplacementPreparation.prepare(row, { row }, { 22 }, { selected },
            { error("An exact selection must not resolve releases") }, {}, { pending = true })
        assertEquals(true, pending)
    }

    @Test fun aRuStoreRowKeepsItsExistingPreparationPath() = runBlocking {
        val rustore = row.copy(source = ManagedSource.RUSTORE.name)
        var pending = false
        QueueReplacementPreparation.prepare(rustore, { rustore }, { error("Not GitHub") },
            { error("Not GitHub") }, { error("Not GitHub") }, { error("Not GitHub") }, { pending = true })
        assertEquals(true, pending)
    }

    private fun assertNoPending(
        loadRow: suspend () -> UpdateQueueEntity? = { row },
        chosen: () -> Long? = { 22 },
        releases: suspend () -> List<GitHubRelease> = { listOf(release) },
        persist: (GitHubAsset) -> Unit = {}
    ) {
        var pending = false
        assertThrows(Exception::class.java) {
            runBlocking {
                QueueReplacementPreparation.prepare(row, loadRow, chosen, { null }, releases, persist, { pending = true })
            }
        }
        assertFalse(pending)
    }
}
