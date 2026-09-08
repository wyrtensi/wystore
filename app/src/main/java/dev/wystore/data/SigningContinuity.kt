package dev.wystore.data

/**
 * Whether an archive may update an app already installed under some certificate.
 *
 * The plain case is the same signer on both sides. The other one is a key rotation: since the v3
 * signing scheme a developer can move to a new key and ship an APK whose lineage carries the old
 * certificate, proving the two are the same identity. Android accepts such an update, so refusing
 * it here would mean Wy Store rejecting an update the platform was willing to install - and saying
 * "the signature does not match", the one sentence that should be reserved for a file that is not
 * what it claims to be.
 */
object SigningContinuity {

    fun allows(
        installedDigests: Set<String>,
        archiveDigests: Set<String>,
        archiveLineage: Set<String>
    ): Boolean {
        if (installedDigests.isEmpty() || archiveDigests.isEmpty()) return false
        if (installedDigests == archiveDigests) return true
        // Every certificate the phone trusts today has to appear in the archive's history; a
        // lineage that covers only part of a multi-signer install proves nothing.
        return archiveLineage.isNotEmpty() && archiveLineage.containsAll(installedDigests)
    }
}
