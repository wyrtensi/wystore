package dev.wystore.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MissingFromSourcePolicyTest {

    /** The first time is the finding. Every round after that is the same sentence again. */
    @Test
    fun anAppIsReportedMissingOnceRatherThanOnEveryCheck() {
        val first = MissingFromSourcePolicy.newlyMissing(
            known = emptySet(),
            missingNow = setOf("com.cubeSuite")
        )
        assertEquals(setOf("com.cubeSuite"), first)

        val second = MissingFromSourcePolicy.newlyMissing(
            known = setOf("com.cubeSuite"),
            missingNow = setOf("com.cubeSuite")
        )
        assertEquals(emptySet<String>(), second)
    }

    @Test
    fun aSecondMissingAppIsStillWorthSaying() {
        val newly = MissingFromSourcePolicy.newlyMissing(
            known = setOf("com.cubeSuite"),
            missingNow = setOf("com.cubeSuite", "com.laboratoriodamente")
        )

        assertEquals(setOf("com.laboratoriodamente"), newly)
    }

    /** Catalogues gain apps. Once the source answers for one, its absence is news again later. */
    @Test
    fun anAppTheSourceStartsAnsweringForIsForgotten() {
        val remembered = MissingFromSourcePolicy.remember(
            known = setOf("com.cubeSuite", "com.laboratoriodamente"),
            missingNow = emptySet(),
            answeredNow = setOf("com.cubeSuite")
        )

        assertEquals(setOf("com.laboratoriodamente"), remembered)

        assertEquals(
            setOf("com.cubeSuite"),
            MissingFromSourcePolicy.newlyMissing(remembered, setOf("com.cubeSuite"))
        )
    }

    /** A round that could not reach the source at all changes nothing either way. */
    @Test
    fun anUnreachableRoundLeavesTheListAsItWas() {
        val remembered = MissingFromSourcePolicy.remember(
            known = setOf("com.cubeSuite"),
            missingNow = emptySet(),
            answeredNow = emptySet()
        )

        assertEquals(setOf("com.cubeSuite"), remembered)
    }
}
