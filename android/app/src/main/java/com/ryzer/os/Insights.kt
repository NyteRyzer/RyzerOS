package com.ryzer.os

import android.graphics.Canvas
import android.graphics.RectF
import java.io.File
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min

// ===== Ryzer Wrapped =================================================================

/** Your month or year in games: totals, top games, favourite system, best day, streaks. */
class WrappedScreen(app: Ryzer) : Screen(app) {
    override val title = "Ryzer Wrapped"
    private var yearly = false

    private class Summary(
        val label: String, val total: Long, val days: Int, val bestDay: Pair<String, Long>?, val longestStreak: Int,
        val top: List<Pair<Game, Long>>, val topSystem: Pair<GameSystem, Long>?, val longestSession: Pair<Game, Long>?,
        val sessions: Int, val newGames: Int,
    )

    private var summary = build()

    private fun build(): Summary {
        val st = app.state
        val cal = Calendar.getInstance()
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val label: String
        val prefix: String
        if (yearly) {
            label = "${cal.get(Calendar.YEAR)}"
            prefix = "${cal.get(Calendar.YEAR)}-"
        } else {
            label = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()).format(cal.time)
            prefix = "%04d-%02d-".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
        }
        val days = st.daily.filterKeys { it.startsWith(prefix) }.filterValues { it > 0 }
        // Longest run of consecutive days in the period
        var longest = 0; var run = 0; var prev: Long? = null
        for (d in days.keys.sorted()) {
            val t = runCatching { fmt.parse(d)!!.time }.getOrNull() ?: continue
            run = if (prev != null && (t - prev) in 80_000_000L..95_000_000L) run + 1 else 1
            longest = max(longest, run); prev = t
        }
        val inPeriod = st.sessions.filter { fmt.format(java.util.Date(it.second)).startsWith(prefix) }
        val perGame = HashMap<String, Long>()
        for ((k, _, secs) in inPeriod) perGame[k] = (perGame[k] ?: 0) + secs
        val top = perGame.entries.sortedByDescending { it.value }.mapNotNull { e -> Library.find(app.systems, e.key)?.let { it to e.value } }.take(5)
        val perSystem = HashMap<GameSystem, Long>()
        for ((g, secs) in perGame.mapNotNull { e -> Library.find(app.systems, e.key)?.let { it to e.value } }) perSystem[g.system] = (perSystem[g.system] ?: 0) + secs
        val longestSession = inPeriod.maxByOrNull { it.third }?.let { s -> Library.find(app.systems, s.first)?.let { it to s.third } }
        // "New" games: their oldest recorded session falls in this period
        val firstSession = HashMap<String, Long>()
        for ((k, t, _) in st.sessions) firstSession[k] = min(firstSession[k] ?: Long.MAX_VALUE, t)
        val newGames = firstSession.count { fmt.format(java.util.Date(it.value)).startsWith(prefix) }
        return Summary(label, days.values.sum(), days.size, days.maxByOrNull { it.value }?.toPair(), longest, top,
            perSystem.maxByOrNull { it.value }?.toPair(), longestSession, inPeriod.size, newGames)
    }

    override fun handle(a: String) {
        when (a) {
            "l", "r", "left", "right" -> { yearly = !yearly; summary = build(); app.sounds.play("move") }
            "x" -> { app.ctx.postScreenshot(); app.notify("Saving a picture of your Wrapped...") }
            "b", "a" -> app.pop()
        }
    }

    override fun draw(c: Canvas, w: Float, h: Float) {
        val s = summary
        G.topBar(c, app, th, "Ryzer Wrapped  <  ${s.label}  >", w)
        if (s.total == 0L && s.sessions == 0) {
            G.text(c, "Nothing played ${if (yearly) "this year" else "this month"} yet.", w / 2, h / 2 - 20, 26f, th.c("text_dim"), align = 'c')
            G.text(c, "Press L / R to switch between this month and this year.", w / 2, h / 2 + 14, 19f, th.c("text_dim"), align = 'c')
            return
        }
        // Big numbers
        val cards = listOf(formatDuration(s.total) to "played", "${s.days}" to "days you played", "${s.longestStreak}" to "day best streak",
            "${s.newGames}" to "new games tried")
        val cw = (w - 40 - 3 * 10) / 4
        cards.forEachIndexed { i, (v, k) ->
            val r = RectF(20 + i * (cw + 10), 50f, 20 + i * (cw + 10) + cw, 122f)
            G.panel(c, r, th, i == 0)
            G.text(c, v, r.centerX(), r.top + 8, 30f, th.c("accent"), align = 'c', isBold = true, maxW = cw - 8)
            G.text(c, k, r.centerX(), r.top + 46, 16f, th.c("text_dim"), align = 'c', maxW = cw - 8)
        }
        // Top games
        val list = RectF(20f, 134f, w * 0.58f, h - 50)
        G.panel(c, list, th)
        G.text(c, "Top games", list.left + 12, list.top + 8, 22f, th.c("accent"), isBold = true)
        if (s.top.isEmpty()) G.text(c, "Play a few sessions to see your top games.", list.left + 12, list.top + 40, 18f, th.c("text_dim"), maxW = list.width() - 24)
        s.top.forEachIndexed { i, (g, secs) ->
            val y = list.top + 40 + i * 34
            G.text(c, "${i + 1}", list.left + 14, y, 24f, th.c("accent"), isBold = true)
            G.text(c, app.title(g), list.left + 40, y + 2, 20f, th.c("text"), maxW = list.width() - 140)
            G.text(c, formatDuration(secs), list.right - 12, y + 2, 18f, th.c("text_dim"), align = 'r')
        }
        // Highlights
        val side = RectF(w * 0.58f + 10, 134f, w - 20, h - 50)
        G.panel(c, side, th)
        var y = side.top + 8
        fun fact(k: String, v: String?) {
            if (v == null) return
            G.text(c, k, side.left + 12, y, 16f, th.c("text_dim")); y += 18
            G.text(c, v, side.left + 12, y, 20f, th.c("text"), isBold = true, maxW = side.width() - 24); y += 30
        }
        fact("Favourite system", s.topSystem?.let { "${it.first.name} (${formatDuration(it.second)})" })
        fact("Biggest day", s.bestDay?.let { "${it.first.substring(5)}: ${formatDuration(it.second)}" })
        fact("Longest session", s.longestSession?.let { "${app.title(it.first)}, ${formatDuration(it.second)}" })
        fact("Sessions", "${s.sessions}")
    }

    override fun drawTop(c: Canvas, w: Float, h: Float) {
        G.topBar(c, app, th, "", w)
        val g = summary.top.firstOrNull()?.first
        val art = g?.let { G.image(it.image()) }
        G.text(c, if (yearly) "Your year in games" else "Your month in games", w / 2, 50f, 34f, th.c("text"), align = 'c', isBold = true)
        if (art != null) {
            val box = G.drawImageFit(c, art, RectF(w / 2 - 120, 100f, w / 2 + 120, h - 90))
            G.text(c, "#1: ${app.title(g)}", w / 2, box.bottom + 10, 24f, th.c("accent"), align = 'c', isBold = true, maxW = w - 40)
        } else G.text(c, formatDuration(summary.total), w / 2, h / 2 - 30, 70f, th.c("accent"), align = 'c', isBold = true)
    }

    override fun hints() = listOf("L/R" to if (yearly) "Month" else "Year", "X" to "Save picture", "B" to "Back")
}

// ===== Cover viewer =================================================================

class CoverViewer(app: Ryzer, private val game: Game) : Screen(app) {
    override val title = "Cover"
    override fun handle(a: String) { if (a == "a" || a == "b") app.pop() }

    override fun draw(c: Canvas, w: Float, h: Float) {
        G.rect(c, RectF(0f, 0f, w, h), 0xFF000000.toInt())
        val art = G.image(game.image())
        if (art == null) {
            G.text(c, "No picture for this game yet.", w / 2, h / 2 - 30, 26f, th.c("text"), align = 'c')
            G.text(c, "Game options > Find artwork online", w / 2, h / 2 + 6, 20f, th.c("text_dim"), align = 'c')
        } else G.drawImageFit(c, art, RectF(10f, 10f, w - 10, h - 46))
        app.hit(RectF(0f, 0f, w, h)) { app.pop() }
    }

    override fun drawTop(c: Canvas, w: Float, h: Float) {
        G.topBar(c, app, th, game.system.name, w)
        G.text(c, app.title(game), w / 2, 60f, 34f, th.c("text"), align = 'c', isBold = true, maxW = w - 40)
        val m = Library.meta[game.key]
        var y = 110f
        m?.let { listOf(it.year, it.developer, it.genre).filter { s -> s.isNotEmpty() }.joinToString("  ·  ") }?.takeIf { it.isNotEmpty() }?.let {
            G.text(c, it, w / 2, y, 22f, th.c("accent"), align = 'c', maxW = w - 40); y += 36 }
        m?.desc?.takeIf { it.isNotEmpty() }?.let { d ->
            G.wrap(d.replace('\n', ' '), 19f, w - 60).take(((h - y - 30) / 23).toInt()).forEach { l ->
                G.text(c, l, 30f, y, 19f, th.c("text")); y += 23 }
        }
    }

    override fun hints() = listOf("B" to "Close")
}

// ===== Library check-up ===============================================================

fun libraryCheckup(app: Ryzer): String {
    val s = Library.lastScan
    val all = app.systems.flatMap { it.games }
    val withGames = app.systems.filter { it.games.isNotEmpty() }
    val noEmu = withGames.filter { Launcher.chosen(app, it) == null }
    val noArt = all.count { it.image() == null }
    val dupes = all.groupBy { cleanName(it.file.nameWithoutExtension).lowercase() + "|" + it.system.id }.count { it.value.size > 1 }
    val sb = StringBuilder()
    sb.append("${all.size} games on ${withGames.size} systems, from ${app.romRoots().size} ROM folder(s).\n\n")
    if (noEmu.isEmpty()) sb.append("✓ Every system with games has an emulator.\n")
    else sb.append("✗ No emulator installed for: ${noEmu.joinToString(", ") { it.short }}. Pick one in Settings > Emulators.\n")
    if (noArt == 0) sb.append("✓ Every game has a picture.\n")
    else sb.append("• $noArt game(s) have no picture. Settings > Library > Artwork can download them.\n")
    if (dupes > 0) sb.append("• $dupes game(s) appear more than once (other regions or copies). See Library > Duplicate finder.\n")
    if (s.hiddenTracks > 0) sb.append("✓ Tidied away ${s.hiddenTracks} disc track(s) that belong to .cue / .gdi / .m3u files.\n")
    if (s.hiddenBios > 0) sb.append("✓ Hid ${s.hiddenBios} BIOS / firmware file(s).\n")
    if (s.emptyFolders.isNotEmpty()) sb.append("• Folders with no games: ${s.emptyFolders.take(6).joinToString(", ") { it.name }}" +
        (if (s.emptyFolders.size > 6) " and ${s.emptyFolders.size - 6} more" else "") + ".\n")
    if (s.unknown.isNotEmpty()) {
        val byExt = s.unknown.groupBy { it.extension.lowercase().ifEmpty { "(no extension)" } }.entries.sortedByDescending { it.value.size }
        sb.append("• ${s.unknown.size} file(s) Ryzer doesn't recognise as games: " +
            byExt.take(5).joinToString(", ") { "${it.value.size} .${it.key}" } +
            ". If those are games, the folder may be named for another system (e.g. .iso in the wrong folder).\n")
    }
    val meta = Library.meta.size
    sb.append(if (meta > 0) "✓ Details (descriptions, years, developers) found for $meta game(s) in your ES-DE / EmulationStation gamelists.\n"
        else "• No gamelist.xml found. If you use ES-DE, its scraped descriptions show up here automatically.\n")
    return sb.toString()
}

// ===== Export ===========================================================================

object Export {
    private fun csv(vararg cols: Any?) = cols.joinToString(",") { v ->
        val s = v?.toString().orEmpty()
        if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }

    /** Writes your library and play history as spreadsheet-friendly CSV files and opens the share menu. */
    fun run(app: Ryzer) {
        val st = app.state
        val dir = File(app.home, "exports").apply { mkdirs() }
        val stamp = today()
        val lib = File(dir, "library-$stamp.csv")
        lib.bufferedWriter().use { w ->
            w.write(csv("System", "Title", "File", "Minutes played", "Launches", "Last played", "Rating", "Status", "Favorite", "Year", "Developer", "Genre"))
            w.newLine()
            for (g in app.allGames().sortedWith(compareBy({ it.system.name }, { app.title(it).lowercase() }))) {
                val m = Library.meta[g.key]
                w.write(csv(g.system.name, app.title(g), g.file.name, st.playtime(g.key) / 60, st.launches[g.key] ?: 0,
                    st.lastPlayed[g.key]?.let { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(it)) },
                    st.ratings[g.key], st.status[g.key], if (st.isFavorite(g.key)) "yes" else "", m?.year, m?.developer, m?.genre))
                w.newLine()
            }
        }
        val sessions = File(dir, "sessions-$stamp.csv")
        sessions.bufferedWriter().use { w ->
            w.write(csv("Started", "Game", "System", "Minutes")); w.newLine()
            for ((k, t, secs) in st.sessions) {
                val g = Library.find(app.systems, k)
                w.write(csv(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(t)),
                    g?.let { app.title(it) } ?: File(k).nameWithoutExtension, g?.system?.name, secs / 60))
                w.newLine()
            }
        }
        app.notify("Saved to RyzerOS/exports", 3f)
        Sharing.shareFile(app, lib, "text/csv", "Share your library")
    }
}

// ===== Goals & reminders ==================================================================

fun goalsMenu(app: Ryzer): Menu {
    val cfg = app.cfg
    return Menu(app, "Goals & reminders", listOf(
        Item("Daily play goal", "A ring on the top screen fills up as you play, and you get a notice when you reach it.",
            value = { if (cfg.dailyGoal == 0) "Off" else "${cfg.dailyGoal} min" },
            change = { d -> val opts = listOf(0, 15, 30, 45, 60, 90, 120)
                cfg.dailyGoal = opts[(opts.indexOf(cfg.dailyGoal).coerceAtLeast(0) + d + opts.size) % opts.size]; cfg.save() }),
        Item("Weekly report", "Every Monday: how much you played last week and your most-played game.",
            value = { if (cfg.weeklyReport) "On" else "Off" }, change = { cfg.weeklyReport = !cfg.weeklyReport; cfg.save() }),
        Item("Streak reminder", "In the evening, if you haven't played yet and have a streak going.",
            value = { if (cfg.streakReminder) "On" else "Off" }, change = { cfg.streakReminder = !cfg.streakReminder; cfg.save() }),
        Item("Unplug reminder", "Tells you when the battery reaches this level while charging. Stopping at 80-90% helps it last longer.",
            value = { if (cfg.chargeAlert == 0) "Off" else "${cfg.chargeAlert}%" },
            change = { d -> val opts = listOf(0, 80, 85, 90)
                cfg.chargeAlert = opts[(opts.indexOf(cfg.chargeAlert).coerceAtLeast(0) + d + opts.size) % opts.size]; cfg.save() }),
        Item("Ryzer Wrapped", "Your month or year in games.", select = { app.push(WrappedScreen(app)) }),
    ))
}

/** The daily goal ring for the top screen. */
fun drawGoalRing(c: Canvas, app: Ryzer, th: Theme, cx: Float, cy: Float, r: Float) {
    val goal = app.cfg.dailyGoal
    if (goal <= 0) return
    val frac = (app.state.playedToday() / 60f / goal).coerceIn(0f, 1f)
    G.ring(c, cx, cy, r, withAlpha(th.c("text_dim"), 0.35f), 4f)
    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE; strokeWidth = 4f; color = if (frac >= 1f) th.c("good") else th.c("accent")
        strokeCap = android.graphics.Paint.Cap.ROUND
    }
    c.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), -90f, 360f * frac, false, p)
    G.text(c, if (frac >= 1f) "✓" else "${(frac * 100).toInt()}", cx, cy - r * 0.62f, r * 0.95f, th.c("text"), align = 'c', isBold = true)
}
