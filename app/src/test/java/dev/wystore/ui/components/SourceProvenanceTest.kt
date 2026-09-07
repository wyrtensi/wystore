package dev.wystore.ui.components

import dev.wystore.data.InstallSource
import dev.wystore.data.ManagedSource
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The reducer puts an enum constant into `PackageUiState.sourceProvenance` and the card translates
 * it with `sourceDisplayName`, whose fallback is to print the raw string. Only the managed sources
 * were listed there, so a card for an app the device got from Play showed a badge reading
 * "GOOGLE_PLAY".
 *
 * `sourceDisplayName` is a composable and cannot be called from here, so this pins the other half:
 * every constant the reducer can carry is one the table names. Adding a source without adding its
 * name fails here instead of on a screen.
 */
class SourceProvenanceTest {
    /** Kept in step with the `when` in SourceLabel.kt by hand; that is what this test guards. */
    private val named = setOf(
        ManagedSource.RUSTORE.name,
        ManagedSource.GITHUB.name,
        InstallSource.GOOGLE_PLAY.name,
        InstallSource.WY_STORE.name,
        InstallSource.OTHER.name
    )

    @Test
    fun everySourceTheReducerCanCarryHasAName() {
        val carried = ManagedSource.entries.map { it.name } + InstallSource.entries.map { it.name }
        assertEquals(emptyList<String>(), carried.filterNot { it in named })
    }
}
