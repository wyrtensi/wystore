package dev.wystore.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPagesTest {

    private val amnezia = GitHubRepository("amnezia-vpn", "amnezia-client")

    private fun managed(source: ManagedSource, repository: GitHubRepository? = null) = ManagedApp(
        packageName = "org.example.app",
        label = "Example",
        pinnedDigests = emptySet(),
        source = source,
        githubRepository = repository
    )

    private fun release(id: Long, tag: String, prerelease: Boolean = false, apk: Boolean = true) = GitHubRelease(
        id = id,
        tagName = tag,
        title = tag,
        description = "notes for $tag",
        publishedAt = null,
        prerelease = prerelease,
        assets = if (apk) listOf(GitHubAsset(id, "app.apk", 1, "https://github.com/x", null)) else emptyList()
    )

    @Test
    fun aRuStoreAppOpensItsCataloguePage() {
        val page = AppPages.resolve("org.example.app", "Example", managed = managed(ManagedSource.RUSTORE))

        assertEquals(AppPage.Store("org.example.app"), page)
    }

    @Test
    fun aGitHubRecordOpensTheRepositoryPage() {
        val page = AppPages.resolve("org.amnezia.vpn", "AmneziaVPN", managed = managed(ManagedSource.GITHUB, amnezia))

        assertTrue(page is AppPage.GitHub)
        val entry = (page as AppPage.GitHub).entry
        assertEquals(amnezia, entry.repository)
        assertTrue("a curated repository keeps its curated entry", entry.curated)
    }

    @Test
    fun aQueueRowWithARepositoryOpensItsPageWithoutARecord() {
        // A GitHub row's package name is a placeholder until verification.
        val page = AppPages.resolve("github:amnezia-vpn/amnezia-client", "", githubRepository = amnezia)

        assertEquals(amnezia, (page as AppPage.GitHub).entry.repository)
    }

    @Test
    fun aRepositoryNotInTheCatalogueStillHasAPage() {
        val repository = GitHubRepository("someone", "their-app")

        val entry = (AppPages.resolve("x", "Their App", githubRepository = repository) as AppPage.GitHub).entry

        assertEquals(repository, entry.repository)
        assertEquals("Their App", entry.title)
        assertEquals("someone", entry.publisher)
    }

    @Test
    fun theCatalogueIsMatchedWhateverTheCaseOfTheRecord() {
        val entry = AppPages.entryFor(GitHubRepository("Amnezia-VPN", "Amnezia-Client"), "")

        assertTrue(entry.curated)
    }

    @Test
    fun aGitHubSourceWithoutARepositoryFallsBackToTheCatalogue() {
        val page = AppPages.resolve("org.example.app", "Example", managed = managed(ManagedSource.GITHUB, null))

        assertEquals(AppPage.Store("org.example.app"), page)
    }

    @Test
    fun theNotesAreForTheDownloadedReleaseWhenThereIsOne() {
        val releases = listOf(release(3, "v3"), release(2, "v2"))

        assertEquals("v2", AppPages.releaseForNotes(releases, downloadedReleaseId = 2)?.tagName)
    }

    @Test
    fun otherwiseTheNotesAreForTheReleaseAnUpdateWouldInstall() {
        val releases = listOf(release(4, "nightly"), release(3, "v3-notes", apk = false), release(2, "v2"))

        assertEquals("v2", AppPages.releaseForNotes(releases)?.tagName)
        assertEquals("v2", AppPages.releaseForNotes(releases, downloadedReleaseId = 99)?.tagName)
    }

    @Test
    fun aRepositoryWithNoInstallableReleaseStillShowsItsNewestNotes() {
        val releases = listOf(release(2, "v2", apk = false))

        assertEquals("v2", AppPages.releaseForNotes(releases)?.tagName)
        assertNull(AppPages.releaseForNotes(emptyList()))
    }

    @Test
    fun aTagReadsAsAVersionUnderTheVersionLabel() {
        assertEquals("2.16.1", AppPages.versionOfTag("v2.16.1"))
        assertEquals("1.8.2", AppPages.versionOfTag("v.1.8.2"))
        assertEquals("3.0", AppPages.versionOfTag("V3.0"))
        assertEquals("nightly", AppPages.versionOfTag("nightly"))
        assertEquals("vivaldi-1", AppPages.versionOfTag("vivaldi-1"))
    }
}
