"""Hardware status and performance profiles.

Profiles are applied by the `ryzer-perf` shell script (system/ryzer-perf), which
writes to sysfs. Reading status is done here directly. On a desktop none of the
sysfs nodes exist, so everything degrades to "unknown" instead of failing.
"""

import glob
import os
import shutil
import subprocess
from pathlib import Path

from ryzer import paths

PROFILES = {
    "battery": "Battery Saver",
    "balanced": "Balanced",
    "performance": "Performance",
    "turbo": "Turbo",
}
PROFILE_ORDER = list(PROFILES)


def perf_script():
    for p in (paths.home() / "system" / "ryzer-perf", Path("/usr/bin/ryzer-perf")):
        if p.is_file() and os.access(p, os.X_OK):
            return p
    return None


def apply_profile(profile: str) -> bool:
    script = perf_script()
    if profile not in PROFILES or script is None:
        return False
    try:
        return subprocess.run([str(script), profile], timeout=10).returncode == 0
    except (OSError, subprocess.SubprocessError):
        return False


def _read(path: str):
    try:
        with open(path) as f:
            return f.read().strip()
    except OSError:
        return None


def battery():
    """Returns (percent, charging) or None when there is no battery."""
    for supply in glob.glob("/sys/class/power_supply/*"):
        if _read(f"{supply}/type") != "Battery":
            continue
        cap = _read(f"{supply}/capacity")
        if cap is None:
            continue
        status = _read(f"{supply}/status") or ""
        return int(cap), status in ("Charging", "Full")
    return None


def cpu_temp():
    temps = []
    for zone in glob.glob("/sys/class/thermal/thermal_zone*/temp"):
        v = _read(zone)
        if v and v.lstrip("-").isdigit():
            temps.append(int(v) / 1000)
    return max(temps) if temps else None


def cpu_freq_mhz():
    v = _read("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq")
    return int(v) // 1000 if v and v.isdigit() else None


def cpu_governor():
    return _read("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor")


def wifi_connected() -> bool:
    for iface in glob.glob("/sys/class/net/wlan*"):
        if _read(f"{iface}/operstate") == "up":
            return True
    return False


def memory():
    """Returns (used_mb, total_mb) or None."""
    info = {}
    try:
        with open("/proc/meminfo") as f:
            for line in f:
                k, v = line.split(":", 1)
                info[k] = int(v.split()[0])
    except (OSError, ValueError):
        return None
    total = info.get("MemTotal", 0) // 1024
    avail = info.get("MemAvailable", 0) // 1024
    return total - avail, total


def disk(path) -> tuple:
    """Returns (used_gb, total_gb) for the filesystem holding path."""
    try:
        u = shutil.disk_usage(path)
    except OSError:
        return None
    return round(u.used / 1e9, 1), round(u.total / 1e9, 1)
