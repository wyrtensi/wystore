package dev.wystore.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubCatalogTest {

    @Test
    fun theShippedCatalogueIsBrowsableWithoutAnythingAdded() {
        val entries = GitHubCatalog.entries()

        assertTrue("the shipped catalogue must not be empty", entries.size >= 10)
        assertTrue(entries.all { it.curated })
        assertTrue("a store entry needs a display title", entries.all { it.title.isNotBlank() })
        assertTrue("a curated entry needs a summary", entries.all { it.summary.isNotBlank() })
        assertEquals("slugs must be unique", entries.size, entries.map { it.slug }.distinct().size)
        assertTrue(
            "the original two entries must still be there",
            entries.map { it.slug }.containsAll(
                listOf("amnezia-vpn/amnezia-client", "wyrtensi/CapturePort")
            )
        )
    }

    @Test
    fun assetFiltersAreValidAndOnlyWhereTheyAreNeeded() {
        val byName = GitHubCatalog.CURATED.associateBy { it.title }

        // sing-box attaches ~40 OpenWrt/Alpine .apk packages alongside its Android build.
        assertEquals("^SFA-", byName.getValue("sing-box").assetNamePattern)
        assertNotNull(byName.getValue("sing-box").assetPattern())
        // SmartTube publishes stable and beta under different package ids from one repo.
        assertEquals("^SmartTube_stable", byName.getValue("SmartTube").assetNamePattern)

        assertNull("entries with clean releases need no filter", byName.getValue("v2rayNG").assetNamePattern)
        // Every declared pattern must compile.
        GitHubCatalog.CURATED.forEach { it.assetPattern() }
    }

    @Test
    fun repositoriesTheUserAddedAreBrowsableToo() {
        val entries = GitHubCatalog.entries(listOf(GitHubRepository("someone", "their-app")))

        assertEquals(GitHubCatalog.CURATED.size + 1, entries.size)
        val added = entries.single { it.slug == "someone/their-app" }
        assertFalse(added.curated)
        assertEquals("their-app", added.title)
        assertEquals("someone", added.publisher)
    }

    @Test
    fun addingARepositoryThatIsAlreadyCuratedDoesNotDuplicateIt() {
        val entries = GitHubCatalog.entries(
            listOf(GitHubRepository("amnezia-vpn", "amnezia-client"), GitHubRepository("Amnezia-VPN", "Amnezia-Client"))
        )

        assertEquals(GitHubCatalog.CURATED.size, entries.size)
        assertTrue(
            "the curated presentation must win",
            entries.single { it.slug == "amnezia-vpn/amnezia-client" }.curated
        )
    }

    @Test
    fun searchMatchesProductNameOwnerAndRepositoryName() {
        // Both Amnezia entries share the owner, so the query matches each of them.
        assertEquals(
            listOf("amnezia-vpn/amnezia-client", "amnezia-vpn/amneziawg-android"),
            GitHubCatalog.search("amnezia").map { it.slug }.sorted()
        )
        assertEquals(
            listOf("wyrtensi/CapturePort"),
            GitHubCatalog.search("captureport").map { it.slug }
        )
        assertEquals(
            listOf("wyrtensi/CapturePort"),
            GitHubCatalog.search("wyrtensi").map { it.slug }
        )
        assertEquals(listOf("2dust/v2rayNG"), GitHubCatalog.search("v2rayng").map { it.slug })
        // Molly describes itself as a Signal fork, so it legitimately matches too; Signal ranks first.
        assertEquals("signalapp/Signal-Android", GitHubCatalog.search("signal").first().slug)
    }

    @Test
    fun searchIsCaseInsensitiveAndMatchesSummaryAndCategory() {
        assertEquals(
            GitHubCatalog.search("amnezia").map { it.slug },
            GitHubCatalog.search("AMNEZIA").map { it.slug }
        )
        // "Обход блокировок" is a category, and the summaries are Russian.
        assertTrue(
            GitHubCatalog.search("обход блокировок").map { it.slug }.contains("romanvht/ByeDPIAndroid")
        )
        assertTrue(GitHubCatalog.search("vpn").map { it.slug }.contains("amnezia-vpn/amnezia-client"))
        assertTrue(GitHubCatalog.search("прокси").map { it.slug }.contains("2dust/v2rayNG"))
    }

    @Test
    fun anExactOwnerSlashNameQueryIsRankedFirst() {
        val results = GitHubCatalog.search(
            "wyrtensi/CapturePort",
            listOf(GitHubRepository("wyrtensi", "CapturePort-extras"))
        )

        assertEquals("wyrtensi/CapturePort", results.first().slug)
    }

    @Test
    fun curatedEntriesOutrankUserAddedOnesForTheSameTerm() {
        val results = GitHubCatalog.search("amnezia", listOf(GitHubRepository("fork", "amnezia-mirror")))

        assertEquals("amnezia-vpn/amnezia-client", results.first().slug)
    }

    @Test
    fun aBlankQueryMatchesNothing() {
        assertTrue(GitHubCatalog.search("").isEmpty())
        assertTrue(GitHubCatalog.search("   ").isEmpty())
    }

    @Test
    fun aQueryThatMatchesNothingReturnsNothing() {
        assertTrue(GitHubCatalog.search("zzqqxxwwvv").isEmpty())
        assertTrue(GitHubCatalog.search("сбербанк").isEmpty())
    }

    @Test
    fun findResolvesBySlugCaseInsensitively() {
        assertNotNull(GitHubCatalog.find("wyrtensi/CapturePort"))
        assertNotNull(GitHubCatalog.find("WYRTENSI/captureport"))
        assertNull(GitHubCatalog.find("nobody/nothing"))
    }

    @Test
    fun anEntryUsesItsOwnIconWhenTheProjectShipsOneAndTheOwnerAvatarOtherwise() {
        val capturePort = GitHubCatalog.find("wyrtensi/CapturePort")!!
        assertTrue(
            "the project icon must win over the owner avatar",
            capturePort.iconUrl.startsWith("https://raw.githubusercontent.com/wyrtensi/CapturePort/")
        )

        val amnezia = GitHubCatalog.find("amnezia-vpn/amnezia-client")!!
        assertEquals("https://github.com/amnezia-vpn.png?size=200", amnezia.iconUrl)

        // An override on a host GitHub does not control is ignored rather than rendered.
        val spoofed = amnezia.copy(iconOverrideUrl = "https://evil.example/icon.png")
        assertEquals("https://github.com/amnezia-vpn.png?size=200", spoofed.iconUrl)
    }

    @Test
    fun onlyGitHubOwnedImageHostsAreTrusted() {
        listOf(
            "https://avatars.githubusercontent.com/u/1?v=4",
            "https://raw.githubusercontent.com/owner/repo/main/icon.png"
        ).forEach { assertTrue(it, GitHubUrlPolicy.isTrustedImage(it)) }

        listOf(
            null,
            "",
            "http://avatars.githubusercontent.com/u/1",
            "https://evil.example/icon.png",
            "https://avatars.githubusercontent.com.evil.example/icon.png",
            "https://user:pass@avatars.githubusercontent.com/u/1"
        ).forEach { assertFalse(it.orEmpty(), GitHubUrlPolicy.isTrustedImage(it)) }
    }
}
