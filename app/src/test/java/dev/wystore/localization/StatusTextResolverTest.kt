package dev.wystore.localization

import dev.wystore.ui.components.StatusCode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The resolver used to hold a Russian and an English sentence per state in Kotlin. The text itself
 * now lives in resources; what still needs pinning is that every state has one, and that no two
 * states share a string — a duplicate would make two different situations read identically.
 */
class StatusTextResolverTest {

    @Test
    fun everyStateHasItsOwnString() {
        val byResource = StatusCode.entries.groupBy { StatusTextResolver.stringRes(it) }

        val shared = byResource.filterValues { it.size > 1 }
        assertEquals("states sharing one string: $shared", emptyMap<Int, List<StatusCode>>(), shared)
        assertEquals(StatusCode.entries.size, byResource.size)
    }

    @Test
    fun noStateResolvesToTheMissingResourceId() {
        StatusCode.entries.forEach { code ->
            assertEquals(
                "$code must map to a real string resource",
                false,
                StatusTextResolver.stringRes(code) == 0
            )
        }
    }
}
