"""Which handheld and which custom firmware Ryzer is running on.

Ryzer runs on any Linux handheld firmware that has Python 3 and SDL2:
ROCKNIX, Knulli/Batocera, muOS, ArkOS and similar. Each keeps its ROMs and
persistent data in a different place; this module knows where.
"""

import os
import platform
from pathlib import Path

# firmware id: (marker paths, writable data root, ROM folders)
FIRMWARES = {
    "rocknix": (["/storage/.config", "/usr/config/rocknix"], "/storage", ["/storage/roms"]),
    "knulli": (["/userdata/system", "/usr/share/batocera"], "/userdata", ["/userdata/roms"]),
    "muos": (["/opt/muos", "/mnt/mmc/MUOS"], "/mnt/mmc", ["/mnt/sdcard/ROMS", "/mnt/mmc/ROMS"]),
    "arkos": (["/opt/system", "/roms/ports"], "/roms", ["/roms", "/roms2"]),
}


def _read(path):
    try:
        return Path(path).read_text(errors="replace").strip("\x00\n ")
    except OSError:
        return ""


def firmware() -> str:
    forced = os.environ.get("RYZER_FIRMWARE")
    if forced:
        return forced
    for fw, (markers, _, _) in FIRMWARES.items():
        if any(Path(m).exists() for m in markers):
            return fw
    return "desktop"


def model() -> str:
    """Human-readable device name, e.g. 'Anbernic RG DS'."""
    for p in ("/proc/device-tree/model", "/sys/firmware/devicetree/base/model", "/sys/class/dmi/id/product_name"):
        name = _read(p)
        if name:
            return name
    return f"{platform.system()} {platform.machine()}"


def data_root():
    fw = firmware()
    if fw in FIRMWARES:
        root = Path(FIRMWARES[fw][1])
        if root.is_dir() and os.access(root, os.W_OK):
            return root
    return None


def rom_dirs() -> list:
    fw = firmware()
    candidates = list(FIRMWARES.get(fw, ((), "", []))[2])
    candidates += ["/storage/roms", "/userdata/roms", "/mnt/sdcard/ROMS", "/mnt/mmc/ROMS", "/roms", "/roms2",
                   "/mnt/SDCARD/Roms", "/mnt/sdcard/roms"]
    seen, out = set(), []
    for c in candidates:
        p = Path(c)
        if p.is_dir() and p.resolve() not in seen:
            seen.add(p.resolve())
            out.append(p)
    return out
