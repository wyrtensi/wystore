package dev.wystore.updates

import android.content.pm.PackageInstaller
import dev.wystore.updates.model.QueueErrorCode

/**
 * What Android's refusal of an install was about, for the row to say.
 *
 * Every refusal used to be recorded as INSTALL_FAILED, shown as "Android could not install the
 * app". The common one is not a fault at all: the same package is already on the phone signed
 * with another key, often in another profile - a work profile, Shelter, a second space - where
 * this profile's list does not show it. Nothing on the row said so, so it read as Wy Store's error.
 *
 * Android names the reason twice: a coarse session status, and the INSTALL_FAILED_* name at the
 * head of its message, which is the precise one. The name wins when there is one.
 */
object InstallRefusal {

    private val BY_NAME = listOf(
        // Another key, or another app's shared user: the one Android cannot install over.
        "INSTALL_FAILED_UPDATE_INCOMPATIBLE" to QueueErrorCode.INSTALL_CONFLICT,
        "INSTALL_FAILED_SHARED_USER_INCOMPATIBLE" to QueueErrorCode.INSTALL_CONFLICT,
        "INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES" to QueueErrorCode.INSTALL_CONFLICT,
        "INSTALL_FAILED_DUPLICATE_PERMISSION" to QueueErrorCode.INSTALL_CONFLICT,
        "INSTALL_FAILED_INSUFFICIENT_STORAGE" to QueueErrorCode.STORAGE_FULL,
        "INSTALL_FAILED_VERSION_DOWNGRADE" to QueueErrorCode.DOWNGRADE,
        "INSTALL_FAILED_OLDER_SDK" to QueueErrorCode.INCOMPATIBLE,
        "INSTALL_FAILED_NO_MATCHING_ABIS" to QueueErrorCode.INCOMPATIBLE,
        "INSTALL_FAILED_CPU_ABI_INCOMPATIBLE" to QueueErrorCode.INCOMPATIBLE
    )

    /**
     * @param status the session's [PackageInstaller] status, or null on a route that has none.
     * @param message what Android wrote, which may carry the INSTALL_FAILED_* name.
     */
    fun codeFor(status: Int?, message: String?): QueueErrorCode {
        message?.let { text -> BY_NAME.firstOrNull { (name, _) -> text.contains(name) }?.let { return it.second } }
        return when (status) {
            PackageInstaller.STATUS_FAILURE_CONFLICT -> QueueErrorCode.INSTALL_CONFLICT
            PackageInstaller.STATUS_FAILURE_STORAGE -> QueueErrorCode.STORAGE_FULL
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> QueueErrorCode.INCOMPATIBLE
            else -> QueueErrorCode.INSTALL_FAILED
        }
    }
}
