package dev.wystore.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the phone is told when the source cannot confirm its own app.
 *
 * RuStore states 5b2be9db... for ru.gdemoideti.parent and serves a file signed 26d83b54... Once the
 * user has been asked about that and answered, the copy on the phone is the file RuStore serves -
 * so "this app is signed with a different key, remove it and install it again" is both wrong and
 * blames the phone for the catalogue's disagreement.
 */
class SignatureCompatibilityAcceptedTest {

    private val advertised = "5b2be9db4566d49179b6b4ca383fbafd0e8012ea5d3926def98505090eb2d241"
    private val served = "26d83b5434593692fbf1c7260a641ff9a8b791a2192832817618533992693da1"
    private val stranger = "0bc473ae8f9488d0d610a3434880d11c198a81b91867a456ab373150e62d807e"

    @Test
    fun `the installed file being the one the source serves is not the phone's fault`() {
        assertEquals(
            SignatureCompatibility.SOURCE_UNCONFIRMED,
            SignatureCompatibilityPolicy.evaluate(setOf(served), advertised, acceptedFingerprint = served)
        )
    }

    @Test
    fun `and an app signed by anyone else still is a mismatch`() {
        assertEquals(
            SignatureCompatibility.MISMATCH,
            SignatureCompatibilityPolicy.evaluate(setOf(stranger), advertised, acceptedFingerprint = served)
        )
        assertEquals(
            SignatureCompatibility.MISMATCH,
            SignatureCompatibilityPolicy.evaluate(setOf(served), advertised, acceptedFingerprint = null)
        )
    }

    @Test
    fun `a source that agrees with itself needs no answer at all`() {
        assertEquals(
            SignatureCompatibility.COMPATIBLE,
            SignatureCompatibilityPolicy.evaluate(setOf(advertised), advertised, acceptedFingerprint = served)
        )
    }

    /**
     * The update check refuses to fetch what cannot install, and the app page offers to remove and
     * reinstall. Both would have been wrong for an app whose only problem is the catalogue: one
     * would have stopped its updates for good, the other would have cost its data for nothing.
     */
    @Test
    fun `an accepted app is not sent through a reinstall`() {
        val decision = TakeoverPolicy.decide(
            installedVersionCode = 2012071,
            installedDigests = setOf(served),
            ownedByStore = false,
            catalogVersionCode = 2012071,
            catalogSignatureHint = advertised,
            acceptedSourceDigest = served
        )

        assertEquals(TakeoverPath.IN_PLACE, decision.path)
    }

    @Test
    fun `while an app from somewhere else still is`() {
        val decision = TakeoverPolicy.decide(
            installedVersionCode = 2012071,
            installedDigests = setOf(stranger),
            ownedByStore = false,
            catalogVersionCode = 2012071,
            catalogSignatureHint = advertised,
            acceptedSourceDigest = served
        )

        assertEquals(TakeoverPath.REPLACE, decision.path)
        assertEquals(TakeoverObstacle.SIGNATURE, decision.obstacle)
    }
}
