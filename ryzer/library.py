"""Game library: system definitions and ROM scanning."""

import re
from dataclasses import dataclass, field
from pathlib import Path

from ryzer import paths
from ryzer.storage import load_json
from ryzer.themes import parse_color

TIER_LABELS = {
    "great": "Full speed",
    "good": "Runs well",
    "playable": "Mostly playable",
    "limited": "Light games only",
    "experimental": "Experimental",
}

_TAGS = re.compile(r"\s*[\(\[][^\)\]]*[\)\]]")
_IMAGE_DIRS = ("images", "media/images", "boxart", "media/box2dfront", "snap")


def clean_name(stem: str) -> str:
    name = _TAGS.sub("", stem).replace("_", " ").strip()
    return name or stem


@dataclass
class System:
    id: str
    name: str
    short: str
    folders: list
    extensions: list
    tier: str
    note: str
    color: tuple
    commands: list
    games: list = field(default_factory=list)

    @property
    def tier_label(self) -> str:
        return TIER_LABELS.get(self.tier, self.tier)


@dataclass
class Game:
    path: Path
    system: System
    clean: bool = True

    @property
    def name(self) -> str:
        return clean_name(self.path.stem) if self.clean else self.path.stem

    @property
    def key(self) -> str:
        return str(self.path)

    def image(self):
        base = self.path.parent
        for sub in _IMAGE_DIRS:
            for ext in (".png", ".jpg"):
                for stem in (self.path.stem, f"{self.path.stem}-image"):
                    p = base / sub / f"{stem}{ext}"
                    if p.is_file():
                        return p
        return None


def load_systems() -> list:
    src = paths.systems_override_file()
    data = load_json(src, None) if src.is_file() else None
    if not data or "systems" not in data:
        data = load_json(paths.DATA_DIR / "systems.json", {"systems": []})
    systems = []
    for s in data["systems"]:
        try:
            systems.append(System(
                id=s["id"], name=s["name"], short=s.get("short", s["id"].upper()),
                folders=list(s["folders"]),
                extensions=[e.lower() for e in s["extensions"]],
                tier=s.get("tier", "good"), note=s.get("note", ""),
                color=parse_color(s.get("color", "#888888")),
                commands=list(s.get("commands", [])),
            ))
        except (KeyError, ValueError):
            continue
    return systems


def scan(systems: list, roms_roots, clean_names: bool = True) -> None:
    """Fill each system's game list from one or more ROM roots. Folder names match
    case-insensitively (muOS uses ROMS/3DS, ROCKNIX uses roms/3ds). Only one level of
    subfolders is scanned so a huge SD card doesn't stall the boot."""
    if isinstance(roms_roots, (str, Path)):
        roms_roots = [roms_roots]
    folders_by_root = {}
    for root in map(Path, roms_roots):
        try:
            folders_by_root[root] = {d.name.lower(): d for d in root.iterdir() if d.is_dir()}
        except OSError:
            continue
    for system in systems:
        found = {}
        for root, folders in folders_by_root.items():
            for folder in system.folders:
                d = folders.get(folder.lower())
                if d is None:
                    continue
                candidates = list(d.iterdir())
                for sub in [c for c in candidates if c.is_dir() and not c.name.startswith(".")]:
                    if sub.name.lower() in {"images", "media", "boxart", "snap", "videos", "manuals"}:
                        continue
                    try:
                        candidates.extend(sub.iterdir())
                    except OSError:
                        pass
                for f in candidates:
                    if f.is_file() and f.suffix.lower() in system.extensions and not f.name.startswith("."):
                        found[str(f)] = Game(f, system, clean_names)
        system.games = sorted(found.values(), key=lambda g: g.name.lower())


def find_game(systems: list, key: str):
    for s in systems:
        for g in s.games:
            if g.key == key:
                return g
    return None


def search(systems: list, query: str) -> list:
    q = query.lower().strip()
    if not q:
        return []
    return [g for s in systems for g in s.games if q in g.name.lower()]
