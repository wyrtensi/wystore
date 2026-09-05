package dev.wystore.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * A download that died at 90% of a 130 MB APK used to start again from zero, on a connection that
 * had just proved unreliable. Resuming is only safe while the bytes on disk are a prefix of the
 * file being fetched; these are the rules that decide that.
 */
class ResumePolicyTest {

    @Test
    fun aPartialPrefixIsResumedFromWhereItStopped() {
        assertEquals(90L, ResumePolicy.resumableBytes(onDiskBytes = 90, expectedBytes = 130))
    }

    @Test
    fun nothingOnDiskStartsFromTheBeginning() {
        assertEquals(0L, ResumePolicy.resumableBytes(onDiskBytes = 0, expectedBytes = 130))
        assertEquals(0L, ResumePolicy.resumableBytes(onDiskBytes = -1, expectedBytes = 130))
    }

    @Test
    fun aFileThatIsAlreadyLongEnoughIsNotAPrefix() {
        // Equal or larger means the expected size changed, or the file came from a different
        // download. Resuming past the end would produce a corrupt APK that still had the right
        // length.
        assertEquals(0L, ResumePolicy.resumableBytes(onDiskBytes = 130, expectedBytes = 130))
        assertEquals(0L, ResumePolicy.resumableBytes(onDiskBytes = 200, expectedBytes = 130))
    }

    @Test
    fun anUnknownExpectedSizeMeansNoResume() {
        assertEquals(0L, ResumePolicy.resumableBytes(onDiskBytes = 90, expectedBytes = 0))
        assertEquals(0L, ResumePolicy.resumableBytes(onDiskBytes = 90, expectedBytes = -1))
    }

    @Test
    fun aDroppedConnectionKeepsWhatWasWritten() {
        assertFalse(ResumePolicy.discardsPartialFile(IOException("Connection reset")))
        assertFalse(
            ResumePolicy.discardsPartialFile(
                SourceFormatException(SourceError.DOWNLOAD_FAILED, "HTTP 503")
            )
        )
    }

    @Test
    fun aFailureAboutTheBytesThemselvesThrowsThemAway() {
        // Keeping these would make every later attempt resume onto data already known to be wrong.
        listOf(
            SourceError.ARTIFACT_SIZE_MISMATCH,
            SourceError.ARTIFACT_INTEGRITY_MISMATCH,
            SourceError.ARTIFACT_TOO_LARGE,
            SourceError.INVALID_ARTIFACT_URL,
            SourceError.UNTRUSTED_HOST
        ).forEach { error ->
            assertTrue(
                "$error must discard the partial file",
                ResumePolicy.discardsPartialFile(SourceFormatException(error, "detail"))
            )
        }
    }
}
