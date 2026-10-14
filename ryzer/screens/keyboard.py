"""On-screen keyboard for search and naming themes."""

import pygame

from ryzer import ui
from ryzer.screens import Screen

ROWS = [
    "1234567890",
    "QWERTYUIOP",
    "ASDFGHJKL-",
    "ZXCVBNM.'&",
]
SPECIAL = ["SPACE", "DEL", "DONE"]


class Keyboard(Screen):
    def __init__(self, app, title, on_done, initial="", max_len=32, live=None):
        super().__init__(app)
        self.title = title
        self.on_done = on_done
        self.live = live            # optional callable(text) for live results
        self.value = initial
        self.max_len = max_len
        self.row, self.col = 1, 0
        self.lower = True

    def _rows(self):
        return [r.lower() if self.lower and i > 0 else r for i, r in enumerate(ROWS)] + [SPECIAL]

    def _type(self, s):
        if len(self.value) < self.max_len:
            self.value += s
            self._changed()

    def _changed(self):
        if self.live:
            self.live(self.value)

    def handle(self, action):
        rows = self._rows()
        snd = self.app.sounds
        if action in ("up", "down"):
            self.row = (self.row + (1 if action == "down" else -1)) % len(rows)
            self.col = min(self.col, len(rows[self.row]) - 1)
            snd.play("move")
        elif action in ("left", "right"):
            self.col = (self.col + (1 if action == "right" else -1)) % len(rows[self.row])
            snd.play("move")
        elif action == "a":
            key = rows[self.row][self.col]
            if key == "SPACE":
                self._type(" ")
            elif key == "DEL":
                self.value = self.value[:-1]
                self._changed()
            elif key == "DONE":
                self._done()
                return
            else:
                self._type(key)
            snd.play("select")
        elif action == "x":
            self.value = self.value[:-1]
            self._changed()
            snd.play("back")
        elif action == "y":
            self._type(" ")
        elif action == "l":
            self.lower = not self.lower
        elif action == "start":
            self._done()
        elif action == "b":
            snd.play("back")
            self.app.pop()

    def _done(self):
        self.app.sounds.play("select")
        self.app.pop()
        self.on_done(self.value.strip())

    def draw(self, surf):
        th = self.theme
        w, h = surf.get_size()
        ui.top_bar(surf, th, self.title, self.app.cfg)
        field = pygame.Rect(20, 54, w - 40, 44)
        ui.panel(surf, field, th, selected=True)
        ui.text(surf, self.value + "_", (field.x + 14, field.y + 11), 28, th.c("text"), max_width=field.width - 28)
        rows = self._rows()
        key_w, key_h, gap = (w - 40 - 9 * 6) // 10, 46, 6
        y = 112
        for r, row in enumerate(rows):
            if r == len(rows) - 1:
                widths = [(w - 40 - 2 * gap) // 3] * 3
            else:
                widths = [key_w] * len(row)
            x = 20
            for c, key in enumerate(row):
                rect = pygame.Rect(x, y, widths[c], key_h)
                sel = r == self.row and c == self.col
                ui.panel(surf, rect, th, selected=sel)
                ui.text(surf, key, (rect.centerx, rect.y + 13), 24,
                        th.c("text") if sel else th.c("text_dim"), align="center")
                x += widths[c] + gap
            y += key_h + gap

    def hints(self):
        return [("L", "Shift"), ("X", "Del"), ("START", "Done"), ("A", "Type")]
