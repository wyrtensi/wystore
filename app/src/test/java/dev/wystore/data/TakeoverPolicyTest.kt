package dev.wystore.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TakeoverPolicyTest {

    private val digest = "a".repeat(64)
    private val otherDigest = "b".repeat(64)

    @Test
    fun `an app that is not installed is not a handover`() {
        val decision = TakeoverPolicy.decide(
            installedVersionCode = null,
            installedDigests = emptySet(),
            ownedByStore = false,
            catalogVersionCode = 10,
            catalogSignatureHint = digest
        )
        assertEquals(TakeoverPath.NONE, decision.path)
    }

    @Test
    fun `an app the store already owns needs no handover`() {
        val decision = TakeoverPolicy.decide(
            installedVersionCode = 10,
            installedDigests = setOf(digest),
            ownedByStore = true,
            catalogVersionCode = 10,
            catalogSignatureHint = digest
        )
        assertEquals(TakeoverPath.NONE, decision.path)
    }

    @Test
    fun `the same version installs over the top - that install is the handover`() {
        val decision = TakeoverPolicy.decide(
            installedVersionCode = 10,
            installedDigests = setOf(digest),
            ownedByStore = false,
            catalogVersionCode = 10,
            catalogSignatureHint = digest
        )
        assertEquals(TakeoverDecision(TakeoverPath.IN_PLACE), decision)
    }

    @Test
    fun `a newer catalogue build installs over the top`() {
        val decision = TakeoverPolicy.decide(
            installedVersionCode = 10,
            installedDigests = setOf(digest),
            ownedByStore = false,
            catalogVersionCode = 11,
            catalogSignatureHint = digest
        )
        assertEquals(TakeoverDecision(TakeoverPath.IN_PLACE), decision)
    }

    @Test
    fun `an older catalogue build has to replace the app instead`() {
        val decision = TakeoverPolicy.decide(
            installedVersionCode = 5031426,
            installedDigests = setOf(digest),
            ownedByStore = false,
            catalogVersionCode = 4031333,
            catalogSignatureHint = digest
        )
        assertEquals(
            TakeoverDecision(TakeoverPath.REPLACE, TakeoverObstacle.OLDER_IN_CATALOGUE),
            decision
        )
    }

    @Test
    fun `a different certificate has to replace the app, whatever the versions say`() {
        val decision = TakeoverPolicy.decide(
            installedVersionCode = 10,
            installedDigests = setOf(otherDigest),
            ownedByStore = false,
            catalogVersionCode = 11,
            catalogSignatureHint = digest
        )
        assertEquals(TakeoverDecision(TakeoverPath.REPLACE, TakeoverObstacle.SIGNATURE), decision)
    }

    @Test
    fun `an undeclared certificate rules nothing out and the archive decides later`() {
        val decision = TakeoverPolicy.decide(
            installedVersionCode = 10,
            installedDigests = setOf(digest),
            ownedByStore = false,
            catalogVersionCode = 11,
            catalogSignatureHint = null
        )
        assertEquals(TakeoverDecision(TakeoverPath.IN_PLACE), decision)
    }

    @Test
    fun `a source that states no version cannot rule out an in-place install`() {
        val decision = TakeoverPolicy.decide(
            installedVersionCode = 10,
            installedDigests = setOf(digest),
            ownedByStore = false,
            catalogVersionCode = null,
            catalogSignatureHint = digest
        )
        assertEquals(TakeoverDecision(TakeoverPath.IN_PLACE), decision)
    }
}
