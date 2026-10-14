"""Render Ryzer OS screens to PNG without a display (for docs and testing).

    python3 tools/preview.py out_dir [--theme ID] [--dual]

Uses a throwaway RYZER_HOME with a fake ROM library.
"""

import argparse
import os
import sys
import tempfile
from pathlib import Path

os.environ.setdefault("SDL_VIDEODRIVER", "dummy")
os.environ.setdefault("SDL_AUDIODRIVER", "dummy")
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

DEMO = {
    "3ds": ["Pokemon X (USA).3ds", "Kirby Triple Deluxe (USA).3ds", "Shovel Knight.3ds"],
    "gamecube": ["Super Monkey Ball (USA).iso", "Ikaruga (Europe).iso"],
    "ps2": ["Katamari Damacy (USA).iso"],
    "nds": ["Mario Kart DS (USA).nds", "Professor Layton (USA).nds", "Chrono Trigger (USA).nds"],
    "gba": ["Metroid Fusion (USA).gba", "Advance Wars (USA).gba", "Golden Sun (USA).gba", "Mother 3 (Japan).gba"],
    "snes": ["Super Metroid (USA).sfc", "Chrono Trigger (USA).sfc", "F-Zero (USA).sfc"],
    "psx": ["Castlevania SOTN (USA).chd", "Crash Bandicoot (USA).chd"],
    "n64": ["Super Mario 64 (USA).z64"],
}


def make_library(root: Path):
    for folder, names in DEMO.items():
        d = root / folder
        d.mkdir(parents=True, exist_ok=True)
        for n in names:
            (d / n).write_bytes(b"\0" * 16)


def build_app(dual=False, theme=None):
    home = Path(tempfile.mkdtemp(prefix="ryzer-preview-"))
    os.environ["RYZER_HOME"] = str(home)
    os.environ["RYZER_ROMS"] = str(home / "roms")
    make_library(home / "roms")
    from ryzer.app import App, parse_args
    args = parse_args(["--windowed", "--dry-run", "--no-boot", "--layout", "dual" if dual else "single"])
    app = App(args)
    if theme:
        app.set_theme(theme, save=False)
    return app


def snap(app, path, frames=8):
    import pygame
    for _ in range(frames):
        if app.current:
            app.current.update(1 / 30)
        app.render(1 / 30, 1000.0)
    pygame.image.save(app.screen, str(path))


def main():
    p = argparse.ArgumentParser()
    p.add_argument("out")
    p.add_argument("--theme")
    p.add_argument("--dual", action="store_true")
    a = p.parse_args()
    out = Path(a.out)
    out.mkdir(parents=True, exist_ok=True)
    app = build_app(a.dual, a.theme)
    from ryzer.screens.boot import BootScreen
    from ryzer.screens.home import HomeScreen
    home = HomeScreen(app)
    boot = BootScreen(app, home)
    app.push(boot)
    boot.t = 1.9
    snap(app, out / "00-boot.png", 1)
    app.stack[:] = [home]
    app.state.record_play(app.systems[0].games[0].key, 5400)
    home.rebuild()
    for _ in range(4):
        home.handle("right")
    snap(app, out / "01-home.png", 30)
    home.handle("a")
    app.current.handle("down")
    snap(app, out / "02-games.png")
    app.current.handle("start")
    snap(app, out / "03-game-options.png")
    app.home()
    app.open_themes()
    snap(app, out / "04-themes.png")
    app.current.handle("x")
    snap(app, out / "05-theme-editor.png")
    app.home()
    app.open_settings()
    snap(app, out / "06-settings.png")
    app.current.handle("a")
    snap(app, out / "07-performance.png")
    app.home()
    app.open_search()
    for k in ("a", "right", "a"):
        app.current.handle(k)
    snap(app, out / "08-search.png")
    print(f"wrote screenshots to {out}")


if __name__ == "__main__":
    main()
