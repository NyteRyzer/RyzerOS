package com.ryzer.os

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * Console-shaped icons for each system (shapes in [IconShapes]), drawn in the system's colour with a
 * light outline so they read on any background. Each size and colour is drawn once, then reused.
 */
object SystemIcons {
    private val cache = object : LinkedHashMap<String, Bitmap>(48, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, Bitmap>) = size > 48
    }
    private val bmpP = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dst = RectF()

    private fun render(id: String, color: Int, px: Int): Bitmap {
        val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val s = px.toFloat()
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = (s * 0.018f).coerceAtLeast(1f) }
        val body = mix(color, 0xFFFFFFFF.toInt(), 0.12f)
        val shapes = IconShapes.forSystem(id)
        fun paint(shape: Shape, p: Paint, dx: Float = 0f, dy: Float = 0f) = when (shape) {
            is Shape.Box -> c.drawRoundRect(RectF((shape.x + dx) * s, (shape.y + dy) * s, (shape.x + shape.w + dx) * s, (shape.y + shape.h + dy) * s),
                shape.r * s, shape.r * s, p)
            is Shape.Dot -> c.drawCircle((shape.cx + dx) * s, (shape.cy + dy) * s, shape.r * s, p)
        }
        // A soft shadow under the outer parts, so the icon sits on top of cover art
        fill.color = 0x55000000
        for (sh in shapes) if (sh.ink == Ink.BODY || sh.ink == Ink.FIXED) paint(sh, fill, 0.012f, 0.02f)
        for (sh in shapes) {
            fill.color = when (sh.ink) {
                Ink.BODY -> body
                Ink.SHADE -> mix(color, 0xFF000000.toInt(), 0.45f)
                Ink.SCREEN -> 0xFF101018.toInt()
                Ink.LIGHT -> 0xFFF5F5FA.toInt()
                Ink.FIXED -> sh.color
            }
            paint(sh, fill)
            if (sh.ink == Ink.BODY) { edge.color = 0xD9FFFFFF.toInt(); paint(sh, edge) }
        }
        return b
    }

    /** The icon for system [id], fitted and centred in [r]. */
    fun draw(c: Canvas, id: String, color: Int, r: RectF) {
        val side = minOf(r.width(), r.height())
        // Drawn at twice the logical size: the canvas is scaled up on big screens (480 logical rows on a 1080p panel)
        val px = SkinMath.quantize(side * 2, 16f).toInt().coerceAtMost(512)
        val bmp = cache.getOrPut("$id|$px|$color") { render(id, color, px) }
        dst.set(r.centerX() - side / 2, r.centerY() - side / 2, r.centerX() + side / 2, r.centerY() + side / 2)
        c.drawBitmap(bmp, null, dst, bmpP)
    }
}

/** Every system's icon on one sheet (Tools > System icons, and the screenshot workflow). */
class IconSheet(app: Ryzer) : Screen(app) {
    override val title = "System icons"

    override fun handle(a: String) { if (a == "b" || a == "a") { app.sounds.play("back"); app.pop() } }

    override fun draw(c: Canvas, w: Float, h: Float) {
        G.topBar(c, app, th, title, w)
        val list = app.systems
        val cols = 7
        val cw = (w - 32) / cols
        val ch = (h - 52 - 44) / ((list.size + cols - 1) / cols)
        list.forEachIndexed { i, s ->
            val x = 16 + (i % cols) * cw; val y = 52 + (i / cols) * ch
            val side = minOf(cw, ch - 22) - 6
            SystemIcons.draw(c, s.id, s.color, RectF(x + (cw - side) / 2, y, x + (cw + side) / 2, y + side))
            G.text(c, s.short, x + cw / 2, y + side + 2, 16f, th.c("text_dim"), align = 'c', maxW = cw - 4)
        }
    }

    override fun drawTop(c: Canvas, w: Float, h: Float) {
        G.topBar(c, app, th, title, w)
        val s = app.systems.firstOrNull { it.id == "3ds" } ?: app.systems.firstOrNull() ?: return
        SystemIcons.draw(c, s.id, s.color, RectF(w / 2 - 150, 60f, w / 2 + 150, 360f))
        G.text(c, "${app.systems.size} systems", w / 2, h - 80, 24f, th.c("text_dim"), align = 'c')
    }

    override fun hints() = listOf("B" to "Back")
}
