package com.ryzer.os

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import org.json.JSONObject
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/** A theme: 12 colours plus a few style switches. Same JSON format as the Linux edition. */
class Theme(
    val id: String,
    var name: String,
    val colors: MutableMap<String, Int>,
    val style: MutableMap<String, Any>,
    val builtin: Boolean,
    var author: String = "Ryzer",
) {
    fun c(key: String): Int = colors[key] ?: 0xFFFF00FF.toInt()
    val radius get() = (style["corner_radius"] as Number).toFloat()
    val tileStyle get() = style["tile_style"] as String
    val background get() = style["background"] as String
    val fontScale get() = (style["font_scale"] as Number).toFloat()
    val particles get() = style["particles"] as Boolean
    val panelAlpha get() = (style["panel_alpha"] as? Number)?.toFloat() ?: 1f
    val wallpaper get() = style["wallpaper"] as? String ?: ""
    val wallpaperDim get() = (style["wallpaper_dim"] as? Number)?.toFloat() ?: 0.55f

    fun copy(newId: String, newName: String) =
        Theme(newId, newName, colors.toMutableMap(), style.toMutableMap(), builtin = false, author = "You")

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("author", author)
        put("colors", JSONObject().apply { for (k in COLOR_KEYS) put(k, hex(c(k))) })
        put("style", JSONObject(style as Map<*, *>))
    }

    companion object {
        val COLOR_KEYS = listOf("bg_top", "bg_bottom", "panel", "panel_border", "accent", "accent2",
            "text", "text_dim", "highlight_text", "good", "warn", "bad")
        val BACKGROUNDS = listOf("gradient", "scanlines", "grid", "dots", "waves", "stars")
        val TILE_STYLES = listOf("glow", "flat", "outline")

        fun hex(c: Int) = String.format("#%06X", c and 0xFFFFFF)

        fun parseColor(s: String): Int {
            val h = s.trim().removePrefix("#")
            require(h.length == 6 && h.all { it.isLetterOrDigit() }) { "bad colour $s" }
            return (0xFF000000 or h.toLong(16)).toInt()
        }

        /** Returns null for a broken theme so one bad file never stops the UI from starting. */
        fun parse(json: JSONObject, builtin: Boolean): Theme? = try {
            val cj = json.getJSONObject("colors")
            val colors = COLOR_KEYS.associateWith { parseColor(cj.getString(it)) }.toMutableMap()
            val sj = json.optJSONObject("style") ?: JSONObject()
            val style = mutableMapOf<String, Any>(
                "corner_radius" to sj.optInt("corner_radius", 12).coerceIn(0, 32),
                "tile_style" to sj.optString("tile_style", "glow").let { if (it in TILE_STYLES) it else "glow" },
                "background" to sj.optString("background", "gradient").let { if (it in BACKGROUNDS) it else "gradient" },
                "font_scale" to sj.optDouble("font_scale", 1.0).toFloat().coerceIn(0.8f, 1.4f),
                "particles" to sj.optBoolean("particles", false),
            )
            // Optional keys used by photo themes
            if (sj.has("panel_alpha")) style["panel_alpha"] = sj.optDouble("panel_alpha", 1.0).toFloat().coerceIn(0.3f, 1f)
            if (sj.has("wallpaper")) style["wallpaper"] = sj.optString("wallpaper")
            if (sj.has("wallpaper_dim")) style["wallpaper_dim"] = sj.optDouble("wallpaper_dim", 0.55).toFloat().coerceIn(0f, 0.9f)
            Theme(json.getString("id"), json.optString("name", json.getString("id")), colors, style, builtin,
                json.optString("author", "Ryzer"))
        } catch (e: Exception) {
            null
        }
    }
}

object Themes {
    fun loadAll(app: Ryzer): LinkedHashMap<String, Theme> {
        val out = LinkedHashMap<String, Theme>()
        val am = app.ctx.assets
        for (f in (am.list("themes") ?: emptyArray()).sorted()) {
            if (!f.endsWith(".json")) continue
            val t = am.open("themes/$f").bufferedReader().use { Theme.parse(JSONObject(it.readText()), true) }
            if (t != null) out[t.id] = t
        }
        app.userThemesDir().listFiles { f -> f.name.endsWith(".json") }?.sortedBy { it.name }?.forEach { f ->
            runCatching { Theme.parse(JSONObject(f.readText()), false) }.getOrNull()?.let { out[it.id] = it }
        }
        return out
    }

    fun save(app: Ryzer, t: Theme) = File(app.userThemesDir(), "${t.id}.json").writeText(t.toJson().toString(2))
    fun delete(app: Ryzer, t: Theme) { if (!t.builtin) File(app.userThemesDir(), "${t.id}.json").delete() }

    fun nextCustomId(themes: Map<String, Theme>): Pair<String, String> {
        var n = 1
        while ("custom-$n" in themes) n++
        return "custom-$n" to "Custom $n"
    }
}

fun mix(a: Int, b: Int, t: Float): Int {
    val tt = t.coerceIn(0f, 1f)
    fun ch(s: Int) = (((a shr s) and 255) + (((b shr s) and 255) - ((a shr s) and 255)) * tt).toInt()
    return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}

fun withAlpha(c: Int, a: Float) = ((a.coerceIn(0f, 1f) * 255).toInt() shl 24) or (c and 0xFFFFFF)

/**
 * Drawing toolkit. Everything is in logical units: the canvas is pre-scaled so the
 * screen is always 480 units tall (width follows the display's aspect ratio), so the
 * layout looks the same on a 640x480 RG DS panel and a 1080p AYN Odin.
 */
object G {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textP = Paint(Paint.ANTI_ALIAS_FLAG)
    private var regular: Typeface = Typeface.DEFAULT
    private var bold: Typeface = Typeface.DEFAULT_BOLD
    val FONTS = listOf("default", "condensed", "mono", "serif", "rounded")

    fun setFont(name: String) {
        val family = when (name) {
            "condensed" -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
            "mono" -> Typeface.MONOSPACE
            "serif" -> Typeface.SERIF
            "rounded" -> Typeface.create("casual", Typeface.NORMAL)
            else -> Typeface.DEFAULT
        }
        regular = family
        bold = Typeface.create(family, Typeface.BOLD)
    }
    private val bgCache = HashMap<String, Bitmap>()

    /** Draws text with its top edge at y (pygame-style). `size` matches the Linux edition's font sizes. */
    fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, align: Char = 'l',
             isBold: Boolean = false, maxW: Float = 0f, theme: Theme? = null): RectF {
        textP.textSize = size * 0.72f * (theme?.fontScale ?: 1f)
        textP.typeface = if (isBold) bold else regular
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

    fun measure(s: String, size: Float, isBold: Boolean = false): Float {
        textP.textSize = size * 0.72f
        textP.typeface = if (isBold) bold else regular
        return textP.measureText(s)
    }

    fun rect(c: Canvas, r: RectF, color: Int, radius: Float = 0f) {
        fill.color = color; fill.shader = null
        c.drawRoundRect(r, radius, radius, fill)
    }

    fun outline(c: Canvas, r: RectF, color: Int, width: Float, radius: Float = 0f) {
        stroke.color = color; stroke.strokeWidth = width
        c.drawRoundRect(r, radius, radius, stroke)
    }

    fun circle(c: Canvas, x: Float, y: Float, r: Float, color: Int) { fill.color = color; fill.shader = null; c.drawCircle(x, y, r, fill) }
    fun ring(c: Canvas, x: Float, y: Float, r: Float, color: Int, w: Float) { stroke.color = color; stroke.strokeWidth = w; c.drawCircle(x, y, r, stroke) }
    fun line(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, color: Int, w: Float = 1f) {
        stroke.color = color; stroke.strokeWidth = w; c.drawLine(x1, y1, x2, y2, stroke)
    }
    fun path(c: Canvas, p: Path, color: Int, filled: Boolean = true, w: Float = 2f) {
        if (filled) { fill.color = color; fill.shader = null; c.drawPath(p, fill) }
        else { stroke.color = color; stroke.strokeWidth = w; c.drawPath(p, stroke) }
    }

    fun background(c: Canvas, th: Theme, w: Float, h: Float, t: Float) {
        // Keyed on the background colours only, so live accent effects (RGB mode) don't rebuild it every frame
        val key = "${th.id}|${th.c("bg_top")}|${th.c("bg_bottom")}|${th.c("text")}|${th.background}|${w.toInt()}x${h.toInt()}"
        val bmp = bgCache[key] ?: buildBg(th, w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1)).also {
            if (bgCache.size > 6) bgCache.clear()
            bgCache[key] = it
        }
        c.drawBitmap(bmp, 0f, 0f, null)
        if (th.background == "waves") {
            val col = mix(th.c("bg_bottom"), th.c("accent"), 0.18f)
            for (k in 0 until 3) {
                val p = Path()
                var x = 0f
                while (x <= w + 16) {
                    val y = h * 0.72f + k * 18 + sin(x / 70 + t * (0.6f + k * 0.2f) + k) * 10
                    if (x == 0f) p.moveTo(x, y) else p.lineTo(x, y)
                    x += 16
                }
                path(c, p, col, filled = false, w = 2f)
            }
        }
    }

    private fun buildBg(th: Theme, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint()
        p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), th.c("bg_top"), th.c("bg_bottom"), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        val lineCol = mix(th.c("bg_top"), th.c("accent"), 0.12f)
        when (th.background) {
            "scanlines" -> { var y = 0; while (y < h) { line(c, 0f, y.toFloat(), w.toFloat(), y.toFloat(), 0x55000000, 1f); y += 3 } }
            "grid" -> {
                var x = 0; while (x < w) { line(c, x.toFloat(), h / 2f, w / 2f + (x - w / 2f) * 2, h.toFloat(), lineCol); x += 32 }
                for (i in 0 until 8) { val y = h / 2f + (h / 2f) * Math.pow(i / 8.0, 1.6).toFloat(); line(c, 0f, y, w.toFloat(), y, lineCol) }
            }
            "dots" -> { var y = 8; while (y < h) { var x = 8 + (y / 20 % 2) * 10; while (x < w) { circle(c, x.toFloat(), y.toFloat(), 1f, lineCol); x += 20 }; y += 20 } }
            "stars" -> { val r = Random(7); repeat(w * h / 2200) { circle(c, r.nextInt(w).toFloat(), r.nextInt(h).toFloat(), 0.8f,
                mix(th.c("bg_top"), th.c("text"), r.nextFloat() * 0.6f)) } }
        }
        return bmp
    }

    /** A user wallpaper, cropped to fill and dimmed with the theme so text stays readable. */
    fun wallpaper(c: Canvas, th: Theme, bmp: Bitmap, w: Float, h: Float) {
        val sc = max(w / bmp.width, h / bmp.height)
        val dw = bmp.width * sc; val dh = bmp.height * sc
        c.drawBitmap(bmp, null, RectF((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2), Paint(Paint.FILTER_BITMAP_FLAG))
        rect(c, RectF(0f, 0f, w, h), withAlpha(th.c("bg_bottom"), th.wallpaperDim))
    }

    /** Panel opacity for this frame: below 1 when "glass" panels sit over a wallpaper. */
    var panelAlpha = 1f

    private val colorCache = object : LinkedHashMap<Bitmap, Int>(16, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<Bitmap, Int>) = size > 40
    }

    /** The most vivid common colour of an image (used to tint the UI around box art). */
    fun dominant(bmp: Bitmap): Int = colorCache.getOrPut(bmp) { Palette.from(bmp).firstOrNull() ?: 0xFF808080.toInt() }

    fun analogClock(c: Canvas, th: Theme, cx: Float, cy: Float, r: Float) {
        val cal = java.util.Calendar.getInstance()
        circle(c, cx, cy, r, th.c("panel"))
        ring(c, cx, cy, r, th.c("accent"), 4f)
        for (i in 0 until 12) {
            val a = i * PI / 6
            val inner = if (i % 3 == 0) r * 0.78f else r * 0.86f
            line(c, cx + inner * sin(a).toFloat(), cy - inner * cos(a).toFloat(),
                cx + r * 0.94f * sin(a).toFloat(), cy - r * 0.94f * cos(a).toFloat(), th.c("text_dim"), if (i % 3 == 0) 3f else 1.5f)
        }
        fun hand(frac: Double, len: Float, width: Float, col: Int) {
            val a = frac * 2 * PI
            line(c, cx, cy, cx + len * sin(a).toFloat(), cy - len * cos(a).toFloat(), col, width)
        }
        val m = cal.get(java.util.Calendar.MINUTE); val hr = cal.get(java.util.Calendar.HOUR) + m / 60.0
        hand(hr / 12, r * 0.5f, 6f, th.c("text"))
        hand(m / 60.0, r * 0.75f, 4f, th.c("text"))
        hand(cal.get(java.util.Calendar.SECOND) / 60.0, r * 0.82f, 2f, th.c("accent"))
        circle(c, cx, cy, 5f, th.c("accent"))
    }

    fun panel(c: Canvas, r: RectF, th: Theme, selected: Boolean = false, pulse: Float = 0f, fillColor: Int? = null) {
        val rad = th.radius
        if (selected && th.tileStyle == "glow") {
            for (i in 3 downTo 1) {
                val g = RectF(r).apply { inset(-i * 2f, -i * 2f) }
                rect(c, g, withAlpha(th.c("accent"), 0.18f + 0.08f * (3 - i) + 0.1f * pulse), rad + i * 2)
            }
        }
        val body0 = fillColor ?: when {
            th.tileStyle == "outline" -> th.c("panel")
            selected -> mix(th.c("panel"), th.c("accent"), 0.18f)
            else -> th.c("panel")
        }
        val body = if (fillColor == null && panelAlpha < 1f) withAlpha(body0, panelAlpha) else body0
        rect(c, r, body, rad)
        val border = if (selected) th.c("accent") else th.c("panel_border")
        outline(c, r, border, if (selected) 3f else if (th.tileStyle == "outline") 2f else 1f, rad)
    }

    fun topBar(c: Canvas, app: Ryzer, th: Theme, title: String, w: Float) {
        text(c, title, 16f, 10f, 26f, th.c("text"), isBold = true, theme = th, maxW = w * 0.55f)
        val now = java.util.Date()
        val clock = java.text.SimpleDateFormat(if (app.cfg.clock24) "HH:mm" else "h:mm a", java.util.Locale.getDefault()).format(now)
        var x = w - 16
        val r = text(c, clock, x, 11f, 24f, th.c("text"), align = 'r', theme = th)
        x = r.left - 14
        if (app.cfg.showBattery) {
            val (pct, charging) = app.battery()
            if (pct >= 0) {
                val br = RectF(x - 30, 14f, x, 28f)
                outline(c, br, th.c("text_dim"), 2f, 3f)
                rect(c, RectF(br.right, br.top + 4, br.right + 3, br.top + 10), th.c("text_dim"))
                val col = when { pct <= 15 -> th.c("bad"); pct <= 30 -> th.c("warn"); else -> th.c("good") }
                rect(c, RectF(br.left + 3, br.top + 3, br.left + 3 + 24 * pct / 100f, br.bottom - 3), col)
                val lr = text(c, "$pct%" + if (charging) " +" else "", br.left - 6, 12f, 20f, th.c("text_dim"), align = 'r', theme = th)
                x = lr.left - 12
            }
        }
        if (app.wifiConnected()) {
            for (i in 0 until 3) {
                stroke.color = th.c("text_dim"); stroke.strokeWidth = 2f
                val rr = RectF(x - 10 - i * 5, 26f - i * 5, x + i * 5, 36f + i * 5)
                c.drawArc(rr, 225f, 90f, false, stroke)
            }
        }
        line(c, 12f, 42f, w - 12, 42f, th.c("panel_border"))
    }

    /** Bottom bar of button prompts. Registers tap targets so touch screens can press them too. */
    fun hints(c: Canvas, th: Theme, items: List<Pair<String, String>>, w: Float, h: Float, hit: (RectF, String) -> Unit) {
        line(c, 12f, h - 36, w - 12, h - 36, th.c("panel_border"))
        var x = w - 16
        for ((label, action) in items.reversed()) {
            val r = text(c, action, x, h - 27, 20f, th.c("text_dim"), align = 'r', theme = th)
            val left: Float
            if (label.length == 1) {
                circle(c, r.left - 16, h - 19, 11f, th.c("accent"))
                text(c, label, r.left - 16, h - 27, 18f, th.c("highlight_text"), align = 'c', isBold = true)
                left = r.left - 28
            } else {
                val tw = measure(label, 16f, true)
                val pr = RectF(r.left - 12 - tw - 10, h - 28, r.left - 7, h - 10)
                rect(c, pr, th.c("accent"), 8f)
                text(c, label, pr.centerX(), h - 26, 16f, th.c("highlight_text"), align = 'c', isBold = true)
                left = pr.left
            }
            hit(RectF(left - 4, h - 34, r.right + 4, h), label.lowercase().substringBefore("/"))
            x = left - 14
        }
    }

    fun toast(c: Canvas, th: Theme, msg: String, alpha: Float, w: Float, h: Float) {
        val tw = min(w - 40, measure(msg, 22f) + 32)
        val r = RectF(w / 2 - tw / 2, h - 86, w / 2 + tw / 2, h - 46)
        rect(c, r, withAlpha(th.c("panel"), 0.92f * alpha), 12f)
        outline(c, r, withAlpha(th.c("accent"), alpha), 2f, 12f)
        text(c, msg, w / 2, r.top + 11, 22f, withAlpha(th.c("text"), alpha), align = 'c', maxW = tw - 24)
    }

    fun scrollbar(c: Canvas, th: Theme, r: RectF, offset: Int, visible: Int, total: Int) {
        if (total <= visible) return
        rect(c, r, th.c("panel"), 3f)
        val hh = max(16f, r.height() * visible / total)
        val y = r.top + (r.height() - hh) * offset / max(1, total - visible)
        rect(c, RectF(r.left, y, r.right, y + hh), th.c("accent"), 3f)
    }

    fun scrollOffset(sel: Int, cur: Int, visible: Int, total: Int): Int {
        if (total <= visible) return 0
        var o = cur
        if (sel < o + 1) o = sel - 1 else if (sel > o + visible - 2) o = sel - visible + 2
        return o.coerceIn(0, total - visible)
    }

    fun wrap(s: String, size: Float, width: Float): List<String> {
        val out = ArrayList<String>()
        for (para in s.split("\n")) {
            var line = ""
            for (word in para.split(" ")) {
                val trial = if (line.isEmpty()) word else "$line $word"
                if (measure(trial, size) <= width) line = trial else { if (line.isNotEmpty()) out.add(line); line = word }
            }
            out.add(line)
        }
        return out
    }

    /** The hexagon "R" emblem used on the boot screen. */
    fun emblem(c: Canvas, th: Theme, cx: Float, cy: Float, size: Float) {
        val p = Path()
        for (k in 0 until 6) {
            val a = (60 * k + 30) * PI / 180
            val x = cx + size * cos(a).toFloat(); val y = cy + size * sin(a).toFloat()
            if (k == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        path(c, p, th.c("panel"))
        path(c, p, th.c("accent"), filled = false, w = 4f)
        text(c, "R", cx, cy - size * 0.62f, size * 1.4f, th.c("accent"), align = 'c', isBold = true)
    }

    private val imageCache = object : LinkedHashMap<String, Bitmap?>(32, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, Bitmap?>) = size > 40
    }

    fun image(file: File?): Bitmap? {
        if (file == null) return null
        return imageCache.getOrPut(file.path) {
            runCatching {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, o)
                var s = 1
                while (o.outWidth / (s * 2) >= 400 && o.outHeight / (s * 2) >= 400) s *= 2
                BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = s })
            }.getOrNull()
        }
    }

    fun drawImageFit(c: Canvas, bmp: Bitmap, box: RectF): RectF {
        val sc = min(box.width() / bmp.width, box.height() / bmp.height)
        val dw = bmp.width * sc; val dh = bmp.height * sc
        val dst = RectF(box.centerX() - dw / 2, box.top, box.centerX() + dw / 2, box.top + dh)
        c.drawBitmap(bmp, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        return dst
    }
}

/** Floating dots for themes with particles on. */
class Particles(private val n: Int = 24) {
    private val items = ArrayList<FloatArray>()
    fun draw(c: Canvas, th: Theme, dt: Float, w: Float, h: Float) {
        if (!th.particles) return
        while (items.size < n) items.add(floatArrayOf(Random.nextFloat() * w, Random.nextFloat() * h, 6 + Random.nextFloat() * 16, 1 + Random.nextFloat() * 1.5f))
        val col = mix(th.c("bg_bottom"), th.c("accent2"), 0.45f)
        for (p in items) {
            p[1] -= p[2] * dt
            if (p[1] < -4) { p[0] = Random.nextFloat() * w; p[1] = h + 4 }
            G.circle(c, p[0], p[1], p[3], col)
        }
    }
}


/**
 * Picks out the main colours of a picture: shrink it, bucket the pixels by colour and
 * rank the buckets by how common and how vivid they are. Used for photo themes and art tints.
 */
object Palette {
    data class Swatch(val color: Int, val count: Int, val sat: Float, val lum: Float)

    fun swatches(src: Bitmap): List<Swatch> {
        val small = Bitmap.createScaledBitmap(src, 48, (48f * src.height / src.width).toInt().coerceIn(8, 96), true)
        val buckets = HashMap<Int, IntArray>()   // key -> [count, r, g, b]
        val hsv = FloatArray(3)
        for (y in 0 until small.height) for (x in 0 until small.width) {
            val p = small.getPixel(x, y)
            if ((p ushr 24) < 128) continue
            val r = (p shr 16) and 255; val g = (p shr 8) and 255; val b = p and 255
            val key = ((r shr 5) shl 6) or ((g shr 5) shl 3) or (b shr 5)
            val acc = buckets.getOrPut(key) { IntArray(4) }
            acc[0]++; acc[1] += r; acc[2] += g; acc[3] += b
        }
        if (small !== src) small.recycle()
        return buckets.values.map { a ->
            val col = (0xFF shl 24) or ((a[1] / a[0]) shl 16) or ((a[2] / a[0]) shl 8) or (a[3] / a[0])
            android.graphics.Color.colorToHSV(col, hsv)
            Swatch(col, a[0], hsv[1], hsv[2])
        }.sortedByDescending { it.count }
    }

    /** Vivid colours first, weighted by how much of the picture they cover. */
    fun from(src: Bitmap): List<Int> = swatches(src)
        .filter { it.lum > 0.25f }
        .sortedByDescending { it.count * (0.25f + it.sat) * (0.4f + it.lum) }
        .map { it.color }
}

/**
 * Live wallpapers: animated backgrounds drawn in code (no video files), tinted by the theme.
 * Everything is computed from time and an index, so there is no state to keep.
 */
object Live {
    val KINDS = listOf("off", "aurora", "starfield", "bubbles", "rain", "snow", "waves")
    private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

    private fun rnd(i: Int, salt: Int): Float {
        var x = i * 374761393 + salt * 668265263
        x = (x xor (x ushr 13)) * 1274126177
        return ((x xor (x ushr 16)) and 0xFFFF) / 65535f
    }

    fun draw(c: Canvas, th: Theme, kind: String, w: Float, h: Float, time: Float, speed: Float) {
        val t = time * speed
        when (kind) {
            "aurora" -> {
                p.style = android.graphics.Paint.Style.STROKE
                for (band in 0 until 4) {
                    val col = mix(th.c("accent"), th.c("accent2"), band / 3f)
                    val path = Path()
                    var x = -20f
                    while (x <= w + 20) {
                        val y = h * (0.25f + band * 0.08f) + sin(x / 90f + t * 0.4f + band) * 26 + sin(x / 37f - t * 0.7f) * 8
                        if (x == -20f) path.moveTo(x, y) else path.lineTo(x, y)
                        x += 12
                    }
                    for (k in 0 until 3) {
                        p.strokeWidth = 40f - k * 12
                        p.color = withAlpha(col, 0.05f + k * 0.05f)
                        c.drawPath(path, p)
                    }
                }
            }
            "starfield" -> {
                p.style = android.graphics.Paint.Style.FILL
                val cx = w / 2; val cy = h / 2
                for (i in 0 until 90) {
                    val ang = rnd(i, 1) * 6.2832f
                    val z = ((rnd(i, 2) + t * 0.12f) % 1f)
                    val dist = z * z * (w * 0.8f)
                    p.color = withAlpha(mix(th.c("text"), th.c("accent"), rnd(i, 3)), 0.25f + z * 0.75f)
                    c.drawCircle(cx + cos(ang) * dist, cy + sin(ang) * dist, 0.6f + z * 2.4f, p)
                }
            }
            "bubbles" -> {
                p.style = android.graphics.Paint.Style.STROKE
                p.strokeWidth = 2f
                for (i in 0 until 26) {
                    val r = 6 + rnd(i, 1) * 26
                    val y = h + r - ((rnd(i, 2) * h + t * (18 + rnd(i, 3) * 30)) % (h + 2 * r))
                    val x = rnd(i, 4) * w + sin(t * 0.8f + i) * 14
                    p.color = withAlpha(mix(th.c("accent2"), th.c("text"), 0.3f), 0.35f)
                    c.drawCircle(x, y, r, p)
                }
            }
            "rain" -> {
                p.style = android.graphics.Paint.Style.STROKE
                p.strokeWidth = 2f
                val cols = (w / 14).toInt()
                for (i in 0 until cols) {
                    val speedI = 60 + rnd(i, 1) * 140
                    val y = (rnd(i, 2) * h + t * speedI) % (h + 80) - 40
                    val len = 20 + rnd(i, 3) * 50
                    p.color = withAlpha(th.c("accent"), 0.15f + rnd(i, 4) * 0.35f)
                    c.drawLine(i * 14f + 7, y, i * 14f + 7, y + len, p)
                }
            }
            "snow" -> {
                p.style = android.graphics.Paint.Style.FILL
                for (i in 0 until 70) {
                    val y = (rnd(i, 1) * h + t * (12 + rnd(i, 2) * 26)) % (h + 10) - 5
                    val x = (rnd(i, 3) * w + sin(t * 0.6f + i) * 18 + w) % w
                    p.color = withAlpha(0xFFFFFFFF.toInt(), 0.4f + rnd(i, 4) * 0.5f)
                    c.drawCircle(x, y, 1f + rnd(i, 5) * 2.5f, p)
                }
            }
            "waves" -> {
                p.style = android.graphics.Paint.Style.FILL
                for (k in 0 until 3) {
                    val path = Path()
                    path.moveTo(0f, h)
                    var x = 0f
                    while (x <= w + 16) {
                        path.lineTo(x, h * (0.62f + k * 0.1f) + sin(x / (110f - k * 20) + t * (0.5f + k * 0.25f)) * (16 - k * 3))
                        x += 16
                    }
                    path.lineTo(w, h); path.close()
                    p.color = withAlpha(mix(th.c("accent"), th.c("bg_bottom"), 0.3f + k * 0.2f), 0.18f + k * 0.06f)
                    c.drawPath(path, p)
                }
            }
        }
    }
}
