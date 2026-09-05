package dev.wystore.data

object PackageNameValidator {
    private val pattern = Regex("^[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+$")

    fun requireValid(packageName: String): String {
        if (packageName.length !in 3..255 || !pattern.matches(packageName)) {
            throw SourceFormatException(SourceError.INVALID_PACKAGE_NAME, "Rejected package name: $packageName")
        }
        return packageName
    }
}
