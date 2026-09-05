package dev.wystore.ui.navigation

import androidx.compose.runtime.saveable.Saver

sealed class WyStoreDestination(val route: String) {
    object Home : WyStoreDestination("home")
    object Search : WyStoreDestination("search")
    object Updates : WyStoreDestination("updates")
    object Library : WyStoreDestination("library")
    object Settings : WyStoreDestination("settings")
    object GitHub : WyStoreDestination("github")

    data class Category(val slug: String) : WyStoreDestination("catalog/$slug") {
        companion object {
            const val ROUTE_PATTERN = "catalog/{slug}"
        }
    }

    data class AppDetails(val packageName: String) : WyStoreDestination("app/$packageName") {
        companion object {
            const val ROUTE_PATTERN = "app/{packageName}"
        }
    }

    companion object {
        /**
         * Rebuilds a destination from its route so the current screen survives process death and
         * configuration changes. Anything unrecognised falls back to Home rather than crashing on
         * a route written by an older build.
         */
        fun fromRoute(route: String?): WyStoreDestination = when {
            route == null -> Home
            route == Home.route -> Home
            route == Search.route -> Search
            route == Updates.route -> Updates
            route == Library.route -> Library
            route == Settings.route -> Settings
            route == GitHub.route -> GitHub
            route.startsWith("catalog/") -> Category(route.removePrefix("catalog/"))
            route.startsWith("app/") -> AppDetails(route.removePrefix("app/"))
            else -> Home
        }

        val Saver: Saver<WyStoreDestination, String> = Saver(
            save = { it.route },
            restore = ::fromRoute
        )
    }
}
