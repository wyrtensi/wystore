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
    // Wy Hub's radii: controls 18, cards 22, panels 28.
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)
