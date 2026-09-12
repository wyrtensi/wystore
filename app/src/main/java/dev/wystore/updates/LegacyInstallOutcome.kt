package dev.wystore.updates

/**
 * What the system installer's answer to the install intent means for the queue row.
 *
 * The intent route used to fire and forget: success turned up later as a package broadcast, and a
 * dialog the user closed left the row INSTALLING - holding the single install slot - until the
 * next reboot reconciled it. That route was only taken on Android 8; on a firmware that refuses
 * sessions it is the route every install takes, so the answer is read.
 */
sealed interface LegacyInstallOutcome {
    data object Installed : LegacyInstallOutcome
    data object Declined : LegacyInstallOutcome
    data class Failed(val installResult: Int?) : LegacyInstallOutcome

    companion object {
        // Activity.RESULT_* values, repeated so this stays a plain JVM decision.
        const val RESULT_OK = -1
        const val RESULT_CANCELED = 0
        const val RESULT_FIRST_USER = 1

        /**
         * PackageManager's hidden INSTALL_* codes by name, for the ones an ordinary install meets.
         * The system installer reports only the number, and "-113" answers nobody's question.
         */
        private val INSTALL_RESULT_NAMES = mapOf(
            -1 to "INSTALL_FAILED_ALREADY_EXISTS",
            -2 to "INSTALL_FAILED_INVALID_APK",
            -4 to "INSTALL_FAILED_INSUFFICIENT_STORAGE",
            -7 to "INSTALL_FAILED_UPDATE_INCOMPATIBLE",
            -8 to "INSTALL_FAILED_SHARED_USER_INCOMPATIBLE",
            -12 to "INSTALL_FAILED_OLDER_SDK",
            -16 to "INSTALL_FAILED_CPU_ABI_INCOMPATIBLE",
            -25 to "INSTALL_FAILED_VERSION_DOWNGRADE",
            -26 to "INSTALL_FAILED_PERMISSION_MODEL_DOWNGRADE",
            -103 to "INSTALL_PARSE_FAILED_NO_CERTIFICATES",
            -104 to "INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES",
            -110 to "INSTALL_FAILED_INTERNAL_ERROR",
            -111 to "INSTALL_FAILED_USER_RESTRICTED",
            -113 to "INSTALL_FAILED_NO_MATCHING_ABIS",
            -115 to "INSTALL_FAILED_ABORTED"
        )

        fun describe(installResult: Int?): String = when (installResult) {
            null -> "system installer reported failure without a code"
            else -> INSTALL_RESULT_NAMES[installResult]?.let { "$it ($installResult)" }
                ?: "system installer code $installResult"
        }

        fun decide(
            resultCode: Int,
            installResult: Int?,
            targetVersionCode: Long,
            installedVersionCode: Long?,
            installedUpdatedAt: Long?,
            handedOverAt: Long,
        ): LegacyInstallOutcome {
            // The device is asked first. A vendor installer is free to ignore EXTRA_RETURN_RESULT
            // and simply close, and the package it installed is the better witness. The time
            // matters for a reinstall of the same version, which was already "installed" before.
            val landed = installedVersionCode != null && installedVersionCode >= targetVersionCode &&
                installedUpdatedAt != null && installedUpdatedAt >= handedOverAt
            return when {
                landed || resultCode == RESULT_OK -> Installed
                resultCode == RESULT_FIRST_USER -> Failed(installResult)
                else -> Declined
            }
        }
    }
}
