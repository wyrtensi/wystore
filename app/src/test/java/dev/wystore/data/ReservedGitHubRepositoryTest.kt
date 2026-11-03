package dev.wystore.data

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class ReservedGitHubRepositoryTest {
    private val source = GitHubReleaseSource()

    @Test
    fun reservedRepositoriesCannotBeAddedByNameOrUrl() {
        listOf("wyrtensi/wystore-library", "WyRtEnSi/WyStOrE", "https://github.com/wyrtensi/wystore-library.git", "https://github.com/WYRTENSI/WYSTORE.git/").forEach { input ->
            try {
                source.parseRepository(input)
                fail("Expected rejection of $input")
            } catch (_: SourceFormatException) {
                // The repository is reserved for its dedicated source.
            }
        }
    }

    @Test
    fun unrelatedRepositoriesRemainAvailable() {
        assertEquals(GitHubRepository("someone", "wystore"), source.parseRepository("someone/wystore"))
        assertEquals(GitHubRepository("wyrtensi", "another"), source.parseRepository("wyrtensi/another"))
    }
    @Test
    fun backupRejectsReservedCurrentAndRememberedRepositoriesButKeepsOtherApps() {
        val reserved = GitHubRepository("WYRTENSI", "wystore-library.git")
        val ordinary = ManagedApp("org.example", "Example", emptySet(), githubRepository = GitHubRepository("someone", "app"))
        val self = ManagedApp("app.wystore", "Wy Store", emptySet(), githubRepository = reserved)
        ReservedGitHubRepository.requireAllowedManaged(listOf(ordinary, self), "app.wystore")
        for (invalid in listOf(ordinary.copy(githubRepository = reserved), ordinary.copy(knownGithubRepository = reserved))) {
            try {
                ReservedGitHubRepository.requireAllowedManaged(listOf(ordinary, invalid), "app.wystore")
                fail("Expected rejection of a reserved managed repository")
            } catch (_: SourceFormatException) {
                // Reject the entire import before making changes.
            }
        }
    }
}
