"""Drawing helpers shared by every screen: backgrounds, panels, text, bars."""

import math
import random
import time

import pygame

from ryzer import perf

_fonts = {}
_bg_cache = {}


def reset_caches():
    """Forget fonts/images; needed whenever pygame itself is re-initialised."""
    _fonts.clear()
    _bg_cache.clear()
    _image_cache.clear()


def font(size: int, bold=False) -> pygame.font.Font:
    key = (size, bold)
    if key not in _fonts:
        f = pygame.font.Font(None, size)
        f.set_bold(bold)
        _fonts[key] = f
    return _fonts[key]


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def text(surf, s, pos, size, color, align="left", bold=False, max_width=None, theme=None):
    if theme is not None:
        size = int(size * theme.style["font_scale"])
    f = font(size, bold)
    s = str(s)
    if max_width and f.size(s)[0] > max_width:
        while s and f.size(s + "...")[0] > max_width:
            s = s[:-1]
        s += "..."
    img = f.render(s, True, color)
    r = img.get_rect()
    x, y = pos
    if align == "center":
        r.midtop = (x, y)
    elif align == "right":
        r.topright = (x, y)
    else:
        r.topleft = (x, y)
    surf.blit(img, r)
    return r


def background(surf, theme, t):
    w, h = surf.get_size()
    key = (theme.id, w, h, tuple(theme.colors.values()), theme.style["background"])
    base = _bg_cache.get(key)
    if base is None:
        base = pygame.Surface((w, h))
        top, bottom = theme.c("bg_top"), theme.c("bg_bottom")
        for y in range(h):
            pygame.draw.line(base, mix(top, bottom, y / max(1, h - 1)), (0, y), (w, y))
        line = mix(theme.c("bg_top"), theme.c("accent"), 0.12)
        kind = theme.style["background"]
        if kind == "scanlines":
            for y in range(0, h, 3):
                pygame.draw.line(base, mix(mix(top, bottom, y / h), (0, 0, 0), 0.35), (0, y), (w, y))
        elif kind == "grid":
            for x in range(0, w, 32):
                pygame.draw.line(base, line, (x, h // 2), (w // 2 + (x - w // 2) * 2, h))
            for i in range(8):
                y = h // 2 + int((h // 2) * (i / 8) ** 1.6)
                pygame.draw.line(base, line, (0, y), (w, y))
        elif kind == "dots":
            for y in range(8, h, 20):
                for x in range(8 + (y // 20 % 2) * 10, w, 20):
                    pygame.draw.circle(base, line, (x, y), 1)
        elif kind == "stars":
            rnd = random.Random(7)
            for _ in range(140):
                c = mix(theme.c("bg_top"), theme.c("text"), rnd.random() * 0.6)
                base.set_at((rnd.randrange(w), rnd.randrange(h)), c)
        _bg_cache.clear()
        _bg_cache[key] = base
    surf.blit(base, (0, 0))
    if theme.style["background"] == "waves":
        c = mix(theme.c("bg_bottom"), theme.c("accent"), 0.18)
        for k in range(3):
            pts = [(x, int(h * 0.72 + k * 18 + math.sin(x / 70 + t * (0.6 + k * 0.2) + k) * 10))
                   for x in range(0, w + 16, 16)]
            pygame.draw.lines(surf, c, False, pts, 2)


class Particles:
    def __init__(self, n=24):
        self.n = n
        self.items = []

    def draw(self, surf, theme, dt):
        if not theme.style.get("particles"):
            return
        w, h = surf.get_size()
        while len(self.items) < self.n:
            self.items.append([random.uniform(0, w), random.uniform(0, h),
                               random.uniform(6, 22), random.uniform(1, 2.5)])
        c = mix(theme.c("bg_bottom"), theme.c("accent2"), 0.45)
        for p in self.items:
            p[1] -= p[2] * dt
            if p[1] < -4:
                p[0], p[1] = random.uniform(0, w), h + 4
            pygame.draw.circle(surf, c, (int(p[0]), int(p[1])), int(p[3]))


def panel(surf, rect, theme, selected=False, pulse=0.0, fill=None):
    r = theme.style["corner_radius"]
    rect = pygame.Rect(rect)
    style = theme.style["tile_style"]
    if selected and style == "glow":
        for i in range(3, 0, -1):
            glow = rect.inflate(i * 4, i * 4)
            c = mix(theme.c("bg_bottom"), theme.c("accent"), 0.25 + 0.1 * (3 - i) + 0.1 * pulse)
            pygame.draw.rect(surf, c, glow, border_radius=r + i * 2)
    body = fill or (mix(theme.c("panel"), theme.c("accent"), 0.18) if selected else theme.c("panel"))
    if style == "outline" and not fill:
        body = theme.c("panel")
    pygame.draw.rect(surf, body, rect, border_radius=r)
    border = theme.c("accent") if selected else theme.c("panel_border")
    width = 3 if selected else (2 if style == "outline" else 1)
    pygame.draw.rect(surf, border, rect, width, border_radius=r)


def top_bar(surf, theme, title, cfg):
    w = surf.get_width()
    text(surf, title, (16, 10), 26, theme.c("text"), bold=True, theme=theme)
    now = time.localtime()
    clock = time.strftime("%H:%M" if cfg["clock_24h"] else "%I:%M %p", now).lstrip("0")
    x = w - 16
    r = text(surf, clock, (x, 11), 24, theme.c("text"), align="right", theme=theme)
    x = r.left - 14
    if cfg["show_battery"]:
        bat = perf.battery()
        if bat:
            pct, charging = bat
            bw, bh = 30, 14
            br = pygame.Rect(x - bw, 14, bw, bh)
            pygame.draw.rect(surf, theme.c("text_dim"), br, 2, border_radius=3)
            pygame.draw.rect(surf, theme.c("text_dim"), (br.right, br.y + 4, 3, 6))
            col = theme.c("bad") if pct <= 15 else theme.c("warn") if pct <= 30 else theme.c("good")
            pygame.draw.rect(surf, col, (br.x + 3, br.y + 3, int((bw - 6) * pct / 100), bh - 6))
            label = f"{pct}%" + (" +" if charging else "")
            r = text(surf, label, (br.left - 6, 12), 20, theme.c("text_dim"), align="right", theme=theme)
            x = r.left - 12
    if perf.wifi_connected():
        for i in range(3):
            pygame.draw.arc(surf, theme.c("text_dim"), (x - 10 - i * 5, 26 - i * 5, 10 + i * 10, 10 + i * 10),
                            math.radians(45), math.radians(135), 2)
    pygame.draw.line(surf, theme.c("panel_border"), (12, 42), (w - 12, 42))


def button_glyph(surf, theme, label, center):
    pygame.draw.circle(surf, theme.c("accent"), center, 11)
    text(surf, label, (center[0], center[1] - 8), 18, theme.c("highlight_text"), align="center", bold=True)


def hints(surf, theme, items):
    """Bottom bar of button prompts, e.g. [("A", "Play"), ("B", "Back")]."""
    w, h = surf.get_size()
    pygame.draw.line(surf, theme.c("panel_border"), (12, h - 36), (w - 12, h - 36))
    x = w - 16
    for label, action in reversed(items):
        r = text(surf, action, (x, h - 27), 20, theme.c("text_dim"), align="right", theme=theme)
        if len(label) == 1:
            button_glyph(surf, theme, label, (r.left - 16, h - 19))
            x = r.left - 38
        else:
            pill = font(16, True).render(label, True, theme.c("highlight_text"))
            pr = pill.get_rect(midright=(r.left - 12, h - 19)).inflate(10, 6)
            pygame.draw.rect(surf, theme.c("accent"), pr, border_radius=8)
            surf.blit(pill, pill.get_rect(center=pr.center))
            x = pr.left - 14


def toast(surf, theme, message, alpha=1.0):
    w, h = surf.get_size()
    f = font(22)
    tw = min(w - 40, f.size(message)[0] + 32)
    rect = pygame.Rect(0, 0, tw, 40)
    rect.midbottom = (w // 2, h - 46)
    layer = pygame.Surface(rect.size, pygame.SRCALPHA)
    pygame.draw.rect(layer, (*theme.c("panel"), int(235 * alpha)), layer.get_rect(), border_radius=12)
    pygame.draw.rect(layer, (*theme.c("accent"), int(255 * alpha)), layer.get_rect(), 2, border_radius=12)
    surf.blit(layer, rect)
    text(surf, message, (rect.centerx, rect.y + 11), 22, theme.c("text"), align="center", max_width=tw - 24)


def scroll_offset(selected, current, visible, total):
    """Keep `selected` on screen with a little look-ahead; returns new offset."""
    if total <= visible:
        return 0
    if selected < current + 1:
        current = selected - 1
    elif selected > current + visible - 2:
        current = selected - visible + 2
    return max(0, min(total - visible, current))


def scrollbar(surf, theme, rect, offset, visible, total):
    if total <= visible:
        return
    rect = pygame.Rect(rect)
    pygame.draw.rect(surf, theme.c("panel"), rect, border_radius=3)
    h = max(16, rect.height * visible // total)
    y = rect.y + (rect.height - h) * offset // max(1, total - visible)
    pygame.draw.rect(surf, theme.c("accent"), (rect.x, y, rect.width, h), border_radius=3)


_image_cache = {}


def load_image(path, max_size):
    key = (str(path), max_size)
    if key not in _image_cache:
        try:
            img = pygame.image.load(str(path)).convert_alpha()
            iw, ih = img.get_size()
            scale = min(max_size[0] / iw, max_size[1] / ih)
            img = pygame.transform.smoothscale(img, (max(1, int(iw * scale)), max(1, int(ih * scale))))
        except (pygame.error, FileNotFoundError, ZeroDivisionError):
            img = None
        if len(_image_cache) > 64:
            _image_cache.clear()
        _image_cache[key] = img
    return _image_cache[key]
