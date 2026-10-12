"""Store listing images for LG Seller Lounge and Samsung TV Seller Office (needs Pillow).

    python tv-web/scripts/make_store_assets.py

Icons are drawn from the brand mark (brand/generate_assets.py). Screenshots come from
scripts/store-screenshots.js (1920x1080 PNG in tv-web/store/screenshots/<lang>/) and are converted
to the sizes and formats each store asks for.
"""
import pathlib
import sys

from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "brand"))
import generate_assets as brand  # noqa: E402

STORE = ROOT / "tv-web" / "store"
NAVY = brand.NAVY_BOTTOM


def mark(size):
    return brand.square_icon(size, with_background=False)


def flat_icon(width, height, scale):
    """The mark on a flat navy background: LG rejects icons with a gradient background."""
    img = Image.new("RGB", (width, height), NAVY)
    side = int(min(width, height) * scale)
    m = mark(side)
    img.paste(m, ((width - side) // 2, (height - side) // 2), m)
    return img


def logo_1920():
    """Samsung's 1920x1080 icon, part 1: mark and wordmark on transparency (32-bit PNG)."""
    width, height = 1920, 1080
    img = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    mark_size = int(height * 0.36)
    font = brand.load_font(int(height * 0.17))
    draw = ImageDraw.Draw(img)
    bbox = draw.textbbox((0, 0), "TS IPTV", font=font)
    title_w, title_h = bbox[2] - bbox[0], bbox[3] - bbox[1]
    gap = int(height * 0.05)
    x = (width - (mark_size + gap + title_w)) // 2
    m = mark(mark_size)
    img.alpha_composite(m, (x, (height - mark_size) // 2))
    draw.text((x + mark_size + gap - bbox[0], (height - title_h) // 2 - bbox[1]), "TS IPTV",
              font=font, fill=(255, 255, 255, 255))
    return img


def background_1920():
    """Samsung's 1920x1080 icon, part 2: background (24-bit)."""
    return brand.vertical_gradient((1920, 1080), brand.NAVY_TOP, brand.NAVY_BOTTOM)


def save(img, path, **kwargs):
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, **kwargs)
    print(f"wrote {path.relative_to(ROOT)} ({path.stat().st_size // 1024} KB)")


def save_jpeg(img, path, limit_kb):
    """Highest JPEG quality under the store's size limit."""
    for quality in (92, 88, 84, 80, 75, 70, 60):
        path.parent.mkdir(parents=True, exist_ok=True)
        img.save(path, "JPEG", quality=quality, optimize=True)
        if path.stat().st_size <= limit_kb * 1024:
            break
    print(f"wrote {path.relative_to(ROOT)} ({path.stat().st_size // 1024} KB, q{quality})")


def screenshots():
    for source_dir in sorted((STORE / "screenshots").glob("*")):
        if not source_dir.is_dir():
            continue
        lang = source_dir.name
        for shot in sorted(source_dir.glob("*.png")):
            img = Image.open(shot).convert("RGB")
            # Samsung: 1920x1080 JPG under 500 KB. LG: 1280x720.
            save_jpeg(img, STORE / "samsung" / "screenshots" / lang / f"{shot.stem}.jpg", 500)
            save_jpeg(img.resize((1280, 720), Image.LANCZOS),
                      STORE / "lg" / "screenshots" / lang / f"{shot.stem}.jpg", 1024)


if __name__ == "__main__":
    save(flat_icon(400, 400, 0.78), STORE / "lg" / "icon-400x400.png")
    save(Image.open(ROOT / "tv-web" / "platforms" / "webos" / "splash.png").convert("RGB"),
         STORE / "lg" / "background-1920x1080.png")
    save(flat_icon(512, 423, 0.8), STORE / "samsung" / "icon-512x423.png")
    save(logo_1920(), STORE / "samsung" / "icon-1920x1080-logo.png", optimize=True)
    save(background_1920(), STORE / "samsung" / "icon-1920x1080-background.png", optimize=True)
    screenshots()
