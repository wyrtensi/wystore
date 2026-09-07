package dev.wystore.data

import dev.wystore.updates.GitHubInstallScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A GitHub queue row is created before the APK exists, so it carries a placeholder package name.
 * Nothing in the catalogue cache matches a placeholder, which is why those rows were the only ones
 * in the queue with no icon and no app name.
 */
class GitHubPlaceholderIconTest {
    private val entry = GitHubCatalog.entries().first()

    @Test
    fun findsTheEntryARowWasCreatedFrom() {
        val placeholder = GitHubInstallScheduler.placeholderPackageName(entry.repository)
        val found = GitHubCatalog.findByPlaceholder(placeholder)
        assertNotNull(found)
        assertEquals(entry.slug, found?.slug)
    }

    @Test
    fun everyCuratedEntryIsReachableFromItsPlaceholder() {
        GitHubCatalog.entries().filter { it.curated }.forEach { curated ->
            val placeholder = GitHubInstallScheduler.placeholderPackageName(curated.repository)
            assertEquals(
                "no catalogue entry for ${curated.slug} via $placeholder",
                curated.slug,
                GitHubCatalog.findByPlaceholder(placeholder)?.slug
            )
        }
    }

    /** The exact placeholder a row on the test device carries. */
    @Test
    fun findsTheRowFromTheDeviceQueue() {
        val found = GitHubCatalog.findByPlaceholder("pending.github.romanvht.byedpiandroid")
        assertEquals("romanvht/ByeDPIAndroid", found?.slug)
        assertNotNull(found?.iconUrl)
    }

    @Test
    fun aRealPackageNameIsNotAPlaceholder() {
        assertNull(GitHubCatalog.findByPlaceholder("ru.sberbankmobile"))
    }
}
