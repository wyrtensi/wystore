package dev.wystore.updates

import dev.wystore.data.InstallSource
import dev.wystore.data.InstalledApp
import dev.wystore.data.ManagedSource
import dev.wystore.data.PendingUpdate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstalledUpdateMatcherTest {
    private val pending = PendingUpdate(
        packageName = "sample.app",
        label = "Sample",
        versionName = "2.0",
        versionCode = 20,
        filePaths = listOf("artifact.apk"),
        signingDigests = setOf("trusted"),
        source = ManagedSource.RUSTORE,
        downloadedAt = 1_000L
    )

    @Test
    fun acceptsActuallyInstalledMatchingOrNewerVersion() {
        assertTrue(InstalledUpdateMatcher.matches(pending, installed(versionCode = 20, digests = setOf("trusted"), updatedAt = 2_000L)))
        assertTrue(InstalledUpdateMatcher.matches(pending, installed(versionCode = 21, digests = setOf("trusted"))))
    }

    @Test
    fun theSameVersionInstalledBeforeTheDownloadIsNotTheInstall() {
        // "Take over" and "Reinstall" fetch the version already on the phone. Read as installed,
        // the download was deleted before it was ever installed and the user told it was.
        assertFalse(InstalledUpdateMatcher.matches(pending, installed(versionCode = 20, digests = setOf("trusted"), updatedAt = 500L)))
    }

    @Test
    fun rejectsMissingWrongVersionPackageOrSignature() {
        assertFalse(InstalledUpdateMatcher.matches(pending, null))
        assertFalse(InstalledUpdateMatcher.matches(pending, installed(packageName = "other.app", versionCode = 20, digests = setOf("trusted"))))
        assertFalse(InstalledUpdateMatcher.matches(pending, installed(versionCode = 19, digests = setOf("trusted"))))
        assertFalse(InstalledUpdateMatcher.matches(pending, installed(versionCode = 20, digests = setOf("other"))))
    }

    private fun installed(
        packageName: String = "sample.app",
        versionCode: Long,
        digests: Set<String>,
        updatedAt: Long = 1L
    ) = InstalledApp(packageName, "Sample", "2.0", versionCode, updatedAt, InstallSource.OTHER, digests)
}
