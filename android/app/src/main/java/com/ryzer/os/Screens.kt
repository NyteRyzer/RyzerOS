package com.ryzer.os

import android.graphics.Canvas
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

/** A screen on the stack. draw() paints the main display, drawTop() the second one. */
abstract class Screen(val app: Ryzer) {
    open val title = ""
    val th get() = app.activeTheme
    open fun handle(a: String) {}
    open fun update(dt: Float) {}
    abstract fun draw(c: Canvas, w: Float, h: Float)
    open fun drawTop(c: Canvas, w: Float, h: Float) = app.drawDefaultTop(c, w, h)
    open fun hints(): List<Pair<String, String>> = listOf("B" to "Back", "A" to "Select")
    open fun onResume() {}
}

class Item(
    val label: String,
    val help: String = "",
    val value: (() -> String)? = null,
    val change: ((Int) -> Unit)? = null,
    val swatch: (() -> Int)? = null,
    val select: (() -> Unit)? = null,
) {
    var colorKey: String? = null
}

open class Menu(app: Ryzer, override val title: String, var items: List<Item>, private val subtitle: String = "") : Screen(app) {
    var sel = 0
    private var offset = 0
    private val rowH = 40f

    fun current() = items.getOrNull(sel)

    override fun handle(a: String) {
        val s = app.sounds
        when (a) {
            "up", "down" -> if (items.isNotEmpty()) { sel = (sel + (if (a == "down") 1 else -1) + items.size) % items.size; s.play("move") }
            "l", "r" -> if (items.isNotEmpty()) { sel = (sel + if (a == "r") 8 else -8).coerceIn(0, items.size - 1); s.play("move") }
            "left", "right" -> current()?.change?.let { it(if (a == "right") 1 else -1); s.play("move") }
            "a" -> current()?.let { it ->
                if (it.select != null) { s.play("select"); it.select.invoke() }
                else it.change?.let { ch -> ch(1); s.play("move") }
            }
            "b" -> { s.play("back"); app.pop() }
        }
    }

    override fun draw(c: Canvas, w: Float, h: Float) {
        G.topBar(c, app, th, title, w)
        var top = 52f
        if (subtitle.isNotEmpty()) { G.text(c, subtitle, 20f, top, 20f, th.c("text_dim"), maxW = w - 40, theme = th); top += 26 }
        val helpH = 30f
        val visible = max(1, ((h - top - 40 - helpH) / rowH).toInt())
        offset = G.scrollOffset(sel, offset, visible, items.size)
        for (i in offset until min(items.size, offset + visible)) {
            val it = items[i]
            val y = top + (i - offset) * rowH
            val r = RectF(16f, y, w - 28, y + rowH - 6)
            val selected = i == sel
            G.panel(c, r, th, selected)
            app.hit(r) { if (sel == i) handle("a") else sel = i }
            val col = if (selected) th.c("text") else mix(th.c("text"), th.c("text_dim"), 0.4f)
            G.text(c, it.label, r.left + 14, r.top + 8, 24f, col, maxW = r.width() / 2, theme = th)
            var x = r.right - 14
            it.swatch?.let { sw ->
                val sr = RectF(x - 26, r.centerY() - 10, x, r.centerY() + 10)
                G.rect(c, sr, sw(), 4f); G.outline(c, sr, th.c("text"), 1f, 4f)
                x = sr.left - 10
            }
            it.value?.let { v ->
                var s = v()
                if (it.change != null && selected) s = "<  $s  >"
                G.text(c, s, x, r.top + 9, 22f, if (selected) th.c("accent") else th.c("text_dim"), align = 'r',
                    maxW = r.width() / 2 - 20, theme = th)
            }
        }
        G.scrollbar(c, th, RectF(w - 20, top, w - 15, top + visible * rowH - 6), offset, visible, items.size)
        current()?.help?.takeIf { it.isNotEmpty() }?.let { G.text(c, it, 20f, h - 36 - helpH + 4, 20f, th.c("text_dim"), maxW = w - 40, theme = th) }
    }

    override fun drawTop(c: Canvas, w: Float, h: Float) {
        G.topBar(c, app, th, title, w)
        current()?.let {
            G.text(c, it.label, w / 2, h / 2 - 40, 40f, th.c("text"), align = 'c', isBold = true, maxW = w - 40)
            G.wrap(it.help, 22f, w - 60).take(4).forEachIndexed { i, line ->
                G.text(c, line, w / 2, h / 2 + 10 + i * 26, 22f, th.c("text_dim"), align = 'c')
            }
        }
    }
}

class Message(app: Ryzer, override val title: String, private val body: String, private val onClose: (() -> Unit)? = null,
              private val okLabel: String = "OK") : Screen(app) {
    private var offset = 0
    override fun handle(a: String) {
        when (a) {
            "a", "b", "start" -> { app.sounds.play("back"); app.pop(); if (a != "b") onClose?.invoke() }
            "down" -> offset++
            "up" -> offset = max(0, offset - 1)
        }
    }

    override fun draw(c: Canvas, w: Float, h: Float) {
        G.topBar(c, app, th, title, w)
        val box = RectF(20f, 56f, w - 20, h - 46)
        G.panel(c, box, th)
        val lines = G.wrap(body, 22f, box.width() - 28)
        val rows = ((box.height() - 20) / 24).toInt()
        offset = min(offset, max(0, lines.size - rows))
        lines.drop(offset).take(rows).forEachIndexed { i, l -> G.text(c, l, box.left + 14, box.top + 12 + i * 24, 22f, th.c("text")) }
    }

    override fun hints() = listOf("A" to okLabel)
}

class Confirm(app: Ryzer, private val question: String, private val yes: String = "Yes", private val no: String = "Cancel",
              private val onYes: () -> Unit) : Screen(app) {
    private var choice = 1
    override fun handle(a: String) {
        when (a) {
            "left", "right" -> { choice = 1 - choice; app.sounds.play("move") }
            "a" -> { app.sounds.play("select"); app.pop(); if (choice == 0) onYes() }
            "b" -> { app.sounds.play("back"); app.pop() }
        }
    }

    override fun draw(c: Canvas, w: Float, h: Float) {
        G.topBar(c, app, th, "Confirm", w)
        val box = RectF(40f, h / 2 - 90, w - 40, h / 2 + 90)
        G.panel(c, box, th)
        G.wrap(question, 24f, box.width() - 40).take(3).forEachIndexed { i, l ->
            G.text(c, l, w / 2, box.top + 24 + i * 28, 24f, th.c("text"), align = 'c')
        }
        listOf(yes, no).forEachIndexed { i, label ->
            val cx = w / 2 + if (i == 0) -90 else 90
            val r = RectF(cx - 75, box.bottom - 58, cx + 75, box.bottom - 18)
            G.panel(c, r, th, choice == i)
            app.hit(r) { choice = i; handle("a") }
            G.text(c, label, r.centerX(), r.top + 10, 24f, th.c("text"), align = 'c')
        }
    }

    override fun hints() = listOf("B" to "Cancel", "A" to "Choose")
}

/** On-screen keyboard for search and naming themes. Touch or gamepad. */
class Keyboard(app: Ryzer, override val title: String, initial: String = "", private val maxLen: Int = 32,
               private val onDone: (String) -> Unit) : Screen(app) {
    private val rowsBase = listOf("1234567890", "QWERTYUIOP", "ASDFGHJKL-", "ZXCVBNM.'&")
    private var value = initial
    private var row = 1
    private var col = 0
    private var lower = true

    private fun rows(): List<List<String>> =
        rowsBase.mapIndexed { i, r -> (if (lower && i > 0) r.lowercase() else r).map { it.toString() } } + listOf(listOf("SPACE", "DEL", "DONE"))

    private fun type(s: String) { if (value.length < maxLen) value += s }

    private fun press(key: String) {
        when (key) {
            "SPACE" -> type(" ")
            "DEL" -> value = value.dropLast(1)
            "DONE" -> { done(); return }
            else -> type(key)
        }
        app.sounds.play("select")
    }

    private fun done() { app.sounds.play("select"); app.pop(); onDone(value.trim()) }

    override fun handle(a: String) {
        val rs = rows()
        when (a) {
            "up", "down" -> { row = (row + (if (a == "down") 1 else -1) + rs.size) % rs.size; col = min(col, rs[row].size - 1); app.sounds.play("move") }
            "left", "right" -> { col = (col + (if (a == "right") 1 else -1) + rs[row].size) % rs[row].size; app.sounds.play("move") }
            "a" -> press(rs[row][col])
            "x" -> { value = value.dropLast(1); app.sounds.play("back") }
            "y" -> type(" ")
            "l" -> lower = !lower
            "start" -> done()
            "b" -> { app.sounds.play("back"); app.pop() }
        }
    }

    override fun draw(c: Canvas, w: Float, h: Float) {
        G.topBar(c, app, th, title, w)
        val field = RectF(20f, 54f, w - 20, 98f)
        G.panel(c, field, th, true)
        G.text(c, "${value}_", field.left + 14, field.top + 11, 28f, th.c("text"), maxW = field.width() - 28)
        val rs = rows()
        val gap = 6f
        val keyW = (w - 40 - 9 * gap) / 10
        var y = 112f
        rs.forEachIndexed { r, keys ->
            val widths = if (r == rs.size - 1) List(3) { (w - 40 - 2 * gap) / 3 } else List(keys.size) { keyW }
            var x = 20f
            keys.forEachIndexed { k, key ->
                val rect = RectF(x, y, x + widths[k], y + 46)
                val sel = r == row && k == col
                G.panel(c, rect, th, sel)
                app.hit(rect) { row = r; col = k; press(key) }
                G.text(c, key, rect.centerX(), rect.top + 13, 24f, if (sel) th.c("text") else th.c("text_dim"), align = 'c')
                x += widths[k] + gap
            }
            y += 46 + gap
        }
    }

    override fun hints() = listOf("L" to "Shift", "X" to "Del", "START" to "Done", "A" to "Type")
}

class BootScreen(app: Ryzer, private val next: Screen) : Screen(app) {
    private var t = 0f
    private val duration = 2.4f
    init { app.sounds.play("boot") }

    override fun handle(a: String) = finish()
    private fun finish() { if (app.current === this) app.replace(next) }
    override fun update(dt: Float) { t += dt; if (t >= duration) finish() }
    override fun hints() = emptyList<Pair<String, String>>()

    override fun draw(c: Canvas, w: Float, h: Float) {
        val p = min(1f, t / (duration * 0.85f))
        val cx = w / 2; val cy = h / 2 - 20
        for (i in 0 until 3) {
            val q = p * 1.4f - i * 0.18f
            if (q in 0f..1f) G.ring(c, cx, cy, 40 + q * 200, mix(th.c("bg_bottom"), th.c("accent"), 1 - q), 3f)
        }
        G.emblem(c, th, cx, cy, 46 * (0.6f + 0.4f * min(1f, p * 2)))
        if (p > 0.35f) {
            val reveal = min(1f, (p - 0.35f) / 0.35f)
            c.save()
            val tw = G.measure("RYZER OS", 54f, true)
            c.clipRect(cx - tw / 2, 0f, cx - tw / 2 + tw * reveal, h)
            G.text(c, "RYZER OS", cx, cy + 62, 54f, th.c("text"), align = 'c', isBold = true)
            c.restore()
            G.line(c, cx - tw / 2, cy + 108, cx - tw / 2 + tw * reveal, cy + 108, th.c("accent"), 3f)
        }
        if (p > 0.7f) G.text(c, "v${BuildInfo.version}  |  ${BuildInfo.device}", cx, cy + 128, 22f, th.c("text_dim"), align = 'c', maxW = w - 40)
    }

    override fun drawTop(c: Canvas, w: Float, h: Float) {
        val p = min(1f, t / duration)
        G.text(c, "Starting up", w / 2, h / 2 - 30, 30f, th.c("text_dim"), align = 'c')
        val bar = RectF(w / 4, h / 2 + 10, w * 3 / 4, h / 2 + 18)
        G.rect(c, bar, th.c("panel"), 4f)
        G.rect(c, RectF(bar.left, bar.top, bar.left + bar.width() * p, bar.bottom), th.c("accent"), 4f)
    }
}

class PermissionScreen(app: Ryzer) : Screen(app) {
    override val title = "Welcome to Ryzer OS"
    override fun handle(a: String) {
        when (a) {
            "a" -> app.ctx.requestStorage()
            "b" -> app.pop()
        }
    }

    override fun onResume() { if (hasStorageAccess(app.ctx)) app.ctx.restart() }

    override fun draw(c: Canvas, w: Float, h: Float) {
        G.topBar(c, app, th, title, w)
        val box = RectF(20f, 60f, w - 20, h - 50)
        G.panel(c, box, th)
        G.emblem(c, th, box.left + 60, box.top + 70, 34f)
        val text = "Ryzer needs \"All files access\" to find the games on your storage and SD card, " +
            "and to keep your themes and settings in the RyzerOS folder.\n\n" +
            "Press A, then switch on \"Allow access to manage all files\" and come back."
        G.wrap(text, 22f, box.width() - 150).forEachIndexed { i, l -> G.text(c, l, box.left + 120, box.top + 24 + i * 26, 22f, th.c("text")) }
        val btn = RectF(w / 2 - 110, box.bottom - 64, w / 2 + 110, box.bottom - 20)
        G.panel(c, btn, th, true)
        app.hit(btn) { handle("a") }
        G.text(c, "Grant access", btn.centerX(), btn.top + 11, 26f, th.c("text"), align = 'c', isBold = true)
    }

    override fun hints() = listOf("B" to "Later", "A" to "Grant")
}

object BuildInfo {
    val version: String get() = runCatching {
        val ctx = currentContext ?: return@runCatching "?"
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
    }.getOrDefault("?")
    val device: String get() = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim()
    val chip: String get() = if (android.os.Build.VERSION.SDK_INT >= 31) "${android.os.Build.SOC_MANUFACTURER} ${android.os.Build.SOC_MODEL}" else android.os.Build.HARDWARE
    var currentContext: android.content.Context? = null
}
