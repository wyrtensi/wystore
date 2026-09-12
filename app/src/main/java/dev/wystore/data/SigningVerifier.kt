package dev.wystore.data

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

data class ArchiveIdentity(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val splitName: String?,
    val signingDigests: Set<String>,
    /**
     * Every certificate this APK proves it descends from, newest last.
     *
     * A developer may rotate a signing key: the new APK carries a lineage showing the old
     * certificate signed over the new one, and Android accepts it as an update to an app installed
     * under the old key. Comparing only the current signer rejects exactly those updates.
     */
    val signingLineage: Set<String> = emptySet()
)

/**
 * Why an APK set was rejected. The reason is a code rather than a message so the UI can render it
 * in the user's language; the layer that produces it has no resources and used to hand a Russian
 * sentence straight to the snackbar.
 */
enum class VerificationError {
    NOT_AN_APK,
    NO_BASE_APK,
    MIXED_VERSIONS,
    WRONG_PACKAGE,
    UNREADABLE_SIGNATURE,
    MIXED_SIGNATURES,
    SOURCE_FINGERPRINT_MISMATCH,
    WRONG_PACKAGE_FOR_UPDATE,
    DOWNGRADE,
    SIGNATURE_MISMATCH
}

data class VerificationResult(
    val identity: ArchiveIdentity,
    val error: VerificationError? = null,
    val plan: VerifiedInstallPlan? = null
) {
    val isValid: Boolean get() = error == null
}

class VerifiedInstallPlan internal constructor(
    internal val files: List<File>,
    val identity: ArchiveIdentity
)

object SigningVerifier {
    @Suppress("DEPRECATION")
    fun installedDigests(info: PackageInfo): Set<String> = signatures(info)
        .map { signature -> sha256(signature.toByteArray()) }
        .toSet()

    /**
     * The certificates an APK's signing history proves it succeeds, empty when it has none.
     *
     * Only meaningful for a single-signer APK: with several signers there is no rotation to speak
     * of and the platform reports none.
     */
    fun lineageDigests(info: PackageInfo): Set<String> {
        val signingInfo = info.signingInfo ?: return emptySet()
        if (signingInfo.hasMultipleSigners()) return emptySet()
        return runCatching {
            signingInfo.signingCertificateHistory
                ?.map { certificate -> sha256(certificate.toByteArray()) }
                ?.toSet()
                .orEmpty()
        }.getOrDefault(emptySet())
    }

    @Suppress("DEPRECATION")
    fun archiveIdentity(packageManager: PackageManager, file: File): ArchiveIdentity? {
        val info = packageManager.getPackageArchiveInfo(file.absolutePath, SigningFlags.forArchive())
            ?: return null
        return ArchiveIdentity(
            packageName = info.packageName,
            versionName = info.versionName.orEmpty(),
            versionCode = info.versionCodeCompat(),
            splitName = info.splitNames?.singleOrNull(),
            signingDigests = installedDigests(info),
            signingLineage = lineageDigests(info)
        )
    }

    /**
     * @param allowReinstall lets the same version through. A reinstall is what "hand updates to Wy
     * Store" and "reinstall" actually are: the file is not newer, and that is the point - the
     * install is what makes Wy Store the installer of record. Refusing it as a downgrade meant
     * those buttons could never do anything. An older version is still refused either way.
     */
    /**
     * @param acceptedSourceDigest a signature the user has already accepted for this app in place
     * of the one [expectedSourceDigest] advertises. The source's own metadata disagreeing with the
     * file the source served is the one refusal a user can answer for - see
     * [dev.wystore.updates.UnverifiedSourceConsent] - and they are only ever asked after this check
     * has refused the file once, with both fingerprints in front of them. It waives that one
     * comparison and no other: a file signed by anyone else still does not match what was accepted,
     * and every remaining check runs, including the signature of an installed app, which Android
     * enforces again.
     */
    fun verifyArtifacts(
        packageManager: PackageManager,
        files: List<File>,
        installed: InstalledApp?,
        expectedPackageName: String?,
        expectedSourceDigest: String? = null,
        allowReinstall: Boolean = false,
        acceptedSourceDigest: String? = null
    ): VerificationResult {
        if (files.isEmpty() || files.any { !isApkContainer(it) }) {
            return invalid(VerificationError.NOT_AN_APK)
        }
        val identities = files.map { archiveIdentity(packageManager, it) }
        val base = identities.filterNotNull().singleOrNull { it.splitName.isNullOrBlank() }
            ?: return invalid(VerificationError.NO_BASE_APK)
        if (identities.filterNotNull().any { it.packageName != base.packageName || it.versionCode != base.versionCode }) {
            return invalid(VerificationError.MIXED_VERSIONS)
        }
        if (expectedPackageName != null && base.packageName != expectedPackageName) return invalid(VerificationError.WRONG_PACKAGE)
        if (base.signingDigests.isEmpty()) return invalid(VerificationError.UNREADABLE_SIGNATURE)
        if (identities.filterNotNull().any { it.signingDigests != base.signingDigests || it.signingDigests.isEmpty() }) {
            return invalid(VerificationError.MIXED_SIGNATURES)
        }
        expectedSourceDigest?.lowercase()?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }?.let { digest ->
            val accepted = acceptedSourceDigest?.lowercase()
            if (digest !in base.signingDigests && accepted !in base.signingDigests) {
                // Carries the identity, so whoever asks the user about this can tell them - and
                // later recognise - which signature the file actually has.
                return invalid(VerificationError.SOURCE_FINGERPRINT_MISMATCH, base)
            }
        }
        if (installed != null) {
            if (installed.packageName != base.packageName) return invalid(VerificationError.WRONG_PACKAGE_FOR_UPDATE)
            if (base.versionCode < installed.versionCode) return invalid(VerificationError.DOWNGRADE)
            if (base.versionCode == installed.versionCode && !allowReinstall) {
                return invalid(VerificationError.DOWNGRADE)
            }
            if (!SigningContinuity.allows(
                    installedDigests = installed.signingDigests,
                    archiveDigests = base.signingDigests,
                    archiveLineage = base.signingLineage
                )
            ) {
                return invalid(VerificationError.SIGNATURE_MISMATCH)
            }
        }
        // Some Android 16 builds do not expose signing data for standalone split APKs.
        // The base APK is checked here; Package Manager revalidates every split and its signature at session commit.
        return VerificationResult(base, plan = VerifiedInstallPlan(files.map { it.canonicalFile }, base))
    }

    private fun isApkContainer(file: File): Boolean = runCatching {
        if (!file.isFile || file.length() <= 0L) return false
        ZipFile(file).use { archive ->
            archive.getEntry("AndroidManifest.xml")?.size?.let { it > 0L } == true
        }
    }.getOrDefault(false)

    /**
     * Whichever of the two the platform actually filled in.
     *
     * [PackageInfo.signingInfo] is the right field and the one to prefer, but for an APK read off
     * disk Android 10 leaves it empty, and reading only that turned every finished download into
     * an unreadable signature there. The legacy array carries the same certificates out of the
     * same file; both are hashed the same way and compared against an installed app's, so the
     * comparison stays like for like whichever one answered.
     */
    @Suppress("DEPRECATION")
    private fun signatures(info: PackageInfo): Array<out android.content.pm.Signature> =
        info.signingInfo?.apkContentsSigners?.takeIf { it.isNotEmpty() }
            ?: info.signatures
            ?: emptyArray()

    private fun invalid(
        reason: VerificationError,
        identity: ArchiveIdentity = ArchiveIdentity("", "", 0, null, emptySet())
    ): VerificationResult = VerificationResult(identity, reason)

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}

private fun PackageInfo.versionCodeCompat(): Long = longVersionCode
