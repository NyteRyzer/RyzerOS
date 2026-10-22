package com.ryzer.os

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.RectF
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import org.json.JSONObject
import java.io.File
import kotlin.math.max

/**
 * Dual-screen play for single-screen systems: the game opens on one display and this
 * "Now Playing" panel fills the other one instead of leaving it blank.
 */
object DualPlay {
    /** Systems that use both screens themselves; they get both displays, no panel. */
    val DUAL_SYSTEMS = setOf("nds", "3ds")

    fun supported(ctx: MainActivity): Boolean =
        Build.VERSION.SDK_INT >= 26 &&
            ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS) &&
            ctx.secondaryDisplays().isNotEmpty()

    fun wanted(app: Ryzer, game: Game) = app.cfg.companion && game.system.id !in DUAL_SYSTEMS && supported(app.ctx)

    /** (display for the game, display for the panel). A system's own choice (from swapping) wins. */
    fun displays(app: Ryzer, game: Game? = null): Pair<Int, Int>? {
        val main = app.ctx.mainDisplayId()
        val second = app.ctx.secondaryDisplays().firstOrNull()?.displayId ?: return null
        val where = game?.let { app.cfg.screenFor[it.system.id] } ?: app.cfg.gameScreen
        return if (where == "second") second to main else main to second
    }

    // ----- the running session, for swapping screens mid-game -----
    var gameDisplay = -1
        private set
    var panelDisplay = -1
        private set
    private var gamePkg: String? = null
    private var gameKey: String? = null
    /** While a swap is moving windows around, Ryzer's own screen may briefly come to the front.
     *  That must not end the session (see MainActivity.onResume). */
    @Volatile var swappingUntil = 0L

    fun swapping() = SystemClock.uptimeMillis() < swappingUntil

    /** Remembers where things are, so they can be swapped later. */
    fun begin(game: Game, pkg: String, gameDisplay: Int, panelDisplay: Int) {
        gameKey = game.key; gamePkg = pkg.ifEmpty { null }
        this.gameDisplay = gameDisplay; this.panelDisplay = panelDisplay
    }

    /** Moving a running app to another screen needs Android 10 or newer. */
    fun canSwap() = Build.VERSION.SDK_INT >= 29 && gamePkg != null && gameDisplay >= 0 && panelDisplay >= 0 && gameDisplay != panelDisplay

    /**
     * Swaps the game and the Now Playing panel between the two screens while the game keeps
     * running: the emulator's task is brought to the front on the other display (Android moves an
     * existing task when it is started on a different display), then the panel reopens where the
     * game was.
     */
    fun swap(app: Ryzer, from: Activity): Boolean {
        val pkg = gamePkg ?: return false
        val game = gameKey?.let { Library.find(app.systems, it) } ?: return false
        if (!canSwap()) { app.notify("Swapping during a game needs Android 10 or newer", 3f); return false }
        val newGame = panelDisplay; val newPanel = gameDisplay
        swappingUntil = SystemClock.uptimeMillis() + 3000
        // 1. The new panel opens on the game's screen. 2. The game moves to the other screen. The game
        //    goes last because Android sends the controller to whatever was started last.
        runCatching {
            from.startActivity(Intent(from, NowPlayingActivity::class.java).putExtra("game", game.key)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
                optionsFor(newPanel))
        }.onFailure { Crash.log(from, it); swappingUntil = 0; return false }
        val bring = (from.packageManager.getLaunchIntentForPackage(pkg) ?: Intent(Intent.ACTION_MAIN).setPackage(pkg))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        val moved = runCatching { from.startActivity(bring, optionsFor(newGame)); true }
            .getOrElse { Crash.log(from, RuntimeException("Couldn't move $pkg to display $newGame", it)); false }
        if (!moved) {
            // Put things back as they were: close the panel we just opened over the game
            NowPlayingActivity.current?.takeIf { it !== from }?.finish()
            swappingUntil = 0
            app.notify("This device won't move the game to the other screen", 3f)
            return false
        }
        gameDisplay = newGame; panelDisplay = newPanel
        from.finish()   // the old panel is behind the game now
        if (app.cfg.rememberSwap) {
            app.cfg.screenFor[game.system.id] = if (newGame == app.ctx.mainDisplayId()) "main" else "second"
            app.cfg.save()
        }
        return true
    }

    fun optionsFor(displayId: Int): Bundle? =
        if (Build.VERSION.SDK_INT >= 26) ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle() else null

    /** Opens the panel on its display. Returns false if this device won't allow it. */
    fun open(app: Ryzer, game: Game, displayId: Int): Boolean = runCatching {
        val i = Intent(app.ctx, NowPlayingActivity::class.java)
            .putExtra("game", game.key)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        app.ctx.startActivity(i, optionsFor(displayId))
        true
    }.getOrElse { Crash.log(app.ctx, RuntimeException("Couldn't open the Now Playing screen", it)); false }

    fun close() {
        NowPlayingActivity.current?.finish()
        gamePkg = null; gameKey = null; gameDisplay = -1; panelDisplay = -1
    }

    // ----- emulator hotkey cheat sheets --------------------------------------------------

    private val BUILTIN = mapOf(
        "retroarch" to "Open the RetroArch menu: press Back, or the 'Menu toggle' combo from Settings > Input > Hotkeys (L3 + R3 is common).\n" +
            "Save / load state, cheats, shaders and quitting are all in the Quick Menu.",
        "ppsspp" to "Pause menu: press Back.\nYou can map Save state / Load state / Fast-forward to buttons in Settings > Controls > Control mapping.",
        "drastic" to "Menu: press Back or tap the menu icon on screen.\nSave states, screen layouts and fast-forward are in the DraStic menu.",
        "azahar" to "Pause menu: press Back.\nSwap screens, layouts and save states are in the in-game menu.",
        "dolphin" to "Menu: press Back.\nSave states and settings are in the in-game menu.",
        "duckstation" to "Pause menu: press Back.\nSave states, cheats and disc changes are in the pause menu.",
        "nethersx2" to "Pause menu: press Back.\nSave states and settings are in the pause menu.",
        "flycast" to "Menu: press Back.",
        "eden" to "Menu: press Back.\nSave data is managed in Eden's game menu.",
        "m64plus_fz" to "Menu: press Back.\nSave states and slot select are in the in-game menu.",
    )

    fun hotkeys(app: Ryzer, emuId: String?): String {
        val id = emuId ?: return "Most Android emulators open their menu with the Back button."
        // A user file wins, so the sheets can be edited: RyzerOS/hotkeys.json {"ppsspp": "..."}
        runCatching { JSONObject(File(app.home, "hotkeys.json").readText()).optString(id) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        val key = if (id.startsWith("ra:")) "retroarch" else id
        return BUILTIN[key] ?: BUILTIN.entries.firstOrNull { key.startsWith(it.key) }?.value
            ?: "Most Android emulators open their menu with the Back button."
    }
}

/** The "Now Playing" panel shown on the second screen while a single-screen game runs. */
class NowPlayingActivity : Activity() {
    companion object { var current: NowPlayingActivity? = null }

    private lateinit var view: NowPlayingView
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() { view.invalidate(); handler.postDelayed(this, 1000) }
    }
    private val openedAt = SystemClock.uptimeMillis()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = Ryzer.instance
        val game = intent.getStringExtra("game")?.let { k -> app?.let { Library.find(it.systems, k) } }
        if (app == null || game == null) { finish(); return }
        current = this
        // Never take the controller away from the game; touch still works.
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view = NowPlayingView(this, app, game)
        setContentView(view)
    }

    override fun onResume() { super.onResume(); handler.post(tick) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(tick) }
    override fun onDestroy() { super.onDestroy(); if (current === this) current = null }

    /**
     * When the game closes, Android hands focus to this panel. That means the session is
     * over, so go back to Ryzer (taps never focus the panel: it is not focusable).
     */
    override fun onTopResumedActivityChanged(isTop: Boolean) {
        super.onTopResumedActivityChanged(isTop)
        if (isTop && SystemClock.uptimeMillis() - openedAt > 4000 && !DualPlay.swapping()) backToRyzer()
    }

    fun backToRyzer() {
        runCatching {
            startActivity(Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finish()
    }

    fun setBrightness(level: Float) {
        window.attributes = window.attributes.apply { screenBrightness = level }
    }
}

class NowPlayingView(private val act: NowPlayingActivity, private val app: Ryzer, private val game: Game) : View(act) {
    private val audio = act.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val hits = ArrayList<Pair<RectF, () -> Unit>>()
    private var page = "info"          // info | hotkeys | notes
    private var dimmed = false
    private var lastBreak = 0L
    private var breakShownAt = 0L
    private var downX = 0f
    private var downY = 0f
    private val emuId = app.state.gameEmu[game.key] ?: Launcher.chosen(app, game.system)

    private fun hit(r: RectF, action: () -> Unit) = hits.add(RectF(r) to action)

    private fun elapsed(): Long {
        val st = app.state
        return if (st.pendingKey == game.key) (System.currentTimeMillis() - st.pendingStart) / 1000 else 0
    }

    override fun onDraw(canvas: Canvas) {
        val lh = app.logicalH
        val scale = height / lh
        val w = width / scale; val h = lh
        canvas.save()
        canvas.scale(scale, scale)
        hits.clear()
        val th = app.activeTheme
        try { draw(canvas, th, w, h) } catch (e: Throwable) { Crash.log(context, e) }
        canvas.restore()
    }

    private fun button(c: Canvas, th: Theme, r: RectF, label: String, active: Boolean = false, action: () -> Unit) {
        G.panel(c, r, th, selected = active)
        G.text(c, label, r.centerX(), r.centerY() - 11, 21f, th.c("text"), align = 'c', isBold = active, maxW = r.width() - 8)
        hit(r, action)
    }

    private fun draw(c: Canvas, th: Theme, w: Float, h: Float) {
        if (dimmed) {   // "screen off": black with a faint clock, tap anywhere to wake
            G.rect(c, RectF(0f, 0f, w, h), 0xFF000000.toInt())
            G.text(c, clock(), w / 2, h / 2 - 30, 60f, withAlpha(th.c("text_dim"), 0.5f), align = 'c', isBold = true)
            G.text(c, "Tap to wake", w / 2, h / 2 + 30, 20f, withAlpha(th.c("text_dim"), 0.4f), align = 'c')
            hit(RectF(0f, 0f, w, h)) { dimmed = false; act.setBrightness(-1f) }
            return
        }
        G.background(c, th, w, h, SystemClock.uptimeMillis() / 1000f)
        app.wallpaper()?.let { G.wallpaper(c, th, it, w, h) }
        // Header
        G.text(c, "NOW PLAYING", 16f, 12f, 20f, th.c("accent"), isBold = true)
        G.text(c, clock(), w - 16, 10f, 24f, th.c("text"), align = 'r')
        val (bat, chg) = app.battery()
        if (bat >= 0) G.text(c, "$bat%" + if (chg) " +" else "", w - 110, 12f, 20f, th.c("text_dim"), align = 'r')
        G.line(c, 12f, 40f, w - 12, 40f, th.c("panel_border"))

        // Game card
        val art = RectF(16f, 52f, 16f + 150f, 52f + 150f)
        val bmp = G.image(game.image())
        if (bmp != null) G.drawImageFit(c, bmp, art) else {
            G.rect(c, art, mix(th.c("panel"), game.system.color, 0.7f), th.radius)
            G.text(c, game.system.short, art.centerX(), art.centerY() - 22, 48f, th.c("highlight_text"), align = 'c', isBold = true)
        }
        val x = art.right + 16
        val st = app.state
        G.wrap(app.title(game), 34f, w - x - 16).take(2).forEachIndexed { i, l ->
            G.text(c, l, x, 52f + i * 34, 34f, th.c("text"), isBold = true) }
        G.text(c, "${game.system.name}  |  ${Launcher.label(app, emuId)}", x, 124f, 20f, th.c("text_dim"), maxW = w - x - 16)
        val secs = elapsed()
        G.text(c, "Playing for ${clockDuration(secs)}", x, 150f, 24f, th.c("accent"), isBold = true)
        val total = st.playtime(game.key) + secs
        val limitLine = if (app.cfg.dailyLimit > 0) {
            val left = app.cfg.dailyLimit * 60L - (st.playedToday() + secs)
            if (left > 0) "  |  ${formatDuration(left)} left today" else "  |  daily limit reached"
        } else ""
        G.text(c, "Total ${formatDuration(total)}$limitLine", x, 178f, 19f, th.c("text_dim"), maxW = w - x - 16)

        // Newest notification (if any arrived while playing)
        if (app.cfg.notifOnPanel && !app.cfg.notifDnd) Notifs.list.firstOrNull()?.let { n ->
            if (System.currentTimeMillis() - n.time < 10 * 60_000) {
                G.text(c, "${n.app}: ${n.title} - ${n.text}", 16f, 194f, 17f, th.c("accent2"), maxW = w - 32)
            }
        }
        // Break reminder
        val brk = app.cfg.breakMinutes
        if (brk > 0 && secs > 0 && secs / (brk * 60L) > lastBreak) {
            lastBreak = secs / (brk * 60L)
            breakShownAt = SystemClock.uptimeMillis()
            app.ctx.buzz(); app.ctx.buzz()
        }
        val panelTop = 214f
        if (SystemClock.uptimeMillis() - breakShownAt < 20000 && breakShownAt > 0) {
            val r = RectF(16f, panelTop, w - 16, panelTop + 40)
            G.rect(c, r, withAlpha(th.c("warn"), 0.9f), 10f)
            G.text(c, "You've played ${formatDuration(secs)} - time for a short break?", r.centerX(), r.top + 9, 21f, th.c("bg_bottom"), align = 'c', isBold = true)
            hit(r) { breakShownAt = 0 }
        }

        // Page area
        val area = RectF(16f, panelTop + 48, w - 16, h - 118)
        when (page) {
            "hotkeys" -> textPanel(c, th, area, "Emulator controls", DualPlay.hotkeys(app, emuId))
            "notes" -> textPanel(c, th, area, "Notes", st.notes[game.key]?.takeIf { it.isNotBlank() }
                ?: "No notes for this game yet. Add some in Ryzer: game options (START) > Notes.")
            else -> {
                G.panel(c, area, th)
                val lines = listOfNotNull(
                    "Rating: ${ListView.stars(st.ratings[game.key] ?: 0)}",
                    "Status: ${ListView.label(st.status[game.key] ?: "")}",
                    "Favorite: ${if (st.isFavorite(game.key)) "Yes" else "No"}",
                    if (brk > 0) "Break reminder every $brk min" else null,
                )
                lines.forEachIndexed { i, l -> G.text(c, l, area.left + 14, area.top + 10 + i * 26, 21f, th.c("text"), maxW = area.width() - 28) }
            }
        }

        // Controls (touch)
        val cols = 6
        val gap = 8f
        val bw = (w - 32 - (cols - 1) * gap) / cols
        val row1 = h - 110; val row2 = h - 58
        fun cell(col: Int, top: Float) = RectF(16 + col * (bw + gap), top, 16 + col * (bw + gap) + bw, top + 46)
        // Swap screens: the big button in the header, so it's easy to hit with a thumb
        if (DualPlay.canSwap()) {
            val r = RectF(w / 2 - 110, 4f, w / 2 + 110, 36f)
            G.rect(c, r, th.c("accent"), 16f)
            G.text(c, "⇅  Swap screens", r.centerX(), r.top + 5, 21f, th.c("highlight_text"), align = 'c', isBold = true)
            hit(r) { swap() }
        }
        button(c, th, cell(0, row1), "Vol -") { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI) }
        button(c, th, cell(1, row1), "Vol +") { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI) }
        button(c, th, cell(2, row1), "Mute") { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI) }
        button(c, th, cell(3, row1), if (st.isFavorite(game.key)) "★ Fav" else "☆ Fav", st.isFavorite(game.key)) { st.toggleFavorite(game.key) }
        button(c, th, cell(4, row1), "Rate") {
            val r = ((st.ratings[game.key] ?: 0) + 1) % 6
            if (r == 0) st.ratings.remove(game.key) else st.ratings[game.key] = r
            st.save()
        }
        button(c, th, cell(5, row1), "Status") {
            val list = ListView.STATUSES
            val s = list[(list.indexOf(st.status[game.key] ?: "") + 1) % list.size]
            if (s.isEmpty()) st.status.remove(game.key) else st.status[game.key] = s
            st.save()
        }
        button(c, th, cell(0, row2), "Info", page == "info") { page = "info" }
        button(c, th, cell(1, row2), "Controls", page == "hotkeys") { page = "hotkeys" }
        button(c, th, cell(2, row2), "Notes", page == "notes") { page = "notes" }
        button(c, th, cell(3, row2), if (brk > 0) "Break ${brk}m" else "Break off") {
            app.cfg.breakMinutes = listOf(0, 30, 45, 60, 90).let { l -> l[(l.indexOf(app.cfg.breakMinutes).coerceAtLeast(0) + 1) % l.size] }
            app.cfg.save(); lastBreak = elapsed() / max(1L, app.cfg.breakMinutes * 60L)
        }
        button(c, th, cell(4, row2), "Screen off") { dimmed = true; act.setBrightness(0.01f) }
        button(c, th, cell(5, row2), "Ryzer") { act.backToRyzer() }
    }

    private fun swap() {
        if (DualPlay.swap(app, act)) app.sounds.play("select")
    }

    private fun textPanel(c: Canvas, th: Theme, r: RectF, title: String, body: String) {
        G.panel(c, r, th)
        G.text(c, title, r.left + 14, r.top + 8, 22f, th.c("accent"), isBold = true)
        val rows = ((r.height() - 40) / 22).toInt()
        G.wrap(body, 19f, r.width() - 28).take(max(0, rows)).forEachIndexed { i, l ->
            G.text(c, l, r.left + 14, r.top + 36 + i * 22, 19f, th.c("text")) }
    }

    private fun clock() = java.text.SimpleDateFormat(if (app.cfg.clock24) "HH:mm" else "h:mm a", java.util.Locale.getDefault()).format(java.util.Date())

    private fun clockDuration(s: Long) = if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val scale = height / app.logicalH
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y }
            MotionEvent.ACTION_UP -> {
                // A long swipe up or down swaps the screens
                if (DualPlay.canSwap() && kotlin.math.abs(e.y - downY) > height * 0.35f && kotlin.math.abs(e.y - downY) > kotlin.math.abs(e.x - downX) * 2) {
                    swap(); return true
                }
                val x = e.x / scale; val y = e.y / scale
                for ((r, action) in hits.reversed()) if (r.contains(x, y)) {
                    runCatching { action() }.onFailure { Crash.log(context, it) }
                    if (app.cfg.haptics) app.ctx.buzz()
                    break
                }
                invalidate()
            }
        }
        return true
    }
}
