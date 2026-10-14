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

    init { load() }

    fun load() {
        val j = readJson(file) ?: return
        theme = j.optString("theme", theme); sound = j.optBoolean("sound", sound)
        volume = j.optInt("volume", volume); clock24 = j.optBoolean("clock24", clock24)
        showBattery = j.optBoolean("showBattery", showBattery); cleanNames = j.optBoolean("cleanNames", cleanNames)
        hideEmpty = j.optBoolean("hideEmpty", hideEmpty); boot = j.optBoolean("boot", boot)
        animations = j.optBoolean("animations", animations); fps = j.optInt("fps", fps)
        swapAB = j.optBoolean("swapAB", swapAB); dual = j.optString("dual", dual)
        swapScreens = j.optBoolean("swapScreens", swapScreens); boost = j.optBoolean("boost", boost)
        showApps = j.optBoolean("showApps", showApps)
        romDirs = j.optJSONArray("romDirs").strings().toMutableList()
        systemEmu.clear(); systemEmu.putAll(j.optJSONObject("systemEmu").stringMap())
    }

    fun save() = writeJson(file, JSONObject().apply {
        put("theme", theme); put("sound", sound); put("volume", volume); put("clock24", clock24)
        put("showBattery", showBattery); put("cleanNames", cleanNames); put("hideEmpty", hideEmpty)
        put("boot", boot); put("animations", animations); put("fps", fps); put("swapAB", swapAB)
        put("dual", dual); put("swapScreens", swapScreens); put("boost", boost); put("showApps", showApps)
        put("romDirs", JSONArray(romDirs)); put("systemEmu", JSONObject(systemEmu as Map<*, *>))
    })
}

/** Favorites, recently played and play time. Games are keyed by ROM path. */
class PlayState(private val file: File) {
    val favorites = mutableListOf<String>()
    val recent = mutableListOf<String>()
    val times = HashMap<String, Long>()
    val launches = HashMap<String, Int>()
    var pendingKey: String? = null
    var pendingStart = 0L

    init {
        readJson(file)?.let { j ->
            favorites += j.optJSONArray("favorites").strings()
            recent += j.optJSONArray("recent").strings()
            j.optJSONObject("playtime")?.let { p -> p.keys().forEach { times[it] = p.getLong(it) } }
            j.optJSONObject("launches")?.let { p -> p.keys().forEach { launches[it] = p.getInt(it) } }
            pendingKey = j.optString("pendingKey").ifEmpty { null }
            pendingStart = j.optLong("pendingStart", 0)
        }
    }

    fun save() = writeJson(file, JSONObject().apply {
        put("favorites", JSONArray(favorites)); put("recent", JSONArray(recent))
        put("playtime", JSONObject(times as Map<*, *>)); put("launches", JSONObject(launches as Map<*, *>))
        put("pendingKey", pendingKey ?: ""); put("pendingStart", pendingStart)
    })

    fun isFavorite(k: String) = k in favorites
    fun toggleFavorite(k: String): Boolean {
        if (!favorites.remove(k)) favorites.add(k)
        save()
        return k in favorites
    }

    fun startPlay(k: String) {
        recent.remove(k); recent.add(0, k)
        while (recent.size > 30) recent.removeAt(recent.size - 1)
        launches[k] = (launches[k] ?: 0) + 1
        pendingKey = k; pendingStart = System.currentTimeMillis()
        save()
    }

    /** Called when Ryzer comes back to the front; credits the time spent in the emulator. */
    fun finishPlay() {
        val k = pendingKey ?: return
        val secs = (System.currentTimeMillis() - pendingStart) / 1000
        if (secs in 1..(12 * 3600)) times[k] = (times[k] ?: 0) + secs
        pendingKey = null
        save()
    }

    fun playtime(k: String) = times[k] ?: 0L
    fun total() = times.values.sum()
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

    /** Box art: next to the ROM, or in ES-DE's downloaded_media folder. */
    fun image(): File? {
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
