"""Built-in themes must be readable and only use backgrounds the Android app can draw."""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
THEMES = sorted((ROOT / "ryzer/themes").glob("*.json"))
UI_KT = (ROOT / "android/app/src/main/java/com/ryzer/os/Ui.kt").read_text()


def lum(hexcol):
    def ch(v):
        v /= 255
        return v / 12.92 if v <= 0.03928 else ((v + 0.055) / 1.055) ** 2.4
    h = hexcol.lstrip("#")
    r, g, b = (int(h[i:i + 2], 16) for i in (0, 2, 4))
    return 0.2126 * ch(r) + 0.7152 * ch(g) + 0.0722 * ch(b)


def contrast(a, b):
    la, lb = sorted((lum(a), lum(b)), reverse=True)
    return (la + 0.05) / (lb + 0.05)


def android_backgrounds():
    m = re.search(r"val BACKGROUNDS = listOf\(([^)]*)\)", UI_KT)
    return set(re.findall(r'"([a-z-]+)"', m.group(1)))


def test_backgrounds_are_drawn_by_the_app():
    known = android_backgrounds()
    for f in THEMES:
        bg = json.loads(f.read_text())["style"]["background"]
        assert bg in known, f"{f.name}: background '{bg}' isn't one the app draws"


def test_text_is_readable():
    for f in THEMES:
        c = json.loads(f.read_text())["colors"]
        for surface in ("panel", "bg_top", "bg_bottom"):
            assert contrast(c["text"], c[surface]) >= 4.5, f"{f.name}: text on {surface} is {contrast(c['text'], c[surface]):.1f}:1"
        assert contrast(c["text_dim"], c["panel"]) >= 3, f"{f.name}: dim text on panel is {contrast(c['text_dim'], c['panel']):.1f}:1"


def test_labels_on_accent_are_readable():
    for f in THEMES:
        c = json.loads(f.read_text())["colors"]
        assert contrast(c["highlight_text"], c["accent"]) >= 2.5, \
            f"{f.name}: highlight text on accent is {contrast(c['highlight_text'], c['accent']):.1f}:1"


def test_new_blades_patterns_are_used():
    used = {json.loads(f.read_text())["style"]["background"] for f in THEMES}
    assert {"speedlines", "hex", "circuit", "halftone", "aurora"} <= used
