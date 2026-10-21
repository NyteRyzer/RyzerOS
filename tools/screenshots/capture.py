"""Installs Ryzer OS on a running emulator and screenshots each screen.

    python3 tools/screenshots/capture.py APK DEMO_DIR OUT_DIR

The emulator should be 960x1440 (portrait): Ryzer splits a tall screen into a top and a
bottom screen, like a DS. The last shots rotate it to landscape (a single-screen handheld).
"""

import subprocess
import sys
import time
from pathlib import Path

PKG = "com.ryzer.os"
KEY = {"up": 19, "down": 20, "left": 21, "right": 22, "a": 96, "b": 97, "x": 99, "y": 100, "l": 102, "r": 103}

# name, config, showcase steps, keys pressed afterwards, extra wait
SHOTS = [
    ("home", "home", "notices", "", 0),
    ("home-system", "home", "", "right right right right down", 0),
    ("home-neon", "home-neon", "", "right right right", 0),
    ("home-gold", "home-gold", "", "right right right right right right down", 0),
    ("home-ribbon", "home-ribbon", "", "right right", 0),
    ("games", "games", "games:gba", "down down", 0),
    ("grid", "grid", "games:snes", "right", 0),
    ("themes", "themes", "themes", "right right", 0),
    ("studio", "studio", "studio", "down down down down", 0),
    ("stats", "stats", "stats", "", 0),
    ("notifications", "notifications", "notices,notifications", "", 0),
    ("tools", "tools", "tools", "", 0),
    ("2048", "2048", "2048", "left up left up right up left up left down left up left up right up left left up", 0),
    ("calendar", "calendar", "calendar", "", 0),
    ("quick", "quick", "quick", "", 0),
    ("roulette", "roulette", "roulette", "", 9),
    ("foryou", "games", "foryou", "", 0),
]
LANDSCAPE = [
    ("land-home", "land-home", "", "right", 0),
    ("land-games", "land-games", "games:psx", "down", 0),
]


def adb(*args, out=None, check=False):
    print("adb", *args, flush=True)
    return subprocess.run(["adb", *args], stdout=out, check=check)


def shell(cmd):
    return adb("shell", cmd)


def snap(path: Path):
    with open(path, "wb") as f:
        adb("exec-out", "screencap", "-p", out=f)


def start(config: str, demo: Path, steps: str):
    shell(f"am force-stop {PKG}")
    adb("push", str(demo / "configs" / f"{config}.json"), "/sdcard/RyzerOS/config.json")
    cmd = f"am start -n {PKG}/.MainActivity"
    if steps:
        cmd += f" --es showcase '{steps}'"
    shell(cmd)


def run(shots, demo: Path, out: Path):
    for name, config, steps, keys, wait in shots:
        start(config, demo, steps)
        time.sleep(7)
        for k in keys.split():
            shell(f"input keyevent {KEY[k]}")
            time.sleep(0.5)
        time.sleep(2 + wait)
        snap(out / f"{name}.png")


def main(apk: str, demo: Path, out: Path):
    out.mkdir(parents=True, exist_ok=True)
    adb("wait-for-device")
    adb("install", "-r", "-g", apk, check=True)
    shell(f"appops set --uid {PKG} MANAGE_EXTERNAL_STORAGE allow")
    shell("settings put secure immersive_mode_confirmations confirmed")
    shell(f"cmd notification allow_listener {PKG}/{PKG}.RyzerNotificationService")
    shell("settings put system screen_off_timeout 1800000")
    shell("svc power stayon true")
    adb("push", str(demo / "ROMs"), "/sdcard/")
    shell("mkdir -p /sdcard/RyzerOS")
    adb("push", str(demo / "RyzerOS" / "state.json"), "/sdcard/RyzerOS/state.json")
    # Warm-up start: first launch scans the library and writes its files
    start("home", demo, "")
    time.sleep(12)

    # Boot animation, caught part-way through
    start("boot", demo, "")
    for i, delay in enumerate([1.2, 0.8, 0.8]):
        time.sleep(delay)
        snap(out / f"boot-{i}.png")
    time.sleep(3)

    run(SHOTS, demo, out)

    shell("settings put system accelerometer_rotation 0")
    shell("settings put system user_rotation 1")
    time.sleep(3)
    run(LANDSCAPE, demo, out)
    shell("settings put system user_rotation 0")
    shell("cat /sdcard/RyzerOS/crash.txt")   # printed to the log, if Ryzer hit an error


if __name__ == "__main__":
    main(sys.argv[1], Path(sys.argv[2]), Path(sys.argv[3]))
