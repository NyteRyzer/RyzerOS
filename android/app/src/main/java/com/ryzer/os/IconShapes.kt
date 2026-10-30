package com.ryzer.os

// Console-shaped system icons as plain shape data in a 0..1 box (no Android, so they can be unit tested).
// Outlines of the hardware only: no logos or wordmarks. SystemIcons draws and caches them.

/** What colour a shape takes: the system's colour, a darker shade of it, a screen, white, or a fixed colour. */
enum class Ink { BODY, SHADE, SCREEN, LIGHT, FIXED }

sealed class Shape(val ink: Ink, val color: Int) {
    /** left, top, right, bottom */
    abstract fun bounds(): FloatArray

    class Box(val x: Float, val y: Float, val w: Float, val h: Float, val r: Float, ink: Ink, color: Int = 0) : Shape(ink, color) {
        override fun bounds() = floatArrayOf(x, y, x + w, y + h)
        override fun toString() = "Box($x,$y,$w,$h)"
    }

    class Dot(val cx: Float, val cy: Float, val r: Float, ink: Ink, color: Int = 0) : Shape(ink, color) {
        override fun bounds() = floatArrayOf(cx - r, cy - r, cx + r, cy + r)
        override fun toString() = "Dot($cx,$cy,$r)"
    }
}


object IconShapes {
    private const val RED = 0xFFE5484D.toInt()
    private const val BLUE = 0xFF3E7BFA.toInt()
    private const val GREEN = 0xFF3DDC84.toInt()
    private const val YELLOW = 0xFFFFC23D.toInt()
    private const val GREY = 0xFFB8B8C2.toInt()

    private fun box(x: Float, y: Float, w: Float, h: Float, r: Float, ink: Ink, color: Int = 0) = Shape.Box(x, y, w, h, r, ink, color)
    private fun dot(cx: Float, cy: Float, r: Float, ink: Ink, color: Int = 0) = Shape.Dot(cx, cy, r, ink, color)

    /** A plus-shaped direction pad centred on (cx, cy). */
    private fun dpad(cx: Float, cy: Float, s: Float, ink: Ink = Ink.SHADE) = listOf(
        box(cx - s, cy - s / 3, s * 2, s * 2 / 3, s * 0.15f, ink), box(cx - s / 3, cy - s, s * 2 / 3, s * 2, s * 0.15f, ink))

    /** Four face buttons in a diamond. */
    private fun diamond(cx: Float, cy: Float, d: Float, r: Float, colors: List<Int>? = null) = listOf(
        dot(cx, cy - d, r, if (colors != null) Ink.FIXED else Ink.LIGHT, colors?.get(0) ?: 0),
        dot(cx + d, cy, r, if (colors != null) Ink.FIXED else Ink.LIGHT, colors?.get(1) ?: 0),
        dot(cx, cy + d, r, if (colors != null) Ink.FIXED else Ink.LIGHT, colors?.get(2) ?: 0),
        dot(cx - d, cy, r, if (colors != null) Ink.FIXED else Ink.LIGHT, colors?.get(3) ?: 0))

    /** The generic gamepad for systems Ryzer has no picture of. */
    val GENERIC: List<Shape> = listOf(box(0.06f, 0.30f, 0.88f, 0.42f, 0.2f, Ink.BODY)) +
        dpad(0.27f, 0.51f, 0.09f) + diamond(0.73f, 0.51f, 0.075f, 0.04f) +
        listOf(box(0.42f, 0.47f, 0.07f, 0.03f, 0.015f, Ink.SHADE), box(0.51f, 0.47f, 0.07f, 0.03f, 0.015f, Ink.SHADE))

    /** Two-screen flip handheld: [wideTop] gives the 3DS its wider top screen. */
    private fun clamshell(wideTop: Boolean, stick: Boolean) = listOf(
        box(0.16f, 0.06f, 0.68f, 0.42f, 0.05f, Ink.BODY),
        if (wideTop) box(0.21f, 0.11f, 0.58f, 0.32f, 0.02f, Ink.SCREEN) else box(0.28f, 0.11f, 0.44f, 0.32f, 0.02f, Ink.SCREEN),
        box(0.16f, 0.47f, 0.68f, 0.04f, 0.01f, Ink.SHADE),
        box(0.16f, 0.50f, 0.68f, 0.44f, 0.05f, Ink.BODY),
        box(0.33f, 0.55f, 0.34f, 0.26f, 0.02f, Ink.SCREEN)) +
        (if (stick) listOf(dot(0.245f, 0.62f, 0.045f, Ink.SHADE)) + dpad(0.245f, 0.80f, 0.04f) else dpad(0.245f, 0.68f, 0.05f)) +
        diamond(0.755f, if (stick) 0.70f else 0.68f, 0.04f, 0.022f)

    private val shapes: Map<String, List<Shape>> = mapOf(
        "3ds" to clamshell(wideTop = true, stick = true),
        "nds" to clamshell(wideTop = false, stick = false),
        "switch" to listOf(
            box(0.06f, 0.26f, 0.18f, 0.48f, 0.08f, Ink.FIXED, BLUE),
            box(0.76f, 0.26f, 0.18f, 0.48f, 0.08f, Ink.FIXED, RED),
            box(0.22f, 0.26f, 0.56f, 0.48f, 0.02f, Ink.FIXED, 0xFF2A2A33.toInt()),
            box(0.26f, 0.30f, 0.48f, 0.40f, 0.02f, Ink.SCREEN),
            dot(0.15f, 0.38f, 0.04f, Ink.FIXED, 0xFF2A2A33.toInt()), dot(0.85f, 0.56f, 0.04f, Ink.FIXED, 0xFF2A2A33.toInt())) +
            diamond(0.15f, 0.58f, 0.035f, 0.016f) + diamond(0.85f, 0.38f, 0.035f, 0.016f),
        "wiiu" to listOf(
            box(0.04f, 0.28f, 0.92f, 0.44f, 0.12f, Ink.BODY),
            box(0.28f, 0.33f, 0.44f, 0.34f, 0.02f, Ink.SCREEN),
            dot(0.15f, 0.40f, 0.045f, Ink.SHADE), dot(0.85f, 0.40f, 0.045f, Ink.SHADE)) +
            dpad(0.15f, 0.58f, 0.045f) + diamond(0.85f, 0.58f, 0.04f, 0.02f),
        "wii" to listOf(
            box(0.24f, 0.10f, 0.22f, 0.80f, 0.04f, Ink.BODY),
            box(0.33f, 0.18f, 0.04f, 0.40f, 0.02f, Ink.SHADE),
            dot(0.35f, 0.80f, 0.025f, Ink.FIXED, BLUE),
            box(0.58f, 0.18f, 0.16f, 0.66f, 0.07f, Ink.LIGHT),
            box(0.62f, 0.26f, 0.08f, 0.03f, 0.01f, Ink.SHADE), box(0.645f, 0.235f, 0.03f, 0.08f, 0.01f, Ink.SHADE),
            dot(0.66f, 0.40f, 0.03f, Ink.SHADE), dot(0.66f, 0.66f, 0.015f, Ink.SHADE), dot(0.66f, 0.72f, 0.015f, Ink.SHADE)),
        "gc" to listOf(
            box(0.14f, 0.20f, 0.72f, 0.72f, 0.06f, Ink.BODY),
            box(0.30f, 0.08f, 0.40f, 0.16f, 0.06f, Ink.SHADE),
            box(0.36f, 0.12f, 0.28f, 0.10f, 0.04f, Ink.BODY),
            dot(0.50f, 0.50f, 0.20f, Ink.SHADE), dot(0.50f, 0.50f, 0.06f, Ink.LIGHT),
            box(0.20f, 0.82f, 0.16f, 0.05f, 0.02f, Ink.SHADE), box(0.64f, 0.82f, 0.16f, 0.05f, 0.02f, Ink.SHADE)),
        "ps2" to listOf(
            box(0.28f, 0.06f, 0.44f, 0.88f, 0.02f, Ink.SHADE),
            box(0.30f, 0.08f, 0.40f, 0.84f, 0.02f, Ink.BODY),
            box(0.36f, 0.12f, 0.05f, 0.76f, 0.02f, Ink.SHADE),
            box(0.46f, 0.14f, 0.20f, 0.03f, 0.01f, Ink.SHADE),
            dot(0.56f, 0.82f, 0.025f, Ink.FIXED, BLUE),
            box(0.46f, 0.72f, 0.20f, 0.02f, 0.01f, Ink.SHADE), box(0.46f, 0.76f, 0.20f, 0.02f, 0.01f, Ink.SHADE)),
        "psx" to listOf(
            box(0.08f, 0.32f, 0.84f, 0.44f, 0.05f, Ink.BODY),
            dot(0.36f, 0.54f, 0.17f, Ink.SHADE), dot(0.36f, 0.54f, 0.04f, Ink.LIGHT),
            dot(0.66f, 0.46f, 0.035f, Ink.SHADE), dot(0.78f, 0.46f, 0.035f, Ink.SHADE),
            box(0.62f, 0.62f, 0.22f, 0.05f, 0.02f, Ink.SHADE)),
        "psp" to listOf(
            box(0.03f, 0.30f, 0.94f, 0.40f, 0.2f, Ink.BODY),
            box(0.26f, 0.34f, 0.48f, 0.32f, 0.02f, Ink.SCREEN),
            dot(0.13f, 0.62f, 0.025f, Ink.SHADE)) + dpad(0.14f, 0.46f, 0.05f) + diamond(0.86f, 0.50f, 0.045f, 0.022f),
        "psvita" to listOf(
            box(0.02f, 0.30f, 0.96f, 0.40f, 0.2f, Ink.BODY),
            box(0.24f, 0.33f, 0.52f, 0.34f, 0.03f, Ink.SCREEN),
            dot(0.15f, 0.61f, 0.035f, Ink.SHADE), dot(0.85f, 0.61f, 0.035f, Ink.SHADE)) +
            dpad(0.13f, 0.43f, 0.04f) + diamond(0.87f, 0.43f, 0.04f, 0.018f),
        "gba" to listOf(
            box(0.04f, 0.30f, 0.92f, 0.40f, 0.18f, Ink.BODY),
            box(0.30f, 0.35f, 0.40f, 0.30f, 0.02f, Ink.SHADE),
            box(0.33f, 0.38f, 0.34f, 0.24f, 0.01f, Ink.SCREEN),
            dot(0.80f, 0.46f, 0.035f, Ink.LIGHT), dot(0.88f, 0.42f, 0.035f, Ink.LIGHT)) + dpad(0.16f, 0.50f, 0.06f),
        "gb" to listOf(
            box(0.24f, 0.04f, 0.52f, 0.92f, 0.06f, Ink.BODY),
            box(0.30f, 0.10f, 0.40f, 0.36f, 0.03f, Ink.SHADE),
            box(0.35f, 0.14f, 0.30f, 0.27f, 0.01f, Ink.SCREEN),
            dot(0.62f, 0.64f, 0.04f, Ink.LIGHT), dot(0.69f, 0.58f, 0.04f, Ink.LIGHT),
            box(0.42f, 0.78f, 0.07f, 0.025f, 0.012f, Ink.SHADE), box(0.52f, 0.78f, 0.07f, 0.025f, 0.012f, Ink.SHADE)) +
            dpad(0.36f, 0.63f, 0.06f),
        "gbc" to listOf(
            box(0.24f, 0.04f, 0.52f, 0.92f, 0.08f, Ink.BODY),
            box(0.30f, 0.10f, 0.40f, 0.36f, 0.03f, Ink.SHADE),
            box(0.35f, 0.14f, 0.30f, 0.27f, 0.01f, Ink.SCREEN),
            dot(0.62f, 0.64f, 0.04f, Ink.LIGHT), dot(0.69f, 0.58f, 0.04f, Ink.LIGHT),
            box(0.44f, 0.80f, 0.12f, 0.025f, 0.012f, Ink.SHADE)) + dpad(0.36f, 0.63f, 0.06f),
        "gamegear" to listOf(
            box(0.02f, 0.28f, 0.96f, 0.44f, 0.12f, Ink.BODY),
            box(0.28f, 0.32f, 0.44f, 0.36f, 0.03f, Ink.SHADE),
            box(0.32f, 0.36f, 0.36f, 0.28f, 0.01f, Ink.SCREEN),
            dot(0.82f, 0.54f, 0.04f, Ink.LIGHT), dot(0.91f, 0.48f, 0.04f, Ink.LIGHT)) + dpad(0.14f, 0.50f, 0.065f),
        "ngpc" to listOf(
            box(0.08f, 0.26f, 0.84f, 0.48f, 0.2f, Ink.BODY),
            box(0.32f, 0.31f, 0.36f, 0.30f, 0.02f, Ink.SCREEN),
            dot(0.19f, 0.48f, 0.07f, Ink.SHADE), dot(0.19f, 0.48f, 0.03f, Ink.LIGHT),
            dot(0.78f, 0.52f, 0.035f, Ink.LIGHT), dot(0.86f, 0.44f, 0.035f, Ink.LIGHT)),
        "snes" to listOf(
            box(0.04f, 0.30f, 0.92f, 0.40f, 0.2f, Ink.BODY),
            box(0.42f, 0.48f, 0.06f, 0.03f, 0.015f, Ink.SHADE), box(0.52f, 0.48f, 0.06f, 0.03f, 0.015f, Ink.SHADE)) +
            dpad(0.22f, 0.50f, 0.075f) + diamond(0.78f, 0.50f, 0.075f, 0.04f, listOf(BLUE, RED, YELLOW, GREEN)),
        "nes" to listOf(
            box(0.04f, 0.32f, 0.92f, 0.36f, 0.03f, Ink.BODY),
            box(0.38f, 0.38f, 0.24f, 0.24f, 0.02f, Ink.SHADE),
            box(0.41f, 0.53f, 0.07f, 0.03f, 0.015f, Ink.LIGHT), box(0.52f, 0.53f, 0.07f, 0.03f, 0.015f, Ink.LIGHT),
            dot(0.72f, 0.52f, 0.045f, Ink.FIXED, RED), dot(0.85f, 0.52f, 0.045f, Ink.FIXED, RED)) + dpad(0.19f, 0.50f, 0.07f),
        "genesis" to listOf(
            box(0.04f, 0.26f, 0.92f, 0.48f, 0.24f, Ink.BODY),
            dot(0.25f, 0.50f, 0.11f, Ink.SHADE),
            dot(0.61f, 0.56f, 0.04f, Ink.LIGHT), dot(0.72f, 0.51f, 0.04f, Ink.LIGHT), dot(0.83f, 0.46f, 0.04f, Ink.LIGHT),
            box(0.46f, 0.36f, 0.08f, 0.03f, 0.015f, Ink.SHADE)) + dpad(0.25f, 0.50f, 0.075f, Ink.LIGHT),
        "mastersystem" to listOf(
            box(0.06f, 0.32f, 0.88f, 0.36f, 0.04f, Ink.BODY),
            dot(0.70f, 0.52f, 0.05f, Ink.FIXED, RED), dot(0.84f, 0.52f, 0.05f, Ink.FIXED, RED)) + dpad(0.22f, 0.50f, 0.08f),
        "segacd" to listOf(
            box(0.06f, 0.24f, 0.88f, 0.56f, 0.04f, Ink.BODY),
            dot(0.40f, 0.52f, 0.22f, Ink.SHADE), dot(0.40f, 0.52f, 0.05f, Ink.LIGHT),
            box(0.70f, 0.32f, 0.16f, 0.05f, 0.02f, Ink.SHADE), box(0.70f, 0.66f, 0.16f, 0.05f, 0.02f, Ink.SHADE)),
        "saturn" to listOf(
            box(0.04f, 0.28f, 0.92f, 0.44f, 0.22f, Ink.BODY)) + dpad(0.22f, 0.50f, 0.075f) +
            listOf(0.62f, 0.73f, 0.84f).flatMap { x -> listOf(dot(x, 0.57f, 0.035f, Ink.LIGHT), dot(x, 0.43f, 0.028f, Ink.SHADE)) },
        "dreamcast" to listOf(
            box(0.10f, 0.14f, 0.80f, 0.72f, 0.08f, Ink.BODY),
            dot(0.50f, 0.42f, 0.22f, Ink.SHADE), dot(0.50f, 0.42f, 0.05f, Ink.LIGHT),
            box(0.18f, 0.74f, 0.64f, 0.04f, 0.02f, Ink.SHADE), dot(0.80f, 0.24f, 0.025f, Ink.FIXED, 0xFFFF8A3D.toInt())),
        "n64" to listOf(
            box(0.06f, 0.20f, 0.88f, 0.26f, 0.12f, Ink.BODY),
            box(0.08f, 0.32f, 0.18f, 0.46f, 0.09f, Ink.BODY),
            box(0.41f, 0.32f, 0.18f, 0.58f, 0.09f, Ink.BODY),
            box(0.74f, 0.32f, 0.18f, 0.46f, 0.09f, Ink.BODY),
            dot(0.50f, 0.46f, 0.055f, Ink.SHADE), dot(0.50f, 0.46f, 0.025f, Ink.LIGHT),
            dot(0.71f, 0.40f, 0.03f, Ink.FIXED, GREEN), dot(0.78f, 0.44f, 0.032f, Ink.FIXED, BLUE)) +
            diamond(0.85f, 0.31f, 0.04f, 0.02f, listOf(YELLOW, YELLOW, YELLOW, YELLOW)) + dpad(0.17f, 0.34f, 0.055f),
        "pce" to listOf(
            box(0.16f, 0.22f, 0.68f, 0.60f, 0.04f, Ink.BODY),
            box(0.16f, 0.22f, 0.68f, 0.14f, 0.04f, Ink.SHADE),
            box(0.30f, 0.48f, 0.40f, 0.06f, 0.02f, Ink.SCREEN),
            box(0.24f, 0.68f, 0.20f, 0.05f, 0.02f, Ink.SHADE), dot(0.70f, 0.70f, 0.03f, Ink.FIXED, RED)),
        "arcade" to listOf(
            box(0.24f, 0.04f, 0.52f, 0.92f, 0.03f, Ink.BODY),
            box(0.26f, 0.06f, 0.48f, 0.12f, 0.02f, Ink.LIGHT),
            box(0.30f, 0.22f, 0.40f, 0.30f, 0.02f, Ink.SCREEN),
            box(0.20f, 0.56f, 0.60f, 0.12f, 0.02f, Ink.SHADE),
            dot(0.34f, 0.58f, 0.03f, Ink.FIXED, RED), box(0.33f, 0.58f, 0.02f, 0.06f, 0.01f, Ink.LIGHT),
            dot(0.52f, 0.62f, 0.025f, Ink.FIXED, YELLOW), dot(0.60f, 0.62f, 0.025f, Ink.FIXED, GREEN), dot(0.68f, 0.62f, 0.025f, Ink.FIXED, BLUE),
            box(0.36f, 0.76f, 0.28f, 0.10f, 0.02f, Ink.SHADE)),
        "atari2600" to listOf(
            box(0.06f, 0.36f, 0.88f, 0.38f, 0.03f, Ink.SHADE),
            box(0.06f, 0.50f, 0.88f, 0.24f, 0.03f, Ink.FIXED, 0xFF8A5A2B.toInt()),
            box(0.20f, 0.28f, 0.60f, 0.14f, 0.02f, Ink.BODY),
            box(0.30f, 0.32f, 0.40f, 0.05f, 0.02f, Ink.SCREEN)) +
            listOf(0.16f, 0.28f, 0.68f, 0.80f).map { x -> box(x, 0.40f, 0.05f, 0.08f, 0.02f, Ink.LIGHT) },
        "dos" to listOf(
            box(0.10f, 0.08f, 0.80f, 0.62f, 0.04f, Ink.BODY),
            box(0.16f, 0.14f, 0.68f, 0.48f, 0.02f, Ink.SCREEN),
            box(0.20f, 0.20f, 0.06f, 0.05f, 0.0f, Ink.FIXED, GREEN), box(0.28f, 0.22f, 0.20f, 0.025f, 0.0f, Ink.FIXED, GREEN),
            box(0.40f, 0.70f, 0.20f, 0.10f, 0.0f, Ink.SHADE),
            box(0.08f, 0.80f, 0.84f, 0.12f, 0.03f, Ink.BODY),
            box(0.56f, 0.84f, 0.28f, 0.03f, 0.01f, Ink.SHADE)),
    )

    fun forSystem(id: String): List<Shape> = shapes[id] ?: GENERIC
}
