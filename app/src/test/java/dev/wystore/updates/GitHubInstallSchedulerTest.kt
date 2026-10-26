package dev.wystore.updates

import dev.wystore.data.GitHubRepository
import dev.wystore.data.GitHubAsset
import dev.wystore.updates.model.QueueState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A GitHub queue row is created before the APK exists, so it cannot know the package name. It used
 * to store the release file name minus `.apk`, which the download worker then asserted against the
 * real package inside the archive — so every GitHub install failed verification.
 */
class GitHubInstallSchedulerTest {
    private val asset = GitHubAsset(7L, "app.apk", 1024L, "https://example.com/app.apk", "sha256:digest")

    @Test fun aReadyOwnershipApkIsReusedOnlyForTheSamePinnedFile() {
        assertFalse(GitHubInstallScheduler.mustRefetchOwnershipApk(QueueState.READY_TO_INSTALL.name, asset, asset))
        assertTrue(GitHubInstallScheduler.mustRefetchOwnershipApk(QueueState.READY_TO_INSTALL.name, null, asset))
        assertTrue(GitHubInstallScheduler.mustRefetchOwnershipApk(QueueState.READY_TO_INSTALL.name,
            asset.copy(id = 8L), asset))
        assertTrue(GitHubInstallScheduler.mustRefetchOwnershipApk(QueueState.READY_TO_INSTALL.name,
            asset.copy(digest = "sha256:other"), asset))
        assertTrue(GitHubInstallScheduler.mustRefetchOwnershipApk(QueueState.READY_TO_INSTALL.name,
            asset.copy(downloadUrl = "https://example.com/other.apk"), asset))
    }

    @Test
    fun aHandoverKeepsThePackageIdentityThatVerificationMustRequire() {
        val packageName = GitHubInstallScheduler.queuePackageName(
            GitHubRepository("owner", "repository"), expectedPackageName = "example.selected.app"
        )

        assertEquals("example.selected.app", packageName)
        assertFalse(GitHubInstallScheduler.isPlaceholder(packageName))
    }

    @Test
    fun aPlaceholderIsRecognisableAndDerivedFromTheRepository() {
        val placeholder = GitHubInstallScheduler.placeholderPackageName(
            GitHubRepository("wyrtensi", "CapturePort")
        )

        assertTrue(GitHubInstallScheduler.isPlaceholder(placeholder))
        assertEquals("pending.github.wyrtensi.captureport", placeholder)
    }

    @Test
    fun differentRepositoriesGetDifferentPlaceholders() {
        val first = GitHubInstallScheduler.placeholderPackageName(GitHubRepository("owner", "app"))
        val second = GitHubInstallScheduler.placeholderPackageName(GitHubRepository("owner", "other"))

        assertNotEquals(first, second)
    }

    @Test
    fun charactersThatCannotAppearInAPackageNameAreNormalised() {
        val placeholder = GitHubInstallScheduler.placeholderPackageName(
            GitHubRepository("amnezia-vpn", "amnezia-client")
        )

        assertEquals("pending.github.amnezia.vpn.amnezia.client", placeholder)
        assertTrue(
            "a placeholder must still look like a package name",
            placeholder.matches(Regex("[a-z0-9]+(\\.[a-z0-9]+)*"))
        )
    }

    @Test
    fun aRealPackageNameIsNotMistakenForAPlaceholder() {
        listOf(
            "org.telegram.messenger.web",
            "dev.wystore",
            "com.github.android"
        ).forEach { assertFalse(it, GitHubInstallScheduler.isPlaceholder(it)) }
    }
}
