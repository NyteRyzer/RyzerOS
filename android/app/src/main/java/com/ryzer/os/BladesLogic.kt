package com.ryzer.os

// Android-free maths behind the Blades look, kept apart so it runs in plain JVM unit tests.

/** Shapes and meters for the Blades style. */
object SkinMath {
    /** Corners of a parallelogram, clockwise from top-left, with the top edge shifted right by [skew]. */
    fun para(x: Float, y: Float, w: Float, h: Float, skew: Float): FloatArray =
        floatArrayOf(x + skew, y, x + w + skew, y, x + w, y + h, x, y + h)

    /** Skew for a panel or row of height [h]: taller shapes lean more, within 8..18 px. */
    fun skewFor(h: Float): Float = (h * 0.3f).coerceIn(8f, 18f)

    /** How many of [count] meter segments are lit for [value] out of [max]. */
    fun segmentsOn(value: Double, max: Double, count: Int = 16): Int = when {
        max <= 0 -> 0
        value >= max -> count
        else -> (value / max * count).toInt().coerceIn(0, count)
    }

    /** True for colours light enough that white text on them is hard to read (relative luminance over 0.5). */
    fun isLight(color: Int): Boolean {
        fun ch(shift: Int): Double { val c = ((color shr shift) and 255) / 255.0; return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4) }
        return 0.2126 * ch(16) + 0.7152 * ch(8) + 0.0722 * ch(0) > 0.5
    }

    /** Rounds a size up to a multiple of [step], so animated sizes share one cached glow. */
    fun quantize(v: Float, step: Float = 8f): Float = (kotlin.math.ceil(v / step) * step).coerceAtLeast(step)

    /** Splits [text] into at most two lines of [maxW]; anything that doesn't fit stays on line two (to be cut with "..."). */
    fun twoLines(text: String, maxW: Float, measure: (String) -> Float): List<String> {
        val words = text.split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        var first = words[0]
        var i = 1
        while (i < words.size && measure("$first ${words[i]}") <= maxW) { first += " ${words[i]}"; i++ }
        return if (i < words.size) listOf(first, words.drop(i).joinToString(" ")) else listOf(first)
    }
}

/** How Blades Home sorts the Home tiles into its four tabs. */
object BladeTabs {
    val NAMES = listOf("CONTINUE", "SYSTEMS", "COLLECTIONS", "TOOLS")

    fun tabOf(kind: String): Int = when (kind) {
        "continue", "pinned", "gotd" -> 0
        "system" -> 1
        "favorites", "foryou", "smart", "collection", "recent", "played", "added" -> 2
        else -> 3
    }

    /** Tile indices per tab, in their original order. */
    fun group(kinds: List<String>): List<List<Int>> {
        val out = List(NAMES.size) { ArrayList<Int>() }
        kinds.forEachIndexed { i, k -> out[tabOf(k)].add(i) }
        return out
    }

    /** The next tab with something in it, wrapping round; stays put when every tab is empty. */
    fun next(groups: List<List<Int>>, from: Int, dir: Int, allowEmpty: Boolean = false): Int {
        val n = groups.size
        for (step in 1 until n) {
            val t = ((from + dir * step) % n + n) % n
            if (allowEmpty || groups[t].isNotEmpty()) return t
        }
        return from
    }

    fun firstNonEmpty(groups: List<List<Int>>): Int = groups.indexOfFirst { it.isNotEmpty() }.takeIf { it >= 0 } ?: 3

    /** Where Home opens: with no games yet, on Tools (Collections would only hold empty Favorites / Recent). */
    fun startTab(groups: List<List<Int>>, hasGames: Boolean): Int = if (hasGames) firstNonEmpty(groups) else 3

    fun clampSel(sel: Int, size: Int): Int = sel.coerceIn(0, maxOf(0, size - 1))
}

/** Timeline of the "Strike" boot animation, in seconds. Each phase runs 0..1 over its window. */
object StrikeBoot {
    const val duration = 2.0f

    fun progress(t: Float, start: Float, end: Float): Float = ((t - start) / (end - start)).coerceIn(0f, 1f)
    fun bladesIn(t: Float) = progress(t, 0f, 0.5f)
    fun mark(t: Float) = progress(t, 0.4f, 0.9f)
    fun title(t: Float) = progress(t, 0.8f, 1.4f)
    fun bladesOut(t: Float) = progress(t, 1.6f, 2.0f)
    /** The mark lands from 140% down to its real size. */
    fun markScale(t: Float) = 1.4f - 0.4f * mark(t)
}
