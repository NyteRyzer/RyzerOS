package com.ryzer.os

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.provider.Settings
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private fun plural(n: Int, w: String) = "$n $w${if (n == 1) "" else "s"}"

class Tile(val kind: String, val label: String, val sub: String = "", val color: Int? = null,
           val system: GameSystem? = null, val game: Game? = null, val note: String = "")

fun drawIcon(c: Canvas, kind: String, cx: Float, cy: Float, s: Float, color: Int) {
    when (kind) {
        "favorites" -> { val p = Path(); for (k in 0 until 10) { val r = if (k % 2 == 0) s else s * 0.45f
            val a = Math.toRadians(-90.0 + k * 36); val x = cx + r * cos(a).toFloat(); val y = cy + r * sin(a).toFloat()
            if (k == 0) p.moveTo(x, y) else p.lineTo(x, y) }; p.close(); G.path(c, p, color) }
        "recent" -> { G.ring(c, cx, cy, s, color, 4f); G.line(c, cx, cy, cx, cy - s * 0.6f, color, 4f); G.line(c, cx, cy, cx + s * 0.45f, cy, color, 4f) }
        "search" -> { G.ring(c, cx - s * 0.2f, cy - s * 0.2f, s * 0.6f, color, 4f); G.line(c, cx + s * 0.22f, cy + s * 0.22f, cx + s * 0.8f, cy + s * 0.8f, color, 6f) }
        "settings" -> { for (k in 0 until 8) { val a = Math.toRadians(k * 45.0)
            G.line(c, cx, cy, cx + s * cos(a).toFloat(), cy + s * sin(a).toFloat(), color, 7f) }
            G.circle(c, cx, cy, s * 0.7f, color); G.circle(c, cx, cy, s * 0.3f, 0xFF000000.toInt()) }
        "themes" -> { G.ring(c, cx, cy, s, color, 4f)
            listOf(0xFFFF5050, 0xFF50C8FF, 0xFFFFD23C, 0xFF78E678).forEachIndexed { k, col -> val a = Math.toRadians(-135.0 + k * 70)
                G.circle(c, cx + s * 0.5f * cos(a).toFloat(), cy + s * 0.5f * sin(a).toFloat(), s * 0.2f, col.toInt()) } }
        "apps" -> for (i in 0 until 3) for (j in 0 until 3) G.rect(c, RectF(cx - s + i * s * 0.7f, cy - s + j * s * 0.7f,
            cx - s + i * s * 0.7f + s * 0.5f, cy - s + j * s * 0.7f + s * 0.5f), color, 3f)
        "device" -> { val r = RectF(cx - s * 0.6f, cy - s, cx + s * 0.6f, cy + s); G.outline(c, r, color, 4f, 6f); G.circle(c, cx, cy + s * 0.7f, 3f, color) }
        "random" -> { val r = RectF(cx - s * 0.8f, cy - s * 0.8f, cx + s * 0.8f, cy + s * 0.8f); G.outline(c, r, color, 4f, 8f)
            listOf(-0.4f to -0.4f, 0.4f to 0.4f, 0f to 0f, 0.4f to -0.4f, -0.4f to 0.4f).forEach { (dx, dy) -> G.circle(c, cx + dx * s, cy + dy * s, 4f, color) } }
        "continue" -> { val p = Path(); p.moveTo(cx - s * 0.5f, cy - s * 0.8f); p.lineTo(cx - s * 0.5f, cy + s * 0.8f); p.lineTo(cx + s * 0.8f, cy); p.close(); G.path(c, p, color) }
    }
}

class HomeScreen(app: Ryzer) : Screen(app) {
    override val title = "Home"
    private var sel = 0
    private var scroll = 0f
    private var t = 0f
    private var tiles = listOf<Tile>()
    private val tileW = 132f; private val tileH = 150f; private val selW = 160f; private val selH = 180f; private val gap = 16f

    init { rebuild() }

    fun rebuild() {
        val old = tiles.getOrNull(sel)?.label
        val out = ArrayList<Tile>()
        val recent = app.state.recent.mapNotNull { Library.find(app.systems, it) }
        recent.firstOrNull()?.let { g ->
            out += Tile("continue", "Continue", g.name, game = g, system = g.system,
                note = "Resume ${g.name} (${g.system.short}). Played ${formatDuration(app.state.playtime(g.key))}.")
        }
        val favs = app.state.favorites.count { Library.find(app.systems, it) != null }
        out += Tile("favorites", "Favorites", plural(favs, "game"), note = "Games you starred with X.")
        out += Tile("recent", "Recent", plural(recent.size, "game"), note = "Your recently played games.")
        for (s in app.systems) {
            if (s.games.isEmpty() && app.cfg.hideEmpty) continue
            out += Tile("system", s.short, plural(s.games.size, "game"), s.color, s,
                note = listOf(s.demand, s.note, "Emulator: " + Launcher.label(app, Launcher.chosen(app, s))).filter { it.isNotEmpty() }.joinToString(". "))
        }
        if (app.cfg.showApps) out += Tile("apps", "Apps", "Android apps", note = "Launch any app on your device, like a console home screen.")
        out += Tile("random", "Surprise", "Random game", note = "Can't decide? Launch a random game from your library.")
        out += Tile("search", "Search", "All systems", note = "Find any game by name.")
        out += Tile("themes", "Themes", "${app.themes.size} themes", note = "Pick from 20+ themes or design your own.")
        out += Tile("settings", "Settings", "Ryzer", note = "Emulators, ROM folders, dual screen, display, sound and controls.")
        out += Tile("device", "Device", "Android settings", note = "Open the Android settings app (Wi-Fi, Bluetooth, performance mode).")
        tiles = out
        val i = tiles.indexOfFirst { it.label == old }
        sel = if (i >= 0) i else min(sel, tiles.size - 1)
    }

    override fun onResume() = rebuild()

    override fun handle(a: String) {
        val n = tiles.size
        when (a) {
            "left", "right" -> { sel = (sel + (if (a == "right") 1 else -1) + n) % n; app.sounds.play("move") }
            "up" -> { sel = (sel - 4).coerceAtLeast(0); app.sounds.play("move") }
            "down" -> { sel = (sel + 4).coerceAtMost(n - 1); app.sounds.play("move") }
            "l", "r" -> { sel = (sel + if (a == "r") 4 else -4).coerceIn(0, n - 1); app.sounds.play("move") }
            "a" -> { app.sounds.play("select"); open(tiles[sel]) }
            "y" -> app.openSearch()
            "x" -> app.openRandom()
            "start" -> app.push(settingsMenu(app))
            "select" -> app.push(ThemePicker(app))
        }
    }

    private fun open(t: Tile) {
        when (t.kind) {
            "continue" -> t.game?.let { app.launch(it) }
            "system" -> t.system?.let { app.push(GameList(app, it.name, it.games, it)) }
            "favorites" -> app.push(GameList(app, "Favorites", null, source = "favorites"))
            "recent" -> app.push(GameList(app, "Recently Played", null, source = "recent"))
            "apps" -> app.push(AppsScreen(app))
            "random" -> app.openRandom()
            "search" -> app.openSearch()
            "themes" -> app.push(ThemePicker(app))
            "settings" -> app.push(settingsMenu(app))
            "device" -> runCatching { app.ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    override fun update(dt: Float) {
        t += dt
        scroll = if (app.cfg.animations) scroll + (sel - scroll) * min(1f, dt * 12) else sel.toFloat()
    }

    private fun drawTile(c: Canvas, tile: Tile, r: RectF, selected: Boolean) {
        val pulse = if (selected) ((sin(t * 4) + 1) / 2) else 0f
        G.panel(c, r, th, selected, pulse)
        val band = RectF(r.left + 6, r.top + 6, r.right - 6, r.top + 6 + r.height() * 0.55f)
        G.rect(c, band, mix(th.c("panel"), tile.color ?: th.c("accent"), if (selected) 0.75f else 0.5f), max(0f, th.radius - 4))
        val iconCol = if (selected) th.c("highlight_text") else mix(th.c("highlight_text"), th.c("panel"), 0.3f)
        if (tile.kind == "system") G.text(c, tile.label, band.centerX(), band.centerY() - 20, if (selected) 44f else 38f, iconCol, align = 'c', isBold = true, maxW = band.width() - 6)
        else drawIcon(c, tile.kind, band.centerX(), band.centerY(), if (selected) 26f else 22f, iconCol)
        G.text(c, tile.label, r.centerX(), band.bottom + 10, if (selected) 24f else 22f, th.c("text"), align = 'c', isBold = selected, maxW = r.width() - 12, theme = th)
        G.text(c, tile.sub, r.centerX(), band.bottom + 36, 18f, th.c("text_dim"), align = 'c', maxW = r.width() - 12)
    }

    override fun draw(c: Canvas, w: Float, h: Float) {
        G.topBar(c, app, th, "RYZER", w)
        val cy = 70 + selH / 2
        val order = tiles.indices.sortedBy { it == sel }
        for (i in order) {
            var dx = (i - scroll) * (tileW + gap)
            if (i != sel) dx += (selW - tileW) / 2 * (if (i > sel) 1 else -1)
            val cx = w / 2 + dx
            if (cx < -selW || cx > w + selW) continue
            val s = i == sel
            val tw = if (s) selW else tileW; val tht = if (s) selH else tileH
            val r = RectF(cx - tw / 2, cy - tht / 2, cx + tw / 2, cy + tht / 2)
            drawTile(c, tiles[i], r, s)
            app.hit(r) { if (sel == i) handle("a") else { sel = i; app.sounds.play("move") } }
        }
        val tile = tiles[sel]
        val info = RectF(16f, cy + selH / 2 + 16, w - 16, h - 46)
        G.panel(c, info, th)
        val title = when (tile.kind) { "system" -> tile.system!!.name; "continue" -> tile.sub; else -> tile.label }
        G.text(c, title, info.left + 14, info.top + 10, 28f, th.c("text"), isBold = true, maxW = info.width() - 40, theme = th)
        G.wrap(tile.note, 20f, info.width() - 28).take(4).forEachIndexed { i, l -> G.text(c, l, info.left + 14, info.top + 42 + i * 21, 20f, th.c("text_dim")) }
        val n = tiles.size
        for (i in 0 until n) {
            val x = w / 2 + (i - (n - 1) / 2f) * 12
            G.circle(c, x, info.top - 8, if (i == sel) 3f else 2f, if (i == sel) th.c("accent") else th.c("panel_border"))
        }
    }

    override fun drawTop(c: Canvas, w: Float, h: Float) {
        G.topBar(c, app, th, "", w)
        app.drawBigClock(c, w, h / 2 - 90)
        val tile = tiles.getOrNull(sel) ?: return
        G.image(tile.game?.image())?.let { G.drawImageFit(c, it, RectF(40f, h / 2 + 10, w - 40, h - 20)); return }
        G.text(c, "Total play time  ${formatDuration(app.state.total())}", w / 2, h / 2 + 40, 24f, th.c("text_dim"), align = 'c')
        G.text(c, "${app.systems.sumOf { it.games.size }} games  |  ${app.state.favorites.size} favorites", w / 2, h / 2 + 72, 24f, th.c("text_dim"), align = 'c')
    }

    override fun hints() = listOf("SELECT" to "Themes", "X" to "Random", "Y" to "Search", "A" to "Open")
}

class GameList(app: Ryzer, override val title: String, private val initial: List<Game>?, private val system: GameSystem? = null,
               private val source: String? = null) : Screen(app) {
    private var games = listOf<Game>()
    private var sel = 0
    private var offset = 0
    private val rowH = 34f

    init { refresh() }

    private fun refresh() {
        games = when (source) {
            "favorites" -> app.state.favorites.mapNotNull { Library.find(app.systems, it) }.sortedBy { it.name.lowercase() }
            "recent" -> app.state.recent.mapNotNull { Library.find(app.systems, it) }
            else -> system?.games ?: initial ?: emptyList()
        }
        sel = sel.coerceIn(0, max(0, games.size - 1))
    }

    override fun onResume() = refresh()
    fun current() = games.getOrNull(sel)

    private fun letterJump(dir: Int) {
        if (games.isEmpty()) return
        val letter = games[sel].name.take(1).lowercase()
        val idx = if (dir > 0) (sel + 1 until games.size) else (sel - 1 downTo 0)
        for (i in idx) {
            val first = games[i].name.take(1).lowercase()
            if (first != letter) {
                var j = i
                if (dir < 0) while (j > 0 && games[j - 1].name.take(1).lowercase() == first) j--
                sel = j; return
            }
        }
        sel = if (dir > 0) games.size - 1 else 0
    }

    override fun handle(a: String) {
        val n = games.size
        when (a) {
            "up", "down" -> if (n > 0) { sel = (sel + (if (a == "down") 1 else -1) + n) % n; app.sounds.play("move") }
            "left", "right" -> if (n > 0) { sel = (sel + if (a == "right") 10 else -10).coerceIn(0, n - 1); app.sounds.play("move") }
            "l", "r" -> { letterJump(if (a == "r") 1 else -1); app.sounds.play("move") }
            "a" -> current()?.let { app.sounds.play("select"); app.launch(it) }
            "x" -> current()?.let {
                val fav = app.state.toggleFavorite(it.key)
                app.sounds.play("fav"); app.notify(if (fav) "Added to favorites" else "Removed from favorites")
                if (source == "favorites") refresh()
            }
            "start" -> current()?.let { options(it) }
            "y" -> app.openSearch()
            "b" -> { app.sounds.play("back"); app.pop() }
        }
    }

    private fun options(g: Game) {
        app.push(Menu(app, g.name, listOf(
            Item("Play", select = { app.pop(); app.launch(g) }),
            Item("Favorite", value = { if (app.state.isFavorite(g.key)) "Yes" else "No" }, change = { app.state.toggleFavorite(g.key) }),
            Item("${g.system.short} emulator", value = { Launcher.label(app, Launcher.chosen(app, g.system)) },
                select = { app.push(EmulatorPicker(app, g.system)) }, help = "Applies to every ${g.system.short} game."),
            Item("Open with another app", select = { app.pop(); app.state.startPlay(g.key)
                runCatching { app.ctx.startActivity(Launcher.anyAppIntent(g, null)) } }, help = "Pick any installed app for just this launch."),
            Item("Game info", select = { app.push(Message(app, g.name, info(g))) }),
        )))
    }

    private fun info(g: Game): String {
        val st = app.state
        return "System: ${g.system.name}\nFile: ${g.file.name}\nFolder: ${g.file.parent}\nSize: ${"%.1f".format(g.file.length() / 1e6)} MB\n" +
            "Play time: ${formatDuration(st.playtime(g.key))}\nLaunches: ${st.launches[g.key] ?: 0}\n" +
            "Emulator: ${Launcher.label(app, Launcher.chosen(app, g.system))}\n\n${g.system.demand}. ${g.system.note}"
    }

    override fun draw(c: Canvas, w: Float, h: Float) {
        G.topBar(c, app, th, title, w)
        if (games.isEmpty()) {
            val msg = when (source) { "favorites" -> "No favorites yet. Press X on any game to add it."; "recent" -> "Nothing played yet."
                else -> "No games found. Put ROMs in ROMs/<system> or add a folder in Settings > ROM folders." }
            G.wrap(msg, 24f, w - 60).forEachIndexed { i, l -> G.text(c, l, w / 2, h / 2 - 24 + i * 28, 24f, th.c("text_dim"), align = 'c') }
            return
        }
        val detailsW = if (app.dual) 0f else min(220f, w * 0.34f)
        val listW = w - 28 - detailsW - (if (detailsW > 0) 8 else 0)
        val top = 52f
        val visible = ((h - top - 44) / rowH).toInt()
        offset = G.scrollOffset(sel, offset, visible, games.size)
        for (i in offset until min(games.size, offset + visible)) {
            val g = games[i]
            val r = RectF(14f, top + (i - offset) * rowH, 14 + listW, top + (i - offset) * rowH + rowH - 4)
            val s = i == sel
            if (s) G.panel(c, r, th, true)
            app.hit(r) { if (sel == i) handle("a") else sel = i }
            if (app.state.isFavorite(g.key)) G.text(c, "*", r.left + 12, r.top + 4, 30f, th.c("warn"), isBold = true)
            val label = if (system != null) g.name else "${g.name}  [${g.system.short}]"
            G.text(c, label, r.left + 28, r.top + 6, 23f, if (s) th.c("text") else th.c("text_dim"), maxW = r.width() - 40, theme = th)
        }
        G.scrollbar(c, th, RectF(listW + 18, top, listW + 23, top + visible * rowH - 4), offset, visible, games.size)
        G.text(c, "${sel + 1}/${games.size}", listW + 10, h - 62, 18f, th.c("text_dim"), align = 'r')
        if (detailsW > 0) details(c, RectF(w - detailsW - 14, top, w - 14, h - 46), true)
    }

    private fun details(c: Canvas, r: RectF, compact: Boolean) {
        val g = current() ?: return
        G.panel(c, r, th)
        var y = r.top + 10
        val bmp = G.image(g.image())
        if (bmp != null) {
            y = G.drawImageFit(c, bmp, RectF(r.left + 10, y, r.right - 10, y + if (compact) r.height() / 2 else r.height() - 150)).bottom + 8
        } else {
            val art = RectF(r.left + 10, y, r.right - 10, y + if (compact) 90 else 140)
            G.rect(c, art, mix(th.c("panel"), g.system.color, 0.6f), max(0f, th.radius - 4))
            G.text(c, g.system.short, art.centerX(), art.centerY() - 18, 44f, th.c("highlight_text"), align = 'c', isBold = true)
            y = art.bottom + 8
        }
        G.text(c, g.name, r.centerX(), y, if (compact) 22f else 30f, th.c("text"), align = 'c', isBold = true, maxW = r.width() - 16)
        y += if (compact) 26 else 36
        for (line in listOf("Played ${formatDuration(app.state.playtime(g.key))}", Launcher.label(app, Launcher.chosen(app, g.system)))) {
            G.text(c, line, r.centerX(), y, if (compact) 19f else 24f, th.c("text_dim"), align = 'c', maxW = r.width() - 16)
            y += if (compact) 22 else 30
        }
    }

    override fun drawTop(c: Canvas, w: Float, h: Float) {
        G.topBar(c, app, th, title, w)
        if (games.isNotEmpty()) details(c, RectF(20f, 52f, w - 20, h - 12), false)
    }

    override fun hints() = listOf("START" to "Options", "X" to "Fav", "L/R" to "A-Z", "A" to "Play")
}
