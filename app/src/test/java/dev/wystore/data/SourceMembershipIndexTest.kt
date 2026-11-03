package dev.wystore.data

import dev.wystore.data.library.LibraryCard
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceMembershipIndexTest {
    private val repo = GitHubRepository("owner", "project")
    private fun managed(packageName: String, current: Boolean) = ManagedApp(
        packageName, packageName, emptySet(),
        githubRepository = if (current) repo else null,
        knownGithubRepository = if (current) null else repo
    )
    private fun card(packageName: String) = LibraryCard(packageName, packageName, "", null, false, emptyMap())

    @Test fun `combines memberships and preserves existing sources`() {
        val index = SourceMembershipIndex(true, true, listOf(card("shared")), setOf("shared"),
            listOf(managed("shared", false)), listOf("catalog"))
        assertEquals(setOf(ManagedSource.WY_STORE, ManagedSource.RUSTORE, ManagedSource.GITHUB),
            index.sourcesFor("shared", setOf(ManagedSource.RUSTORE)))
        assertEquals(setOf(ManagedSource.GITHUB), index.sourcesFor("catalog", emptySet()))
    }

    @Test fun `disabled sources add no membership and changed snapshots take effect`() {
        val disabled = SourceMembershipIndex(false, false, listOf(card("app")), emptySet(),
            listOf(managed("app", true)), listOf("app"))
        assertEquals(emptySet<ManagedSource>(), disabled.sourcesFor("app", emptySet()))
        val updated = SourceMembershipIndex(true, true, listOf(card("other")), emptySet(),
            listOf(managed("app", true)), emptyList())
        assertEquals(setOf(ManagedSource.GITHUB), updated.sourcesFor("app", emptySet()))
        assertEquals(setOf(ManagedSource.WY_STORE), updated.sourcesFor("other", emptySet()))
    }
}
