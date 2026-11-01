package dev.wystore.data.library

import kotlin.math.abs

enum class LibraryIncompatibility { ANDROID, ABI, FEATURES, GRAPHICS, SPLITS, NO_VARIANT }
sealed interface LibrarySelection {
    data class Compatible(val version: ResolvedLibraryVersion) : LibrarySelection
    data class Unsupported(val reasons: Set<LibraryIncompatibility>) : LibrarySelection
}
data class ResolvedLibraryVersion(
    val packageName: String, val label: String, val variantId: String,
    val versionCode: Long, val versionName: String, val publicationHash: String,
    val certificates: Set<String>, val certificateScopeSdk: Int?,
    val members: List<LibraryMember>, val artifacts: List<LibraryArtifact>
) {
    val totalBytes: Long get() = artifacts.fold(0L) { total, item -> Math.addExact(total, item.size) }
    fun preliminaryCertificates(sdk: Int): Set<String> =
        if (certificateScopeSdk == null || certificateScopeSdk == sdk) certificates else emptySet()
}

/** Select one complete delivered set; profiles are sampling cohorts rather than device model requirements. */
object LibraryVariantPolicy {
    fun select(app: LibraryApplication, device: LibraryDeviceSpec): LibrarySelection {
        val failures = linkedSetOf<LibraryIncompatibility>()
        val candidates = app.variants.filter { variant ->
            val c = variant.compatibility
            val reason = when {
                c.minSdk > device.sdk -> LibraryIncompatibility.ANDROID
                c.nativeAbis.isNotEmpty() && device.abis.none { it in c.nativeAbis } -> LibraryIncompatibility.ABI
                !device.features.containsAll(c.requiredFeatures) -> LibraryIncompatibility.FEATURES
                c.glEsVersion > device.glEsVersion -> LibraryIncompatibility.GRAPHICS
                !c.providedSplitTypes.containsAll(c.requiredSplitTypes) ||
                    !variant.files.map { it.split }.toSet().containsAll(c.usesSplits) -> LibraryIncompatibility.SPLITS
                else -> null
            }
            if (reason != null) failures += reason
            reason == null
        }
        val selected = candidates.sortedWith(compareByDescending<LibraryVariant> { it.versionCode }
            .thenBy { variant -> device.abis.indexOfFirst { it in variant.compatibility.nativeAbis }.takeIf { it >= 0 } ?: device.abis.size }
            .thenBy { variant -> localeRank(variant.compatibility, device.locales) }
            .thenBy { abs(it.compatibility.density.toLong() - device.density) }
            .thenBy { it.id }).firstOrNull()
            ?: return LibrarySelection.Unsupported(failures.ifEmpty { setOf(LibraryIncompatibility.NO_VARIANT) })
        return LibrarySelection.Compatible(resolve(app, selected))
    }

    fun resolve(app: LibraryApplication, selected: LibraryVariant): ResolvedLibraryVersion {
        require(selected in app.variants) { "Variant does not belong to application" }
        val members = selected.files.sortedBy { if (it.role == "base") 0 else 1 }
        return ResolvedLibraryVersion(app.packageName, app.name, selected.id,
            selected.versionCode, selected.versionName, app.publication.catalog.sha256,
            selected.certificates, selected.certificateScopeSdk, members,
            members.map { app.artifacts.getValue(it.artifact) })
    }

    private fun localeRank(c: LibraryCompatibility, preferred: List<String>): Int {
        val language = c.locale.substringBefore('_')
        val exact = preferred.indexOfFirst { it == c.locale }
        if (exact >= 0) return exact
        val sameLanguage = preferred.indexOfFirst { it.substringBefore('_') == language }
        if (sameLanguage >= 0) return preferred.size + sameLanguage
        return if (language == "en") preferred.size * 2 else preferred.size * 2 + 1
    }
}
