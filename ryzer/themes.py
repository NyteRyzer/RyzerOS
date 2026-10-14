"""Theme loading, validation and saving.

A theme is a JSON file with a `colors` table and a `style` table. Built-in themes
ship in ryzer/themes/; user themes (made with the on-device editor or by hand)
live in <RYZER_HOME>/themes/ and override a built-in with the same id.
"""

import re
from dataclasses import dataclass, field
from pathlib import Path

from ryzer import paths
from ryzer.storage import load_json, save_json

COLOR_KEYS = (
    "bg_top", "bg_bottom", "panel", "panel_border", "accent", "accent2",
    "text", "text_dim", "highlight_text", "good", "warn", "bad",
)

BACKGROUNDS = ("gradient", "scanlines", "grid", "dots", "waves", "stars")
TILE_STYLES = ("glow", "flat", "outline")

STYLE_DEFAULTS = {
    "corner_radius": 12,
    "tile_style": "glow",
    "background": "gradient",
    "font_scale": 1.0,
    "particles": False,
}

_HEX = re.compile(r"^#?([0-9a-fA-F]{6})$")


def parse_color(value) -> tuple:
    m = _HEX.match(str(value).strip())
    if not m:
        raise ValueError(f"bad color {value!r}, expected #RRGGBB")
    h = m.group(1)
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def to_hex(rgb) -> str:
    return "#{:02X}{:02X}{:02X}".format(*rgb)


@dataclass
class Theme:
    id: str
    name: str
    colors: dict
    style: dict = field(default_factory=lambda: dict(STYLE_DEFAULTS))
    author: str = "Ryzer"
    builtin: bool = True

    def c(self, key: str) -> tuple:
        return self.colors[key]

    @classmethod
    def from_dict(cls, data: dict, builtin: bool) -> "Theme":
        missing = [k for k in COLOR_KEYS if k not in data.get("colors", {})]
        if missing:
            raise ValueError(f"theme {data.get('id')} missing colors: {', '.join(missing)}")
        colors = {k: parse_color(data["colors"][k]) for k in COLOR_KEYS}
        style = {**STYLE_DEFAULTS, **data.get("style", {})}
        if style["background"] not in BACKGROUNDS:
            style["background"] = "gradient"
        if style["tile_style"] not in TILE_STYLES:
            style["tile_style"] = "glow"
        style["corner_radius"] = max(0, min(32, int(style["corner_radius"])))
        style["font_scale"] = max(0.8, min(1.4, float(style["font_scale"])))
        return cls(
            id=str(data["id"]),
            name=str(data.get("name", data["id"])),
            colors=colors,
            style=style,
            author=str(data.get("author", "Ryzer")),
            builtin=builtin,
        )

    def to_dict(self) -> dict:
        return {
            "id": self.id,
            "name": self.name,
            "author": self.author,
            "colors": {k: to_hex(v) for k, v in self.colors.items()},
            "style": dict(self.style),
        }

    def copy(self, new_id: str, new_name: str) -> "Theme":
        return Theme(new_id, new_name, dict(self.colors), dict(self.style),
                     author="You", builtin=False)


def _load_dir(directory: Path, builtin: bool) -> dict:
    themes = {}
    if not directory.is_dir():
        return themes
    for f in sorted(directory.glob("*.json")):
        try:
            t = Theme.from_dict(load_json(f, {}), builtin)
        except (ValueError, KeyError, TypeError):
            continue  # a broken user theme must never stop the UI from booting
        themes[t.id] = t
    return themes


def load_all() -> dict:
    themes = _load_dir(paths.BUILTIN_THEMES_DIR, builtin=True)
    themes.update(_load_dir(paths.user_themes_dir(), builtin=False))
    return themes


def save_user_theme(theme: Theme) -> Path:
    path = paths.user_themes_dir() / f"{theme.id}.json"
    save_json(path, theme.to_dict())
    return path


def delete_user_theme(theme: Theme) -> None:
    if theme.builtin:
        return
    (paths.user_themes_dir() / f"{theme.id}.json").unlink(missing_ok=True)


def next_custom_id(themes: dict) -> tuple:
    n = 1
    while f"custom-{n}" in themes:
        n += 1
    return f"custom-{n}", f"Custom {n}"
