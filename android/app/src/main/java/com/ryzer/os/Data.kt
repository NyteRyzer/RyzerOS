package com.ryzer.os

import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { getString(it) }

private fun JSONObject?.stringMap(): MutableMap<String, String> {
    val m = LinkedHashMap<String, String>()
    this?.keys()?.forEach { m[it] = getString(it) }
    return m
}

/** Missing keys and JSON nulls both come back as null (org.json turns null into "null"). */
private fun JSONObject.optStr(k: String): String? = if (isNull(k)) null else optString(k).ifEmpty { null }

private fun readJson(f: File): JSONObject? = runCatching { JSONObject(f.readText()) }.getOrNull()

private fun writeJson(f: File, j: JSONObject) {
    f.parentFile?.mkdirs()
    val tmp = File(f.path + ".tmp")
    tmp.writeText(j.toString(2))
    tmp.renameTo(f)  // atomic replace so a crash can't leave half a file
}

/** User settings, saved as config.json in the Ryzer folder so they survive reinstalls. */
class Config(private val file: File) {
    var theme = "ryzer-red"
    var sound = true
    var volume = 70
    var clock24 = false
    var clockStyle = "digital"     // digital | analog (top screen)
    var showBattery = true
    var cleanNames = true
    var hideEmpty = true
    var boot = true
    var animations = true
    var fps = 30
    var swapAB = false
    var dual = "auto"            // auto | on | off
    var swapScreens = false      // put the menu on the second display instead
    var boost = true             // close cached background apps before heavy systems
    var showApps = true
    var romDirs = mutableListOf<String>()
    val systemEmu = LinkedHashMap<String, String>()   // system id -> emulator id or "pkg:<package>"
    // Library view
    var sort = "name"            // name | recent | played | rating | size | added
    var filter = "all"           // all | favorites | unplayed | backlog | playing | beaten | completed
    var grid = false             // box-art grid instead of a list
    var showHidden = false
    var systemSort = "default"   // default | name | games | recent
    val hiddenSystems = mutableSetOf<String>()
    // Look
    var themeMode = "fixed"      // fixed | daily | random
    var dayNight = false
    var dayTheme = "snow"
    var nightTheme = "midnight-blue"
    var font = "default"         // default | condensed | mono | serif | rounded
    var uiSize = "normal"        // small | normal | large
    var wallpaper = ""           // file name in RyzerOS/wallpapers, "" = off
    var screensaver = 5          // minutes idle before the screensaver, 0 = off
    // Device
    var haptics = true
    var batterySaver = true      // drop to 20 fps and no animations under 20% battery
    var kidMode = false
    var kidPin = ""
    var romAccess = "auto"       // auto | provider | folder: how games are handed to emulators
    // Dual screen while playing
    var companion = true         // "Now Playing" panel on the other screen for single-screen games
    var gameScreen = "main"      // main | second: where single-screen games open
    var breakMinutes = 0         // break reminder on the companion, 0 = off
    var dailyLimit = 0           // minutes per day, 0 = no limit
    // Extra looks
    var artTint = true           // tint panels with the selected game's box-art colours
    var systemAccent = false     // accent colour follows the selected system on Home
    var rgb = false              // animated rainbow accent
    var nightLight = 0           // warm filter strength 0..50
    var glass = true             // see-through panels over wallpapers
    var homeLayout = "3ds"       // 3ds | ribbon | grid
    var bootStyle = "classic"    // classic | glitch | minimal
    var soundPack = "classic"    // classic | chiptune | soft
    var music = false            // play RyzerOS/music in the menus
    var musicVolume = 40
    var userName = ""
    // 2.0
    var notifBanners = true
    var notifDnd = false
    var notifOnPanel = true
    var ryzerNotices = true
    var swapKey = -1             // extra key code that swaps the screens (learned in settings); L3/R3 always work
    var live = "off"             // live wallpaper: off | aurora | starfield | bubbles | rain | snow | waves
    var liveSpeed = 100          // percent
    var iconRows = 3             // 3DS home: rows of icons (2-4)
    var avatar = ""              // profile picture file in the Ryzer folder

    init { load() }

    fun load() {
        val j = readJson(file) ?: return
        theme = j.optString("theme", theme); sound = j.optBoolean("sound", sound)
        volume = j.optInt("volume", volume); clock24 = j.optBoolean("clock24", clock24)
        clockStyle = j.optString("clockStyle", clockStyle)
        showBattery = j.optBoolean("showBattery", showBattery); cleanNames = j.optBoolean("cleanNames", cleanNames)
        hideEmpty = j.optBoolean("hideEmpty", hideEmpty); boot = j.optBoolean("boot", boot)
        animations = j.optBoolean("animations", animations); fps = j.optInt("fps", fps)
        swapAB = j.optBoolean("swapAB", swapAB); dual = j.optString("dual", dual)
        swapScreens = j.optBoolean("swapScreens", swapScreens); boost = j.optBoolean("boost", boost)
        showApps = j.optBoolean("showApps", showApps)
        romDirs = j.optJSONArray("romDirs").strings().toMutableList()
        systemEmu.clear(); systemEmu.putAll(j.optJSONObject("systemEmu").stringMap())
        sort = j.optString("sort", sort); filter = j.optString("filter", filter); grid = j.optBoolean("grid", grid)
        showHidden = j.optBoolean("showHidden", showHidden); systemSort = j.optString("systemSort", systemSort)
        hiddenSystems.clear(); hiddenSystems += j.optJSONArray("hiddenSystems").strings()
        themeMode = j.optString("themeMode", themeMode); dayNight = j.optBoolean("dayNight", dayNight)
        dayTheme = j.optString("dayTheme", dayTheme); nightTheme = j.optString("nightTheme", nightTheme)
        font = j.optString("font", font); uiSize = j.optString("uiSize", uiSize)
        wallpaper = j.optString("wallpaper", wallpaper); screensaver = j.optInt("screensaver", screensaver)
        haptics = j.optBoolean("haptics", haptics); batterySaver = j.optBoolean("batterySaver", batterySaver)
        kidMode = j.optBoolean("kidMode", kidMode); kidPin = j.optString("kidPin", kidPin)
        romAccess = j.optString("romAccess", romAccess)
        companion = j.optBoolean("companion", companion); gameScreen = j.optString("gameScreen", gameScreen)
        breakMinutes = j.optInt("breakMinutes", breakMinutes); dailyLimit = j.optInt("dailyLimit", dailyLimit)
        artTint = j.optBoolean("artTint", artTint); systemAccent = j.optBoolean("systemAccent", systemAccent)
        rgb = j.optBoolean("rgb", rgb); nightLight = j.optInt("nightLight", nightLight); glass = j.optBoolean("glass", glass)
        homeLayout = j.optString("homeLayout", homeLayout); bootStyle = j.optString("bootStyle", bootStyle)
        soundPack = j.optString("soundPack", soundPack); music = j.optBoolean("music", music)
        musicVolume = j.optInt("musicVolume", musicVolume); userName = j.optString("userName", userName)
        notifBanners = j.optBoolean("notifBanners", notifBanners); notifDnd = j.optBoolean("notifDnd", notifDnd)
        notifOnPanel = j.optBoolean("notifOnPanel", notifOnPanel); ryzerNotices = j.optBoolean("ryzerNotices", ryzerNotices)
        swapKey = j.optInt("swapKey", swapKey); live = j.optString("live", live); liveSpeed = j.optInt("liveSpeed", liveSpeed)
        iconRows = j.optInt("iconRows", iconRows); avatar = j.optString("avatar", avatar)
        if (j.optInt("uiVersion", 1) < 2) homeLayout = "3ds"   // 2.0 introduces the new Home; old configs switch once
    }

    fun save() = writeJson(file, JSONObject().apply {
        put("theme", theme); put("sound", sound); put("volume", volume); put("clock24", clock24)
        put("clockStyle", clockStyle)
        put("showBattery", showBattery); put("cleanNames", cleanNames); put("hideEmpty", hideEmpty)
        put("boot", boot); put("animations", animations); put("fps", fps); put("swapAB", swapAB)
        put("dual", dual); put("swapScreens", swapScreens); put("boost", boost); put("showApps", showApps)
        put("romDirs", JSONArray(romDirs)); put("systemEmu", JSONObject(systemEmu as Map<*, *>))
        put("sort", sort); put("filter", filter); put("grid", grid); put("showHidden", showHidden)
        put("systemSort", systemSort); put("hiddenSystems", JSONArray(hiddenSystems.toList()))
        put("themeMode", themeMode); put("dayNight", dayNight); put("dayTheme", dayTheme); put("nightTheme", nightTheme)
        put("font", font); put("uiSize", uiSize); put("wallpaper", wallpaper); put("screensaver", screensaver)
        put("haptics", haptics); put("batterySaver", batterySaver); put("kidMode", kidMode); put("kidPin", kidPin)
        put("romAccess", romAccess)
        put("companion", companion); put("gameScreen", gameScreen); put("breakMinutes", breakMinutes); put("dailyLimit", dailyLimit)
        put("artTint", artTint); put("systemAccent", systemAccent); put("rgb", rgb); put("nightLight", nightLight); put("glass", glass)
        put("homeLayout", homeLayout); put("bootStyle", bootStyle); put("soundPack", soundPack); put("music", music)
        put("musicVolume", musicVolume); put("userName", userName)
        put("notifBanners", notifBanners); put("notifDnd", notifDnd); put("notifOnPanel", notifOnPanel); put("ryzerNotices", ryzerNotices)
        put("swapKey", swapKey); put("live", live); put("liveSpeed", liveSpeed); put("iconRows", iconRows); put("avatar", avatar); put("uiVersion", 2)
    })
}

private fun JSONObject?.longMap(into: MutableMap<String, Long>) { this?.keys()?.forEach { into[it] = getLong(it) } }
private fun JSONObject?.intMap(into: MutableMap<String, Int>) { this?.keys()?.forEach { into[it] = getInt(it) } }

fun today(offsetDays: Int = 0): String {
    val cal = java.util.Calendar.getInstance()
    cal.add(java.util.Calendar.DAY_OF_YEAR, offsetDays)
    return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal.time)
}

/** Everything Ryzer remembers about your games. Games are keyed by ROM path. */
class PlayState(private val file: File) {
    val favorites = mutableListOf<String>()
    val recent = mutableListOf<String>()
    val times = HashMap<String, Long>()
    val launches = HashMap<String, Int>()
    val ratings = HashMap<String, Int>()          // 1..5 stars
    val status = HashMap<String, String>()        // backlog | playing | beaten | completed
    val titles = HashMap<String, String>()        // custom display names
    val gameEmu = HashMap<String, String>()       // per-game emulator override
    val hidden = mutableSetOf<String>()
    val collections = LinkedHashMap<String, MutableList<String>>()
    val daily = HashMap<String, Long>()           // yyyy-MM-dd -> seconds played
    val milestones = mutableSetOf<String>()
    val notes = HashMap<String, String>()         // per-game notes (codes, where you left off...)
    val pins = mutableListOf<String>()            // games pinned to Home
    val sessions = mutableListOf<Triple<String, Long, Long>>()   // (game, start ms, seconds), newest first
    var pendingKey: String? = null
    var pendingStart = 0L

    init {
        readJson(file)?.let { j ->
            favorites += j.optJSONArray("favorites").strings()
            recent += j.optJSONArray("recent").strings()
            j.optJSONObject("playtime").longMap(times)
            j.optJSONObject("launches").intMap(launches)
            j.optJSONObject("ratings").intMap(ratings)
            status.putAll(j.optJSONObject("status").stringMap())
            titles.putAll(j.optJSONObject("titles").stringMap())
            gameEmu.putAll(j.optJSONObject("gameEmu").stringMap())
            hidden += j.optJSONArray("hidden").strings()
            j.optJSONObject("collections")?.let { c -> c.keys().forEach { collections[it] = c.getJSONArray(it).strings().toMutableList() } }
            j.optJSONObject("daily").longMap(daily)
            milestones += j.optJSONArray("milestones").strings()
            notes.putAll(j.optJSONObject("notes").stringMap())
            pins += j.optJSONArray("pins").strings()
            j.optJSONArray("sessions")?.let { a -> for (i in 0 until a.length()) a.optJSONArray(i)?.let { e ->
                sessions += Triple(e.optString(0), e.optLong(1), e.optLong(2)) } }
            pendingKey = j.optString("pendingKey").ifEmpty { null }
            pendingStart = j.optLong("pendingStart", 0)
        }
    }

    fun save() = writeJson(file, JSONObject().apply {
        put("favorites", JSONArray(favorites)); put("recent", JSONArray(recent))
        put("playtime", JSONObject(times as Map<*, *>)); put("launches", JSONObject(launches as Map<*, *>))
        put("ratings", JSONObject(ratings as Map<*, *>)); put("status", JSONObject(status as Map<*, *>))
        put("titles", JSONObject(titles as Map<*, *>)); put("gameEmu", JSONObject(gameEmu as Map<*, *>))
        put("hidden", JSONArray(hidden.toList()))
        put("collections", JSONObject().apply { collections.forEach { (k, v) -> put(k, JSONArray(v)) } })
        put("daily", JSONObject(daily as Map<*, *>)); put("milestones", JSONArray(milestones.toList()))
        put("notes", JSONObject(notes as Map<*, *>)); put("pins", JSONArray(pins))
        put("sessions", JSONArray().apply { sessions.forEach { (k, t, secs) -> put(JSONArray().put(k).put(t).put(secs)) } })
        put("pendingKey", pendingKey ?: ""); put("pendingStart", pendingStart)
    })

    fun isFavorite(k: String) = k in favorites
    fun toggleFavorite(k: String): Boolean {
        if (!favorites.remove(k)) favorites.add(k)
        save()
        return k in favorites
    }

    fun toggleHidden(k: String): Boolean {
        if (!hidden.remove(k)) hidden.add(k)
        save()
        return k in hidden
    }

    fun startPlay(k: String) {
        recent.remove(k); recent.add(0, k)
        while (recent.size > 30) recent.removeAt(recent.size - 1)
        launches[k] = (launches[k] ?: 0) + 1
        pendingKey = k; pendingStart = System.currentTimeMillis()
        save()
    }

    /**
     * Called when Ryzer comes back to the front; credits the time spent in the emulator.
     * Returns the finished session (game key, seconds) for the "you played..." summary.
     */
    fun finishPlay(): Pair<String, Long>? {
        val k = pendingKey ?: return null
        val secs = (System.currentTimeMillis() - pendingStart) / 1000
        pendingKey = null
        val ok = secs in 1..(12 * 3600)
        if (ok) {
            times[k] = (times[k] ?: 0) + secs
            daily[today()] = (daily[today()] ?: 0) + secs
            sessions.add(0, Triple(k, pendingStart, secs))
            while (sessions.size > 100) sessions.removeAt(sessions.size - 1)
        }
        save()
        return if (ok) k to secs else null
    }

    fun forget(k: String) {
        favorites.remove(k); recent.remove(k); times.remove(k); launches.remove(k); ratings.remove(k)
        status.remove(k); titles.remove(k); gameEmu.remove(k); hidden.remove(k); notes.remove(k); pins.remove(k)
        collections.values.forEach { it.remove(k) }
        save()
    }

    fun playtime(k: String) = times[k] ?: 0L
    fun playedToday() = daily[today()] ?: 0L
    fun total() = times.values.sum()

    /** Consecutive days with play time, ending today (or yesterday if nothing played yet today). */
    fun streak(): Int {
        var start = if ((daily[today()] ?: 0) > 0) 0 else -1
        var n = 0
        while ((daily[today(start)] ?: 0) > 0) { n++; start-- }
        return n
    }
}

fun formatDuration(seconds: Long): String {
    val h = seconds / 3600; val m = (seconds % 3600) / 60
    return if (h > 0) "${h}h ${"%02d".format(m)}m" else "${m}m"
}

class Emulator(
    val id: String, val name: String, val components: List<String>, val action: String?,
    val category: String?, val data: String?, val extras: Map<String, String>, val flags: List<String>,
    val core: String? = null,
) {
    companion object {
        fun parse(id: String, j: JSONObject) = Emulator(
            id, j.getString("name"), j.getJSONArray("components").strings(),
            j.optStr("action"), j.optStr("category"), j.optStr("data"),
            j.optJSONObject("extras").stringMap(), j.optJSONArray("flags").strings(),
        )
    }

    /** A RetroArch entry bound to one core, e.g. "ra:mgba". */
    fun withCore(core: String) = Emulator("ra:$core", "RetroArch ($core)", components, action, category, data, extras, flags, core)
}

class GameSystem(
    val id: String, val name: String, val short: String, val color: Int, val folders: List<String>,
    val extensions: Set<String>, val emulators: List<String>, val demand: String, val note: String,
) {
    var games: List<Game> = emptyList()
}

private val TAGS = Regex("\\s*[(\\[][^)\\]]*[)\\]]")
private val IMAGE_DIRS = listOf("images", "media/images", "boxart", "media/box2dfront", "snap", "covers", "Imgs")
private val SKIP_DIRS = setOf("images", "media", "boxart", "snap", "videos", "manuals", "covers", "imgs")

fun cleanName(stem: String): String = TAGS.replace(stem, "").replace('_', ' ').trim().ifEmpty { stem }

class Game(val file: File, val system: GameSystem, private val clean: Boolean) {
    val name: String = if (clean) cleanName(file.nameWithoutExtension) else file.nameWithoutExtension
    val key: String get() = file.path

    private var imageLooked = false
    private var imageFile: File? = null

    /** Box art: next to the ROM, or in ES-DE's downloaded_media folder. Looked up once, then cached. */
    fun image(): File? {
        if (!imageLooked) { imageLooked = true; imageFile = runCatching { findImage() }.getOrNull() }
        return imageFile
    }

    private fun findImage(): File? {
        val stem = file.nameWithoutExtension
        val dirs = IMAGE_DIRS.map { File(file.parentFile, it) } +
            listOf("covers", "miximages", "screenshots").map {
                File(Environment.getExternalStorageDirectory(), "ES-DE/downloaded_media/${file.parentFile?.name}/$it")
            }
        for (d in dirs) for (ext in listOf(".png", ".jpg", ".webp")) {
            val f = File(d, stem + ext)
            if (f.isFile) return f
        }
        return null
    }
}

object Library {
    fun loadEmulators(app: Ryzer): Map<String, Emulator> {
        val j = readJson(File(app.home, "emulators.json"))
            ?: JSONObject(app.ctx.assets.open("emulators.json").bufferedReader().use { it.readText() })
        val emus = j.getJSONObject("emulators")
        return emus.keys().asSequence().associateWith { Emulator.parse(it, emus.getJSONObject(it)) }
    }

    fun loadSystems(app: Ryzer): List<GameSystem> {
        val j = readJson(File(app.home, "systems.json"))
            ?: JSONObject(app.ctx.assets.open("systems.json").bufferedReader().use { it.readText() })
        val arr = j.getJSONArray("systems")
        return (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val s = arr.getJSONObject(i)
                GameSystem(s.getString("id"), s.getString("name"), s.optString("short", s.getString("id").uppercase()),
                    Theme.parseColor(s.optString("color", "#888888")), s.getJSONArray("folders").strings(),
                    s.getJSONArray("extensions").strings().map { it.lowercase() }.toSet(),
                    s.optJSONArray("emulators").strings(), s.optString("demand"), s.optString("note"))
            }.getOrNull()
        }
    }

    /** Common ROM folder locations on internal storage and SD cards. */
    fun defaultRomDirs(): List<String> {
        val roots = mutableListOf(Environment.getExternalStorageDirectory())
        File("/storage").listFiles()?.filter { it.isDirectory && it.name != "emulated" && it.name != "self" }?.let { roots += it }
        val out = mutableListOf<String>()
        for (r in roots) for (n in listOf("ROMs", "Roms", "roms", "Emulation/roms", "Games", "games")) {
            val d = File(r, n)
            if (d.isDirectory && out.none { File(it).canonicalPath == d.canonicalPath }) out += d.path
        }
        return out
    }

    fun scan(systems: List<GameSystem>, roots: List<File>, clean: Boolean) {
        for (sys in systems) {
            val found = LinkedHashMap<String, Game>()
            for (root in roots) {
                val children = root.listFiles() ?: continue
                val wanted = sys.folders.map { it.lowercase() }.toSet()
                for (dir in children.filter { it.isDirectory && it.name.lowercase() in wanted }) {
                    val files = (dir.listFiles() ?: emptyArray()).toMutableList()
                    files.filter { it.isDirectory && !it.name.startsWith(".") && it.name.lowercase() !in SKIP_DIRS }
                        .forEach { sub -> sub.listFiles()?.let { files += it } }
                    for (f in files) {
                        if (f.isFile && !f.name.startsWith(".") && ".${f.extension.lowercase()}" in sys.extensions) {
                            found[f.path] = Game(f, sys, clean)
                        }
                    }
                }
            }
            sys.games = found.values.sortedBy { it.name.lowercase() }
        }
    }

    fun find(systems: List<GameSystem>, key: String): Game? {
        for (s in systems) for (g in s.games) if (g.key == key) return g
        return null
    }

    fun search(systems: List<GameSystem>, q: String): List<Game> {
        val query = q.trim().lowercase()
        if (query.isEmpty()) return emptyList()
        return systems.flatMap { s -> s.games.filter { query in it.name.lowercase() } }
    }
}
