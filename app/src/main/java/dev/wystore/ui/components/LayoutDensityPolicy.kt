package dev.wystore.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import dev.wystore.settings.LayoutDensity

object LayoutDensityPolicy {
    fun isCompact(choice: LayoutDensity, widthDp: Float, fontScale: Float, tv: Boolean): Boolean =
        !tv && when (choice) {
            LayoutDensity.AUTO -> widthDp / fontScale.coerceAtLeast(1f) < 420f
            LayoutDensity.STANDARD -> false
            LayoutDensity.COMPACT -> true
        }
}

val LocalCompactLayout = staticCompositionLocalOf { false }
