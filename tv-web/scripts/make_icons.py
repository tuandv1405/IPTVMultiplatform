"""Generates the TV icons and splash from brand/ (needs Pillow). Run from the repo root:

    python tv-web/scripts/make_icons.py
"""
import pathlib

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
BRAND = ROOT / "brand"
TV = ROOT / "tv-web"
NAVY = (3, 4, 29, 255)


def icon(size, out):
    img = Image.open(BRAND / "app-icon-1024.png").convert("RGBA").resize((size, size), Image.LANCZOS)
    img.save(out)


def padded(width, height, out, scale=0.8):
    """The square icon centred on a navy canvas (Tizen wants 512x423)."""
    canvas = Image.new("RGBA", (width, height), NAVY)
    side = int(min(width, height) * scale)
    img = Image.open(BRAND / "app-icon-1024.png").convert("RGBA").resize((side, side), Image.LANCZOS)
    canvas.alpha_composite(img, ((width - side) // 2, (height - side) // 2))
    canvas.save(out)


def splash(out):
    banner = Image.open(BRAND / "tv-banner-1280x720.png").convert("RGB").resize((1920, 1080), Image.LANCZOS)
    banner.save(out)


if __name__ == "__main__":
    icon(80, TV / "platforms/webos/icon.png")
    icon(130, TV / "platforms/webos/largeIcon.png")
    splash(TV / "platforms/webos/splash.png")
    padded(512, 423, TV / "platforms/tizen/icon.png")
    icon(128, TV / "src/img/logo.png")
    print("icons written")
