"""Game list with details panel, favorites, letter jump and per-game options."""

import pygame

from ryzer import launcher, perf, ui
from ryzer.config import format_duration
from ryzer.library import find_game
from ryzer.screens import Screen
from ryzer.screens.menus import Item, Menu, Message

ROW_H = 34


class GameList(Screen):
    def __init__(self, app, title, games, system=None, source=None):
        super().__init__(app)
        self.title = title
        self.system = system
        self.source = source   # "favorites" / "recent" lists refresh themselves
        self._games = games
        self.sel = 0
        self.offset = 0
        self.refresh()

    def refresh(self):
        st, systems = self.app.state, self.app.systems
        if self.source == "favorites":
            self.games = sorted((g for g in (find_game(systems, k) for k in st.data["favorites"]) if g),
                                key=lambda g: g.name.lower())
        elif self.source == "recent":
            self.games = [g for g in (find_game(systems, k) for k in st.data["recent"]) if g]
        else:
            self.games = list(self._games or [])
        self.sel = min(self.sel, max(0, len(self.games) - 1))

    def on_resume(self):
        self.refresh()

    def current(self):
        return self.games[self.sel] if self.games else None

    def _letter_jump(self, direction):
        if not self.games:
            return
        letter = self.games[self.sel].name[:1].lower()
        idxs = range(self.sel + 1, len(self.games)) if direction > 0 else range(self.sel - 1, -1, -1)
        for i in idxs:
            first = self.games[i].name[:1].lower()
            if first != letter:
                if direction < 0:  # go to the first game of that letter
                    while i > 0 and self.games[i - 1].name[:1].lower() == first:
                        i -= 1
                self.sel = i
                return
        self.sel = len(self.games) - 1 if direction > 0 else 0

    def handle(self, action):
        snd = self.app.sounds
        n = len(self.games)
        if action in ("up", "down") and n:
            self.sel = (self.sel + (1 if action == "down" else -1)) % n
            snd.play("move")
        elif action in ("left", "right") and n:
            self.sel = max(0, min(n - 1, self.sel + (10 if action == "right" else -10)))
            snd.play("move")
        elif action in ("l", "r"):
            self._letter_jump(1 if action == "r" else -1)
            snd.play("move")
        elif action == "a" and n:
            snd.play("select")
            self.app.launch(self.current())
        elif action == "x" and n:
            fav = self.app.state.toggle_favorite(self.current().key)
            snd.play("fav")
            self.app.notify("Added to favorites" if fav else "Removed from favorites")
            if self.source == "favorites":
                self.refresh()
        elif action == "start" and n:
            self.options()
        elif action == "y":
            self.app.open_search()
        elif action == "b":
            snd.play("back")
            self.app.pop()

    def options(self):
        app, game = self.app, self.current()
        sid = game.system.id

        def profile_value():
            return perf.PROFILES[app.cfg.profile_for(sid)]

        def profile_change(d):
            order = perf.PROFILE_ORDER
            cur = app.cfg.profile_for(sid)
            app.cfg.data["system_profiles"][sid] = order[(order.index(cur) + d) % len(order)]
            app.cfg.save()

        def show_cmd():
            argv = launcher.resolve(game.system.commands, game.path)
            app.push(Message(app, "Launch command", launcher.describe(argv) + "\n\nEdit systems.json to change it."))

        items = [
            Item("Play", select=lambda: (app.pop(), app.launch(game))),
            Item("Favorite", value=lambda: "Yes" if app.state.is_favorite(game.key) else "No",
                 change=lambda d: app.state.toggle_favorite(game.key)),
            Item(f"{game.system.short} speed profile", value=profile_value, change=profile_change,
                 help="Applies to every game on this system. Turbo = max stock clocks."),
            Item("Show launch command", select=show_cmd),
            Item("Game info", select=lambda: app.push(Message(app, game.name, self.info_text(game)))),
        ]
        app.push(Menu(app, game.name, items))

    def info_text(self, game):
        st = self.app.state
        try:
            size = game.path.stat().st_size / 1e6
        except OSError:
            size = 0
        return (f"System: {game.system.name}\nFile: {game.path.name}\nSize: {size:.1f} MB\n"
                f"Play time: {format_duration(st.playtime(game.key))}\n"
                f"Launches: {st.data['launches'].get(game.key, 0)}\n"
                f"Performance (RK3568-class chip): {game.system.tier_label}\n{game.system.note}")

    def draw(self, surf):
        th = self.theme
        w, h = surf.get_size()
        ui.top_bar(surf, th, self.title, self.app.cfg)
        if not self.games:
            msg = {"favorites": "No favorites yet. Press X on any game to add it.",
                   "recent": "Nothing played yet."}.get(self.source, "No games found. Copy ROMs to the roms folder.")
            ui.text(surf, msg, (w // 2, h // 2 - 12), 24, th.c("text_dim"), align="center", max_width=w - 40)
            return
        list_w = w - 220 if not self.app.dual else w - 28
        top = 52
        visible = (h - top - 44) // ROW_H
        self.offset = ui.scroll_offset(self.sel, self.offset, visible, len(self.games))
        for i, g in enumerate(self.games[self.offset:self.offset + visible]):
            idx = self.offset + i
            rect = pygame.Rect(14, top + i * ROW_H, list_w, ROW_H - 4)
            sel = idx == self.sel
            if sel:
                ui.panel(surf, rect, th, selected=True)
            x = rect.x + 12
            if self.app.state.is_favorite(g.key):
                ui.text(surf, "*", (x, rect.y + 4), 30, th.c("warn"), bold=True)
            x += 16
            label = g.name if self.system else f"{g.name}  [{g.system.short}]"
            ui.text(surf, label, (x, rect.y + 6), 23, th.c("text") if sel else th.c("text_dim"),
                    max_width=rect.width - 40, theme=th)
        ui.scrollbar(surf, th, (list_w + 18, top, 5, visible * ROW_H - 4), self.offset, visible, len(self.games))
        ui.text(surf, f"{self.sel + 1}/{len(self.games)}", (list_w + 10, h - 62), 18, th.c("text_dim"), align="right")
        if not self.app.dual:
            self.draw_details(surf, pygame.Rect(w - 196, top, 182, h - top - 46), compact=True)

    def draw_details(self, surf, rect, compact=False):
        th = self.theme
        g = self.current()
        if not g:
            return
        ui.panel(surf, rect, th)
        y = rect.y + 10
        img = g.image()
        pic = ui.load_image(img, (rect.width - 20, rect.height // 2 if compact else rect.height - 150)) if img else None
        if pic:
            surf.blit(pic, pic.get_rect(midtop=(rect.centerx, y)))
            y += pic.get_height() + 8
        else:
            art = pygame.Rect(rect.x + 10, y, rect.width - 20, 90 if compact else 140)
            pygame.draw.rect(surf, ui.mix(th.c("panel"), g.system.color, 0.6), art,
                             border_radius=max(0, th.style["corner_radius"] - 4))
            ui.text(surf, g.system.short, (art.centerx, art.centery - 18), 44, th.c("highlight_text"),
                    align="center", bold=True)
            y = art.bottom + 8
        ui.text(surf, g.name, (rect.centerx, y), 22 if compact else 30, th.c("text"), align="center",
                bold=True, max_width=rect.width - 16)
        y += 26 if compact else 36
        st = self.app.state
        for line in (f"Played {format_duration(st.playtime(g.key))}",
                     g.system.tier_label,
                     perf.PROFILES[self.app.cfg.profile_for(g.system.id)] + " profile"):
            ui.text(surf, line, (rect.centerx, y), 19 if compact else 24, th.c("text_dim"), align="center",
                    max_width=rect.width - 16)
            y += 22 if compact else 30

    def draw_top(self, surf):
        th = self.theme
        w, h = surf.get_size()
        ui.top_bar(surf, th, self.title, self.app.cfg)
        if self.games:
            self.draw_details(surf, pygame.Rect(20, 52, w - 40, h - 64))

    def hints(self):
        return [("START", "Options"), ("X", "Fav"), ("L/R", "A-Z"), ("A", "Play")]


class SearchResults(GameList):
    """Live-updating results list used under the on-screen keyboard."""

    def __init__(self, app, query, games):
        super().__init__(app, f'Search: "{query}"', games)
