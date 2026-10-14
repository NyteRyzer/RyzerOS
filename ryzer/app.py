"""Main loop, display setup and app-wide actions."""

import argparse
import os
import random
import sys
import time

import pygame

from ryzer import NAME, launcher, library, paths, perf, themes, ui
from ryzer.config import Config, State
from ryzer.input import Input
from ryzer.sound import Sounds

LOGICAL = (640, 480)   # RG DS panels are 640x480; the RG DS Plus is scaled up by SDL


class App:
    def __init__(self, args):
        paths.ensure_dirs()
        self.args = args
        self.cfg = Config()
        self.state = State()
        self.themes = themes.load_all()
        self.theme = self.themes.get(self.cfg["theme"]) or next(iter(self.themes.values()))
        self._preview = None
        self.focus_theme = None
        self.systems = library.load_systems()
        self.rescan()
        self.stack = []
        self.running = True
        self.toast_msg, self.toast_until = "", 0.0
        self.last_input = time.monotonic()
        self.particles = ui.Particles()
        self.quit_hold = 0.0

        pygame.init()
        ui.reset_caches()
        pygame.display.set_caption(NAME)
        pygame.mouse.set_visible(False)
        self.open_display()
        self.input = Input(self.cfg["input"])
        self.sounds = Sounds(self.cfg["sound"], self.cfg["volume"])
        self.clock = pygame.time.Clock()
        perf.apply_profile(self.cfg["default_profile"])

    # ----- display -------------------------------------------------------
    def _want_dual(self, info_w, info_h):
        layout = self.args.layout or self.cfg["screen_layout"]
        if layout == "dual":
            return True
        if layout == "single":
            return False
        # auto: the RG DS reports its two panels as one tall framebuffer
        return info_h >= info_w * 1.5

    def open_display(self):
        if not pygame.display.get_init():
            pygame.display.init()
        info = pygame.display.Info()
        on_device = paths.home().as_posix().startswith("/storage") and not self.args.windowed
        self.dual = self._want_dual(info.current_w or 0, info.current_h or 0)
        size = (LOGICAL[0], LOGICAL[1] * 2) if self.dual else LOGICAL
        flags = pygame.SCALED | (pygame.FULLSCREEN if on_device else 0)
        try:
            self.screen = pygame.display.set_mode(size, flags, vsync=1)
        except pygame.error:
            self.screen = pygame.display.set_mode(size, flags)
        self.main_surf = self.screen.subsurface((0, LOGICAL[1] if self.dual else 0, *LOGICAL))
        self.top_surf = self.screen.subsurface((0, 0, *LOGICAL)) if self.dual else None
        pygame.mouse.set_visible(False)

    def quit_display(self):
        pygame.display.quit()

    # ----- screen stack --------------------------------------------------
    @property
    def current(self):
        return self.stack[-1] if self.stack else None

    def push(self, screen):
        self.stack.append(screen)

    def pop(self):
        if len(self.stack) > 1:
            self.stack.pop()
            self.current.on_resume()

    def replace(self, screen):
        if self.stack:
            self.stack.pop()
        self.stack.append(screen)

    def home(self):
        del self.stack[1:]
        self.current.on_resume()

    # ----- themes --------------------------------------------------------
    def set_theme(self, theme_id, save=True):
        if theme_id in self.themes:
            self.theme = self.themes[theme_id]
            self._preview = None
            if save:
                self.cfg.set("theme", theme_id)

    def preview_theme(self, theme):
        self._preview = theme

    def end_preview(self):
        self._preview = None

    @property
    def active_theme(self):
        return self._preview or self.theme

    # ----- library -------------------------------------------------------
    def rescan(self):
        library.scan(self.systems, paths.roms_dir(), self.cfg["clean_names"])

    def launch(self, game):
        profile = self.cfg.profile_for(game.system.id)
        if self.args.dry_run:
            ok, _, msg = launcher.run(game, profile, dry_run=True)
            self.state.record_play(game.key, 0)
            self.notify(("Would run: " if ok else "") + msg, 4)
            return
        self.draw_launching(game)
        self.quit_display()
        ok, played, msg = launcher.run(game, profile)
        perf.apply_profile(self.cfg["default_profile"])
        self.open_display()
        self.input.open_pads()
        pygame.event.clear()
        if played > 2 or ok:
            self.state.record_play(game.key, played)
        if msg:
            self.sounds.play("error")
            self.notify(msg, 4)
        if self.current:
            self.current.on_resume()

    def draw_launching(self, game):
        th = self.active_theme
        ui.background(self.main_surf, th, 0)
        w, h = self.main_surf.get_size()
        ui.text(self.main_surf, "Launching", (w // 2, h // 2 - 50), 26, th.c("text_dim"), align="center")
        ui.text(self.main_surf, game.name, (w // 2, h // 2 - 14), 36, th.c("text"), align="center",
                bold=True, max_width=w - 40)
        ui.text(self.main_surf, f"{game.system.name}  |  {perf.PROFILES[self.cfg.profile_for(game.system.id)]}",
                (w // 2, h // 2 + 30), 22, th.c("accent"), align="center")
        pygame.display.flip()

    # ----- shortcuts used by several screens -----------------------------
    def open_search(self):
        from ryzer.screens.games import SearchResults
        from ryzer.screens.keyboard import Keyboard

        def done(query):
            if query:
                results = library.search(self.systems, query)
                if results:
                    self.push(SearchResults(self, query, results))
                else:
                    self.notify(f'No games match "{query}"')
        self.push(Keyboard(self, "Search games", done))

    def open_random(self):
        games = [g for s in self.systems for g in s.games if s.id != "ports"]
        if not games:
            self.sounds.play("error")
            self.notify("No games found yet")
            return
        game = random.choice(games)
        self.notify(f"Surprise! {game.name}")
        self.launch(game)

    def open_settings(self):
        from ryzer.screens.settings import settings_menu
        self.push(settings_menu(self))

    def open_themes(self):
        from ryzer.screens.themes_screen import ThemePicker
        self.push(ThemePicker(self))

    def open_power(self):
        from ryzer.screens.settings import power_menu
        self.push(power_menu(self))

    def notify(self, msg, seconds=2.2):
        self.toast_msg, self.toast_until = msg, time.monotonic() + seconds

    def quit(self):
        self.running = False

    def screenshot(self):
        path = paths.screenshots_dir() / time.strftime("ryzer-%Y%m%d-%H%M%S.png")
        pygame.image.save(self.screen, str(path))
        self.notify(f"Screenshot saved: {path.name}")

    # ----- shared top-screen drawing -------------------------------------
    def draw_big_clock(self, surf, y):
        th = self.active_theme
        w = surf.get_width()
        fmt = "%H:%M" if self.cfg["clock_24h"] else "%I:%M"
        ui.text(surf, time.strftime(fmt).lstrip("0") or "0", (w // 2, y), 110, th.c("text"), align="center", bold=True)
        ui.text(surf, time.strftime("%A, %B %d"), (w // 2, y + 92), 28, th.c("accent"), align="center")

    def draw_default_top(self, surf):
        ui.top_bar(surf, self.active_theme, "", self.cfg)
        self.draw_big_clock(surf, surf.get_height() // 2 - 80)

    # ----- main loop -----------------------------------------------------
    def run(self, first_screen):
        self.push(first_screen)
        while self.running:
            dt = self.clock.tick(self.cfg["fps"]) / 1000
            now = time.monotonic()
            events = pygame.event.get()
            for e in events:
                if e.type == pygame.QUIT:
                    self.running = False
            actions = self.input.process(events, now)
            if actions:
                was_dim = self.is_dim(now)
                self.last_input = now
                if was_dim:
                    actions = []  # first press only wakes the screen
            if self.input.combo("select", "start"):
                self.quit_hold += dt
                if self.quit_hold > 1.0:
                    self.running = False
            else:
                self.quit_hold = 0
            for a in actions:
                if a == "r" and "select" in self.input.held:
                    self.screenshot()
                    continue
                if self.current:
                    self.current.handle(a)
            if self.current:
                self.current.update(dt)
            self.render(dt, now)
        self.cfg.save()
        self.state.save()
        pygame.quit()

    def is_dim(self, now):
        mins = self.cfg["sleep_minutes"]
        return bool(mins) and now - self.last_input > mins * 60

    def render(self, dt, now):
        th = self.active_theme
        saved, self.theme = self.theme, th   # screens read app.theme while drawing
        try:
            t = now
            ui.background(self.main_surf, th, t)
            self.particles.draw(self.main_surf, th, dt)
            scr = self.current
            if scr:
                scr.draw(self.main_surf)
                ui.hints(self.main_surf, th, scr.hints())
            if now < self.toast_until:
                ui.toast(self.main_surf, th, self.toast_msg, min(1.0, (self.toast_until - now) * 3))
            if self.dual and self.top_surf is not None:
                ui.background(self.top_surf, th, t)
                if scr:
                    scr.draw_top(self.top_surf)
            if self.is_dim(now):
                shade = pygame.Surface(self.screen.get_size(), pygame.SRCALPHA)
                shade.fill((0, 0, 0, 200))
                self.screen.blit(shade, (0, 0))
        finally:
            self.theme = saved
        pygame.display.flip()


def parse_args(argv=None):
    p = argparse.ArgumentParser(prog="ryzer", description=f"{NAME} frontend")
    p.add_argument("--layout", choices=["auto", "single", "dual"], help="force screen layout")
    p.add_argument("--windowed", action="store_true", help="run in a window (desktop preview)")
    p.add_argument("--dry-run", action="store_true", help="show emulator commands instead of running them")
    p.add_argument("--no-boot", action="store_true", help="skip the boot animation")
    return p.parse_args(argv)


def main(argv=None):
    args = parse_args(argv)
    if not os.environ.get("DISPLAY") and not os.environ.get("WAYLAND_DISPLAY") and sys.platform == "linux":
        os.environ.setdefault("SDL_VIDEODRIVER", "kmsdrm")  # bare console on the handheld
    app = App(args)
    from ryzer.screens.boot import BootScreen
    from ryzer.screens.home import HomeScreen
    home = HomeScreen(app)
    first = BootScreen(app, home) if app.cfg["boot_animation"] and not args.no_boot else home
    app.run(first)
