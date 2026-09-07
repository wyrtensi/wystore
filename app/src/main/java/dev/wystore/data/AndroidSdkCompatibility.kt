package dev.wystore.data

object AndroidSdkCompatibility {
    fun requireSupported(minSdkVersion: Int?, deviceSdkVersion: Int) {
        if (minSdkVersion != null && minSdkVersion > deviceSdkVersion) {
            // The two version labels are the useful part and are language-neutral; the sentence
            // around them is built from resources where this surfaces.
            throw SourceFormatException(
                SourceError.INCOMPATIBLE_ANDROID,
                "Requires ${label(minSdkVersion)}, device is ${label(deviceSdkVersion)}"
            )
        }
    }

    /**
     * The API level behind a release number such as "9" or "8.1".
     *
     * The catalogue publishes the release the user sees, not the API level. Read as an API level -
     * which is what the app used to do when the source left minSdkVersion out - "Android 9" became
     * SDK 9, so every device on earth compared as new enough and the download failed instead.
     */
    fun sdkForRelease(release: String): Int? {
        val trimmed = release.trim().removePrefix("Android").trim().substringBefore(' ')
        val normalized = if (trimmed.endsWith(".0")) trimmed.dropLast(2) else trimmed
        return RELEASE_TO_SDK[normalized]
    }

    private val RELEASE_TO_SDK = mapOf(
        "5" to 21,
        "5.1" to 22,
        "6" to 23,
        "7" to 24,
        "7.1" to 25,
        "8" to 26,
        "8.1" to 27,
        "9" to 28,
        "10" to 29,
        "11" to 30,
        "12" to 31,
        "12.1" to 32,
        "13" to 33,
        "14" to 34,
        "15" to 35,
        "16" to 36
    )

    fun label(sdkVersion: Int): String {
        val release = when (sdkVersion) {
            21 -> "5.0"
            22 -> "5.1"
            23 -> "6.0"
            24 -> "7.0"
            25 -> "7.1"
            26 -> "8.0"
            27 -> "8.1"
            28 -> "9"
            29 -> "10"
            30 -> "11"
            31, 32 -> "12"
            33 -> "13"
            34 -> "14"
            35 -> "15"
            36 -> "16"
            else -> null
        }
        return if (release == null) "Android SDK $sdkVersion" else "Android $release (SDK $sdkVersion)"
    }
}
