package dev.wystore.data

import org.junit.Assert.*
import org.junit.Test

class AppSourceKnowledgeTest {
    @Test fun `leaving a custom GitHub source retains its verified package association`() {
        val repo = GitHubRepository("example", "custom")
        val current = ManagedApp("example.app", "App", emptySet(), source = ManagedSource.GITHUB, githubRepository = repo)
        assertEquals(repo, AppSourceKnowledge.remember(current, null))
        val otherSource = current.copy(source = ManagedSource.WY_STORE, githubRepository = null,
            knownGithubRepository = AppSourceKnowledge.remember(current, null))
        assertEquals(repo, AppSourceKnowledge.remember(otherSource, null))
        assertEquals("example.app", AppSourceKnowledge.packageFor(repo, listOf(otherSource)))
    }

    @Test fun `a repository known to publish several packages does not invent a search identity`() {
        val repo = GitHubRepository("example", "multiple")
        val first = ManagedApp("example.first", "First", emptySet(), knownGithubRepository = repo)
        assertNull(AppSourceKnowledge.packageFor(repo, listOf(first, first.copy(packageName = "example.second"))))
    }
}
