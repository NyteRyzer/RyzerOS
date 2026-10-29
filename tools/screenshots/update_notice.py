"""What someone on an older Ryzer OS sees when a new version comes out.

    python3 tools/screenshots/update_notice.py OLD_APK DEMO_DIR OUT_DIR

Installs an older release (2.4.0) in the emulator with automatic updates on, starts it like a normal
day and snaps the screen as its updater finds the newest GitHub release: first the "Update available"
notice, then the update screen once the download is ready.
"""

import json
import sys
import time
from pathlib import Path

from capture import PKG, TZ, adb, shell, snap, version_name

CONFIG = {
    "theme": "ryzer-red", "sound": False, "boot": False, "resumePrompt": False, "screensaver": 0, "batterySaver": False,
    "userName": "Nyte", "autoUpdate": True, "autoDownloadUpdate": True, "lastUpdateCheck": 0, "skipVersion": "",
    "ryzerNotices": True, "notifBanners": True, "notifDnd": False, "lastVersion": "2.4.0", "uiVersion": 2,
}


def main(apk: str, demo: Path, out: Path):
    out.mkdir(parents=True, exist_ok=True)
    adb("wait-for-device")
    adb("root")
    time.sleep(3)
    adb("wait-for-device")
    shell(f"setprop persist.sys.timezone {TZ}")
    adb("install", "-r", "-g", apk, check=True)
    print("=== UPDATE NOTICE === installed:", version_name(), flush=True)
    shell(f"cmd package set-home-activity {PKG}/.MainActivity")
    for launcher in ("com.google.android.apps.nexuslauncher", "com.android.launcher3"):
        shell(f"pm disable-user --user 0 {launcher}")
    shell(f"appops set --uid {PKG} MANAGE_EXTERNAL_STORAGE allow")
    shell(f"appops set {PKG} REQUEST_INSTALL_PACKAGES allow")
    shell("settings put secure immersive_mode_confirmations confirmed")
    shell("svc power stayon true")
    shell("svc wifi enable")
    adb("push", str(demo / "ROMs"), "/sdcard/")
    shell("mkdir -p /sdcard/RyzerOS")
    adb("push", str(demo / "RyzerOS" / "state.json"), "/sdcard/RyzerOS/state.json")
    cfg = out / "config.json"
    cfg.write_text(json.dumps(CONFIG))
    adb("push", str(cfg), "/sdcard/RyzerOS/config.json")
    cfg.unlink()
    shell(f"am start -n {PKG}/.MainActivity")
    for i, wait in enumerate((20, 15, 45)):   # scan + check, the notice, then the finished download
        time.sleep(wait)
        shell("am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS")
        snap(out / f"notice-{i}.png")
    shell("cat /sdcard/RyzerOS/crash.txt")
    print("=== END UPDATE NOTICE ===", flush=True)


if __name__ == "__main__":
    main(sys.argv[1], Path(sys.argv[2]), Path(sys.argv[3]))
