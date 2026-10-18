package dev.wystore.settings

/**
 * The accent the Wy Hub look is drawn in, chosen in Appearance.
 *
 * Each one is a single tone used on both themes, picked so that white text on it - every accent
 * button - passes 4.5:1, and so that as a colour of its own it still stands 3:1 off the dark page
 * and the light one. A brighter tone read better on the dark page and failed under white text,
 * which is why the concept's own #F66C3F gave way to [ORANGE]. See AccentPresetTest.
 */
enum class AccentPreset(val argb: Long) {
    ORANGE(0xFFCF4A1A),
    RED(0xFFE0283C),
    PINK(0xFFD43584),
    VIOLET(0xFFA348E4),
    INDIGO(0xFF6363F2),
    BLUE(0xFF1F76DA),
    TEAL(0xFF0E7C8C),
    GREEN(0xFF278646),
    AMBER(0xFF976F20)
}
