package dev.wystore.data

import android.content.pm.PackageManager

object SigningFlags {
    /** For a package the system has installed, where the modern field is always filled in. */
    fun forSdk(sdk: Int): Int = PackageManager.GET_SIGNING_CERTIFICATES

    /**
     * For an APK file on disk, where it is not.
     *
     * Both flags, because the modern one alone is not enough: on Android 10 the same archive that
     * reports its signers perfectly well on Android 11 comes back with an empty `signingInfo`, and
     * the store rejected every download it had just finished as having an unreadable signature.
     * The old field is still filled when it is asked for, so it is asked for, and whichever of the
     * two answers arrives is used.
     */
    @Suppress("DEPRECATION")
    fun forArchive(): Int =
        PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
}
