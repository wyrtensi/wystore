package dev.wystore.updates

import dev.wystore.data.SourceError
import dev.wystore.updates.model.QueueErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubExactDownloadPolicyTest {
    @Test
    fun aRemovedSelectedAssetIsASourceChange() {
        for (status in listOf(404, 410)) {
            val failure = GitHubExactDownloadPolicy.failureFor(status)!!
            assertEquals(QueueErrorCode.SOURCE_CHANGED, failure.code)
            assertFalse(failure.retryable)
        }
    }

    @Test
    fun changedBytesCannotBeInstalledAsTheSelectedAsset() {
        for (error in listOf(SourceError.ARTIFACT_SIZE_MISMATCH, SourceError.ARTIFACT_INTEGRITY_MISMATCH)) {
            assertEquals(QueueErrorCode.SOURCE_CHANGED, GitHubExactDownloadPolicy.failureFor(error)?.code)
        }
    }

    @Test
    fun transientFailuresRetainTheirOwnDiagnosis() {
        assertNull(GitHubExactDownloadPolicy.failureFor(503))
        assertNull(GitHubExactDownloadPolicy.failureFor(SourceError.DOWNLOAD_FAILED))
    }
}
