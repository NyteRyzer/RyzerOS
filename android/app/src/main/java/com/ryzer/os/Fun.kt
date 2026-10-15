package com.ryzer.os

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.os.BatteryManager
import android.os.SystemClock
import android.provider.Settings
import java.io.File
import java.util.Calendar
import java.util.zip.CRC32
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

// ===== Launch animation ===============================================================

/** A short splash before a game starts: the box art zooms in over the system colour. */
class LaunchSplash(app: Ryzer, private val game: Game) : Screen(app) {
    private var t = 0f
    private var done = false
    override fun hints() = emptyList<Pair<String, String>>()
    override fun handle(a: String) { if (a == "b") { done = true; app.pop() } else finish() }

    private fun finish() {
        if (done) return
        done = true
        app.pop()
        app.launchNow(game)
    }

    override fun update(dt: Float) { t += dt; if (t > 0.9f) finish() }

    override fun draw(c: Canvas, w: Float, h: Float) {
        val p = min(1f, t / 0.7f)
        G.rect(c, RectF(0f, 0f, w, h), withAlpha(game.system.color, 0.35f + 0.4f * p))
        val size = 120 + 160 * p
        val box = RectF(w / 2 - size / 2, h / 2 - size / 2 - 20, w / 2 + size / 2, h / 2 + size / 2 - 20)
        val art = G.image(game.image())
        if (art != null) G.drawImageFit(c, art, box) else G.text(c, game.system.short, w / 2, h / 2 - 60, 60 + 50 * p, th.c("highlight_text"), align = 'c', isBold = true)
        G.text(c, app.title(game), w / 2, box.bottom + 14, 30f, th.c("text"), align = 'c', isBold = true, maxW = w - 40)
        G.text(c, "Starting ${Launcher.label(app, app.state.gameEmu[game.key] ?: Launcher.chosen(app, game.system))}...", w / 2, box.bottom + 50, 20f, th.c("text_dim"), align = 'c')
    }
}

// ===== Game roulette ==================================================================

/** A slot-machine reel that spins through your games and lands on one. */
class Roulette(app: Ryzer, private val pool: List<Game>) : Screen(app) {
    override val title = "Game Roulette"
    private val reel = pool.shuffled().take(40).ifEmpty { pool }
    private var pos = 0f
    private var speed = 0f
    private var stopped = false

    init { spin() }

    private fun spin() { speed = 30f + Random.nextFloat() * 15f; stopped = false; app.sounds.play("select") }

    override fun update(dt: Float) {
        if (stopped || reel.isEmpty()) return
        val before = pos.toInt()
        pos += speed * dt
        speed *= 0.985f
        if (pos.toInt() != before) app.sounds.play("move")
        if (speed < 0.6f) { pos = pos.toInt().toFloat(); stopped = true; app.sounds.play("fav") }
    }

    private fun picked() = reel[(pos.toInt() % reel.size + reel.size) % reel.size]

    override fun handle(a: String) {
        when (a) {
            "a" -> if (stopped && reel.isNotEmpty()) { val g = picked(); app.pop(); app.launch(g) }
            "x" -> spin()
            "b" -> app.pop()
        }
    }

    override fun draw(c: Canvas, w: Float, h: Float) {
        G.topBar(c, app, th, title, w)
        if (reel.isEmpty()) { G.text(c, "No games to spin yet.", w / 2, h / 2, 26f, th.c("text_dim"), align = 'c'); return }
        val cy = h / 2 - 10
        val rowH = 56f
        val frac = pos - pos.toInt()
        for (k in -3..3) {
            val idx = ((pos.toInt() + k) % reel.size + reel.size) % reel.size
            val y = cy + (k - frac) * rowH
            val dist = Math.abs(k - frac)
            val g = reel[idx]
            val alpha = (1f - dist / 3.5f).coerceIn(0.1f, 1f)
            G.text(c, app.title(g), w / 2, y - 16, if (dist < 0.5f) 34f else 26f, withAlpha(if (dist < 0.5f) th.c("text") else th.c("text_dim"), alpha),
                align = 'c', isBold = dist < 0.5f, maxW = w - 120)
        }
        val win = RectF(40f, cy - rowH / 2 - 4, w - 40, cy + rowH / 2 - 4)
        G.outline(c, win, th.c("accent"), 3f, 14f)
        if (stopped) {
            val g = picked()
            G.text(c, "${g.system.name}  -  press A to play", w / 2, win.bottom + 16, 22f, th.c("accent"), align = 'c', isBold = true)
        }
    }

    override fun hints() = listOf("X" to "Spin again", "B" to "Back", "A" to "Play")
}

// ===== Mini-games =====================================================================

class SnakeGame(app: Ryzer) : Screen(app) {
    override val title = "Ryzer Snake"
    private val cols = 24; private val rows = 15
    private var snake = ArrayDeque(listOf(12 to 7, 11 to 7, 10 to 7))
    private var dir = 1 to 0
    private var next = dir
    private var food = 5 to 5
    private var acc = 0f
    private var over = false
    private var score = 0

    init { placeFood() }

    private fun placeFood() {
        do { food = Random.nextInt(cols) to Random.nextInt(rows) } while (food in snake)
    }

    override fun handle(a: String) {
        when (a) {
            "up" -> if (dir.second == 0) next = 0 to -1
            "down" -> if (dir.second == 0) next = 0 to 1
            "left" -> if (dir.first == 0) next = -1 to 0
            "right" -> if (dir.first == 0) next = 1 to 0
            "a" -> if (over) { snake = ArrayDeque(listOf(12 to 7, 11 to 7, 10 to 7)); dir = 1 to 0; next = dir; score = 0; over = false; placeFood() }
            "b" -> { saveBest(); app.pop() }
        }
    }

    private fun saveBest() { if (score > app.state.bestScores["snake"] ?: 0) { app.state.bestScores["snake"] = score; app.state.save() } }

    override fun update(dt: Float) {
        if (over) return
        acc += dt
        val step = max(0.06f, 0.16f - score * 0.003f)
        if (acc < step) return
        acc = 0f
        dir = next
        val head = snake.first()
        val nh = ((head.first + dir.first + cols) % cols) to ((head.second + dir.second + rows) % rows)
        if (nh in snake) { over = true; app.sounds.play("error"); saveBest(); return }
        snake.addFirst(nh)
        if (nh == food) { score++; app.sounds.play("fav"); placeFood() } else snake.removeLast()
    }

    override fun draw(c: Canvas, w: Float, h: Float) {
        G.topBar(c, app, th, "Snake  |  Score $score  |  Best ${app.state.bestScores["snake"] ?: 0}", w)
        val cell = min((w - 40) / cols, (h - 110) / rows)
        val ox = (w - cell * cols) / 2; val oy = 56f
        G.rect(c, RectF(ox, oy, ox + cell * cols, oy + cell * rows), withAlpha(th.c("panel"), 0.9f), 8f)
        G.circle(c, ox + food.first * cell + cell / 2, oy + food.second * cell + cell / 2, cell * 0.4f, th.c("bad"))
        snake.forEachIndexed { i, (x, y) ->
            G.rect(c, RectF(ox + x * cell + 1, oy + y * cell + 1, ox + (x + 1) * cell - 1, oy + (y + 1) * cell - 1),
                if (i == 0) th.c("accent") else mix(th.c("accent"), th.c("accent2"), i / snake.size.toFloat()), 4f)
        }
        if (over) G.text(c, "Game over - press A to play again", w / 2, oy + cell * rows / 2 - 14, 28f, th.c("text"), align = 'c', isBold = true)
    }

    override fun hints() = listOf("B" to "Quit", "A" to if (over) "Again" else "")
}

class Game2048(app: Ryzer) : Screen(app) {
    override val title = "2048"
    private var board = Array(4) { IntArray(4) }
    private var score = 0
    private var over = false

    init { add(); add() }

    private fun add() {
        val empty = (0 until 16).filter { board[it / 4][it % 4] == 0 }
        if (empty.isEmpty()) return
        val i = empty.random()
        board[i / 4][i % 4] = if (Random.nextFloat() < 0.9f) 2 else 4
    }

    /** Slides one row to the left, merging pairs. */
    private fun slide(row: IntArray): IntArray {
        val v = row.filter { it != 0 }.toMutableList()
        var i = 0
        while (i < v.size - 1) { if (v[i] == v[i + 1]) { v[i] *= 2; score += v[i]; v.removeAt(i + 1) }; i++ }
        return IntArray(4) { v.getOrElse(it) { 0 } }
    }

    private fun move(dir: String) {
        val before = board.map { it.copyOf() }
        for (k in 0 until 4) {
            val line = IntArray(4) { i -> when (dir) { "left" -> board[k][i]; "right" -> board[k][3 - i]; "up" -> board[i][k]; else -> board[3 - i][k] } }
            val out = slide(line)
            for (i in 0 until 4) when (dir) { "left" -> board[k][i] = out[i]; "right" -> board[k][3 - i] = out[i]; "up" -> board[i][k] = out[i]; else -> board[3 - i][k] = out[i] }
        }
        if (board.indices.any { !board[it].contentEquals(before[it]) }) { add(); app.sounds.play("move") }
        over = (0 until 4).none { r -> (0 until 4).any { c2 -> board[r][c2] == 0 || (c2 < 3 && board[r][c2] == board[r][c2 + 1]) || (r < 3 && board[r][c2] == board[r + 1][c2]) } }
        if (score > (app.state.bestScores["2048"] ?: 0)) { app.state.bestScores["2048"] = score; app.state.save() }
    }

    override fun handle(a: String) {
        when (a) {
            "up", "down", "left", "right" -> if (!over) move(a)
            "a" -> if (over) { board = Array(4) { IntArray(4) }; score = 0; over = false; add(); add() }
            "b" -> app.pop()
        }
    }

    override fun draw(c: Canvas, w: Float, h: Float) {
        G.topBar(c, app, th, "2048  |  Score $score  |  Best ${app.state.bestScores["2048"] ?: 0}", w)
        val size = min(w - 40, h - 110)
        val ox = (w - size) / 2; val oy = 54f
        G.rect(c, RectF(ox, oy, ox + size, oy + size), th.c("panel"), 12f)
        val cell = (size - 50) / 4
        for (r in 0 until 4) for (c2 in 0 until 4) {
            val v = board[r][c2]
            val rr = RectF(ox + 10 + c2 * (cell + 10), oy + 10 + r * (cell + 10), ox + 10 + c2 * (cell + 10) + cell, oy + 10 + r * (cell + 10) + cell)
            val lvl = if (v == 0) 0f else (Math.log(v.toDouble()) / Math.log(2048.0)).toFloat()
            G.rect(c, rr, if (v == 0) withAlpha(th.c("bg_bottom"), 0.6f) else mix(th.c("accent2"), th.c("accent"), lvl), 8f)
            if (v > 0) G.text(c, "$v", rr.centerX(), rr.centerY() - cell * 0.18f, cell * (if (v >= 1024) 0.32f else 0.42f), th.c("highlight_text"), align = 'c', isBold = true)
        }
        if (over) G.text(c, "No moves left - A to restart", w / 2, oy + size / 2 - 14, 28f, th.c("text"), align = 'c', isBold = true)
    }

    override fun hints() = listOf("B" to "Quit", "A" to if (over) "Restart" else "")
}

// ===== Play calendar ==================================================================

/** A month view of the days you played, shaded by how long. */
class PlayCalendar(app: Ryzer) : Screen(app) {
    override val title = "Play calendar"
    private val cal = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
    private var selDay = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)

    override fun handle(a: String) {
        val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        when (a) {
            "l" -> { cal.add(Calendar.MONTH, -1); selDay = 1 }
            "r" -> { cal.add(Calendar.MONTH, 1); selDay = 1 }
            "left" -> selDay = max(1, selDay - 1)
            "right" -> selDay = min(days, selDay + 1)
            "up" -> selDay = max(1, selDay - 7)
            "down" -> selDay = min(days, selDay + 7)
            "a", "b" -> { app.pop(); return }
            else -> return
        }
        app.sounds.play("move")
    }

    private fun key(day: Int) = "%04d-%02d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, day)

    override fun draw(c: Canvas, w: Float, h: Float) {
        val month = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()).format(cal.time)
        G.topBar(c, app, th, "Play calendar  <  $month  >", w)
        val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val startDow = (cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY)
        val cellW = (w - 40) / 7; val cellH = (h - 150) / 6
        listOf("S", "M", "T", "W", "T", "F", "S").forEachIndexed { i, d -> G.text(c, d, 20 + i * cellW + cellW / 2, 48f, 18f, th.c("text_dim"), align = 'c') }
        val peak = max(60L, (1..days).maxOf { app.state.daily[key(it)] ?: 0L })
        for (d in 1..days) {
            val slot = startDow + d - 1
            val r = RectF(20 + (slot % 7) * cellW + 3, 72 + (slot / 7) * cellH + 3, 20 + (slot % 7 + 1) * cellW - 3, 72 + (slot / 7 + 1) * cellH - 3)
            val secs = app.state.daily[key(d)] ?: 0L
            G.rect(c, r, if (secs > 0) mix(th.c("panel"), th.c("accent"), 0.25f + 0.75f * secs / peak) else withAlpha(th.c("panel"), 0.8f), 6f)
            if (d == selDay) G.outline(c, r, th.c("text"), 2f, 6f)
            G.text(c, "$d", r.left + 6, r.top + 3, 17f, th.c("text"))
            app.hit(r) { selDay = d }
        }
        val secs = app.state.daily[key(selDay)] ?: 0L
        G.text(c, "${key(selDay)}:  ${if (secs > 0) "played ${formatDuration(secs)}" else "no play"}", w / 2, h - 70, 20f, th.c("accent"), align = 'c', isBold = true)
    }

    override fun hints() = listOf("L/R" to "Month", "B" to "Back")
}

// ===== Stopwatch & timer ==============================================================

class StopwatchTimer(app: Ryzer) : Screen(app) {
    override val title = "Stopwatch & timer"
    private var mode = 0                 // 0 stopwatch, 1 timer
    private var running = false
    private var base = 0L                // stopwatch: accumulated ms
    private var startedAt = 0L
    private var timerMin = 5
    private var timerEnd = 0L
    private var laps = mutableListOf<Long>()

    private fun elapsed() = base + if (running) SystemClock.uptimeMillis() - startedAt else 0L

    override fun handle(a: String) {
        when (a) {
            "l", "r" -> { mode = 1 - mode; running = false }
            "a" -> if (mode == 0) {
                if (running) { base = elapsed(); running = false } else { startedAt = SystemClock.uptimeMillis(); running = true }
            } else {
                if (running) running = false else { timerEnd = SystemClock.uptimeMillis() + timerMin * 60_000L; running = true }
            }
            "x" -> if (mode == 0) { if (running) laps.add(0, elapsed()) else { base = 0; laps.clear() } }
            "up" -> if (mode == 1 && !running) timerMin = min(180, timerMin + 1)
            "down" -> if (mode == 1 && !running) timerMin = max(1, timerMin - 1)
            "right" -> if (mode == 1 && !running) timerMin = min(180, timerMin + 5)
            "left" -> if (mode == 1 && !running) timerMin = max(1, timerMin - 5)
            "b" -> { app.pop(); return }
            else -> return
        }
        app.sounds.play("move")
    }

    override fun update(dt: Float) {
        if (mode == 1 && running && SystemClock.uptimeMillis() >= timerEnd) {
            running = false
            app.sounds.play("boot"); app.ctx.buzz(); app.ctx.buzz()
            Notifs.ryzer("Timer done", "Your $timerMin minute timer finished.")
        }
    }

    private fun fmt(ms: Long) = "%02d:%02d.%d".format(ms / 60000, ms / 1000 % 60, ms / 100 % 10)

    override fun draw(c: Canvas, w: Float, h: Float) {
        G.topBar(c, app, th, if (mode == 0) "Stopwatch  (L/R: timer)" else "Timer  (L/R: stopwatch)", w)
        if (mode == 0) {
            G.text(c, fmt(elapsed()), w / 2, 90f, 90f, th.c("text"), align = 'c', isBold = true)
            laps.take(5).forEachIndexed { i, l -> G.text(c, "Lap ${laps.size - i}:  ${fmt(l)}", w / 2, 200 + i * 28f, 22f, th.c("text_dim"), align = 'c') }
        } else {
            val left = if (running) max(0L, timerEnd - SystemClock.uptimeMillis()) else timerMin * 60_000L
            G.text(c, "%02d:%02d".format(left / 60000, left / 1000 % 60), w / 2, 90f, 100f, th.c("text"), align = 'c', isBold = true)
            if (!running) G.text(c, "Up/Down: 1 min   Left/Right: 5 min", w / 2, 230f, 20f, th.c("text_dim"), align = 'c')
        }
    }

    override fun hints() = if (mode == 0) listOf("X" to if (running) "Lap" else "Reset", "A" to if (running) "Stop" else "Start")
        else listOf("B" to "Back", "A" to if (running) "Cancel" else "Start")
}

// ===== Controller tester ==============================================================

class ControllerTest(app: Ryzer) : Screen(app) {
    override val title = "Controller test"
    val pressed = sortedSetOf<String>()
    val seen = linkedSetOf<String>()
    var axes = FloatArray(6)
    private var exitHold = 0f

    fun key(code: Int, down: Boolean) {
        val name = android.view.KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
        if (code == android.view.KeyEvent.KEYCODE_BACK && !down) { app.pop(); return }
        if (down) { pressed += name; seen += name } else pressed -= name
    }

    override fun update(dt: Float) {
        exitHold = if ("BUTTON_START" in pressed && "BUTTON_SELECT" in pressed) exitHold + dt else 0f
        if (exitHold > 0.6f) app.pop()
    }

    override fun draw(c: Canvas, w: Float, h: Float) {
        G.topBar(c, app, th, "Controller test (hold START + SELECT to exit)", w)
        G.text(c, "Pressed now:", 20f, 54f, 20f, th.c("text_dim"))
        G.text(c, pressed.joinToString("  ").ifEmpty { "-" }, 20f, 80f, 24f, th.c("accent"), isBold = true, maxW = w - 40)
        val names = listOf("Left X", "Left Y", "Right X", "Right Y", "L2", "R2")
        for (i in 0 until 6) {
            val y = 130 + i * 34f
            G.text(c, names[i], 20f, y, 20f, th.c("text"))
            val bar = RectF(120f, y + 4, w - 120, y + 20)
            G.rect(c, bar, th.c("panel"), 6f)
            val v = axes[i].coerceIn(-1f, 1f)
            val mid = if (i >= 4) bar.left else bar.centerX()
            val end = if (i >= 4) bar.left + bar.width() * v else bar.centerX() + bar.width() / 2 * v
            G.rect(c, RectF(min(mid, end), bar.top, max(mid, end), bar.bottom), th.c("accent"), 6f)
            G.text(c, "%.2f".format(v), w - 20, y, 18f, th.c("text_dim"), align = 'r')
        }
        G.text(c, "Seen: " + seen.joinToString(", "), 20f, h - 80, 17f, th.c("text_dim"), maxW = w - 40)
    }

    override fun handle(a: String) {}
    override fun hints() = emptyList<Pair<String, String>>()
}

// ===== Battery info ===================================================================

class BatteryInfo(app: Ryzer) : Screen(app) {
    override val title = "Battery"
    private var lines = listOf<Pair<String, String>>()
    private var timer = 99f

    override fun update(dt: Float) {
        timer += dt
        if (timer < 2f) return
        timer = 0f
        val i = app.ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
        val bm = app.ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / max(1, i.getIntExtra(BatteryManager.EXTRA_SCALE, 100))
        val health = when (i.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"; BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Worn out"; BatteryManager.BATTERY_HEALTH_COLD -> "Too cold"; else -> "Unknown"
        }
        val plug = when (i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
            BatteryManager.BATTERY_PLUGGED_AC -> "Wall charger"; BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"; else -> "Not plugged in"
        }
        val currentMa = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW).let { if (it == Int.MIN_VALUE) null else it / 1000 }
        lines = listOf(
            "Charge" to "$level%",
            "Power" to plug,
            "Temperature" to "%.1f °C".format(i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f),
            "Voltage" to "%.2f V".format(i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) / 1000f),
            "Current" to (currentMa?.let { "$it mA" } ?: "Not reported"),
            "Health" to health,
            "Type" to (i.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Unknown"),
        )
    }

    override fun handle(a: String) { if (a == "a" || a == "b") app.pop() }

    override fun draw(c: Canvas, w: Float, h: Float) {
        G.topBar(c, app, th, title, w)
        lines.forEachIndexed { i, (k, v) ->
            val y = 56 + i * 46f
            G.panel(c, RectF(16f, y, w - 16, y + 40), th)
            G.text(c, k, 30f, y + 10, 22f, th.c("text_dim"))
            G.text(c, v, w - 30, y + 10, 22f, th.c("text"), align = 'r')
        }
    }

    override fun hints() = listOf("B" to "Back")
}

// ===== Share card, screenshots, checksum =============================================

object Sharing {
    fun shareFile(app: Ryzer, f: File, mime: String, title: String) {
        val uri = Launcher.providerUri(f)
        val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { app.ctx.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { app.notify("Saved to ${f.path}") }
    }

    /** Renders a "my gaming stats" card and opens the share sheet. */
    fun statsCard(app: Ryzer) {
        val th = app.activeTheme
        val bmp = Bitmap.createBitmap(1200, 675, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(1200f / 640f, 1200f / 640f)
        val w = 640f; val h = 360f
        G.background(c, th, w, h, 0f)
        G.emblem(c, th, 50f, 50f, 26f)
        G.text(c, if (app.cfg.userName.isNotBlank()) "${app.cfg.userName}'s Ryzer stats" else "My Ryzer stats", 90f, 26f, 34f, th.c("text"), isBold = true)
        val st = app.state
        val cards = listOf("Play time" to formatDuration(st.total()), "Games" to "${app.allGames().size}", "Streak" to "${st.streak()} days",
            "Milestones" to "${st.milestones.size}/${Milestones.ALL.size}")
        cards.forEachIndexed { i, (k, v) ->
            val r = RectF(20 + i * 152f, 100f, 20 + i * 152f + 140, 170f)
            G.rect(c, r, th.c("panel"), 12f)
            G.text(c, v, r.centerX(), r.top + 10, 30f, th.c("accent"), align = 'c', isBold = true, maxW = 132f)
            G.text(c, k, r.centerX(), r.top + 44, 18f, th.c("text_dim"), align = 'c')
        }
        G.text(c, "Most played", 20f, 186f, 22f, th.c("text"), isBold = true)
        st.times.entries.sortedByDescending { it.value }.take(4).forEachIndexed { i, (k, secs) ->
            val name = Library.find(app.systems, k)?.let { app.title(it) } ?: File(k).nameWithoutExtension
            G.text(c, "${i + 1}. $name", 20f, 214 + i * 26f, 20f, th.c("text"), maxW = 480f)
            G.text(c, formatDuration(secs), w - 20, 214 + i * 26f, 20f, th.c("text_dim"), align = 'r')
        }
        G.text(c, "Ryzer OS ${BuildInfo.version}", w - 20, h - 26, 16f, th.c("text_dim"), align = 'r')
        val f = File(File(app.home, "screenshots").apply { mkdirs() }, "ryzer-stats-${today()}.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        shareFile(app, f, "image/png", "Share your stats")
    }

    /** Saves exactly what Ryzer's main screen shows. */
    fun screenshot(app: Ryzer) {
        val f = app.ctx.captureScreen(File(File(app.home, "screenshots").apply { mkdirs() },
            "ryzer-${java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())}.png"))
        if (f != null) { app.sounds.play("select"); app.notify("Screenshot saved: ${f.name}") } else app.notify("Couldn't take a screenshot")
    }

    fun checksum(app: Ryzer, g: Game) {
        if (g.file.length() > 1_500_000_000L) { app.notify("Too big to check quickly (over 1.5 GB)"); return }
        app.notify("Checking ${g.file.name}...", 10f)
        var crc = 0L
        app.runInBackground({
            val c = CRC32()
            g.file.inputStream().buffered(1 shl 16).use { input ->
                val buf = ByteArray(1 shl 16)
                while (true) { val n = input.read(buf); if (n < 0) break; c.update(buf, 0, n) }
            }
            crc = c.value
        }) { e ->
            if (e != null) app.notify("Couldn't read the file")
            else app.push(Message(app, "ROM check", "${g.file.name}\n\nCRC32: ${"%08X".format(crc)}\nSize: ${g.file.length()} bytes\n\n" +
                "Compare the CRC32 with a known-good list (e.g. No-Intro / Redump) to make sure the file isn't damaged."))
        }
    }
}

// ===== Tools menu, help, what's new ===================================================

fun toolsMenu(app: Ryzer) = Menu(app, "Tools", listOf(
    Item("Game Roulette", "Spin a reel of your games and play whatever it lands on.", select = { app.push(Roulette(app, app.allGames())) }),
    Item("Snake", "A quick game of Snake. Best: ${app.state.bestScores["snake"] ?: 0}", select = { app.push(SnakeGame(app)) }),
    Item("2048", "Slide and merge tiles. Best: ${app.state.bestScores["2048"] ?: 0}", select = { app.push(Game2048(app)) }),
    Item("Play calendar", "See the days you played, month by month.", select = { app.push(PlayCalendar(app)) }),
    Item("Share my stats", "Makes a stats card picture and opens the share menu.", select = { runCatching { Sharing.statsCard(app) }.onFailure { app.notify("Couldn't make the card") } }),
    Item("Take a screenshot", "Saves Ryzer's screen to RyzerOS/screenshots.", select = { app.pop(); app.ctx.postScreenshot() }),
    Item("Stopwatch & timer", select = { app.push(StopwatchTimer(app)) }),
    Item("Controller test", "Shows every button and stick as you press them.", select = { app.push(ControllerTest(app)) }),
    Item("Battery", "Charge, temperature, voltage and health.", select = { app.push(BatteryInfo(app)) }),
    Item("System brightness", "Changes the whole device's brightness (asks for permission once).", value = { "${(systemBrightness(app) * 100 / 255)}%" },
        change = { d -> setSystemBrightness(app, d) }),
    Item("Help & tips", select = { app.push(Message(app, "Help & tips", HELP_TEXT)) }),
    Item("What's new", select = { app.push(Message(app, "What's new in Ryzer OS ${BuildInfo.version}", WHATS_NEW)) }),
))

fun systemBrightness(app: Ryzer): Int = runCatching { Settings.System.getInt(app.ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS) }.getOrDefault(128)

fun setSystemBrightness(app: Ryzer, d: Int) {
    if (!Settings.System.canWrite(app.ctx)) {
        app.notify("Allow 'Modify system settings' for Ryzer OS, then try again")
        runCatching { app.ctx.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, android.net.Uri.parse("package:${app.ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        return
    }
    runCatching {
        Settings.System.putInt(app.ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(app.ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (systemBrightness(app) + d * 25).coerceIn(5, 255))
    }
}

const val WHATS_NEW = "2.1 adds 30 features:\n" +
    "- Mini-games: Snake and 2048\n- Game Roulette\n- Launch animation\n- Play calendar\n- Share-able stats card\n- Screenshots\n" +
    "- Smart collections (Playing, Backlog, Top rated, Unplayed)\n- For You recommendations\n- NEW badges on recently added games\n" +
    "- Home tile editor\n- Icon shapes for the 3DS layout\n- Neon mode\n- Screen transitions\n- Binary and word clocks\n" +
    "- 4 new live wallpapers (fireflies, leaves, hearts, matrix)\n- Seasonal effects\n- Photo-frame screensaver\n- Per-system backgrounds\n" +
    "- Custom boot message\n- Unlockable bonus themes\n- Battery info\n- Stopwatch & timer\n- Bedtime reminder\n- Controller test\n" +
    "- System brightness control\n- ROM checksum (CRC32)\n- 'Continue playing?' at startup\n- Search across games, apps and settings\n" +
    "- This What's New screen and Help & tips"

const val HELP_TEXT = "Buttons: A open, B back, X random / favorite, Y search, START options, SELECT themes.\n" +
    "L + R together: quick menu.  L3 / R3: swap the two screens.\n\n" +
    "Games go in ROMs/<system> folders (ROMs/gba, ROMs/3ds...). Pick emulators in Settings > Emulators.\n\n" +
    "Personalize everything in Theme Studio (Themes > X), or make a theme from a photo (Themes > START).\n\n" +
    "Notifications: Settings > Notifications > START > allow access.\n\n" +
    "Pictures you can add: RyzerOS/wallpapers (backgrounds), RyzerOS/tiles/<system>.png (tile art), " +
    "RyzerOS/backgrounds/<system>.jpg (shown in that system's list), RyzerOS/photos (photo-frame screensaver), RyzerOS/music (menu music).\n\n" +
    "Something broke? The error is in RyzerOS/crash.txt."

// ===== Universal search ===============================================================

/** Search results across games, installed apps and Ryzer's settings. */
fun universalSearch(app: Ryzer, q: String): Menu {
    val query = q.lowercase()
    val items = mutableListOf<Item>()
    app.allGames().filter { query in app.title(it).lowercase() }.take(60).forEach { g ->
        items += Item("${app.title(g)}  [${g.system.short}]", "Game - ${g.system.name}", select = { app.launch(g) })
    }
    val pm = app.ctx.packageManager
    pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .filter { query in it.loadLabel(pm).toString().lowercase() }.take(10).forEach { r ->
            items += Item("App: ${r.loadLabel(pm)}", select = {
                runCatching { app.ctx.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(r.activityInfo.packageName, r.activityInfo.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            })
        }
    val places = listOf(
        "Settings" to { app.push(settingsMenu(app)) }, "Theme Studio" to { app.push(ThemeStudio(app, app.theme)) },
        "Themes" to { app.push(ThemePicker(app)) }, "Notifications" to { app.push(NotificationsScreen(app)) },
        "Stats" to { app.push(StatsScreen(app)) }, "Tools" to { app.push(toolsMenu(app)) }, "Profile" to { app.push(profileMenu(app)) },
        "Snake" to { app.push(SnakeGame(app)) }, "2048" to { app.push(Game2048(app)) }, "Roulette" to { app.push(Roulette(app, app.allGames())) },
        "Calendar" to { app.push(PlayCalendar(app)) }, "Battery" to { app.push(BatteryInfo(app)) }, "Controller test" to { app.push(ControllerTest(app)) },
        "Emulators" to { app.push(settingsMenu(app)) }, "Quick menu" to { app.push(QuickMenu(app)) },
    )
    places.filter { query in it.first.lowercase() }.forEach { (name, go) -> items += Item("Open: $name", select = go) }
    if (items.isEmpty()) items += Item("Nothing found for \"$q\"")
    return Menu(app, "Search: \"$q\" (${items.size})", items)
}


// ===== Bonus themes ===================================================================

/** Five extra themes that unlock as you earn milestones. */
object BonusThemes {
    private fun theme(id: String, name: String, c: List<String>, bg: String, tile: String, live: String) = JSONObject().apply {
        put("id", id); put("name", name); put("author", "Ryzer bonus")
        val keys = listOf("bg_top", "bg_bottom", "panel", "panel_border", "accent", "accent2", "text", "text_dim", "highlight_text", "good", "warn", "bad")
        put("colors", JSONObject().apply { keys.forEachIndexed { i, k -> put(k, c[i]) } })
        put("style", JSONObject().apply { put("corner_radius", 14); put("tile_style", tile); put("background", bg); put("particles", true); put("live", live) })
    }

    /** (milestones needed, theme) */
    val ALL = listOf(
        3 to theme("bonus-bronze", "Bronze Trophy", listOf("#2A1A0E", "#120A04", "#3A2414", "#8C5A2B", "#CD7F32", "#E8A860", "#FFF1E0", "#C9A27E", "#1A0E04", "#7BC67B", "#F0B44C", "#E5533D"), "diagonal", "glow", "fireflies"),
        6 to theme("bonus-silver", "Silver Trophy", listOf("#2B3036", "#121518", "#3A4048", "#9AA4AE", "#C0C8D0", "#7FD1FF", "#F4F7FA", "#A8B2BC", "#101418", "#7BD67B", "#F2C14E", "#F2545B"), "stripes", "glow", "starfield"),
        9 to theme("bonus-gold", "Gold Trophy", listOf("#2E2408", "#120D02", "#3E3110", "#B8932B", "#FFD34D", "#FFF08A", "#FFFBEA", "#D9C27A", "#201800", "#8BE38B", "#FFB347", "#FF6B5B"), "waves", "glow", "aurora"),
        12 to theme("bonus-platinum", "Platinum", listOf("#E9EEF3", "#C7D2DD", "#FFFFFF", "#9FB2C4", "#5B7BA0", "#A06BD8", "#14202C", "#5E7184", "#FFFFFF", "#2E9E5B", "#D9831F", "#D03A3A"), "grid", "flat", "bubbles"),
        Milestones.ALL.size to theme("bonus-legend", "Ryzer Legend", listOf("#1A0010", "#05000A", "#2A0620", "#FF2E88", "#FF2E88", "#00F0FF", "#FFFFFF", "#D49ACB", "#0A0010", "#3DFFA2", "#FFD23F", "#FF3B3B"), "scanlines", "glow", "matrix"),
    )

    fun unlocked(app: Ryzer): List<Theme> = ALL.filter { app.state.milestones.size >= it.first }.mapNotNull { Theme.parse(it.second, true) }

    fun next(app: Ryzer): String? = ALL.firstOrNull { app.state.milestones.size < it.first }?.let { (need, j) ->
        "${j.getString("name")}: ${need - app.state.milestones.size} more milestone(s)" }

    /** Adds newly earned themes and announces them. */
    fun refresh(app: Ryzer) {
        for (t in unlocked(app)) if (t.id !in app.themes) {
            app.themes[t.id] = t
            app.notify("New theme unlocked: ${t.name}!", 3.5f)
            if (app.cfg.ryzerNotices) Notifs.ryzer("Theme unlocked: ${t.name}", "Find it in Themes. Earned with your milestones.")
        }
    }
}

// ===== Resume prompt ==================================================================

class ResumePrompt(app: Ryzer, private val game: Game) : Screen(app) {
    override val title = "Welcome back"
    private var t = 0f
    override fun update(dt: Float) { t += dt; if (t > 12f) app.pop() }   // goes away on its own
    override fun handle(a: String) {
        when (a) {
            "a" -> { app.pop(); app.launch(game) }
            "b" -> app.pop()
            "x" -> { app.cfg.resumePrompt = false; app.cfg.save(); app.pop(); app.notify("Won't ask again (Settings > Look & feel)") }
        }
    }

    override fun draw(c: Canvas, w: Float, h: Float) {
        G.rect(c, RectF(0f, 0f, w, h), withAlpha(0xFF000000.toInt(), 0.5f))
        val r = RectF(w * 0.1f, h * 0.2f, w * 0.9f, h * 0.75f)
        G.panel(c, r, th, true)
        val art = G.image(game.image())
        val box = RectF(r.left + 16, r.top + 16, r.left + 16 + r.height() - 32, r.bottom - 16)
        if (art != null) G.drawImageFit(c, art, box) else { G.rect(c, box, mix(th.c("panel"), game.system.color, 0.7f), 10f)
            G.text(c, game.system.short, box.centerX(), box.centerY() - 22, 40f, th.c("highlight_text"), align = 'c', isBold = true) }
        val x = box.right + 18; val mw = r.right - x - 16
        G.text(c, greeting(app), x, r.top + 18, 22f, th.c("accent"), maxW = mw)
        G.text(c, "Continue playing?", x, r.top + 50, 26f, th.c("text_dim"), maxW = mw)
        G.text(c, app.title(game), x, r.top + 84, 32f, th.c("text"), isBold = true, maxW = mw)
        G.text(c, "${game.system.name}  -  ${formatDuration(app.state.playtime(game.key))} played", x, r.top + 124, 20f, th.c("text_dim"), maxW = mw)
        app.state.notes[game.key]?.let { G.text(c, "Note: $it", x, r.top + 152, 18f, th.c("text_dim"), maxW = mw) }
        app.hit(r) { handle("a") }
    }

    override fun hints() = listOf("X" to "Don't ask again", "B" to "Not now", "A" to "Play")
}
