package dev.wystore.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class RuStoreSplitFallbackPolicyTest {

    @Test
    fun missingSplitArtifactWith404Or410FallsBackToWholeApk() {
        assertTrue(
            RuStoreSplitFallbackPolicy.shouldFallbackToWholeApk(
                requestedWithoutSplits = false,
                artifactCount = 4,
                error = SourceFormatException(SourceError.DOWNLOAD_FAILED, "HTTP 404")
            )
        )
        assertTrue(
            RuStoreSplitFallbackPolicy.shouldFallbackToWholeApk(
                requestedWithoutSplits = false,
                artifactCount = 2,
                error = SourceFormatException(SourceError.DOWNLOAD_FAILED, "HTTP 410")
            )
        )
    }

    @Test
    fun alreadyUniversalOrSingleArtifactDoesNotFallbackAgain() {
        assertFalse(
            RuStoreSplitFallbackPolicy.shouldFallbackToWholeApk(
                requestedWithoutSplits = true,
                artifactCount = 4,
                error = SourceFormatException(SourceError.DOWNLOAD_FAILED, "HTTP 404")
            )
        )
        assertFalse(
            RuStoreSplitFallbackPolicy.shouldFallbackToWholeApk(
                requestedWithoutSplits = false,
                artifactCount = 1,
                error = SourceFormatException(SourceError.DOWNLOAD_FAILED, "HTTP 404")
            )
        )
    }

    @Test
    fun transientServerOrNetworkErrorsDoNotTriggerUniversalFallback() {
        assertFalse(
            RuStoreSplitFallbackPolicy.shouldFallbackToWholeApk(
                requestedWithoutSplits = false,
                artifactCount = 4,
                error = SourceFormatException(SourceError.DOWNLOAD_FAILED, "HTTP 503")
            )
        )
        assertFalse(
            RuStoreSplitFallbackPolicy.shouldFallbackToWholeApk(
                requestedWithoutSplits = false,
                artifactCount = 4,
                error = SourceFormatException(SourceError.ARTIFACT_SIZE_MISMATCH, "Declared 10, expected 20")
            )
        )
        assertFalse(
            RuStoreSplitFallbackPolicy.shouldFallbackToWholeApk(
                requestedWithoutSplits = false,
                artifactCount = 4,
                error = IOException("Connection reset")
            )
        )
    }
}
