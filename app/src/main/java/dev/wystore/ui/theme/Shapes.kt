package dev.wystore.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Corner radii grow with the size of the surface they belong to: pills and badges take
 * [Shapes.extraSmall], cards [Shapes.large], and full-bleed containers [Shapes.extraLarge].
 * Components read these instead of writing their own `RoundedCornerShape`, so one change here
 * moves the whole app.
 */
val WyStoreShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)
