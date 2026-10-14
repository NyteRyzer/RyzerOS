package com.ryzer.os

import android.app.ActivityManager
import android.content.ComponentName
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File

/** Turns a game + emulator definition into an Android intent. */
object Launcher {
    const val ANY_APP_PREFIX = "pkg:"
    const val CHOOSER = "chooser"
    private val HEAVY = setOf("switch", "3ds", "wiiu", "wii", "gc", "ps2", "psvita")

    fun emulator(app: Ryzer, id: String): Emulator? {
        if (id.startsWith("ra:")) return app.emulators["retroarch"]?.withCore(id.removePrefix("ra:"))
        return app.emulators[id]
    }

    fun isInstalled(ctx: Context, pkg: String) = try {
        ctx.packageManager.getPackageInfo(pkg, 0); true
    } catch (e: PackageManager.NameNotFoundException) { false }

    /** First "package/activity" of the emulator that is installed, as (package, class). */
    fun installedComponent(ctx: Context, emu: Emulator): Pair<String, String>? {
        for (c in emu.components) {
            val pkg = c.substringBefore("/")
            var cls = c.substringAfter("/")
            if (cls.startsWith(".")) cls = pkg + cls
            if (isInstalled(ctx, pkg)) return pkg to cls
        }
        return null
    }

    fun appLabel(ctx: Context, pkg: String): String = runCatching {
        ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    /** Emulator choices for a system: the known list, marked installed or not. */
    fun choices(app: Ryzer, sys: GameSystem): List<Pair<String, Boolean>> =
        sys.emulators.mapNotNull { id -> emulator(app, id)?.let { id to (installedComponent(app.ctx, it) != null) } }

    /** The emulator Ryzer will use: the user's pick, else the first installed one in the list. */
    fun chosen(app: Ryzer, sys: GameSystem): String? {
        app.cfg.systemEmu[sys.id]?.let { return it }
        return choices(app, sys).firstOrNull { it.second }?.first
    }

    fun label(app: Ryzer, id: String?): String = when {
        id == null -> "None installed"
        id == CHOOSER -> "Ask every time"
        id.startsWith(ANY_APP_PREFIX) -> appLabel(app.ctx, id.removePrefix(ANY_APP_PREFIX))
        else -> emulator(app, id)?.name ?: id
    }

    // ----- URIs --------------------------------------------------------------

    /** Storage-framework document URI, the form most modern emulators accept (ES-DE's %ROMSAF%). */
    fun safUri(f: File): Uri {
        val path = f.absolutePath
        val primary = Environment.getExternalStorageDirectory().absolutePath
        val (vol, rel) = if (path.startsWith("$primary/")) "primary" to path.removePrefix("$primary/")
        else {
            val parts = path.removePrefix("/storage/").split("/", limit = 2)
            parts[0] to parts.getOrElse(1) { "" }
        }
        return Uri.parse("content://com.android.externalstorage.documents/tree/" + Uri.encode("$vol:") +
            "/document/" + Uri.encode("$vol:$rel"))
    }

    fun providerUri(f: File): Uri = Uri.Builder().scheme("content").authority(RomProvider.AUTHORITY).path(f.absolutePath).build()

    private fun fill(template: String, game: Game, pkg: String, core: String?): String = template
        .replace("{path}", game.file.absolutePath)
        .replace("{saf}", safUri(game.file).toString())
        .replace("{provider}", providerUri(game.file).toString())
        .replace("{package}", pkg)
        .replace("{external}", Environment.getExternalStorageDirectory().absolutePath)
        .replace("{core}", core ?: "")

    fun buildIntent(app: Ryzer, emu: Emulator, game: Game): Intent? {
        val (pkg, cls) = installedComponent(app.ctx, emu) ?: return null
        val i = Intent(emu.action ?: if (emu.data != null) Intent.ACTION_VIEW else Intent.ACTION_MAIN)
        i.component = ComponentName(pkg, cls)
        emu.category?.let { i.addCategory(it) }
        emu.data?.let { i.data = Uri.parse(fill(it, game, pkg, emu.core)) }
        for ((k, v) in emu.extras) {
            val value = fill(v, game, pkg, emu.core)
            if (k.startsWith("bool:")) i.putExtra(k.removePrefix("bool:"), value.toBoolean()) else i.putExtra(k, value)
        }
        var flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        if ("clear_task" in emu.flags) flags = flags or Intent.FLAG_ACTIVITY_CLEAR_TASK
        if ("clear_top" in emu.flags) flags = flags or Intent.FLAG_ACTIVITY_CLEAR_TOP
        i.addFlags(flags)
        if (emu.data?.contains("{provider}") == true || emu.extras.values.any { "{provider}" in it }) {
            app.ctx.grantUriPermission(pkg, providerUri(game.file),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        return i
    }

    /** Open the ROM with any app ("Open with..."), for emulators Ryzer doesn't know. */
    fun anyAppIntent(game: Game, pkg: String?): Intent {
        val uri = providerUri(game.file)
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, RomProvider.mime(game.file))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        if (pkg != null) i.setPackage(pkg)
        return if (pkg == null) Intent.createChooser(i, "Open ${game.name} with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) else i
    }

    fun isHeavy(sys: GameSystem) = sys.id in HEAVY

    /**
     * Closes cached background apps so a heavy emulator gets the RAM. Only affects apps
     * Android already considers expendable; it can't touch the foreground or system services.
     */
    fun boost(ctx: Context, keep: String) {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val pm = ctx.packageManager
        for (ai in pm.getInstalledApplications(0)) {
            if (ai.flags and ApplicationInfo.FLAG_SYSTEM != 0) continue
            if (ai.packageName == ctx.packageName || ai.packageName == keep) continue
            runCatching { am.killBackgroundProcesses(ai.packageName) }
        }
    }
}

/**
 * Hands single ROM files to emulators that take a content:// link (ES-DE's %ROMPROVIDER%).
 * Not exported: an app can only open a file Ryzer explicitly granted it when launching.
 */
class RomProvider : ContentProvider() {
    companion object {
        const val AUTHORITY = "com.ryzer.os.roms"
        fun mime(f: File): String =
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "application/octet-stream"
    }

    private fun fileFor(uri: Uri): File {
        val f = File(uri.path ?: throw SecurityException("no path")).canonicalFile
        if (!f.path.startsWith("/storage/") && !f.path.startsWith("/sdcard/") && !f.path.startsWith("/mnt/")) {
            throw SecurityException("outside shared storage")
        }
        return f
    }

    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
        ParcelFileDescriptor.open(fileFor(uri), ParcelFileDescriptor.parseMode(if ("w" in mode) "rw" else "r"))

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor {
        val f = fileFor(uri)
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val row = cols.map { if (it == OpenableColumns.SIZE) f.length() else if (it == OpenableColumns.DISPLAY_NAME) f.name else null }
        return MatrixCursor(cols, 1).apply { addRow(row) }
    }

    override fun getType(uri: Uri) = mime(fileFor(uri))
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
}
