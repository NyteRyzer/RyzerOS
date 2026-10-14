"""Settings, system info and power menus."""

import platform
import subprocess

from ryzer import NAME, VERSION, paths, perf, tweaks, ui
from ryzer.config import format_duration
from ryzer.screens import Screen
from ryzer.screens.menus import Confirm, Item, Menu, Message


def cycle(options, current, d):
    i = options.index(current) if current in options else 0
    return options[(i + d) % len(options)]


def toggle_item(app, label, key, help="", on_change=None):
    def change(_d):
        app.cfg.set(key, not app.cfg[key])
        if on_change:
            on_change()
    return Item(label, value=lambda: "On" if app.cfg[key] else "Off", change=change, help=help)


def choice_item(app, label, key, options, fmt=str, help="", on_change=None):
    def change(d):
        app.cfg.set(key, cycle(options, app.cfg[key], d))
        if on_change:
            on_change()
    return Item(label, value=lambda: fmt(app.cfg[key]), change=change, help=help)


def settings_menu(app):
    items = [
        Item("Performance", select=lambda: app.push(performance_menu(app)),
             help="Speed profiles, emulator tuning and CPU status."),
        Item("Display", select=lambda: app.push(display_menu(app)), help="Layout, animations, clock and sleep."),
        Item("Sound", select=lambda: app.push(sound_menu(app))),
        Item("Library", select=lambda: app.push(library_menu(app)), help="Rescan ROMs and naming options."),
        Item("Controls", select=lambda: app.push(controls_menu(app))),
        Item("Themes", select=app.open_themes, help="Pick one of 20+ themes or build your own."),
        Item("System info", select=lambda: app.push(SystemInfo(app))),
        Item("About Ryzer OS", select=lambda: app.push(Message(app, "About", about_text(app)))),
    ]
    return Menu(app, "Settings", items)


def performance_menu(app):
    names = perf.PROFILES

    def sys_profile_menu():
        items = []
        for s in app.systems:
            def change(d, sid=s.id):
                app.cfg.data["system_profiles"][sid] = cycle(perf.PROFILE_ORDER, app.cfg.profile_for(sid), d)
                app.cfg.save()
            items.append(Item(s.name, value=lambda sid=s.id: names[app.cfg.profile_for(sid)], change=change,
                              help=f"{s.tier_label}. {s.note}"))
        app.push(Menu(app, "Per-system profiles", items))

    def apply_tweaks():
        report = tweaks.apply_all()
        app.push(Message(app, "Emulator tuning", "\n".join(report) +
                         "\n\nRun each emulator once first so it creates its config, then apply again."))

    def restore():
        n = tweaks.restore_all()
        app.notify(f"Restored {n} emulator config(s)")

    def status():
        f, t, g = perf.cpu_freq_mhz(), perf.cpu_temp(), perf.cpu_governor()
        app.push(Message(app, "CPU status",
                         f"Governor: {g or 'unknown'}\nFrequency: {f or '?'} MHz\n"
                         f"Temperature: {f'{t:.0f} C' if t else 'unknown'}\n"
                         f"perf script: {perf.perf_script() or 'not installed'}"))

    items = [
        choice_item(app, "Menu profile", "default_profile", perf.PROFILE_ORDER, fmt=lambda v: names[v],
                    help="Used while browsing and for systems without their own profile.",
                    on_change=lambda: perf.apply_profile(app.cfg["default_profile"])),
        Item("Per-system profiles", select=sys_profile_menu,
             help="3DS, GameCube and PS2 default to Turbo."),
        Item("Apply emulator speed tweaks", select=apply_tweaks,
             help="Tunes Azahar, Dolphin and AetherSX2 for speed (backs up first)."),
        Item("Restore emulator settings", select=lambda: app.push(
            Confirm(app, "Restore emulator configs from the Ryzer backups?", restore))),
        Item("CPU status", select=status),
    ]
    return Menu(app, "Performance", items)


def display_menu(app):
    def layout_changed():
        app.notify("Layout applies after restarting Ryzer")
    return Menu(app, "Display", [
        choice_item(app, "Screen layout", "screen_layout", ["auto", "single", "dual"], fmt=str.title,
                    help="Dual uses the top screen for art and info.", on_change=layout_changed),
        toggle_item(app, "Animations", "animations", help="Turn off for the snappiest menus."),
        toggle_item(app, "Boot animation", "boot_animation"),
        choice_item(app, "Menu frame rate", "fps", [30, 60], fmt=lambda v: f"{v} fps",
                    help="30 fps saves battery; the menu still feels smooth."),
        toggle_item(app, "24-hour clock", "clock_24h"),
        toggle_item(app, "Show battery", "show_battery"),
        choice_item(app, "Dim when idle", "sleep_minutes", [0, 1, 2, 5, 10],
                    fmt=lambda v: "Never" if v == 0 else f"{v} min"),
    ])


def sound_menu(app):
    def vol(d):
        app.cfg.set("volume", max(0, min(100, app.cfg["volume"] + d * 10)))
        app.sounds.set_volume(app.cfg["volume"])
        app.sounds.play("move")

    def snd_toggle():
        app.sounds.enabled = app.cfg["sound"]
    return Menu(app, "Sound", [
        toggle_item(app, "Menu sounds", "sound", on_change=snd_toggle),
        Item("Menu volume", value=lambda: f"{app.cfg['volume']}%", change=vol),
    ])


def library_menu(app):
    def rescan():
        app.rescan()
        app.notify(f"Found {sum(len(s.games) for s in app.systems)} games")
    return Menu(app, "Library", [
        Item("Rescan ROMs", select=rescan, help=f"ROM folder: {paths.roms_dir()}"),
        toggle_item(app, "Clean game names", "clean_names", help='Hides tags like "(USA) [!]".', on_change=app.rescan),
        toggle_item(app, "Hide empty systems", "hide_empty_systems"),
        Item("Clear recently played", select=lambda: app.push(
            Confirm(app, "Clear your recently played list?", lambda: clear_recent(app)))),
    ])


def clear_recent(app):
    app.state.data["recent"] = []
    app.state.save()
    app.notify("Recent list cleared")


def controls_menu(app):
    def swap(_d):
        app.cfg.data["input"]["swap_ab"] = not app.cfg.data["input"]["swap_ab"]
        app.cfg.save()
    return Menu(app, "Controls", [
        Item("Swap A / B", value=lambda: "On" if app.cfg.data["input"]["swap_ab"] else "Off", change=swap,
             help="Use the other face button to confirm."),
        Item("Button test", select=lambda: app.push(ButtonTest(app))),
        Item("Hotkeys", select=lambda: app.push(Message(app, "Hotkeys",
             "SELECT + START (hold): exit Ryzer\nSELECT + R: screenshot\nHome X: random game\n"
             "Home Y: search\nHome SELECT: themes\nList L/R: jump by letter\nList START: game options"))),
    ])


def about_text(app):
    games = sum(len(s.games) for s in app.systems)
    return (f"{NAME} {VERSION}\nA console-style frontend and performance layer for the Anbernic RG DS.\n\n"
            f"Games: {games}\nThemes: {len(app.themes)}\n"
            f"Total play time: {format_duration(app.state.total_playtime())}\n"
            f"Data folder: {paths.home()}\n\nEverything is editable: themes and systems.json are plain JSON, "
            f"and the UI is plain Python in the ryzer/ folder.")


class SystemInfo(Screen):
    title = "System info"

    def __init__(self, app):
        super().__init__(app)
        self.timer = 0
        self.lines = []
        self.refresh()

    def refresh(self):
        mem = perf.memory()
        dsk = perf.disk(paths.roms_dir() if paths.roms_dir().exists() else paths.home())
        bat = perf.battery()
        t = perf.cpu_temp()
        self.lines = [
            ("Device", platform.machine() + "  " + platform.release()),
            ("CPU", f"{perf.cpu_freq_mhz() or '?'} MHz  ({perf.cpu_governor() or 'unknown'})"),
            ("Temperature", f"{t:.0f} C" if t else "unknown"),
            ("Memory", f"{mem[0]} / {mem[1]} MB" if mem else "unknown"),
            ("Storage", f"{dsk[0]} / {dsk[1]} GB" if dsk else "unknown"),
            ("Battery", f"{bat[0]}%{' (charging)' if bat[1] else ''}" if bat else "no battery"),
            ("Wi-Fi", "Connected" if perf.wifi_connected() else "Off"),
            ("Layout", "Dual screen" if self.app.dual else "Single screen"),
        ]

    def update(self, dt):
        self.timer += dt
        if self.timer > 1:
            self.timer = 0
            self.refresh()

    def handle(self, action):
        if action in ("a", "b"):
            self.app.pop()

    def draw(self, surf):
        th = self.theme
        w, _ = surf.get_size()
        ui.top_bar(surf, th, self.title, self.app.cfg)
        for i, (k, v) in enumerate(self.lines):
            y = 60 + i * 44
            ui.panel(surf, (16, y, w - 32, 38), th)
            ui.text(surf, k, (30, y + 9), 22, th.c("text_dim"))
            ui.text(surf, v, (w - 30, y + 9), 22, th.c("text"), align="right", max_width=w - 200)

    def hints(self):
        return [("B", "Back")]


class ButtonTest(Screen):
    title = "Button test"
    BUTTONS = ("up", "down", "left", "right", "a", "b", "x", "y", "l", "r", "select", "start")

    def handle(self, action):
        pass

    def update(self, dt):
        if self.app.input.combo("select", "b"):
            self.app.pop()

    def draw(self, surf):
        th = self.theme
        w, _ = surf.get_size()
        ui.top_bar(surf, th, "Button test  (SELECT+B to exit)", self.app.cfg)
        for i, b in enumerate(self.BUTTONS):
            r, c = divmod(i, 4)
            rect = (20 + c * ((w - 40) // 4), 70 + r * 80, (w - 40) // 4 - 10, 64)
            ui.panel(surf, rect, th, selected=b in self.app.input.held)
            ui.text(surf, b.upper(), (rect[0] + rect[2] // 2, rect[1] + 20), 26, th.c("text"), align="center")

    def hints(self):
        return []


def power_menu(app):
    def run(cmd):
        app.quit_display()
        subprocess.run(cmd)

    return Menu(app, "Power", [
        Item("Exit Ryzer OS", select=lambda: app.push(Confirm(app, "Exit to the firmware's frontend?", app.quit)),
             help="Returns to the stock frontend (EmulationStation)."),
        Item("Reboot", select=lambda: app.push(Confirm(app, "Reboot now?", lambda: run(["reboot"])))),
        Item("Shut down", select=lambda: app.push(Confirm(app, "Shut down now?", lambda: run(["poweroff"])))),
    ])
