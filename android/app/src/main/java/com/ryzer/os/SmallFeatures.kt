package com.ryzer.os

// Logic behind the 3.2 "20 small features" update, kept free of Android so it runs in plain unit tests.

/** Typo-tolerant search: every word you type must match a word of the title, exactly, as a prefix or with a typo or two. */
object FuzzyMatch {
    private fun words(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim().split(' ').filter { it.isNotEmpty() }

    private fun distance(a: String, b: String): Int {
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            System.arraycopy(cur, 0, prev, 0, cur.size)
        }
        return prev[b.length]
    }

    /** Typos allowed for a word of this length. */
    private fun allowed(len: Int) = when { len <= 3 -> 0; len <= 6 -> 1; else -> 2 }

    /** Higher is better; null when the title doesn't match. */
    fun score(query: String, title: String): Int? {
        val q = words(query); val t = words(title)
        if (q.isEmpty() || t.isEmpty()) return null
        var total = 0
        for (w in q) {
            total += t.maxOfOrNull { tw ->
                when {
                    tw == w -> 3
                    tw.startsWith(w) -> 2
                    distance(w, tw.take(w.length + 1)) <= allowed(w.length) || distance(w, tw) <= allowed(w.length) -> 1
                    else -> 0
                }
            }?.takeIf { it > 0 } ?: return null
        }
        if (t.first().startsWith(q.first())) total += 2   // the title starts with what you typed
        return total
    }
}

/** What a ROM's file name says about it. */
object RomInfo {
    private val regions = listOf(
        "USA" to listOf("usa", "us", "america"),
        "EUR" to listOf("europe", "eu", "uk", "germany", "france", "spain", "italy", "australia"),
        "JPN" to listOf("japan", "jp", "jpn"),
        "WLD" to listOf("world"),
        "KOR" to listOf("korea"),
        "BRA" to listOf("brazil"),
        "ASI" to listOf("asia"),
    )

    /** The first region named in brackets, e.g. "(USA, Europe)" → USA; null when the name has none. */
    fun region(fileName: String): String? {
        for (group in Regex("\\(([^)]*)\\)").findAll(fileName)) {
            for (part in group.groupValues[1].split(',').map { it.trim().lowercase() }) {
                regions.firstOrNull { (_, names) -> part in names }?.let { return it.first }
            }
        }
        return null
    }
}

/** One small goal per week, the same for everyone in that week. */
object Challenges {
    enum class Kind { SYSTEMS, GAMES, MINUTES, SESSIONS }
    data class Challenge(val id: String, val title: String, val kind: Kind, val target: Int)
    data class Played(val key: String, val system: String, val seconds: Long)

    val ALL = listOf(
        Challenge("systems3", "Play 3 different systems", Kind.SYSTEMS, 3),
        Challenge("games3", "Play 3 different games", Kind.GAMES, 3),
        Challenge("minutes120", "Play for 2 hours", Kind.MINUTES, 120),
        Challenge("sessions7", "Have 7 play sessions", Kind.SESSIONS, 7),
        Challenge("games5", "Try 5 different games", Kind.GAMES, 5),
        Challenge("systems4", "Visit 4 different systems", Kind.SYSTEMS, 4),
        Challenge("minutes300", "Play for 5 hours", Kind.MINUTES, 300),
    )

    fun byId(id: String) = ALL.first { it.id == id }

    /** [week] counts weeks since 1970, so every device agrees on this week's challenge. */
    fun forWeek(week: Long): Challenge = ALL[(week % ALL.size).toInt()]

    fun progress(c: Challenge, sessions: List<Played>): Int = when (c.kind) {
        Kind.SYSTEMS -> sessions.map { it.system }.distinct().size
        Kind.GAMES -> sessions.map { it.key }.distinct().size
        Kind.MINUTES -> (sessions.sumOf { it.seconds } / 60).toInt()
        Kind.SESSIONS -> sessions.size
    }
}

/** Open-Meteo reports the weather as WMO codes. */
object WeatherCodes {
    fun describe(code: Int): String = when (code) {
        0 -> "Clear"
        1, 2 -> "Partly cloudy"
        3 -> "Cloudy"
        45, 48 -> "Fog"
        in 51..57 -> "Drizzle"
        in 61..67, in 80..82 -> "Rain"
        in 71..77, 85, 86 -> "Snow"
        in 95..99 -> "Storm"
        else -> "—"
    }
}

/** One well-known fact per system for the top-screen band (release years are each system's first launch). */
object ConsoleFacts {
    private val facts = mapOf(
        "switch" to "The Switch (2017) works both as a home console and as a handheld.",
        "3ds" to "The 3DS (2011) shows 3D pictures without glasses.",
        "wiiu" to "The Wii U (2012) introduced a controller with its own touch screen.",
        "wii" to "The Wii (2006) made motion controls mainstream with its pointer remote.",
        "gc" to "The GameCube (2001) played games from small 8 cm discs.",
        "ps2" to "The PlayStation 2 (2000) is one of the best-selling consoles ever made.",
        "psvita" to "The PS Vita (2011) has a touch pad on the back as well as a touch screen.",
        "nds" to "The Nintendo DS (2004) has two screens; the lower one is a touch screen.",
        "psp" to "The PSP (2004) played games from UMD discs.",
        "dreamcast" to "The Dreamcast (1998) was Sega's last home console.",
        "saturn" to "The Sega Saturn (1994) was built around two main processors.",
        "n64" to "The Nintendo 64 (1996) made the analog stick standard for 3D games.",
        "psx" to "The original PlayStation (1994) brought CD-ROM games to millions of homes.",
        "gba" to "The Game Boy Advance (2001) also plays most Game Boy and Game Boy Color games.",
        "gbc" to "The Game Boy Color (1998) added a colour screen and still played Game Boy games.",
        "gb" to "The original Game Boy (1989) ran on four AA batteries.",
        "snes" to "The Super Nintendo (1990) could scale and rotate backgrounds with Mode 7.",
        "nes" to "The NES, the Famicom in Japan (1983), helped bring video games back into homes.",
        "genesis" to "The Mega Drive (1988) was sold as the Genesis in North America.",
        "segacd" to "The Sega CD (1991) added a CD drive to the Mega Drive / Genesis.",
        "mastersystem" to "The Master System was Sega's 8-bit home console of the mid-1980s.",
        "gamegear" to "The Game Gear (1990) was Sega's colour handheld.",
        "pce" to "The PC Engine (1987) was sold as the TurboGrafx-16 in North America.",
        "arcade" to "Arcade emulators like FinalBurn Neo and MAME run the original machines' code.",
        "atari2600" to "The Atari 2600 (1977) popularised swappable game cartridges.",
        "ngpc" to "The Neo Geo Pocket Color (1999) was SNK's colour handheld.",
        "dos" to "MS-DOS games filled PCs through the 1980s and 1990s.",
    )

    fun forSystem(id: String): List<String> = listOfNotNull(facts[id])

    /** The fact to show today ([day] counts days since 1970). */
    fun pick(id: String, day: Long): String? = forSystem(id).let { if (it.isEmpty()) null else it[(day % it.size).toInt()] }
}

object ListOps {
    /** Moves [id] one place left (dir -1) or right (+1); unknown ids and moves past an end change nothing. */
    fun <T> move(list: List<T>, id: T, dir: Int): List<T> {
        val i = list.indexOf(id); val j = i + dir
        if (i < 0 || j !in list.indices) return list
        return list.toMutableList().apply { this[i] = this[j]; this[j] = id }
    }

    /** [items] with the ones in [saved] first, in that order; the rest keep their order. */
    fun <T> ordered(items: List<T>, saved: List<T>): List<T> {
        val first = saved.filter { it in items }
        return first + items.filter { it !in first }
    }

    /** Favorites not played for [days] days (or never), in favorites order. */
    fun forgotten(favorites: List<String>, lastPlayed: Map<String, Long>, now: Long, days: Int): List<String> =
        favorites.filter { (lastPlayed[it] ?: 0L) < now - days * 86_400_000L }

    /** Percent of games marked beaten or completed. */
    fun completion(statuses: List<String>): Int =
        if (statuses.isEmpty()) 0 else statuses.count { it == "beaten" || it == "completed" } * 100 / statuses.size
}
