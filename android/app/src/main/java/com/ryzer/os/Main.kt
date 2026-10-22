package com.ryzer.os

import android.Manifest
import android.app.Activity
import android.app.Presentation
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.RectF
import android.hardware.display.DisplayManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Display
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

enum class Role { MAIN, TOP }

/** App-wide state and actions shared by every screen. */
class Ryzer(val ctx: MainActivity) {
    companion object {
        /** The running instance, so the Now Playing panel (its own activity) can reach it. */
        var instance: Ryzer? = null
    }

    val home: File = pickHome()
    val cfg = Config(File(home, "config.json"))
    val state = PlayState(File(home, "state.json"))
    var themes = Themes.loadAll(this)
    var theme: Theme = themes[cfg.theme] ?: themes.values.first()
    private var preview: Theme? = null
    /** The theme chosen by the user (or being previewed), before live effects. */
    val baseTheme get() = preview ?: theme
    private var frameTheme: Theme? = null
    /** The theme to draw with this frame: base theme plus RGB / system-colour effects. */
    val activeTheme: Theme get() = frameTheme ?: baseTheme
    /** Set by Home to the selected system's colour when "System colours" is on. */
    var accentOverride: Int? = null
    private var appliedFont = ""
    /** Last time the top screen (second display or split half) was drawn. */
    var topShownAt = 0L
    var emulators: Map<String, Emulator> = Library.loadEmulators(this)
    var systems: List<GameSystem> = Library.loadSystems(this)
    val stack = ArrayList<Screen>()
    val current get() = stack.lastOrNull()
    var scanning = false
    private var toastMsg = ""
    private var toastUntil = 0L
    val sounds = Sounds(cfg.sound, cfg.volume, cfg.soundPack)
    val music = MenuMusic(this)
    private val particles = Particles()
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    val dual get() = ctx.dualActive
    var focusTheme: String? = null
    var lastInput = SystemClock.uptimeMillis()
    private var checkTimer = 99f
    private var batteryLow = false
    private var warned15 = false
    private var warned5 = false
    private var timeTheme: String? = null
    private var bedtimeShown = ""

    init {
        instance = this
        Library.coversDir = Scraper.coversDir(this)
        G.setFont(cfg.font)
        when (cfg.themeMode) {   // theme of the day / random theme on every start
            "daily" -> themes.keys.sorted().let { ids -> setTheme(ids[(today().hashCode() and 0x7fffffff) % ids.size], save = false) }
            "random" -> setTheme(themes.keys.random(), save = false)
        }
    }

    /** The game's display name: your custom title if you renamed it. */
    fun title(g: Game) = state.titles[g.key] ?: g.name

    /** Drops hidden games unless "Show hidden games" is on. */
    fun visible(list: List<Game>) = if (cfg.showHidden) list else list.filter { it.key !in state.hidden }

    fun allGames() = visible(systems.flatMap { it.games })

    val logicalH get() = when (cfg.uiSize) { "small" -> 540f; "large" -> 420f; else -> 480f }
    val animationsOn get() = cfg.animations && !batteryLow
    fun effectiveFps() = if (batteryLow) 20 else cfg.fps

    private fun pickHome(): File {
        val shared = File(Environment.getExternalStorageDirectory(), "RyzerOS")
        if (hasStorageAccess(ctx) && (shared.isDirectory || shared.mkdirs())) return shared
        return ctx.getExternalFilesDir(null) ?: ctx.filesDir
    }

    fun userThemesDir() = File(home, "themes").apply { mkdirs() }

    fun romRoots(): List<File> = (cfg.romDirs.ifEmpty { Library.defaultRomDirs() }).map { File(it) }

    // ----- screen stack -----
    fun push(s: Screen) { stack.add(s); ctx.redraw() }
    fun pop() { if (stack.size > 1) { stack.removeAt(stack.size - 1); current?.onResume() } ; ctx.redraw() }
    fun replace(s: Screen) { if (stack.isNotEmpty()) stack.removeAt(stack.size - 1); stack.add(s) }
    fun goHome() { while (stack.size > 1) stack.removeAt(stack.size - 1); current?.onResume() }

    /** Switches Home between the 3DS, ribbon and grid layouts right away. */
    fun applyHomeLayout() {
        if (stack.isEmpty()) return
        stack[0] = if (cfg.homeLayout == "3ds") HomeShell(this) else HomeScreen(this)
    }

    // ----- themes -----
    fun setTheme(id: String, save: Boolean = true) {
        themes[id]?.let { theme = it; preview = null; if (save) { cfg.theme = id; cfg.save() } }
    }
    fun previewTheme(t: Theme) { preview = t }

    private fun decorate(base: Theme, now: Float): Theme {
        val hue = (now * 40f) % 360f
        val accent = when {
            cfg.rgb -> android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.75f, 1f))
            cfg.systemAccent -> accentOverride
            else -> null
        } ?: return base
        val colors = base.colors.toMutableMap()
        colors["accent"] = accent
        if (cfg.rgb) colors["accent2"] = android.graphics.Color.HSVToColor(floatArrayOf((hue + 120f) % 360f, 0.6f, 1f))
        return Theme(base.id, base.name, colors, base.style, base.builtin, base.author)
    }

    fun runInBackground(work: () -> Unit, done: (Throwable?) -> Unit) {
        io.execute { val e = runCatching(work).exceptionOrNull(); main.post { done(e) } }
    }
    fun endPreview() { preview = null }

    // ----- library -----
    fun rescan(done: (() -> Unit)? = null) {
        if (scanning) return
        scanning = true
        val roots = romRoots()
        val list = systems
        io.execute {
            val error = runCatching { Library.scan(list, roots, cfg.cleanNames) }.exceptionOrNull()
            main.post {
                scanning = false
                if (error != null) notify("Scan problem: ${error.javaClass.simpleName}", 4f)
                else if (state.noteSeen(systems.flatMap { s -> s.games.map { it.key } })) state.save()
                safely { current?.onResume(); done?.invoke() }
                if (error == null && cfg.autoArt && !cfg.kidMode) safely { Scraper.start(this, allGames(), auto = true, onlyMissing = true) }
                safely { Updater.maybeCheck(this) }
                ctx.redraw()
            }
        }
    }

    fun reloadData() {
        emulators = Library.loadEmulators(this)
        systems = Library.loadSystems(this)
        rescan()
    }

    /** Starts a game, with the launch animation first when it's on. */
    fun launch(game: Game) {
        if (cfg.launchAnim && animationsOn && current !is LaunchSplash) push(LaunchSplash(this, game)) else launchNow(game)
    }

    fun launchNow(game: Game) {
        val sys = game.system
        val choice = state.gameEmu[game.key] ?: Launcher.chosen(this, sys)
        val intents: List<Intent> = when {
            choice == null -> emptyList()
            choice == Launcher.CHOOSER -> listOf(Launcher.anyAppIntent(game, null))
            choice.startsWith(Launcher.ANY_APP_PREFIX) -> listOf(Launcher.anyAppIntent(game, choice.removePrefix(Launcher.ANY_APP_PREFIX)))
            else -> Launcher.emulator(this, choice)?.let { Launcher.candidates(this, it, game) } ?: emptyList()
        }
        if (intents.isEmpty()) {
            sounds.play("error")
            notify("No ${sys.short} emulator installed - pick one")
            push(EmulatorPicker(this, sys))
            return
        }
        if (cfg.dailyLimit > 0 && state.playedToday() >= cfg.dailyLimit * 60L) {
            if (cfg.kidMode) {
                sounds.play("error")
                push(Message(this, "Time's up for today", "Today's play time limit (${formatDuration(cfg.dailyLimit * 60L)}) has been reached. " +
                    "See you tomorrow!"))
                return
            }
            notify("Over today's ${formatDuration(cfg.dailyLimit * 60L)} play limit", 3f)
        }
        val first = intents.first()
        val target = first.component?.packageName ?: first.`package` ?: ""
        val name = Launcher.label(this, choice)
        // Dual-screen devices: single-screen systems get the game on one display and the
        // Now Playing panel on the other. DS / 3DS keep both screens for the emulator.
        val screens = if (DualPlay.wanted(this, game)) DualPlay.displays(this, game) else null
        val start = Runnable {
            var lastError: Throwable? = null
            state.startPlay(game.key)
            music.pause()
            var gameOptions: android.os.Bundle? = null
            if (screens != null) {
                // The panel is already drawn behind Ryzer's top screen (see below): uncover it as the game starts
                ctx.releaseSecondScreen()
                val gameDisplay = screens.first
                if (gameDisplay != ctx.mainDisplayId()) gameOptions = DualPlay.optionsFor(gameDisplay)
            }
            for (intent in intents) {
                try {
                    var shownOn = screens?.first
                    try {
                        ctx.startActivity(intent, gameOptions)
                    } catch (e: SecurityException) {
                        if (gameOptions == null) throw e
                        ctx.startActivity(intent)   // this device won't open apps on that screen: use the default one
                        shownOn = ctx.mainDisplayId()
                    }
                    // Remember which emulator and screen actually took the game, for "Swap screens"
                    if (screens != null && shownOn != null) {
                        val pkg = intent.component?.packageName ?: intent.`package` ?: target
                        DualPlay.begin(game, pkg, shownOn, screens.second)
                    }
                    return@Runnable
                } catch (e: Exception) {   // ActivityNotFound, SecurityException, FileUriExposed...
                    lastError = e
                    Crash.log(ctx, RuntimeException("Launch attempt failed for $name: ${intent.toUri(0)}", e))
                }
            }
            DualPlay.close()
            state.pendingKey = null
            state.save()
            sounds.play("error")
            val why = lastError?.message?.take(80) ?: "unknown error"
            notify("$name couldn't start: $why", 6f)
            push(Message(this, "Couldn't start $name",
                "Ryzer tried ${intents.size} ways to open ${title(game)} in $name and Android refused all of them.\n\n" +
                "Last error: $why\n\nTry this:\n" +
                "1. Open $name once by itself and give it access to your ROM folder (${game.file.parent}).\n" +
                "2. Update $name to its newest version.\n" +
                "3. In this game's options (START), pick 'Open with another app' or another emulator.\n\n" +
                "The full error was saved to ${Crash.file(ctx).path}."))
        }
        notify("Launching ${title(game)}")
        // Freeing memory used to happen before the game started (a few seconds on slow chips); it now runs
        // alongside, while the emulator is loading. The emulator's own package is never touched.
        if (cfg.boost && Launcher.isHeavy(sys)) io.execute { Launcher.boost(ctx, target) }
        if (screens == null) { start.run(); return }
        // Dual screen: open the Now Playing panel first, behind Ryzer's top screen, and start the game once
        // it has drawn. The second screen goes straight from Ryzer to the panel, never blank. The game
        // still starts last, so it gets the controller.
        DualPlay.begin(game, target, screens.first, screens.second)
        val once = java.util.concurrent.atomic.AtomicBoolean(false)
        val go = Runnable { if (once.compareAndSet(false, true)) safely { start.run() } }
        if (DualPlay.open(this, game, screens.second, onReady = { main.post(go) })) main.postDelayed(go, 900)   // in case it never reports
        else go.run()
    }

    fun openSearch() {
        push(Keyboard(this, "Search games, apps & settings") { q ->
            if (q.isNotEmpty()) push(universalSearch(this, q))
        })
    }

    fun openRandom() {
        randomFrom(allGames())
    }

    fun randomFrom(games: List<Game>) {
        if (games.isEmpty()) { sounds.play("error"); notify("No games found yet"); return }
        val g = games.random()
        notify("Surprise! ${title(g)}")
        launch(g)
    }

    fun notify(msg: String, seconds: Float = 2.2f) {
        toastMsg = msg; toastUntil = SystemClock.uptimeMillis() + (seconds * 1000).toLong(); ctx.redraw()
    }

    // ----- device status -----
    // Battery and Wi-Fi are read at most every few seconds: both are system calls and the
    // top bar is drawn every frame on both screens, which is too slow on RK3568-class chips.
    private var batteryCache = -1 to false
    private var batteryAt = 0L
    private var wifiCache = false
    private var wifiAt = 0L

    fun battery(): Pair<Int, Boolean> {
        val now = SystemClock.uptimeMillis()
        if (now - batteryAt > 5000) { batteryAt = now; batteryCache = runCatching { readBattery() }.getOrDefault(-1 to false) }
        return batteryCache
    }

    fun wifiConnected(): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now - wifiAt > 5000) { wifiAt = now; wifiCache = runCatching { readWifi() }.getOrDefault(false) }
        return wifiCache
    }

    private fun readBattery(): Pair<Int, Boolean> {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return -1 to false
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        if (level < 0) return -1 to false
        return level * 100 / scale to (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
    }

    private fun readWifi(): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    // ----- drawing -----
    val hits = HashMap<Role, MutableList<Pair<RectF, () -> Unit>>>()
    private var hitRole = Role.MAIN

    /** Screens call this while drawing to make a region tappable. */
    fun hit(r: RectF, action: () -> Unit) { hits.getOrPut(hitRole) { mutableListOf() }.add(RectF(r) to action) }

    fun tap(role: Role, x: Float, y: Float) {
        val list = hits[role] ?: return
        for ((r, action) in list.reversed()) if (r.contains(x, y)) { action(); ctx.redraw(); return }
    }

    private var wallpaperName = ""
    private var wallpaperBmp: android.graphics.Bitmap? = null
    fun wallpaperDir() = File(home, "wallpapers").apply { mkdirs() }
    /** A photo theme's own picture wins over the wallpaper setting. */
    fun wallpaper(): android.graphics.Bitmap? {
        val name = baseTheme.wallpaper.ifEmpty { cfg.wallpaper }
        if (name.isEmpty()) return null
        if (name != wallpaperName) {
            wallpaperName = name
            wallpaperBmp = G.image(File(wallpaperDir(), name))
        }
        return wallpaperBmp
    }

    fun drawBigClock(c: Canvas, w: Float, y: Float) {
        val th = activeTheme
        if (cfg.clockStyle == "analog") { G.analogClock(c, th, w / 2, y + 60, 70f); return }
        if (cfg.clockStyle == "binary") { drawBinaryClock(c, th, w, y); return }
        if (cfg.clockStyle == "words") {
            val lines = G.wrap(wordTime(), 40f, w - 60)
            lines.forEachIndexed { i, l -> G.text(c, l, w / 2, y + 10 + i * 42, 40f, th.c("text"), align = 'c', isBold = true) }
            G.text(c, java.text.SimpleDateFormat("EEEE, MMMM d", java.util.Locale.getDefault()).format(java.util.Date()), w / 2, y + 20 + lines.size * 42, 24f,
                th.c("accent"), align = 'c')
            return
        }
        val now = java.util.Date()
        val fmt = java.text.SimpleDateFormat(if (cfg.clock24) "HH:mm" else "h:mm", java.util.Locale.getDefault())
        G.text(c, fmt.format(now), w / 2, y, 110f, th.c("text"), align = 'c', isBold = true)
        G.text(c, java.text.SimpleDateFormat("EEEE, MMMM d", java.util.Locale.getDefault()).format(now), w / 2, y + 92, 28f,
            th.c("accent"), align = 'c')
    }

    /** Hours, minutes and seconds as columns of lit dots (binary-coded decimal). */
    private fun drawBinaryClock(c: Canvas, th: Theme, w: Float, y: Float) {
        val cal = java.util.Calendar.getInstance()
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY).let { if (cfg.clock24) it else (it + 11) % 12 + 1 }
        val digits = "%02d%02d%02d".format(hour, cal.get(java.util.Calendar.MINUTE), cal.get(java.util.Calendar.SECOND))
        val r = 11f; val gap = 34f
        val left = w / 2 - (6 * gap + 20) / 2 + gap / 2
        digits.forEachIndexed { i, ch ->
            val v = ch - '0'
            val x = left + i * gap + (i / 2) * 10
            for (bit in 0 until 4) {
                val cy = y + 120 - bit * gap
                if (v and (1 shl bit) != 0) G.circle(c, x, cy, r, th.c("accent")) else G.ring(c, x, cy, r, withAlpha(th.c("text_dim"), 0.5f), 2f)
            }
        }
        G.text(c, "%s:%s:%s".format(digits.substring(0, 2), digits.substring(2, 4), digits.substring(4)), w / 2, y + 146, 24f, th.c("text_dim"), align = 'c')
    }

    /** "quarter past seven", "twenty to nine"... */
    fun wordTime(): String {
        val n = listOf("twelve", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve")
        val cal = java.util.Calendar.getInstance()
        val m = (cal.get(java.util.Calendar.MINUTE) + 2) / 5 * 5
        val h = cal.get(java.util.Calendar.HOUR) + if (m > 30) 1 else 0
        val hour = n[h % 12]
        val fives = mapOf(5 to "five", 10 to "ten", 20 to "twenty", 25 to "twenty-five")
        return when (m) {
            0, 60 -> "$hour o'clock"
            15 -> "quarter past $hour"
            30 -> "half past $hour"
            45 -> "quarter to $hour"
            in 1..29 -> "${fives[m]} past $hour"
            else -> "${fives[60 - m]} to $hour"
        }
    }

    fun drawDefaultTop(c: Canvas, w: Float, h: Float) {
        G.topBar(c, this, activeTheme, "", w)
        drawBigClock(c, w, h / 2 - 80)
    }

    fun render(c: Canvas, role: Role, w: Float, h: Float, dt: Float, t: Float) {
        if (current !is HomeScreen && current !is HomeShell) accentOverride = null
        if (role == Role.TOP) topShownAt = SystemClock.uptimeMillis()
        frameTheme = decorate(baseTheme, t)
        val th = activeTheme
        hitRole = role
        hits[role] = mutableListOf()
        G.background(c, th, w, h, t)
        val wp = wallpaper()
        if (wp != null) G.wallpaper(c, th, wp, w, h)
        val liveKind = (baseTheme.style["live"] as? String)?.takeIf { it != "off" } ?: cfg.live
        if (liveKind != "off" && animationsOn) Live.draw(c, th, liveKind, w, h, t, cfg.liveSpeed / 100f)
        if (cfg.seasonal && animationsOn && liveKind == "off" && (role == Role.TOP || SystemClock.uptimeMillis() - topShownAt > 1000)) Live.draw(c, th, Live.seasonal(), w, h, t, 0.8f)
        G.neon = cfg.neon
        val font = (baseTheme.style["font"] as? String)?.takeIf { it != "default" } ?: cfg.font
        if (font != appliedFont) { appliedFont = font; G.setFont(font) }
        G.panelAlpha = if (wp != null && cfg.glass) min(th.panelAlpha, 0.8f) else th.panelAlpha
        try { renderContent(c, role, th, w, h, dt) } finally {
            if (cfg.nightLight > 0) G.rect(c, RectF(0f, 0f, w, h), withAlpha(0xFFFF8A00.toInt(), cfg.nightLight / 100f * 0.5f))
        }
    }

    private var lastDrawn: Screen? = null
    private var transitionAt = 0L

    private fun renderContent(c: Canvas, role: Role, th: Theme, w: Float, h: Float, dt: Float) {
        if (role == Role.MAIN && animationsOn) particles.draw(c, th, dt, w, h)
        val scr = current ?: return
        if (role == Role.MAIN) {
            if (scr !== lastDrawn) { lastDrawn = scr; transitionAt = SystemClock.uptimeMillis() }
            val age = SystemClock.uptimeMillis() - transitionAt
            val slide = if (cfg.transitions && animationsOn && age < 180) (1f - age / 180f).let { it * it } * 40f else 0f
            try {
                c.save(); c.translate(slide, 0f)
                try { scr.draw(c, w, h) } finally { c.restore() }
            } catch (e: Throwable) {
                Crash.log(ctx, e)
                G.text(c, "This screen hit an error and was closed.", w / 2, h / 2, 24f, th.c("bad"), align = 'c')
                if (stack.size > 1 && stack.last() === scr) { stack.removeAt(stack.size - 1); main.post { ctx.redraw() } }
                return
            }
            G.hints(c, th, scr.hints(), w, h) { r, action -> hit(r) { handle(action) } }
            val now = SystemClock.uptimeMillis()
            if (now < toastUntil) G.toast(c, th, toastMsg, ((toastUntil - now) / 330f).coerceAtMost(1f), w, h)
            Notifs.drawBanner(c, this, th, w)
            if (scanning) G.text(c, "Scanning...", w - 16, 46f, 18f, th.c("accent"), align = 'r')
            else if (Scraper.running && scr !is ScrapeScreen) G.text(c, "Artwork ${Scraper.checked}/${Scraper.total}", w - 16, 46f, 18f, th.c("accent"), align = 'r')
        } else {
            runCatching { scr.drawTop(c, w, h) }.onFailure { Crash.log(ctx, it) }
        }
    }

    fun handle(action: String) {
        lastInput = SystemClock.uptimeMillis()
        if (cfg.haptics) ctx.buzz()
        safely { current?.handle(action) }
        ctx.redraw()
    }

    /**
     * Runs UI code so that a bug in one screen can't close the whole app: the error is
     * logged to crash.txt, the broken screen is closed and a message is shown instead.
     */
    fun safely(block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            Crash.log(ctx, e)
            val bad = current
            if (stack.size > 1) stack.removeAt(stack.size - 1)
            notify("Something went wrong in ${bad?.javaClass?.simpleName ?: "Ryzer"} - it was closed", 4f)
        }
    }

    /** A finished game session: show a summary and check milestones. */
    fun onSession(session: Pair<String, Long>?) {
        session ?: return
        val g = Library.find(systems, session.first)
        if (session.second >= 60) notify("You played ${g?.let { title(it) } ?: "a game"} for ${formatDuration(session.second)}", 3.5f)
        Milestones.check(this)
    }

    fun update(dt: Float) {
        safely { current?.update(dt) }
        checkTimer += dt
        if (checkTimer < 30f) return
        checkTimer = 0f
        // Battery: warnings and automatic power saving
        val (pct, charging) = battery()
        batteryLow = cfg.batterySaver && pct in 0..19 && !charging
        if (pct in 0..5 && !charging && !warned5) {
            warned5 = true; sounds.play("error"); notify("Battery at $pct% - plug in soon!", 4f)
            if (cfg.ryzerNotices) Notifs.ryzer("Battery at $pct%", "Plug in soon - Ryzer will keep your place.", banner = false)
        } else if (pct in 0..15 && !charging && !warned15) {
            warned15 = true; notify("Battery low ($pct%)", 3f)
            if (cfg.ryzerNotices) Notifs.ryzer("Battery low", "$pct% left.", banner = false)
        }
        if (charging) { warned5 = false; warned15 = false }
        // Day / night theme
        if (cfg.dayNight) {
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            val want = if (hour in 7..18) cfg.dayTheme else cfg.nightTheme
            if (want != timeTheme && themes.containsKey(want)) { timeTheme = want; setTheme(want, save = false) }
        }
        // Bedtime reminder, once per night
        if (cfg.bedtime >= 0) {
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            if (hour == cfg.bedtime && bedtimeShown != today()) {
                bedtimeShown = today()
                sounds.play("boot")
                Notifs.ryzer("It's bedtime", "Time to save your game and get some rest. Good night${if (cfg.userName.isNotBlank()) ", ${cfg.userName}" else ""}!")
            }
        }
        // Screensaver
        val idle = (SystemClock.uptimeMillis() - lastInput) / 60000f
        if (cfg.screensaver > 0 && idle >= cfg.screensaver && current !is Screensaver && current !is BootScreen) push(Screensaver(this))
    }

    fun checkSoon() { checkTimer = 99f }

    /** "Continue playing X?" once at startup, after the boot animation. */
    fun offerResume(tries: Int = 0) {
        if (current is BootScreen) { if (tries < 20) main.postDelayed({ offerResume(tries + 1) }, 400); return }
        if (stack.size != 1) return
        val g = visible(state.recent.mapNotNull { Library.find(systems, it) }).firstOrNull() ?: return
        push(ResumePrompt(this, g))
    }
}

/** One drawing surface: the main screen, the top screen, or both stacked on a tall single display. */
class RyzerView(context: Context, private val app: () -> Ryzer?, var role: Role) : View(context) {
    private var last = SystemClock.uptimeMillis()

    init { isFocusable = role == Role.MAIN; isFocusableInTouchMode = role == Role.MAIN }

    private fun regions(): List<Pair<Role, RectF>> {
        val w = width.toFloat(); val h = height.toFloat()
        val a = app() ?: return emptyList()
        val split = role == Role.MAIN && !a.dual && a.cfg.dual != "off" && h >= w * 1.5f
        val upper = RectF(0f, 0f, w, h / 2); val lower = RectF(0f, h / 2, w, h)
        return if (split) (if (a.cfg.swapScreens) listOf(Role.MAIN to upper, Role.TOP to lower) else listOf(Role.TOP to upper, Role.MAIN to lower))
        else listOf(role to RectF(0f, 0f, w, h))
    }

    override fun onDraw(canvas: Canvas) {
        val a = app() ?: return
        val now = SystemClock.uptimeMillis()
        val dt = ((now - last) / 1000f).coerceIn(0f, 0.1f)
        last = now
        for ((r, rect) in regions()) {
            val lh = a.logicalH
            val scale = rect.height() / lh
            canvas.save()
            canvas.translate(rect.left, rect.top)
            canvas.clipRect(0f, 0f, rect.width(), rect.height())
            canvas.scale(scale, scale)
            try {
                a.render(canvas, r, rect.width() / scale, lh, dt, now / 1000f)
            } catch (e: Throwable) {
                Crash.log(context, e)
            }
            canvas.restore()
        }
    }

    private var downX = 0f
    private var downY = 0f

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val a = app() ?: return false
        if (e.actionMasked == MotionEvent.ACTION_DOWN) { downX = e.x; downY = e.y; return true }
        if (e.actionMasked != MotionEvent.ACTION_UP) return true
        // Swipes: sideways moves through Home / pages, up-down scrolls lists
        val dx = e.x - downX; val dy = e.y - downY
        val step = 48 * resources.displayMetrics.density
        if (abs(dx) > step && abs(dx) > abs(dy)) {
            repeat((abs(dx) / (step * 2)).toInt().coerceIn(1, 5)) { a.handle(if (dx < 0) "right" else "left") }
            return true
        }
        if (abs(dy) > step && abs(dy) > abs(dx)) {
            repeat((abs(dy) / (step / 1.5f)).toInt().coerceIn(1, 10)) { a.handle(if (dy < 0) "down" else "up") }
            return true
        }
        for ((r, rect) in regions()) if (rect.contains(e.x, e.y)) {
            val scale = rect.height() / a.logicalH
            a.tap(r, (e.x - rect.left) / scale, (e.y - rect.top) / scale)
        }
        return true
    }
}

/** Shows Ryzer on a second physical display (AYN Thor, RG DS, AYANEO Pocket DS, Retroid Dual...). */
class SecondScreen(ctx: Context, display: Display, app: () -> Ryzer?, role: Role) : Presentation(ctx, display) {
    val view = RyzerView(ctx, app, role)
    init {
        setContentView(view)
        window?.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
    }
}

class MainActivity : Activity() {
    private var app: Ryzer? = null
    private lateinit var mainView: RyzerView
    private var second: SecondScreen? = null
    var dualActive = false
        private set
    private val handler = Handler(Looper.getMainLooper())
    private var resumed = false
    private val held = HashSet<String>()
    private var axisDir: String? = null
    private var axisRepeatAt = 0L

    private val tick = object : Runnable {
        override fun run() {
            val a = app ?: return
            if (!resumed) return
            val now = SystemClock.uptimeMillis()
            axisDir?.let { if (now >= axisRepeatAt) { a.handle(it); axisRepeatAt = now + 75 } }
            a.update(1f / a.effectiveFps())
            redraw()
            handler.postDelayed(this, (1000 / a.effectiveFps()).toLong())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BuildInfo.currentContext = applicationContext
        Crash.install(this)
        mainView = RyzerView(this, { app }, Role.MAIN)
        setContentView(mainView)
        mainView.requestFocus()
        start()
        openShowcase(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Updater.ACTION_STATUS) { app?.let { Updater.onStatus(it, intent) }; return }
        openShowcase(intent)
    }

    /** `am start ... --es showcase stats` opens a screen directly; used to take the README screenshots. */
    private fun openShowcase(i: Intent?) {
        val spec = i?.getStringExtra("showcase") ?: return
        handler.postDelayed({ app?.let { a -> a.safely { Showcase.open(a, spec) }; redraw() } }, 2500)
    }

    private fun start() {
        val a = try {
            Ryzer(this)
        } catch (e: Throwable) {
            // Last resort: show the error instead of closing, so it can be reported.
            Crash.log(this, e)
            setContentView(android.widget.TextView(this).apply {
                setPadding(40, 40, 40, 40); textSize = 14f; setTextColor(0xFFFFFFFF.toInt()); setBackgroundColor(0xFF1A0508.toInt())
                text = "Ryzer OS couldn't start on this device.\n\n${Crash.describe(e)}\n\n" +
                    "A copy was saved to ${Crash.file(this@MainActivity).path}. Please send it to the developer."
            })
            return
        }
        app = a
        val homeScreen: Screen = if (a.cfg.homeLayout == "3ds") HomeShell(a) else HomeScreen(a)
        a.stack.clear()
        a.stack.add(if (a.cfg.boot) BootScreen(a, homeScreen) else homeScreen)
        if (!hasStorageAccess(this)) a.push(PermissionScreen(a))
        Crash.pending(this)?.let { report ->
            a.cfg.boot = false   // skip the boot animation after a crash, to get to Home faster
            a.push(Message(a, "Ryzer closed unexpectedly", "Sorry about that. The error is below and saved in crash.txt; " +
                "send it to the developer and it can be fixed.\n\n$report"))
        }
        // First start after an update: show what's new, and drop the downloaded installer
        val now = BuildInfo.version
        if (a.cfg.lastVersion != now) {
            if (a.cfg.lastVersion.isNotEmpty()) a.push(Message(a, "Updated to Ryzer OS $now", WHATS_NEW))
            a.cfg.lastVersion = now; a.cfg.save()
            java.io.File(cacheDir, "update").deleteRecursively()
        }
        val offer = a.cfg.resumePrompt && a.stack.size == 1
        a.rescan { if (offer) a.offerResume() }
    }

    /** Called by the permission screen once access is granted, so data moves to /RyzerOS. */
    fun restart() { second?.dismiss(); second = null; start(); updateDisplays() }

    override fun onResume() {
        super.onResume()
        // Mid-swap the game leaves this screen for a moment before the panel covers it: the game
        // is still running, so don't end the session or grab the second screen.
        if (DualPlay.swapping()) return
        DualPlay.close()   // a game just ended (or never started): the panel's job is done
        resumed = true
        hideSystemBars()
        app?.let { a ->
            a.safely { a.onSession(a.state.finishPlay()) }
            a.lastInput = SystemClock.uptimeMillis()
            a.safely { a.current?.onResume() }
        }
        updateDisplays()
        app?.music?.resume()
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        resumed = false
        // Free the second screen so dual-screen emulators (DraStic, Azahar, melonDS) can use it.
        releaseSecondScreen()
        app?.music?.pause()
    }

    fun releaseSecondScreen() {
        second?.dismiss()
        second = null
        dualActive = false
    }

    fun mainDisplayId(): Int = mainView.display?.displayId ?: Display.DEFAULT_DISPLAY

    // ----- photo themes -----
    private var onPhoto: ((Uri) -> Unit)? = null

    fun pickPhoto(done: (Uri) -> Unit) {
        onPhoto = done
        val i = Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE)
        runCatching { startActivityForResult(Intent.createChooser(i, "Pick a picture for your theme"), 42) }
            .onFailure { app?.notify("No gallery app found") }
    }

    @Deprecated("Uses the platform API on purpose: Ryzer has no AndroidX dependency")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode == 42 && resultCode == RESULT_OK && uri != null) onPhoto?.invoke(uri)
        onPhoto = null
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        }
    }

    fun secondaryDisplays(): List<Display> {
        val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val mine = mainView.display?.displayId ?: Display.DEFAULT_DISPLAY
        return dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).filter { it.displayId != mine }
    }

    fun updateDisplays() {
        val a = app ?: return
        second?.dismiss(); second = null; dualActive = false
        val disp = secondaryDisplays().firstOrNull()
        if (disp != null && a.cfg.dual != "off" && resumed) {
            runCatching {
                val p = SecondScreen(this, disp, { app }, if (a.cfg.swapScreens) Role.MAIN else Role.TOP)
                p.show()
                second = p
                dualActive = true
            }
        }
        mainView.role = if (dualActive && a.cfg.swapScreens) Role.TOP else Role.MAIN
        redraw()
    }

    fun redraw() { mainView.invalidate(); second?.view?.invalidate() }

    /** Draws the main screen into a PNG. */
    fun captureScreen(f: File): File? = runCatching {
        val w = mainView.width; val h = mainView.height
        if (w <= 0 || h <= 0) return null
        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        mainView.draw(Canvas(bmp))
        f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        f
    }.getOrNull()

    /** Takes the screenshot a moment later, once the menu that asked for it has closed. */
    fun postScreenshot() { handler.postDelayed({ app?.let { Sharing.screenshot(it) } }, 350) }

    private val vibrator by lazy { getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator }

    /** A tiny tick of rumble on button presses, for handhelds with a vibration motor. */
    fun buzz() {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(android.os.VibrationEffect.createOneShot(12, 60))
            else @Suppress("DEPRECATION") v.vibrate(12)
        }
    }

    /** Ryzer's own screen brightness (does not change the system setting). */
    fun setMenuBrightness(level: Float) {
        window.attributes = window.attributes.apply { screenBrightness = level }
    }

    // ----- input -----
    private fun action(code: Int): String? = when (code) {
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W -> "up"
        KeyEvent.KEYCODE_DPAD_DOWN -> "down"
        KeyEvent.KEYCODE_DPAD_LEFT -> "left"
        KeyEvent.KEYCODE_DPAD_RIGHT -> "right"
        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_Z -> "a"
        KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_X -> "b"
        KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_C -> "x"
        KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_V -> "y"
        KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_Q -> "l"
        KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_R2, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_E -> "r"
        KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_S -> "start"
        KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_A -> "select"
        else -> null
    }

    private fun swap(a: String) = if (app?.cfg?.swapAB == true) when (a) { "a" -> "b"; "b" -> "a"; else -> a } else a

    /** Set while Settings waits for "press the button you want to use for swapping". */
    var learningSwapKey: ((Int) -> Unit)? = null

    /** Swaps what the two screens show (menu <-> info), instantly. */
    fun toggleSwap() {
        val a = app ?: return
        a.cfg.swapScreens = !a.cfg.swapScreens
        a.cfg.save()
        updateDisplays()
        a.sounds.play("select")
        a.notify(if (a.cfg.swapScreens) "Screens swapped" else "Screens back to normal", 1.4f)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val a = app ?: return super.onKeyDown(keyCode, event)
        (a.current as? ControllerTest)?.let { it.key(keyCode, true); redraw(); return true }
        learningSwapKey?.let { learn ->
            if (keyCode != KeyEvent.KEYCODE_BACK) { learningSwapKey = null; learn(keyCode); return true }
        }
        if (event.repeatCount == 0 && (keyCode == KeyEvent.KEYCODE_BUTTON_THUMBL || keyCode == KeyEvent.KEYCODE_BUTTON_THUMBR ||
                (a.cfg.swapKey > 0 && keyCode == a.cfg.swapKey))) {
            toggleSwap()
            return true
        }
        val act = action(keyCode)?.let { swap(it) } ?: return super.onKeyDown(keyCode, event)
        if (event.repeatCount > 0 && act !in setOf("up", "down", "left", "right")) return true
        held.add(act)
        if ("l" in held && "r" in held && act in setOf("l", "r")) {   // L + R together: quick menu
            if (a.current !is QuickMenu) a.push(QuickMenu(a))
            return true
        }
        a.handle(act)
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        (app?.current as? ControllerTest)?.let { it.key(keyCode, false); redraw(); return true }
        val act = action(keyCode)?.let { swap(it) } ?: return super.onKeyUp(keyCode, event)
        held.remove(act)
        return true
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        val a = app
        if (a != null && ev.isFromSource(InputDevice.SOURCE_JOYSTICK) && ev.action == MotionEvent.ACTION_MOVE) {
            (a.current as? ControllerTest)?.let { t ->
                t.axes = floatArrayOf(ev.getAxisValue(MotionEvent.AXIS_X), ev.getAxisValue(MotionEvent.AXIS_Y),
                    ev.getAxisValue(MotionEvent.AXIS_Z), ev.getAxisValue(MotionEvent.AXIS_RZ),
                    maxOf(ev.getAxisValue(MotionEvent.AXIS_LTRIGGER), ev.getAxisValue(MotionEvent.AXIS_BRAKE)),
                    maxOf(ev.getAxisValue(MotionEvent.AXIS_RTRIGGER), ev.getAxisValue(MotionEvent.AXIS_GAS)))
                redraw()
                return true
            }
            val x = ev.getAxisValue(MotionEvent.AXIS_X).let { if (abs(it) > 0.5f) it else ev.getAxisValue(MotionEvent.AXIS_HAT_X) }
            val y = ev.getAxisValue(MotionEvent.AXIS_Y).let { if (abs(it) > 0.5f) it else ev.getAxisValue(MotionEvent.AXIS_HAT_Y) }
            val dir = when {
                y < -0.5f -> "up"; y > 0.5f -> "down"; x < -0.5f -> "left"; x > 0.5f -> "right"; else -> null
            }
            if (dir != axisDir) {
                axisDir = dir
                if (dir != null) { a.handle(dir); axisRepeatAt = SystemClock.uptimeMillis() + 350 }
            }
            return true
        }
        return super.dispatchGenericMotionEvent(ev)
    }

    @Deprecated("Back is handled as the B button")
    override fun onBackPressed() { app?.handle("b") }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (hasStorageAccess(this)) restart()
    }

    fun requestStorage() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
            } catch (e: ActivityNotFoundException) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
        }
    }
}

fun hasStorageAccess(ctx: Context): Boolean =
    if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
    else ctx.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

/** UI sound effects synthesised at startup, so the APK ships no audio files. */
class Sounds(var enabled: Boolean, volume: Int, pack: String = "classic") {
    private val rate = 22050
    private val tracks = HashMap<String, AudioTrack>()
    private var vol = volume / 100f
    private var pack = pack

    init { build() }

    /** Sound packs: classic (clean tones), chiptune (8-bit square waves), soft (lower and quieter). */
    fun setPack(p: String) {
        pack = p
        tracks.values.forEach { runCatching { it.release() } }
        tracks.clear()
        build()
    }

    private fun build() {
        runCatching {
            mapOf(
                "move" to Triple(floatArrayOf(880f), 25, 0.12f), "select" to Triple(floatArrayOf(660f, 990f), 70, 0.2f),
                "back" to Triple(floatArrayOf(600f, 400f), 70, 0.18f), "error" to Triple(floatArrayOf(220f, 180f), 140, 0.2f),
                "boot" to Triple(floatArrayOf(220f, 330f, 440f, 660f, 880f), 700, 0.22f), "fav" to Triple(floatArrayOf(990f, 1320f), 90, 0.18f),
            ).forEach { (name, spec) ->
                val f = if (pack == "soft") FloatArray(spec.first.size) { spec.first[it] * 0.7f } else spec.first
                val v = if (pack == "soft") spec.third * 0.6f else spec.third
                tracks[name] = tone(f, if (pack == "chiptune") (spec.second * 0.8f).toInt() else spec.second, v)
            }
        }
    }

    private fun tone(freqs: FloatArray, ms: Int, volume: Float): AudioTrack {
        val n = rate * ms / 1000
        val buf = ShortArray(n) { i ->
            val f = freqs[minOf(freqs.size - 1, i * freqs.size / n)]
            val env = minOf(1f, i / 200f) * (1 - i.toFloat() / n).let { it * it }
            val wave = sin(2 * PI * f * i / rate)
            val sample = if (pack == "chiptune") (if (wave >= 0) 0.6 else -0.6) else wave
            (sample * env * volume * 32767).toInt().toShort()
        }
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(n * 2).build()
        t.write(buf, 0, n)
        return t
    }

    fun setVolume(v: Int) { vol = v / 100f }

    fun play(name: String) {
        if (!enabled) return
        val t = tracks[name] ?: return
        runCatching { t.stop(); t.reloadStaticData(); t.setVolume(vol); t.play() }
    }
}
