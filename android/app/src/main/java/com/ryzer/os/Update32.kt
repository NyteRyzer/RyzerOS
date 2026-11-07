package com.ryzer.os

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// The 3.2 small features that need Android: weather, weekly challenges, game options, tags and covers.
// Their logic lives in SmallFeatures.kt (unit tested).

/** Current weather for your city from Open-Meteo (free, no account). Fetched at most hourly (about 1 KB). */
object Weather {
    @Volatile var temp: Double? = null
    @Volatile var code = -1
    @Volatile private var fetchedAt = 0L
    @Volatile private var busy = false

    /** "72° Partly cloudy", or null when weather is off or not fetched yet. */
    fun line(app: Ryzer): String? {
        if (!app.cfg.weather || app.cfg.weatherCity.isEmpty()) return null
        val t = temp ?: return null
        return "${t.toInt()}°${if (app.cfg.weatherF) "F" else "C"} ${WeatherCodes.describe(code)}"
    }

    fun maybeRefresh(app: Ryzer, force: Boolean = false) {
        val cfg = app.cfg
        if (!cfg.weather || cfg.weatherCity.isEmpty() || busy) return
        if (!force && System.currentTimeMillis() - fetchedAt < 3_600_000L) return
        busy = true
        Thread {
            runCatching {
                val unit = if (cfg.weatherF) "&temperature_unit=fahrenheit" else ""
                val j = JSONObject(get("https://api.open-meteo.com/v1/forecast?latitude=${cfg.weatherLat}&longitude=${cfg.weatherLon}" +
                    "&current=temperature_2m,weather_code$unit")).getJSONObject("current")
                temp = j.getDouble("temperature_2m"); code = j.getInt("weather_code"); fetchedAt = System.currentTimeMillis()
            }
            busy = false
            app.ctx.runOnUiThread { app.ctx.redraw() }
        }.start()
    }

    /** Looks a city up by name; [done] gets "City, Country" or null on the main thread. */
    fun setCity(app: Ryzer, name: String, done: (String?) -> Unit) {
        Thread {
            val found = runCatching {
                val r = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?count=1&name=" + URLEncoder.encode(name, "UTF-8")))
                r.optJSONArray("results")?.optJSONObject(0)
            }.getOrNull()
            app.ctx.runOnUiThread {
                if (found == null) { done(null); return@runOnUiThread }
                app.cfg.weatherCity = listOf(found.optString("name"), found.optString("country")).filter { it.isNotEmpty() }.joinToString(", ")
                app.cfg.weatherLat = found.getDouble("latitude"); app.cfg.weatherLon = found.getDouble("longitude"); app.cfg.weather = true
                app.cfg.save()
                temp = null; maybeRefresh(app, force = true)
                done(app.cfg.weatherCity)
            }
        }.start()
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "RyzerOS/${BuildInfo.version}")
        return c.inputStream.bufferedReader().use { it.readText() }
    }
}

fun weatherMenu(app: Ryzer): Menu {
    val cfg = app.cfg
    return Menu(app, "Weather", listOf(
        Item("Show weather", "Temperature and sky on the top screen, from Open-Meteo. Updates once an hour (a tiny download).",
            value = { if (cfg.weather && cfg.weatherCity.isNotEmpty()) "On" else "Off" },
            change = { _ -> if (cfg.weatherCity.isEmpty()) app.notify("Pick your city first") else { cfg.weather = !cfg.weather; cfg.save(); Weather.maybeRefresh(app, true) } }),
        Item("City", value = { cfg.weatherCity.ifEmpty { "Not set" } }, select = {
            app.push(Keyboard(app, "Your city", "", 40) { name ->
                if (name.isNotBlank()) { app.notify("Looking up $name..."); Weather.setCity(app, name.trim()) { city ->
                    app.notify(city?.let { "Weather for $it" } ?: "Couldn't find \"$name\" (check Wi-Fi)") } }
            }) }),
        Item("Units", value = { if (cfg.weatherF) "°F" else "°C" }, change = { _ -> cfg.weatherF = !cfg.weatherF; cfg.save(); Weather.maybeRefresh(app, true) }),
    ))
}

/** This week's small goal: checked after every session, one notice when you reach it. */
object WeeklyChallenge {
    fun week(now: Long = System.currentTimeMillis()): Long {
        val tz = java.util.TimeZone.getDefault().getOffset(now)
        return (now + tz) / 86_400_000L / 7   // Monday-ish weeks, the same for everyone in one time zone
    }

    fun current() = Challenges.forWeek(week())

    fun progress(app: Ryzer): Pair<Challenges.Challenge, Int> {
        val wk = week()
        val c = Challenges.forWeek(wk)
        val played = app.state.sessions.filter { week(it.second) == wk }.map { (k, _, secs) ->
            Challenges.Played(k, Library.find(app.systems, k)?.system?.id ?: k.substringBeforeLast('/'), secs)
        }
        return c to Challenges.progress(c, played).coerceAtMost(c.target)
    }

    fun check(app: Ryzer) {
        val (c, n) = progress(app)
        val wk = week().toString()
        if (n >= c.target && app.cfg.challengeDone != wk) {
            app.cfg.challengeDone = wk; app.cfg.save()
            app.sounds.play("fav")
            Notifs.ryzer("Weekly challenge done!", "${c.title}. A new one starts next week.")
        }
    }

    fun line(app: Ryzer): String = progress(app).let { (c, n) -> "${c.title}: $n / ${c.target}" + if (n >= c.target) "  Done!" else "" }
}

/** The 3.2 entries for a game's options (START on a game). */
fun gameExtraItems(app: Ryzer, g: Game, refresh: () -> Unit): List<Item> {
    val st = app.state
    val kid = app.cfg.kidMode
    val items = mutableListOf(
        Item("Up Next", "Queue it: Up Next games come first on the Continue tab, and leave the list once you play them.",
            value = { if (g.key in st.upNext) "Queued" else "No" }, change = { _ ->
                if (!st.upNext.remove(g.key)) st.upNext.add(g.key); st.save() }),
        Item("Tags", "Your own labels, separated by commas (Co-op, RPG, Short...). Filter a list by tag in its options.",
            value = { st.tags[g.key]?.joinToString(", ")?.ifEmpty { null } ?: "None" }, select = {
                app.push(Keyboard(app, "Tags for ${app.title(g)}", st.tags[g.key]?.joinToString(", ") ?: "", 60) { text ->
                    val tags = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                    if (tags.isEmpty()) st.tags.remove(g.key) else st.tags[g.key] = tags.toMutableList()
                    st.save(); refresh() }) }),
    )
    if (!kid) {
        items += Item("Play with...", "Start this time with another emulator, without changing the default.", select = {
            val choices = Launcher.choices(app, g.system).filter { it.second }
            if (choices.isEmpty()) app.notify("No ${g.system.short} emulators installed")
            else app.push(Menu(app, "Play ${app.title(g)} with", choices.map { (id, _) ->
                Item(Launcher.label(app, id), select = { app.pop(); app.pop(); app.playOnceWith = id; app.launch(g) }) }))
        })
        items += Item("Set cover from gallery", "Use any picture as this game's box art.", select = { setCustomCover(app, g) })
    }
    return items
}

/** Copies a picked picture into RyzerOS/covers/custom/<system>/<rom>.jpg (it wins over every other cover). */
fun setCustomCover(app: Ryzer, g: Game) {
    val dir = Library.coversDir ?: run { app.notify("Ryzer can't write to its folder"); return }
    app.ctx.pickPhoto { uri ->
        app.runInBackground({
            val bmp = app.ctx.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) } ?: error("not a picture")
            val scale = minOf(1f, 600f / maxOf(bmp.width, bmp.height))
            val small = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
            val out = File(File(dir, "custom/${g.system.id}").apply { mkdirs() }, "${g.file.nameWithoutExtension}.jpg")
            out.outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            G.forget(out)
        }) { e ->
            g.refreshImage()
            app.notify(if (e == null) "New cover for ${app.title(g)}" else "Couldn't use that picture")
        }
    }
}

/** Games in [games] whose title matches [q], best match first (typos allowed). */
fun fuzzyFilter(app: Ryzer, games: List<Game>, q: String): List<Game> =
    games.mapNotNull { g -> FuzzyMatch.score(q, app.title(g))?.let { g to it } }.sortedByDescending { it.second }.map { it.first }

/** Storage used per system, for the Library check-up. */
fun storageLines(app: Ryzer): String {
    val per = app.systems.filter { it.games.isNotEmpty() }.map { s -> s to s.games.sumOf { it.file.length() } }.sortedByDescending { it.second }
    if (per.isEmpty()) return ""
    val total = per.sumOf { it.second }
    fun gb(b: Long) = if (b >= 1_000_000_000L) "%.1f GB".format(b / 1e9) else "%.0f MB".format(b / 1e6)
    return "\nStorage (${gb(total)} in total):\n" + per.joinToString("\n") { (s, b) -> "  ${s.name}: ${gb(b)}" } + "\n"
}
