package com.ryzer.os

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.zip.CRC32
import java.util.zip.ZipFile
import kotlin.math.max
import kotlin.math.min

/**
 * Downloads artwork for games that have none.
 *
 * 1. Identify the game: Ryzer reads the ROM's CRC32 (free for .zip files, which store it) and looks
 *    it up in the libretro database (No-Intro / Redump / FBNeo DATs). That gives the exact
 *    official name even when the file is called "pkmn_emerald.gba". Discs and big files are
 *    matched by name instead, with fuzzy matching against the same database.
 * 2. Download the picture from the libretro thumbnail library (thumbnails.libretro.com, mirrored
 *    on GitHub). No account or API key needed.
 * 3. Save a small copy in RyzerOS/covers/<system>/<rom name>.webp, which Ryzer shows everywhere.
 *    ROM folders are never written to.
 */
object Scraper {
    /** Ryzer system id -> libretro system name (database + thumbnail folders use the same names). */
    private val SYSTEMS = mapOf(
        "nes" to "Nintendo - Nintendo Entertainment System", "snes" to "Nintendo - Super Nintendo Entertainment System",
        "n64" to "Nintendo - Nintendo 64", "gb" to "Nintendo - Game Boy", "gbc" to "Nintendo - Game Boy Color",
        "gba" to "Nintendo - Game Boy Advance", "nds" to "Nintendo - Nintendo DS", "3ds" to "Nintendo - Nintendo 3DS",
        "gc" to "Nintendo - GameCube", "wii" to "Nintendo - Wii", "wiiu" to "Nintendo - Wii U",
        "psx" to "Sony - PlayStation", "ps2" to "Sony - PlayStation 2", "psp" to "Sony - PlayStation Portable",
        "psvita" to "Sony - PlayStation Vita", "genesis" to "Sega - Mega Drive - Genesis", "segacd" to "Sega - Mega-CD - Sega CD",
        "mastersystem" to "Sega - Master System - Mark III", "gamegear" to "Sega - Game Gear", "saturn" to "Sega - Saturn",
        "dreamcast" to "Sega - Dreamcast", "pce" to "NEC - PC Engine - TurboGrafx 16", "atari2600" to "Atari - 2600",
        "ngpc" to "SNK - Neo Geo Pocket Color", "arcade" to "FBNeo - Arcade Games", "dos" to "DOS",
    )
    /** Where each system's database lives in libretro-database/metadat. */
    private val DATS = mapOf(
        "gc" to "redump", "wii" to "redump", "psx" to "redump", "ps2" to "redump", "segacd" to "redump",
        "saturn" to "redump", "dreamcast" to "redump", "arcade" to "fbneo-split",
    )
    /** Cartridge systems whose ROM checksum identifies the game. */
    private val HASHABLE = setOf("nes", "snes", "n64", "gb", "gbc", "gba", "nds", "genesis", "mastersystem", "gamegear",
        "pce", "atari2600", "ngpc")
    private const val HASH_LIMIT = 64L * 1024 * 1024

    val TYPES = listOf("boxart" to "Box art", "snap" to "Screenshot", "title" to "Title screen")
    private fun folder(type: String) = when (type) { "snap" -> "Named_Snaps"; "title" -> "Named_Titles"; else -> "Named_Boxarts" }

    fun supports(sys: GameSystem) = sys.id in SYSTEMS

    // ----- state shown in the UI -----
    @Volatile var running = false
        private set
    @Volatile var total = 0
    @Volatile var checked = 0
    @Volatile var found = 0
    @Volatile var current = ""
    @Volatile var status = ""
    /** Newest covers first, for the progress screen. */
    val recent = ArrayList<Game>()
    @Volatile private var cancel = false
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun coversDir(app: Ryzer) = File(app.home, "covers")
    fun coverFile(app: Ryzer, g: Game) = File(File(coversDir(app), g.system.id), g.file.nameWithoutExtension + ".webp")

    /** Games that have no picture yet (and weren't already looked up recently without luck). */
    fun missing(app: Ryzer, games: List<Game>, retryMisses: Boolean): List<Game> {
        val misses = if (retryMisses) emptyMap() else loadMisses(app)
        val now = System.currentTimeMillis()
        return games.filter { g ->
            supports(g.system) && g.image() == null && (misses[g.key]?.let { now - it > 14 * 86_400_000L } ?: true)
        }
    }

    fun stop() { cancel = true }

    /**
     * Starts downloading in the background. `auto` runs stay quiet (one notice at the end);
     * manual runs show the progress screen. With `onlyMissing`, games that already have a
     * picture are skipped (worked out on the background thread: it checks files for every game).
     */
    fun start(app: Ryzer, games: List<Game>, auto: Boolean, force: Boolean = false, onlyMissing: Boolean = false, retryMisses: Boolean = false) {
        if (running) { if (!auto) app.push(ScrapeScreen(app)); return }
        if (games.isEmpty()) { if (!auto) app.notify("No games here"); return }
        if (app.cfg.artWifiOnly && !app.wifiConnected()) {
            if (!auto) app.notify("Connect to Wi-Fi first (or turn off 'Only on Wi-Fi' in Artwork settings)", 4f)
            return
        }
        running = true; cancel = false
        total = 0; checked = 0; found = 0; current = ""; status = "Checking which games need artwork..."
        synchronized(recent) { recent.clear() }
        val type = app.cfg.artType
        if (!auto) app.push(ScrapeScreen(app))
        worker.execute {
            val dbs = HashMap<String, Db?>()
            val misses = loadMisses(app).toMutableMap()
            val todo = if (onlyMissing) missing(app, games, retryMisses) else games
            total = todo.size
            try {
                for (g in todo) {
                    // Don't compete with the emulator: wait while a game is running
                    while (app.state.pendingKey != null && !cancel) { status = "Paused while you play"; Thread.sleep(2000) }
                    if (cancel) break
                    current = app.title(g)
                    val ok = runCatching {
                        val id = g.system.id
                        val db = if (dbs.containsKey(id)) dbs[id] else {
                            status = "Loading the ${g.system.short} game list..."
                            loadDb(app, g.system).also { dbs[id] = it }
                        }
                        status = "Looking up ${current}"
                        scrapeOne(app, g, db, type, force)
                    }.onFailure { Crash.log(app.ctx, RuntimeException("Artwork lookup failed for ${g.file.name}", it)) }.getOrDefault(false)
                    checked++
                    if (ok) {
                        found++
                        misses.remove(g.key)
                        main.post { G.forget(coverFile(app, g)); g.refreshImage(); synchronized(recent) { recent.add(0, g); if (recent.size > 12) recent.removeAt(recent.size - 1) }; app.ctx.redraw() }
                    } else misses[g.key] = System.currentTimeMillis()
                }
            } finally {
                saveMisses(app, misses)
                main.post {
                    running = false
                    status = if (cancel) "Stopped" else "Done"
                    current = ""
                    if (checked > 0 || !auto) app.notify(if (checked == 0) "Every game already has artwork" else
                        "Artwork: found $found of $checked game${if (checked == 1) "" else "s"}", 3f)
                    if (found > 0 && app.cfg.ryzerNotices) Notifs.ryzer("Artwork downloaded", "Found pictures for $found game${if (found == 1) "" else "s"}.", banner = auto)
                    app.safely { app.current?.onResume() }
                    app.ctx.redraw()
                }
            }
        }
    }

    // ----- one game -----

    private fun scrapeOne(app: Ryzer, g: Game, db: Db?, type: String, force: Boolean): Boolean {
        val out = coverFile(app, g)
        if (out.isFile && !force) return true
        val sysName = SYSTEMS[g.system.id] ?: return false
        for (name in candidates(g, db).take(6)) {
            if (cancel) return false
            val bytes = download(sysName, folder(type), name) ?: continue
            if (save(bytes, out)) return true
        }
        return false
    }

    /** Names to try, best first: from the checksum, the ROM's file name, then fuzzy matches. */
    private fun candidates(g: Game, db: Db?): List<String> {
        val out = LinkedHashSet<String>()
        val stem = g.file.nameWithoutExtension
        if (db != null) {
            romCrc(g)?.let { crc -> db.byCrc[crc]?.let { out += it } }
            db.byFile[stem.lowercase()]?.let { out += it }          // arcade sets: "mslug" -> "Metal Slug - Super Vehicle-001"
        }
        out += stem                                                  // already a No-Intro / Redump name
        val core = normalize(stem)
        if (db != null && core.isNotEmpty()) {
            val regions = preferredRegions(stem)
            val exact = db.byCore[core].orEmpty()
            out += exact.sortedBy { regionRank(it, regions) }
            if (exact.isEmpty()) out += fuzzy(core, db).sortedBy { regionRank(it, regions) }.take(3)
        }
        if (db == null) {
            val clean = cleanName(stem)
            for (r in listOf("USA", "World", "Europe", "Japan")) out += "$clean ($r)"
            out += clean
        }
        return out.toList()
    }

    private fun romCrc(g: Game): String? {
        if (g.system.id !in HASHABLE) return null
        val f = g.file
        return runCatching {
            when (f.extension.lowercase()) {
                "zip" -> ZipFile(f).use { z ->
                    z.entries().asSequence().filter { !it.isDirectory }.maxByOrNull { it.size }?.crc?.takeIf { it >= 0 }
                }
                "7z" -> null
                else -> if (f.length() > HASH_LIMIT) null else {
                    val c = CRC32()
                    f.inputStream().buffered(1 shl 16).use { input ->
                        val buf = ByteArray(1 shl 16)
                        while (true) { val n = input.read(buf); if (n < 0) break; c.update(buf, 0, n) }
                    }
                    c.value
                }
            }?.let { "%08X".format(it) }
        }.getOrNull()
    }

    // ----- matching -----

    private val TAGS = Regex("\\s*[(\\[][^)\\]]*[)\\]]")
    private val NON_ALNUM = Regex("[^a-z0-9]+")

    /** "Legend of Zelda, The - Minish Cap (USA)" and "the_legend_of_zelda_minish_cap" both become "legend of zelda minish cap". */
    fun normalize(s: String): String {
        var t = TAGS.replace(s, "").lowercase().replace("&", " and ").replace("_", " ")
        t = t.replace(Regex(",\\s*the\\b"), " ").replace(Regex("^the\\s+"), "")
        return NON_ALNUM.replace(t, " ").trim().split(" ").filter { it.isNotEmpty() && it != "the" }.joinToString(" ")
    }

    private fun fuzzy(core: String, db: Db): List<String> {
        val words = core.split(" ").toSet()
        if (words.isEmpty()) return emptyList()
        var best = 0.0
        val scored = ArrayList<Pair<String, Double>>()
        for ((k, names) in db.byCore) {
            val other = db.words[k] ?: continue
            if (other.isEmpty()) continue
            val inter = words.count { it in other }
            if (inter == 0) continue
            val score = inter.toDouble() / (words.size + other.size - inter)   // Jaccard
            if (score >= 0.6 && score >= best - 0.05) {
                best = max(best, score)
                names.forEach { scored += it to score }
            }
        }
        return scored.filter { it.second >= best - 0.05 }.sortedByDescending { it.second }.map { it.first }
    }

    private fun preferredRegions(stem: String): List<String> {
        val tags = Regex("\\(([^)]*)\\)").findAll(stem).joinToString(",") { it.groupValues[1] }
        val fromFile = listOf("USA", "Europe", "Japan", "World").filter { it in tags }
        return (fromFile + listOf("USA", "World", "Europe", "Japan")).distinct()
    }

    private fun regionRank(name: String, regions: List<String>): Int {
        val i = regions.indexOfFirst { name.contains(it) }
        val penalty = if (Regex("\\((Proto|Beta|Demo|Sample|Kiosk)", RegexOption.IGNORE_CASE).containsMatchIn(name)) 50 else 0
        return (if (i < 0) regions.size else i) + penalty
    }

    // ----- database (DAT) -----

    private class Db {
        val byCrc = HashMap<String, String>()
        val byFile = HashMap<String, String>()
        val byCore = HashMap<String, MutableList<String>>()
        val words = HashMap<String, Set<String>>()
    }

    private val ROM_LINE = Regex("rom \\( name (?:\"([^\"]+)\"|(\\S+)).*?crc ([0-9A-Fa-f]{8})")

    /** Downloads (once a month) and parses the system's game list. */
    private fun loadDb(app: Ryzer, sys: GameSystem): Db? {
        val name = SYSTEMS[sys.id] ?: return null
        if (sys.id in setOf("wiiu", "dos")) return null
        val kind = DATS[sys.id] ?: "no-intro"
        val cache = File(File(coversDir(app), ".db"), "${sys.id}.dat")
        if (!cache.isFile || System.currentTimeMillis() - cache.lastModified() > 30 * 86_400_000L) {
            val url = "https://raw.githubusercontent.com/libretro/libretro-database/master/metadat/$kind/${enc(name)}.dat"
            val bytes = get(url, 12 * 1024 * 1024)
            if (bytes != null) { cache.parentFile?.mkdirs(); cache.writeBytes(bytes) }
        }
        if (!cache.isFile) return null
        val db = Db()
        var game: String? = null
        cache.bufferedReader().useLines { lines ->
            for (raw in lines) {
                val line = raw.trim()
                when {
                    line.startsWith("game (") -> game = null
                    game == null && line.startsWith("name \"") -> {
                        val n = line.substringAfter("name \"").substringBeforeLast('"')
                        game = n
                        val core = normalize(n)
                        if (core.isNotEmpty()) {
                            db.byCore.getOrPut(core) { ArrayList(2) }.add(n)
                            if (core !in db.words) db.words[core] = core.split(" ").toSet()
                        }
                    }
                    line.startsWith("rom (") && game != null -> ROM_LINE.find(line)?.let { m ->
                        db.byCrc.putIfAbsent(m.groupValues[3].uppercase(), game!!)
                        val file = (m.groupValues[1].ifEmpty { m.groupValues[2] }).substringBeforeLast('.').lowercase()
                        db.byFile.putIfAbsent(file, game!!)
                    }
                }
            }
        }
        return db
    }

    // ----- network -----

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    /** libretro's thumbnail file names replace these characters with "_". */
    private fun thumbName(name: String) = name.replace(Regex("[&*/:`<>?\\\\|\"]"), "_")

    private fun download(sysName: String, folder: String, game: String): ByteArray? {
        val file = enc(thumbName(game)) + ".png"
        return get("https://thumbnails.libretro.com/${enc(sysName)}/$folder/$file", 8 * 1024 * 1024)
            ?: get("https://raw.githubusercontent.com/libretro-thumbnails/${enc(sysName.replace(' ', '_'))}/master/$folder/$file", 8 * 1024 * 1024)
    }

    private fun get(url: String, limit: Int): ByteArray? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000; readTimeout = 30_000
                setRequestProperty("User-Agent", "RyzerOS/${BuildInfo.version} (Android frontend)")
            }
            if (conn.responseCode != 200) return null
            conn.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(1 shl 15)
                while (true) {
                    val n = input.read(buf); if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > limit) return null
                }
                out.toByteArray()
            }
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    /** Shrinks the picture to at most 512 px and saves it as WebP (keeps transparency, ~30 KB). */
    private fun save(bytes: ByteArray, out: File): Boolean {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        if (o.outWidth <= 0) return false
        var s = 1
        while (max(o.outWidth, o.outHeight) / (s * 2) >= 512) s *= 2
        val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s }) ?: return false
        val scale = min(1f, 512f / max(src.width, src.height))
        val bmp = if (scale < 1f) Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true) else src
        out.parentFile?.mkdirs()
        val tmp = File(out.path + ".tmp")
        @Suppress("DEPRECATION")
        val fmt = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
        tmp.outputStream().use { bmp.compress(fmt, 85, it) }
        if (bmp !== src) bmp.recycle()
        src.recycle()
        return tmp.renameTo(out)
    }

    // ----- games already looked up without luck -----

    private fun missFile(app: Ryzer) = File(coversDir(app), ".misses.json")

    private fun loadMisses(app: Ryzer): Map<String, Long> = runCatching {
        val j = JSONObject(missFile(app).readText())
        j.keys().asSequence().associateWith { j.getLong(it) }
    }.getOrDefault(emptyMap())

    private fun saveMisses(app: Ryzer, m: Map<String, Long>) {
        runCatching { missFile(app).parentFile?.mkdirs(); missFile(app).writeText(JSONObject(m as Map<*, *>).toString()) }
    }

    fun clearMisses(app: Ryzer) { missFile(app).delete() }

    /** Deletes every downloaded picture (your own art next to the ROMs is never touched). */
    fun deleteAll(app: Ryzer) {
        coversDir(app).listFiles()?.filter { it.isDirectory && it.name != ".db" }?.forEach { it.deleteRecursively() }
        clearMisses(app)
        app.systems.forEach { s -> s.games.forEach { it.refreshImage() } }
        G.clearImages()
    }
}

/** Live progress while artwork downloads. B hides it; the download keeps going in the background. */
class ScrapeScreen(app: Ryzer) : Screen(app) {
    override val title = "Artwork"

    override fun handle(a: String) {
        when (a) {
            "x" -> if (Scraper.running) { Scraper.stop(); app.notify("Stopping after this game...") }
            "a", "b" -> app.pop()
        }
    }

    override fun draw(c: Canvas, w: Float, h: Float) {
        G.topBar(c, app, th, "Downloading artwork", w)
        val s = Scraper
        val frac = if (s.total == 0) 0f else s.checked / s.total.toFloat()
        G.text(c, if (s.running) "${s.checked} of ${s.total} games checked" else "${s.status}: ${s.checked} of ${s.total} checked",
            20f, 54f, 24f, th.c("text"), isBold = true, maxW = w - 40)
        G.text(c, "Found ${s.found}", w - 20, 54f, 24f, th.c("good"), align = 'r', isBold = true)
        val bar = RectF(20f, 90f, w - 20, 104f)
        G.rect(c, bar, th.c("panel"), 7f)
        G.rect(c, RectF(bar.left, bar.top, bar.left + bar.width() * frac, bar.bottom), th.c("accent"), 7f)
        if (s.running) G.text(c, s.status, 20f, 112f, 18f, th.c("text_dim"), maxW = w - 40)
        val shown = synchronized(s.recent) { s.recent.toList() }
        if (shown.isEmpty()) {
            G.text(c, if (s.running) "Covers appear here as they're found." else "No new pictures this time.", w / 2, h / 2, 22f, th.c("text_dim"), align = 'c')
            return
        }
        val cols = max(3, (w / 120).toInt())
        val cw = (w - 40 - (cols - 1) * 10) / cols
        val chh = cw * 1.3f
        shown.take(cols * 2).forEachIndexed { i, g ->
            val r = RectF(20 + (i % cols) * (cw + 10), 146 + (i / cols) * (chh + 30), 20 + (i % cols) * (cw + 10) + cw, 146 + (i / cols) * (chh + 30) + chh)
            G.panel(c, r, th, i == 0)
            G.image(g.image())?.let { G.drawImageFit(c, it, RectF(r.left + 4, r.top + 4, r.right - 4, r.bottom - 4)) }
            G.text(c, app.title(g), r.centerX(), r.bottom + 4, 15f, th.c("text_dim"), align = 'c', maxW = cw)
        }
    }

    override fun drawTop(c: Canvas, w: Float, h: Float) {
        val g = synchronized(Scraper.recent) { Scraper.recent.firstOrNull() }
        val bmp = g?.let { G.image(it.image()) }
        if (bmp == null) { app.drawDefaultTop(c, w, h); return }
        G.topBar(c, app, th, "", w)
        val box = G.drawImageFit(c, bmp, RectF(w / 2 - 150, 50f, w / 2 + 150, h - 80))
        G.text(c, app.title(g!!), w / 2, box.bottom + 10, 26f, th.c("text"), align = 'c', isBold = true, maxW = w - 40)
    }

    override fun hints() = if (Scraper.running) listOf("X" to "Stop", "B" to "Hide") else listOf("B" to "Back")
}

/** Settings > Library > Artwork. */
fun artworkMenu(app: Ryzer): Menu {
    val cfg = app.cfg
    return Menu(app, "Artwork", listOf(
        Item("Download missing artwork", "Finds pictures for every game that doesn't have one yet.", value = {
            if (Scraper.running) "${Scraper.checked}/${Scraper.total}" else "" },
            select = { Scraper.start(app, app.allGames(), auto = false, onlyMissing = true) }),
        Item("Automatic downloads", "After each library scan, new games get their artwork in the background.",
            value = { if (cfg.autoArt) "On" else "Off" }, change = { cfg.autoArt = !cfg.autoArt; cfg.save() }),
        Item("Only on Wi-Fi", value = { if (cfg.artWifiOnly) "On" else "Off" }, change = { cfg.artWifiOnly = !cfg.artWifiOnly; cfg.save() }),
        Item("Picture type", "What to download for new games. Already downloaded pictures stay until you delete them.",
            value = { Scraper.TYPES.firstOrNull { it.first == cfg.artType }?.second ?: cfg.artType },
            change = { d -> val ids = Scraper.TYPES.map { it.first }; cfg.artType = ids[(ids.indexOf(cfg.artType) + d + ids.size) % ids.size]; cfg.save() }),
        Item("Try again for games not found", "Games without a match are skipped for 2 weeks. This looks them up again now.",
            select = { Scraper.clearMisses(app); Scraper.start(app, app.allGames(), auto = false, onlyMissing = true, retryMisses = true) }),
        Item("Show progress", select = { app.push(ScrapeScreen(app)) }),
        Item("Delete downloaded artwork", "Removes the pictures in RyzerOS/covers. Your own art next to your ROMs is never touched.",
            select = { app.push(Confirm(app, "Delete all downloaded artwork?", yes = "Delete") { Scraper.deleteAll(app); app.notify("Downloaded artwork deleted") }) }),
        Item("How it works", select = { app.push(Message(app, "Artwork", ART_HELP)) }),
    ))
}

const val ART_HELP = "Ryzer identifies each game by reading its ROM: the CRC32 checksum (instant for .zip files) is looked up in the " +
    "libretro database of No-Intro, Redump and FBNeo game lists, so even oddly named files are recognised. Discs and large " +
    "files are matched by name.\n\nPictures come from the free libretro thumbnail library (thumbnails.libretro.com, " +
    "with GitHub as a backup). No account is needed.\n\nThey're saved small (about 30 KB each) in RyzerOS/covers. " +
    "Your ROM folders are never changed. Art you already have next to your ROMs or from ES-DE always wins.\n\n" +
    "Ryzer only goes online when it downloads artwork. Turn off 'Automatic downloads' to only do it when you ask."
