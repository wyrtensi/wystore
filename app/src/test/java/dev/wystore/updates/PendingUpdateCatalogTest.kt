package dev.wystore.updates

import dev.wystore.data.ManagedSource
import dev.wystore.data.InstallSource
import dev.wystore.data.InstalledApp
import dev.wystore.data.PendingUpdate
import org.junit.Assert.assertEquals
import org.junit.Test

class PendingUpdateCatalogTest {
    @Test
    fun newerUpdateReplacesExistingPackageWithoutDroppingOthers() {
        val old = pending("one.app", versionCode = 10)
        val other = pending("two.app", versionCode = 4)
        val newer = pending("one.app", versionCode = 11)

        val result = PendingUpdateCatalog.upsert(listOf(old, other), newer)

        assertEquals(listOf(other, newer), result)
    }

    @Test
    fun olderUpdateCannotReplaceNewerDownloadedPackage() {
        val newer = pending("one.app", versionCode = 11)
        val older = pending("one.app", versionCode = 10)

        assertEquals(listOf(newer), PendingUpdateCatalog.upsert(listOf(newer), older))
    }

    @Test
    fun installedSameOrNewerMatchingVersionConfirmsDownload() {
        val pending = listOf(
            pending("older.app", versionCode = 10),
            pending("same.app", versionCode = 10),
            pending("newer.app", versionCode = 10)
        )

        val confirmed = PendingUpdateCatalog.confirmedPackages(
            pending,
            listOf(installed("older.app", 9), installed("same.app", 10), installed("newer.app", 11))
        )

        assertEquals(setOf("same.app", "newer.app"), confirmed)
    }

    @Test
    fun sameVersionWithDifferentSignatureDoesNotConfirmDownload() {
        val confirmed = PendingUpdateCatalog.confirmedPackages(
            listOf(pending("one.app", versionCode = 10)),
            listOf(installed("one.app", versionCode = 10, signingDigests = setOf("other")))
        )

        assertEquals(emptySet<String>(), confirmed)
    }

    private fun pending(packageName: String, versionCode: Long) = PendingUpdate(
        packageName = packageName,
        label = packageName,
        versionName = versionCode.toString(),
        versionCode = versionCode,
        filePaths = listOf("$packageName-$versionCode.apk"),
        signingDigests = setOf("digest"),
        source = ManagedSource.RUSTORE,
        downloadedAt = versionCode
    )

    private fun installed(
        packageName: String,
        versionCode: Long,
        signingDigests: Set<String> = setOf("digest")
    ) = InstalledApp(
        packageName = packageName,
        label = packageName,
        versionName = versionCode.toString(),
        versionCode = versionCode,
        lastUpdateTime = versionCode,
        source = InstallSource.OTHER,
        signingDigests = signingDigests
    )
}
