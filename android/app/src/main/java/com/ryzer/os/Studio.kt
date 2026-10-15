package com.ryzer.os

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.util.Base64
import org.json.JSONObject
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

private val STUDIO_LABELS = mapOf("bg_top" to "Background top", "bg_bottom" to "Background bottom", "panel" to "Panels",
    "panel_border" to "Panel borders", "accent" to "Accent", "accent2" to "Second accent", "text" to "Text", "text_dim" to "Soft text",
    "highlight_text" to "Text on accent", "good" to "Good / OK", "warn" to "Warning", "bad" to "Error")

private fun <T> cyc(options: List<T>, cur: T, d: Int): T = options[((options.indexOf(cur).coerceAtLeast(0) + d) % options.size + options.size) % options.size]

private fun luminance(c: Int): Double {
    fun ch(v: Int): Double { val s = v / 255.0; return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4) }
    return 0.2126 * ch(Color.red(c)) + 0.7152 * ch(Color.green(c)) + 0.0722 * ch(Color.blue(c))
}

/** WCAG contrast ratio between two colours (1..21). */
fun contrast(a: Int, b: Int): Double {
    val l1 = luminance(a); val l2 = luminance(b)
    return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
}

/**
 * Theme Studio: a full designer for every part of a theme - all 12 colours (pick from a
 * palette or fine-tune hue / saturation / brightness), background pattern, live wallpaper,
 * picture, glass, tiles, corners, text, font and particles - with a live preview.
 * Themes can be shared as a code and imported back.
 */
class ThemeStudio(app: Ryzer, base: Theme) : Menu(app, "Theme Studio", emptyList(), "Left/Right adjusts  |  Y switches Hue / Sat / Bright  |  A opens the colour picker") {
    val t: Theme = if (base.builtin) base.copy(Themes.nextCustomId(app.themes).first, "${base.name} Custom") else base.copy(base.id, base.name)
    private val channel = HashMap<String, Int>()   // 0 hue, 1 saturation, 2 brightness

    init {
        app.previewTheme(t)
        items = build()
    }

    private fun preview() = app.previewTheme(t)

    private fun header(label: String) = Item("-- $label --")

    private fun build(): List<Item> {
        val list = mutableListOf<Item>()
        list += Item("Name", value = { t.name }, select = { app.push(Keyboard(app, "Theme name", t.name, 24) { if (it.isNotEmpty()) t.name = it }) })
        list += header("Quick start")
        list += Item("Start from another theme", "Copies another theme's colours and style into this one.", value = { "Choose" }, change = { d ->
            val others = app.themes.values.toList()
            val src = others[((others.indexOfFirst { it.colors == t.colors }.coerceAtLeast(0) + d) % others.size + others.size) % others.size]
            t.colors.putAll(src.colors); t.style.putAll(src.style); preview()
        })
        list += Item("Randomize colours", "A fresh, matching palette every press.", select = { randomize(); preview() })
        list += Item("Use a photo", "Builds a theme from any picture in your gallery.", select = { app.endPreview(); app.pop(); PhotoTheme.pickAndApply(app) })
        list += header("Colours")
        for (key in Theme.COLOR_KEYS) list += Item(STUDIO_LABELS[key] ?: key, value = {
            val hsv = FloatArray(3); Color.colorToHSV(t.c(key), hsv)
            val ch = channel[key] ?: 0
            val part = when (ch) { 0 -> "H ${hsv[0].toInt()}°"; 1 -> "S ${(hsv[1] * 100).toInt()}%"; else -> "B ${(hsv[2] * 100).toInt()}%" }
            "$part  ${Theme.hex(t.c(key))}"
        }, change = { d ->
            val hsv = FloatArray(3); Color.colorToHSV(t.c(key), hsv)
            when (channel[key] ?: 0) {
                0 -> hsv[0] = (hsv[0] + d * 6f + 360f) % 360f
                1 -> hsv[1] = (hsv[1] + d * 0.05f).coerceIn(0f, 1f)
                else -> hsv[2] = (hsv[2] + d * 0.05f).coerceIn(0f, 1f)
            }
            t.colors[key] = Color.HSVToColor(hsv); preview()
        }, swatch = { t.c(key) }, select = { app.push(ColorPicker(app, STUDIO_LABELS[key] ?: key, t.c(key)) { t.colors[key] = it; preview() }) })
            .also { it.colorKey = key }
        list += Item("Readability check", "Compares text with the background. 4.5 or more is easy to read.", value = {
            val r = contrast(t.c("text"), t.c("bg_top"))
            "%.1f:1  %s".format(r, if (r >= 4.5) "Great" else if (r >= 3) "OK" else "Hard to read")
        }, select = {
            // Auto-fix: pick black or white text, whichever reads better
            val white = 0xFFF5F5F5.toInt(); val black = 0xFF111111.toInt()
            t.colors["text"] = if (contrast(white, t.c("bg_top")) >= contrast(black, t.c("bg_top"))) white else black
            t.colors["text_dim"] = mix(t.c("text"), t.c("bg_top"), 0.4f)
            t.colors["highlight_text"] = if (contrast(white, t.c("accent")) >= contrast(black, t.c("accent"))) white else black
            preview(); app.notify("Text colours fixed for readability")
        })
        list += header("Background")
        list += styleItem("Pattern", "background", Theme.BACKGROUNDS)
        list += Item("Live wallpaper", "Animated background for this theme (overrides the global setting).",
            value = { (t.style["live"] as? String ?: "off").replaceFirstChar { it.uppercase() } },
            change = { d -> t.style["live"] = cyc(Live.KINDS, t.style["live"] as? String ?: "off", d); preview() })
        list += Item("Picture", "A picture from RyzerOS/wallpapers, or press A to pick from your gallery.", value = { t.wallpaper.ifEmpty { "None" } }, change = { d ->
            val files = listOf("") + (app.wallpaperDir().listFiles { f -> f.extension.lowercase() in setOf("png", "jpg", "jpeg", "webp") }?.map { it.name }?.sorted() ?: emptyList())
            t.style["wallpaper"] = cyc(files, t.wallpaper, d); preview()
        }, select = {
            app.ctx.pickPhoto { uri ->
                app.runInBackground({ t.style["wallpaper"] = PhotoTheme.importPicture(app, uri) }) { e ->
                    if (e != null) app.notify("Couldn't use that picture") else preview()
                }
            }
        })
        list += Item("Picture dimming", value = { "${(t.wallpaperDim * 100).toInt()}%" }, change = { d ->
            t.style["wallpaper_dim"] = (t.wallpaperDim + d * 0.05f).coerceIn(0f, 0.9f); preview() })
        list += Item("Particles", value = { if (t.particles) "On" else "Off" }, change = { t.style["particles"] = !t.particles; preview() })
        list += header("Panels & text")
        list += Item("Panel opacity", "Lower = more see-through (glass) panels.", value = { "${(t.panelAlpha * 100).toInt()}%" }, change = { d ->
            t.style["panel_alpha"] = (t.panelAlpha + d * 0.05f).coerceIn(0.3f, 1f); preview() })
        list += styleItem("Tile style", "tile_style", Theme.TILE_STYLES)
        list += Item("Corner roundness", value = { "${t.radius.toInt()}" }, change = { d ->
            t.style["corner_radius"] = (t.radius.toInt() + d * 2).coerceIn(0, 32); preview() })
        list += Item("Text size", value = { "%.1fx".format(t.fontScale) }, change = { d ->
            t.style["font_scale"] = (t.fontScale + d * 0.1f).coerceIn(0.8f, 1.4f); preview() })
        list += Item("Font", "This theme's font (Default follows Look & feel).", value = { (t.style["font"] as? String ?: "default").replaceFirstChar { it.uppercase() } },
            change = { d -> t.style["font"] = cyc(G.FONTS, t.style["font"] as? String ?: "default", d); preview() })
        list += header("Save & share")
        list += Item("Save theme", "Saves and applies it.", select = { save(asCopy = false) })
        list += Item("Save as a new theme", select = { save(asCopy = true) })
        list += Item("Share code", "Copies a code you can paste to a friend, and saves it in RyzerOS/themes/shared.", select = { share() })
        list += Item("Import a shared code", "Paste a code into the clipboard first, then press A.", select = { import() })
        return list
    }

    private fun styleItem(label: String, key: String, options: List<String>) = Item(label,
        value = { (t.style[key] as String).replaceFirstChar { it.uppercase() } },
        change = { d -> t.style[key] = cyc(options, t.style[key] as String, d); preview() })

    /** A matching palette: a random base hue with complementary accents, dark background. */
    private fun randomize() {
        val hue = Random.nextFloat() * 360f
        val dark = Random.nextFloat() < 0.8f
        fun hsv(h: Float, s: Float, v: Float) = Color.HSVToColor(floatArrayOf((h + 360f) % 360f, s, v))
        if (dark) {
            t.colors["bg_top"] = hsv(hue, 0.55f, 0.18f); t.colors["bg_bottom"] = hsv(hue, 0.6f, 0.05f)
            t.colors["panel"] = hsv(hue, 0.5f, 0.13f); t.colors["panel_border"] = hsv(hue, 0.5f, 0.35f)
            t.colors["text"] = hsv(hue, 0.04f, 0.97f); t.colors["text_dim"] = hsv(hue, 0.15f, 0.65f)
        } else {
            t.colors["bg_top"] = hsv(hue, 0.08f, 0.99f); t.colors["bg_bottom"] = hsv(hue, 0.15f, 0.9f)
            t.colors["panel"] = hsv(hue, 0.03f, 1f); t.colors["panel_border"] = hsv(hue, 0.2f, 0.8f)
            t.colors["text"] = hsv(hue, 0.4f, 0.15f); t.colors["text_dim"] = hsv(hue, 0.15f, 0.45f)
        }
        val accent = hsv(hue + Random.nextInt(-40, 40), 0.8f, 1f)
        t.colors["accent"] = accent
        t.colors["accent2"] = hsv(hue + 150 + Random.nextInt(-30, 30), 0.65f, 1f)
        t.colors["highlight_text"] = if (contrast(0xFFFFFFFF.toInt(), accent) > contrast(0xFF111111.toInt(), accent)) 0xFFFFFFFF.toInt() else 0xFF111111.toInt()
        t.style["background"] = Theme.BACKGROUNDS.random()
    }

    private fun save(asCopy: Boolean) {
        val saved = if (asCopy) t.copy(Themes.nextCustomId(app.themes).first, "${t.name} 2") else t
        Themes.save(app, saved)
        app.themes = Themes.loadAll(app)
        app.setTheme(saved.id, save = true)
        app.focusTheme = saved.id
        app.notify("Saved '${saved.name}'")
        Milestones.check(app)
        app.pop()
    }

    private fun share() {
        val json = t.toJson().apply {
            // A shared theme travels without its picture (pictures stay on your device)
            optJSONObject("style")?.remove("wallpaper")
        }.toString()
        val code = "RYZER1:" + Base64.encodeToString(json.toByteArray(), Base64.NO_WRAP)
        val cm = app.ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Ryzer theme", code))
        runCatching { File(app.userThemesDir(), "shared").apply { mkdirs() }.let { File(it, "${t.name.replace(Regex("[^A-Za-z0-9 _-]"), "")}.ryzertheme").writeText(code) } }
        app.notify("Theme code copied - paste it anywhere to share")
    }

    private fun import() {
        val cm = app.ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.getItemAt(0)?.coerceToText(app.ctx)?.toString()?.trim().orEmpty()
        val parsed = runCatching {
            require(text.startsWith("RYZER1:"))
            Theme.parse(JSONObject(String(Base64.decode(text.removePrefix("RYZER1:"), Base64.DEFAULT))), builtin = false)
        }.getOrNull()
        if (parsed == null) { app.notify("No Ryzer theme code in the clipboard"); return }
        t.colors.putAll(parsed.colors); t.style.putAll(parsed.style)
        t.name = parsed.name
        preview()
        app.notify("Imported '${parsed.name}' - save it to keep it")
    }

    override fun handle(a: String) {
        val key = current()?.colorKey
        if (a == "y" && key != null) { channel[key] = ((channel[key] ?: 0) + 1) % 3; app.sounds.play("move"); return }
        if (a == "b") app.endPreview()
        super.handle(a)
    }

    override fun listWidth(w: Float) = if (app.dual) w else w * 0.6f

    override fun draw(c: Canvas, w: Float, h: Float) {
        super.draw(c, w, h)
        if (!app.dual) drawPreview(c, RectF(w * 0.6f, 56f, w - 14, h - 46))
    }

    override fun drawTop(c: Canvas, w: Float, h: Float) {
        G.topBar(c, app, th, "Preview: ${t.name}", w)
        drawPreview(c, RectF(30f, 54f, w - 30, h - 16))
    }

    /** A small live mock-up of Ryzer in the theme being edited. */
    private fun drawPreview(c: Canvas, r: RectF) {
        G.panel(c, r, t, fillColor = t.c("bg_top"))
        c.save()
        c.clipRect(r)
        c.translate(r.left, r.top)
        val w = r.width(); val h = r.height()
        G.background(c, t, w, h, 0f)
        app.wallpaper()?.let { if (t.wallpaper.isNotEmpty()) G.wallpaper(c, t, it, w, h) }
        (t.style["live"] as? String)?.takeIf { it != "off" }?.let { Live.draw(c, t, it, w, h, android.os.SystemClock.uptimeMillis() / 1000f, 1f) }
        G.text(c, "RYZER", 10f, 6f, 22f, t.c("text"), isBold = true, theme = t)
        val tile = (w - 50) / 3
        for (k in 0 until 3) {
            val tr = RectF(10 + k * (tile + 15), 36f, 10 + k * (tile + 15) + tile, 36f + tile)
            val prevAlpha = G.panelAlpha
            G.panelAlpha = t.panelAlpha
            G.panel(c, tr, t, selected = k == 1)
            G.panelAlpha = prevAlpha
            G.rect(c, RectF(tr.left + 5, tr.top + 5, tr.right - 5, tr.top + tile * 0.55f), mix(t.c("panel"), if (k == 1) t.c("accent") else t.c("accent2"), 0.6f), max(0f, t.radius - 4))
            G.text(c, listOf("GBA", "PS2", "3DS")[k], tr.centerX(), tr.bottom - 26, 18f, t.c("text"), align = 'c', theme = t)
        }
        val info = RectF(10f, 46f + tile, w - 10, min(h - 40, 46f + tile + 70))
        G.panel(c, info, t)
        G.text(c, "Game title", info.left + 10, info.top + 6, 22f, t.c("text"), isBold = true, theme = t)
        G.text(c, "Soft text looks like this", info.left + 10, info.top + 32, 17f, t.c("text_dim"), theme = t)
        val btn = RectF(w - 90, h - 34, w - 10, h - 8)
        G.rect(c, btn, t.c("accent"), 12f)
        G.text(c, "Play", btn.centerX(), btn.top + 3, 19f, t.c("highlight_text"), align = 'c', isBold = true)
        c.restore()
    }

    override fun hints() = listOf("Y" to "H/S/B", "B" to "Discard", "A" to "Select")
}

/** Touch- and gamepad-friendly colour picker: 12 hues x 5 shades, greys, and fine-tuning. */
class ColorPicker(app: Ryzer, override val title: String, start: Int, private val onPick: (Int) -> Unit) : Screen(app) {
    private val cols = 12
    private val colors: List<Int> = buildList {
        for (shade in listOf(0.95f to 0.35f, 1f to 0.65f, 1f to 1f, 0.75f to 1f, 0.45f to 1f)) // (value, saturation)
            for (i in 0 until cols) add(Color.HSVToColor(floatArrayOf(i * 30f, shade.second, shade.first)))
        for (i in 0 until cols) { val v = (i * 255 / (cols - 1)); add(Color.rgb(v, v, v)) }
    }
    private var sel = colors.indices.minByOrNull { dist(colors[it], start) } ?: 0
    private var current = start

    private fun dist(a: Int, b: Int) = Math.abs(Color.red(a) - Color.red(b)) + Math.abs(Color.green(a) - Color.green(b)) + Math.abs(Color.blue(a) - Color.blue(b))

    override fun handle(a: String) {
        when (a) {
            "left" -> sel = (sel - 1 + colors.size) % colors.size
            "right" -> sel = (sel + 1) % colors.size
            "up" -> sel = (sel - cols + colors.size) % colors.size
            "down" -> sel = (sel + cols) % colors.size
            "a" -> { onPick(colors[sel]); app.sounds.play("select"); app.pop(); return }
            "x" -> { onPick(current); app.pop(); return }   // keep the original
            "b" -> { app.sounds.play("back"); app.pop(); return }
            else -> return
        }
        app.sounds.play("move")
    }

    override fun draw(c: Canvas, w: Float, h: Float) {
        G.topBar(c, app, th, "Colour: $title", w)
        val rows = colors.size / cols
        val gap = 6f
        val cell = min((w - 32 - (cols - 1) * gap) / cols, (h - 160 - (rows - 1) * gap) / rows)
        val x0 = (w - (cols * cell + (cols - 1) * gap)) / 2
        colors.forEachIndexed { i, col ->
            val r = RectF(x0 + (i % cols) * (cell + gap), 56 + (i / cols) * (cell + gap), x0 + (i % cols) * (cell + gap) + cell, 56 + (i / cols) * (cell + gap) + cell)
            G.rect(c, r, col, 6f)
            if (i == sel) { G.outline(c, RectF(r).apply { inset(-3f, -3f) }, th.c("text"), 3f, 8f) }
            app.hit(r) { if (sel == i) handle("a") else sel = i }
        }
        val y = 56 + rows * (cell + gap) + 10
        G.text(c, "Before", 30f, y, 20f, th.c("text_dim"))
        G.rect(c, RectF(30f, y + 26, 110f, y + 66), current, 8f)
        G.text(c, "After", 140f, y, 20f, th.c("text_dim"))
        G.rect(c, RectF(140f, y + 26, 220f, y + 66), colors[sel], 8f)
        G.text(c, Theme.hex(colors[sel]), 240f, y + 36, 22f, th.c("text"))
        G.text(c, "Fine-tune afterwards with Left/Right in the Studio.", w - 20, y + 36, 18f, th.c("text_dim"), align = 'r', maxW = w - 420)
    }

    override fun hints() = listOf("X" to "Keep", "B" to "Cancel", "A" to "Use colour")
}
