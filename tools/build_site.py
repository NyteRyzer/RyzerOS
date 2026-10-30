"""Builds the GitHub Pages site into OUT_DIR.

    python3 tools/build_site.py OUT_DIR [releases.json]

releases.json (optional) is `gh release list --json tagName,publishedAt`; versions listed there
get download buttons and dates on the releases page.
"""

import html
import json
import re
import shutil
import sys
from pathlib import Path

import markdown

ROOT = Path(__file__).resolve().parent.parent
REPO = "https://github.com/NyteRyzer/RyzerOS"
SITE = "https://nyteryzer.github.io/RyzerOS"

# (file, caption, detail) for the screenshot gallery; dual-screen captures
GALLERY = [
    ("home", "Blades Home", "Continue tab, hero card on top"),
    ("home-system", "Every system, one tap away", "Power bars for size and progress"),
    ("home-neon", "Collections", "Vaporwave theme + aurora live wallpaper"),
    ("system-icons", "Console icons", "27 systems, each with its own icon"),
    ("theme-circuit", "Cyberpunk theme", "New circuit-board background"),
    ("theme-aurora", "Neon Night theme", "Aurora glow"),
    ("theme-hex", "Lava theme", "Hex mesh background"),
    ("blades-settings", "New slanted menus", "Colour-coded Settings sections"),
    ("boot-1", "Strike boot animation", "Two seconds, any button skips"),
    ("games", "Game lists", "Slanted covers and play-time meters"),
    ("foryou", "For You", "Picks from what you play most"),
    ("grid", "Grid view", "Your covers, front and centre"),
    ("home-gold", "3DS layout + bonus themes", "Unlocked by earning milestones"),
    ("themes", "26 themes", "Live preview as you browse"),
    ("studio", "Theme Studio", "Design every colour and effect"),
    ("stats", "Play stats", "Time, streaks and top games"),
    ("wrapped", "Ryzer Wrapped", "Your month or year in games"),
    ("calendar", "Play calendar", "Every day you played"),
    ("notifications", "Notification centre", "Banners and do not disturb"),
    ("tools", "Tools", "Mini-games and gadgets"),
    ("2048", "2048", "One of two built-in mini-games"),
    ("roulette", "Game Roulette", "Can't decide? Spin for it"),
    ("quick", "Quick menu (L + R)", "Volume, brightness, swap screens"),
    ("classic-settings", "Classic style", "The rounded 2.x look, one setting away"),
    ("home-ribbon", "Ribbon layout", "With the analog clock"),
]
LANDSCAPE = [
    ("land-home", "Single-screen handhelds", "Retroid, Odin and friends get a one-screen layout"),
    ("land-games", "Game list on one screen", "Details panel next to the list"),
]


def figure(name, cap, detail, land=False):
    return (f'    <figure><div class="ds{" land" if land else ""}"><img loading="lazy" src="screenshots/{name}.png" '
            f'alt="{html.escape(cap)}"></div><figcaption>{html.escape(cap)}<span>{html.escape(detail)}</span></figcaption></figure>')


def changelog():
    text = (ROOT / "CHANGELOG.md").read_text()
    parts = re.split(r"^## ", text, flags=re.M)[1:]
    out = []
    for p in parts:
        head, body = p.split("\n", 1)
        version, _, subtitle = head.partition(":")
        out.append((version.strip(), subtitle.strip(), body.strip()))
    return out


def releases_html(tags):
    rows = []
    entries = changelog()
    for i, (version, subtitle, body) in enumerate(entries):
        tag = f"v{version}"
        info = tags.get(tag)
        body = re.sub(r"^\*\*(Play|Library|Look|Look & feel|Tools|Stats|Device)\*\*(.*)$", r"#### \1\2", body, flags=re.M)
        body = body.replace("](docs/", f"]({REPO}/blob/HEAD/docs/")
        body = re.sub(r"^  (?=[-*] |\d+\. )", "    ", body, flags=re.M)   # nested lists need 4 spaces
        notes = markdown.markdown(body, extensions=["sane_lists"])
        date = f'<span class="date">{info["publishedAt"][:10]}</span>' if info else ""
        dl = ""
        if info:
            dl = (f'<div class="dl"><a class="btn{" primary" if i == 0 else ""}" href="{REPO}/releases/download/{tag}/RyzerOS.apk">APK</a>'
                  f'<a class="btn" href="{REPO}/releases/download/{tag}/RyzerOS-Linux.zip">Linux zip</a>'
                  f'<a class="btn" href="{REPO}/releases/tag/{tag}">On GitHub</a></div>')
        latest = '<span class="tag">LATEST</span>' if i == 0 else ""
        cls = "release latest" if i == 0 else ("release collapsed" if len(body) > 900 else "release")
        title = html.escape(subtitle[0].upper() + subtitle[1:]) if subtitle else ""
        rows.append(f'<article class="{cls}" id="{tag}"><header><h2>{version}</h2>{latest}{date}{dl}</header>'
                    f'<h3 style="margin:4px 0 14px;font-size:20px">{title}</h3><div class="notes">{notes}</div></article>')
    return "\n".join(rows)


def main(out: Path, tags_file: str | None):
    tags = {}
    if tags_file and Path(tags_file).is_file():
        tags = {r["tagName"]: r for r in json.loads(Path(tags_file).read_text())}
    version = re.search(r'VERSION = "([^"]+)"', (ROOT / "ryzer/__init__.py").read_text()).group(1)
    # A patch release (2.1.1) is announced with its feature release's headline (2.1.0)
    feature = ".".join(version.split(".")[:2]) + ".0"
    subtitle = next((s for v, s, _ in changelog() if v == feature), "") or next((s for v, s, _ in changelog() if v == version), "")
    if out.exists():
        shutil.rmtree(out)
    shutil.copytree(ROOT / "site", out)
    shots = out / "screenshots"
    shots.mkdir()
    for f in (ROOT / "docs/screenshots").glob("*.png"):
        shutil.copy(f, shots / f.name)
    have = {f.stem for f in shots.glob("*.png")}
    values = {
        "{{VERSION}}": version, "{{SUBTITLE}}": html.escape(subtitle), "{{REPO}}": REPO, "{{SITE}}": SITE,
        "{{GALLERY}}": "\n".join(figure(*g) for g in GALLERY if g[0] in have),
        "{{LANDSCAPE}}": "\n".join(figure(*g, land=True) for g in LANDSCAPE if g[0] in have),
        "{{RELEASES}}": releases_html(tags),
    }
    for page in out.glob("*.html"):
        text = page.read_text()
        for k, v in values.items():
            text = text.replace(k, v)
        page.write_text(text)
    print(f"Built {out} for {version} ({len(have)} screenshots)")


if __name__ == "__main__":
    main(Path(sys.argv[1]), sys.argv[2] if len(sys.argv) > 2 else None)
