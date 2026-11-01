package dev.wystore.data

object AppSourceIdentity {
    fun merge(apps: List<StoreApp>): List<StoreApp> = apps.groupBy { it.packageName }.values.map { matches ->
        matches.first().copy(availableSources = matches.flatMap { it.availableSources + it.source }.toSet())
    }
}
