package dev.wystore.data

object AppSourceLinks {
    const val LIBRARY = "https://github.com/wyrtensi/wystore-library"
    fun page(source: ManagedSource, packageName: String): String = when (source) {
        ManagedSource.WY_STORE -> "$LIBRARY/releases/tag/$packageName-library"
        ManagedSource.RUSTORE -> "https://www.rustore.ru/catalog/app/$packageName"
        ManagedSource.GITHUB -> throw IllegalArgumentException("A GitHub page requires its repository identity")
    }
}
