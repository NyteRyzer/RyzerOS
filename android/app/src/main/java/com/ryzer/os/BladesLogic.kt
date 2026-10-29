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
    fun next(groups: List<List<Int>>, from: Int, dir: Int): Int {
        val n = groups.size
        for (step in 1 until n) {
            val t = ((from + dir * step) % n + n) % n
            if (groups[t].isNotEmpty()) return t
        }
        return from
    }

    fun firstNonEmpty(groups: List<List<Int>>): Int = groups.indexOfFirst { it.isNotEmpty() }.takeIf { it >= 0 } ?: 3
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
