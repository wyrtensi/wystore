package dev.wystore.data

enum class OwnershipHandover {
    OWNED, REINSTALL, UNSUPPORTED_ANDROID, UNSUPPORTED_INSTALLER
}

/** A handover must establish update ownership, not merely change the last installer. */
object OwnershipHandoverPolicy {
    fun decide(
        sdkInt: Int,
        updateOwnerPackageName: String?,
        ownPackageName: String,
        sessionsSupported: Boolean
    ): OwnershipHandover = when {
        sdkInt < 34 -> OwnershipHandover.UNSUPPORTED_ANDROID
        updateOwnerPackageName == ownPackageName -> OwnershipHandover.OWNED
        !sessionsSupported -> OwnershipHandover.UNSUPPORTED_INSTALLER
        else -> OwnershipHandover.REINSTALL
    }
}
