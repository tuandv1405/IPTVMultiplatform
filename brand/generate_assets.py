"""
Generates every raster brand asset for TS IPTV from one vector description.

Run from the repository root:

    python brand/generate_assets.py

Outputs
  brand/                                 Play Console upload set
  composeApp/src/androidMain/res/        launcher icons (adaptive + legacy)
  composeApp/src/androidMain/            ic_launcher-playstore.png

The mark is a rounded play triangle with two broadcast arcs to its left,
filled with the app's accent gradient over the app's deep-navy background.
"""

import math
import os

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BRAND_DIR = os.path.join(ROOT, "brand")
RES_DIR = os.path.join(ROOT, "composeApp", "src", "androidMain", "res")
ANDROID_MAIN = os.path.join(ROOT, "composeApp", "src", "androidMain")

# Palette lifted from TSColors.kt so the icon matches the running app.
NAVY_TOP = (12, 13, 44)       # #0C0D2C  backgroundGradientColor1
NAVY_BOTTOM = (3, 4, 29)      # #03041D  DeepBlue
ACCENT_GREEN = (0, 245, 160)  # #00F5A0  AccentGreen
ACCENT_CYAN = (0, 217, 233)   # #00D9E9  AccentCyan

SS = 4  # supersampling factor

# The wordmark needs a real typeface. These are tried in order; the script still
# produces every asset with Pillow's bitmap default if none of them are present.
FONT_CANDIDATES = [
    "C:/Windows/Fonts/segoeuib.ttf",
    "C:/Windows/Fonts/calibrib.ttf",
    "C:/Windows/Fonts/arialbd.ttf",
    "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
]


def load_font(size):
    for path in FONT_CANDIDATES:
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def _lerp(a, b, t):
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def vertical_gradient(size, top, bottom):
    w, h = size
    img = Image.new("RGB", (1, h))
    px = img.load()
    for y in range(h):
        px[0, y] = _lerp(top, bottom, y / max(h - 1, 1))
    return img.resize((w, h), Image.BICUBIC)


def diagonal_gradient(size, start, end):
    """45-degree gradient, rendered small and upscaled."""
    w, h = size
    n = 64
    img = Image.new("RGB", (n, n))
    px = img.load()
    for y in range(n):
        for x in range(n):
            px[x, y] = _lerp(start, end, (x + y) / (2 * (n - 1)))
    return img.resize((w, h), Image.BICUBIC)


def mark_mask(size, inset=0.0):
    """
    Alpha mask of the logo mark on a transparent square of `size` px.

    `inset` shrinks the mark towards the centre, expressed as a fraction of the
    canvas - used to keep the adaptive-icon foreground inside its safe zone.
    """
    s = size * SS
    mask = Image.new("L", (s, s), 0)
    d = ImageDraw.Draw(mask)

    scale = 1.0 - 2 * inset
    cx = cy = s / 2

    def p(x, y):
        """Maps design coordinates in a -0.5..0.5 square onto the canvas."""
        return (cx + x * s * scale, cy + y * s * scale)

    # --- Play triangle, right of centre, corners rounded by a round-joined stroke
    r = 0.030 * s * scale
    tri = [p(0.045, -0.225), p(0.320, 0.0), p(0.045, 0.225)]
    d.polygon(tri, fill=255)
    d.line(tri + [tri[0]], fill=255, width=int(r * 2), joint="curve")
    for x, y in tri:
        d.ellipse([x - r, y - r, x + r, y + r], fill=255)

    # --- Two broadcast arcs opening to the right, left of the triangle
    stroke = 0.050 * s * scale
    for radius in (0.150, 0.270):
        rr = radius * s * scale
        box = [cx - rr, cy - rr, cx + rr, cy + rr]
        # Arc is drawn on the left-hand side, sweeping through 180 degrees.
        d.arc(box, start=118, end=242, fill=255, width=int(stroke))
        # Round the arc caps. Pillow strokes inwards from the bounding box, so
        # the stroke centreline sits half a stroke inside the nominal radius.
        cap_r = rr - stroke / 2
        cr = stroke / 2
        for angle in (118, 242):
            ax = cx + cap_r * math.cos(math.radians(angle))
            ay = cy + cap_r * math.sin(math.radians(angle))
            d.ellipse([ax - cr, ay - cr, ax + cr, ay + cr], fill=255)

    return mask.resize((size, size), Image.LANCZOS)


def glow(size, mask):
    """Soft accent halo behind the mark, so the icon reads on dark launchers."""
    blurred = mask.filter(ImageFilter.GaussianBlur(size * 0.06))
    layer = Image.new("RGB", (size, size), ACCENT_GREEN)
    out = Image.new("L", (size, size), 0)
    out.paste(blurred)
    return layer, out.point(lambda v: int(v * 0.22))


def square_icon(size, with_background=True, inset=0.0, rounded=False):
    mask = mark_mask(size, inset=inset)

    if with_background:
        base = vertical_gradient((size, size), NAVY_TOP, NAVY_BOTTOM).convert("RGBA")
        glow_layer, glow_mask = glow(size, mask)
        base.paste(glow_layer, (0, 0), glow_mask)
    else:
        base = Image.new("RGBA", (size, size), (0, 0, 0, 0))

    fg = diagonal_gradient((size, size), ACCENT_GREEN, ACCENT_CYAN).convert("RGBA")
    base.paste(fg, (0, 0), mask)

    if rounded and with_background:
        corner = Image.new("L", (size * SS, size * SS), 0)
        ImageDraw.Draw(corner).rounded_rectangle(
            [0, 0, size * SS - 1, size * SS - 1], radius=int(size * SS * 0.22), fill=255
        )
        base.putalpha(corner.resize((size, size), Image.LANCZOS))

    return base


def circle_icon(size):
    base = square_icon(size)
    circle = Image.new("L", (size * SS, size * SS), 0)
    ImageDraw.Draw(circle).ellipse([0, 0, size * SS - 1, size * SS - 1], fill=255)
    base.putalpha(circle.resize((size, size), Image.LANCZOS))
    return base


def feature_graphic(width=1024, height=500):
    """Play Store feature graphic. Must be fully opaque."""
    img = vertical_gradient((width, height), NAVY_TOP, NAVY_BOTTOM).convert("RGBA")

    # Faint arc motif bleeding off the right edge.
    deco = Image.new("L", (width * 2, height * 2), 0)
    dd = ImageDraw.Draw(deco)
    for radius in (320, 470, 620, 770):
        rr = radius * 2
        box = [width * 2 - rr, height - rr, width * 2 + rr, height + rr]
        dd.arc(box, start=100, end=260, fill=255, width=6)
    deco = deco.resize((width, height), Image.LANCZOS).point(lambda v: int(v * 0.18))
    img.paste(Image.new("RGB", (width, height), ACCENT_CYAN), (0, 0), deco)

    # Logo + wordmark lockup, centred so nothing is lost to platform cropping.
    mark_size = int(height * 0.46)
    mark = square_icon(mark_size, with_background=False)

    title_font = load_font(int(height * 0.20))
    tagline_font = load_font(int(height * 0.072))
    title, tagline = "TS IPTV", "Your playlists. Every screen."

    measure = ImageDraw.Draw(img)
    title_w = measure.textbbox((0, 0), title, font=title_font)[2]
    tagline_w = measure.textbbox((0, 0), tagline, font=tagline_font)[2]

    gap = int(height * 0.07)
    text_w = max(title_w, tagline_w)
    total_w = mark_size + gap + text_w
    x = (width - total_w) // 2

    img.paste(mark, (x, (height - mark_size) // 2), mark)

    text_x = x + mark_size + gap
    measure.text(
        (text_x, height // 2 - int(height * 0.145)), title,
        font=title_font, fill=(255, 255, 255),
    )
    measure.text(
        (text_x + 4, height // 2 + int(height * 0.075)), tagline,
        font=tagline_font, fill=ACCENT_CYAN,
    )

    return img.convert("RGB")


def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    print("wrote", os.path.relpath(path, ROOT))


def main():
    # --- Play Console upload set -------------------------------------------
    save(square_icon(512).convert("RGB"), os.path.join(BRAND_DIR, "play-icon-512.png"))
    save(square_icon(512).convert("RGB"), os.path.join(ANDROID_MAIN, "ic_launcher-playstore.png"))
    save(feature_graphic(), os.path.join(BRAND_DIR, "feature-graphic-1024x500.png"))
    save(square_icon(1024, rounded=True), os.path.join(BRAND_DIR, "app-icon-1024.png"))
    save(square_icon(180, rounded=True), os.path.join(BRAND_DIR, "favicon-180.png"))
    save(square_icon(32, rounded=True), os.path.join(BRAND_DIR, "favicon-32.png"))

    # --- Android launcher icons --------------------------------------------
    # Legacy (API 24-25) square + round icons.
    legacy = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for density, px in legacy.items():
        save(square_icon(px, rounded=True), os.path.join(RES_DIR, f"mipmap-{density}", "ic_launcher.png"))
        save(circle_icon(px), os.path.join(RES_DIR, f"mipmap-{density}", "ic_launcher_round.png"))

    # Adaptive foreground (API 26+): 108dp canvas, art confined to the 66dp safe zone.
    adaptive = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
    for density, px in adaptive.items():
        fg = square_icon(px, with_background=False, inset=0.16)
        save(fg, os.path.join(RES_DIR, f"mipmap-{density}", "ic_launcher_foreground.png"))

        # Themed-icon layer (Android 13+): the same mark as a flat white silhouette,
        # which the launcher then tints with the user's wallpaper colours.
        mono = Image.new("RGBA", (px, px), (0, 0, 0, 0))
        mono.putalpha(mark_mask(px, inset=0.16))
        mono.paste(Image.new("RGB", (px, px), (255, 255, 255)), (0, 0), mono.getchannel("A"))
        save(mono, os.path.join(RES_DIR, f"mipmap-{density}", "ic_launcher_monochrome.png"))


if __name__ == "__main__":
    main()
