package com.ryzer.os

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface

/**
 * The 3.0 "Blades" look: slanted panels, a bold italic display font, segment meters and a cached glow.
 * The toolkit in [G] asks [blades] and draws through here; Classic never touches it.
 */
object Skin {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.MITER }
    private val textP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bmpP = Paint(Paint.FILTER_BITMAP_FLAG)
    private val path = Path()
    private var face: Typeface? = null

    val blades: Boolean get() = Ryzer.instance?.cfg?.uiStyle == "blades"

    /** Titillium Web Bold Italic from the assets, or the system's bold italic if it can't be read. */
    fun display(ctx: Context): Typeface = face ?: (runCatching {
        Typeface.createFromAsset(ctx.assets, "fonts/TitilliumWeb-BoldItalic.ttf")
    }.getOrNull() ?: Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)).also { face = it }

    private fun shape(r: RectF, skew: Float): Path {
        val p = SkinMath.para(r.left, r.top, r.width() - skew, r.height(), skew)
        path.reset()
        path.moveTo(p[0], p[1]); path.lineTo(p[2], p[3]); path.lineTo(p[4], p[5]); path.lineTo(p[6], p[7]); path.close()
        return path
    }

    /** A filled parallelogram that fits inside [r] (the lean is taken out of its width). */
    fun parallelogram(c: Canvas, r: RectF, color: Int, skew: Float = SkinMath.skewFor(r.height())) {
        fill.color = color; fill.shader = null
        c.drawPath(shape(r, skew), fill)
    }

    fun outlinePara(c: Canvas, r: RectF, color: Int, width: Float, skew: Float = SkinMath.skewFor(r.height())) {
        stroke.color = color; stroke.strokeWidth = width
        c.drawPath(shape(r, skew), stroke)
    }

    /** Clips to a parallelogram (for covers); pair with c.save()/c.restore(). */
    fun clip(c: Canvas, r: RectF, skew: Float = SkinMath.skewFor(r.height())) { c.clipPath(Path(shape(r, skew))) }

    private const val GLOW = 14
    private val glowCache = object : LinkedHashMap<String, Bitmap>(24, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, Bitmap>) = size > 24
    }

    /** A soft halo around the parallelogram at [r], blurred once per size and colour then reused. */
    fun glow(c: Canvas, r: RectF, color: Int, skew: Float = SkinMath.skewFor(r.height())) {
        val w = r.width().toInt().coerceAtLeast(1); val h = r.height().toInt().coerceAtLeast(1)
        val key = "$w|$h|${skew.toInt()}|$color"
        val bmp = glowCache.getOrPut(key) {
            Bitmap.createBitmap(w + GLOW * 2, h + GLOW * 2, Bitmap.Config.ARGB_8888).also { b ->
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color; maskFilter = BlurMaskFilter(GLOW * 0.6f, BlurMaskFilter.Blur.NORMAL)
                }
                Canvas(b).drawPath(Path(shape(RectF(GLOW.toFloat(), GLOW.toFloat(), GLOW + w.toFloat(), GLOW + h.toFloat()), skew)), p)
            }
        }
        c.drawBitmap(bmp, r.left - GLOW, r.top - GLOW, bmpP)
    }

    /** A row of [count] slanted segments, the first [on] lit. */
    fun segments(c: Canvas, x: Float, y: Float, w: Float, h: Float, on: Int, count: Int, onColor: Int, offColor: Int) {
        val gap = 3f
        val sw = (w - gap * (count - 1)) / count
        val skew = (h * 0.5f).coerceAtMost(sw * 0.6f)
        for (i in 0 until count) {
            val sx = x + i * (sw + gap)
            parallelogram(c, RectF(sx, y, sx + sw, y + h), if (i < on) onColor else offColor, skew)
        }
    }

    /** Display-font text with its top edge at y, like [G.text]. */
    fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, align: Char = 'l', maxW: Float = 0f): RectF {
        textP.textSize = size * 0.72f
        textP.typeface = face ?: Ryzer.instance?.ctx?.let { display(it) } ?: Typeface.DEFAULT_BOLD
        textP.color = color
        var str = s
        if (maxW > 0 && textP.measureText(str) > maxW) {
            while (str.isNotEmpty() && textP.measureText("$str...") > maxW) str = str.dropLast(1)
            str += "..."
        }
        val w = textP.measureText(str)
        val left = when (align) { 'c' -> x - w / 2; 'r' -> x - w; else -> x }
        val fm = textP.fontMetrics
        c.drawText(str, left, y - fm.ascent, textP)
        return RectF(left, y, left + w, y + fm.descent - fm.ascent)
    }

    fun measure(s: String, size: Float): Float {
        textP.textSize = size * 0.72f
        textP.typeface = face ?: Typeface.DEFAULT_BOLD
        return textP.measureText(s)
    }
}
