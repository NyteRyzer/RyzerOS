"""Filesystem locations.

On the device everything lives under /storage/ryzer (the persistent partition on
ROCKNIX-style firmwares). On a desktop it falls back to ~/.ryzer so the UI can be
previewed and edited without a handheld. Every path can be overridden with an
environment variable.
"""

import os
from pathlib import Path

PACKAGE_DIR = Path(__file__).resolve().parent
BUILTIN_THEMES_DIR = PACKAGE_DIR / "themes"
DATA_DIR = PACKAGE_DIR / "data"


def _default_home() -> Path:
    if Path("/storage").is_dir() and os.access("/storage", os.W_OK):
        return Path("/storage/ryzer")
    return Path.home() / ".ryzer"


def home() -> Path:
    return Path(os.environ.get("RYZER_HOME", _default_home()))


def roms_dir() -> Path:
    if "RYZER_ROMS" in os.environ:
        return Path(os.environ["RYZER_ROMS"])
    for candidate in ("/storage/roms", "/roms", "/mnt/sdcard/roms"):
        if Path(candidate).is_dir():
            return Path(candidate)
    return home() / "roms"


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
