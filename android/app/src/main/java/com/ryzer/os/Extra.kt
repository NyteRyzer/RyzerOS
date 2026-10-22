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
            "x" -> app.push(ThemeStudio(app, list[sel]))
            "start" -> PhotoTheme.pickAndApply(app)
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

    override fun hints() = listOf("START" to "Photo", "Y" to "Delete", "X" to "Theme Studio", "A" to "Apply")
}

// ----- Settings -----------------------------------------------------------------

fun settingsMenu(app: Ryzer): Menu {
    val cfg = app.cfg
    fun toggle(label: String, help: String = "", get: () -> Boolean, set: (Boolean) -> Unit) =
        Item(label, help, value = { if (get()) "On" else "Off" }, change = { set(!get()); cfg.save() })

    val emulators = { Menu(app, "Emulators", listOf(
        Item("How games are opened", "Auto: Ryzer shares the file directly (no setup needed), except .cue/.m3u discs. " +
            "Folder access: the emulator reads your ROM folder itself (give it access inside the emulator first). " +
            "Ryzer falls back to the other ways automatically if one is refused.",
            value = { mapOf("auto" to "Auto", "provider" to "Direct file", "folder" to "Folder access")[cfg.romAccess] ?: cfg.romAccess },
            change = { d -> cfg.romAccess = cycle(listOf("auto", "provider", "folder"), cfg.romAccess, d); cfg.save() }),
    ) + app.systems.map { s ->
        Item(s.name, "${s.demand}. ${s.note}", value = { Launcher.label(app, Launcher.chosen(app, s)) }, select = { app.push(EmulatorPicker(app, s)) })
    }) }

    val display = { Menu(app, "Display & dual screen", listOf(
        Item("Dual screen", "Auto uses a second display when the device has one (AYN Thor, RG DS, AYANEO Pocket DS, Retroid Dual).",
            value = { cfg.dual.replaceFirstChar { it.uppercase() } }, change = { d -> cfg.dual = cycle(listOf("auto", "on", "off"), cfg.dual, d); cfg.save(); app.ctx.updateDisplays() }),
        toggle("Swap screens", "Put the menu on the second display and art/info on the main one.", { cfg.swapScreens }) { cfg.swapScreens = it; app.ctx.updateDisplays() },
        Item("Swap button", "L3 / R3 (stick clicks) always swap the screens. Press A here, then press any other button to use it as well (e.g. a dedicated screen button).",
            value = { if (cfg.swapKey > 0) android.view.KeyEvent.keyCodeToString(cfg.swapKey).removePrefix("KEYCODE_") else "L3 / R3" },
            select = {
                app.notify("Press the button you want to use for swapping...", 6f)
                app.ctx.learningSwapKey = { code -> cfg.swapKey = code; cfg.save(); app.notify("Swap button set: ${android.view.KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")}") }
            }),
        Item("Reset swap button", select = { cfg.swapKey = -1; cfg.save(); app.notify("Swap button: L3 / R3") }),
        Item("Detected displays", value = { "${app.ctx.secondaryDisplays().size + 1}" }, help = "Ryzer frees the second screen while a game runs, so dual-screen emulators can use it."),
        toggle("Now Playing screen", "For single-screen systems (GBA, SNES, PSP...) the game opens on one screen and the other shows the game, a timer and quick controls. DS and 3DS keep both screens.",
            { cfg.companion }) { cfg.companion = it },
        Item("Single-screen games open on", "If games open on the wrong screen, switch this. You can also swap while playing: tap 'Swap screens' on the Now Playing screen.",
            value = { if (cfg.gameScreen == "second") "Second screen" else "Main screen" },
            change = { cfg.gameScreen = if (cfg.gameScreen == "second") "main" else "second"; cfg.save() }),
        Item("Remember swaps for each system", "Swap screens while playing a GBA game, and GBA games open there from then on.",
            value = { if (cfg.rememberSwap) "On" else "Off" }, change = { cfg.rememberSwap = !cfg.rememberSwap; cfg.save() }),
        Item("Reset screens for every system", value = { if (cfg.screenFor.isEmpty()) "None set" else cfg.screenFor.keys.joinToString(", ") { it.uppercase() } },
            select = { cfg.screenFor.clear(); cfg.save(); app.notify("Every system uses the default screen again") }),
        Item("Bedtime reminder", "A notification at this hour every night.", value = { if (cfg.bedtime < 0) "Off" else "%d:00".format(cfg.bedtime) },
            change = { d -> cfg.bedtime = when { cfg.bedtime < 0 && d > 0 -> 21; cfg.bedtime < 0 -> 23; else -> (cfg.bedtime + d).let { if (it > 23 || it < 0) -1 else it } }; cfg.save() }),
        Item("Break reminder", "The Now Playing screen buzzes and reminds you to take a break.", value = { if (cfg.breakMinutes == 0) "Off" else "Every ${cfg.breakMinutes} min" },
            change = { d -> cfg.breakMinutes = cycle(listOf(0, 30, 45, 60, 90), cfg.breakMinutes, d); cfg.save() }),
        Item("Daily play limit", "Shows time left on the Now Playing screen. In kid mode, games won't start once it's used up.",
            value = { if (cfg.dailyLimit == 0) "Off" else formatDuration(cfg.dailyLimit * 60L) },
            change = { d -> cfg.dailyLimit = cycle(listOf(0, 30, 60, 90, 120, 180, 240), cfg.dailyLimit, d); cfg.save() }),
        toggle("Animations", "Turn off for the snappiest menus.", { cfg.animations }) { cfg.animations = it },
        toggle("Boot animation", get = { cfg.boot }) { cfg.boot = it },
        Item("Menu frame rate", "30 fps saves battery; the menu still feels smooth.", value = { "${cfg.fps} fps" },
            change = { d -> cfg.fps = cycle(listOf(30, 60), cfg.fps, d); cfg.save() }),
        toggle("24-hour clock", get = { cfg.clock24 }) { cfg.clock24 = it },
        Item("Clock style", "How the big clock looks on the top screen.", value = { cfg.clockStyle.replaceFirstChar { it.uppercase() } },
            change = { d -> cfg.clockStyle = cycle(listOf("digital", "analog", "binary", "words"), cfg.clockStyle, d); cfg.save() }),
        toggle("Show battery", get = { cfg.showBattery }) { cfg.showBattery = it },
        Item("Screensaver", "Drifting box art and a clock after you stop pressing buttons. It also dims the screen.",
            value = { if (cfg.screensaver == 0) "Off" else "${cfg.screensaver} min" },
            change = { d -> cfg.screensaver = cycle(listOf(0, 1, 2, 5, 10, 15), cfg.screensaver, d); cfg.save() }),
    )) }

    val look = { Menu(app, "Look & feel", listOf(
        Item("Make a theme from a photo", "Pick any picture from your gallery: Ryzer uses it as the background and builds the colours from it.",
            select = { PhotoTheme.pickAndApply(app) }),
        Item("Your name", "Used for the greeting on the top screen.", value = { cfg.userName.ifEmpty { "Not set" } }, select = {
            app.push(Keyboard(app, "Your name", cfg.userName, 16) { cfg.userName = it; cfg.save() }) }),
        Item("Home layout", "3DS: banner on top, icon grid below. Ribbon: console-style row. Grid: everything at once.",
            value = { mapOf("3ds" to "3DS style", "ribbon" to "Ribbon", "grid" to "Grid")[cfg.homeLayout] ?: cfg.homeLayout },
            change = { d -> cfg.homeLayout = cycle(listOf("3ds", "ribbon", "grid"), cfg.homeLayout, d); cfg.save(); app.applyHomeLayout() }),
        Item("Icon rows (3DS layout)", value = { "${cfg.iconRows}" }, change = { d -> cfg.iconRows = (cfg.iconRows + d).coerceIn(2, 4); cfg.save() }),
        Item("Live wallpaper", "Animated backgrounds tinted by your theme. A theme can also set its own in Theme Studio.",
            value = { cfg.live.replaceFirstChar { it.uppercase() } }, change = { d -> cfg.live = cycle(Live.KINDS, cfg.live, d); cfg.save() }),
        Item("Live wallpaper speed", value = { "${cfg.liveSpeed}%" }, change = { d -> cfg.liveSpeed = (cfg.liveSpeed + d * 25).coerceIn(25, 300); cfg.save() }),
        toggle("Glass panels", "See-through panels when a wallpaper or photo theme is on.", { cfg.glass }) { cfg.glass = it },
        toggle("Box-art colours", "Tints panels with the colours of the selected game's box art.", { cfg.artTint }) { cfg.artTint = it },
        toggle("System colours", "The accent colour changes to match the system you're on.", { cfg.systemAccent }) { cfg.systemAccent = it },
        toggle("RGB mode", "Rainbow accent colours that slowly cycle, like gaming RGB.", { cfg.rgb }) { cfg.rgb = it },
        Item("Night light", "A warm filter that's easier on the eyes at night.", value = { if (cfg.nightLight == 0) "Off" else "${cfg.nightLight}%" },
            change = { d -> cfg.nightLight = (cfg.nightLight + d * 10).coerceIn(0, 50); cfg.save() }),
        Item("Boot animation style", value = { cfg.bootStyle.replaceFirstChar { it.uppercase() } },
            change = { d -> cfg.bootStyle = cycle(listOf("classic", "glitch", "minimal"), cfg.bootStyle, d); cfg.save() }),
        Item("Boot message", "Your own text on the boot screen.", value = { cfg.bootMessage.ifEmpty { "None" } }, select = {
            app.push(Keyboard(app, "Boot message", cfg.bootMessage, 30) { cfg.bootMessage = it; cfg.save() }) }),
        Item("Home tiles", "Choose which tiles show on Home.", select = { app.push(tileEditor(app)) }),
        Item("Icon shape (3DS layout)", value = { cfg.iconShape.replaceFirstChar { it.uppercase() } },
            change = { d -> cfg.iconShape = cycle(listOf("rounded", "circle", "square", "hexagon"), cfg.iconShape, d); cfg.save() }),
        toggle("Neon mode", "Glowing outlines on every panel.", { cfg.neon }) { cfg.neon = it },
        toggle("Screen transitions", "Screens slide in when they open.", { cfg.transitions }) { cfg.transitions = it },
        toggle("Seasonal effects", "Snow in winter, petals in spring, fireflies in summer, leaves in autumn (when no live wallpaper is on).",
            { cfg.seasonal }) { cfg.seasonal = it },
        toggle("Launch animation", "A short splash with the box art before a game starts.", { cfg.launchAnim }) { cfg.launchAnim = it },
        toggle("NEW badges", "Marks games added in the last 7 days.", { cfg.newBadges }) { cfg.newBadges = it },
        toggle("'Continue playing?' at startup", "Offers your last game when Ryzer starts.", { cfg.resumePrompt }) { cfg.resumePrompt = it },
        toggle("Photo-frame screensaver", "The screensaver shows your pictures from RyzerOS/photos.", { cfg.photoFrame }) {
            cfg.photoFrame = it; if (it) java.io.File(app.home, "photos").mkdirs() },
        Item("System backgrounds", "Put a picture named after a system in RyzerOS/backgrounds (e.g. gba.jpg) to show it behind that system's game list.",
            select = { java.io.File(app.home, "backgrounds").mkdirs(); app.push(Message(app, "System backgrounds",
                "Put pictures in ${app.home}/backgrounds named by system id:\n\n" + app.systems.joinToString(", ") { it.id } + "\n\nPNG, JPG or WEBP.")) }),
        Item("Bonus themes", "Extra themes you unlock by earning milestones.", value = { "${BonusThemes.unlocked(app).size}/${BonusThemes.ALL.size}" },
            select = { app.push(Message(app, "Bonus themes", BonusThemes.ALL.joinToString("\n") { (need, j) ->
                "${j.getString("name")}: ${if (app.state.milestones.size >= need) "unlocked" else "needs $need milestones"}" } +
                "\n\nYou have ${app.state.milestones.size} milestones.")) }),
        Item("Custom system tiles", "Put a picture named after a system in RyzerOS/tiles (e.g. gba.png, ps2.jpg) to use it as that system's tile.",
            select = { java.io.File(app.home, "tiles").mkdirs(); app.push(Message(app, "Custom system tiles",
                "Put pictures in ${app.home}/tiles named by system id:\n\n" + app.systems.joinToString(", ") { it.id } +
                "\n\nPNG, JPG or WEBP. They show on Home right away.")) }),
        Item("Interface size", "Bigger text and tiles for small screens, or more on screen for big ones.",
            value = { cfg.uiSize.replaceFirstChar { it.uppercase() } }, change = { d -> cfg.uiSize = cycle(listOf("small", "normal", "large"), cfg.uiSize, d); cfg.save() }),
        Item("Font", value = { cfg.font.replaceFirstChar { it.uppercase() } }, change = { d -> cfg.font = cycle(G.FONTS, cfg.font, d); G.setFont(cfg.font); cfg.save() }),
        Item("Wallpaper", "Put PNG or JPG images in RyzerOS/wallpapers, then pick one here.", value = { cfg.wallpaper.ifEmpty { "Off" } }, change = { d ->
            val files = listOf("") + (app.wallpaperDir().listFiles { f -> f.extension.lowercase() in setOf("png", "jpg", "jpeg", "webp") }?.map { it.name }?.sorted() ?: emptyList())
            if (files.size == 1) app.notify("Add images to ${app.wallpaperDir()} first")
            cfg.wallpaper = cycle(files, cfg.wallpaper, d); cfg.save() }),
        Item("Theme mode", "Fixed: your chosen theme. Daily: a new theme every day. Random: a surprise every start.",
            value = { cfg.themeMode.replaceFirstChar { it.uppercase() } }, change = { d -> cfg.themeMode = cycle(listOf("fixed", "daily", "random"), cfg.themeMode, d); cfg.save() }),
        toggle("Day / night themes", "Switches between a day theme (7am-7pm) and a night theme automatically.", { cfg.dayNight }) { cfg.dayNight = it; app.checkSoon() },
        Item("Day theme", value = { app.themes[cfg.dayTheme]?.name ?: cfg.dayTheme }, change = { d -> cfg.dayTheme = cycle(app.themes.keys.toList(), cfg.dayTheme, d); cfg.save(); app.checkSoon() }),
        Item("Night theme", value = { app.themes[cfg.nightTheme]?.name ?: cfg.nightTheme }, change = { d -> cfg.nightTheme = cycle(app.themes.keys.toList(), cfg.nightTheme, d); cfg.save(); app.checkSoon() }),
        Item("Home system order", value = { mapOf("default" to "Default", "name" to "A-Z", "games" to "Most games", "recent" to "Recently played")[cfg.systemSort] ?: cfg.systemSort },
            change = { d -> cfg.systemSort = cycle(listOf("default", "name", "games", "recent"), cfg.systemSort, d); cfg.save() }),
        Item("Show / hide systems", "Choose which systems appear on Home.", select = { app.push(Menu(app, "Systems on Home", app.systems.map { s ->
            Item(s.name, value = { if (s.id in cfg.hiddenSystems) "Hidden" else "Shown" }, change = {
                if (!cfg.hiddenSystems.remove(s.id)) cfg.hiddenSystems.add(s.id); cfg.save() }) })) }),
    )) }

    val sound = { Menu(app, "Sound & music", listOf(
        toggle("Menu sounds", get = { cfg.sound }) { cfg.sound = it; app.sounds.enabled = it },
        Item("Menu volume", value = { "${cfg.volume}%" }, change = { d -> cfg.volume = (cfg.volume + d * 10).coerceIn(0, 100); cfg.save(); app.sounds.setVolume(cfg.volume); app.sounds.play("move") }),
        Item("Sound pack", "Classic tones, 8-bit chiptune, or soft and quiet.", value = { cfg.soundPack.replaceFirstChar { it.uppercase() } },
            change = { d -> cfg.soundPack = cycle(listOf("classic", "chiptune", "soft"), cfg.soundPack, d); cfg.save(); app.sounds.setPack(cfg.soundPack); app.sounds.play("select") }),
        toggle("Menu music", "Plays your own songs from RyzerOS/music while you browse (MP3, OGG, M4A, FLAC, WAV). Pauses during games.",
            { cfg.music }) { cfg.music = it; if (it) { if (app.music.trackCount() == 0) app.notify("Add songs to ${app.music.dir()} first"); app.music.next() } else app.music.stop() },
        Item("Music volume", value = { "${cfg.musicVolume}%" }, change = { d -> cfg.musicVolume = (cfg.musicVolume + d * 10).coerceIn(0, 100); cfg.save(); app.music.setVolume() }),
        Item("Next song", value = { app.music.nowPlaying.ifEmpty { "-" } }, select = { app.music.next() }),
    )) }

    val library = { Menu(app, "Library", listOf(
        Item("ROM folders", "Where Ryzer looks for <system> folders like 3ds, gc, ps2.", value = { "${app.romRoots().size}" }, select = { app.push(romFoldersMenu(app)) }),
        Item("Rescan ROMs", select = { app.rescan { app.notify("Found ${app.systems.sumOf { it.games.size }} games") } }),
        Item("Artwork", "Downloads box art, screenshots or title screens for your games automatically.",
            value = { if (Scraper.running) "${Scraper.checked}/${Scraper.total}" else if (cfg.autoArt) "Automatic" else "Manual" },
            select = { app.push(artworkMenu(app)) }),
        toggle("Clean game names", "Hides tags like \"(USA) [!]\".", { cfg.cleanNames }) { cfg.cleanNames = it; app.rescan() },
        toggle("Hide empty systems", get = { cfg.hideEmpty }) { cfg.hideEmpty = it },
        toggle("Show Apps tile", get = { cfg.showApps }) { cfg.showApps = it },
        Item("Reload systems & emulators", "Re-reads systems.json / emulators.json from the RyzerOS folder if you edited them.", select = { app.reloadData(); app.notify("Reloaded") }),
        Item("Collections", "Rename or delete your collections.", value = { "${app.state.collections.size}" }, select = { app.push(manageCollectionsMenu(app)) }),
        toggle("Show hidden games", get = { cfg.showHidden }) { cfg.showHidden = it },
        Item("Duplicate finder", "Finds the same game more than once (other regions or copies).", select = { app.push(duplicatesMenu(app)) }),
        Item("Storage by system", "How much space each system's games use.", select = { app.push(StorageScreen(app)) }),
        Item("Clear recently played", select = { app.push(Confirm(app, "Clear your recently played list?") { app.state.recent.clear(); app.state.save(); app.notify("Cleared") }) }),
    )) }

    val performance = { Menu(app, "Performance", listOf(
        toggle("Boost before heavy games", "Closes cached background apps before Switch, 3DS, Wii U, GameCube, Wii, PS2 and Vita games to free RAM.", { cfg.boost }) { cfg.boost = it },
        Item("Free memory now", select = { Launcher.boost(app.ctx, ""); app.notify("Background apps closed") }),
        Item("Device performance mode", "Opens Android settings. AYN, Retroid and Anbernic put their CPU/fan performance modes in quick settings or a dedicated settings page.",
            select = { runCatching { app.ctx.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }),
        toggle("Battery saver", "Below 20% battery: 20 fps menus and no animations. Also warns at 15% and 5%.", { cfg.batterySaver }) { cfg.batterySaver = it },
        Item("Speed tips", select = { app.push(Message(app, "Speed tips", SPEED_TIPS)) }),
    )) }

    val controls = { Menu(app, "Controls", listOf(
        toggle("Swap A / B", "Use the other face button to confirm (for Nintendo-style layouts).", { cfg.swapAB }) { cfg.swapAB = it },
        toggle("Vibration", "A light tick of rumble on button presses (devices with a vibration motor).", { cfg.haptics }) { cfg.haptics = it },
        Item("Controls help", select = { app.push(Message(app, "Controls", "D-pad / stick: move\nA: open / play   B: back\nX: favorite / random   Y: search\n" +
            "L / R: page or letter jump\nL + R together: quick menu (volume, brightness, Wi-Fi...)\n" +
            "START: options / settings   SELECT: themes (Home) or grid/list (game lists)\nTouch: tap anything, including the button hints.")) }),
    )) }

    val personalize = { Menu(app, "Personalize", listOf(
        Item("Theme Studio", "Design every colour, pattern, effect and font of your own theme.", select = { app.push(ThemeStudio(app, app.theme)) }),
        Item("Themes", "Pick from your themes.", select = { app.push(ThemePicker(app)) }),
        Item("Theme from a photo", select = { PhotoTheme.pickAndApply(app) }),
        Item("Profile", "Your name and picture.", select = { app.push(profileMenu(app)) }),
        Item("Look & feel", "Layout, live wallpaper, effects, sizes and fonts.", select = { app.push(look()) }),
        Item("Sound & music", select = { app.push(sound()) }),
        Item("Swap screens now", select = { app.ctx.toggleSwap() }),
    )) }

    return Menu(app, "Settings", listOf(
        Item("Personalize", "Theme Studio, themes, profile, layout, live wallpaper, sounds.", select = { app.push(personalize()) }),
        Item("Notifications", "Notification centre, banners and do not disturb.", value = { "${Notifs.unread()} new" }, select = { app.push(NotificationsScreen(app)) }),
        Item("Emulators", "Choose which emulator each system uses. Any installed app works.", select = { app.push(emulators()) }),
        Item("Library", "ROM folders, rescanning and naming.", select = { app.push(library()) }),
        Item("Display & dual screen", select = { app.push(display()) }),
        Item("Look & feel", "Interface size, font, wallpaper, theme schedule and Home layout.", select = { app.push(look()) }),
        Item("Play stats", "Play time, 7-day chart, top games and milestones.", select = { app.push(StatsScreen(app)) }),
        Item("Tools & games", "Snake, 2048, Game Roulette, play calendar, stats card, screenshots, timer, controller test, battery.",
            select = { app.push(toolsMenu(app)) }),
        Item("Performance", select = { app.push(performance()) }),
        Item("Themes", "Pick one of 20+ themes or build your own.", select = { app.push(ThemePicker(app)) }),
        Item("Sound & music", select = { app.push(sound()) }),
        Item("Controls", select = { app.push(controls()) }),
        Item("Set as home screen", "Make Ryzer OS your device's home screen, like Cocoon or iiSU.", select = {
            runCatching { app.ctx.startActivity(Intent(android.provider.Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { app.notify("Open Android Settings > Apps > Default apps > Home app") } }),
        Item("Kid mode", "Locks settings and file actions behind a PIN, so kids can only play.", select = { KidMode.enable(app) }),
        Item("Back up settings", "Saves settings, play data and your themes to RyzerOS/backups.", select = {
            runCatching { Backup.create(app) }.onSuccess { app.notify("Saved ${it.name}") }.onFailure { app.notify("Backup failed: ${it.message}") } }),
        Item("Restore latest backup", select = {
            val b = Backup.latest(app)
            if (b == null) app.notify("No backups in ${app.home}/backups")
            else app.push(Confirm(app, "Restore ${b.name}? Current settings will be replaced.", yes = "Restore") { Backup.restore(app, b) }) }),
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

/** Picks the emulator for a whole system, or (with [game]) an override for one game. */
class EmulatorPicker(app: Ryzer, private val sys: GameSystem, private val game: Game? = null)
    : Menu(app, if (game != null) "Emulator: ${app.title(game)}" else "${sys.short} emulator", emptyList(), sys.demand) {
    init {
        val cur = if (game != null) app.state.gameEmu[game.key] else Launcher.chosen(app, sys)
        val list = mutableListOf<Item>()
        for ((id, installed) in Launcher.choices(app, sys)) {
            val name = Launcher.label(app, id)
            val pkg = Launcher.emulator(app, id)?.components?.firstOrNull()?.substringBefore("/")
            list += Item((if (id == cur) "> " else "") + name,
                if (installed) "Installed" else "Not installed. Press A to open its store page.",
                value = { if (installed) "Installed" else "Get it" },
                select = { if (installed) pick(id) else if (pkg != null) openStore(app, pkg) })
        }
        list += Item((if (cur == Launcher.CHOOSER) "> " else "") + "Ask every time", "Shows Android's 'Open with' list on each launch.", select = { pick(Launcher.CHOOSER) })
        list += Item((if (cur?.startsWith(Launcher.ANY_APP_PREFIX) == true) "> " else "") + "Any app...",
            "Use any installed emulator, even one Ryzer doesn't know.", select = { app.push(AppsScreen(app) { pkg -> pick(Launcher.ANY_APP_PREFIX + pkg) }) })
        val hasOverride = if (game != null) app.state.gameEmu.containsKey(game.key) else app.cfg.systemEmu.containsKey(sys.id)
        if (hasOverride) list += Item(if (game != null) "Use the system's emulator" else "Reset to automatic", select = {
            if (game != null) { app.state.gameEmu.remove(game.key); app.state.save() } else { app.cfg.systemEmu.remove(sys.id); app.cfg.save() }
            app.pop() })
        items = list
    }

    private fun pick(id: String) {
        if (game != null) { app.state.gameEmu[game.key] = id; app.state.save() }
        else { app.cfg.systemEmu[sys.id] = id; app.cfg.save() }
        app.notify("${if (game != null) app.title(game) else sys.short}: ${Launcher.label(app, id)}")
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
