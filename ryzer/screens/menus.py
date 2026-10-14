"""Generic list menu, message box and confirm dialog."""

import pygame

from ryzer import ui
from ryzer.screens import Screen


class Item:
    def __init__(self, label, select=None, value=None, change=None, help="", swatch=None):
        self.label = label
        self.select = select      # callable on A
        self.value = value        # callable -> str shown on the right
        self.change = change      # callable(direction) on left/right
        self.help = help
        self.swatch = swatch      # callable -> rgb, draws a colour chip


class Menu(Screen):
    ROW_H = 40

    def __init__(self, app, title, items, subtitle=""):
        super().__init__(app)
        self.title = title
        self.items = items
        self.subtitle = subtitle
        self.sel = 0
        self.offset = 0

    def current(self):
        return self.items[self.sel] if self.items else None

    def handle(self, action):
        snd = self.app.sounds
        if action in ("up", "down") and self.items:
            self.sel = (self.sel + (1 if action == "down" else -1)) % len(self.items)
            snd.play("move")
        elif action in ("l", "r") and self.items:
            self.sel = max(0, min(len(self.items) - 1, self.sel + (8 if action == "r" else -8)))
            snd.play("move")
        elif action in ("left", "right"):
            it = self.current()
            if it and it.change:
                it.change(1 if action == "right" else -1)
                snd.play("move")
        elif action == "a":
            it = self.current()
            if it and it.select:
                snd.play("select")
                it.select()
            elif it and it.change:
                it.change(1)
                snd.play("move")
        elif action == "b":
            snd.play("back")
            self.app.pop()

    def draw(self, surf):
        th = self.theme
        w, h = surf.get_size()
        ui.top_bar(surf, th, self.title, self.app.cfg)
        top = 52
        if self.subtitle:
            ui.text(surf, self.subtitle, (20, top), 20, th.c("text_dim"), max_width=w - 40, theme=th)
            top += 26
        help_h = 30
        visible = max(1, (h - top - 40 - help_h) // self.ROW_H)
        self.offset = ui.scroll_offset(self.sel, self.offset, visible, len(self.items))
        for i, it in enumerate(self.items[self.offset:self.offset + visible]):
            idx = self.offset + i
            rect = pygame.Rect(16, top + i * self.ROW_H, w - 44, self.ROW_H - 6)
            selected = idx == self.sel
            ui.panel(surf, rect, th, selected=selected)
            col = th.c("text") if selected else ui.mix(th.c("text"), th.c("text_dim"), 0.4)
            ui.text(surf, it.label, (rect.x + 14, rect.y + 8), 24, col, max_width=rect.width // 2, theme=th)
            x = rect.right - 14
            if it.swatch:
                sw = pygame.Rect(0, 0, 26, 20)
                sw.midright = (x, rect.centery)
                pygame.draw.rect(surf, it.swatch(), sw, border_radius=4)
                pygame.draw.rect(surf, th.c("text"), sw, 1, border_radius=4)
                x = sw.left - 10
            if it.value:
                val = str(it.value())
                if it.change:
                    val = f"<  {val}  >" if selected else val
                ui.text(surf, val, (x, rect.y + 9), 22, th.c("accent") if selected else th.c("text_dim"),
                        align="right", max_width=rect.width // 2 - 20, theme=th)
        ui.scrollbar(surf, th, (w - 20, top, 5, visible * self.ROW_H - 6), self.offset, visible, len(self.items))
        it = self.current()
        if it and it.help:
            ui.text(surf, it.help, (20, h - 36 - help_h + 4), 20, th.c("text_dim"), max_width=w - 40, theme=th)

    def draw_top(self, surf):
        th = self.theme
        w, h = surf.get_size()
        ui.top_bar(surf, th, self.title, self.app.cfg)
        it = self.current()
        if it:
            ui.text(surf, it.label, (w // 2, h // 2 - 40), 40, th.c("text"), align="center", bold=True,
                    max_width=w - 40)
            if it.help:
                ui.text(surf, it.help, (w // 2, h // 2 + 10), 22, th.c("text_dim"), align="center",
                        max_width=w - 40)

    def hints(self):
        return [("B", "Back"), ("A", "Select")]


def wrap(s, size, width):
    f = ui.font(size)
    lines = []
    for para in str(s).split("\n"):
        line = ""
        for word in para.split(" "):
            trial = f"{line} {word}".strip()
            if f.size(trial)[0] <= width:
                line = trial
            else:
                if line:
                    lines.append(line)
                line = word
        lines.append(line)
    return lines


class Message(Screen):
    def __init__(self, app, title, body, on_close=None):
        super().__init__(app)
        self.title = title
        self.body = body
        self.on_close = on_close
        self.offset = 0

    def handle(self, action):
        if action in ("a", "b", "start"):
            self.app.sounds.play("back")
            self.app.pop()
            if self.on_close:
                self.on_close()
        elif action == "down":
            self.offset += 1
        elif action == "up":
            self.offset = max(0, self.offset - 1)

    def draw(self, surf):
        th = self.theme
        w, h = surf.get_size()
        ui.top_bar(surf, th, self.title, self.app.cfg)
        box = pygame.Rect(20, 56, w - 40, h - 56 - 46)
        ui.panel(surf, box, th)
        lines = wrap(self.body, 22, box.width - 28)
        rows = (box.height - 20) // 24
        self.offset = min(self.offset, max(0, len(lines) - rows))
        for i, line in enumerate(lines[self.offset:self.offset + rows]):
            ui.text(surf, line, (box.x + 14, box.y + 12 + i * 24), 22, th.c("text"))

    def hints(self):
        return [("A", "OK")]


class Confirm(Screen):
    def __init__(self, app, question, on_yes, yes="Yes", no="Cancel"):
        super().__init__(app)
        self.title = "Confirm"
        self.question = question
        self.on_yes = on_yes
        self.labels = (yes, no)
        self.choice = 1

    def handle(self, action):
        if action in ("left", "right"):
            self.choice = 1 - self.choice
            self.app.sounds.play("move")
        elif action == "a":
            self.app.sounds.play("select")
            self.app.pop()
            if self.choice == 0:
                self.on_yes()
        elif action == "b":
            self.app.sounds.play("back")
            self.app.pop()

    def draw(self, surf):
        th = self.theme
        w, h = surf.get_size()
        ui.top_bar(surf, th, "Confirm", self.app.cfg)
        box = pygame.Rect(40, h // 2 - 90, w - 80, 180)
        ui.panel(surf, box, th)
        for i, line in enumerate(wrap(self.question, 24, box.width - 40)[:3]):
            ui.text(surf, line, (w // 2, box.y + 24 + i * 28), 24, th.c("text"), align="center")
        for i, label in enumerate(self.labels):
            r = pygame.Rect(0, 0, 150, 40)
            r.center = (w // 2 + (-90 if i == 0 else 90), box.bottom - 38)
            ui.panel(surf, r, th, selected=self.choice == i)
            ui.text(surf, label, (r.centerx, r.y + 10), 24, th.c("text"), align="center")

    def hints(self):
        return [("B", "Cancel"), ("A", "Choose")]
