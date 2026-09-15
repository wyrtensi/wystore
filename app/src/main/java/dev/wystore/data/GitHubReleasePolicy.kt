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

    /** How release assets spell each Android ABI in their file names. */
    private val ABI_ALIASES = mapOf(
        "arm64-v8a" to listOf("arm64-v8a", "arm64", "aarch64", "armv8"),
        "armeabi-v7a" to listOf("armeabi-v7a", "armeabi", "armv7", "arm32"),
        "armeabi" to listOf("armeabi"),
        "x86_64" to listOf("x86_64", "x86-64", "x64", "amd64"),
        "x86" to listOf("x86", "i686", "i386")
    )
    private val UNIVERSAL_ALIASES = listOf("universal", "all", "fat")

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
        // The device's own ABIs, in the device's order of preference.
        for (abi in supportedAbis.map { it.lowercase() }) {
            val aliases = ABI_ALIASES[abi] ?: listOf(abi)
            assets.filter { asset -> aliases.any { nameHasToken(asset.name, it) } }
                .minByOrNull { it.sizeBytes }
                ?.let { return it }
        }
        UNIVERSAL_ALIASES.forEach { alias ->
            assets.filter { nameHasToken(it.name, alias) }.minByOrNull { it.sizeBytes }?.let { return it }
        }
        // A build that names no ABI at all is presumably for every ABI. One that names another
        // ABI is not: a 32-bit TV box handed the arm64 build fails with NO_MATCHING_ABIS.
        val allAliases = ABI_ALIASES.values.flatten()
        assets.filter { asset -> allAliases.none { nameHasToken(asset.name, it) } }
            .minByOrNull { it.sizeBytes }
            ?.let { return it }
        // Every build is for some other ABI. The smallest still fails, but with the installer's
        // own explanation rather than a silent "nothing to install".
        return assets.minByOrNull { it.sizeBytes }
    }

    /**
     * Whether [alias] appears in [name] as a whole token: "x86" must not match "x86_64", and
     * "arm64" must not match inside an unrelated word.
     */
    private fun nameHasToken(name: String, alias: String): Boolean {
        val lower = name.lowercase()
        var from = 0
        while (true) {
            val index = lower.indexOf(alias, from)
            if (index < 0) return false
            val before = lower.getOrNull(index - 1)
            val after = lower.getOrNull(index + alias.length)
            if (!before.isTokenChar() && !after.isTokenChar()) return true
            from = index + 1
        }
    }

    private fun Char?.isTokenChar(): Boolean = this != null && (isLetterOrDigit() || this == '_')
}
