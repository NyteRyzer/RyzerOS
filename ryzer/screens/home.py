"""The dashboard: a console-style ribbon of tiles."""

import math
import random

import pygame

from ryzer import ui
from ryzer.config import format_duration
from ryzer.library import find_game
from ryzer.screens import Screen
from ryzer.screens.menus import wrap

TILE_W, TILE_H = 132, 150
SEL_W, SEL_H = 160, 180
GAP = 16


def plural(n, word):
    return f"{n} {word}{'' if n == 1 else 's'}"


class Tile:
    def __init__(self, kind, label, sub="", color=None, system=None, game=None, note="", tier=None):
        self.kind = kind
        self.label = label
        self.sub = sub
        self.color = color
        self.system = system
        self.game = game
        self.note = note
        self.tier = tier


def draw_icon(surf, kind, center, size, color):
    cx, cy = center
    s = size
    if kind == "favorites":
        pts = []
        for k in range(10):
            r = s if k % 2 == 0 else s * 0.45
            a = math.radians(-90 + k * 36)
            pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
        pygame.draw.polygon(surf, color, pts)
    elif kind == "recent":
        pygame.draw.circle(surf, color, center, s, 4)
        pygame.draw.line(surf, color, center, (cx, cy - s * 0.6), 4)
        pygame.draw.line(surf, color, center, (cx + s * 0.45, cy), 4)
    elif kind == "search":
        pygame.draw.circle(surf, color, (cx - s * 0.2, cy - s * 0.2), s * 0.6, 4)
        pygame.draw.line(surf, color, (cx + s * 0.22, cy + s * 0.22), (cx + s * 0.8, cy + s * 0.8), 6)
    elif kind == "settings":
        for k in range(8):
            a = math.radians(k * 45)
            pygame.draw.line(surf, color, center, (cx + s * math.cos(a), cy + s * math.sin(a)), 7)
        pygame.draw.circle(surf, color, center, int(s * 0.7))
        pygame.draw.circle(surf, (0, 0, 0), center, int(s * 0.3))
    elif kind == "themes":
        cols = [(255, 80, 80), (80, 200, 255), (255, 210, 60), (120, 230, 120)]
        pygame.draw.circle(surf, color, center, s, 4)
        for k, c in enumerate(cols):
            a = math.radians(-135 + k * 70)
            pygame.draw.circle(surf, c, (cx + s * 0.5 * math.cos(a), cy + s * 0.5 * math.sin(a)), s * 0.2)
    elif kind == "power":
        pygame.draw.arc(surf, color, (cx - s, cy - s, 2 * s, 2 * s), math.radians(120), math.radians(420), 5)
        pygame.draw.line(surf, color, (cx, cy - s - 2), (cx, cy), 5)
    elif kind == "random":
        r = pygame.Rect(0, 0, s * 1.6, s * 1.6)
        r.center = center
        pygame.draw.rect(surf, color, r, 4, border_radius=8)
        for dx, dy in ((-0.4, -0.4), (0.4, 0.4), (0, 0), (0.4, -0.4), (-0.4, 0.4)):
            pygame.draw.circle(surf, color, (cx + dx * s, cy + dy * s), 4)
    elif kind == "continue":
        pygame.draw.polygon(surf, color, [(cx - s * 0.5, cy - s * 0.8), (cx - s * 0.5, cy + s * 0.8), (cx + s * 0.8, cy)])


class HomeScreen(Screen):
    title = "Home"

    def __init__(self, app):
        super().__init__(app)
        self.sel = 0
        self.scroll = 0.0
        self.t = 0.0
        self.tiles = []
        self.rebuild()

    def rebuild(self):
        app = self.app
        old = self.tiles[self.sel].label if self.tiles else None
        tiles = []
        recent = [g for g in (find_game(app.systems, k) for k in app.state.data["recent"]) if g]
        if recent:
            g = recent[0]
            tiles.append(Tile("continue", "Continue", g.name, game=g, system=g.system,
                              note=f"Resume {g.name} ({g.system.short}). Played {format_duration(app.state.playtime(g.key))}."))
        favs = [g for g in (find_game(app.systems, k) for k in app.state.data["favorites"]) if g]
        tiles.append(Tile("favorites", "Favorites", plural(len(favs), "game"), note="Games you starred with X."))
        tiles.append(Tile("recent", "Recent", plural(len(recent), "game"), note="Your recently played games."))
        for s in app.systems:
            if not s.games and app.cfg["hide_empty_systems"]:
                continue
            tiles.append(Tile("system", s.short, plural(len(s.games), "game"), color=s.color, system=s,
                              note=s.note, tier=s))
        tiles.append(Tile("random", "Surprise", "Random game", note="Can't decide? Launch a random game from your library."))
        tiles.append(Tile("search", "Search", "All systems", note="Find any game by name."))
        tiles.append(Tile("themes", "Themes", f"{len(app.themes)} themes", note="Pick from 20+ themes or design your own."))
        tiles.append(Tile("settings", "Settings", "System", note="Performance, display, sound, controls and more."))
        tiles.append(Tile("power", "Power", "Exit / Reboot", note="Exit Ryzer, reboot or shut down."))
        self.tiles = tiles
        labels = [t.label for t in tiles]
        if old in labels:
            self.sel = labels.index(old)
        self.sel = min(self.sel, len(tiles) - 1)

    def on_resume(self):
        self.rebuild()

    def handle(self, action):
        n = len(self.tiles)
        snd = self.app.sounds
        if action in ("left", "right"):
            self.sel = (self.sel + (1 if action == "right" else -1)) % n
            snd.play("move")
        elif action in ("l", "r"):
            self.sel = max(0, min(n - 1, self.sel + (4 if action == "r" else -4)))
            snd.play("move")
        elif action == "a":
            snd.play("select")
            self.open(self.tiles[self.sel])
        elif action == "y":
            self.app.open_search()
        elif action == "x":
            self.app.open_random()
        elif action == "start":
            self.app.open_settings()
        elif action == "select":
            self.app.open_themes()
        elif action == "b":
            pass  # already home

    def open(self, tile):
        app = self.app
        from ryzer.screens.games import GameList
        if tile.kind == "continue":
            app.launch(tile.game)
        elif tile.kind == "system":
            app.push(GameList(app, tile.system.name, tile.system.games, system=tile.system))
        elif tile.kind == "favorites":
            app.push(GameList(app, "Favorites", None, source="favorites"))
        elif tile.kind == "recent":
            app.push(GameList(app, "Recently Played", None, source="recent"))
        elif tile.kind == "random":
            app.open_random()
        elif tile.kind == "search":
            app.open_search()
        elif tile.kind == "themes":
            app.open_themes()
        elif tile.kind == "settings":
            app.open_settings()
        elif tile.kind == "power":
            app.open_power()

    def update(self, dt):
        self.t += dt
        if self.app.cfg["animations"]:
            self.scroll += (self.sel - self.scroll) * min(1.0, dt * 12)
        else:
            self.scroll = float(self.sel)

    def draw_tile(self, surf, tile, rect, selected):
        th = self.theme
        pulse = (math.sin(self.t * 4) + 1) / 2 if selected else 0
        ui.panel(surf, rect, th, selected=selected, pulse=pulse)
        band_col = tile.color or th.c("accent")
        band = pygame.Rect(rect.x + 6, rect.y + 6, rect.width - 12, rect.height * 0.55)
        pygame.draw.rect(surf, ui.mix(th.c("panel"), band_col, 0.75 if selected else 0.5), band,
                         border_radius=max(0, th.style["corner_radius"] - 4))
        icon_col = th.c("highlight_text") if selected else ui.mix(th.c("highlight_text"), th.c("panel"), 0.3)
        if tile.kind == "system":
            ui.text(surf, tile.label, (band.centerx, band.centery - 20), 44 if selected else 38,
                    icon_col, align="center", bold=True)
        else:
            draw_icon(surf, tile.kind, band.center, 26 if selected else 22, icon_col)
        name = tile.label if tile.kind != "continue" else "Continue"
        ui.text(surf, name, (rect.centerx, band.bottom + 10), 24 if selected else 22,
                th.c("text"), align="center", bold=selected, max_width=rect.width - 12, theme=th)
        ui.text(surf, tile.sub, (rect.centerx, band.bottom + 36), 18, th.c("text_dim"),
                align="center", max_width=rect.width - 12)

    def draw(self, surf):
        th = self.theme
        w, h = surf.get_size()
        ui.top_bar(surf, th, "RYZER", self.app.cfg)
        cy = 70 + SEL_H // 2
        # Draw unselected first, selected last so its glow sits on top
        order = sorted(range(len(self.tiles)), key=lambda i: i == self.sel)
        for i in order:
            dx = (i - self.scroll) * (TILE_W + GAP)
            if i != self.sel:
                dx += (SEL_W - TILE_W) / 2 * (1 if i > self.sel else -1)
            cx = w // 2 + dx
            if cx < -SEL_W or cx > w + SEL_W:
                continue
            sel = i == self.sel
            tw, tht = (SEL_W, SEL_H) if sel else (TILE_W, TILE_H)
            rect = pygame.Rect(0, 0, tw, tht)
            rect.center = (int(cx), cy)
            self.draw_tile(surf, self.tiles[i], rect, sel)
        # Info panel
        tile = self.tiles[self.sel]
        info = pygame.Rect(16, cy + SEL_H // 2 + 16, w - 32, h - (cy + SEL_H // 2 + 16) - 46)
        ui.panel(surf, info, th)
        title = tile.system.name if tile.kind == "system" else (tile.sub if tile.kind == "continue" else tile.label)
        ui.text(surf, title, (info.x + 14, info.y + 10), 28, th.c("text"), bold=True,
                max_width=info.width - 200, theme=th)
        if tile.tier:
            self.tier_badge(surf, tile.tier, (info.right - 12, info.y + 10))
        for i, line in enumerate(wrap(tile.note, 20, info.width - 28)[:3]):
            ui.text(surf, line, (info.x + 14, info.y + 42 + i * 21), 20, th.c("text_dim"))
        # Page dots
        n = len(self.tiles)
        for i in range(n):
            x = w // 2 + (i - (n - 1) / 2) * 12
            pygame.draw.circle(surf, th.c("accent") if i == self.sel else th.c("panel_border"),
                               (int(x), info.y - 8), 3 if i == self.sel else 2)

    def tier_badge(self, surf, system, topright):
        th = self.theme
        col = {"great": th.c("good"), "good": th.c("good"), "playable": th.c("warn"),
               "limited": th.c("warn"), "experimental": th.c("bad")}.get(system.tier, th.c("text_dim"))
        img = ui.font(18, True).render(system.tier_label.upper(), True, th.c("bg_bottom"))
        r = img.get_rect().inflate(14, 8)
        r.topright = topright
        pygame.draw.rect(surf, col, r, border_radius=8)
        surf.blit(img, img.get_rect(center=r.center))

    def draw_top(self, surf):
        th = self.theme
        w, h = surf.get_size()
        ui.top_bar(surf, th, "", self.app.cfg)
        self.app.draw_big_clock(surf, h // 2 - 90)
        tile = self.tiles[self.sel]
        img = tile.game.image() if tile.game else None
        if img:
            pic = ui.load_image(img, (w - 80, h // 2 - 60))
            if pic:
                surf.blit(pic, pic.get_rect(midtop=(w // 2, h // 2 + 10)))
                return
        st = self.app.state
        ui.text(surf, f"Total play time  {format_duration(st.total_playtime())}", (w // 2, h // 2 + 40),
                24, th.c("text_dim"), align="center")
        ui.text(surf, f"{sum(len(s.games) for s in self.app.systems)} games  |  "
                      f"{len(st.data['favorites'])} favorites", (w // 2, h // 2 + 72),
                24, th.c("text_dim"), align="center")

    def hints(self):
        return [("SELECT", "Themes"), ("X", "Random"), ("Y", "Search"), ("A", "Open")]
