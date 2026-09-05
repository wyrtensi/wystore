package dev.wystore.data

import android.content.pm.PackageManager

object SigningFlags {
    @Suppress("DEPRECATION")
    fun forSdk(sdk: Int): Int =
        if (sdk >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
}
