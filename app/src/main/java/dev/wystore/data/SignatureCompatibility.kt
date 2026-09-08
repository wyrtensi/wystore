package dev.wystore.data

/** Whether an update from a source can install over what is already on the phone. */
enum class SignatureCompatibility {
    /** The source's certificate is the one the installed app carries. */
    COMPATIBLE,

    /** The source declares a different certificate: Android will refuse to update in place. */
    MISMATCH,

    /** Not enough is known - no declared fingerprint, or the installed signature is unreadable. */
    UNKNOWN
}

/**
 * Compares what a source says it signs with against what the phone already has.
 *
 * RuStore declares a fingerprint in the app's details, and the check already fetches those details,
 * so a package the store cannot install over can be recognised before a single byte is downloaded.
 * Without this the mismatch surfaced only after the whole APK had arrived - as a red row saying the
 * signature did not pass, which reads as tampering rather than as "this app came from somewhere
 * else and has to be reinstalled to move".
 *
 * [MISMATCH] is a strong signal, not a proof: a developer who rotated their signing key ships an
 * APK whose lineage covers the old certificate, and that lineage is only readable from the file
 * itself. So this decides what to start on its own, never what to forbid - an update the user asks
 * for by hand is still downloaded, and [SigningVerifier] rules on it with the archive in hand.
 */
object SignatureCompatibilityPolicy {

    private val FINGERPRINT = Regex("[0-9a-f]{64}")

    fun evaluate(installedDigests: Set<String>, declaredFingerprint: String?): SignatureCompatibility {
        val declared = declaredFingerprint?.trim()?.lowercase()?.takeIf { it.matches(FINGERPRINT) }
            ?: return SignatureCompatibility.UNKNOWN
        if (installedDigests.isEmpty()) return SignatureCompatibility.UNKNOWN
        return if (declared in installedDigests.map { it.lowercase() }) {
            SignatureCompatibility.COMPATIBLE
        } else {
            SignatureCompatibility.MISMATCH
        }
    }
}
