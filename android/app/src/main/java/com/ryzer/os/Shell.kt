package com.ryzer.os

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.os.SystemClock
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Picture for a Home tile: box art for games, RyzerOS/tiles/<system>.png for systems. */
fun tileArt(app: Ryzer, tile: Tile): Bitmap? {
    tile.game?.let { if (tile.kind == "pinned" || tile.kind == "gotd" || tile.kind == "continue") return G.image(it.image()) }
    val s = tile.system ?: return null
    if (tile.kind != "system") return null
    val dir = File(app.home, "tiles")
    return listOf("png", "jpg", "webp").firstNotNullOfOrNull { ext -> File(dir, "${s.id}.$ext").takeIf { it.isFile } }?.let { G.image(it) }
}

fun profilePicture(app: Ryzer): Bitmap? = app.cfg.avatar.takeIf { it.isNotEmpty() }?.let { G.image(File(app.home, it)) }

/** Round profile badge: your picture, or your initial on the accent colour. */
fun drawAvatar(c: Canvas, app: Ryzer, th: Theme, cx: Float, cy: Float, r: Float) {
    val pic = profilePicture(app)
    if (pic != null) {
        c.save()
        val path = android.graphics.Path().apply { addCircle(cx, cy, r, android.graphics.Path.Direction.CW) }
        c.clipPath(path)
        val sc = max(2 * r / pic.width, 2 * r / pic.height)
        c.drawBitmap(pic, null, RectF(cx - pic.width * sc / 2, cy - pic.height * sc / 2, cx + pic.width * sc / 2, cy + pic.height * sc / 2),
            G.bitmapPaint)
        c.restore()
    } else {
        G.circle(c, cx, cy, r, th.c("accent"))
        val initial = app.cfg.userName.trim().take(1).uppercase().ifEmpty { "R" }
        G.text(c, initial, cx, cy - r * 0.62f, r * 1.5f, th.c("highlight_text"), align = 'c', isBold = true)
    }
    G.ring(c, cx, cy, r, th.c("text"), 2f)
}

/**
 * The 2.0 Home, inspired by the 3DS HOME Menu: the top screen shows a big banner for the
 * selected item, the bottom screen a toolbar and a sideways-scrolling grid of icons.
 * Ryzer's own touches (glow, tile bands, themes) stay.
 */
class HomeShell(app: Ryzer) : Screen(app) {
    /** Set once you move the selection; until then Home stays on its first tile. */
    private var moved = false
    override val title = "Home"
    private var tiles = listOf<Tile>()
    private var sel = 0
    private var scroll = 0f              // in columns, animated
    private var t = 0f
    private var onToolbar = false
    private var tool = 0
    private val kid get() = app.cfg.kidMode

    private class Tool(val kind: String, val label: String, val run: () -> Unit)

    private fun tools(): List<Tool> = buildList {
        if (!kid) add(Tool("settings", "Settings") { app.push(settingsMenu(app)) })
        if (!kid) add(Tool("themes", "Theme Studio") { app.push(ThemePicker(app)) })
        add(Tool("notifications", "Notifications") { app.push(NotificationsScreen(app)) })
        add(Tool("stats", "Stats") { app.push(StatsScreen(app)) })
        add(Tool("search", "Search") { app.openSearch() })
        add(Tool("swap", "Swap screens") { app.ctx.toggleSwap() })
        add(Tool("quick", "Quick menu") { app.push(QuickMenu(app)) })
        add(Tool("tools", "Tools & games") { app.push(toolsMenu(app)) })
        add(Tool("profile", "Profile") { if (kid) KidMode.unlock(app) else app.push(profileMenu(app)) })
    }

    init { rebuild() }

    fun rebuild() {
        val old = tiles.getOrNull(sel)?.let { it.kind + it.label }
        tiles = homeTiles(app)
        // Until you press something, stay on the first tile: Home is first built before the
        // library scan finishes, and Continue only appears once it has.
        if (!moved && sel != 0) moved = true   // a tap picked a tile
        if (!moved) { sel = 0; return }
        val i = tiles.indexOfFirst { it.kind + it.label == old }
        sel = if (i >= 0) i else sel.coerceIn(0, max(0, tiles.size - 1))
    }

    override fun onResume() = rebuild()

    private val rows get() = app.cfg.iconRows.coerceIn(2, 4)

    override fun handle(a: String) {
        moved = true
        val n = tiles.size
        val r = rows
        if (onToolbar) {
            val tl = tools()
            when (a) {
                "left" -> tool = (tool - 1 + tl.size) % tl.size
                "right" -> tool = (tool + 1) % tl.size
                "down", "b" -> onToolbar = false
                "a" -> { app.sounds.play("select"); tl.getOrNull(tool)?.run?.invoke(); return }
                else -> return
            }
            app.sounds.play("move")
            return
        }
        when (a) {
            "up" -> if (sel % r == 0) { onToolbar = true; tool = tool.coerceIn(0, tools().size - 1) } else sel -= 1
            "down" -> if (sel % r < r - 1 && sel + 1 < n) sel += 1
            "left" -> if (sel - r >= 0) sel -= r
            "right" -> sel = min(n - 1, sel + r)
            "l" -> sel = max(0, sel - r * 4)
            "r" -> sel = min(n - 1, sel + r * 4)
            "a" -> { app.sounds.play("select"); tiles.getOrNull(sel)?.let { openTile(app, it) }; return }
            "x" -> { app.openRandom(); return }
            "y" -> { app.openSearch(); return }
            "start" -> { if (kid) KidMode.unlock(app) else app.push(settingsMenu(app)); return }
            "select" -> { if (!kid) app.push(ThemePicker(app)); return }
            else -> return
        }
        app.sounds.play("move")
    }

    override fun update(dt: Float) { t += dt }

    // ----- bottom screen: toolbar + icon grid -----

    override fun draw(c: Canvas, w: Float, h: Float) {
        val tile = tiles.getOrNull(sel)
        if (app.cfg.systemAccent) app.accentOverride = tile?.system?.color
        drawToolbar(c, w)
        val topVisible = SystemClock.uptimeMillis() - app.topShownAt < 600
        val gridTop = if (topVisible) 62f else 128f
        if (!topVisible && tile != null) compactBanner(c, tile, RectF(16f, 58f, w - 16, 120f))
        drawGrid(c, w, gridTop, h - 54)
    }

    private fun drawToolbar(c: Canvas, w: Float) {
        G.rect(c, RectF(0f, 0f, w, 50f), withAlpha(th.c("panel"), 0.85f * G.panelAlpha))
        G.line(c, 0f, 50f, w, 50f, th.c("panel_border"))
        val tl = tools()
        val size = 38f
        val gap = 10f
        var x = 12f
        tl.forEachIndexed { i, tool ->
            val r = RectF(x, 6f, x + size, 6f + size)
            val selected = onToolbar && i == this.tool
            G.circle(c, r.centerX(), r.centerY(), size / 2, if (selected) th.c("accent") else mix(th.c("panel"), th.c("text"), 0.12f))
            if (selected) G.ring(c, r.centerX(), r.centerY(), size / 2 + 3, th.c("accent2"), 2f)
            val ic = if (selected) th.c("highlight_text") else th.c("text")
            when (tool.kind) {
                "notifications" -> {
                    G.ring(c, r.centerX(), r.centerY() - 2, 9f, ic, 3f)
                    G.line(c, r.centerX() - 10, r.centerY() + 8, r.centerX() + 10, r.centerY() + 8, ic, 3f)
                    val n = Notifs.unread()
                    if (n > 0) {
                        G.circle(c, r.right - 4, r.top + 6, 9f, th.c("bad"))
                        G.text(c, if (n > 9) "9+" else "$n", r.right - 4, r.top - 1, 15f, 0xFFFFFFFF.toInt(), align = 'c', isBold = true)
                    }
                }
                "swap" -> {
                    G.line(c, r.left + 10, r.centerY() - 5, r.right - 10, r.centerY() - 5, ic, 3f)
                    G.line(c, r.right - 14, r.centerY() - 10, r.right - 10, r.centerY() - 5, ic, 3f)
                    G.line(c, r.left + 10, r.centerY() + 6, r.right - 10, r.centerY() + 6, ic, 3f)
                    G.line(c, r.left + 14, r.centerY() + 11, r.left + 10, r.centerY() + 6, ic, 3f)
                }
                "quick" -> for (k in 0 until 3) G.line(c, r.left + 11, r.top + 13 + k * 6, r.right - 11, r.top + 13 + k * 6, ic, 3f)
                "profile" -> drawAvatar(c, app, th, r.centerX(), r.centerY(), size / 2 - 3)
                else -> drawIcon(c, tool.kind, r.centerX(), r.centerY(), 11f, ic)
            }
            app.hit(r) { this.tool = i; onToolbar = true; tool.run() }
            x += size + gap
        }
        if (onToolbar) G.text(c, tl.getOrNull(tool)?.label ?: "", w - 14, 8f, 20f, th.c("accent"), align = 'r', isBold = true)
        else {
            val clock = TimeText.clock(h24 = app.cfg.clock24, amPm = false)
            G.text(c, clock, w - 14, 9f, 26f, th.c("text"), align = 'r', isBold = true)
            val (bat, chg) = app.battery()
            if (bat >= 0) G.text(c, "$bat%" + if (chg) "+" else "", w - 14, 33f, 14f, th.c("text_dim"), align = 'r')
        }
    }

    private fun compactBanner(c: Canvas, tile: Tile, r: RectF) {
        G.panel(c, r, th)
        val art = tileArt(app, tile)
        if (art != null) G.drawImageFit(c, art, RectF(r.left + 6, r.top + 6, r.left + 56, r.bottom - 6))
        G.text(c, bannerTitle(tile), r.left + 66, r.top + 6, 26f, th.c("text"), isBold = true, maxW = r.width() - 80)
        G.text(c, tile.note, r.left + 66, r.top + 36, 18f, th.c("text_dim"), maxW = r.width() - 80)
    }

    private fun bannerTitle(tile: Tile) = when (tile.kind) {
        "system" -> tile.system!!.name
        "continue", "gotd" -> tile.sub
        else -> tile.label
    }

    private fun drawGrid(c: Canvas, w: Float, top: Float, bottom: Float) {
        val r = rows
        val gap = 14f
        val cell = min((bottom - top - 24 - (r - 1) * gap) / r, 132f)
        val colW = cell + gap
        val visibleCols = max(1, ((w - 40) / colW).toInt())
        val selCol = sel / r
        // Keep the selected column on screen, sliding smoothly like the 3DS menu.
        val target = (selCol - visibleCols / 2).toFloat().coerceIn(0f, max(0f, ((tiles.size - 1) / r - visibleCols + 1).toFloat()))
        scroll = if (app.animationsOn) scroll + (target - scroll) * 0.25f else target
        val gridH = r * cell + (r - 1) * gap
        val y0 = top + (bottom - top - 24 - gridH) / 2
        val x0 = (w - visibleCols * colW + gap) / 2
        tiles.forEachIndexed { i, tile ->
            val col = i / r; val row = i % r
            val x = x0 + (col - scroll) * colW
            if (x < -colW || x > w + colW) return@forEachIndexed
            val selected = i == sel && !onToolbar
            val bob = if (selected && app.animationsOn) sin(t * 3) * 2 else 0f
            val zoom = if (selected) 1.12f else 1f
            val cx = x + cell / 2; val cy = y0 + row * (cell + gap) + cell / 2 + bob
            val half = cell * zoom / 2
            val rect = RectF(cx - half, cy - half, cx + half, cy + half)
            drawIconTile(c, tile, rect, selected)
            app.hit(rect) { if (sel == i && !onToolbar) handle("a") else { sel = i; onToolbar = false; app.sounds.play("move") } }
        }
        // 3DS-style scroll bar
        val cols = (tiles.size + r - 1) / r
        if (cols > visibleCols) {
            val bar = RectF(40f, bottom - 12, w - 40, bottom - 6)
            G.rect(c, bar, withAlpha(th.c("panel"), 0.9f), 3f)
            val frac = visibleCols.toFloat() / cols
            val pos = (scroll / max(1f, (cols - visibleCols).toFloat())).coerceIn(0f, 1f)
            val tw = bar.width() * frac
            G.rect(c, RectF(bar.left + (bar.width() - tw) * pos, bar.top, bar.left + (bar.width() - tw) * pos + tw, bar.bottom), th.c("accent"), 3f)
        }
    }

    /** Outline for the "circle" and "hexagon" icon shapes. */
    private fun shapePath(r: RectF, shape: String) = android.graphics.Path().apply {
        if (shape == "circle") addCircle(r.centerX(), r.centerY(), r.width() / 2, android.graphics.Path.Direction.CW)
        else {
            for (k in 0 until 6) {
                val a = Math.toRadians(30.0 + k * 60)
                val x = r.centerX() + r.width() / 2 * Math.cos(a).toFloat(); val y = r.centerY() + r.height() / 2 * Math.sin(a).toFloat()
                if (k == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
    }

    private fun drawIconTile(c: Canvas, tile: Tile, r: RectF, selected: Boolean) {
        val shape = app.cfg.iconShape
        if (shape == "circle" || shape == "hexagon") {
            val path = shapePath(r, shape)
            c.save(); c.translate(3f, 5f); G.path(c, path, 0x40000000); c.restore()   // drop shadow
            if (selected) { c.save(); c.scale(1.08f, 1.08f, r.centerX(), r.centerY()); G.path(c, path, withAlpha(th.c("accent"), 0.3f + 0.2f * (sin(t * 4) + 1) / 2)); c.restore() }
            c.save(); c.clipPath(path)
            drawIconBody(c, tile, r, 0f)
            c.restore()
            G.path(c, path, if (selected) th.c("accent") else withAlpha(th.c("panel_border"), 0.8f), filled = false, w = if (selected) 3f else 1f)
            if (tile.kind == "pinned") G.text(c, "PIN", r.centerX(), r.top + 6, 14f, th.c("highlight_text"), align = 'c', isBold = true)
            return
        }
        val rad = if (shape == "square") 2f else max(8f, th.radius)
        G.rect(c, RectF(r.left + 3, r.top + 5, r.right + 3, r.bottom + 5), 0x40000000, rad)   // drop shadow
        if (selected) {
            // Blades panels are slanted; these icons are square, so give them a square glow and outline instead
            if (Skin.blades) { Skin.glow(c, r, withAlpha(th.c("accent"), 0.45f + 0.2f * (sin(t * 4) + 1) / 2), 0f); G.rect(c, r, th.c("panel"), rad) }
            else G.panel(c, r, th, true, (sin(t * 4) + 1) / 2)
        }
        drawIconBody(c, tile, r, rad)
        G.outline(c, r, if (selected) th.c("accent") else withAlpha(th.c("panel_border"), 0.8f), if (selected) 3f else 1f, rad)
        if (tile.kind == "pinned") G.text(c, "PIN", r.right - 6, r.top + 4, 14f, th.c("highlight_text"), align = 'r', isBold = true)
    }

    private fun drawIconBody(c: Canvas, tile: Tile, r: RectF, rad: Float) {
        val base = tile.color ?: mix(th.c("panel"), th.c("accent"), 0.35f)
        G.rect(c, r, mix(th.c("panel"), base, 0.7f), rad)
        val art = tileArt(app, tile)
        val ic = th.c("highlight_text")
        when {
            art != null -> { c.save(); c.clipRect(RectF(r.left + 3, r.top + 3, r.right - 3, r.bottom - 3)); G.drawImageFit(c, art, RectF(r.left + 3, r.top + 3, r.right - 3, r.bottom - 3)); c.restore() }
            tile.kind == "system" && tile.system != null -> {
                val side = r.height() * 0.66f
                SystemIcons.draw(c, tile.system.id, tile.system.color, RectF(r.centerX() - side / 2, r.top + 2, r.centerX() + side / 2, r.top + 2 + side))
            }
            tile.kind == "system" || tile.kind == "pinned" || tile.kind == "gotd" ->
                G.text(c, tile.system?.short ?: tile.label, r.centerX(), r.centerY() - r.height() * 0.17f, r.height() * 0.32f, ic, align = 'c', isBold = true, maxW = r.width() - 8)
            else -> drawIcon(c, tile.kind, r.centerX(), r.centerY() - 6, r.height() * 0.2f, ic)
        }
        // Ryzer touch: a coloured band with the name under the icon
        val band = RectF(r.left, r.bottom - r.height() * 0.24f, r.right, r.bottom)
        G.rect(c, band, withAlpha(th.c("bg_bottom"), 0.72f), rad)
        G.text(c, tile.label, band.centerX(), band.top + 3, min(18f, r.height() * 0.15f), th.c("text"), align = 'c', maxW = r.width() - 8)
    }

    // ----- top screen: status bar + 3D-style banner -----

    override fun drawTop(c: Canvas, w: Float, h: Float) {
        val tile = tiles.getOrNull(sel) ?: return
        // Status bar
        drawAvatar(c, app, th, 26f, 22f, 15f)
        val gr = G.text(c, greeting(app), 50f, 10f, 22f, th.c("text"), isBold = true, maxW = w * 0.45f)
        drawGoalRing(c, app, th, gr.right + 22, 22f, 13f)
        val now = java.util.Date()
        val clock = TimeText.clock(now.time, app.cfg.clock24)
        val date = TimeText.format(now.time, "EEE MMM d")
        val rr = G.text(c, clock, w - 14, 9f, 24f, th.c("text"), align = 'r', isBold = true)
        G.text(c, date, rr.left - 12, 12f, 19f, th.c("text_dim"), align = 'r')
        val n = Notifs.unread()
        if (n > 0) G.text(c, "$n new", rr.left - 110, 12f, 19f, th.c("accent"), align = 'r', isBold = true)
        G.line(c, 12f, 42f, w - 12, 42f, th.c("panel_border"))

        // Banner with a gentle floating "3D" effect: layered shadow + parallax + glint
        val float = if (app.animationsOn) sin(t * 1.3f) * 4 else 0f
        val card = RectF(30f, 64f + float, w - 30, h - 70 + float)
        for (k in 3 downTo 1) G.rect(c, RectF(card.left + k * 3, card.top + k * 4, card.right + k * 3, card.bottom + k * 4), withAlpha(0xFF000000.toInt(), 0.10f), th.radius + 6)
        val tint = tileArt(app, tile)?.takeIf { app.cfg.artTint }?.let { G.dominant(it) } ?: tile.color ?: th.c("accent")
        G.rect(c, card, withAlpha(mix(th.c("panel"), tint, 0.35f), max(0.85f, G.panelAlpha)), th.radius + 6)
        G.outline(c, card, th.c("accent"), 2f, th.radius + 6)
        val art = tileArt(app, tile)
        val artBox = RectF(card.left + 18, card.top + 18, card.left + 18 + card.height() - 36, card.bottom - 18)
        if (art != null) G.drawImageFit(c, art, RectF(artBox.left - float * 0.5f, artBox.top, artBox.right - float * 0.5f, artBox.bottom))
        else {
            G.rect(c, artBox, mix(th.c("panel"), tile.color ?: th.c("accent"), 0.75f), th.radius)
            if (tile.kind == "system" || tile.system != null) G.text(c, tile.system?.short ?: "", artBox.centerX(), artBox.centerY() - 34, 64f, th.c("highlight_text"), align = 'c', isBold = true)
            else drawIcon(c, tile.kind, artBox.centerX(), artBox.centerY(), 40f, th.c("highlight_text"))
        }
        val tx = artBox.right + 22
        val tw = card.right - tx - 18
        G.wrap(bannerTitle(tile), 40f, tw).take(2).forEachIndexed { i, l -> G.text(c, l, tx + float * 0.3f, card.top + 22 + i * 40, 40f, th.c("text"), isBold = true) }
        G.text(c, tile.sub, tx, card.top + 106, 22f, th.c("accent"), isBold = true, maxW = tw)
        G.wrap(tile.note, 19f, tw).take(5).forEachIndexed { i, l -> G.text(c, l, tx, card.top + 138 + i * 22, 19f, th.c("text_dim")) }
        // glint sweeping across the card
        if (app.animationsOn) {
            val gx = card.left + ((t * 120) % (card.width() + 300)) - 150
            c.save(); c.clipRect(card)
            G.rect(c, RectF(gx, card.top, gx + 40, card.bottom), withAlpha(0xFFFFFFFF.toInt(), 0.06f))
            c.restore()
        }
        // Ticker: newest notification, or a tip
        val ticker = Notifs.list.firstOrNull()?.let { "${it.app}: ${it.title}" } ?: tips[((t / 8).toInt()) % tips.size]
        G.text(c, ticker, w / 2, h - 52, 19f, th.c("text_dim"), align = 'c', maxW = w - 40)
    }

    private val tips = listOf(
        "Tip: press L3 or R3 (or your swap button) to swap the screens",
        "Tip: Theme Studio lets you design every colour, pattern and effect",
        "Tip: L + R together opens the quick menu from anywhere",
        "Tip: turn any photo into a theme in Theme Studio",
        "Tip: pin your favourite games to Home from their options (START)",
    )

    override fun hints() = if (onToolbar) listOf("B" to "Back", "A" to "Open")
        else if (kid) listOf("START" to "Unlock", "X" to "Random", "A" to "Open")
        else listOf("SELECT" to "Themes", "X" to "Random", "Y" to "Search", "A" to "Open")
}

/** Profile: name, picture and greeting. */
fun profileMenu(app: Ryzer): Menu {
    val cfg = app.cfg
    return Menu(app, "Profile", listOf(
        Item("Name", "Shown in the greeting and on your profile badge.", value = { cfg.userName.ifEmpty { "Not set" } }, select = {
            app.push(Keyboard(app, "Your name", cfg.userName, 16) { cfg.userName = it; cfg.save() }) }),
        Item("Profile picture", "Pick any picture from your gallery.", value = { if (cfg.avatar.isEmpty()) "None" else "Set" }, select = {
            app.ctx.pickPhoto { uri ->
                app.runInBackground({
                    val name = "profile-${System.currentTimeMillis()}.jpg"
                    val bmp = app.ctx.contentResolver.openInputStream(uri).use { android.graphics.BitmapFactory.decodeStream(it) } ?: error("not a picture")
                    val side = min(bmp.width, bmp.height)
                    val sq = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
                    val small = Bitmap.createScaledBitmap(sq, 256, 256, true)
                    File(app.home, name).outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                    cfg.avatar = name; cfg.save()
                }) { e -> app.notify(if (e == null) "Profile picture set" else "Couldn't use that picture") }
            }
        }),
        Item("Remove picture", select = { cfg.avatar = ""; cfg.save() }),
        Item("Play stats", select = { app.push(StatsScreen(app)) }),
    ))
}
