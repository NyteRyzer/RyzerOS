package com.ryzer.os

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Crash reporting with no network access: errors are written to crash.txt (in the
 * RyzerOS folder when Ryzer can write there, otherwise in the app's own folder) and
 * shown on the next start so they can be sent to the developer.
 */
object Crash {
    private const val PENDING = "crash-pending.txt"

    fun file(ctx: Context): File {
        val shared = File(Environment.getExternalStorageDirectory(), "RyzerOS")
        return if (hasStorageAccess(ctx) && (shared.isDirectory || shared.mkdirs())) File(shared, "crash.txt")
        else File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "crash.txt")
    }

    fun describe(e: Throwable): String {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        return sw.toString().lines().take(25).joinToString("\n")
    }

    private fun header() = "Ryzer OS ${BuildInfo.version} | ${Build.MANUFACTURER} ${Build.MODEL} | Android ${Build.VERSION.RELEASE} " +
        "(API ${Build.VERSION.SDK_INT}) | ${java.util.Date()}"

    /** Records a handled error (the app keeps running). */
    fun log(ctx: Context, e: Throwable) {
        runCatching { file(ctx).appendText("${header()}\n${describe(e)}\n\n") }
    }

    /** Catches anything that would close the app, saves it, then lets Android handle it as usual. */
    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val report = "${header()}\n${describe(e)}"
                File(app.filesDir, PENDING).writeText(report)
                file(app).appendText("$report\n\n")
            }
            previous?.uncaughtException(t, e)
        }
    }

    /** The report from a crash on the previous run, if there was one (shown once). */
    fun pending(ctx: Context): String? {
        val f = File(ctx.filesDir, PENDING)
        if (!f.isFile) return null
        val text = runCatching { f.readText() }.getOrNull()
        f.delete()
        return text
    }
}
