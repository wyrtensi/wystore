package dev.wystore.data

import androidx.test.platform.app.InstrumentationRegistry
import dev.wystore.data.library.LibraryCard
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.system.measureNanoTime

class SourceDecorationPerformanceTest {
    @Test fun indexedBadgesMatchThePreviousLookupAcrossALargeCatalog() {
        val curated = GitHubCatalog.entries()
        val packages = curated.mapNotNull { it.packageName }.take(63)
        val cards = packages.map { LibraryCard(it, it, "", null, true, emptyMap()) }
        val inputs = List(1000) { packages[it % packages.size] }
        fun previous(): List<Set<ManagedSource>> = inputs.map { pkg ->
            mutableSetOf(ManagedSource.RUSTORE).apply {
                if (cards.any { it.packageName == pkg }) add(ManagedSource.WY_STORE)
                if (GitHubCatalog.entries().any { it.packageName == pkg }) add(ManagedSource.GITHUB)
            }
        }
        val index = SourceMembershipIndex(true, true, cards, emptySet(), emptyList(), curated.mapNotNull { it.packageName })
        fun indexed() = inputs.map { index.sourcesFor(it, setOf(ManagedSource.RUSTORE)) }
        assertEquals(previous(), indexed())
        val old = List(5) { measureNanoTime { previous() } }.sorted()[2] / 1_000_000.0
        val new = List(5) { measureNanoTime { indexed() } }.sorted()[2] / 1_000_000.0
        InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply {
            putString("stream", "Source badges, 1000 cards, median of 5: previous=${old}ms indexed=${new}ms\n")
        })
    }
}
