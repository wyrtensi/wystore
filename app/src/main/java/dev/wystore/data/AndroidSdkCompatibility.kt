package dev.wystore.data

object AndroidSdkCompatibility {
    fun requireSupported(minSdkVersion: Int?, deviceSdkVersion: Int) {
        if (minSdkVersion != null && minSdkVersion > deviceSdkVersion) {
            throw SourceFormatException(
                "Этой версии приложения требуется ${label(minSdkVersion)}, " +
                    "а на устройстве ${label(deviceSdkVersion)}"
            )
        }
    }

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
