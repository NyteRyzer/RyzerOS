package com.ryzer.os

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Notification centre. Reads the device's notifications through Android's notification
 * listener (the user switches it on once in Android settings), and also holds Ryzer's own
 * notices (milestones, battery, scans...).
 */
object Notifs {
    class Note(
        val key: String, val pkg: String, val app: String, val title: String, val text: String,
        val time: Long, val intent: PendingIntent?, val autoCancel: Boolean, val fromRyzer: Boolean,
    ) {
        var read = false
    }

    val list = ArrayList<Note>()
    var connected = false
    /** The newest note, for the banner. */
    var banner: Note? = null
    var bannerAt = 0L
    private val main = Handler(Looper.getMainLooper())
    private val icons = HashMap<String, Bitmap?>()
    /** Keeps Ryzer's own notices apart when several are posted in the same millisecond. */
    private var counter = 0

    fun unread() = list.count { !it.read }

    fun granted(ctx: Context): Boolean {
        val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
        return flat.split(":").any { ComponentName.unflattenFromString(it)?.packageName == ctx.packageName }
    }

    fun openAccessSettings(ctx: Context) {
        runCatching { ctx.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** Ryzer's own notices go in the same centre. */
    fun ryzer(title: String, text: String, banner: Boolean = true) = main.post {
        val n = Note("ryzer:${SystemClock.uptimeMillis()}:${++counter}", "com.ryzer.os", "Ryzer OS", title, text,
            System.currentTimeMillis(), null, true, true)
        add(n, banner)
    }

    private fun add(n: Note, showBanner: Boolean) {
        list.removeAll { it.key == n.key }
        list.add(0, n)
        while (list.size > 100) list.removeAt(list.size - 1)
        if (showBanner) { banner = n; bannerAt = SystemClock.uptimeMillis() }
        Ryzer.instance?.let { app ->
            if (showBanner && app.cfg.notifBanners && !app.cfg.notifDnd) app.sounds.play("fav")
            app.ctx.redraw()
        }
        NowPlayingActivity.current?.window?.decorView?.postInvalidate()
    }

    fun fromSystem(ctx: Context, sbn: StatusBarNotification, showBanner: Boolean) {
        val n = sbn.notification ?: return
        if (sbn.packageName == ctx.packageName) return
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return          // music players, downloads...
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = n.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras?.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras?.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        val label = runCatching { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(sbn.packageName, 0)).toString() }
            .getOrDefault(sbn.packageName)
        val note = Note(sbn.key, sbn.packageName, label, title, text, sbn.postTime, n.contentIntent,
            n.flags and Notification.FLAG_AUTO_CANCEL != 0, false)
        main.post { add(note, showBanner) }
    }

    fun removed(key: String) = main.post {
        list.removeAll { it.key == key }
        Ryzer.instance?.ctx?.redraw()
    }

    fun open(ctx: Context, n: Note) {
        n.read = true
        runCatching { n.intent?.send() }.onFailure {
            runCatching { ctx.packageManager.getLaunchIntentForPackage(n.pkg)?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        }
        if (n.autoCancel) dismiss(n)
    }

    fun dismiss(n: Note) {
        list.remove(n)
        if (!n.fromRyzer) runCatching { RyzerNotificationService.instance?.cancelNotification(n.key) }
    }

    fun clearAll() {
        list.filter { !it.fromRyzer }.forEach { n -> runCatching { RyzerNotificationService.instance?.cancelNotification(n.key) } }
        list.clear()
    }

    fun icon(ctx: Context, pkg: String): Bitmap? = icons.getOrPut(pkg) {
        runCatching {
            val d = if (pkg == ctx.packageName) ctx.applicationInfo.loadIcon(ctx.packageManager) else ctx.packageManager.getApplicationIcon(pkg)
            val b = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, 64, 64); d.draw(Canvas(b)); b
        }.getOrNull()
    }

    fun ago(t: Long): String {
        val m = (System.currentTimeMillis() - t) / 60000
        return when { m < 1 -> "now"; m < 60 -> "${m}m"; m < 1440 -> "${m / 60}h"; else -> "${m / 1440}d" }
    }

    /** A slide-down banner at the top of the screen for a few seconds after a new notification. */
    fun drawBanner(c: Canvas, app: Ryzer, th: Theme, w: Float) {
        val n = banner ?: return
        if (!app.cfg.notifBanners || app.cfg.notifDnd) return
        val age = SystemClock.uptimeMillis() - bannerAt
        if (age > 4500) return
        val slide = when { age < 250 -> age / 250f; age > 4200 -> (4500 - age) / 300f; else -> 1f }
        val top = -60 + 66 * slide
        val r = RectF(w * 0.12f, top, w * 0.88f, top + 56)
        G.rect(c, r, withAlpha(th.c("panel"), 0.96f), 14f)
        G.outline(c, r, th.c("accent"), 2f, 14f)
        icon(app.ctx, n.pkg)?.let { G.drawImageFit(c, it, RectF(r.left + 8, r.top + 8, r.left + 48, r.top + 48)) }
        G.text(c, "${n.app}  ·  ${n.title}", r.left + 56, r.top + 6, 20f, th.c("text"), isBold = true, maxW = r.width() - 66)
        G.text(c, n.text, r.left + 56, r.top + 30, 18f, th.c("text_dim"), maxW = r.width() - 66)
        app.hit(r) { app.push(NotificationsScreen(app)) }
    }
}

/** Android calls this with every notification once the user grants "Notification access". */
class RyzerNotificationService : NotificationListenerService() {
    companion object { var instance: RyzerNotificationService? = null }

    override fun onListenerConnected() {
        instance = this
        Notifs.connected = true
        runCatching { activeNotifications?.sortedBy { it.postTime }?.forEach { Notifs.fromSystem(this, it, showBanner = false) } }
    }

    override fun onListenerDisconnected() { Notifs.connected = false; instance = null }

    override fun onNotificationPosted(sbn: StatusBarNotification) { Notifs.fromSystem(this, sbn, showBanner = true) }

    override fun onNotificationRemoved(sbn: StatusBarNotification) { Notifs.removed(sbn.key) }
}

class NotificationsScreen(app: Ryzer) : Screen(app) {
    override val title = "Notifications"
    private var sel = 0
    private var offset = 0

    override fun onResume() { sel = sel.coerceIn(0, maxOf(0, Notifs.list.size - 1)) }

    override fun handle(a: String) {
        val list = Notifs.list
        when (a) {
            "up", "down" -> if (list.isNotEmpty()) { sel = (sel + (if (a == "down") 1 else -1) + list.size) % list.size; app.sounds.play("move") }
            "a" -> list.getOrNull(sel)?.let { Notifs.open(app.ctx, it); app.sounds.play("select") }
            "x" -> list.getOrNull(sel)?.let { Notifs.dismiss(it); sel = sel.coerceAtMost(maxOf(0, list.size - 1)); app.sounds.play("back") }
            "y" -> { Notifs.clearAll(); sel = 0; app.notify("All cleared") }
            "start" -> app.push(notificationSettings(app))
            "b" -> { app.sounds.play("back"); app.pop() }
        }
    }

    override fun draw(c: Canvas, w: Float, h: Float) {
        G.topBar(c, app, th, "Notifications (${Notifs.list.size})", w)
        Notifs.list.forEach { it.read = true }
        if (!Notifs.granted(app.ctx)) {
            val r = RectF(16f, 52f, w - 16, 112f)
            G.panel(c, r, th, fillColor = withAlpha(mix(th.c("panel"), th.c("warn"), 0.25f), 1f))
            G.text(c, "Turn on notification access to see your apps' notifications here.", r.left + 14, r.top + 8, 20f, th.c("text"), maxW = r.width() - 28)
            G.text(c, "Press START, then 'Allow notification access'.", r.left + 14, r.top + 34, 18f, th.c("text_dim"))
            app.hit(r) { Notifs.openAccessSettings(app.ctx) }
        }
        val list = Notifs.list
        val top = if (Notifs.granted(app.ctx)) 52f else 122f
        if (list.isEmpty()) { G.text(c, "You're all caught up.", w / 2, h / 2, 26f, th.c("text_dim"), align = 'c'); return }
        val rowH = 64f
        val visible = maxOf(1, ((h - top - 44) / rowH).toInt())
        offset = G.scrollOffset(sel, offset, visible, list.size)
        for (i in offset until minOf(list.size, offset + visible)) {
            val n = list[i]
            val r = RectF(16f, top + (i - offset) * rowH, w - 16, top + (i - offset) * rowH + rowH - 6)
            G.panel(c, r, th, i == sel)
            app.hit(r) { if (sel == i) handle("a") else sel = i }
            Notifs.icon(app.ctx, n.pkg)?.let { G.drawImageFit(c, it, RectF(r.left + 10, r.top + 9, r.left + 50, r.top + 49)) }
            G.text(c, n.app, r.left + 60, r.top + 5, 17f, th.c("accent"), isBold = true, maxW = r.width() * 0.5f)
            G.text(c, Notifs.ago(n.time), r.right - 12, r.top + 5, 17f, th.c("text_dim"), align = 'r')
            G.text(c, n.title, r.left + 60, r.top + 22, 20f, th.c("text"), isBold = true, maxW = r.width() - 80)
            G.text(c, n.text, r.left + 60, r.top + 40, 17f, th.c("text_dim"), maxW = r.width() - 80)
        }
    }

    override fun hints() = listOf("START" to "Settings", "Y" to "Clear all", "X" to "Dismiss", "A" to "Open")
}

fun notificationSettings(app: Ryzer): Menu {
    val cfg = app.cfg
    return Menu(app, "Notification settings", listOf(
        Item("Allow notification access", "Opens Android settings: switch on Ryzer OS so it can show your apps' notifications.",
            value = { if (Notifs.granted(app.ctx)) "On" else "Off" }, select = { Notifs.openAccessSettings(app.ctx) }),
        Item("Pop-up banners", "A banner slides down when a notification arrives.", value = { if (cfg.notifBanners) "On" else "Off" },
            change = { cfg.notifBanners = !cfg.notifBanners; cfg.save() }),
        Item("Do not disturb", "No banners or sounds; notifications still collect in the centre.", value = { if (cfg.notifDnd) "On" else "Off" },
            change = { cfg.notifDnd = !cfg.notifDnd; cfg.save() }),
        Item("Show on Now Playing screen", "The newest notification appears on the second screen while you play.",
            value = { if (cfg.notifOnPanel) "On" else "Off" }, change = { cfg.notifOnPanel = !cfg.notifOnPanel; cfg.save() }),
        Item("Ryzer notices", "Milestones, low battery and library scans in the notification centre.", value = { if (cfg.ryzerNotices) "On" else "Off" },
            change = { cfg.ryzerNotices = !cfg.ryzerNotices; cfg.save() }),
        Item("Send a test notification", select = { Notifs.ryzer("Test notification", "Notifications are working!") }),
    ))
}
