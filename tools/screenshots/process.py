"""Turns the raw emulator captures into the images used by the README, website and releases.

    python3 tools/screenshots/process.py [RAW_DIR] [OUT_DIR]

- Dual-screen captures (960x1440) are scaled to 480x720, with a thin hinge drawn between the screens.
- Landscape captures (1440x960) are scaled to 960x640.
- hero.png (1280x640) is the banner at the top of the README, and the link preview on Reddit/Discord.
"""

import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent.parent
FONT_DIRS = ["/usr/share/fonts/opentype/inter", "/usr/share/fonts/truetype/dejavu"]


def font(size, weight="Bold"):
    for name in (f"InterDisplay-{weight}.otf", f"Inter-{weight}.otf", "DejaVuSans-Bold.ttf"):
        for d in FONT_DIRS:
            p = Path(d) / name
            if p.is_file():
                return ImageFont.truetype(str(p), size)
    return ImageFont.load_default()


def display(size):
    """The app's own bold italic display font (Titillium Web), for the hero title."""
    p = ROOT / "android/app/src/main/assets/fonts/TitilliumWeb-BoldItalic.ttf"
    return ImageFont.truetype(str(p), size) if p.is_file() else font(size)


def rounded(img: Image.Image, radius: int) -> Image.Image:
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, img.width - 1, img.height - 1], radius, fill=255)
    out = Image.new("RGBA", img.size)
    out.paste(img, (0, 0), mask)
    return out


def dual(src: Image.Image, width=480) -> Image.Image:
    """Scales a tall capture and adds a dark hinge between the top and bottom screen."""
    h = width * src.height // src.width
    img = src.convert("RGB").resize((width, h), Image.LANCZOS)
    d = ImageDraw.Draw(img)
    mid = h // 2
    d.rectangle([0, mid - 3, width, mid + 3], fill=(14, 14, 20))
    return img


def device(screen: Image.Image, pad=14, radius=30) -> Image.Image:
    """A handheld-style frame with a soft shadow around a screenshot."""
    w, h = screen.width + pad * 2, screen.height + pad * 2
    body = Image.new("RGBA", (w, h))
    d = ImageDraw.Draw(body)
    d.rounded_rectangle([0, 0, w - 1, h - 1], radius, fill=(34, 34, 46), outline=(70, 70, 92), width=2)
    body.alpha_composite(rounded(screen.convert("RGBA"), 10), (pad, pad))
    shadow = Image.new("RGBA", (w + 80, h + 80))
    ImageDraw.Draw(shadow).rounded_rectangle([40, 52, w + 40, h + 52], radius, fill=(0, 0, 0, 170))
    shadow = shadow.filter(ImageFilter.GaussianBlur(22))
    shadow.alpha_composite(body, (40, 40))
    return shadow


def glow(base: Image.Image, xy, radius, color):
    layer = Image.new("RGBA", base.size)
    x, y = xy
    ImageDraw.Draw(layer).ellipse([x - radius, y - radius, x + radius, y + radius], fill=color)
    base.alpha_composite(layer.filter(ImageFilter.GaussianBlur(radius // 2)))


def hero(shots: dict, out: Path, version: str):
    W, H = 1280, 640
    img = Image.new("RGBA", (W, H), (11, 11, 16, 255))
    glow(img, (260, 160), 300, (255, 42, 61, 90))
    glow(img, (1080, 520), 320, (255, 122, 69, 60))
    grid = Image.new("RGBA", (W, H))
    g = ImageDraw.Draw(grid)
    for x in range(0, W, 40):   # faint grid, like the "grid" theme background
        g.line([(x, 0), (x, H)], fill=(255, 255, 255, 10))
    for y in range(0, H, 40):
        g.line([(0, y), (W, y)], fill=(255, 255, 255, 10))
    img.alpha_composite(grid)

    # three devices, the middle one in front
    order = [("home-system", -7, 700, 70), ("home-neon", 7, 1170, 70), ("home", 0, 935, 22)]
    for name, angle, cx, top in order:
        if name not in shots:
            continue
        dev = device(dual(shots[name], 260), pad=10, radius=24)
        dev = dev.rotate(angle, resample=Image.BICUBIC, expand=True)
        img.alpha_composite(dev, (cx - dev.width // 2, top - 40))

    # title block
    d = ImageDraw.Draw(img)
    x = 64
    d.polygon([(x + 14, 92), (x + 52, 92), (x + 38, 138), (x, 138)], fill=(255, 42, 61))   # slanted Blades mark
    d.text((x + 66, 80), "RYZER OS", font=display(58), fill=(255, 255, 255))
    d.text((x, 160), "YOUR HANDHELD,", font=display(50), fill=(243, 243, 247))
    d.text((x, 214), "YOUR CONSOLE.", font=display(50), fill=(255, 92, 92))
    lines = ["A console-style frontend for", "Anbernic, AYN, Retroid & AYANEO.", "",
             "New in 3.0: the Blades redesign", "Dual-screen Home · automatic box art", "Theme Studio · 26 themes · stats"]
    y = 300
    for ln in lines:
        d.text((x, y), ln, font=font(24, "Medium"), fill=(190, 190, 205))
        y += 34 if ln else 14
    pill = f"  v{version} · free APK  "
    tw = d.textlength(pill, font=font(22))
    d.polygon([(x + 12, 548), (x + tw + 20, 548), (x + tw + 8, 590), (x, 590)], fill=(255, 42, 61))
    d.text((x + 4, 556), pill, font=font(22), fill=(255, 255, 255))
    img.convert("RGB").save(out / "hero.png", optimize=True)


def main(raw: Path, out: Path):
    out.mkdir(parents=True, exist_ok=True)
    shots = {}
    for f in sorted(raw.glob("*.png")):
        im = Image.open(f)
        shots[f.stem] = im
        if im.height > im.width:
            dual(im).save(out / f.name, optimize=True)
        else:
            im.convert("RGB").resize((960, 960 * im.height // im.width), Image.LANCZOS).save(out / f.name, optimize=True)
    import re
    version = re.search(r'VERSION = "([^"]+)"', (ROOT / "ryzer/__init__.py").read_text()).group(1)
    hero(shots, out, version)
    print(f"{len(shots)} screenshots + hero.png -> {out}")


if __name__ == "__main__":
    raw = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "docs/screenshots/raw"
    out = Path(sys.argv[2]) if len(sys.argv) > 2 else ROOT / "docs/screenshots"
    main(raw, out)
