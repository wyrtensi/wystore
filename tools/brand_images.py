#!/usr/bin/env python3
"""Draw every brand picture from one source: site icons, logo, banners, the TV banner.

    python tools/brand_images.py            # write all of them
    python tools/brand_images.py --only banner

The pictures used to be drawn one by one, and when the app's look changed they no longer agreed
with each other or with the launcher icon. Here the mark is the launcher icon's own path
(res/drawable/ic_wy_store_foreground.xml) and the colours are the Wy Hub dark theme's, so a change
to either is one edit and a rerun.

Each picture is an HTML page rendered by headless Edge (or Chrome) - the same Manrope the site
serves, real gradients - and then cut to size with Pillow. The banners carry no sentence, only
the name and the two sources, so one picture serves every language.
"""
import argparse
import os
import random
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
DOCS = ROOT / "docs"
RES = ROOT / "app" / "src" / "main" / "res"
FONT = (DOCS / "assets" / "fonts" / "manrope-500.woff").as_uri()

BROWSERS = [
    r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    r"C:\Program Files\Microsoft\Edge\Application\msedge.exe",
    r"C:\Program Files\Google\Chrome\Application\chrome.exe",
    "/usr/bin/google-chrome",
    "/usr/bin/chromium",
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
]

# The launcher icon's monogram and tiles, in its 108-unit adaptive layer.
MARK = (
    "M27.05,36.74L35.17,63.42L39.85,63.31L43.98,50.19L48.39,63.53L52.97,63.25L55.25,55.2"
    "L59.06,62.6C58.1,64.9 56.5,66.5 53.95,66.96C52.6,67.3 51.9,70.8 52.91,71.31"
    "C56.4,71.6 60.4,70.3 61.6,67.9L72.67,44.64L67.23,44.85L61.57,57.7L56.83,49.92L59.06,40.77"
    "L54.0,40.77L50.24,53.56L46.32,40.94L41.92,40.72L37.67,53.56L32.88,36.85z"
)
TILES = [(69.9, 58.6), (76.05, 58.6), (69.9, 64.8), (76.05, 64.8)]
TILE_DIM = "rgba(255,255,255,0.28)"
TILE_LIT = "#F66C3F"

INK = "#15161A"
PAPER = "#F3F3F1"
ACCENT = "#CF4A1A"
ACCENT_TEXT = "#E07650"

# A launcher shows the middle 72 of the 108 units, so that is the tile here too. Outside a
# launcher nothing crops the layer, and the mark at launcher scale looked small in its tile;
# drawn on its own it is set larger.
MARK_SCALE = 1.18


def icon_svg(uid, rounded=True):
    """The icon as an SVG element over the 18..90 square: glass, the wash, the hairline, the mark."""
    radius = 17 if rounded else 0
    tiles = "".join(
        f'<rect x="{x}" y="{y}" width="4.9" height="4.9" rx="1.1" fill="{TILE_LIT if i == 3 else TILE_DIM}"/>'
        for i, (x, y) in enumerate(TILES)
    )
    hairline = (
        f'<rect x="18.35" y="18.35" width="71.3" height="71.3" rx="{radius - 0.35}" fill="none" '
        'stroke="#fff" stroke-opacity="0.14" stroke-width="0.7"/>'
        if rounded else ""
    )
    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72" width="100%" height="100%">
  <defs>
    <linearGradient id="g{uid}" x1="0" y1="18" x2="0" y2="90" gradientUnits="userSpaceOnUse">
      <stop offset="0" stop-color="#2B2C32"/><stop offset="1" stop-color="#17181C"/>
    </linearGradient>
    <radialGradient id="w{uid}" cx="18" cy="90" r="80" gradientUnits="userSpaceOnUse">
      <stop offset="0" stop-color="{ACCENT}" stop-opacity="0.75"/>
      <stop offset="0.55" stop-color="{ACCENT}" stop-opacity="0.18"/>
      <stop offset="1" stop-color="{ACCENT}" stop-opacity="0"/>
    </radialGradient>
  </defs>
  <rect x="18" y="18" width="72" height="72" rx="{radius}" fill="url(#g{uid})"/>
  <rect x="18" y="18" width="72" height="72" rx="{radius}" fill="url(#w{uid})"/>
  {hairline}
  <g transform="translate(54 54) scale({MARK_SCALE}) translate(-54 -54)">
    <path d="{MARK}" fill="{PAPER}"/>{tiles}
  </g>
</svg>"""


def streaks(w, h, light, seed=7):
    """The site's star field, still: short vertical strokes, nearer ones brighter and longer."""
    rnd = random.Random(seed)
    colour = "27,27,29" if light else "242,239,233"
    lines = []
    for _ in range(int(min(420, max(90, w * h / 2600)))):
        z = rnd.random() * 0.85 + 0.15
        x, y = rnd.random() * w, rnd.random() * h
        alpha = (0.05 + z * 0.22) if light else (0.08 + z * 0.45)
        lines.append(
            f'<line x1="{x:.1f}" y1="{y:.1f}" x2="{x:.1f}" y2="{y + 2 + z * z * 16:.1f}" '
            f'stroke="rgba({colour},{alpha:.2f})" stroke-width="{0.7 + z * 1.1:.2f}" stroke-linecap="round"/>'
        )
    return f'<svg class="field" viewBox="0 0 {w} {h}">{"".join(lines)}</svg>'


def banner_html(w, h, light=False):
    icon = round(h * 0.46)
    left = round(min(w * 0.09, h * 0.3))
    bg, fg = ("#F7F6F4", "#1B1B1D") if light else (INK, PAPER)
    eyebrow = "#C4501F" if light else ACCENT_TEXT
    glow = 0.22 if light else 0.42
    ghost = "rgba(0,0,0,0.05)" if light else "rgba(255,255,255,0.045)"
    # The monogram again, huge and only outlined, leaving off the right edge.
    ghost_h = h * 1.5
    ghost_scale = ghost_h / 35
    return f"""<!doctype html><html><head><meta charset="utf-8"><style>
@font-face {{ font-family: Manrope; font-weight: 500; src: url("{FONT}") format("woff"); }}
html, body {{ margin: 0; width: {w}px; height: {h}px; overflow: hidden; background: {bg}; }}
.field, .ghost, .glow {{ position: absolute; inset: 0; width: 100%; height: 100%; }}
.glow {{ background:
  radial-gradient({h * 1.1}px {h * 0.9}px at {left + icon / 2}px 50%, rgba(207,74,26,{glow}), transparent 70%),
  radial-gradient({w * 0.7}px {h * 0.8}px at 0 100%, rgba(207,74,26,{glow * 0.45}), transparent 70%); }}
.row {{ position: absolute; left: {left}px; top: 0; bottom: 0; display: flex; align-items: center;
  gap: {round(h * 0.075)}px; }}
.icon {{ width: {icon}px; height: {icon}px; border-radius: {icon * 17 / 72}px;
  box-shadow: 0 {h * 0.06}px {h * 0.14}px rgba(0,0,0,{0.18 if light else 0.55}); }}
.text {{ font-family: Manrope; font-weight: 500; color: {fg}; }}
.eyebrow {{ font-size: {h * 0.062}px; color: {eyebrow}; letter-spacing: 0.01em; margin-bottom: {h * 0.012}px; }}
.name {{ font-size: {h * 0.205}px; line-height: 1; letter-spacing: -0.025em; white-space: nowrap; }}
</style></head><body>
<div class="glow"></div>
{streaks(w, h, light)}
<svg class="ghost" viewBox="0 0 {w} {h}"><g transform="translate({w - ghost_h * 1.05:.1f} {h * 0.5 - ghost_h * 0.5:.1f}) scale({ghost_scale:.3f}) translate(-27 -36.7)">
  <path d="{MARK}" fill="none" stroke="{ghost}" stroke-width="{1.4 / ghost_scale:.3f}"/></g></svg>
<div class="row"><div class="icon">{icon_svg("b")}</div>
  <div class="text"><div class="eyebrow">RuStore · GitHub</div><div class="name">Wy Store</div></div></div>
</body></html>"""


def icon_html(size, rounded):
    return f"""<!doctype html><html><head><meta charset="utf-8"><style>
html, body {{ margin: 0; width: {size}px; height: {size}px; overflow: hidden; background: transparent; }}
</style></head><body>{icon_svg("i", rounded)}</body></html>"""


def browser():
    for path in BROWSERS:
        if os.path.exists(path):
            return path
    found = shutil.which("msedge") or shutil.which("chrome") or shutil.which("chromium")
    if not found:
        sys.exit("No Edge or Chrome to render with.")
    return found


def render(html, w, h, out, tmp):
    page = tmp / f"{out.stem}-{w}x{h}.html"
    page.write_text(html, encoding="utf-8")
    shot = tmp / f"{out.stem}-{w}x{h}.png"
    subprocess.run(
        [browser(), "--headless=new", "--disable-gpu", "--hide-scrollbars", "--no-first-run",
         f"--user-data-dir={tmp / 'profile'}", "--force-device-scale-factor=1",
         "--default-background-color=00000000", "--allow-file-access-from-files",
         f"--window-size={w},{h}", f"--screenshot={shot}", page.as_uri()],
        check=True, capture_output=True, timeout=120,
    )
    im = Image.open(shot)
    if im.size != (w, h):
        im = im.crop((0, 0, w, h))
    return im


def save(im, path, opaque=False, size=None):
    if size:
        im = im.resize((size, size), Image.LANCZOS)
    if opaque:
        im = im.convert("RGB")
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.suffix == ".webp":
        im.save(path, "WEBP", quality=92, method=6)
    else:
        im.save(path, optimize=True)
    print(f"{path.relative_to(ROOT)}  {im.size[0]}x{im.size[1]}")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--only", choices=["icon", "banner"], help="draw only icons or only banners")
    args = parser.parse_args()

    with tempfile.TemporaryDirectory() as t:
        tmp = Path(t)
        if args.only != "banner":
            (DOCS / "logo.svg").write_text(icon_svg("l") + "\n", encoding="utf-8")
            print("docs/logo.svg")
            big = render(icon_html(1024, True), 1024, 1024, Path("icon"), tmp)
            square = render(icon_html(1024, False), 1024, 1024, Path("square"), tmp)
            save(big, DOCS / "logo.png", size=512)
            save(big, DOCS / "app_icon.png", size=256)
            save(big, DOCS / "assets/img/icon-512.png", size=512)
            save(big, DOCS / "assets/img/icon-192.png", size=192)
            save(big, DOCS / "assets/img/icon-192.webp", size=192)
            save(big, DOCS / "assets/img/favicon-32.png", size=32)
            # iOS rounds the corners itself and shows black where a picture is transparent.
            save(square, DOCS / "assets/img/icon-180.png", opaque=True, size=180)
            ico = big.resize((48, 48), Image.LANCZOS)
            ico.save(DOCS / "favicon.ico", sizes=[(16, 16), (32, 32), (48, 48)])
            print("docs/favicon.ico  16, 32, 48")
            # Only launchers that predate adaptive icons read these; they get the full square.
            for folder, px in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
                save(square, RES / f"mipmap-{folder}" / "ic_wy_store.webp", opaque=True, size=px)
        if args.only != "icon":
            for name, w, h, light in [
                ("banner", 1280, 440, False), ("banner-en", 1280, 440, False),
                ("banner-light", 1280, 440, True), ("banner-en-light", 1280, 440, True),
                ("banner-1024x500", 1024, 500, False), ("banner-en-1024x500", 1024, 500, False),
                ("og", 1200, 630, False), ("og-en", 1200, 630, False),
            ]:
                folder = DOCS / "assets/img" if name.startswith("og") else DOCS
                save(render(banner_html(w, h, light), w, h, Path(name), tmp), folder / f"{name}.png", opaque=True)
            save(render(banner_html(640, 360), 640, 360, Path("tv"), tmp),
                 RES / "drawable-xhdpi" / "tv_banner.png", opaque=True)


if __name__ == "__main__":
    main()
