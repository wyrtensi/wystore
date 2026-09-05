package dev.wystore.data

/**
 * Chooses which GitHub release to install and which of its assets to download.
 *
 * Real repositories break the naive "first release that is not a prerelease" rule in three ways:
 * some publish a rolling `nightly`/`canary` tag that is *not* flagged as a prerelease and always
 * sorts first; some publish releases with no APK at all (notes-only tags, or desktop-only builds);
 * and some attach unrelated `.apk` files — sing-box ships OpenWrt/Alpine packages that also end in
 * `.apk`. Picking blindly hands the user a router package or an empty release.
 */
object GitHubReleasePolicy {

    /** Tags that are not a stable build even when the release is not flagged `prerelease`. */
    private val UNSTABLE_TAG = Regex(
        "(?i)(^|[^a-z0-9])(nightly|canary|snapshot|preview|dev|alpha|beta|rc)([^a-z0-9]|\\d|$)"
    )

    /** ABI directory names, most specific first, as they appear in asset file names. */
    private val ABI_PREFERENCE = listOf("arm64-v8a", "arm64", "aarch64", "armeabi-v7a", "universal")

    fun isStable(release: GitHubRelease): Boolean =
        !release.prerelease && !UNSTABLE_TAG.containsMatchIn(release.tagName)

    /** Installable assets of one release, after applying an entry-specific name filter. */
    fun apkAssets(release: GitHubRelease, assetPattern: Regex? = null): List<GitHubAsset> =
        release.assets
            .filter { it.name.endsWith(".apk", ignoreCase = true) }
            .filter { assetPattern == null || assetPattern.containsMatchIn(it.name) }

    /**
     * Newest release that can actually be installed. Prefers a stable tag, then any non-prerelease,
     * then anything at all — but never returns a release with no installable asset.
     */
    fun selectRelease(releases: List<GitHubRelease>, assetPattern: Regex? = null): GitHubRelease? {
        val installable = releases.filter { apkAssets(it, assetPattern).isNotEmpty() }
        return installable.firstOrNull { isStable(it) }
            ?: installable.firstOrNull { !it.prerelease }
            ?: installable.firstOrNull()
    }

    /**
     * The asset to download for this device. Prefers a build for the device's own ABI over a
     * universal one, because universal APKs here run to hundreds of megabytes.
     */
    fun preferredAsset(
        assets: List<GitHubAsset>,
        supportedAbis: List<String> = emptyList()
    ): GitHubAsset? {
        if (assets.isEmpty()) return null
        if (assets.size == 1) return assets.single()
        val order = (supportedAbis.map { it.lowercase() } + ABI_PREFERENCE).distinct()
        for (abi in order) {
            assets.filter { it.name.lowercase().contains(abi) }
                .minByOrNull { it.sizeBytes }
                ?.let { return it }
        }
        // Nothing identifies an ABI, so take the smallest rather than the first listed.
        return assets.minByOrNull { it.sizeBytes }
    }
}
