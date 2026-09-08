package dev.wystore.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SignatureCompatibilityPolicyTest {

    private val installed = "a".repeat(64)
    private val other = "b".repeat(64)

    @Test
    fun theSameFingerprintIsCompatible() {
        assertEquals(
            SignatureCompatibility.COMPATIBLE,
            SignatureCompatibilityPolicy.evaluate(setOf(installed), installed.uppercase())
        )
    }

    /**
     * The case that used to cost a full download: an app installed from somewhere else, whose
     * store build is signed with a different key and can never install over it.
     */
    @Test
    fun aDifferentDeclaredFingerprintIsAMismatch() {
        assertEquals(
            SignatureCompatibility.MISMATCH,
            SignatureCompatibilityPolicy.evaluate(setOf(installed), other)
        )
    }

    @Test
    fun nothingToCompareIsNotAMismatch() {
        assertEquals(
            SignatureCompatibility.UNKNOWN,
            SignatureCompatibilityPolicy.evaluate(setOf(installed), null)
        )
        assertEquals(
            SignatureCompatibility.UNKNOWN,
            SignatureCompatibilityPolicy.evaluate(setOf(installed), "не отпечаток")
        )
        assertEquals(
            SignatureCompatibility.UNKNOWN,
            SignatureCompatibilityPolicy.evaluate(emptySet(), installed)
        )
    }
}
