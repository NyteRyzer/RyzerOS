package com.ryzer.os

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * Turns any picture into a theme: the photo becomes the background (dimmed so text stays
 * readable), and the UI colours are taken from the photo itself.
 */
object PhotoTheme {
    private fun lum(c: Int) = (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) / 255

    private fun sat(c: Int): Float { val hsv = FloatArray(3); Color.colorToHSV(c, hsv); return hsv[1] }

    /** Makes a colour bright and saturated enough to work as an accent on a dark UI. */
    private fun vivid(c: Int, minV: Float = 0.85f, minS: Float = 0.55f): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(c, hsv)
        hsv[1] = hsv[1].coerceAtLeast(minS); hsv[2] = hsv[2].coerceAtLeast(minV)
        return Color.HSVToColor(hsv)
    }

    private fun shade(c: Int, v: Float, s: Float = 0.5f): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(c, hsv)
        hsv[1] = minOf(hsv[1], s); hsv[2] = v
        return Color.HSVToColor(hsv)
    }

    /** Copies the picture into RyzerOS/wallpapers (shrunk to screen size) and returns its file name. */
    fun importPicture(app: Ryzer, uri: Uri): String {
        val resolver = app.ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 1280 || bounds.outHeight / (sample * 2) >= 1280) sample *= 2
        val bmp = resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: error("That file isn't a picture Ryzer can read")
        val name = "photo-${System.currentTimeMillis()}.jpg"
        FileOutputStream(File(app.wallpaperDir(), name)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        bmp.recycle()
        return name
    }

    /** Builds and saves a theme from a picture already in the wallpapers folder. */
    fun create(app: Ryzer, picture: String): Theme {
        val bmp = BitmapFactory.decodeFile(File(app.wallpaperDir(), picture).path) ?: error("Couldn't read the picture")
        val sw = Palette.swatches(bmp)
        bmp.recycle()
        val common = sw.firstOrNull()?.color ?: 0xFF303040.toInt()
        val vividOnes = sw.filter { it.sat > 0.3f && it.lum > 0.3f }.sortedByDescending { it.count * (0.3f + it.sat) }
        val accent = vivid(vividOnes.getOrNull(0)?.color ?: common)
        val accent2 = vivid(vividOnes.firstOrNull { Math.abs(hue(it.color) - hue(accent)) > 40 }?.color ?: shift(accent, 150f), 0.8f, 0.45f)
        val colors = mutableMapOf(
            "bg_top" to shade(common, 0.22f), "bg_bottom" to shade(common, 0.08f),
            "panel" to shade(common, 0.16f, 0.45f), "panel_border" to shade(accent, 0.45f, 0.6f),
            "accent" to accent, "accent2" to accent2,
            "text" to 0xFFF6F4F2.toInt(), "text_dim" to mix(0xFFF6F4F2.toInt(), shade(common, 0.6f), 0.45f),
            "highlight_text" to if (lum(accent) > 0.6) 0xFF101010.toInt() else 0xFFFFFFFF.toInt(),
            "good" to 0xFF3DDC84.toInt(), "warn" to 0xFFFFC23D.toInt(), "bad" to 0xFFFF4D5E.toInt(),
        )
        val (id, _) = nextPhotoId(app.themes)
        val name = "Photo " + java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault()).format(java.util.Date())
        val style = mutableMapOf<String, Any>(
            "corner_radius" to 14, "tile_style" to "glow", "background" to "gradient", "font_scale" to 1.0f,
            "particles" to true, "panel_alpha" to 0.72f, "wallpaper" to picture, "wallpaper_dim" to 0.42f,
        )
        val t = Theme(id, name, colors, style, builtin = false, author = "You")
        Themes.save(app, t)
        return t
    }

    private fun hue(c: Int): Float { val hsv = FloatArray(3); Color.colorToHSV(c, hsv); return hsv[0] }

    private fun shift(c: Int, by: Float): Int { val hsv = FloatArray(3); Color.colorToHSV(c, hsv); hsv[0] = (hsv[0] + by) % 360f; return Color.HSVToColor(hsv) }

    private fun nextPhotoId(themes: Map<String, Theme>): Pair<String, String> {
        var n = 1
        while ("photo-$n" in themes) n++
        return "photo-$n" to "Photo $n"
    }

    /** Full flow: pick from the gallery, build the theme in the background, apply it. */
    fun pickAndApply(app: Ryzer) {
        app.ctx.pickPhoto { uri ->
            app.notify("Building your theme...", 6f)
            var theme: Theme? = null
            app.runInBackground({
                val pic = importPicture(app, uri)
                theme = create(app, pic)
            }) { error ->
                val t = theme
                if (error != null || t == null) {
                    Crash.log(app.ctx, error ?: IllegalStateException("no theme"))
                    app.notify("Couldn't make a theme from that picture", 4f)
                } else {
                    app.themes = Themes.loadAll(app)
                    app.setTheme(t.id, save = true)
                    app.focusTheme = t.id
                    app.safely { app.current?.onResume() }   // lets an open theme picker jump to the new theme
                    app.sounds.play("fav")
                    app.notify("New theme: ${t.name}", 3f)
                    Milestones.check(app)
                }
            }
        }
    }
}

/** Background music for the menus, from RyzerOS/music. Pauses while a game runs. */
class MenuMusic(private val app: Ryzer) {
    private var player: MediaPlayer? = null
    private var queue = ArrayDeque<File>()
    var nowPlaying: String = ""
        private set

    fun dir() = File(app.home, "music").apply { mkdirs() }

    private fun tracks() = dir().listFiles { f -> f.extension.lowercase() in setOf("mp3", "ogg", "m4a", "aac", "flac", "wav", "opus") }
        ?.toList()?.shuffled() ?: emptyList()

    fun resume() {
        if (!app.cfg.music) { stop(); return }
        player?.let { runCatching { it.setVolume(vol(), vol()); it.start() }; return }
        next()
    }

    fun pause() { runCatching { player?.pause() } }

    fun stop() {
        runCatching { player?.release() }
        player = null
        nowPlaying = ""
    }

    fun setVolume() { runCatching { player?.setVolume(vol(), vol()) } }

    private fun vol() = app.cfg.musicVolume / 100f

    fun next() {
        stop()
        if (!app.cfg.music) return
        if (queue.isEmpty()) queue = ArrayDeque(tracks())
        val f = queue.removeFirstOrNull() ?: return
        runCatching {
            player = MediaPlayer().apply {
                setDataSource(f.path)
                setVolume(vol(), vol())
                setOnCompletionListener { next() }
                prepare()
                start()
            }
            nowPlaying = f.nameWithoutExtension
        }.onFailure { Crash.log(app.ctx, it); player = null }
    }

    fun trackCount() = tracks().size
}
