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
        "played", "stats" -> for (i in 0 until 3) { val bh = s * (0.6f + 0.5f * i); G.rect(c, RectF(cx - s + i * s * 0.7f, cy + s - bh, cx - s + i * s * 0.7f + s * 0.5f, cy + s), color, 2f) }
        "added" -> { G.ring(c, cx, cy, s, color, 4f); G.line(c, cx - s * 0.5f, cy, cx + s * 0.5f, cy, color, 5f); G.line(c, cx, cy - s * 0.5f, cx, cy + s * 0.5f, color, 5f) }
        "gotd" -> { G.ring(c, cx, cy, s, color, 4f); G.text(c, "1", cx, cy - s * 0.7f, s * 1.6f, color, align = 'c', isBold = true) }
        "collection" -> for (i in 0 until 3) G.outline(c, RectF(cx - s + i * 6, cy - s * 0.7f + i * 6, cx + s * 0.6f + i * 6, cy + s * 0.5f + i * 6), color, 3f, 4f)
    }
}

/** Today's pick: the same game all day, favouring ones you haven't played yet. */
fun gameOfTheDay(app: Ryzer): Game? {
    val all = app.allGames()
    if (all.isEmpty()) return null
    val fresh = all.filter { (app.state.launches[it.key] ?: 0) == 0 }.ifEmpty { all }
    return fresh.sortedBy { it.key }[(today().hashCode() and 0x7fffffff) % fresh.size]
}

fun greeting(app: Ryzer): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val part = when (hour) { in 5..11 -> "Good morning"; in 12..17 -> "Good afternoon"; in 18..22 -> "Good evening"; else -> "Up late" }
    return if (app.cfg.userName.isNotBlank()) "$part, ${app.cfg.userName}" else part
}

class HomeScreen(app: Ryzer) : Screen(app) {
    override val title = "Home"
    private var sel = 0
    private var scroll = 0f
    private var t = 0f
    private var tiles = listOf<Tile>()
    private val tileW = 132f; private val tileH = 150f; private val selW = 160f; private val selH = 180f; private val gap = 16f
    private val kid get() = app.cfg.kidMode
    private val grid get() = app.cfg.homeLayout == "grid"
    private var cols = 5
    private var gridOffset = 0

    init { rebuild() }

    fun rebuild() {
        val old = tiles.getOrNull(sel)?.let { it.kind + it.label }
        val st = app.state
        val out = ArrayList<Tile>()
        val recent = app.visible(st.recent.mapNotNull { Library.find(app.systems, it) })
        recent.firstOrNull()?.let { g ->
            out += Tile("continue", "Continue", app.title(g), game = g, system = g.system,
                note = "Resume ${app.title(g)} (${g.system.short}). Played ${formatDuration(st.playtime(g.key))}.")
        }
        for (g in st.pins.mapNotNull { Library.find(app.systems, it) }) {
            out += Tile("pinned", app.title(g), g.system.short, g.system.color, g.system, g,
                note = "Pinned to Home. ${g.system.name}, played ${formatDuration(st.playtime(g.key))}. Unpin it in the game's options (START).")
        }
        gameOfTheDay(app)?.let { g ->
            out += Tile("gotd", "Game of the Day", app.title(g), g.system.color, g.system, g,
                note = "Today's pick: ${app.title(g)} (${g.system.name}). A new suggestion every day, favouring games you haven't tried.")
        }
        val favs = app.visible(st.favorites.mapNotNull { Library.find(app.systems, it) }).size
        out += Tile("favorites", "Favorites", plural(favs, "game"), note = "Games you starred with X.")
        out += Tile("recent", "Recent", plural(recent.size, "game"), note = "Your recently played games.")
        val played = st.times.count { it.value > 0 }
        if (played > 0) out += Tile("played", "Most Played", plural(played, "game"), note = "Your games ranked by play time.")
        if (app.allGames().isNotEmpty()) out += Tile("added", "New", "Recently added", note = "The newest games on your storage.")
        for ((name, keys) in st.collections) out += Tile("collection", name, plural(keys.size, "game"), note = "Your collection. Add games from any game's options (START).")
        for (s in sortedSystems(app)) {
            if (s.games.isEmpty() && app.cfg.hideEmpty) continue
            out += Tile("system", s.short, plural(app.visible(s.games).size, "game"), s.color, s,
                note = listOf(s.demand, s.note, "Emulator: " + Launcher.label(app, Launcher.chosen(app, s))).filter { it.isNotEmpty() }.joinToString(". "))
        }
        if (app.cfg.showApps && !kid) out += Tile("apps", "Apps", "Android apps", note = "Launch any app on your device, like a console home screen.")
        out += Tile("random", "Surprise", "Random game", note = "Can't decide? Launch a random game from your library.")
        out += Tile("search", "Search", "All systems", note = "Find any game by name.")
        out += Tile("stats", "Stats", "${st.streak()} day streak", note = "Play time, a 7-day activity chart, session history, top games and milestones.")
        if (!kid) {
            out += Tile("themes", "Themes", "${app.themes.size} themes", note = "Pick a theme, design your own, or turn any photo into a theme.")
            out += Tile("settings", "Settings", "Ryzer", note = "Emulators, ROM folders, dual screen, display, sound and controls.")
            out += Tile("device", "Device", "Android settings", note = "Open the Android settings app (Wi-Fi, Bluetooth, performance mode).")
        }
        tiles = out
        val i = tiles.indexOfFirst { it.kind + it.label == old }
        sel = if (i >= 0) i else min(sel, tiles.size - 1)
    }

    override fun onResume() = rebuild()

    override fun handle(a: String) {
        val n = tiles.size
        val rowStep = if (grid) cols else 4
        when (a) {
            "left", "right" -> { sel = if (grid) (sel + if (a == "right") 1 else -1).coerceIn(0, n - 1) else (sel + (if (a == "right") 1 else -1) + n) % n; app.sounds.play("move") }
            "up" -> { sel = (sel - rowStep).coerceAtLeast(0); app.sounds.play("move") }
            "down" -> { sel = (sel + rowStep).coerceAtMost(n - 1); app.sounds.play("move") }
            "l", "r" -> { sel = (sel + if (a == "r") 4 else -4).coerceIn(0, n - 1); app.sounds.play("move") }
            "a" -> { app.sounds.play("select"); open(tiles[sel]) }
            "y" -> app.openSearch()
            "x" -> app.openRandom()
            "start" -> if (kid) KidMode.unlock(app) else app.push(settingsMenu(app))
            "select" -> if (!kid) app.push(ThemePicker(app))
        }
    }

    private fun open(t: Tile) {
        when (t.kind) {
            "continue", "pinned", "gotd" -> t.game?.let { app.launch(it) }
            "system" -> t.system?.let { app.push(GameList(app, it.name, it.games, it)) }
            "favorites" -> app.push(GameList(app, "Favorites", null, source = "favorites"))
            "recent" -> app.push(GameList(app, "Recently Played", null, source = "recent"))
            "played" -> app.push(GameList(app, "Most Played", null, source = "played"))
            "added" -> app.push(GameList(app, "Recently Added", null, source = "added"))
            "collection" -> app.push(GameList(app, t.label, null, source = "collection:${t.label}"))
            "stats" -> app.push(StatsScreen(app))
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
        scroll = if (app.animationsOn) scroll + (sel - scroll) * min(1f, dt * 12) else sel.toFloat()
    }

    /** A custom picture for a system tile: RyzerOS/tiles/<system id>.png (or .jpg). */
    private fun tileArt(tile: Tile): android.graphics.Bitmap? {
        tile.game?.let { if (tile.kind == "pinned" || tile.kind == "gotd") return G.image(it.image()) }
        val s = tile.system ?: return null
        if (tile.kind != "system") return null
        val dir = java.io.File(app.home, "tiles")
        return listOf("png", "jpg", "webp").firstNotNullOfOrNull { ext -> java.io.File(dir, "${s.id}.$ext").takeIf { it.isFile } }?.let { G.image(it) }
    }

    private fun drawTile(c: Canvas, tile: Tile, r: RectF, selected: Boolean) {
        val pulse = if (selected && app.animationsOn) ((sin(t * 4) + 1) / 2) else 0f
        G.panel(c, r, th, selected, pulse)
        val band = RectF(r.left + 6, r.top + 6, r.right - 6, r.top + 6 + r.height() * 0.55f)
        G.rect(c, band, mix(th.c("panel"), tile.color ?: th.c("accent"), if (selected) 0.75f else 0.5f), max(0f, th.radius - 4))
        val iconCol = if (selected) th.c("highlight_text") else mix(th.c("highlight_text"), th.c("panel"), 0.3f)
        val art = tileArt(tile)
        when {
            art != null -> { c.save(); c.clipRect(band); G.drawImageFit(c, art, band); c.restore() }
            tile.kind == "system" -> G.text(c, tile.label, band.centerX(), band.centerY() - 20, if (selected) 44f else 38f, iconCol, align = 'c', isBold = true, maxW = band.width() - 6)
            tile.kind == "pinned" || tile.kind == "gotd" -> G.text(c, tile.system?.short ?: "", band.centerX(), band.centerY() - 20, 38f, iconCol, align = 'c', isBold = true)
            else -> drawIcon(c, tile.kind, band.centerX(), band.centerY(), if (selected) 26f else 22f, iconCol)
        }
        if (tile.kind == "pinned") G.text(c, "PIN", band.right - 6, band.top + 4, 15f, th.c("highlight_text"), align = 'r', isBold = true)
        G.text(c, tile.label, r.centerX(), band.bottom + 10, if (selected) 24f else 22f, th.c("text"), align = 'c', isBold = selected, maxW = r.width() - 12, theme = th)
        G.text(c, tile.sub, r.centerX(), band.bottom + 36, 18f, th.c("text_dim"), align = 'c', maxW = r.width() - 12)
    }

    override fun draw(c: Canvas, w: Float, h: Float) {
        val tile = tiles[sel]
        if (app.cfg.systemAccent) app.accentOverride = tile.system?.color
        G.topBar(c, app, th, if (kid) "RYZER  |  KID MODE" else "RYZER", w)
        if (grid) { drawGrid(c, w, h); return }
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
        val info = RectF(16f, cy + selH / 2 + 16, w - 16, h - 46)
        val tint = tile.game?.image()?.let { G.image(it) }?.takeIf { app.cfg.artTint }?.let { G.dominant(it) }
        G.panel(c, info, th, fillColor = tint?.let { withAlpha(mix(th.c("panel"), it, 0.3f), G.panelAlpha) })
        val title = when (tile.kind) { "system" -> tile.system!!.name; "continue", "gotd" -> tile.sub; else -> tile.label }
        G.text(c, title, info.left + 14, info.top + 10, 28f, th.c("text"), isBold = true, maxW = info.width() - 40, theme = th)
        G.wrap(tile.note, 20f, info.width() - 28).take(max(1, ((info.height() - 46) / 21).toInt())).forEachIndexed { i, l ->
            G.text(c, l, info.left + 14, info.top + 42 + i * 21, 20f, th.c("text_dim")) }
        val n = tiles.size
        for (i in 0 until n) {
            val x = w / 2 + (i - (n - 1) / 2f) * 10
            G.circle(c, x, info.top - 8, if (i == sel) 3f else 2f, if (i == sel) th.c("accent") else th.c("panel_border"))
        }
    }

    /** Grid layout: every tile at once, like a dashboard. */
    private fun drawGrid(c: Canvas, w: Float, h: Float) {
        cols = max(4, (w / 125).toInt())
        val gp = 10f
        val cw = (w - 32 - (cols - 1) * gp) / cols
        val ch = 118f
        val top = 52f
        val rowsVisible = max(1, ((h - top - 80) / (ch + gp)).toInt())
        val row = sel / cols
        if (row < gridOffset) gridOffset = row else if (row >= gridOffset + rowsVisible) gridOffset = row - rowsVisible + 1
        tiles.forEachIndexed { i, tile ->
            val rr = i / cols - gridOffset; val cc = i % cols
            if (rr < 0 || rr >= rowsVisible) return@forEachIndexed
            val r = RectF(16 + cc * (cw + gp), top + rr * (ch + gp), 16 + cc * (cw + gp) + cw, top + rr * (ch + gp) + ch)
            drawTile(c, tile, r, i == sel)
            app.hit(r) { if (sel == i) handle("a") else { sel = i; app.sounds.play("move") } }
        }
        val tile = tiles[sel]
        G.text(c, tile.note, 20f, h - 72, 19f, th.c("text_dim"), maxW = w - 40)
    }

    override fun drawTop(c: Canvas, w: Float, h: Float) {
        G.topBar(c, app, th, greeting(app), w)
        app.drawBigClock(c, w, h / 2 - 120)
        val tile = tiles.getOrNull(sel) ?: return
        G.image(tile.game?.image())?.let { G.drawImageFit(c, it, RectF(40f, h / 2 - 10, w - 40, h - 20)); return }
        G.text(c, "Total play time  ${formatDuration(app.state.total())}  |  ${app.state.streak()} day streak", w / 2, h / 2 + 10, 22f,
            th.c("text_dim"), align = 'c', maxW = w - 30)
        // Recently played shelf
        val recent = app.visible(app.state.recent.mapNotNull { Library.find(app.systems, it) }).take(5)
        if (recent.isEmpty()) return
        val bw = min(110f, (w - 40 - 4 * 12) / 5)
        val startX = w / 2 - (recent.size * bw + (recent.size - 1) * 12) / 2
        G.text(c, "Recently played", w / 2, h / 2 + 44, 18f, th.c("accent"), align = 'c', isBold = true)
        recent.forEachIndexed { i, g ->
            val r = RectF(startX + i * (bw + 12), h / 2 + 70, startX + i * (bw + 12) + bw, h / 2 + 70 + bw)
            val bmp = G.image(g.image())
            if (bmp != null) G.drawImageFit(c, bmp, r) else {
                G.rect(c, r, mix(th.c("panel"), g.system.color, 0.6f), 8f)
                G.text(c, g.system.short, r.centerX(), r.centerY() - 14, 28f, th.c("highlight_text"), align = 'c', isBold = true)
            }
            G.text(c, app.title(g), r.centerX(), r.bottom + 4, 15f, th.c("text_dim"), align = 'c', maxW = bw)
            app.hit(r) { app.launch(g) }
        }
    }

    override fun hints() = if (kid) listOf("START" to "Unlock", "X" to "Random", "A" to "Open")
        else listOf("SELECT" to "Themes", "X" to "Random", "Y" to "Search", "A" to "Open")
}

class GameList(app: Ryzer, override val title: String, private val initial: List<Game>?, private val system: GameSystem? = null,
               private val source: String? = null) : Screen(app) {
    private var games = listOf<Game>()
    private var sel = 0
    private var offset = 0
    private val rowH = 34f
    private var cols = 4
    private val collection = source?.takeIf { it.startsWith("collection:") }?.removePrefix("collection:")

    init { refresh() }

    private fun refresh() {
        val st = app.state
        val keep = current()?.key
        games = when {
            source == "favorites" -> ListView.apply(app, st.favorites.mapNotNull { Library.find(app.systems, it) })
            source == "recent" -> ListView.apply(app, st.recent.mapNotNull { Library.find(app.systems, it) }, sortIt = false)
            source == "played" -> ListView.apply(app, st.times.entries.filter { it.value > 0 }.sortedByDescending { it.value }
                .mapNotNull { Library.find(app.systems, it.key) }, sortIt = false)
            source == "added" -> ListView.apply(app, app.systems.flatMap { it.games }.sortedByDescending { it.file.lastModified() }.take(60), sortIt = false)
            collection != null -> ListView.apply(app, collectionGames(app, collection))
            else -> ListView.apply(app, system?.games ?: initial ?: emptyList())
        }
        sel = games.indexOfFirst { it.key == keep }.let { if (it >= 0) it else sel.coerceIn(0, max(0, games.size - 1)) }
    }

    override fun onResume() = refresh()
    fun current() = games.getOrNull(sel)
    private val grid get() = app.cfg.grid

    private fun letterJump(dir: Int) {
        if (games.isEmpty()) return
        fun first(i: Int) = app.title(games[i]).take(1).lowercase()
        val letter = first(sel)
        val idx = if (dir > 0) (sel + 1 until games.size) else (sel - 1 downTo 0)
        for (i in idx) {
            val f = first(i)
            if (f != letter) {
                var j = i
                if (dir < 0) while (j > 0 && first(j - 1) == f) j--
                sel = j; return
            }
        }
        sel = if (dir > 0) games.size - 1 else 0
    }

    override fun handle(a: String) {
        val n = games.size
        when (a) {
            "up", "down" -> if (n > 0) {
                val step = if (grid) cols else 1
                sel = if (grid) (sel + if (a == "down") step else -step).coerceIn(0, n - 1) else (sel + (if (a == "down") 1 else -1) + n) % n
                app.sounds.play("move")
            }
            "left", "right" -> if (n > 0) {
                sel = (sel + (if (a == "right") 1 else -1) * (if (grid) 1 else 10)).coerceIn(0, n - 1); app.sounds.play("move")
            }
            "l", "r" -> { letterJump(if (a == "r") 1 else -1); app.sounds.play("move") }
            "a" -> current()?.let { app.sounds.play("select"); app.launch(it) }
            "x" -> current()?.let {
                val fav = app.state.toggleFavorite(it.key)
                app.sounds.play("fav"); app.notify(if (fav) "Added to favorites" else "Removed from favorites")
                Milestones.check(app)
                if (source == "favorites") refresh()
            }
            "select" -> { app.cfg.grid = !app.cfg.grid; app.cfg.save(); app.notify(if (app.cfg.grid) "Grid view" else "List view") }
            "start" -> current()?.let { options(it) } ?: listOptions()
            "y" -> app.openSearch()
            "b" -> { app.sounds.play("back"); app.pop() }
        }
    }

    private fun cycleCfg(list: List<String>, cur: String, d: Int) = list[((list.indexOf(cur).coerceAtLeast(0) + d) % list.size + list.size) % list.size]

    private fun viewItems(): List<Item> = listOf(
        Item("Sort by", value = { ListView.label(app.cfg.sort) }, change = { d -> app.cfg.sort = cycleCfg(ListView.SORTS, app.cfg.sort, d); app.cfg.save() }),
        Item("Filter", value = { ListView.label(app.cfg.filter) }, change = { d -> app.cfg.filter = cycleCfg(ListView.FILTERS, app.cfg.filter, d); app.cfg.save() }),
        Item("View", value = { if (app.cfg.grid) "Grid" else "List" }, change = { app.cfg.grid = !app.cfg.grid; app.cfg.save() }),
        Item("Show hidden games", value = { if (app.cfg.showHidden) "On" else "Off" }, change = { app.cfg.showHidden = !app.cfg.showHidden; app.cfg.save() }),
        Item("Random game from this list", select = { app.pop(); app.randomFrom(games) }),
    )

    private fun listOptions() = app.push(Menu(app, title, viewItems()))

    private fun options(g: Game) {
        val st = app.state
        val kid = app.cfg.kidMode
        val items = mutableListOf(
            Item("Play", select = { app.pop(); app.launch(g) }),
            Item("Favorite", value = { if (st.isFavorite(g.key)) "Yes" else "No" }, change = { st.toggleFavorite(g.key); Milestones.check(app) }),
            Item("Rating", value = { ListView.stars(st.ratings[g.key] ?: 0) }, change = { d ->
                val r = ((st.ratings[g.key] ?: 0) + d).coerceIn(0, 5)
                if (r == 0) st.ratings.remove(g.key) else st.ratings[g.key] = r
                st.save(); Milestones.check(app) }),
            Item("Status", "Track your backlog: Backlog, Playing, Beaten, Completed.", value = { ListView.label(st.status[g.key] ?: "") }, change = { d ->
                val s = cycleCfg(ListView.STATUSES, st.status[g.key] ?: "", d)
                if (s.isEmpty()) st.status.remove(g.key) else st.status[g.key] = s
                st.save(); Milestones.check(app) }),
            Item("Add to collection", select = { app.push(addToCollectionMenu(app, g)) }),
            Item("Pin to Home", "Puts this game on its own Home tile.", value = { if (g.key in st.pins) "Pinned" else "No" }, change = {
                if (!st.pins.remove(g.key)) st.pins.add(g.key); st.save() }),
            Item("Notes", "Codes, passwords, where you left off. Shown on the Now Playing screen too.",
                value = { st.notes[g.key]?.takeIf { it.isNotBlank() }?.let { if (it.length > 18) it.take(18) + "..." else it } ?: "None" },
                select = { app.push(Keyboard(app, "Notes for ${app.title(g)}", st.notes[g.key] ?: "", 120) { text ->
                    if (text.isBlank()) st.notes.remove(g.key) else st.notes[g.key] = text
                    st.save() }) }),
            Item("Emulator controls", "How to open the emulator's menu, save states and more.", select = {
                app.push(Message(app, "Controls: ${Launcher.label(app, st.gameEmu[g.key] ?: Launcher.chosen(app, g.system))}",
                    DualPlay.hotkeys(app, st.gameEmu[g.key] ?: Launcher.chosen(app, g.system)))) }),
        )
        if (collection != null) items += Item("Remove from '$collection'", select = {
            st.collections[collection]?.remove(g.key); st.save(); app.pop(); refresh() })
        if (!kid) {
            items += Item("Rename", "Shown everywhere in Ryzer. The file isn't renamed.", value = { app.title(g) }, select = {
                app.push(Keyboard(app, "Rename game", app.title(g), 40) { name ->
                    if (name.isEmpty() || name == g.name) st.titles.remove(g.key) else st.titles[g.key] = name
                    st.save() })
            })
            items += Item("Hide game", "Hidden games disappear from lists. Turn on 'Show hidden games' to see them.",
                value = { if (g.key in st.hidden) "Hidden" else "Shown" }, change = { st.toggleHidden(g.key) })
            items += Item("Emulator for this game", value = { st.gameEmu[g.key]?.let { Launcher.label(app, it) } ?: "Same as system" },
                select = { app.push(EmulatorPicker(app, g.system, g)) }, help = "Overrides the ${g.system.short} emulator for just this game.")
            items += Item("${g.system.short} emulator", value = { Launcher.label(app, Launcher.chosen(app, g.system)) },
                select = { app.push(EmulatorPicker(app, g.system)) }, help = "Applies to every ${g.system.short} game.")
            items += Item("Open with another app", select = { app.pop(); st.startPlay(g.key)
                runCatching { app.ctx.startActivity(Launcher.anyAppIntent(g, null)) } }, help = "Pick any installed app for just this launch.")
        }
        items += viewItems()
        items += Item("Game info", select = { app.push(Message(app, app.title(g), info(g))) })
        if (!kid) items += Item("Delete ROM file", "Permanently deletes the file from your storage.", select = {
            app.push(Confirm(app, "Delete ${g.file.name} permanently? This can't be undone.", yes = "Delete") {
                if (g.file.delete()) { st.forget(g.key); app.notify("Deleted ${app.title(g)}"); app.pop(); app.rescan() }
                else app.notify("Couldn't delete (file is read-only?)")
            })
        })
        app.push(Menu(app, app.title(g), items))
    }

    private fun info(g: Game): String {
        val st = app.state
        return "System: ${g.system.name}\nFile: ${g.file.name}\nFolder: ${g.file.parent}\nSize: ${"%.1f".format(g.file.length() / 1e6)} MB\n" +
            "Play time: ${formatDuration(st.playtime(g.key))}\nLaunches: ${st.launches[g.key] ?: 0}\n" +
            "Rating: ${ListView.stars(st.ratings[g.key] ?: 0)}\nStatus: ${ListView.label(st.status[g.key] ?: "")}\n" +
            (st.notes[g.key]?.let { "Notes: $it\n" } ?: "") +
            "Emulator: ${Launcher.label(app, st.gameEmu[g.key] ?: Launcher.chosen(app, g.system))}\n\n${g.system.demand}. ${g.system.note}"
    }

    override fun draw(c: Canvas, w: Float, h: Float) {
        val tag = listOfNotNull(ListView.label(app.cfg.sort).takeIf { app.cfg.sort != "name" }, ListView.label(app.cfg.filter).takeIf { app.cfg.filter != "all" })
        G.topBar(c, app, th, title + if (tag.isEmpty()) "" else "  (${tag.joinToString(", ")})", w)
        if (games.isEmpty()) {
            val msg = when {
                app.cfg.filter != "all" -> "No games match the filter '${ListView.label(app.cfg.filter)}'. Press START to change it."
                source == "favorites" -> "No favorites yet. Press X on any game to add it."
                source == "recent" || source == "played" -> "Nothing played yet."
                collection != null -> "This collection is empty. Use 'Add to collection' in any game's options."
                else -> "No games found. Put ROMs in ROMs/<system> or add a folder in Settings > Library > ROM folders." }
            G.wrap(msg, 24f, w - 60).forEachIndexed { i, l -> G.text(c, l, w / 2, h / 2 - 24 + i * 28, 24f, th.c("text_dim"), align = 'c') }
            return
        }
        if (grid) drawGrid(c, w, h) else drawList(c, w, h)
    }

    private fun drawList(c: Canvas, w: Float, h: Float) {
        val st = app.state
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
            if (st.isFavorite(g.key)) G.text(c, "*", r.left + 12, r.top + 4, 30f, th.c("warn"), isBold = true)
            val label = (if (system != null) app.title(g) else "${app.title(g)}  [${g.system.short}]") + if (g.key in st.hidden) "  (hidden)" else ""
            val right = st.ratings[g.key]?.let { "★$it" } ?: ""
            G.text(c, label, r.left + 28, r.top + 6, 23f, if (s) th.c("text") else th.c("text_dim"), maxW = r.width() - 80, theme = th)
            if (right.isNotEmpty()) G.text(c, right, r.right - 10, r.top + 7, 19f, th.c("warn"), align = 'r')
        }
        G.scrollbar(c, th, RectF(listW + 18, top, listW + 23, top + visible * rowH - 4), offset, visible, games.size)
        G.text(c, "${sel + 1}/${games.size}", listW + 10, h - 62, 18f, th.c("text_dim"), align = 'r')
        if (detailsW > 0) details(c, RectF(w - detailsW - 14, top, w - 14, h - 46), true)
    }

    private fun drawGrid(c: Canvas, w: Float, h: Float) {
        cols = max(3, (w / 150).toInt())
        val gap = 10f
        val cw = (w - 28 - (cols - 1) * gap) / cols
        val ch = cw * 1.15f
        val top = 52f
        val rowsVisible = max(1, ((h - top - 44) / (ch + gap)).toInt())
        val row = sel / cols
        val firstRow = (offset / cols).let { r -> when { row < r -> row; row >= r + rowsVisible -> row - rowsVisible + 1; else -> r } }
        offset = firstRow * cols
        for (i in offset until min(games.size, offset + rowsVisible * cols)) {
            val g = games[i]
            val rr = (i - offset) / cols; val cc = (i - offset) % cols
            val r = RectF(14 + cc * (cw + gap), top + rr * (ch + gap), 14 + cc * (cw + gap) + cw, top + rr * (ch + gap) + ch)
            val s = i == sel
            G.panel(c, r, th, s)
            app.hit(r) { if (sel == i) handle("a") else sel = i }
            val art = RectF(r.left + 6, r.top + 6, r.right - 6, r.bottom - 30)
            val bmp = G.image(g.image())
            if (bmp != null) G.drawImageFit(c, bmp, art)
            else {
                G.rect(c, art, mix(th.c("panel"), g.system.color, 0.6f), max(0f, th.radius - 4))
                G.text(c, g.system.short, art.centerX(), art.centerY() - 16, 34f, th.c("highlight_text"), align = 'c', isBold = true)
            }
            if (app.state.isFavorite(g.key)) G.text(c, "*", r.right - 16, r.top + 4, 30f, th.c("warn"), isBold = true)
            G.text(c, app.title(g), r.centerX(), r.bottom - 26, 18f, if (s) th.c("text") else th.c("text_dim"), align = 'c', maxW = cw - 10)
        }
    }

    private fun details(c: Canvas, r: RectF, compact: Boolean) {
        val g = current() ?: return
        val tint = if (app.cfg.artTint) G.image(g.image())?.let { G.dominant(it) } else null
        G.panel(c, r, th, fillColor = tint?.let { withAlpha(mix(th.c("panel"), it, 0.3f), G.panelAlpha) })
        var y = r.top + 10
        val bmp = G.image(g.image())
        if (bmp != null) {
            y = G.drawImageFit(c, bmp, RectF(r.left + 10, y, r.right - 10, y + if (compact) r.height() / 2 else r.height() - 170)).bottom + 8
        } else {
            val art = RectF(r.left + 10, y, r.right - 10, y + if (compact) 90 else 140)
            G.rect(c, art, mix(th.c("panel"), g.system.color, 0.6f), max(0f, th.radius - 4))
            G.text(c, g.system.short, art.centerX(), art.centerY() - 18, 44f, th.c("highlight_text"), align = 'c', isBold = true)
            y = art.bottom + 8
        }
        G.text(c, app.title(g), r.centerX(), y, if (compact) 22f else 30f, th.c("text"), align = 'c', isBold = true, maxW = r.width() - 16)
        y += if (compact) 26 else 36
        val st = app.state
        val lines = listOfNotNull("Played ${formatDuration(st.playtime(g.key))}",
            st.ratings[g.key]?.let { ListView.stars(it) }, st.status[g.key]?.let { ListView.label(it) },
            Launcher.label(app, st.gameEmu[g.key] ?: Launcher.chosen(app, g.system)))
        for (line in lines) {
            G.text(c, line, r.centerX(), y, if (compact) 19f else 24f, th.c("text_dim"), align = 'c', maxW = r.width() - 16)
            y += if (compact) 22 else 30
        }
    }

    override fun drawTop(c: Canvas, w: Float, h: Float) {
        G.topBar(c, app, th, title, w)
        if (games.isNotEmpty()) details(c, RectF(20f, 52f, w - 20, h - 12), false)
    }

    override fun hints() = listOf("START" to "Options", "SELECT" to if (grid) "List" else "Grid", "X" to "Fav", "A" to "Play")
}
