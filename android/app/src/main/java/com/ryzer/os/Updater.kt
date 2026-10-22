package com.ryzer.os

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.graphics.Canvas
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Keeps Ryzer up to date from its GitHub releases.
 *
 * Once a day (on Wi-Fi) Ryzer asks GitHub for the newest release. If it's newer, it downloads
 * RyzerOS.apk in the background and offers to install it. Installing uses Android's own
 * package installer: Android checks the update is signed with the same key as the installed
 * app, so only real Ryzer builds can update it. Your settings, themes and play history live
 * in the RyzerOS folder and are kept.
 */
object Updater {
    private const val API = "https://api.github.com/repos/NyteRyzer/RyzerOS/releases/latest"
    private const val LATEST = "https://github.com/NyteRyzer/RyzerOS/releases/latest"
    const val ACTION_STATUS = "com.ryzer.os.UPDATE_STATUS"

    class Release(val version: String, val notes: String, val apkUrl: String, val size: Long)

    enum class State { IDLE, CHECKING, DOWNLOADING, READY, INSTALLING, FAILED }

    @Volatile var state = State.IDLE
        private set
    @Volatile var release: Release? = null
        private set
    @Volatile var progress = 0f
    @Volatile var message = ""
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private fun apkFile(app: Ryzer) = File(app.ctx.cacheDir, "update/RyzerOS.apk")

    /** "2.10.0" > "2.9.1": compares numbers, not text. */
    fun isNewer(candidate: String, current: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").split('.', '-').map { it.toIntOrNull() ?: 0 }
        val a = parts(candidate); val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Called after each library scan: checks at most every 20 hours. */
    fun maybeCheck(app: Ryzer) {
        val cfg = app.cfg
        if (!cfg.autoUpdate || cfg.kidMode) return
        if (System.currentTimeMillis() - cfg.lastUpdateCheck < 20 * 3_600_000L) {
            // A download from earlier may already be waiting
            release?.let { if (state == State.READY) offer(app, it) }
            return
        }
        if (!app.wifiConnected()) return
        check(app, manual = false)
    }

    /** `force` treats the latest release as new even if it isn't (used by the automated install test). */
    fun check(app: Ryzer, manual: Boolean, force: Boolean = false) {
        if (state == State.CHECKING || state == State.DOWNLOADING || state == State.INSTALLING) {
            if (manual) app.push(UpdateScreen(app)); return
        }
        state = State.CHECKING; message = "Checking for updates..."
        if (manual) app.push(UpdateScreen(app))
        worker.execute {
            val found = runCatching { fetchLatest() }.onFailure { Crash.log(app.ctx, RuntimeException("Update check failed", it)) }.getOrNull()
            main.post {
                app.cfg.lastUpdateCheck = System.currentTimeMillis(); app.cfg.save()
                val current = BuildInfo.version
                when {
                    found == null -> { state = State.FAILED; message = "Couldn't reach GitHub. Check your internet connection." }
                    !force && !isNewer(found.version, current) -> {
                        state = State.IDLE; release = null; message = "You have the newest version ($current)."
                        if (manual) app.notify(message)
                    }
                    !manual && found.version == app.cfg.skipVersion -> { state = State.IDLE; release = found }
                    else -> {
                        release = found
                        state = State.IDLE; message = "Ryzer OS ${found.version} is available."
                        if (app.cfg.ryzerNotices && !manual) Notifs.ryzer("Update available: Ryzer OS ${found.version}", "Open Settings > Updates to see what's new.")
                        if (manual || (app.cfg.autoDownloadUpdate && app.wifiConnected())) download(app, found, manual)
                    }
                }
                app.ctx.redraw()
            }
        }
    }

    fun download(app: Ryzer, r: Release, manual: Boolean) {
        val out = apkFile(app)
        if (state == State.READY && out.isFile && out.length() == r.size) { if (!manual) offer(app, r); return }
        state = State.DOWNLOADING; progress = 0f; message = "Downloading Ryzer OS ${r.version}..."
        worker.execute {
            val ok = runCatching { fetchApk(r, out) }.onFailure { Crash.log(app.ctx, RuntimeException("Update download failed", it)) }.getOrDefault(false)
            main.post {
                if (ok) {
                    state = State.READY; message = "Ryzer OS ${r.version} is ready to install."
                    if (!manual) offer(app, r)
                } else {
                    state = State.FAILED; message = "The download didn't finish. Try again on a steady connection."
                    out.delete()
                }
                app.ctx.redraw()
            }
        }
    }

    /** Shows the update screen, but never in the middle of something (a game, a menu you're in). */
    private fun offer(app: Ryzer, r: Release) {
        if (app.current is UpdateScreen || app.cfg.skipVersion == r.version) return
        if (app.stack.size == 1) app.push(UpdateScreen(app))
        else app.notify("Ryzer OS ${r.version} is ready: Settings > Updates", 4f)
    }

    /** Hands the downloaded APK to Android's installer. */
    fun install(app: Ryzer) {
        val ctx = app.ctx
        val file = apkFile(app)
        if (state != State.READY || !file.isFile) { app.notify("Download the update first"); return }
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            app.push(Message(app, "One-time permission", "Android asks you to allow Ryzer OS to install its own updates.\n\n" +
                "On the next screen, switch on 'Allow from this source', then come back and press Install again.", onClose = {
                runCatching { ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))) }
            }))
            return
        }
        state = State.INSTALLING; message = "Installing..."
        worker.execute {
            val error = runCatching {
                val installer = ctx.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                params.setAppPackageName(ctx.packageName)
                // Android 12+: once Ryzer has installed an update itself, later ones need no extra tap
                if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                val id = installer.createSession(params)
                installer.openSession(id).use { session ->
                    session.openWrite("RyzerOS.apk", 0, file.length()).use { out ->
                        file.inputStream().use { it.copyTo(out, 1 shl 16) }
                        session.fsync(out)
                    }
                    val status = Intent(ctx, MainActivity::class.java).setAction(ACTION_STATUS)
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                    session.commit(PendingIntent.getActivity(ctx, 7, status, flags).intentSender)
                }
            }.exceptionOrNull()
            if (error != null) main.post {
                Crash.log(ctx, RuntimeException("Update install failed", error))
                state = State.READY; message = "Android couldn't start the install: ${error.message?.take(80)}"
                app.ctx.redraw()
            }
        }
    }

    /** Android reports back here (MainActivity.onNewIntent) while and after installing. */
    fun onStatus(app: Ryzer, intent: Intent) {
        when (val s = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    else intent.getParcelableExtra(Intent.EXTRA_INTENT)
                confirm?.let { runCatching { app.ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            }
            PackageInstaller.STATUS_SUCCESS -> { message = "Updated! Ryzer will restart."; state = State.IDLE }
            else -> {
                state = State.READY
                val why = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
                message = when (s) {
                    PackageInstaller.STATUS_FAILURE_ABORTED -> "Install cancelled. Press Install to try again."
                    PackageInstaller.STATUS_FAILURE_CONFLICT -> "This update is signed differently from the installed Ryzer, so Android refused it."
                    PackageInstaller.STATUS_FAILURE_STORAGE -> "Not enough free space to install the update."
                    else -> "The install failed. $why".trim()
                }
                app.notify(message, 4f)
            }
        }
        app.ctx.redraw()
    }

    // ----- network -----

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000; readTimeout = 30_000
        setRequestProperty("User-Agent", "RyzerOS/${BuildInfo.version} (updater)")
    }

    private fun fetchLatest(): Release? {
        // The GitHub API gives the notes and the file size
        runCatching {
            val c = open(API).apply { setRequestProperty("Accept", "application/vnd.github+json") }
            try {
                if (c.responseCode == 200) {
                    val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                    val assets = j.getJSONArray("assets")
                    for (i in 0 until assets.length()) {
                        val a = assets.getJSONObject(i)
                        if (a.optString("name") == "RyzerOS.apk") {
                            return Release(j.getString("tag_name").removePrefix("v"), cleanNotes(j.optString("body")),
                                a.getString("browser_download_url"), a.optLong("size", -1))
                        }
                    }
                }
            } finally { c.disconnect() }
        }
        // Fallback (API rate limit): the "latest" page redirects to the newest tag
        val c = open(LATEST).apply { instanceFollowRedirects = false }
        try {
            val tag = c.getHeaderField("Location")?.substringAfterLast("/tag/") ?: return null
            return Release(tag.removePrefix("v"), "", "https://github.com/NyteRyzer/RyzerOS/releases/download/$tag/RyzerOS.apk", -1)
        } finally { c.disconnect() }
    }

    private fun fetchApk(r: Release, out: File): Boolean {
        out.parentFile?.mkdirs()
        val tmp = File(out.path + ".part")
        val c = open(r.apkUrl)
        try {
            if (c.responseCode != 200) return false
            val total = if (r.size > 0) r.size else c.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
            var got = 0L
            c.inputStream.use { input ->
                tmp.outputStream().use { o ->
                    val buf = ByteArray(1 shl 16)
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        o.write(buf, 0, n); got += n
                        if (total > 0) progress = got.toFloat() / total
                    }
                }
            }
            if (total > 0 && got != total) return false
            return tmp.renameTo(out)
        } finally { c.disconnect() }
    }

    /** Release notes are Markdown; this keeps the readable text. */
    fun cleanNotes(md: String): String = md.lines().mapNotNull { raw ->
        val l = raw.trim()
        when {
            l.startsWith("![") || l.startsWith("|") || l.startsWith("---") || l.startsWith("> ") || l.startsWith("[Full changelog") -> null
            else -> l.replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1").replace("**", "").replace("`", "").replace(Regex("^#+\\s*"), "")
                .replace(Regex("^[-*]\\s+"), "• ").replace("*", "")
        }
    }.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()
        .let { s -> s.substringAfter("What's new", s).trim() }
}

/** Update details: what's new, download progress, install. */
class UpdateScreen(app: Ryzer) : Screen(app) {
    override val title = "Updates"
    private var scroll = 0

    override fun handle(a: String) {
        val u = Updater
        val r = u.release
        when (a) {
            "a" -> when {
                u.state == Updater.State.READY -> u.install(app)
                r != null && u.state != Updater.State.DOWNLOADING && u.state != Updater.State.INSTALLING -> u.download(app, r, manual = true)
                u.state == Updater.State.IDLE || u.state == Updater.State.FAILED -> u.check(app, manual = false)
            }
            "x" -> r?.let { app.cfg.skipVersion = it.version; app.cfg.save(); app.notify("Won't remind you about ${it.version}"); app.pop() }
            "up" -> scroll = maxOf(0, scroll - 1)
            "down" -> scroll++
            "b" -> app.pop()
            else -> return
        }
        app.sounds.play("move")
    }

    override fun draw(c: Canvas, w: Float, h: Float) {
        val u = Updater
        val r = u.release
        G.topBar(c, app, th, "Updates", w)
        G.text(c, "You have Ryzer OS ${BuildInfo.version}", 20f, 52f, 22f, th.c("text_dim"))
        if (r != null) G.text(c, "New: Ryzer OS ${r.version}", w - 20, 50f, 26f, th.c("good"), align = 'r', isBold = true)
        G.text(c, u.message, 20f, 84f, 22f, th.c("text"), isBold = true, maxW = w - 40)
        if (u.state == Updater.State.DOWNLOADING) {
            val bar = RectF(20f, 118f, w - 20, 132f)
            G.rect(c, bar, th.c("panel"), 7f)
            G.rect(c, RectF(bar.left, bar.top, bar.left + bar.width() * u.progress, bar.bottom), th.c("accent"), 7f)
            G.text(c, "${(u.progress * 100).toInt()}%", w - 20, 136f, 18f, th.c("text_dim"), align = 'r')
        }
        val notes = r?.notes?.takeIf { it.isNotBlank() } ?: return
        val box = RectF(16f, 160f, w - 16, h - 50)
        G.panel(c, box, th)
        G.text(c, "What's new", box.left + 14, box.top + 8, 22f, th.c("accent"), isBold = true)
        val lines = notes.lines().flatMap { if (it.isBlank()) listOf("") else G.wrap(it, 19f, box.width() - 28) }
        val rows = ((box.height() - 44) / 23).toInt()
        scroll = scroll.coerceIn(0, maxOf(0, lines.size - rows))
        lines.drop(scroll).take(rows).forEachIndexed { i, l -> G.text(c, l, box.left + 14, box.top + 38 + i * 23, 19f, th.c("text")) }
    }

    override fun hints(): List<Pair<String, String>> {
        val u = Updater
        val action = when {
            u.state == Updater.State.READY -> "Install"
            u.state == Updater.State.DOWNLOADING || u.state == Updater.State.INSTALLING || u.state == Updater.State.CHECKING -> ""
            u.release != null -> "Download"
            else -> "Check again"
        }
        return listOfNotNull("B" to "Later", if (u.release != null) "X" to "Skip version" else null, if (action.isNotEmpty()) "A" to action else null)
    }
}

/** Settings > Updates. */
fun updatesMenu(app: Ryzer): Menu {
    val cfg = app.cfg
    return Menu(app, "Updates", listOf(
        Item("Check for updates now", value = { if (Updater.release != null) "${Updater.release!!.version} available" else "" },
            select = { Updater.check(app, manual = true) }),
        Item("Check automatically", "Once a day on Wi-Fi, after Ryzer scans your games.", value = { if (cfg.autoUpdate) "On" else "Off" },
            change = { cfg.autoUpdate = !cfg.autoUpdate; cfg.save() }),
        Item("Download updates automatically", "Downloads new versions on Wi-Fi in the background, then asks before installing.",
            value = { if (cfg.autoDownloadUpdate) "On" else "Off" }, change = { cfg.autoDownloadUpdate = !cfg.autoDownloadUpdate; cfg.save() }),
        Item("Skipped version", value = { cfg.skipVersion.ifEmpty { "None" } }, select = { cfg.skipVersion = ""; cfg.save(); app.notify("Updates will be offered again") }),
        Item("What's new in this version", select = { app.push(Message(app, "What's new in Ryzer OS ${BuildInfo.version}", WHATS_NEW)) }),
    ))
}
