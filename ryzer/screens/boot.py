"""Boot animation: the Ryzer logo powering up, console style."""

import math

import pygame

from ryzer import NAME, VERSION, ui
from ryzer.screens import Screen

DURATION = 2.4


class BootScreen(Screen):
    def __init__(self, app, next_screen):
        super().__init__(app)
        self.next_screen = next_screen
        self.t = 0.0
        self.app.sounds.play("boot")

    def handle(self, action):
        self.finish()

    def finish(self):
        self.app.replace(self.next_screen)

    def update(self, dt):
        self.t += dt
        if self.t >= DURATION:
            self.finish()

    def hints(self):
        return []

    def _logo(self, surf, progress):
        th = self.theme
        w, h = surf.get_size()
        cx, cy = w // 2, h // 2 - 20
        # Expanding rings
        for i in range(3):
            p = (progress * 1.4 - i * 0.18)
            if 0 < p < 1:
                r = int(40 + p * 200)
                c = ui.mix(th.c("bg_bottom"), th.c("accent"), 1 - p)
                pygame.draw.circle(surf, c, (cx, cy), r, 3)
        # The "R" emblem: a hexagon with a slash
        a = min(1.0, progress * 2)
        size = 46 * (0.6 + 0.4 * a)
        pts = [(cx + size * math.cos(math.radians(60 * k + 30)),
                cy + size * math.sin(math.radians(60 * k + 30))) for k in range(6)]
        pygame.draw.polygon(surf, th.c("panel"), pts)
        pygame.draw.polygon(surf, th.c("accent"), pts, 4)
        ui.text(surf, "R", (cx, cy - 30), 64, th.c("accent"), align="center", bold=True)
        # Wordmark wipes in
        if progress > 0.35:
            reveal = min(1.0, (progress - 0.35) / 0.35)
            word = ui.font(54, True).render(NAME.upper(), True, th.c("text"))
            wr = word.get_rect(midtop=(cx, cy + 62))
            surf.blit(word, wr, area=pygame.Rect(0, 0, int(wr.width * reveal), wr.height))
            pygame.draw.line(surf, th.c("accent"), (wr.left, wr.bottom + 6),
                             (wr.left + int(wr.width * reveal), wr.bottom + 6), 3)
        if progress > 0.7:
            ui.text(surf, f"v{VERSION}  |  RG DS Edition", (cx, cy + 128), 22,
                    th.c("text_dim"), align="center")

    def draw(self, surf):
        self._logo(surf, min(1.0, self.t / (DURATION * 0.85)))

    def draw_top(self, surf):
        th = self.theme
        w, h = surf.get_size()
        p = min(1.0, self.t / DURATION)
        ui.text(surf, "Starting up", (w // 2, h // 2 - 30), 30, th.c("text_dim"), align="center")
        bar = pygame.Rect(w // 4, h // 2 + 10, w // 2, 8)
        pygame.draw.rect(surf, th.c("panel"), bar, border_radius=4)
        pygame.draw.rect(surf, th.c("accent"), (bar.x, bar.y, int(bar.width * p), bar.height), border_radius=4)
