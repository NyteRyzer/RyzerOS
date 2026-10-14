"""Filesystem locations.

On a handheld everything lives in a "ryzer" folder on the firmware's persistent
partition (/storage on ROCKNIX, /userdata on Knulli, /mnt/mmc on muOS, /roms on
ArkOS - see device.py). On a desktop it falls back to ~/.ryzer so the UI can be
previewed and edited without a handheld. Every path can be overridden with an
environment variable.
"""

import os
from pathlib import Path

PACKAGE_DIR = Path(__file__).resolve().parent
BUILTIN_THEMES_DIR = PACKAGE_DIR / "themes"
DATA_DIR = PACKAGE_DIR / "data"


def _default_home() -> Path:
    from ryzer import device
    root = device.data_root()
    return root / "ryzer" if root else Path.home() / ".ryzer"


def home() -> Path:
    return Path(os.environ.get("RYZER_HOME", _default_home()))


def roms_dirs() -> list:
    """Every ROM root to scan (internal + SD card on dual-slot devices)."""
    if "RYZER_ROMS" in os.environ:
        return [Path(p) for p in os.environ["RYZER_ROMS"].split(os.pathsep)]
    from ryzer import device
    return device.rom_dirs() or [home() / "roms"]


def roms_dir() -> Path:
    return roms_dirs()[0]


def user_themes_dir() -> Path:
    return home() / "themes"


def config_file() -> Path:
    return home() / "config.json"


def state_file() -> Path:
    return home() / "state.json"


def screenshots_dir() -> Path:
    return home() / "screenshots"


def systems_override_file() -> Path:
    """Optional user copy of systems.json that replaces the built-in one."""
    return home() / "systems.json"


def ensure_dirs() -> None:
    for d in (home(), user_themes_dir(), screenshots_dir()):
        d.mkdir(parents=True, exist_ok=True)
