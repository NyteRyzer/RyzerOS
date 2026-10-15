package com.ryzer.os

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.RectF
import android.media.AudioManager
import android.provider.Settings
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.sin

// ----- Sorting and filtering ---------------------------------------------------

object ListView {
    val SORTS = listOf("name", "recent", "played", "rating", "size", "added")
    val FILTERS = listOf("all", "favorites", "unplayed", "backlog", "playing", "beaten", "completed")
    val STATUSES = listOf("", "backlog", "playing", "beaten", "completed")

    fun label(s: String) = when (s) {
        "name" -> "Name"; "recent" -> "Last played"; "played" -> "Most played"; "rating" -> "Rating"
        "size" -> "File size"; "added" -> "Recently added"; "all" -> "All games"; "favorites" -> "Favorites"
        "unplayed" -> "Never played"; "" -> "None"; else -> s.replaceFirstChar { it.uppercase() }
    }

    fun apply(app: Ryzer, games: List<Game>, sortIt: Boolean = true): List<Game> {
        val st = app.state
        var list = app.visible(games)
        list = when (val f = app.cfg.filter) {
            "all" -> list
            "favorites" -> list.filter { st.isFavorite(it.key) }
            "unplayed" -> list.filter { (st.launches[it.key] ?: 0) == 0 }
            else -> list.filter { st.status[it.key] == f }
        }
        if (!sortIt) return list
        return when (app.cfg.sort) {
            "recent" -> list.sortedBy { st.recent.indexOf(it.key).let { i -> if (i < 0) Int.MAX_VALUE else i } }
            "played" -> list.sortedByDescending { st.playtime(it.key) }
            "rating" -> list.sortedByDescending { st.ratings[it.key] ?: 0 }
            "size" -> list.sortedByDescending { it.file.length() }
            "added" -> list.sortedByDescending { it.file.lastModified() }
            else -> list.sortedBy { app.title(it).lowercase() }
        }
    }

    fun stars(n: Int) = if (n <= 0) "Not rated" else "★".repeat(n) + "☆".repeat(5 - n)
}

// ----- Milestones -------------------------------------------------------------

object Milestones {
    class M(val id: String, val name: String, val desc: String, val test: (Ryzer) -> Boolean)

    val ALL = listOf(
        M("first", "Press Start", "Launch your first game") { it.state.launches.isNotEmpty() },
        M("hour", "Warming Up", "Play for 1 hour in total") { it.state.total() >= 3600 },
        M("ten", "Dedicated", "Play for 10 hours in total") { it.state.total() >= 36000 },
        M("fifty", "Legend", "Play for 50 hours in total") { it.state.total() >= 180000 },
        M("launch100", "Frequent Flyer", "Launch games 100 times") { it.state.launches.values.sum() >= 100 },
        M("fav10", "Curator", "Favorite 10 games") { it.state.favorites.size >= 10 },
        M("systems5", "Collector", "Play games on 5 different systems") { r ->
            r.state.launches.keys.mapNotNull { Library.find(r.systems, it)?.system?.id }.toSet().size >= 5 },
        M("streak7", "On a Roll", "Play 7 days in a row") { it.state.streak() >= 7 },
        M("collection", "Organized", "Create a collection") { it.state.collections.isNotEmpty() },
        M("critic", "Critic", "Rate 10 games") { it.state.ratings.size >= 10 },
        M("beaten", "Credits Roll", "Mark a game as beaten or completed") { r -> r.state.status.values.any { it == "beaten" || it == "completed" } },
        M("heavy", "Heavy Hitter", "Play a 3DS, GameCube, PS2 or Switch game") { r ->
            r.state.launches.keys.any { Library.find(r.systems, it)?.system?.id in setOf("3ds", "gc", "ps2", "switch") } },
        M("theme", "Stylist", "Save your own theme") { r -> r.themes.values.any { !it.builtin } },
        M("marathon", "Marathon", "Play 3 hours in one day") { r -> r.state.daily.values.any { it >= 10800 } },
    )

    /** Unlocks anything newly earned and announces it. */
    fun check(app: Ryzer) {
        for (m in ALL) if (m.id !in app.state.milestones && runCatching { m.test(app) }.getOrDefault(false)) {
            app.state.milestones += m.id
            app.state.save()
            app.sounds.play("fav")
            app.notify("Milestone unlocked: ${m.name}!", 3.5f)
        }
    }
}

// ----- Stats ------------------------------------------------------------------

class StatsScreen(app: Ryzer) : Screen(app) {
    override val title = "Play stats"
    private var page = 0
    private val pages = listOf("Overview", "Systems", "History", "Milestones")

    override fun handle(a: String) {
        when (a) {
            "left", "l" -> { page = (page + pages.size - 1) % pages.size; app.sounds.play("move") }
            "right", "r" -> { page = (page + 1) % pages.size; app.sounds.play("move") }
            "a", "b" -> { app.sounds.play("back"); app.pop() }
        }
    }

    override fun draw(c: Canvas, w: Float, h: Float) {
        G.topBar(c, app, th, "Stats  <  ${pages[page]}  >", w)
        when (page) {
            0 -> overview(c, w, h)
            1 -> systems(c, w, h)
            2 -> history(c, w, h)
            else -> milestones(c, w, h)
        }
    }

    private fun overview(c: Canvas, w: Float, h: Float) {
        val st = app.state
        val cards = listOf(
            "Total play time" to formatDuration(st.total()),
            "Launches" to "${st.launches.values.sum()}",
            "Day streak" to "${st.streak()}",
            "Games" to "${app.allGames().size}",
        )
        val cw = (w - 32 - 3 * 10) / 4
        cards.forEachIndexed { i, (k, v) ->
            val r = RectF(16 + i * (cw + 10), 52f, 16 + i * (cw + 10) + cw, 118f)
            G.panel(c, r, th)
            G.text(c, v, r.centerX(), r.top + 8, 30f, th.c("accent"), align = 'c', isBold = true, maxW = cw - 8)
            G.text(c, k, r.centerX(), r.top + 42, 17f, th.c("text_dim"), align = 'c', maxW = cw - 8)
        }
        // Last 7 days activity chart
        val chart = RectF(16f, 128f, w * 0.5f - 6, h - 46)
        G.panel(c, chart, th)
        G.text(c, "Last 7 days", chart.left + 12, chart.top + 8, 20f, th.c("text"), isBold = true)
        val days = (6 downTo 0).map { st.daily[today(-it)] ?: 0L }
        val peak = max(1L, days.maxOrNull() ?: 1L)
        val bw = (chart.width() - 24) / 7
        val baseY = chart.bottom - 26
        val maxH = baseY - chart.top - 40
        val names = (6 downTo 0).map { d ->
            val cal = java.util.Calendar.getInstance(); cal.add(java.util.Calendar.DAY_OF_YEAR, -d)
            java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault()).format(cal.time).take(2)
        }
        days.forEachIndexed { i, secs ->
            val x = chart.left + 12 + i * bw
            val bh = maxH * secs / peak
            G.rect(c, RectF(x + 4, baseY - max(2f, bh), x + bw - 4, baseY), if (i == 6) th.c("accent") else th.c("accent2"), 3f)
            G.text(c, names[i], x + bw / 2, baseY + 4, 16f, th.c("text_dim"), align = 'c')
        }
        // Most played
        val top = RectF(w * 0.5f + 6, 128f, w - 16, h - 46)
        G.panel(c, top, th)
        G.text(c, "Most played", top.left + 12, top.top + 8, 20f, th.c("text"), isBold = true)
        st.times.entries.sortedByDescending { it.value }.take(((top.height() - 40) / 24).toInt()).forEachIndexed { i, (k, secs) ->
            val name = Library.find(app.systems, k)?.let { app.title(it) } ?: File(k).nameWithoutExtension
            val y = top.top + 36 + i * 24
            G.text(c, "${i + 1}. $name", top.left + 12, y, 19f, th.c("text"), maxW = top.width() - 100)
            G.text(c, formatDuration(secs), top.right - 12, y, 19f, th.c("text_dim"), align = 'r')
        }
    }

    private fun systems(c: Canvas, w: Float, h: Float) {
        val st = app.state
        val rows = app.systems.map { s -> Triple(s, s.games.sumOf { st.playtime(it.key) }, s.games.size) }
            .filter { it.third > 0 }.sortedByDescending { it.second }
        val peak = max(1L, rows.maxOfOrNull { it.second } ?: 1L)
        val visible = ((h - 100) / 30).toInt()
        rows.take(visible).forEachIndexed { i, (s, secs, n) ->
            val y = 56 + i * 30f
            G.text(c, s.short, 20f, y + 4, 20f, th.c("text"), isBold = true)
            val bar = RectF(90f, y + 4, 90 + (w - 300) * secs / peak, y + 22)
            G.rect(c, RectF(90f, y + 4, w - 210, y + 22), th.c("panel"), 4f)
            G.rect(c, bar, s.color, 4f)
            G.text(c, "${formatDuration(secs)}  |  $n games", w - 20, y + 4, 18f, th.c("text_dim"), align = 'r')
        }
    }

    /** The last play sessions, newest first. */
    private fun history(c: Canvas, w: Float, h: Float) {
        val list = app.state.sessions
        if (list.isEmpty()) { G.text(c, "No sessions yet. Play something!", w / 2, h / 2, 24f, th.c("text_dim"), align = 'c'); return }
        val fmt = java.text.SimpleDateFormat("EEE MMM d, h:mm a", java.util.Locale.getDefault())
        val rows = ((h - 100) / 30).toInt()
        list.take(rows).forEachIndexed { i, (k, start, secs) ->
            val g = Library.find(app.systems, k)
            val y = 54 + i * 30f
            G.text(c, fmt.format(java.util.Date(start)), 20f, y + 4, 18f, th.c("text_dim"))
            G.text(c, (g?.let { app.title(it) } ?: File(k).nameWithoutExtension) + (g?.let { "  [${it.system.short}]" } ?: ""),
                220f, y + 3, 20f, th.c("text"), maxW = w - 340)
            G.text(c, formatDuration(secs), w - 20, y + 4, 19f, th.c("accent"), align = 'r')
        }
    }

    private fun milestones(c: Canvas, w: Float, h: Float) {
        val cols = 2
        val cw = (w - 32 - 10) / cols
        val unlocked = app.state.milestones
        G.text(c, "${unlocked.size} / ${Milestones.ALL.size} unlocked", w - 16, 46f, 18f, th.c("accent"), align = 'r')
        Milestones.ALL.forEachIndexed { i, m ->
            val r = RectF(16 + (i % cols) * (cw + 10), 62 + (i / cols) * 54f, 16 + (i % cols) * (cw + 10) + cw, 62 + (i / cols) * 54f + 48)
            if (r.bottom > h - 40) return@forEachIndexed
            val got = m.id in unlocked
            G.panel(c, r, th, fillColor = if (got) mix(th.c("panel"), th.c("good"), 0.25f) else null)
            G.text(c, (if (got) "✓ " else "") + m.name, r.left + 10, r.top + 5, 21f, if (got) th.c("text") else th.c("text_dim"), isBold = got, maxW = cw - 20)
            G.text(c, m.desc, r.left + 10, r.top + 27, 16f, th.c("text_dim"), maxW = cw - 20)
        }
    }

    override fun hints() = listOf("L/R" to "Page", "B" to "Back")
}

// ----- Library tools -------------------------------------------------------------

fun normalized(name: String) = cleanName(name).lowercase().filter { it.isLetterOrDigit() }

/** Groups of the same game found more than once (other regions, revisions or copies). */
fun duplicatesMenu(app: Ryzer): Screen {
    val groups = app.systems.flatMap { s -> s.games.groupBy { normalized(it.file.nameWithoutExtension) }.values.filter { it.size > 1 } }
    if (groups.isEmpty()) return Message(app, "Duplicate finder", "No duplicates found. Every game appears once.")
    return Menu(app, "Duplicates (${groups.size})", groups.map { g ->
        Item("${app.title(g[0])} [${g[0].system.short}]", "${g.size} copies: " + g.joinToString(", ") { it.file.name },
            value = { "${g.size}x" }, select = { app.push(GameList(app, "Copies of ${app.title(g[0])}", g)) })
    })
}

class StorageScreen(app: Ryzer) : Screen(app) {
    override val title = "Storage by system"
    private val rows = app.systems.map { s -> Triple(s, s.games.sumOf { it.file.length() }, s.games.size) }
        .filter { it.third > 0 }.sortedByDescending { it.second }
    private val total = rows.sumOf { it.second }

    override fun handle(a: String) { if (a == "a" || a == "b") app.pop() }

    override fun draw(c: Canvas, w: Float, h: Float) {
        G.topBar(c, app, th, "$title  (${"%.1f".format(total / 1e9)} GB)", w)
        val peak = max(1L, rows.maxOfOrNull { it.second } ?: 1L)
        rows.take(((h - 100) / 30).toInt()).forEachIndexed { i, (s, bytes, n) ->
            val y = 56 + i * 30f
            G.text(c, s.short, 20f, y + 4, 20f, th.c("text"), isBold = true)
            G.rect(c, RectF(90f, y + 4, w - 210, y + 22), th.c("panel"), 4f)
            G.rect(c, RectF(90f, y + 4, 90 + (w - 300) * bytes / peak, y + 22), s.color, 4f)
            G.text(c, "%.2f GB  |  %d".format(bytes / 1e9, n), w - 20, y + 4, 18f, th.c("text_dim"), align = 'r')
        }
        if (rows.isEmpty()) G.text(c, "No games found.", w / 2, h / 2, 24f, th.c("text_dim"), align = 'c')
    }

    override fun hints() = listOf("B" to "Back")
}

// ----- Collections ------------------------------------------------------------------

fun collectionGames(app: Ryzer, name: String) = app.state.collections[name].orEmpty().mapNotNull { Library.find(app.systems, it) }

fun addToCollectionMenu(app: Ryzer, g: Game): Menu {
    val st = app.state
    val items = st.collections.keys.map { name ->
        Item(name, value = { if (g.key in st.collections[name].orEmpty()) "In it" else "" }, select = {
            val list = st.collections.getOrPut(name) { mutableListOf() }
            if (!list.remove(g.key)) list.add(g.key)
            st.save()
            app.notify(if (g.key in list) "Added to $name" else "Removed from $name")
            Milestones.check(app)
        })
    } + Item("+ New collection", select = {
        app.push(Keyboard(app, "Collection name", maxLen = 24) { name ->
            if (name.isNotEmpty()) {
                st.collections.getOrPut(name) { mutableListOf() }.let { if (g.key !in it) it.add(g.key) }
                st.save(); app.notify("Added to $name"); Milestones.check(app)
                app.pop()
            }
        })
    })
    return Menu(app, "Add to collection", items)
}

fun manageCollectionsMenu(app: Ryzer): Menu {
    val st = app.state
    lateinit var menu: Menu
    fun build(): List<Item> = st.collections.keys.map { name ->
        Item(name, "A: open   X: rename   Y: delete", value = { "${st.collections[name]?.size ?: 0} games" },
            select = { app.push(GameList(app, name, null, source = "collection:$name")) })
    }.ifEmpty { listOf(Item("No collections yet", "Open any game's options (START) and choose 'Add to collection'.")) }
    menu = object : Menu(app, "Collections", emptyList()) {
        override fun handle(a: String) {
            val name = current()?.label?.takeIf { st.collections.containsKey(it) }
            when {
                a == "x" && name != null -> app.push(Keyboard(app, "Rename collection", name, 24) { new ->
                    if (new.isNotEmpty() && new != name) {
                        val games = st.collections.remove(name) ?: mutableListOf()
                        st.collections[new] = games; st.save(); items = build()
                    }
                })
                a == "y" && name != null -> app.push(Confirm(app, "Delete collection '$name'? Games stay on your device.", yes = "Delete") {
                    st.collections.remove(name); st.save(); items = build(); sel = 0
                })
                else -> super.handle(a)
            }
        }
        override fun hints() = listOf("Y" to "Delete", "X" to "Rename", "A" to "Open")
    }
    menu.items = build()
    return menu
}

// ----- Screensaver -------------------------------------------------------------------

/** Attract mode: drifting box art and the clock. Any button wakes it. */
class Screensaver(app: Ryzer) : Screen(app) {
    private var t = 0f
    private val art = app.allGames().shuffled().asSequence().mapNotNull { g -> g.image()?.let { g to it } }.take(8).toList()
    private var idx = 0
    private var x = 0.3f; private var y = 0.3f; private var dx = 0.07f; private var dy = 0.05f

    init { app.ctx.setMenuBrightness(0.15f) }

    override fun handle(a: String) {
        app.ctx.setMenuBrightness(-1f)   // back to the system brightness
        app.pop()
    }

    override fun update(dt: Float) {
        t += dt
        x += dx * dt; y += dy * dt
        if (x < 0f || x > 1f) { dx = -dx; x = x.coerceIn(0f, 1f) }
        if (y < 0f || y > 1f) { dy = -dy; y = y.coerceIn(0f, 1f) }
        if (t > 8f) { t = 0f; idx++ }
    }

    override fun draw(c: Canvas, w: Float, h: Float) {
        G.rect(c, RectF(0f, 0f, w, h), withAlpha(0xFF000000.toInt(), 0.75f))
        val bw = 200f; val bh = 200f
        val px = 20 + x * (w - bw - 40); val py = 20 + y * (h - bh - 120)
        val item = art.getOrNull(idx % max(1, art.size))
        val bmp = item?.let { G.image(it.second) }
        if (bmp != null) {
            G.drawImageFit(c, bmp, RectF(px, py, px + bw, py + bh))
            G.text(c, app.title(item.first), px + bw / 2, py + bh + 6, 22f, th.c("text"), align = 'c', maxW = 300f)
        } else {
            G.emblem(c, th, px + bw / 2, py + bh / 2, 50f)
        }
        val clock = java.text.SimpleDateFormat(if (app.cfg.clock24) "HH:mm" else "h:mm", java.util.Locale.getDefault()).format(java.util.Date())
        G.text(c, clock, w - 24, h - 90, 60f, withAlpha(th.c("text"), 0.7f + 0.3f * sin(t)), align = 'r', isBold = true)
    }

    override fun drawTop(c: Canvas, w: Float, h: Float) {
        G.rect(c, RectF(0f, 0f, w, h), 0xFF000000.toInt())
        app.drawBigClock(c, w, h / 2 - 80)
    }

    override fun hints() = emptyList<Pair<String, String>>()
}

// ----- Quick menu (L + R) -------------------------------------------------------------

class QuickMenu(app: Ryzer) : Menu(app, "Quick menu", emptyList(), "Opened with L + R from anywhere") {
    private val audio = app.ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var brightness = 1f

    private fun vol(): Int {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max(1, max)
    }

    private fun settings(action: String) {
        runCatching { app.ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { app.notify("Not available on this device") }
    }

    init {
        items = listOf(
            Item("Volume", value = { "${vol()}%" }, change = { d ->
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (d > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, 0) }),
            Item("Mute", value = { if (vol() == 0) "On" else "Off" }, change = {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0) }),
            Item("Menu brightness", "Dims Ryzer itself; games use the system brightness.", value = { "${(brightness * 100).toInt()}%" },
                change = { d -> brightness = (brightness + d * 0.1f).coerceIn(0.1f, 1f); app.ctx.setMenuBrightness(brightness) }),
            Item("Battery", value = { app.battery().let { (p, ch) -> if (p < 0) "?" else "$p%" + if (ch) " (charging)" else "" } }),
            Item("Wi-Fi settings", select = { settings(Settings.ACTION_WIFI_SETTINGS) }),
            Item("Bluetooth settings", select = { settings(Settings.ACTION_BLUETOOTH_SETTINGS) }),
            Item("Display settings", select = { settings(Settings.ACTION_DISPLAY_SETTINGS) }),
            Item("Free memory", select = { Launcher.boost(app.ctx, ""); app.notify("Background apps closed") }),
            Item("Rescan games", select = { app.rescan { app.notify("Found ${app.allGames().size} games") } }),
            Item("Random game", select = { app.pop(); app.openRandom() }),
            Item("Go home", select = { app.goHome() }),
        )
    }
}

// ----- Kid mode -------------------------------------------------------------------------

object KidMode {
    fun enable(app: Ryzer) {
        app.push(Keyboard(app, "Choose a PIN (4+ digits)", maxLen = 8) { pin ->
            if (pin.length >= 4 && pin.all { it.isDigit() }) {
                app.cfg.kidPin = pin; app.cfg.kidMode = true; app.cfg.save()
                app.goHome(); app.notify("Kid mode on. Press START on Home to unlock.")
            } else app.notify("PIN must be at least 4 digits")
        })
    }

    fun unlock(app: Ryzer) {
        app.push(Keyboard(app, "Enter PIN to leave kid mode", maxLen = 8) { pin ->
            if (pin == app.cfg.kidPin) { app.cfg.kidMode = false; app.cfg.save(); app.goHome(); app.notify("Kid mode off") }
            else { app.sounds.play("error"); app.notify("Wrong PIN") }
        })
    }
}

// ----- Backup -------------------------------------------------------------------------

object Backup {
    private fun dir(app: Ryzer) = File(app.home, "backups").apply { mkdirs() }

    fun create(app: Ryzer): File {
        app.cfg.save(); app.state.save()
        val out = File(dir(app), "ryzer-backup-${today()}.zip")
        ZipOutputStream(out.outputStream()).use { zip ->
            val files = listOf(File(app.home, "config.json"), File(app.home, "state.json")) +
                (app.userThemesDir().listFiles()?.toList() ?: emptyList())
            for (f in files.filter { it.isFile }) {
                zip.putNextEntry(ZipEntry(f.relativeTo(app.home).path))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return out
    }

    fun latest(app: Ryzer): File? = dir(app).listFiles { f -> f.name.endsWith(".zip") }?.maxByOrNull { it.lastModified() }

    fun restore(app: Ryzer, zipFile: File) {
        ZipInputStream(zipFile.inputStream()).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                val target = File(app.home, e.name).canonicalFile
                if (target.path.startsWith(app.home.canonicalPath) && !e.isDirectory) {   // never write outside the Ryzer folder
                    target.parentFile?.mkdirs()
                    target.outputStream().use { zip.copyTo(it) }
                }
                e = zip.nextEntry
            }
        }
        app.ctx.restart()
    }
}

/** Opens the store page for a missing emulator (Play Store app, or the website if there's no store). */
fun openStore(app: Ryzer, pkg: String) {
    val ctx = app.ctx
    val market = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { ctx.startActivity(market) }.onFailure {
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { app.notify("No app store or browser found") }
    }
}

/** Tile ordering for the Home ribbon. */
fun sortedSystems(app: Ryzer): List<GameSystem> {
    val st = app.state
    val shown = app.systems.filter { it.id !in app.cfg.hiddenSystems }
    return when (app.cfg.systemSort) {
        "name" -> shown.sortedBy { it.name }
        "games" -> shown.sortedByDescending { it.games.size }
        "recent" -> shown.sortedBy { s ->
            st.recent.indexOfFirst { k -> s.games.any { it.key == k } }.let { if (it < 0) Int.MAX_VALUE else it } }
        else -> shown
    }
}
