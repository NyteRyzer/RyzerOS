package com.ryzer.os

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Environment
import android.os.StatFs
import java.io.File
import kotlin.math.max
import kotlin.math.min

private fun <T> cycle(options: List<T>, cur: T, d: Int): T = options[((options.indexOf(cur).coerceAtLeast(0) + d) % options.size + options.size) % options.size]

// ----- Themes ------------------------------------------------------------------

fun miniPreview(c: Canvas, r: RectF, t: Theme) {
    val steps = 12
    for (i in 0 until steps) {
        val y0 = r.top + r.height() * i / steps
        G.rect(c, RectF(r.left, y0, r.right, y0 + r.height() / steps + 1), mix(t.c("bg_top"), t.c("bg_bottom"), i / (steps - 1f)))
    }
    val rad = max(2f, t.radius / 3)
    val tw = r.width() / 5
    for (k in 0 until 3) {
        val s = k == 1
        val ww = tw + if (s) 8 else 0; val hh = r.height() * if (s) 0.5f else 0.42f
        val cx = r.centerX() + (k - 1) * (tw + 10); val cy = r.top + r.height() * 0.42f
        val tr = RectF(cx - ww / 2, cy - hh / 2, cx + ww / 2, cy + hh / 2)
        G.rect(c, tr, mix(t.c("panel"), t.c("accent"), if (s) 0.25f else 0f), rad)
        G.outline(c, tr, if (s) t.c("accent") else t.c("panel_border"), if (s) 2f else 1f, rad)
    }
    val bar = RectF(r.left + 8, r.bottom - 18, r.right - 8, r.bottom - 8)
    G.rect(c, bar, t.c("panel"), 4f)
    G.rect(c, RectF(bar.left, bar.top, bar.centerX(), bar.bottom), t.c("accent2"), 4f)
    G.outline(c, r, t.c("text_dim"), 1f)
}

class ThemePicker(app: Ryzer) : Screen(app) {
    override val title = "Themes"
    private var original = app.cfg.theme
    private var list = listOf<Theme>()
    private var sel = 0
    private var offset = 0
    private var cols = 3

    init { reload(); sel = list.indexOfFirst { it.id == original }.coerceAtLeast(0) }

    private fun reload() {
        app.themes = Themes.loadAll(app)
        list = app.themes.values.sortedWith(compareBy({ !it.builtin }, { it.name.lowercase() }))
    }

    override fun onResume() {
        var cur = list.getOrNull(sel)?.id
        app.focusTheme?.let { cur = it; original = it; app.focusTheme = null }
        reload()
        sel = list.indexOfFirst { it.id == cur }.let { if (it >= 0) it else min(sel, list.size - 1) }
        app.setTheme(list[sel].id, save = false)
    }

    private fun move(d: Int) {
        sel = (sel + d).coerceIn(0, list.size - 1)
        app.setTheme(list[sel].id, save = false)  // live preview
        app.sounds.play("move")
    }

    override fun handle(a: String) {
        when (a) {
            "left" -> move(-1); "right" -> move(1); "up" -> move(-cols); "down" -> move(cols)
            "a" -> { app.setTheme(list[sel].id, save = true); app.sounds.play("select"); app.notify("Theme set: ${list[sel].name}"); app.pop() }
            "b" -> { app.setTheme(original, save = false); app.sounds.play("back"); app.pop() }
            "x" -> app.push(ThemeEditor(app, list[sel]))
            "y" -> {
                val t = list[sel]
                if (t.builtin) app.notify("Built-in themes can't be deleted")
                else app.push(Confirm(app, "Delete theme '${t.name}'?", yes = "Delete") { Themes.delete(app, t); sel = max(0, sel - 1); onResume() })
            }
        }
    }

    override fun draw(c: Canvas, w: Float, h: Float) {
        G.topBar(c, app, th, "Themes  (${sel + 1}/${list.size})", w)
        cols = max(3, (w / 210).toInt())
        val cellW = (w - 32 - (cols - 1) * 12) / cols
        val cellH = 128f
        val top = 52f
        val rowsVisible = max(1, ((h - top - 44) / (cellH + 10)).toInt())
        val row = sel / cols
        if (row < offset) offset = row else if (row >= offset + rowsVisible) offset = row - rowsVisible + 1
        list.forEachIndexed { i, t ->
            val r = i / cols; val cc = i % cols
            if (r < offset || r >= offset + rowsVisible) return@forEachIndexed
            val rect = RectF(16 + cc * (cellW + 12), top + (r - offset) * (cellH + 10), 16 + cc * (cellW + 12) + cellW, top + (r - offset) * (cellH + 10) + cellH)
            val s = i == sel
            G.panel(c, rect, th, s)
            app.hit(rect) { if (sel == i) handle("a") else { sel = i; app.setTheme(t.id, save = false) } }
            miniPreview(c, RectF(rect.left + 8, rect.top + 8, rect.right - 8, rect.bottom - 36), t)
            G.text(c, t.name + if (t.builtin) "" else "  (custom)", rect.centerX(), rect.bottom - 30, 20f, if (s) th.c("text") else th.c("text_dim"), align = 'c', maxW = rect.width() - 12)
            if (t.id == app.cfg.theme) G.circle(c, rect.right - 14, rect.top + 14, 6f, th.c("good"))
        }
    }

    override fun hints() = listOf("Y" to "Delete", "X" to "Edit copy", "B" to "Cancel", "A" to "Apply")
}

private val COLOR_LABELS = mapOf("bg_top" to "Background top", "bg_bottom" to "Background bottom", "panel" to "Panel",
    "panel_border" to "Panel border", "accent" to "Accent", "accent2" to "Accent 2", "text" to "Text", "text_dim" to "Dim text",
    "highlight_text" to "Text on accent", "good" to "Good / OK", "warn" to "Warning", "bad" to "Error")

/** Edits a copy of a theme; built-in themes are never modified. */
class ThemeEditor(app: Ryzer, base: Theme) : Menu(app, "Theme Editor", emptyList(), "Left/Right adjust  |  Y switches R/G/B channel") {
    private val t: Theme = if (base.builtin) {
        val (id, _) = Themes.nextCustomId(app.themes)
        base.copy(id, "${base.name} Custom")
    } else base.copy(base.id, base.name)
    private val channel = HashMap<String, Int>()

    init {
        val list = mutableListOf(Item("Name", value = { t.name }, select = {
            app.push(Keyboard(app, "Theme name", t.name, 24) { if (it.isNotEmpty()) t.name = it })
        }))
        for (key in Theme.COLOR_KEYS) list += Item(COLOR_LABELS[key] ?: key, value = {
            val col = t.c(key); val ch = channel[key] ?: 0
            "${"RGB"[ch]}:${"%3d".format((col shr (16 - 8 * ch)) and 255)}  ${Theme.hex(col)}"
        }, change = { d ->
            val ch = channel[key] ?: 0; val shift = 16 - 8 * ch
            val v = (((t.c(key) shr shift) and 255) + d * 8).coerceIn(0, 255)
            t.colors[key] = (t.c(key) and (255 shl shift).inv()) or (v shl shift) or (0xFF shl 24)
            app.previewTheme(t)
        }, swatch = { t.c(key) }).also { it.colorKey = key }
        list += styleItem("Background", "background", Theme.BACKGROUNDS)
        list += styleItem("Tile style", "tile_style", Theme.TILE_STYLES)
        list += Item("Corner radius", value = { "${t.radius.toInt()}" }, change = { d ->
            t.style["corner_radius"] = (t.radius.toInt() + d * 2).coerceIn(0, 32); app.previewTheme(t) })
        list += Item("Text size", value = { "%.1fx".format(t.fontScale) }, change = { d ->
            t.style["font_scale"] = (t.fontScale + d * 0.1f).coerceIn(0.8f, 1.4f); app.previewTheme(t) })
        list += Item("Particles", value = { if (t.particles) "On" else "Off" }, change = { t.style["particles"] = !t.particles; app.previewTheme(t) })
        list += Item("Save theme", help = "Saved to RyzerOS/themes - you can also edit the JSON with any text editor.", select = { save() })
        items = list
        app.previewTheme(t)
    }

    private fun styleItem(label: String, key: String, options: List<String>) = Item(label,
        value = { (t.style[key] as String).replaceFirstChar { it.uppercase() } },
        change = { d -> t.style[key] = cycle(options, t.style[key] as String, d); app.previewTheme(t) })

    private fun save() {
        Themes.save(app, t)
        app.themes = Themes.loadAll(app)
        app.setTheme(t.id, save = true)
        app.focusTheme = t.id
        app.notify("Saved '${t.name}'")
        app.pop()
    }

    override fun handle(a: String) {
        val key = current()?.colorKey
        if (a == "y" && key != null) { channel[key] = ((channel[key] ?: 0) + 1) % 3; app.sounds.play("move"); return }
        if (a == "b") app.endPreview()
        super.handle(a)
    }

    override fun hints() = listOf("Y" to "R/G/B", "B" to "Discard", "A" to "Select")
}

// ----- Settings -----------------------------------------------------------------

fun settingsMenu(app: Ryzer): Menu {
    val cfg = app.cfg
    fun toggle(label: String, help: String = "", get: () -> Boolean, set: (Boolean) -> Unit) =
        Item(label, help, value = { if (get()) "On" else "Off" }, change = { set(!get()); cfg.save() })

    val emulators = { Menu(app, "Emulators", app.systems.map { s ->
        Item(s.name, "${s.demand}. ${s.note}", value = { Launcher.label(app, Launcher.chosen(app, s)) }, select = { app.push(EmulatorPicker(app, s)) })
    }) }

    val display = { Menu(app, "Display & dual screen", listOf(
        Item("Dual screen", "Auto uses a second display when the device has one (AYN Thor, RG DS, AYANEO Pocket DS, Retroid Dual).",
            value = { cfg.dual.replaceFirstChar { it.uppercase() } }, change = { d -> cfg.dual = cycle(listOf("auto", "on", "off"), cfg.dual, d); cfg.save(); app.ctx.updateDisplays() }),
        toggle("Swap screens", "Put the menu on the second display and art/info on the main one.", { cfg.swapScreens }) { cfg.swapScreens = it; app.ctx.updateDisplays() },
        Item("Detected displays", value = { "${app.ctx.secondaryDisplays().size + 1}" }, help = "Ryzer frees the second screen while a game runs, so dual-screen emulators can use it."),
        toggle("Animations", "Turn off for the snappiest menus.", { cfg.animations }) { cfg.animations = it },
        toggle("Boot animation", get = { cfg.boot }) { cfg.boot = it },
        Item("Menu frame rate", "30 fps saves battery; the menu still feels smooth.", value = { "${cfg.fps} fps" },
            change = { d -> cfg.fps = cycle(listOf(30, 60), cfg.fps, d); cfg.save() }),
        toggle("24-hour clock", get = { cfg.clock24 }) { cfg.clock24 = it },
        toggle("Show battery", get = { cfg.showBattery }) { cfg.showBattery = it },
    )) }

    val sound = { Menu(app, "Sound", listOf(
        toggle("Menu sounds", get = { cfg.sound }) { cfg.sound = it; app.sounds.enabled = it },
        Item("Menu volume", value = { "${cfg.volume}%" }, change = { d -> cfg.volume = (cfg.volume + d * 10).coerceIn(0, 100); cfg.save(); app.sounds.setVolume(cfg.volume); app.sounds.play("move") }),
    )) }

    val library = { Menu(app, "Library", listOf(
        Item("ROM folders", "Where Ryzer looks for <system> folders like 3ds, gc, ps2.", value = { "${app.romRoots().size}" }, select = { app.push(romFoldersMenu(app)) }),
        Item("Rescan ROMs", select = { app.rescan { app.notify("Found ${app.systems.sumOf { it.games.size }} games") } }),
        toggle("Clean game names", "Hides tags like \"(USA) [!]\".", { cfg.cleanNames }) { cfg.cleanNames = it; app.rescan() },
        toggle("Hide empty systems", get = { cfg.hideEmpty }) { cfg.hideEmpty = it },
        toggle("Show Apps tile", get = { cfg.showApps }) { cfg.showApps = it },
        Item("Reload systems & emulators", "Re-reads systems.json / emulators.json from the RyzerOS folder if you edited them.", select = { app.reloadData(); app.notify("Reloaded") }),
        Item("Clear recently played", select = { app.push(Confirm(app, "Clear your recently played list?") { app.state.recent.clear(); app.state.save(); app.notify("Cleared") }) }),
    )) }

    val performance = { Menu(app, "Performance", listOf(
        toggle("Boost before heavy games", "Closes cached background apps before Switch, 3DS, Wii U, GameCube, Wii, PS2 and Vita games to free RAM.", { cfg.boost }) { cfg.boost = it },
        Item("Free memory now", select = { Launcher.boost(app.ctx, ""); app.notify("Background apps closed") }),
        Item("Device performance mode", "Opens Android settings. AYN, Retroid and Anbernic put their CPU/fan performance modes in quick settings or a dedicated settings page.",
            select = { runCatching { app.ctx.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }),
        Item("Speed tips", select = { app.push(Message(app, "Speed tips", SPEED_TIPS)) }),
    )) }

    val controls = { Menu(app, "Controls", listOf(
        toggle("Swap A / B", "Use the other face button to confirm (for Nintendo-style layouts).", { cfg.swapAB }) { cfg.swapAB = it },
        Item("Controls help", select = { app.push(Message(app, "Controls", "D-pad / stick: move\nA: open / play   B: back\nX: favorite / random   Y: search\n" +
            "L / R: page or letter jump\nSTART: options / settings   SELECT: themes\nTouch: tap anything, including the button hints.")) }),
    )) }

    return Menu(app, "Settings", listOf(
        Item("Emulators", "Choose which emulator each system uses. Any installed app works.", select = { app.push(emulators()) }),
        Item("Library", "ROM folders, rescanning and naming.", select = { app.push(library()) }),
        Item("Display & dual screen", select = { app.push(display()) }),
        Item("Performance", select = { app.push(performance()) }),
        Item("Themes", "Pick one of 20+ themes or build your own.", select = { app.push(ThemePicker(app)) }),
        Item("Sound", select = { app.push(sound()) }),
        Item("Controls", select = { app.push(controls()) }),
        Item("Set as home screen", "Make Ryzer OS your device's home screen, like Cocoon or iiSU.", select = {
            runCatching { app.ctx.startActivity(Intent(android.provider.Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { app.notify("Open Android Settings > Apps > Default apps > Home app") } }),
        Item("System info", select = { app.push(SystemInfo(app)) }),
        Item("Emulator setup help", select = { app.push(Message(app, "Emulator setup", SETUP_HELP)) }),
        Item("About Ryzer OS", select = { app.push(Message(app, "About", "Ryzer OS ${BuildInfo.version} for Android\n${BuildInfo.device}\n\n" +
            "Games: ${app.systems.sumOf { it.games.size }}   Themes: ${app.themes.size}\nPlay time: ${formatDuration(app.state.total())}\n" +
            "Data folder: ${app.home}\n\nThemes, systems.json and emulators.json in the data folder are plain JSON you can edit.")) }),
    ))
}

const val SETUP_HELP = "1. Put games in a ROMs folder, one folder per system: ROMs/3ds, ROMs/gc, ROMs/ps2, ROMs/nds, ROMs/switch...\n" +
    "2. Install the emulators you want (Settings > Emulators shows which are found).\n" +
    "3. Many emulators (Azahar, Dolphin, NetherSX2, DuckStation, DraStic...) need folder access the first time: open the emulator once " +
    "and give it access to that system's folder, e.g. ROMs/3ds.\n" +
    "4. RetroArch: install the cores you use from RetroArch's Online Updater.\n" +
    "5. Unknown emulator? Pick it under Emulators > 'Any app', or use 'Ask every time'."

const val SPEED_TIPS = "- Use the device's performance mode for 3DS, GameCube, PS2 and Switch.\n" +
    "- Keep internal resolution at 1x on mid-range chips (RK3568, Unisoc T618/T820, Dimensity 900).\n" +
    "- PS2: NetherSX2 with the Vulkan renderer on Adreno (Snapdragon) GPUs; Mali GPUs (RG DS) do best with OpenGL and light games.\n" +
    "- GameCube/Wii: Dolphin with 'Skip EFB access', async shaders, 1x resolution.\n" +
    "- 3DS: Azahar with 1x resolution and the shader JIT on; dual-screen devices can use a separate-screens layout.\n" +
    "- Leave 'Boost before heavy games' on, especially on 3-4 GB devices."

fun romFoldersMenu(app: Ryzer): Menu {
    val cfg = app.cfg
    lateinit var menu: Menu
    fun build(): List<Item> {
        val dirs = cfg.romDirs.ifEmpty { Library.defaultRomDirs() }
        return dirs.map { d ->
            Item(d, if (cfg.romDirs.isEmpty()) "Found automatically. Add a folder to choose your own." else "Press A to remove.",
                select = { if (cfg.romDirs.isNotEmpty()) app.push(Confirm(app, "Stop scanning $d?", yes = "Remove") {
                    cfg.romDirs.remove(d); cfg.save(); menu.items = build(); app.rescan() }) })
        } + Item("+ Add folder", "Browse to the folder that holds your system folders.", select = {
            app.push(FolderPicker(app, Environment.getExternalStorageDirectory()) { f ->
                if (cfg.romDirs.isEmpty()) cfg.romDirs.addAll(Library.defaultRomDirs())
                if (f.path !in cfg.romDirs) cfg.romDirs.add(f.path)
                cfg.save(); menu.items = build(); app.rescan()
            })
        }) + Item("Use automatic folders", select = { cfg.romDirs.clear(); cfg.save(); menu.items = build(); app.rescan() })
    }
    menu = Menu(app, "ROM folders", emptyList())
    menu.items = build()
    return menu
}

/** Gamepad-friendly folder browser. */
class FolderPicker(app: Ryzer, start: File, private val onPick: (File) -> Unit) : Menu(app, "Choose ROM folder", emptyList()) {
    private var dir = start
    init { load() }

    private fun load() {
        val subs = (dir.listFiles() ?: emptyArray()).filter { it.isDirectory && !it.name.startsWith(".") }.sortedBy { it.name.lowercase() }
        val list = mutableListOf(Item("[ Use this folder ]", dir.path, select = { app.pop(); onPick(dir) }))
        dir.parentFile?.takeIf { it.path != "/" && it.canRead() }?.let { p -> list += Item(".. (up)", p.path, select = { dir = p; sel = 0; load() }) }
        if (dir.path == Environment.getExternalStorageDirectory().path) {
            File("/storage").listFiles()?.filter { it.isDirectory && it.name != "emulated" && it.name != "self" && it.canRead() }
                ?.forEach { sd -> list += Item("SD card: ${sd.name}", sd.path, select = { dir = sd; sel = 0; load() }) }
        }
        subs.forEach { s -> list += Item("${s.name}/", select = { dir = s; sel = 0; load() }) }
        items = list
    }
}

// ----- Emulator choice -------------------------------------------------------------

class EmulatorPicker(app: Ryzer, private val sys: GameSystem) : Menu(app, "${sys.short} emulator", emptyList(), sys.demand) {
    init {
        val cur = Launcher.chosen(app, sys)
        val list = mutableListOf<Item>()
        for ((id, installed) in Launcher.choices(app, sys)) {
            val name = Launcher.label(app, id)
            list += Item((if (id == cur) "> " else "") + name, if (installed) "Installed" else "Not installed - get it from its official site or store.",
                value = { if (installed) "Installed" else "Missing" },
                select = { if (installed) pick(id) else app.notify("$name isn't installed") })
        }
        list += Item((if (cur == Launcher.CHOOSER) "> " else "") + "Ask every time", "Shows Android's 'Open with' list on each launch.", select = { pick(Launcher.CHOOSER) })
        list += Item((if (cur?.startsWith(Launcher.ANY_APP_PREFIX) == true) "> " else "") + "Any app...",
            "Use any installed emulator, even one Ryzer doesn't know.", select = { app.push(AppsScreen(app) { pkg -> pick(Launcher.ANY_APP_PREFIX + pkg) }) })
        if (app.cfg.systemEmu.containsKey(sys.id)) list += Item("Reset to automatic", select = { app.cfg.systemEmu.remove(sys.id); app.cfg.save(); app.pop() })
        items = list
    }

    private fun pick(id: String) {
        app.cfg.systemEmu[sys.id] = id
        app.cfg.save()
        app.notify("${sys.short}: ${Launcher.label(app, id)}")
        while (app.current is EmulatorPicker || app.current is AppsScreen) app.pop()
    }
}

// ----- Apps drawer ------------------------------------------------------------------

class AppsScreen(app: Ryzer, private val onPick: ((String) -> Unit)? = null) : Screen(app) {
    override val title = if (onPick == null) "Apps" else "Choose an app"
    private val apps: List<ResolveInfo>
    private var sel = 0
    private var offset = 0
    private var cols = 4
    private val labels = HashMap<String, String>()

    init {
        val pm = app.ctx.packageManager
        apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .filter { it.activityInfo.packageName != app.ctx.packageName }
            .onEach { labels[it.activityInfo.packageName + it.activityInfo.name] = it.loadLabel(pm).toString() }
            .sortedBy { labels[it.activityInfo.packageName + it.activityInfo.name]!!.lowercase() }
    }

    private val icons = HashMap<String, android.graphics.Bitmap?>()
    private fun icon(r: ResolveInfo): android.graphics.Bitmap? = icons.getOrPut(r.activityInfo.packageName + r.activityInfo.name) {
        runCatching {
            val d = r.loadIcon(app.ctx.packageManager)
            val b = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, 96, 96); d.draw(Canvas(b)); b
        }.getOrNull()
    }

    private fun open(r: ResolveInfo) {
        val pkg = r.activityInfo.packageName
        if (onPick != null) { onPick.invoke(pkg); return }
        runCatching { app.ctx.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(pkg, r.activityInfo.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    override fun handle(a: String) {
        val n = apps.size
        if (n == 0) { if (a == "b") app.pop(); return }
        when (a) {
            "left" -> sel = max(0, sel - 1); "right" -> sel = min(n - 1, sel + 1)
            "up" -> sel = max(0, sel - cols); "down" -> sel = min(n - 1, sel + cols)
            "l" -> sel = max(0, sel - cols * 3); "r" -> sel = min(n - 1, sel + cols * 3)
            "a" -> { app.sounds.play("select"); open(apps[sel]); return }
            "b" -> { app.sounds.play("back"); app.pop(); return }
            else -> return
        }
        app.sounds.play("move")
    }

    override fun draw(c: Canvas, w: Float, h: Float) {
        G.topBar(c, app, th, "$title  (${apps.size})", w)
        cols = max(4, (w / 130).toInt())
        val cw = (w - 32 - (cols - 1) * 10) / cols; val ch = 112f; val top = 52f
        val rowsVisible = max(1, ((h - top - 44) / (ch + 10)).toInt())
        val row = sel / cols
        if (row < offset) offset = row else if (row >= offset + rowsVisible) offset = row - rowsVisible + 1
        apps.forEachIndexed { i, r ->
            val rr = i / cols; val cc = i % cols
            if (rr < offset || rr >= offset + rowsVisible) return@forEachIndexed
            val rect = RectF(16 + cc * (cw + 10), top + (rr - offset) * (ch + 10), 16 + cc * (cw + 10) + cw, top + (rr - offset) * (ch + 10) + ch)
            val s = i == sel
            G.panel(c, rect, th, s)
            app.hit(rect) { if (sel == i) handle("a") else sel = i }
            icon(r)?.let { G.drawImageFit(c, it, RectF(rect.centerX() - 28, rect.top + 10, rect.centerX() + 28, rect.top + 66)) }
            G.text(c, labels[r.activityInfo.packageName + r.activityInfo.name] ?: "", rect.centerX(), rect.bottom - 34, 19f,
                if (s) th.c("text") else th.c("text_dim"), align = 'c', maxW = rect.width() - 10)
        }
    }

    override fun hints() = listOf("B" to "Back", "A" to if (onPick == null) "Open" else "Choose")
}

// ----- System info ------------------------------------------------------------------

class SystemInfo(app: Ryzer) : Screen(app) {
    override val title = "System info"
    private var timer = 1f
    private var lines = listOf<Pair<String, String>>()

    override fun update(dt: Float) {
        timer += dt
        if (timer < 1f) return
        timer = 0f
        val am = app.ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val st = runCatching { StatFs(Environment.getExternalStorageDirectory().path) }.getOrNull()
        val (bat, chg) = app.battery()
        lines = listOf(
            "Device" to BuildInfo.device,
            "Chip" to BuildInfo.chip,
            "Android" to "${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})",
            "Memory" to "${(mi.totalMem - mi.availMem) / 1048576} / ${mi.totalMem / 1048576} MB",
            "Storage" to (st?.let { "%.1f / %.1f GB".format((it.totalBytes - it.availableBytes) / 1e9, it.totalBytes / 1e9) } ?: "unknown"),
            "Battery" to if (bat >= 0) "$bat%${if (chg) " (charging)" else ""}" else "unknown",
            "Displays" to "${app.ctx.secondaryDisplays().size + 1}${if (app.dual) " (dual screen on)" else ""}",
            "Wi-Fi" to if (app.wifiConnected()) "Connected" else "Off",
        )
    }

    override fun handle(a: String) { if (a == "a" || a == "b") app.pop() }

    override fun draw(c: Canvas, w: Float, h: Float) {
        G.topBar(c, app, th, title, w)
        lines.forEachIndexed { i, (k, v) ->
            val y = 56 + i * 44f
            G.panel(c, RectF(16f, y, w - 16, y + 38), th)
            G.text(c, k, 30f, y + 9, 22f, th.c("text_dim"))
            G.text(c, v, w - 30, y + 9, 22f, th.c("text"), align = 'r', maxW = w - 200)
        }
    }

    override fun hints() = listOf("B" to "Back")
}
