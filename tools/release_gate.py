"""Checks the emulator results before a release (and at the end of the screenshot workflow).

    python3 tools/release_gate.py [RAW_DIR]          # exits 1 and lists the problems if any

A release needs, from docs/screenshots/raw:
- perf.txt:      Blades Home no more than 25% slower than Classic
- stability.txt: every random-input run finished with Ryzer still running, no problems and no crash entries
- tested.txt:    written by the screenshot workflow; the app code it tested must be the code being released
"""

import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def problems(perf: str, stability: str, tested: str, current: str) -> list:
    out = []
    nums = re.findall(r"(blades|classic)=([0-9.]+)", perf)
    if len(nums) != 2:
        out.append(f"no Home timing in perf.txt ({perf.strip()[:60]})")
    else:
        t = dict(nums)
        b, c = float(t["blades"]), float(t["classic"])
        if b > 1.25 * c:
            out.append(f"Blades Home is {b / c:.2f}x slower than Classic (limit 1.25x)")
    runs = re.findall(r"^\[(\S+)\] events finished=(\w+) running=(\w+) problems=(\d+)", stability, re.M)
    if not runs:
        out.append("no random-input results in stability.txt")
    for name, finished, running, n in runs:
        if finished != "True":
            out.append(f"[{name}] random input didn't finish")
        if running != "True":
            out.append(f"[{name}] Ryzer wasn't running afterwards")
        if n != "0":
            out.append(f"[{name}] {n} crash/freeze reports")
    if "crash.txt: empty" not in stability:
        out.append("crash.txt has errors from the run")
    if tested.split() != current.split():
        out.append(f"the emulator tested different app code ({tested.strip()}) than this ({current.strip()}): re-run the screenshots workflow")
    return out


def code_trees() -> str:
    tree = lambda p: subprocess.run(["git", "rev-parse", f"HEAD:{p}"], capture_output=True, text=True, cwd=ROOT).stdout.strip()
    return f"android={tree('android')} ryzer={tree('ryzer')}"


def main(raw: Path):
    read = lambda n: (raw / n).read_text() if (raw / n).exists() else ""
    found = problems(read("perf.txt"), read("stability.txt"), read("tested.txt"), code_trees())
    print(read("perf.txt").strip()); print(read("stability.txt").strip())
    for p in found:
        print("PROBLEM:", p)
    print("Release gate:", "passed" if not found else "FAILED")
    sys.exit(1 if found else 0)


if __name__ == "__main__":
    main(Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "docs/screenshots/raw")
