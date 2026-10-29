package com.ryzer.os

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The 3.0 Home: four tabs of tall slanted "blades" on the bottom screen, and a big hero card with
 * power bars on the top screen. Content and actions come from the same Home tiles as every layout.
 */
class BladesHome(app: Ryzer) : Screen(app) {
    override val title = "Home"
    /** Set once you move; until then Home stays on the first blade (the library may still be scanning). */
    private var moved = false
    private var tiles = listOf<Tile>()
    private var groups = List(4) { emptyList<Int>() }
    var tab = 0
    private val sel = IntArray(4)
    private var scroll = 0f
    private var t = 0f
    private val kid get() = app.cfg.kidMode

    init { rebuild() }

    /** Library still empty: every tab can be visited, and the empty ones say where games go. */
    private var noGames = false
    /** Picture file per tile and the largest system's game count, worked out once per rebuild rather than per frame. */
    private var artFiles = mapOf<Tile, java.io.File?>()
    private var largestSystem = 0

    fun rebuild() {
        val old = current()?.let { it.kind + it.label }
        tiles = bladeTiles()
        groups = BladeTabs.group(tiles.map { it.kind })
        noGames = app.allGames().isEmpty()
        artFiles = tiles.associateWith { artFile(it) }
        largestSystem = app.systems.maxOfOrNull { app.visible(it.games).size } ?: 0
        for (i in sel.indices) sel[i] = BladeTabs.clampSel(sel[i], groups[i].size)
        if (!moved && sel.any { it != 0 }) moved = true   // a tap picked a blade
        if (!moved) { tab = BladeTabs.startTab(groups, !noGames); sel.fill(0); scroll = 0f; return }
        if (groups[tab].isEmpty() && !noGames) tab = BladeTabs.firstNonEmpty(groups)
        val i = groups[tab].indexOfFirst { tiles[it].kind + tiles[it].label == old }
        if (i >= 0) sel[tab] = i
    }

    /**
     * The Home tiles plus what Blades shows on top: your next recent games on Continue (newest first),
     * and Notifications, Wrapped and Profile on Tools (the 3DS Home has these in its toolbar).
     */
    private fun bladeTiles(): List<Tile> {
        val base = homeTiles(app).toMutableList()
        val st = app.state
        val more = app.visible(st.recent.mapNotNull { Library.find(app.systems, it) }).drop(1).take(4).map { g ->
            Tile("continue", app.title(g), app.title(g), g.system.color, g.system, g,
                note = "Played ${formatDuration(st.playtime(g.key))}" + (st.lastPlayed[g.key]?.let { ", ${agoText(it)}" } ?: "") + ".")
        }
        base.addAll(base.indexOfFirst { it.kind == "continue" } + 1, more)
        base += Tile("notifications", "Alerts", "${Notifs.unread()} new", note = "Your notification centre: banners, do not disturb and everything that came in.")
        base += Tile("wrapped", "Wrapped", "Your year in games", note = "Ryzer Wrapped: total time, top games, best streak and your biggest day.")
        base += Tile("profile", "Profile", app.cfg.userName.ifEmpty { "Set your name" }, note = "Your name, picture and play stats.")
        return base
    }

    private fun open(tile: Tile) = when (tile.kind) {
        "notifications" -> app.push(NotificationsScreen(app))
        "wrapped" -> app.push(WrappedScreen(app))
        "profile" -> if (kid) KidMode.unlock(app) else app.push(profileMenu(app))
        else -> openTile(app, tile)
    }

    override fun onResume() = rebuild()

    /** Opens tab [n] (showcase and tab taps); counts as moving so a rescan doesn't jump back. */
    fun showTab(n: Int) {
        if (n !in groups.indices || groups[n].isEmpty() && !noGames) return
        moved = true; tab = n; sel[n] = BladeTabs.clampSel(sel[n], groups[n].size); scroll = sel[n].toFloat()
    }

    private fun items() = groups.getOrElse(tab) { emptyList() }
    fun current(): Tile? = items().getOrNull(sel[tab])?.let { tiles.getOrNull(it) }

    private fun switchTab(dir: Int) {
        val n = BladeTabs.next(groups, tab, dir, allowEmpty = noGames)
        if (n != tab) { showTab(n); app.sounds.play("move") }
    }

    override fun handle(a: String) {
        moved = true
        val n = items().size
        when (a) {
            "left" -> if (sel[tab] > 0) sel[tab]-- else return
            "right" -> if (sel[tab] < n - 1) sel[tab]++ else return
            "l" -> { switchTab(-1); return }
            "r" -> { switchTab(1); return }
            "a" -> { current()?.let { app.sounds.play("select"); open(it) }; return }
            "x" -> { app.openRandom(); return }
            "y" -> { app.openSearch(); return }
            "start" -> { if (kid) KidMode.unlock(app) else app.push(settingsMenu(app)); return }
            "select" -> { if (!kid) app.push(ThemePicker(app)); return }
            else -> return
        }
        app.sounds.play("move")
    }

    override fun update(dt: Float) { t += dt }

    private val still get() = !app.animationsOn

    // ----- art -----

    /** Box art for game tiles; for systems RyzerOS/tiles/<system>, else the cover of that system's most-played game. */
    private fun artFile(tile: Tile): java.io.File? {
        tile.game?.let { if (tile.kind == "pinned" || tile.kind == "gotd" || tile.kind == "continue") return it.image() }
        val s = tile.system?.takeIf { tile.kind == "system" } ?: return null
        val dir = java.io.File(app.home, "tiles")
        listOf("png", "jpg", "webp").map { java.io.File(dir, "${s.id}.$it") }.firstOrNull { it.isFile }?.let { return it }
        return app.visible(s.games).maxByOrNull { app.state.playtime(it.key) }?.takeIf { app.state.playtime(it.key) > 0 }?.image()
    }

    private fun art(tile: Tile): Bitmap? = artFiles[tile]?.let { G.image(it) }

    private val blurCache = object : LinkedHashMap<Bitmap, Bitmap>(8, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<Bitmap, Bitmap>) = size > 8
    }

    /** A cheap, cached blur: shrink to a few dozen pixels, then let filtering smear it back up. */
    private fun blurred(src: Bitmap): Bitmap = blurCache.getOrPut(src) {
        val w = 24; val h = max(8, (24f * src.height / src.width).toInt())
        Bitmap.createScaledBitmap(Bitmap.createScaledBitmap(src, w, h, true), w * 4, h * 4, true)
    }

    private val filter = Paint(Paint.FILTER_BITMAP_FLAG)

    private fun fillCover(c: Canvas, bmp: Bitmap, r: RectF) {
        val sc = max(r.width() / bmp.width, r.height() / bmp.height)
        val dw = bmp.width * sc; val dh = bmp.height * sc
        c.drawBitmap(bmp, null, RectF(r.centerX() - dw / 2, r.centerY() - dh / 2, r.centerX() + dw / 2, r.centerY() + dh / 2), filter)
    }

    private fun bandColor(tile: Tile) = if (tile.kind == "system") tile.color ?: th.c("accent") else th.c("accent")

    private fun bannerTitle(tile: Tile) = when (tile.kind) {
        "system" -> tile.system!!.name
        "continue", "gotd" -> tile.sub
        else -> tile.label
    }

    // ----- bottom screen -----

    override fun draw(c: Canvas, w: Float, h: Float) {
        val tile = current()
        if (app.cfg.systemAccent) app.accentOverride = tile?.system?.color
        drawTabs(c, w)
        val topVisible = SystemClock.uptimeMillis() - app.topShownAt < 600
        var rowTop = 58f
        var scale = 1f
        if (!topVisible && tile != null) { compactHero(c, tile, RectF(16f, 52f, w - 16, 112f)); rowTop = 122f; scale = 0.74f }
        if (items().isEmpty()) {
            Skin.text(c, "NO GAMES YET", w / 2, rowTop + 90, 34f, th.c("text"), align = 'c')
            G.text(c, "Put ROMs in ROMs/<system>, or add a folder in Settings > Library.", w / 2, rowTop + 136, 20f, th.c("text"), align = 'c', maxW = w - 40, theme = th)
            return
        }
        drawRow(c, w, rowTop, scale)
        val y = rowTop + 290 * scale + 10
        tile?.let {
            Skin.text(c, bannerTitle(it).uppercase(), w / 2, y, if (scale < 1) 26f else 30f, th.c("text"), align = 'c', maxW = w - 40)
            G.text(c, it.note.ifEmpty { it.sub }, w / 2, y + if (scale < 1) 26 else 32, 19f, th.c("text_dim"), align = 'c', maxW = w - 60, theme = th)
        }
    }

    private fun drawTabs(c: Canvas, w: Float) {
        val tw = (w - 40) / 4
        BladeTabs.NAMES.forEachIndexed { i, name ->
            val r = RectF(20 + i * tw + 4, 8f, 20 + (i + 1) * tw - 4, 40f)
            val on = i == tab
            val empty = groups[i].isEmpty()
            if (on) Skin.glow(c, r, withAlpha(th.c("accent"), 0.5f), 12f)
            Skin.parallelogram(c, r, if (on) th.c("accent") else withAlpha(th.c("panel"), 0.75f * G.panelAlpha), 12f)
            Skin.text(c, name, r.centerX(), r.top + 5, 20f,
                when { on -> th.c("highlight_text"); empty -> withAlpha(th.c("text_dim"), 0.4f); else -> th.c("text_dim") }, align = 'c', maxW = r.width() - 16)
            if (!empty || noGames) app.hit(r) { if (tab != i) app.sounds.play("move"); showTab(i) }
        }
    }

    private fun drawRow(c: Canvas, w: Float, top: Float, scale: Float) {
        val list = items()
        val s = sel[tab]
        scroll = if (still) s.toFloat() else scroll + (s - scroll) * 0.25f
        val uw = 92f * scale; val uh = 236f * scale
        val sw = 150f * scale; val sh = 290f * scale
        val gap = 12f * scale
        val skew = 26f * scale
        val cx = w / 2
        val baseY = top + sh
        // Unselected first, selected last so its glow sits on top
        val order = list.indices.sortedBy { if (it == s) 1 else 0 }
        for (i in order) {
            val d = i - scroll
            val grow = (1 - abs(d)).coerceIn(0f, 1f)
            val bw = uw + (sw - uw) * grow; val bh = uh + (sh - uh) * grow
            val x = cx + d * (uw + gap) + d.coerceIn(-1f, 1f) * (sw - uw) / 2
            if (x + bw < -skew || x - bw > w + skew) continue
            val r = RectF(x - bw / 2, baseY - bh, x + bw / 2, baseY)
            drawBlade(c, tiles[list[i]], r, skew, i == s)
            app.hit(r) { moved = true; if (sel[tab] == i) handle("a") else { sel[tab] = i; app.sounds.play("move") } }
        }
    }

    private fun drawBlade(c: Canvas, tile: Tile, r: RectF, skew: Float, selected: Boolean) {
        if (selected) {
            val pulse = if (still) 0.6f else 0.5f + 0.15f * sin(t * 3)
            Skin.glow(c, r, withAlpha(th.c("accent"), pulse), skew)
        }
        c.save(); Skin.clip(c, r, skew)
        val fill = mix(th.c("panel"), tile.color ?: mix(th.c("panel"), th.c("accent"), 0.35f), 0.7f)
        G.rect(c, r, fill)
        val bmp = art(tile)
        if (bmp != null) fillCover(c, bmp, r)
        else glyph(c, tile, r.centerX(), r.centerY(), r.width(), ink(fill))
        if (!selected) G.rect(c, r, withAlpha(0xFF000000.toInt(), 0.45f))
        // Name band along the bottom
        val bandH = if (selected) 34f else 26f
        val band = RectF(r.left - skew, r.bottom - bandH, r.right + skew, r.bottom)
        G.rect(c, band, withAlpha(bandColor(tile), if (selected) 0.95f else 0.7f))
        if (!still && selected) {   // glint sliding down the blade
            val gy = r.top + ((t * 160) % (r.height() + 200)) - 100
            G.rect(c, RectF(r.left - skew, gy, r.right + skew, gy + 26), withAlpha(0xFFFFFFFF.toInt(), 0.07f))
        }
        c.restore()
        val labelSize = if (selected) 20f else 15f
        // The shape's bottom edge runs from r.left to r.right - skew, so the band's middle sits left of r's
        val bandMid = r.centerX() - skew / 2 + skew * (bandH / 2) / r.height()
        Skin.text(c, tile.label.uppercase(), bandMid, band.top + (bandH - labelSize * 0.9f) / 2,
            labelSize, th.c("highlight_text"), align = 'c', maxW = r.width() - skew - 10)
        Skin.outlinePara(c, r, if (selected) th.c("accent") else withAlpha(th.c("panel_border"), 0.8f), if (selected) 3f else 1f, skew)
        if (tile.kind == "pinned") Skin.text(c, "PIN", r.right - 6, r.top + 6, 14f, th.c("highlight_text"), align = 'r')
    }

    /** Readable ink on [fill]: dark on light fills (light themes), the theme's highlight colour otherwise. */
    private fun ink(fill: Int) = if (SkinMath.isLight(fill)) 0xFF15151C.toInt() else th.c("highlight_text")

    /** What stands in for missing art: the system's short name, the tile's icon, or its initial. */
    private fun glyph(c: Canvas, tile: Tile, cx: Float, cy: Float, size: Float, color: Int) = when {
        tile.kind == "system" || tile.system != null && tile.kind != "continue" ->
            Skin.text(c, tile.system?.short ?: tile.label, cx, cy - size * 0.22f, size * 0.42f, color, align = 'c', maxW = size - 8)
        tile.kind in setOf("notifications", "wrapped", "profile") ->
            Skin.text(c, tile.label.take(1), cx, cy - size * 0.3f, size * 0.6f, color, align = 'c')
        else -> drawIcon(c, tile.kind, cx, cy - 10, size * 0.2f, color)
    }

    /** One-screen devices: the hero squeezed into a strip above the blades. */
    private fun compactHero(c: Canvas, tile: Tile, r: RectF) {
        G.panel(c, r, th)
        val art = RectF(r.left + 14, r.top + 6, r.left + 60, r.bottom - 6)
        art(tile)?.let { c.save(); Skin.clip(c, art, 10f); fillCover(c, it, art); c.restore() }
            ?: Skin.parallelogram(c, art, bandColor(tile), 10f)
        Skin.text(c, bannerTitle(tile), r.left + 72, r.top + 6, 26f, th.c("text"), maxW = r.width() - 90)
        G.text(c, tile.note, r.left + 72, r.top + 36, 18f, th.c("text_dim"), maxW = r.width() - 90)
    }

    // ----- top screen -----

    override fun drawTop(c: Canvas, w: Float, h: Float) {
        val tile = current()
        val bmp = tile?.let { art(it) }
        if (bmp != null) {
            fillCover(c, blurred(bmp), RectF(0f, 0f, w, h))
            G.rect(c, RectF(0f, 0f, w, h), withAlpha(th.c("bg_bottom"), 0.62f))
        }
        // dark diagonal wedge behind the text side
        Skin.parallelogram(c, RectF(w * 0.36f, 0f, w + 120, h), withAlpha(th.c("bg_bottom"), 0.5f), 120f)
        drawHud(c, w)
        if (tile == null) {
            Skin.text(c, "RYZER OS", w / 2, h / 2 - 40, 60f, th.c("text"), align = 'c')
            return
        }
        val float = if (still) 0f else sin(t * 1.3f) * 3
        // Slanted cover card
        val card = RectF(40f, 70f + float, 40f + min(220f, w * 0.34f), h - 96 + float)
        val skew = 26f
        Skin.glow(c, card, withAlpha(tile.color ?: th.c("accent"), 0.7f), skew)
        c.save(); Skin.clip(c, card, skew)
        val cardFill = mix(th.c("panel"), tile.color ?: th.c("accent"), 0.7f)
        G.rect(c, card, cardFill)
        if (bmp != null) fillCover(c, bmp, card) else glyph(c, tile, card.centerX(), card.centerY(), card.width(), ink(cardFill))
        c.restore()
        Skin.outlinePara(c, card, th.c("accent"), 3f, skew)

        val tx = card.right + 34
        val tw = w - tx - 24
        var y = 76f
        // System chip
        (tile.system?.name ?: BladeTabs.NAMES[tab]).uppercase().let { chip ->
            val cw = min(tw, Skin.measure(chip, 18f) + 30)
            Skin.parallelogram(c, RectF(tx, y, tx + cw, y + 26), tile.system?.color ?: th.c("accent"), 10f)
            Skin.text(c, chip, tx + cw / 2, y + 3, 18f, th.c("highlight_text"), align = 'c', maxW = cw - 16)
        }
        y += 38
        // Two lines at most; a longer title keeps the rest on line two, cut with "..."
        val titleLines = SkinMath.twoLines(bannerTitle(tile).uppercase(), tw) { Skin.measure(it, 46f) }
        titleLines.forEach { l -> Skin.text(c, l, tx, y, 46f, th.c("text"), maxW = tw); y += 42 }
        y += 8
        val bars = powerBars(tile)
        if (bars.isEmpty()) {
            G.wrap(tile.note, 20f, tw).take(4).forEach { l -> G.text(c, l, tx, y, 20f, th.c("text_dim"), theme = th); y += 24 }
        } else for ((label, value, on) in bars) {
            Skin.text(c, label, tx, y, 17f, th.c("text"))
            G.text(c, value, tx + tw, y, 17f, th.c("text"), align = 'r', theme = th)
            Skin.segments(c, tx, y + 22, tw, 12f, on, 16, th.c("accent"), withAlpha(th.c("text_dim"), 0.22f))
            y += 46
        }
        // Bottom band: resume line, else the newest notification, else a tip
        val band = RectF(-20f, h - 62, w + 20, h - 26)
        Skin.parallelogram(c, band, withAlpha(th.c("bg_bottom"), 0.75f), 14f)
        val line = when {
            tile.kind == "continue" -> "A  RESUME  ·  ${tile.note}"
            Notifs.list.isNotEmpty() -> Notifs.list.first().let { "${it.app}: ${it.title}" }
            else -> tips[((t / 8).toInt()) % tips.size]
        }
        G.text(c, line, w / 2, band.top + 8, 19f, th.c("text"), align = 'c', maxW = w - 50, theme = th)
    }

    private fun drawHud(c: Canvas, w: Float) {
        G.rect(c, RectF(0f, 0f, w, 44f), withAlpha(th.c("bg_bottom"), 0.55f))
        drawAvatar(c, app, th, 26f, 22f, 15f)
        val gr = Skin.text(c, greeting(app), 50f, 9f, 24f, th.c("text"), maxW = w * 0.45f)
        drawGoalRing(c, app, th, gr.right + 22, 22f, 13f)
        val now = java.util.Date()
        val clock = java.text.SimpleDateFormat(if (app.cfg.clock24) "HH:mm" else "h:mm a", java.util.Locale.getDefault()).format(now)
        var x = Skin.text(c, clock, w - 14, 8f, 26f, th.c("text"), align = 'r').left - 14
        if (app.cfg.showBattery) {
            val (pct, chg) = app.battery()
            if (pct >= 0) x = G.batterySegments(c, th, x, 16f, pct, chg) - 12
        }
        val n = Notifs.unread()
        if (n > 0) Skin.text(c, "$n NEW", x, 12f, 19f, th.c("accent"), align = 'r')
        G.rect(c, RectF(0f, 43f, w, 45f), withAlpha(th.c("accent"), 0.5f))
    }

    private data class Bar(val label: String, val value: String, val on: Int)

    /** Two meters for games (play time, rating) and systems (library size, played). Other tiles get none. */
    private fun powerBars(tile: Tile): List<Bar> {
        val st = app.state
        tile.game?.let { g ->
            val played = st.playtime(g.key)
            val most = st.times.values.maxOrNull() ?: 0L
            val rating = st.ratings[g.key] ?: 0
            return listOf(Bar("PLAY TIME", formatDuration(played), SkinMath.segmentsOn(played.toDouble(), most.toDouble())),
                Bar("RATING", if (rating > 0) "$rating / 5" else "Not rated", SkinMath.segmentsOn(rating.toDouble(), 5.0)))
        }
        val s = tile.system?.takeIf { tile.kind == "system" } ?: return emptyList()
        val games = app.visible(s.games)
        val largest = largestSystem
        val played = games.count { st.playtime(it.key) > 0 }
        return listOf(Bar("GAMES", "${games.size}", SkinMath.segmentsOn(games.size.toDouble(), largest.toDouble())),
            Bar("PLAYED", "$played / ${games.size}", SkinMath.segmentsOn(played.toDouble(), games.size.toDouble())))
    }

    private val tips = listOf(
        "Tip: L / R switch tabs",
        "Tip: press L3 or R3 (or your swap button) to swap the screens",
        "Tip: L + R together opens the quick menu from anywhere",
        "Tip: pin your favourite games to Home from their options (START)",
        "Tip: Settings > Look & feel > Interface style brings back the Classic look",
    )

    override fun hints() = if (kid) listOf("L/R" to "Tabs", "START" to "Unlock", "X" to "Random", "A" to "Open")
        else listOf("L/R" to "Tabs", "SELECT" to "Themes", "X" to "Random", "Y" to "Search", "A" to "Open")
}
