"""Resolve and run the emulator for a game."""

import os
import shlex
import shutil
import subprocess
import time
from pathlib import Path

from ryzer import perf

CORE_DIRS = ("/tmp/cores", "/usr/lib/libretro", "/usr/lib/aarch64-linux-gnu/libretro",
             str(Path.home() / ".config/retroarch/cores"))


def find_core(core: str):
    for d in CORE_DIRS:
        p = Path(d) / f"{core}_libretro.so"
        if p.is_file():
            return p
    return None


def _retroarch():
    return shutil.which("retroarch")


def resolve(command_templates: list, rom: Path):
    """Return the argv of the first usable command, or None."""
    for template in command_templates:
        if template.startswith("ra:"):
            ra, core = _retroarch(), find_core(template[3:])
            if ra and core:
                return [ra, "-L", str(core), str(rom)]
            continue
        parts = shlex.split(template.replace("{rom}", "\x00"))
        if not parts:
            continue
        program = parts[0]
        if not (shutil.which(program) or (os.path.isabs(program) and os.access(program, os.X_OK))):
            continue
        return [str(rom) if p == "\x00" else p for p in parts]
    return None


def describe(argv) -> str:
    return " ".join(shlex.quote(a) for a in argv) if argv else "no emulator found"


def run(game, profile: str, dry_run: bool = False):
    """Launch the game and block until the emulator exits.

    Returns (ok, seconds_played, message)."""
    argv = resolve(game.system.commands, game.path)
    if argv is None:
        return False, 0, f"No emulator installed for {game.system.name}"
    if dry_run:
        return True, 0, describe(argv)
    perf.apply_profile(profile)
    start = time.monotonic()
    try:
        result = subprocess.run(argv, cwd=str(game.path.parent))
        ok = result.returncode == 0
        msg = "" if ok else f"Emulator exited with code {result.returncode}"
    except OSError as e:
        ok, msg = False, str(e)
    played = time.monotonic() - start
    return ok, played, msg
