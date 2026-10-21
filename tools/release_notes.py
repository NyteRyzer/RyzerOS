"""Prints the GitHub release notes for one version, built from CHANGELOG.md.

    python3 tools/release_notes.py 2.1.0 [--latest]
    python3 tools/release_notes.py 2.1.0 --title

--latest adds the screenshots banner (only the newest release shows the current look).
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RAW = "https://raw.githubusercontent.com/NyteRyzer/RyzerOS/claude/ryzer-os-anbernic-xjo8vx"
REPO = "https://github.com/NyteRyzer/RyzerOS"


def section(version: str) -> tuple[str, str]:
    text = (ROOT / "CHANGELOG.md").read_text()
    m = re.search(rf"^## {re.escape(version)}(?::[ \t]*([^\n]*))?\n(.*?)(?=^## |\Z)", text, re.M | re.S)
    if not m:
        return "", ""
    body = m.group(2).strip().replace("](docs/", f"]({REPO}/blob/HEAD/docs/")
    # Changelog sub-headings become one level smaller inside a release
    body = re.sub(r"^### ", "#### ", body, flags=re.M)
    body = re.sub(r"^\*\*(Play|Library|Look|Look & feel|Tools|Stats|Device)\*\*(.*)$", r"#### \1\2", body, flags=re.M)
    return (m.group(1) or "").strip(), body


def notes(version: str, latest: bool) -> str:
    subtitle, body = section(version)
    out = []
    if latest:
        out.append(f"![Ryzer OS]({RAW}/docs/screenshots/hero.png)\n")
    else:
        out.append(f"> This is an older version. **[Get the newest Ryzer OS]({REPO}/releases/latest)**\n")
    if subtitle:
        out.append(f"## {subtitle[0].upper() + subtitle[1:]}\n")
    out.append(f"""### Download

| Your device | File | How to install |
| --- | --- | --- |
| **Android handhelds on the stock OS** (Anbernic, AYN, Retroid, AYANEO, any Android 6+) | **RyzerOS.apk** | Download it on the handheld and open it. Allow "install unknown apps" if asked. |
| **Custom firmware** (ROCKNIX, Knulli, muOS, ArkOS) | **RyzerOS-Linux.zip** | Unzip it on the device and run `install.sh`. [Guide]({REPO}/blob/HEAD/docs/LINUX.md#install-the-linux-edition) |

Installing over an older version keeps your settings, themes and play history.
""")
    if body:
        out.append("### What's new\n\n" + body + "\n")
    # A patch release (2.1.1) also shows what came in its feature release (2.1.0)
    major, minor, patch = (version.split(".") + ["0", "0"])[:3]
    if latest and patch != "0":
        base = f"{major}.{minor}.0"
        base_title, base_body = section(base)
        if base_body:
            out.append(f"### Also new in {major}.{minor}: {base_title}\n\n" + base_body + "\n")
    out.append(f"""---

[Full changelog]({REPO}/blob/HEAD/CHANGELOG.md) · [Website](https://nyteryzer.github.io/RyzerOS/) · \
[Report a bug]({REPO}/issues) · If Ryzer ever closes by itself, the error is saved in `RyzerOS/crash.txt`.""")
    return "\n".join(out)


def title(version: str) -> str:
    subtitle, _ = section(version)
    return f"Ryzer OS {version}" + (f" — {subtitle[0].upper() + subtitle[1:]}" if subtitle else "")


if __name__ == "__main__":
    if "--title" in sys.argv:
        print(title(sys.argv[1]))
    else:
        print(notes(sys.argv[1], "--latest" in sys.argv))
