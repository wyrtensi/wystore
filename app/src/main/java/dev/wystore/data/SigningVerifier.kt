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
        if (Build.VERSION.SDK_INT < 28) return emptySet()
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
        val flags = SigningFlags.forSdk(Build.VERSION.SDK_INT)
        val info = packageManager.getPackageArchiveInfo(file.absolutePath, flags) ?: return null
        return ArchiveIdentity(
            packageName = info.packageName,
            versionName = info.versionName.orEmpty(),
            versionCode = info.versionCodeCompat(),
            splitName = info.splitNames?.singleOrNull(),
            signingDigests = installedDigests(info),
            signingLineage = lineageDigests(info)
        )
    }

    fun verifyArtifacts(
        packageManager: PackageManager,
        files: List<File>,
        installed: InstalledApp?,
        expectedPackageName: String?,
        expectedSourceDigest: String? = null
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
            if (digest !in base.signingDigests) return invalid(VerificationError.SOURCE_FINGERPRINT_MISMATCH)
        }
        if (installed != null) {
            if (installed.packageName != base.packageName) return invalid(VerificationError.WRONG_PACKAGE_FOR_UPDATE)
            if (base.versionCode <= installed.versionCode) return invalid(VerificationError.DOWNGRADE)
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

    @Suppress("DEPRECATION")
    private fun signatures(info: PackageInfo): Array<out android.content.pm.Signature> = when {
        Build.VERSION.SDK_INT >= 28 -> info.signingInfo?.apkContentsSigners ?: emptyArray()
        else -> info.signatures ?: emptyArray()
    }

    private fun invalid(reason: VerificationError): VerificationResult = VerificationResult(
        ArchiveIdentity("", "", 0, null, emptySet()),
        reason
    )

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}

@Suppress("DEPRECATION")
private fun PackageInfo.versionCodeCompat(): Long = if (Build.VERSION.SDK_INT >= 28) longVersionCode else versionCode.toLong()
