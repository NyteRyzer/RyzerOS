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

private fun writeJson(f: File, j: JSONObject) = writeText(f, j.toString(2))

private fun writeText(f: File, text: String) {
    f.parentFile?.mkdirs()
    val tmp = File(f.path + ".tmp")
    tmp.writeText(text)
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
    // 2.1
    var launchAnim = true        // short splash before a game starts
    var newBadges = true         // "NEW" on games added in the last week
    var neon = false             // glowing outlines on panels
    var transitions = true       // slide between screens
    var iconShape = "rounded"    // 3DS home icons: rounded | circle | square | hexagon
    var bedtime = -1             // hour (0-23) for a bedtime reminder, -1 = off
    var bootMessage = ""         // custom text on the boot screen
    var seasonal = true          // snow in winter, petals in spring...
    var photoFrame = false       // screensaver shows pictures from RyzerOS/photos
    var resumePrompt = true      // "continue playing X?" at startup
    val hiddenTiles = mutableSetOf<String>()   // Home tiles switched off in the tile editor
    // 2.2
    var autoArt = true           // download missing artwork after each library scan
    var artWifiOnly = true
    var artType = "boxart"       // boxart | snap | title
    val screenFor = LinkedHashMap<String, String>()   // system id -> "main" | "second", set by swapping while playing
    var rememberSwap = true      // a swap during a game sets that system's screen for next time
    // Updates
    var autoUpdate = true        // check GitHub once a day on Wi-Fi
    var autoDownloadUpdate = true
    var lastUpdateCheck = 0L
    var skipVersion = ""
    var lastVersion = ""         // version that last ran, to show What's New after an update
    // 2.4
    var dailyGoal = 0            // minutes of play per day, 0 = off
    var goalDay = ""             // day the goal was last reached (one notice per day)
    var weeklyReport = true
    var lastWeeklyReport = ""
    var streakReminder = true
    var lastStreakReminder = ""
    var groupDiscs = true        // hide .bin tracks behind .cue/.gdi and discs behind .m3u
    var hideBios = true
    var panelAutoDim = 0         // Now Playing: seconds without a touch before it dims, 0 = off
    var chargeAlert = 0          // percent (80 / 90) to remind you to unplug, 0 = off
    var slideshow = 0            // minutes between wallpapers, 0 = off
    var highContrast = false
    val recentSearches = mutableListOf<String>()

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
        launchAnim = j.optBoolean("launchAnim", launchAnim); newBadges = j.optBoolean("newBadges", newBadges)
        neon = j.optBoolean("neon", neon); transitions = j.optBoolean("transitions", transitions)
        iconShape = j.optString("iconShape", iconShape); bedtime = j.optInt("bedtime", bedtime)
        bootMessage = j.optString("bootMessage", bootMessage); seasonal = j.optBoolean("seasonal", seasonal)
        photoFrame = j.optBoolean("photoFrame", photoFrame); resumePrompt = j.optBoolean("resumePrompt", resumePrompt)
        hiddenTiles.clear(); hiddenTiles += j.optJSONArray("hiddenTiles").strings()
        autoArt = j.optBoolean("autoArt", autoArt); artWifiOnly = j.optBoolean("artWifiOnly", artWifiOnly)
        artType = j.optString("artType", artType)
        screenFor.clear(); screenFor.putAll(j.optJSONObject("screenFor").stringMap())
        rememberSwap = j.optBoolean("rememberSwap", rememberSwap)
        autoUpdate = j.optBoolean("autoUpdate", autoUpdate); autoDownloadUpdate = j.optBoolean("autoDownloadUpdate", autoDownloadUpdate)
        lastUpdateCheck = j.optLong("lastUpdateCheck", 0); skipVersion = j.optString("skipVersion", ""); lastVersion = j.optString("lastVersion", "")
        dailyGoal = j.optInt("dailyGoal", dailyGoal); goalDay = j.optString("goalDay", "")
        weeklyReport = j.optBoolean("weeklyReport", weeklyReport); lastWeeklyReport = j.optString("lastWeeklyReport", "")
        streakReminder = j.optBoolean("streakReminder", streakReminder); lastStreakReminder = j.optString("lastStreakReminder", "")
        groupDiscs = j.optBoolean("groupDiscs", groupDiscs); hideBios = j.optBoolean("hideBios", hideBios)
        panelAutoDim = j.optInt("panelAutoDim", panelAutoDim); chargeAlert = j.optInt("chargeAlert", chargeAlert)
        slideshow = j.optInt("slideshow", slideshow); highContrast = j.optBoolean("highContrast", highContrast)
        recentSearches.clear(); recentSearches += j.optJSONArray("recentSearches").strings()
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
        put("launchAnim", launchAnim); put("newBadges", newBadges); put("neon", neon); put("transitions", transitions)
        put("iconShape", iconShape); put("bedtime", bedtime); put("bootMessage", bootMessage); put("seasonal", seasonal)
        put("photoFrame", photoFrame); put("resumePrompt", resumePrompt); put("hiddenTiles", JSONArray(hiddenTiles.toList()))
        put("autoArt", autoArt); put("artWifiOnly", artWifiOnly); put("artType", artType)
        put("screenFor", JSONObject(screenFor as Map<*, *>)); put("rememberSwap", rememberSwap)
        put("autoUpdate", autoUpdate); put("autoDownloadUpdate", autoDownloadUpdate); put("lastUpdateCheck", lastUpdateCheck)
        put("skipVersion", skipVersion); put("lastVersion", lastVersion)
        put("dailyGoal", dailyGoal); put("goalDay", goalDay); put("weeklyReport", weeklyReport); put("lastWeeklyReport", lastWeeklyReport)
        put("streakReminder", streakReminder); put("lastStreakReminder", lastStreakReminder)
        put("groupDiscs", groupDiscs); put("hideBios", hideBios); put("panelAutoDim", panelAutoDim); put("chargeAlert", chargeAlert)
        put("slideshow", slideshow); put("highContrast", highContrast); put("recentSearches", JSONArray(recentSearches))
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
    val bestScores = HashMap<String, Int>()       // mini-game high scores
    val firstSeen = HashMap<String, Long>()       // when a ROM first showed up, for NEW badges
    val lastPlayed = HashMap<String, Long>()      // when each game was last started
    val gameScreen = HashMap<String, String>()    // per-game "main" | "second" for dual-screen play
    @Volatile var pendingKey: String? = null      // read by background work that waits while a game runs
    var pendingStart = 0L
    private val writer = java.util.concurrent.Executors.newSingleThreadExecutor()

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
            j.optJSONObject("bestScores").intMap(bestScores)
            j.optJSONObject("firstSeen").longMap(firstSeen)
            j.optJSONObject("lastPlayed").longMap(lastPlayed)
            gameScreen.putAll(j.optJSONObject("gameScreen").stringMap())
            if (lastPlayed.isEmpty()) for ((k, t, _) in sessions) if (k !in lastPlayed) lastPlayed[k] = t   // from older versions
            pendingKey = j.optString("pendingKey").ifEmpty { null }
            pendingStart = j.optLong("pendingStart", 0)
        }
    }

    /**
     * Snapshots the data now and writes the file in the background, in order: saving happens right
     * as games launch, and a big library makes this file large.
     */
    fun save() {
        val text = snapshot().toString()
        writer.execute { writeText(file, text) }
    }

    private fun snapshot() = JSONObject().apply {
        put("favorites", JSONArray(favorites)); put("recent", JSONArray(recent))
        put("playtime", JSONObject(times as Map<*, *>)); put("launches", JSONObject(launches as Map<*, *>))
        put("ratings", JSONObject(ratings as Map<*, *>)); put("status", JSONObject(status as Map<*, *>))
        put("titles", JSONObject(titles as Map<*, *>)); put("gameEmu", JSONObject(gameEmu as Map<*, *>))
        put("hidden", JSONArray(hidden.toList()))
        put("collections", JSONObject().apply { collections.forEach { (k, v) -> put(k, JSONArray(v)) } })
        put("daily", JSONObject(daily as Map<*, *>)); put("milestones", JSONArray(milestones.toList()))
        put("notes", JSONObject(notes as Map<*, *>)); put("pins", JSONArray(pins))
        put("sessions", JSONArray().apply { sessions.forEach { (k, t, secs) -> put(JSONArray().put(k).put(t).put(secs)) } })
        put("bestScores", JSONObject(bestScores as Map<*, *>)); put("firstSeen", JSONObject(firstSeen as Map<*, *>))
        put("lastPlayed", JSONObject(lastPlayed as Map<*, *>)); put("gameScreen", JSONObject(gameScreen as Map<*, *>))
        put("pendingKey", pendingKey ?: ""); put("pendingStart", pendingStart)
    }

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
        lastPlayed[k] = System.currentTimeMillis()
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
            while (sessions.size > 500) sessions.removeAt(sessions.size - 1)   // enough for a year of Ryzer Wrapped
        }
        save()
        return if (ok) k to secs else null
    }

    fun forget(k: String) {
        lastPlayed.remove(k); gameScreen.remove(k)
        favorites.remove(k); recent.remove(k); times.remove(k); launches.remove(k); ratings.remove(k)
        status.remove(k); titles.remove(k); gameEmu.remove(k); hidden.remove(k); notes.remove(k); pins.remove(k)
        collections.values.forEach { it.remove(k) }
        save()
    }

    /** Remembers when each ROM first appeared; true if anything new was recorded. */
    fun noteSeen(keys: Collection<String>): Boolean {
        val now = System.currentTimeMillis()
        // The very first scan isn't "new": everything was already there.
        val stamp = if (firstSeen.isEmpty()) 0L else now
        var changed = false
        for (k in keys) if (k !in firstSeen) { firstSeen[k] = stamp; changed = true }
        return changed
    }

    fun isNew(k: String) = (firstSeen[k] ?: 0L).let { it > 0 && System.currentTimeMillis() - it < 7 * 86_400_000L }

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

/** "today", "yesterday", "3 days ago", "2 months ago". */
fun agoText(ms: Long): String {
    val days = ((System.currentTimeMillis() - ms) / 86_400_000L).toInt()
    return when {
        days <= 0 -> "today"
        days == 1 -> "yesterday"
        days < 30 -> "$days days ago"
        days < 365 -> "${days / 30} month${if (days / 30 == 1) "" else "s"} ago"
        else -> "${days / 365} year${if (days / 365 == 1) "" else "s"} ago"
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

    /** Looks for the picture again (after artwork was downloaded or deleted). */
    fun refreshImage() { imageLooked = false; imageFile = null }

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
        // Artwork Ryzer downloaded (Scraper): your own art above always wins
        Library.coversDir?.let { File(File(it, system.id), "$stem.webp") }?.takeIf { it.isFile }?.let { return it }
        return null
    }
}

object Library {
    /** RyzerOS/covers, where downloaded artwork goes; set when Ryzer starts. */
    @Volatile var coversDir: File? = null

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
        // Shared storage on Android ignores letter case, so "ROMs", "Roms" and "roms" can all be the
        // same folder. Use the folder names as they really are on disk, once each.
        fun add(d: File) {
            if (d.isDirectory && out.none { it.equals(d.path, ignoreCase = true) || File(it).canonicalPath == d.canonicalPath }) out += d.path
        }
        for (r in roots) {
            val children = r.listFiles()?.filter { it.isDirectory } ?: continue
            for (want in listOf("roms", "games")) children.filter { it.name.equals(want, ignoreCase = true) }.forEach { add(it) }
            children.filter { it.name.equals("emulation", ignoreCase = true) }.forEach { e ->
                e.listFiles()?.filter { it.isDirectory && it.name.equals("roms", ignoreCase = true) }?.forEach { add(it) }
            }
        }
        return out
    }

    /** What the last scan tidied away or couldn't use, for Library check-up. */
    class ScanStats {
        var hiddenTracks = 0
        var hiddenBios = 0
        val unknown = ArrayList<File>()          // files in system folders with an extension the system doesn't use
        val emptyFolders = ArrayList<File>()
        val systemDirs = HashMap<String, MutableList<File>>()   // system id -> its folders
    }
    @Volatile var lastScan = ScanStats()

    /** Files that are part of another file: tracks listed in a .cue/.gdi, discs listed in an .m3u. */
    private fun referencedBy(sheet: File): List<File> = runCatching {
        val dir = sheet.parentFile
        sheet.readLines().take(400).mapNotNull { line ->
            val l = line.trim()
            val name = when (sheet.extension.lowercase()) {
                "cue" -> Regex("^FILE\\s+\"?(.+?)\"?\\s+\\w+$", RegexOption.IGNORE_CASE).find(l)?.groupValues?.get(1)
                "gdi" -> l.split(Regex("\\s+")).getOrNull(4)?.trim('"')
                "m3u" -> l.takeIf { it.isNotEmpty() && !it.startsWith("#") }
                else -> null
            }
            name?.let { File(dir, it) }
        }
    }.getOrDefault(emptyList())

    private val BIOS = Regex("(?i)(\\bbios\\b|\\[bios\\]|^scph[-_]?\\d+|^gba_bios|^bios[79]\\.|^firmware\\.bin|^syscard\\d|^dc_(boot|flash)|^(neogeo|pgm|skns|decocass|isgsm)\\.zip$|^kick\\d|^ps[12]_?bios)")
    private val NOT_GAMES = setOf("png", "jpg", "jpeg", "webp", "txt", "xml", "nfo", "sav", "srm", "state", "dat", "ini", "cfg", "db", "md", "pdf", "mp4")

    fun scan(systems: List<GameSystem>, roots: List<File>, clean: Boolean, groupDiscs: Boolean = true, hideBios: Boolean = true) {
        val stats = ScanStats()
        for (sys in systems) {
            val found = LinkedHashMap<String, Game>()
            for (root in roots) {
                val children = root.listFiles() ?: continue
                val wanted = sys.folders.map { it.lowercase() }.toSet()
                for (dir in children.filter { it.isDirectory && it.name.lowercase() in wanted }) {
                    stats.systemDirs.getOrPut(sys.id) { ArrayList() } += dir
                    val files = (dir.listFiles() ?: emptyArray()).toMutableList()
                    files.filter { it.isDirectory && !it.name.startsWith(".") && it.name.lowercase() !in SKIP_DIRS }
                        .forEach { sub -> sub.listFiles()?.let { files += it } }
                    // Tracks and discs that belong to a .cue / .gdi / .m3u are one game, not several
                    val parts = HashSet<String>()
                    if (groupDiscs) files.filter { it.isFile && it.extension.lowercase() in setOf("cue", "gdi", "m3u") && ".${it.extension.lowercase()}" in sys.extensions }
                        .forEach { sheet -> referencedBy(sheet).forEach { parts += it.path.lowercase() } }
                    var any = false
                    for (f in files) {
                        if (!f.isFile || f.name.startsWith(".")) continue
                        val ext = f.extension.lowercase()
                        if (hideBios && BIOS.containsMatchIn(f.name)) { stats.hiddenBios++; continue }
                        if (".$ext" !in sys.extensions) { if (ext !in NOT_GAMES && stats.unknown.size < 200) stats.unknown += f; continue }
                        if (f.path.lowercase() in parts) { stats.hiddenTracks++; continue }
                        found[f.path] = Game(f, sys, clean)
                        any = true
                    }
                    if (!any) stats.emptyFolders += dir
                }
            }
            sys.games = found.values.sortedBy { it.name.lowercase() }
        }
        index = HashMap<String, Game>().apply { for (s in systems) for (g in s.games) put(g.key, g) }
        lastScan = stats
    }

    // ----- ES-DE / EmulationStation gamelist.xml -----

    class Meta(val name: String, val desc: String, val developer: String, val publisher: String, val year: String,
               val genre: String, val players: String)

    /** Game details by game key, read from gamelist.xml files after each scan. */
    @Volatile var meta: Map<String, Meta> = emptyMap()

    /**
     * Reads ES-DE's gamelists (ES-DE/gamelists/<system>/gamelist.xml) and classic
     * EmulationStation / Batocera / RetroBat ones (gamelist.xml inside the ROM folder).
     */
    fun loadMeta(systems: List<GameSystem>) {
        val out = HashMap<String, Meta>()
        val esde = listOf(File(Environment.getExternalStorageDirectory(), "ES-DE/gamelists"))
        for (sys in systems) {
            if (sys.games.isEmpty()) continue
            val byName = HashMap<String, String>()
            for (g in sys.games) byName[g.file.name.lowercase()] = g.key
            val lists = ArrayList<File>()
            for (dir in lastScan.systemDirs[sys.id].orEmpty()) {
                lists += File(dir, "gamelist.xml")
                for (e in esde) lists += File(e, "${dir.name}/gamelist.xml")
            }
            for (f in lists.distinctBy { it.path }) if (f.isFile) runCatching { parseGamelist(f, byName, out) }
        }
        meta = out
    }

    private fun parseGamelist(f: File, byName: Map<String, String>, out: MutableMap<String, Meta>) {
        val p = android.util.Xml.newPullParser()
        f.inputStream().buffered().use { input ->
            p.setInput(input, null)
            var fields: HashMap<String, String>? = null
            var tag = ""
            while (true) {
                when (p.next()) {
                    org.xmlpull.v1.XmlPullParser.END_DOCUMENT -> return
                    org.xmlpull.v1.XmlPullParser.START_TAG -> {
                        tag = p.name
                        if (tag == "game") fields = HashMap()
                    }
                    org.xmlpull.v1.XmlPullParser.TEXT -> fields?.let { m -> if (tag.isNotEmpty()) m[tag] = (m[tag] ?: "") + p.text }
                    org.xmlpull.v1.XmlPullParser.END_TAG -> {
                        if (p.name == "game") fields?.let { m ->
                            val file = m["path"]?.trim()?.substringAfterLast('/')?.lowercase()
                            val key = file?.let { byName[it] }
                            if (key != null) out[key] = Meta(m["name"].orEmpty().trim(), m["desc"].orEmpty().trim(),
                                m["developer"].orEmpty().trim(), m["publisher"].orEmpty().trim(),
                                m["releasedate"].orEmpty().trim().take(4).takeIf { it.all(Char::isDigit) }.orEmpty(),
                                m["genre"].orEmpty().trim(), m["players"].orEmpty().trim())
                            fields = null
                        }
                        tag = ""
                    }
                }
            }
        }
    }

    /** Every game by key, rebuilt after each scan: Home looks games up hundreds of times per refresh. */
    @Volatile private var index: Map<String, Game> = emptyMap()

    fun find(systems: List<GameSystem>, key: String): Game? {
        val idx = index
        if (idx.isNotEmpty()) return idx[key]
        for (s in systems) for (g in s.games) if (g.key == key) return g
        return null
    }

    fun search(systems: List<GameSystem>, q: String): List<Game> {
        val query = q.trim().lowercase()
        if (query.isEmpty()) return emptyList()
        return systems.flatMap { s -> s.games.filter { query in it.name.lowercase() } }
    }
}
