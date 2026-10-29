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
TZ = "America/Chicago"
KEY = {"up": 19, "down": 20, "left": 21, "right": 22, "a": 96, "b": 97, "x": 99, "y": 100, "l": 102, "r": 103}

# name, config, showcase steps, keys pressed afterwards, extra wait
SHOTS = [
    ("home", "home", "notices", "", 0),
    ("home-system", "home", "", "right right right right right", 0),
    ("home-neon", "home-neon", "", "right right right right right right right down down", 0),
    ("home-gold", "home-gold", "", "right right right right right down down", 0),
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
    ("wrapped", "wrapped", "wrapped", "", 0),
    ("checkup", "checkup", "checkup", "", 0),
    ("blades-settings", "home", "settings", "down", 0),
    ("classic-settings", "classic", "settings", "", 0),
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
    shell("am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS")
    time.sleep(0.4)
    with open(path, "wb") as f:
        adb("exec-out", "screencap", "-p", out=f)


def start(config: str, demo: Path, steps: str):
    shell(f"am force-stop {PKG}")
    adb("push", str(demo / "configs" / f"{config}.json"), "/sdcard/RyzerOS/config.json")
    steps = ",".join(x for x in ("clearsystem", steps) if x)
    cmd = f"am start -n {PKG}/.MainActivity --es showcase '{steps}'"
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


def art_check(demo: Path):
    """End-to-end check of the artwork downloader: games with no picture should get one.
    Results go to the log only (the downloaded pictures are real box art, not committed)."""
    tests = {"snes": ["Super Mario World (USA).sfc", "earthbound.sfc"], "gba": ["the_legend_of_zelda_minish_cap.gba"],
             "psx": ["Spyro the Dragon (USA).chd"], "genesis": ["sonic 2.md"]}
    for sys_id, names in tests.items():
        for n in names:
            shell(f"echo ryzer > '/sdcard/ROMs/{sys_id}/{n}'")
    start("artcheck", demo, "")
    time.sleep(75)
    print("=== ARTWORK CHECK ===", flush=True)
    shell("ls -l /sdcard/RyzerOS/covers/*/")
    shell("cat /sdcard/RyzerOS/crash.txt")
    print("=== END ARTWORK CHECK ===", flush=True)


def physical_ids() -> list:
    """screencap -d wants SurfaceFlinger's display ids (the built-in screen first)."""
    import re
    r = subprocess.run(["adb", "shell", "dumpsys SurfaceFlinger --display-id"], capture_output=True, text=True).stdout
    return re.findall(r"Display (\d+)", r)


def snap_display(path: Path, phys: str):
    with open(path, "wb") as f:
        adb("exec-out", "screencap", "-p", "-d", phys, out=f)


def displays_of_interest():
    """Prints which display each relevant activity is on."""
    r = subprocess.run(["adb", "shell", "dumpsys activity activities"], capture_output=True, text=True).stdout
    for line in r.splitlines():
        s = line.strip()
        if s.startswith("Display #") or ("com.android.settings" in s or "com.ryzer.os" in s) and ("ActivityRecord" in s or "Task{" in s):
            print("   ", s[:200], flush=True)


def swap_check(demo: Path, out: Path):
    """Simulates a second screen, starts a stand-in game (the Settings app) on the main screen with
    Now Playing on the other, swipes the panel, and checks the two traded places."""
    out.mkdir(parents=True, exist_ok=True)
    shell("settings put global overlay_display_devices 1280x960/240")
    time.sleep(5)
    disp = subprocess.run(["adb", "shell", "dumpsys display"], capture_output=True, text=True).stdout
    ids = sorted({int(x) for x in __import__("re").findall(r"mDisplayId=(\d+)", disp)})
    print("=== SWAP CHECK === displays:", ids, flush=True)
    second = next((i for i in ids if i != 0), None)
    if second is None:
        print("no second display; skipping", flush=True)
        return
    start("swapcheck", demo, "playtest:com.android.settings")
    time.sleep(8)
    print("before swap:", flush=True)
    displays_of_interest()
    phys = physical_ids()
    print("physical display ids:", phys, flush=True)
    for i, ph in enumerate(phys[:2]):
        snap_display(out / f"before-{i}.png", ph)
    # swipe down the Now Playing panel on the second display
    shell(f"input -d {second} swipe 640 150 640 820 300")
    time.sleep(6)
    print("after swap:", flush=True)
    displays_of_interest()
    for i, ph in enumerate(phys[:2]):
        snap_display(out / f"after-{i}.png", ph)
    shell("cat /sdcard/RyzerOS/crash.txt")
    print("=== END SWAP CHECK ===", flush=True)
    shell("settings delete global overlay_display_devices")


def tap_text(*labels: str) -> bool:
    """Taps the first on-screen button whose text matches (for Android's own install dialog)."""
    import re
    shell("uiautomator dump /sdcard/ui.xml")
    xml = subprocess.run(["adb", "shell", "cat /sdcard/ui.xml"], capture_output=True, text=True).stdout
    for label in labels:
        m = re.search(r'text="' + re.escape(label) + r'"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml, re.I)
        if m:
            x1, y1, x2, y2 = map(int, m.groups())
            shell(f"input tap {(x1 + x2) // 2} {(y1 + y2) // 2}")
            print(f"tapped '{label}'", flush=True)
            return True
    print("no button among", labels, "| screen text:", re.findall(r'text="([^"]+)"', xml)[:20], flush=True)
    return False


def version_name() -> str:
    r = subprocess.run(["adb", "shell", f"dumpsys package {PKG}"], capture_output=True, text=True).stdout
    import re
    m = re.search(r"versionName=(\S+)", r)
    c = re.search(r"versionCode=(\d+)", r)
    return f"{m.group(1) if m else '?'} (code {c.group(1) if c else '?'})"


def update_check(demo: Path):
    """End-to-end test of the updater: download the real latest release from GitHub and install
    it over this test build through Android's installer."""
    print("=== UPDATE CHECK === installed before:", version_name(), flush=True)
    shell(f"appops set {PKG} REQUEST_INSTALL_PACKAGES allow")
    start("swapcheck", demo, "updatetest")
    time.sleep(40)                      # check + download (~5 MB)
    shell(f"input keyevent {KEY['a']}")  # Install
    time.sleep(6)
    for _ in range(3):                   # Android's confirmation (first self-update only)
        if tap_text("Update", "Install", "UPDATE", "INSTALL"):
            break
        time.sleep(4)
    time.sleep(20)
    print("installed after:", version_name(), flush=True)
    shell("cat /sdcard/RyzerOS/crash.txt")
    print("=== END UPDATE CHECK ===", flush=True)


def main(apk: str, demo: Path, out: Path):
    out.mkdir(parents=True, exist_ok=True)
    adb("wait-for-device")
    # Evening in the US, so the greeting reads "Good evening" (the sample play history uses this zone too)
    adb("root")
    time.sleep(3)
    adb("wait-for-device")
    shell("settings put global auto_time_zone 0")
    shell(f"setprop persist.sys.timezone {TZ}")
    shell(f"service call alarm 3 s16 {TZ}")
    adb("install", "-r", "-g", apk, check=True)
    # Ryzer becomes the home app, so the stock launcher can't pop up "isn't responding" dialogs
    shell(f"cmd package set-home-activity {PKG}/.MainActivity")
    for launcher in ("com.google.android.apps.nexuslauncher", "com.android.launcher3"):
        shell(f"pm disable-user --user 0 {launcher}")
    shell("service call notification 1")   # clear the emulator's own notifications
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

    run(SHOTS, demo, out)

    shell("settings put system accelerometer_rotation 0")
    shell("settings put system user_rotation 1")
    time.sleep(3)
    run(LANDSCAPE, demo, out)
    shell("settings put system user_rotation 0")
    art_check(demo)
    swap_check(demo, out / "swapcheck")
    update_check(demo)
    shell("cat /sdcard/RyzerOS/crash.txt")   # printed to the log, if Ryzer hit an error


if __name__ == "__main__":
    main(sys.argv[1], Path(sys.argv[2]), Path(sys.argv[3]))
