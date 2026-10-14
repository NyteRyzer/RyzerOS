"""Theme picker with live preview, and the on-device theme editor."""

import pygame

from ryzer import themes as theme_mod
from ryzer import ui
from ryzer.screens import Screen
from ryzer.screens.keyboard import Keyboard
from ryzer.screens.menus import Confirm, Item, Menu

COLOR_LABELS = {
    "bg_top": "Background top", "bg_bottom": "Background bottom", "panel": "Panel",
    "panel_border": "Panel border", "accent": "Accent", "accent2": "Accent 2",
    "text": "Text", "text_dim": "Dim text", "highlight_text": "Text on accent",
    "good": "Good / OK", "warn": "Warning", "bad": "Error",
}


def mini_preview(surf, rect, theme):
    """A tiny render of the dashboard in the given theme."""
    rect = pygame.Rect(rect)
    r = max(2, theme.style["corner_radius"] // 3)
    for y in range(rect.height):
        c = ui.mix(theme.c("bg_top"), theme.c("bg_bottom"), y / max(1, rect.height - 1))
        pygame.draw.line(surf, c, (rect.x, rect.y + y), (rect.right - 1, rect.y + y))
    tw = rect.width // 5
    for k in range(3):
        sel = k == 1
        t = pygame.Rect(0, 0, tw + (8 if sel else 0), int(rect.height * (0.5 if sel else 0.42)))
        t.center = (rect.centerx + (k - 1) * (tw + 10), rect.y + rect.height * 0.42)
        pygame.draw.rect(surf, ui.mix(theme.c("panel"), theme.c("accent"), 0.25 if sel else 0), t, border_radius=r)
        pygame.draw.rect(surf, theme.c("accent") if sel else theme.c("panel_border"), t, 2 if sel else 1,
                         border_radius=r)
    bar = pygame.Rect(rect.x + 8, rect.bottom - 18, rect.width - 16, 10)
    pygame.draw.rect(surf, theme.c("panel"), bar, border_radius=4)
    pygame.draw.rect(surf, theme.c("accent2"), (bar.x, bar.y, bar.width // 2, bar.height), border_radius=4)
    pygame.draw.rect(surf, theme.c("text_dim"), rect, 1)


class ThemePicker(Screen):
    title = "Themes"
    COLS = 3

    def __init__(self, app):
        super().__init__(app)
        self.original = app.cfg["theme"]
        self.offset = 0
        self.reload()
        ids = [t.id for t in self.list]
        self.sel = ids.index(self.original) if self.original in ids else 0

    def reload(self):
        self.app.themes = theme_mod.load_all()
        self.list = sorted(self.app.themes.values(), key=lambda t: (not t.builtin, t.name.lower()))

    def on_resume(self):
        cur = self.list[self.sel].id if self.list else None
        if self.app.focus_theme:  # the editor just saved a new theme
            cur = self.original = self.app.focus_theme
            self.app.focus_theme = None
        self.reload()
        ids = [t.id for t in self.list]
        self.sel = ids.index(cur) if cur in ids else min(self.sel, len(self.list) - 1)
        self.app.set_theme(self.list[self.sel].id, save=False)

    def _move(self, d):
        self.sel = max(0, min(len(self.list) - 1, self.sel + d))
        self.app.set_theme(self.list[self.sel].id, save=False)  # live preview
        self.app.sounds.play("move")

    def handle(self, action):
        if action == "left":
            self._move(-1)
        elif action == "right":
            self._move(1)
        elif action == "up":
            self._move(-self.COLS)
        elif action == "down":
            self._move(self.COLS)
        elif action == "a":
            self.app.set_theme(self.list[self.sel].id, save=True)
            self.app.sounds.play("select")
            self.app.notify(f"Theme set: {self.list[self.sel].name}")
            self.app.pop()
        elif action == "b":
            self.app.set_theme(self.original, save=False)
            self.app.sounds.play("back")
            self.app.pop()
        elif action == "x":
            self.app.push(ThemeEditor(self.app, self.list[self.sel]))
        elif action == "y":
            t = self.list[self.sel]
            if not t.builtin:
                def delete():
                    theme_mod.delete_user_theme(t)
                    self.sel = max(0, self.sel - 1)
                    self.on_resume()
                self.app.push(Confirm(self.app, f"Delete theme '{t.name}'?", delete, yes="Delete"))
            else:
                self.app.notify("Built-in themes can't be deleted")

    def draw(self, surf):
        th = self.theme
        w, h = surf.get_size()
        ui.top_bar(surf, th, f"Themes  ({self.sel + 1}/{len(self.list)})", self.app.cfg)
        cell_w = (w - 32 - (self.COLS - 1) * 12) // self.COLS
        cell_h = 128
        top = 52
        rows_visible = (h - top - 44) // (cell_h + 10)
        row = self.sel // self.COLS
        total_rows = (len(self.list) + self.COLS - 1) // self.COLS
        if row < self.offset:
            self.offset = row
        elif row >= self.offset + rows_visible:
            self.offset = row - rows_visible + 1
        self.offset = max(0, min(self.offset, max(0, total_rows - rows_visible)))
        for i, t in enumerate(self.list):
            r, c = divmod(i, self.COLS)
            if not self.offset <= r < self.offset + rows_visible:
                continue
            rect = pygame.Rect(16 + c * (cell_w + 12), top + (r - self.offset) * (cell_h + 10), cell_w, cell_h)
            sel = i == self.sel
            ui.panel(surf, rect, th, selected=sel)
            mini_preview(surf, (rect.x + 8, rect.y + 8, rect.width - 16, rect.height - 44), t)
            name = t.name + ("" if t.builtin else "  (custom)")
            ui.text(surf, name, (rect.centerx, rect.bottom - 30), 20, th.c("text") if sel else th.c("text_dim"),
                    align="center", max_width=rect.width - 12)
            if t.id == self.app.cfg["theme"]:
                pygame.draw.circle(surf, th.c("good"), (rect.right - 14, rect.y + 14), 6)

    def hints(self):
        return [("Y", "Delete"), ("X", "Edit copy"), ("B", "Cancel"), ("A", "Apply")]


class ThemeEditor(Menu):
    """Edits a copy of a theme. Built-in themes are never modified."""

    STEP = 8

    def __init__(self, app, base):
        if base.builtin:
            new_id, new_name = theme_mod.next_custom_id(app.themes)
            self.theme_obj = base.copy(new_id, new_name)
            self.theme_obj.name = f"{base.name} Custom"
        else:
            self.theme_obj = base.copy(base.id, base.name)
        self.channel = {k: 0 for k in theme_mod.COLOR_KEYS}  # which of R/G/B is being edited
        items = [Item("Name", value=lambda: self.theme_obj.name, select=self.rename)]
        for key in theme_mod.COLOR_KEYS:
            items.append(self._color_item(key))
        items += [
            self._style_item("Background", "background", theme_mod.BACKGROUNDS),
            self._style_item("Tile style", "tile_style", theme_mod.TILE_STYLES),
            Item("Corner radius", value=lambda: self.theme_obj.style["corner_radius"],
                 change=lambda d: self._style_num("corner_radius", d * 2, 0, 32)),
            Item("Text size", value=lambda: f"{self.theme_obj.style['font_scale']:.1f}x",
                 change=lambda d: self._style_num("font_scale", d * 0.1, 0.8, 1.4)),
            Item("Particles", value=lambda: "On" if self.theme_obj.style["particles"] else "Off",
                 change=lambda d: self._toggle_particles()),
            Item("Save theme", select=self.save, help="Saved to ryzer/themes/ - you can also edit the JSON on a PC."),
        ]
        super().__init__(app, "Theme Editor", items,
                         subtitle="Left/Right adjust  |  Y switches R/G/B channel")
        app.preview_theme(self.theme_obj)

    def _color_item(self, key):
        def value():
            rgb = self.theme_obj.colors[key]
            ch = "RGB"[self.channel[key]]
            return f"{ch}:{rgb[self.channel[key]]:3d}  {theme_mod.to_hex(rgb)}"

        def change(d):
            rgb = list(self.theme_obj.colors[key])
            ch = self.channel[key]
            rgb[ch] = max(0, min(255, rgb[ch] + d * self.STEP))
            self.theme_obj.colors[key] = tuple(rgb)
            self.app.preview_theme(self.theme_obj)

        it = Item(COLOR_LABELS[key], value=value, change=change, swatch=lambda: self.theme_obj.colors[key])
        it.color_key = key
        return it

    def _style_item(self, label, key, options):
        def change(d):
            cur = self.theme_obj.style[key]
            self.theme_obj.style[key] = options[(options.index(cur) + d) % len(options)]
            self.app.preview_theme(self.theme_obj)
        return Item(label, value=lambda: self.theme_obj.style[key].title(), change=change)

    def _style_num(self, key, delta, lo, hi):
        v = self.theme_obj.style[key] + delta
        self.theme_obj.style[key] = round(max(lo, min(hi, v)), 1) if isinstance(delta, float) else max(lo, min(hi, v))
        self.app.preview_theme(self.theme_obj)

    def _toggle_particles(self):
        self.theme_obj.style["particles"] = not self.theme_obj.style["particles"]
        self.app.preview_theme(self.theme_obj)

    def rename(self):
        def done(name):
            if name:
                self.theme_obj.name = name
        self.app.push(Keyboard(self.app, "Theme name", done, initial=self.theme_obj.name, max_len=24))

    def save(self):
        theme_mod.save_user_theme(self.theme_obj)
        self.app.themes = theme_mod.load_all()
        self.app.set_theme(self.theme_obj.id, save=True)
        self.app.focus_theme = self.theme_obj.id
        self.app.notify(f"Saved '{self.theme_obj.name}'")
        self.app.pop()

    def handle(self, action):
        it = self.current()
        if action == "y" and hasattr(it, "color_key"):
            self.channel[it.color_key] = (self.channel[it.color_key] + 1) % 3
            self.app.sounds.play("move")
            return
        if action == "b":
            self.app.end_preview()
        super().handle(action)

    def hints(self):
        return [("Y", "R/G/B"), ("B", "Discard"), ("A", "Select")]
