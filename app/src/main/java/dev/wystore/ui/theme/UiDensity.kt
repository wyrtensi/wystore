package dev.wystore.ui.theme

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import dev.wystore.settings.UiScale

/** Preserve Android's nonlinear font conversion instead of rebuilding a linear font scale. */
class UiDensity(private val system: Density, scale: UiScale) : Density {
    override val density = system.density * scale.factor
    override val fontScale = system.fontScale
    override fun TextUnit.toDp(): Dp = with(system) { this@toDp.toDp() }
    override fun Dp.toSp(): TextUnit = with(system) { this@toSp.toSp() }
}
