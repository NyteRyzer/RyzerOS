"""User settings (config.json) and play state (state.json)."""

import time

from ryzer import paths
from ryzer.storage import deep_merge, load_json, save_json

DEFAULTS = {
    "theme": "ryzer-red",
    "sound": True,
    "volume": 70,
    "clock_24h": False,
    "show_battery": True,
    "clean_names": True,       # strip "(USA) [!]" tags from game names
    "hide_empty_systems": True,
    "boot_animation": True,
    "animations": True,
    "screen_layout": "auto",   # auto | single | dual
    "default_profile": "balanced",
    "system_profiles": {       # per-system performance profile overrides
        "3ds": "turbo",
        "gc": "turbo",
        "ps2": "turbo",
        "psp": "performance",
        "n64": "performance",
        "nds": "performance",
    },
    "sleep_minutes": 0,        # 0 = never dim
    "fps": 30,                 # UI frame cap; the menu doesn't need 60 and this saves battery
    "input": {
        "keys": {
            "up": ["up"], "down": ["down"], "left": ["left"], "right": ["right"],
            "a": ["return", "z"], "b": ["backspace", "x"], "x": ["c"], "y": ["v"],
            "l": ["q", "page up"], "r": ["e", "page down"],
            "start": ["s"], "select": ["a"],
        },
        # Raw joystick fallback (used only when SDL has no controller mapping)
        "joy_buttons": {"a": 0, "b": 1, "x": 2, "y": 3, "l": 4, "r": 5,
                        "select": 6, "start": 7},
        "swap_ab": False,      # Nintendo-style confirm on the right button
    },
}

STATE_DEFAULTS = {"favorites": [], "recent": [], "playtime": {}, "launches": {}}


class Config:
    def __init__(self):
        self.data = deep_merge(DEFAULTS, load_json(paths.config_file(), {}))

    def __getitem__(self, key):
        return self.data[key]

    def get(self, key, default=None):
        return self.data.get(key, default)

    def set(self, key, value):
        self.data[key] = value
        self.save()

    def save(self):
        save_json(paths.config_file(), self.data)

    def profile_for(self, system_id: str) -> str:
        return self.data["system_profiles"].get(system_id, self.data["default_profile"])


class State:
    """Favorites, recently played and play time. Games are keyed by ROM path."""

    MAX_RECENT = 30

    def __init__(self):
        self.data = deep_merge(STATE_DEFAULTS, load_json(paths.state_file(), {}))

    def save(self):
        save_json(paths.state_file(), self.data)

    def is_favorite(self, path: str) -> bool:
        return path in self.data["favorites"]

    def toggle_favorite(self, path: str) -> bool:
        favs = self.data["favorites"]
        if path in favs:
            favs.remove(path)
        else:
            favs.append(path)
        self.save()
        return path in favs

    def record_play(self, path: str, seconds: float) -> None:
        recent = [p for p in self.data["recent"] if p != path]
        recent.insert(0, path)
        self.data["recent"] = recent[: self.MAX_RECENT]
        self.data["playtime"][path] = self.data["playtime"].get(path, 0) + int(seconds)
        self.data["launches"][path] = self.data["launches"].get(path, 0) + 1
        self.data["last_played"] = {**self.data.get("last_played", {}), path: int(time.time())}
        self.save()

    def playtime(self, path: str) -> int:
        return self.data["playtime"].get(path, 0)

    def total_playtime(self) -> int:
        return sum(self.data["playtime"].values())


def format_duration(seconds: int) -> str:
    h, rem = divmod(int(seconds), 3600)
    m = rem // 60
    if h:
        return f"{h}h {m:02d}m"
    return f"{m}m"
