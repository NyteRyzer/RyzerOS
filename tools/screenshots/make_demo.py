"""Builds a sample library for the screenshot workflow.

    python3 tools/screenshots/make_demo.py OUT_DIR

Writes OUT_DIR/ROMs (empty placeholder ROMs with generated cover art), OUT_DIR/RyzerOS/state.json
(play history, ratings, milestones) and OUT_DIR/configs/*.json (one config per screenshot).
"""

import hashlib
import json
import random
import sys
import time
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

from PIL import Image, ImageDraw, ImageFont

SD = "/storage/emulated/0"

LIBRARY = {
    "3ds": ("3DS", "#D12228", [
        "Pokemon X (USA).3ds", "Kirby Triple Deluxe (USA).3ds", "Shovel Knight (USA).3ds",
        "Fire Emblem Awakening (USA).3ds", "Mario Kart 7 (USA).3ds", "Zelda - A Link Between Worlds (USA).3ds"]),
    "nds": ("NINTENDO DS", "#8C8C96", [
        "Mario Kart DS (USA).nds", "Professor Layton and the Curious Village (USA).nds",
        "Chrono Trigger (USA).nds", "Phoenix Wright - Ace Attorney (USA).nds", "The World Ends with You (USA).nds"]),
    "gba": ("GAME BOY ADVANCE", "#5B3EB5", [
        "Metroid Fusion (USA).gba", "Advance Wars (USA).gba", "Golden Sun (USA).gba", "Mother 3 (Japan).gba",
        "Castlevania - Aria of Sorrow (USA).gba", "Minish Cap (USA).gba", "Fire Emblem (USA).gba"]),
    "snes": ("SUPER NINTENDO", "#6E5BC8", [
        "Super Metroid (USA).sfc", "Chrono Trigger (USA).sfc", "F-Zero (USA).sfc", "EarthBound (USA).sfc",
        "Donkey Kong Country (USA).sfc", "Super Mario World (USA).sfc"]),
    "psx": ("PLAYSTATION", "#3A4A9C", [
        "Castlevania - Symphony of the Night (USA).chd", "Crash Bandicoot (USA).chd", "Spyro the Dragon (USA).chd",
        "Final Fantasy VII (USA).chd"]),
    "n64": ("NINTENDO 64", "#2F8F3A", ["Super Mario 64 (USA).z64", "Banjo-Kazooie (USA).z64", "F-Zero X (USA).z64"]),
    "gc": ("GAMECUBE", "#5A4FCF", ["Super Monkey Ball (USA).iso", "Ikaruga (Europe).iso", "Metroid Prime (USA).iso"]),
    "ps2": ("PLAYSTATION 2", "#1F3C88", ["Katamari Damacy (USA).iso", "Shadow of the Colossus (USA).iso", "Okami (USA).iso"]),
    "psp": ("PSP", "#30343F", ["Patapon (USA).iso", "Lumines (USA).iso", "Crisis Core (USA).iso"]),
    "gb": ("GAME BOY", "#7C8C3C", ["Tetris (World).gb", "Link's Awakening (USA).gb"]),
    "nes": ("NES", "#B23A3A", ["Mega Man 2 (USA).nes", "Super Mario Bros. 3 (USA).nes", "Kirby's Adventure (USA).nes"]),
    "genesis": ("GENESIS", "#222222", ["Sonic the Hedgehog 2 (World).md", "Streets of Rage 2 (USA).md"]),
    "dreamcast": ("DREAMCAST", "#E8692C", ["Jet Set Radio (USA).chd", "Crazy Taxi (USA).chd"]),
}

BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"


def font(size):
    try:
        return ImageFont.truetype(BOLD, size)
    except OSError:
        return ImageFont.load_default()


def hex_rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def clean(stem):
    import re
    return re.sub(r"\s*[(\[][^)\]]*[)\]]", "", stem).strip()


def cover(path: Path, title: str, system_label: str, color: str):
    """A stylised placeholder cover: gradient, shapes, title. Not real box art."""
    w, h = 360, 480
    seed = int(hashlib.md5(title.encode()).hexdigest(), 16)
    rnd = random.Random(seed)
    base = hex_rgb(color)
    hue2 = tuple(min(255, max(0, c + rnd.randint(-90, 90))) for c in base)
    img = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(img)
    for y in range(h):
        t = y / h
        d.line([(0, y), (w, y)], fill=tuple(int(base[i] * (1 - t) + hue2[i] * t * 0.6) for i in range(3)))
    for _ in range(6):
        r = rnd.randint(40, 160)
        x, y = rnd.randint(-40, w + 40), rnd.randint(60, h + 40)
        a = tuple(min(255, c + 60) for c in hue2)
        d.ellipse([x - r, y - r, x + r, y + r], outline=a, width=rnd.randint(3, 10))
    d.rectangle([0, 0, w, 44], fill=(15, 15, 20))
    d.text((14, 10), system_label, font=font(20), fill=(240, 240, 240))
    size = 38
    while True:
        f = font(size)
        lines, line = [], ""
        for wd in title.upper().split():
            trial = (line + " " + wd).strip()
            if line and d.textlength(trial, font=f) > w - 36:
                lines.append(line)
                line = wd
            else:
                line = trial
        lines.append(line)
        if size <= 22 or (max(d.textlength(ln, font=f) for ln in lines) <= w - 36 and len(lines) <= 4):
            break
        size -= 2
    y = h - 36 - len(lines) * (size + 8)
    for ln in lines:
        d.text((20, y + 2), ln, font=f, fill=(0, 0, 0))
        d.text((18, y), ln, font=f, fill=(255, 255, 255))
        y += size + 8
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)


BASE_CONFIG = {
    "theme": "ryzer-red", "sound": False, "boot": False, "animations": True, "fps": 30, "dual": "auto",
    "userName": "Nyte", "resumePrompt": False, "screensaver": 0, "batterySaver": False, "music": False,
    "seasonal": True, "live": "off", "homeLayout": "3ds", "uiVersion": 2, "launchAnim": False, "neon": False,
    "transitions": False, "autoArt": False, "dailyGoal": 90, "clockStyle": "digital", "iconRows": 3, "iconShape": "rounded", "grid": False,
}

CONFIGS = {
    "home": {},
    "home-neon": {"theme": "vaporwave", "live": "aurora", "neon": True, "iconShape": "circle"},
    "home-gold": {"theme": "bonus-gold", "live": "fireflies", "iconShape": "hexagon"},
    "home-ribbon": {"theme": "crimson-steel", "homeLayout": "ribbon", "clockStyle": "analog"},
    "games": {"theme": "midnight-blue"},
    "grid": {"theme": "cyberpunk", "grid": True},
    "themes": {"theme": "sunset"},
    "studio": {"theme": "ocean-deep"},
    "stats": {"theme": "ryzer-red"},
    "notifications": {"theme": "arctic"},
    "tools": {"theme": "gameboy", "clockStyle": "binary"},
    "2048": {"theme": "cherry-blossom"},
    "calendar": {"theme": "forest"},
    "quick": {"theme": "carbon", "clockStyle": "words"},
    "roulette": {"theme": "lava"},
    "land-home": {"theme": "purple-haze", "live": "starfield"},
    "land-games": {"theme": "gold-rush"},
    "artcheck": {"autoArt": True, "artWifiOnly": False},
    "wrapped": {"theme": "neon-night"},
    "checkup": {"theme": "ocean-deep"},
    "swapcheck": {"companion": True, "gameScreen": "main"},
}


def main(out: Path):
    now = int(time.time() * 1000)
    day = 86_400_000
    rnd = random.Random(7)
    keys = []
    for sysid, (label, color, games) in LIBRARY.items():
        for g in games:
            f = out / "ROMs" / sysid / g
            f.parent.mkdir(parents=True, exist_ok=True)
            f.write_bytes(b"\0" * 1024)
            stem = Path(g).stem
            cover(out / "ROMs" / sysid / "images" / f"{stem}.png", clean(stem), label, color)
            keys.append(f"{SD}/ROMs/{sysid}/{g}")

    def key(sysid, name):
        return next(k for k in keys if f"/{sysid}/" in k and k.split("/")[-1].startswith(name))

    played = {
        key("3ds", "Fire Emblem"): 14 * 3600, key("gba", "Metroid Fusion"): 9 * 3600, key("snes", "Super Metroid"): 7 * 3600,
        key("psx", "Castlevania"): 11 * 3600, key("nds", "Chrono"): 6 * 3600, key("ps2", "Okami"): 5 * 3600,
        key("gba", "Golden Sun"): 4 * 3600, key("n64", "Super Mario 64"): 3 * 3600, key("gc", "Metroid Prime"): 8 * 3600,
        key("3ds", "Shovel"): 2 * 3600, key("psp", "Patapon"): 3 * 3600, key("snes", "EarthBound"): 6 * 3600,
    }
    recent = [key("3ds", "Fire Emblem"), key("psx", "Castlevania"), key("gba", "Metroid Fusion"), key("gc", "Metroid Prime"),
              key("snes", "EarthBound"), key("nds", "Chrono"), key("ps2", "Okami")]
    ratings = {k: rnd.choice([4, 5, 5, 3]) for k in played}
    status = {recent[0]: "playing", recent[1]: "playing", key("snes", "Super Metroid"): "completed",
              key("gba", "Golden Sun"): "beaten", key("ps2", "Shadow"): "backlog", key("gc", "Ikaruga"): "backlog",
              key("nds", "The World"): "backlog"}
    daily = {}
    zone = ZoneInfo("America/Chicago")   # same zone as the emulator (capture.py)
    for i in range(0, 50):
        if i < 12 or rnd.random() < 0.6:
            d = datetime.fromtimestamp(now / 1000 - i * 86400, zone)
            daily[d.strftime("%Y-%m-%d")] = rnd.randint(900, 3 * 3600 + 600)
    sessions = []
    for i in range(30):
        k = rnd.choice(list(played))
        sessions.append([k, now - i * day // 2 - rnd.randint(0, 3_600_000), rnd.randint(600, 7200)])
    first_seen = {k: now - 40 * day for k in keys}
    for k in [key("3ds", "Zelda"), key("gba", "Minish"), key("dreamcast", "Jet Set"), key("snes", "Donkey")]:
        first_seen[k] = now - day
    state = {
        "favorites": [recent[0], recent[1], key("snes", "Super Metroid"), key("gba", "Mother 3"), key("n64", "Super Mario 64")],
        "recent": recent, "playtime": played, "launches": {k: rnd.randint(3, 40) for k in played},
        "ratings": ratings, "status": status, "titles": {}, "gameEmu": {}, "hidden": [],
        "collections": {"Couch co-op": [key("3ds", "Mario Kart"), key("nds", "Mario Kart")],
                        "Metroidvanias": [key("gba", "Metroid Fusion"), key("snes", "Super Metroid"), key("psx", "Castlevania"),
                                          key("gba", "Castlevania")]},
        "daily": daily,
        "milestones": ["first", "hour", "ten", "fifty", "launch100", "systems5", "streak7", "collection", "critic", "beaten"],
        "notes": {recent[0]: "Chapter 12 - recruit Gaius before turn 5"},
        "pins": [key("ps2", "Katamari")],
        "sessions": sessions, "bestScores": {"snake": 41, "2048": 5120}, "firstSeen": first_seen,
        "pendingKey": "", "pendingStart": 0,
    }
    # A multi-track disc (one game, not two), a BIOS file (hidden) and an ES-DE style gamelist (details)
    psx = out / "ROMs" / "psx"
    (psx / "Ape Escape (USA).bin").write_bytes(b"\0" * 2048)
    (psx / "Ape Escape (USA).cue").write_text('FILE "Ape Escape (USA).bin" BINARY\n  TRACK 01 MODE2/2352\n    INDEX 01 00:00:00\n')
    cover(psx / "images" / "Ape Escape (USA).png", "Ape Escape", "PLAYSTATION", "#3A4A9C")
    (out / "ROMs" / "gba" / "gba_bios.bin").write_bytes(b"\0" * 1024)
    facts = {
        "Metroid Fusion (USA).gba": ("2002", "Nintendo R&D1", "Action, Platform", "Samus explores a research station overrun by the shape-shifting X parasite."),
        "Advance Wars (USA).gba": ("2001", "Intelligent Systems", "Strategy", "Turn-based battles across islands, commanding infantry, tanks and aircraft."),
        "Golden Sun (USA).gba": ("2001", "Camelot", "Role-playing", "Young adepts set out to stop the lighthouses of Weyard from being lit."),
        "Fire Emblem (USA).gba": ("2003", "Intelligent Systems", "Tactical RPG", "Lead a band of knights across Elibe; fallen units are gone for good."),
        "Castlevania - Aria of Sorrow (USA).gba": ("2003", "Konami", "Action, Exploration", "Soma Cruz explores a castle inside a solar eclipse, absorbing enemy souls."),
        "Minish Cap (USA).gba": ("2004", "Flagship / Capcom", "Action-adventure", "Link shrinks to the size of the Minish to save Hyrule."),
        "Mother 3 (Japan).gba": ("2006", "Brownie Brown / HAL", "Role-playing", "A family's story in the Nowhere Islands, told in eight chapters."),
    }
    lines = ["<?xml version=\"1.0\"?>", "<gameList>"]
    for f, (year, dev, genre, desc) in facts.items():
        lines += ["  <game>", f"    <path>./{f}</path>", f"    <name>{Path(f).stem}</name>", f"    <desc>{desc}</desc>",
                  f"    <releasedate>{year}0101T000000</releasedate>", f"    <developer>{dev.replace('&', '&amp;')}</developer>",
                  f"    <genre>{genre}</genre>", "    <players>1</players>", "  </game>"]
    lines.append("</gameList>")
    (out / "ROMs" / "gba" / "gamelist.xml").write_text("\n".join(lines))
    (out / "RyzerOS").mkdir(parents=True, exist_ok=True)
    (out / "RyzerOS" / "state.json").write_text(json.dumps(state, indent=1))
    (out / "configs").mkdir(exist_ok=True)
    for name, extra in CONFIGS.items():
        (out / "configs" / f"{name}.json").write_text(json.dumps({**BASE_CONFIG, **extra}, indent=1))
    print(f"{len(keys)} games, {len(CONFIGS)} configs in {out}")


if __name__ == "__main__":
    main(Path(sys.argv[1]))
