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
