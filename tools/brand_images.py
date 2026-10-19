#!/usr/bin/env python3
"""Draw every brand picture from one source: site icons, logo, banners, the TV banner.

    python tools/brand_images.py            # write all of them
    python tools/brand_images.py --only banner

The pictures used to be drawn one by one, and when the app's look changed they no longer agreed
with each other or with the launcher icon. Here the mark is the launcher icon's own path
(res/drawable/ic_wy_store_foreground.xml) and the colours are the Wy Hub dark theme's, so a change
to either is one edit and a rerun.

Each picture is an HTML page rendered by headless Edge (or Chrome) - the same Manrope the site
serves, real gradients - and then cut to size with Pillow. The banners carry no sentence: the
name, the two sources, and badges for Android, Android TV and open source. Only the last badge
has words, so there is a Russian and an English file of each and nothing more.
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

# Every AccentPreset in the app: (corner wash hex, lit catalogue tile hex).
ACCENTS = {
    "orange": ("#CF4A1A", "#F66C3F"),
    "red": ("#E0283C", "#F45163"),
    "pink": ("#D43584", "#EC5CA3"),
    "violet": ("#A348E4", "#BD6FF5"),
    "indigo": ("#6363F2", "#8282FA"),
    "blue": ("#1F76DA", "#4796F0"),
    "teal": ("#0E7C8C", "#28A3B5"),
    "green": ("#278646", "#44AD66"),
    "amber": ("#976F20", "#C49538"),
}

# The banners' badges: what it runs on, and that it is open. Android 9 is the app's minSdk 28.
ANDROID = (
    '<svg viewBox="3 5.5 18 14"><path fill-rule="evenodd" fill="currentColor" d="M4 18a8 8 0 0 1 16 0z'
    'M8.2 14.2a1.1 1.1 0 1 0 2.2 0a1.1 1.1 0 1 0-2.2 0zM13.6 14.2a1.1 1.1 0 1 0 2.2 0a1.1 1.1 0 1 0-2.2 0z"/>'
    '<path d="M7.6 10.6 5.8 7.4M16.4 10.6l1.8-3.2" stroke="currentColor" stroke-width="1.6" '
    'stroke-linecap="round"/></svg>'
)
TV = (
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round">'
    '<rect x="2.9" y="4.4" width="18.2" height="12.6" rx="2.6"/><path d="M8.5 20.4h7"/></svg>'
)
GITHUB = '<svg viewBox="0 0 16 16"><path fill="currentColor" d="M8 0c4.42 0 8 3.58 8 8a8.013 8.013 0 0 1-5.45 7.59c-.4.08-.55-.17-.55-.38 0-.27.01-1.13.01-2.2 0-.75-.25-1.23-.54-1.48 1.78-.2 3.65-.88 3.65-3.95 0-.88-.31-1.59-.82-2.15.08-.2.36-1.02-.08-2.12 0 0-.67-.22-2.2.82-.64-.18-1.32-.27-2-.27-.68 0-1.36.09-2 .27-1.53-1.03-2.2-.82-2.2-.82-.44 1.1-.16 1.92-.08 2.12-.51.56-.82 1.28-.82 2.15 0 3.06 1.86 3.75 3.64 3.95-.23.2-.44.55-.51 1.07-.46.21-1.61.55-2.33-.66-.15-.24-.6-.83-1.23-.82-.67.01-.27.38.01.53.34.19.73.9.82 1.13.16.45.68 1.31 2.69.94 0 .67.01 1.3.01 1.49 0 .21-.15.45-.55.38A7.995 7.995 0 0 1 0 8c0-4.42 3.58-8 8-8Z"/></svg>'
OPEN_SOURCE = {"ru": "Открытый код", "en": "Open source"}

# A launcher shows the middle 72 of the 108 units, so that is the tile here too. Outside a
# launcher nothing crops the layer, and the mark at launcher scale looked small in its tile;
# drawn on its own it is set larger.
MARK_SCALE = 1.18


def icon_svg(uid, rounded=True, accent=ACCENT, tile_lit=TILE_LIT):
    """The icon as an SVG element over the 18..90 square: glass, the wash, the hairline, the mark."""
    radius = 17 if rounded else 0
    tiles = "".join(
        f'<rect x="{x}" y="{y}" width="4.9" height="4.9" rx="1.1" fill="{tile_lit if i == 3 else TILE_DIM}"/>'
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
      <stop offset="0" stop-color="{accent}" stop-opacity="0.75"/>
      <stop offset="0.55" stop-color="{accent}" stop-opacity="0.18"/>
      <stop offset="1" stop-color="{accent}" stop-opacity="0"/>
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


def banner_html(w, h, light=False, lang=None, rounded=False):
    icon = round(h * 0.46)
    radius = round(icon * 17 / 72) if rounded else 0
    left = round(min(w * 0.09, h * 0.3))
    bg, fg = ("#F7F6F4", "#1B1B1D") if light else (INK, PAPER)
    eyebrow = "#C4501F" if light else ACCENT_TEXT
    glow = 0.22 if light else 0.42
    ghost = "rgba(0,0,0,0.05)" if light else "rgba(255,255,255,0.045)"
    border = "rgba(0,0,0,0.12)" if light else "rgba(255,255,255,0.14)"
    badges = "" if not lang else (
        f'<div class="badges"><span class="badge">{ANDROID}Android 9+</span>'
        f'<span class="badge">{TV}Android TV</span>'
        f'<span class="badge open">{GITHUB}{OPEN_SOURCE[lang]}</span></div>'
    )
    frame = (
        f'<svg class="frame" viewBox="0 0 {w} {h}">'
        f'<rect x="1.25" y="1.25" width="{w - 2.5}" height="{h - 2.5}" rx="{radius - 1.25}" '
        f'fill="none" stroke="{border}" stroke-width="2.5"/></svg>'
        if rounded else ""
    )
    # The monogram again, huge and only outlined, leaving off the right edge.
    ghost_h = h * 1.5
    ghost_scale = ghost_h / 35
    return f"""<!doctype html><html><head><meta charset="utf-8"><style>
@font-face {{ font-family: Manrope; font-weight: 500; src: url("{FONT}") format("woff"); }}
html, body {{ margin: 0; width: {w}px; height: {h}px; overflow: hidden; background: transparent; }}
.card {{ position: relative; width: 100%; height: 100%; overflow: hidden; background: {bg};
  border-radius: {radius}px; }}
.field, .ghost, .glow, .frame {{ position: absolute; inset: 0; width: 100%; height: 100%; }}
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
.badges {{ display: flex; gap: {h * 0.022}px; margin-top: {h * 0.06}px; }}
.badge {{ display: flex; align-items: center; gap: {h * 0.016}px; height: {h * 0.09}px;
  padding: 0 {h * 0.034}px 0 {h * 0.026}px; border-radius: 999px; font-size: {h * 0.043}px;
  white-space: nowrap; color: {"#3A3B40" if light else "#D9DADD"};
  background: {"rgba(236,234,231,0.92)" if light else "rgba(37,38,43,0.9)"};
  border: 1px solid {"rgba(0,0,0,0.1)" if light else "rgba(255,255,255,0.13)"}; }}
.badge svg {{ width: {h * 0.05}px; height: {h * 0.05}px; flex: none; }}
.badge.open svg {{ color: {eyebrow}; }}
</style></head><body><div class="card">
<div class="glow"></div>
{streaks(w, h, light)}
<svg class="ghost" viewBox="0 0 {w} {h}"><g transform="translate({w - ghost_h * 1.05:.1f} {h * 0.5 - ghost_h * 0.5:.1f}) scale({ghost_scale:.3f}) translate(-27 -36.7)">
  <path d="{MARK}" fill="none" stroke="{ghost}" stroke-width="{1.4 / ghost_scale:.3f}"/></g></svg>
<div class="row"><div class="icon">{icon_svg("b")}</div>
  <div class="text"><div class="eyebrow">RuStore · GitHub</div><div class="name">Wy Store</div>{badges}</div></div>
{frame}
</div></body></html>"""


def rusinternet_banner_html(w, h, light=False, lang="ru"):
    """The Wy Store banner drawn on rusinternet.com's own card surface and palette."""
    icon = round(h * 0.46)
    left = round(min(w * 0.09, h * 0.3))
    bg = (
        "linear-gradient(135deg, #FFFFFF 0%, #F6F8FB 100%)"
        if light
        else "linear-gradient(135deg, #242832 0%, #1B1E26 100%)"
    )
    fg = "#101828" if light else "#E9EBF1"
    eyebrow = "#C4501F" if light else ACCENT_TEXT
    c1 = "rgba(226,231,239,0.55)" if light else "rgba(52,58,70,0.45)"
    c2 = "#EEF2F8" if light else "rgba(44,49,60,0.55)"
    ghost = "rgba(16,24,40,0.06)" if light else "rgba(233,235,241,0.055)"
    badge_bg = "#EEF2F8" if light else "#2C313C"
    badge_border = "#E2E7EF" if light else "#343A46"
    badge_fg = "#101828" if light else "#E9EBF1"
    badge_ic = "#5F6B7F" if light else "#9CA4B3"
    shadow = (
        "0 18px 38px rgba(16,24,40,0.14), 0 4px 12px rgba(16,24,40,0.07)"
        if light
        else "0 22px 48px rgba(0,0,0,0.45)"
    )
    icon_glow = "rgba(207,74,26,0.10)" if light else "rgba(207,74,26,0.18)"
    ghost_h = h * 1.5
    ghost_scale = ghost_h / 35
    badges = (
        f'<div class="badges"><span class="badge">{ANDROID}Android 9+</span>'
        f'<span class="badge">{TV}Android TV</span>'
        f'<span class="badge open">{GITHUB}{OPEN_SOURCE[lang]}</span></div>'
    )
    return f"""<!doctype html><html><head><meta charset="utf-8"><style>
@font-face {{ font-family: Manrope; font-weight: 500; src: url("{FONT}") format("woff"); }}
html, body {{ margin: 0; width: {w}px; height: {h}px; overflow: hidden; background: {bg}; }}
.ghost, .glow {{ position: absolute; inset: 0; width: 100%; height: 100%; }}
.glow {{ background:
  radial-gradient({h * 1.0}px {h * 0.85}px at {left + icon / 2}px 50%, {icon_glow}, transparent 70%); }}
.row {{ position: absolute; left: {left}px; top: 0; bottom: 0; display: flex; align-items: center;
  gap: {round(h * 0.075)}px; }}
.icon {{ width: {icon}px; height: {icon}px; border-radius: {icon * 17 / 72}px;
  box-shadow: {shadow}; }}
.text {{ font-family: Manrope, Inter, "Segoe UI", sans-serif; font-weight: 500; color: {fg}; }}
.eyebrow {{ font-size: {h * 0.062}px; color: {eyebrow}; letter-spacing: 0.01em; margin-bottom: {h * 0.012}px; }}
.name {{ font-size: {h * 0.205}px; line-height: 1; letter-spacing: -0.025em; white-space: nowrap; }}
.badges {{ display: flex; gap: {h * 0.022}px; margin-top: {h * 0.06}px; }}
.badge {{ display: flex; align-items: center; gap: {h * 0.016}px; height: {h * 0.09}px;
  padding: 0 {h * 0.034}px 0 {h * 0.026}px; border-radius: 999px; font-size: {h * 0.043}px;
  white-space: nowrap; color: {badge_fg};
  background: {badge_bg}; border: 1px solid {badge_border}; }}
.badge svg {{ width: {h * 0.05}px; height: {h * 0.05}px; flex: none; color: {badge_ic}; }}
.badge.open svg {{ color: {eyebrow}; }}
</style></head><body>
<div class="glow"></div>
<svg class="ghost" viewBox="0 0 {w} {h}">
  <circle cx="{w * 0.92:.1f}" cy="{h * 0.08:.1f}" r="{h * 0.52:.1f}" fill="{c1}"/>
  <circle cx="{w * 0.80:.1f}" cy="{h * 0.54:.1f}" r="{h * 0.44:.1f}" fill="{c2}"/>
  <g transform="translate({w - ghost_h * 1.05:.1f} {h * 0.5 - ghost_h * 0.5:.1f}) scale({ghost_scale:.3f}) translate(-27 -36.7)">
    <path d="{MARK}" fill="none" stroke="{ghost}" stroke-width="{1.4 / ghost_scale:.3f}"/>
  </g>
</svg>
<div class="row"><div class="icon">{icon_svg("b")}</div>
  <div class="text"><div class="eyebrow">RuStore · GitHub</div><div class="name">Wy Store</div>{badges}</div></div>
</body></html>"""


def icon_html(size, rounded, accent=ACCENT, tile_lit=TILE_LIT):
    return f"""<!doctype html><html><head><meta charset="utf-8"><style>
html, body {{ margin: 0; width: {size}px; height: {size}px; overflow: hidden; background: transparent; }}
</style></head><body>{icon_svg("i", rounded, accent, tile_lit)}</body></html>"""


def write_accent_xmls(name, accent_hex, lit_hex):
    raw = accent_hex.lstrip("#").upper()
    bg_xml = f"""<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <!-- Wy Hub glass: graphite a shade lighter at the top, as the app's panels are. The
         gradient spans the part a launcher shows (18 to 90), not the whole layer. -->
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="0"
                android:startY="18"
                android:endX="0"
                android:endY="90"
                android:startColor="#FF2B2C32"
                android:endColor="#FF17181C" />
        </aapt:attr>
    </path>
    <!-- The panels' wash of the accent, low in one corner. -->
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="radial"
                android:centerX="18"
                android:centerY="90"
                android:gradientRadius="80">
                <item android:offset="0" android:color="#BF{raw}" />
                <item android:offset="0.55" android:color="#2E{raw}" />
                <item android:offset="1" android:color="#00{raw}" />
            </gradient>
        </aapt:attr>
    </path>
</vector>
"""
    fg_xml = f"""<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <!-- Wy monogram: white on the graphite glass. The tail of the y is two curves; as
         straight segments it read as a broken stroke at launcher size. -->
    <path
        android:fillColor="#F3F3F1"
        android:pathData="{MARK}" />
    <!-- 2x2 catalogue tiles: three dim, one lit in the accent -->
    <path
        android:fillColor="#47FFFFFF"
        android:pathData="M70.55,58.84L70.11,59.28L69.9,59.88L69.9,62.33L70.17,62.98L70.44,63.25L70.98,63.53L73.71,63.53L74.2,63.31L74.63,62.82L74.79,62.33L74.79,59.82L74.47,59.12L74.25,58.9L73.54,58.63L71.15,58.63z" />
    <path
        android:fillColor="#47FFFFFF"
        android:pathData="M76.7,58.84L76.26,59.28L76.05,59.93L76.05,62.27L76.32,62.98L76.65,63.31L77.14,63.53L79.8,63.53L80.4,63.25L80.78,62.82L80.95,62.33L80.95,59.88L80.62,59.12L80.29,58.84L79.69,58.63L77.24,58.63z" />
    <path
        android:fillColor="#47FFFFFF"
        android:pathData="M70.49,65.1L70.11,65.49L69.9,66.03L69.9,68.48L70.0,68.86L70.28,69.3L70.55,69.51L71.09,69.73L73.6,69.73L74.3,69.41L74.58,69.08L74.79,68.48L74.79,66.03L74.41,65.27L74.14,65.05L73.54,64.83L71.15,64.83z" />
    <path
        android:fillColor="{lit_hex}"
        android:pathData="M76.7,65.05L76.26,65.49L76.05,66.08L76.05,68.43L76.26,69.08L76.7,69.51L77.24,69.73L79.75,69.73L80.51,69.35L80.73,69.08L80.95,68.48L80.95,66.08L80.73,65.49L80.29,65.05L79.69,64.83L77.3,64.83z" />
</vector>
"""
    anydpi_xml = f"""<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_wy_store_background_{name}" />
    <foreground android:drawable="@drawable/ic_wy_store_foreground_{name}" />
</adaptive-icon>
"""
    anydpi_v33_xml = f"""<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_wy_store_background_{name}" />
    <foreground android:drawable="@drawable/ic_wy_store_foreground_{name}" />
    <monochrome android:drawable="@drawable/ic_wy_store_monochrome" />
</adaptive-icon>
"""
    (RES / "drawable" / f"ic_wy_store_background_{name}.xml").write_text(bg_xml, encoding="utf-8")
    (RES / "drawable" / f"ic_wy_store_foreground_{name}.xml").write_text(fg_xml, encoding="utf-8")
    (RES / "mipmap-anydpi" / f"ic_wy_store_{name}.xml").write_text(anydpi_xml, encoding="utf-8")
    (RES / "mipmap-anydpi-v33" / f"ic_wy_store_{name}.xml").write_text(anydpi_v33_xml, encoding="utf-8")


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
    try:
        label = path.relative_to(ROOT)
    except ValueError:
        label = path
    print(f"{label}  {im.size[0]}x{im.size[1]}")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--only", choices=["icon", "banner", "rusinternet"], help="draw only one group")
    parser.add_argument("--rusinternet-dir", type=Path, help="path to rusinternet public/assets directory")
    args = parser.parse_args()

    with tempfile.TemporaryDirectory() as t:
        tmp = Path(t)
        if args.only in (None, "icon"):
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
            for name, (accent_hex, lit_hex) in ACCENTS.items():
                if name == "orange":
                    continue
                write_accent_xmls(name, accent_hex, lit_hex)
                (DOCS / f"logo-{name}.svg").write_text(
                    icon_svg(f"l-{name}", True, accent_hex, lit_hex) + "\n", encoding="utf-8"
                )
                print(f"docs/logo-{name}.svg")
                accent_big = render(
                    icon_html(1024, True, accent_hex, lit_hex), 1024, 1024, Path(f"icon-{name}"), tmp
                )
                accent_square = render(
                    icon_html(1024, False, accent_hex, lit_hex), 1024, 1024, Path(f"square-{name}"), tmp
                )
                save(accent_big, DOCS / f"logo-{name}.png", size=512)
                for folder, px in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
                    save(accent_square, RES / f"mipmap-{folder}" / f"ic_wy_store_{name}.webp", opaque=True, size=px)
        if args.only in (None, "banner"):
            for name, w, h, rounded in [
                ("banner", 1280, 440, True), ("banner-en", 1280, 440, True),
                ("banner-1024x500", 1024, 500, False), ("banner-en-1024x500", 1024, 500, False),
                ("og", 1200, 630, False), ("og-en", 1200, 630, False),
            ]:
                folder = DOCS / "assets/img" if name.startswith("og") else DOCS
                lang = "en" if "-en" in name else "ru"
                save(
                    render(banner_html(w, h, False, lang, rounded), w, h, Path(name), tmp),
                    folder / f"{name}.png",
                    opaque=not rounded,
                )
            for name, lang in [("banner-light", "ru"), ("banner-en-light", "en")]:
                save(
                    render(rusinternet_banner_html(1280, 440, True, lang), 1280, 440, Path(name), tmp),
                    DOCS / f"{name}.png",
                    opaque=True,
                )
            save(render(banner_html(640, 360), 640, 360, Path("tv"), tmp),
                 RES / "drawable-xhdpi" / "tv_banner.png", opaque=True)
        if args.only == "rusinternet" or args.rusinternet_dir:
            target = args.rusinternet_dir or Path(r"D:\ru-certificates\public\assets")
            big = render(icon_html(1024, True), 1024, 1024, Path("rus-icon"), tmp)
            save(big, target / "wystore.png", size=256)
            for name, light, lang in [
                ("wystore-banner", False, "ru"),
                ("wystore-banner-en", False, "en"),
                ("wystore-banner-light", True, "ru"),
                ("wystore-banner-light-en", True, "en"),
            ]:
                save(
                    render(rusinternet_banner_html(1280, 440, light, lang), 1280, 440, Path(name), tmp),
                    target / f"{name}.png",
                    opaque=True,
                )


if __name__ == "__main__":
    main()

