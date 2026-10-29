package com.ryzer.os

/**
 * Opens screens by name, so the screenshot workflow (tools/screenshots) can show each part of
 * Ryzer without tapping through menus. Steps are separated by commas: "notices,notifications".
 */
object Showcase {
    fun open(app: Ryzer, spec: String) {
        for (step in spec.split(',').map { it.trim() }.filter { it.isNotEmpty() }) {
            val name = step.substringBefore(':')
            val arg = step.substringAfter(':', "")
            val sys = app.systems.firstOrNull { it.id == arg }
            when (name) {
                "home" -> app.goHome()
                "games" -> sys?.let { app.push(GameList(app, it.name, it.games, it)) }
                "foryou" -> app.push(GameList(app, "For You", null, source = "foryou"))
                "themes" -> app.push(ThemePicker(app))
                "studio" -> app.push(ThemeStudio(app, app.theme))
                "stats" -> app.push(StatsScreen(app))
                "notifications" -> app.push(NotificationsScreen(app))
                "tools" -> app.push(toolsMenu(app))
                "settings" -> app.push(settingsMenu(app))
                "quick" -> app.push(QuickMenu(app))
                "calendar" -> app.push(PlayCalendar(app))
                "snake" -> app.push(SnakeGame(app))
                "2048" -> app.push(Game2048(app))
                "roulette" -> app.push(Roulette(app, app.allGames()))
                "battery" -> app.push(BatteryInfo(app))
                "profile" -> app.push(profileMenu(app))
                "whatsnew" -> app.push(Message(app, "What's new in Ryzer OS ${BuildInfo.version}", WHATS_NEW))
                "notices" -> {
                    Notifs.ryzer("Library updated", "Found 4 new games.", banner = false)
                    Notifs.ryzer("Theme unlocked: Gold Trophy", "Find it in Themes. Earned with your milestones.", banner = false)
                    Notifs.ryzer("Timer done", "Your 30 minute timer finished.", banner = false)
                    Notifs.ryzer("Milestone unlocked: On a Roll", "Play 7 days in a row", banner = false)
                }
                "clearsystem" -> {   // the emulator's own notifications ("Configure keyboard"...)
                    Notifs.list.filter { !it.fromRyzer }.forEach { Notifs.dismiss(it) }
                }
                "playtest" -> {   // a stand-in "emulator" (any app) on one screen, Now Playing on the other
                    val game = app.allGames().firstOrNull { it.system.id !in DualPlay.DUAL_SYSTEMS } ?: return
                    val (gameD, panelD) = DualPlay.displays(app, game) ?: return
                    app.ctx.releaseSecondScreen()
                    DualPlay.begin(game, arg, gameD, panelD)
                    DualPlay.open(app, game, panelD)
                    app.ctx.packageManager.getLaunchIntentForPackage(arg)?.let {
                        app.ctx.startActivity(it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK), DualPlay.optionsFor(gameD))
                    }
                }
                "updatetest" -> Updater.check(app, manual = true, force = true)
                "wrapped" -> app.push(WrappedScreen(app))
                "checkup" -> app.push(Message(app, "Library check-up", libraryCheckup(app)))
                "cover" -> sys?.games?.firstOrNull()?.let { app.push(CoverViewer(app, it)) }
                "banner" -> Notifs.ryzer("Milestone unlocked: Marathon", "Play 3 hours in one day")
            }
        }
    }
}
