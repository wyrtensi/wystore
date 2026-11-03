package dev.wystore.ui.components

/** Keep short leading destinations together instead of sizing every row for the longest name. */
object NavigationLayoutPolicy {
    fun rows(labelWidths: List<Int>, availableWidth: Int): List<Int> {
        require(availableWidth >= 0 && labelWidths.all { it >= 0 })
        val result = mutableListOf<Int>()
        var cursor = 0
        while (cursor < labelWidths.size) {
            var count = labelWidths.size - cursor
            while (count > 1 && labelWidths.subList(cursor, cursor + count).sumOf { it.toLong() } > availableWidth) count--
            result += count
            cursor += count
        }
        return result
    }
}
