#!/usr/bin/env python3
"""
Builds a self-contained demo playlist for capturing store screenshots.

Store listings must not show third-party broadcast content: a real playlist puts
someone else's video frames, station logos and channel marks into the images.
Everything this script produces is original artwork in the app's own palette,
with invented channel names, so the screenshots carry no third-party rights.

    python play-store/screenshots/make-demo-assets.py
    python -m http.server 8099 --directory <out dir>

Then import http://10.0.2.2:8099/demo.m3u in an emulator.

Requires Pillow and ffmpeg on PATH.
"""

import os
import shutil
import subprocess
import sys

from PIL import Image, ImageDraw, ImageFont

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "demo-server")

# Same palette as composeApp/.../ui/themes/TSColors.kt
NAVY_TOP = (12, 13, 44)
NAVY_BOTTOM = (3, 4, 29)
ACCENT = (0, 245, 160)
CYAN = (0, 217, 233)
WHITE = (243, 244, 246)

FONTS = [
    "C:/Windows/Fonts/segoeuib.ttf",
    "C:/Windows/Fonts/arialbd.ttf",
    "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
]

# Invented channels. Nothing here maps to a real broadcaster.
CHANNELS = [
    ("ts-one",       "TS One",         "Giải trí",  (0, 245, 160)),
    ("ts-news",      "TS News",        "Tin tức",   (59, 130, 246)),
    ("ts-sport",     "TS Sport",       "Thể thao",  (16, 185, 129)),
    ("ts-cinema",    "TS Cinema",      "Phim",      (168, 85, 247)),
    ("ts-kids",      "TS Kids",        "Thiếu nhi", (251, 191, 36)),
    ("ts-music",     "TS Music",       "Âm nhạc",   (236, 72, 153)),
    ("ts-discovery", "TS Discovery",   "Khám phá",  (0, 217, 233)),
    ("ts-life",      "TS Life",        "Đời sống",  (248, 113, 113)),
    ("ts-travel",    "TS Travel",      "Du lịch",   (52, 211, 153)),
    ("ts-classic",   "TS Classic",     "Phim",      (156, 163, 175)),
]


def font(size):
    for path in FONTS:
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def vertical_gradient(size, top, bottom):
    w, h = size
    strip = Image.new("RGB", (1, h))
    px = strip.load()
    for y in range(h):
        t = y / max(h - 1, 1)
        px[0, y] = tuple(round(a + (b - a) * t) for a, b in zip(top, bottom))
    return strip.resize((w, h), Image.BICUBIC)


def centered(draw, y, text, fnt, fill, width):
    w = draw.textbbox((0, 0), text, font=fnt)[2]
    draw.text(((width - w) / 2, y), text, font=fnt, fill=fill)


def make_logo(key, name, accent, size=256):
    """Channel logo: accent-tinted tile with the channel's initials."""
    ss = 4
    img = Image.new("RGBA", (size * ss, size * ss), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle(
        [0, 0, size * ss - 1, size * ss - 1],
        radius=int(size * ss * 0.24),
        fill=NAVY_TOP + (255,),
        outline=accent + (255,),
        width=int(size * ss * 0.03),
    )
    initials = "".join(part[0] for part in name.split()[:2]).upper()
    f = font(int(size * ss * 0.42))
    bbox = d.textbbox((0, 0), initials, font=f)
    d.text(
        ((size * ss - bbox[2]) / 2, (size * ss - bbox[3]) / 2 - bbox[1]),
        initials, font=f, fill=accent + (255,),
    )
    img = img.resize((size, size), Image.LANCZOS)
    path = os.path.join(OUT, "logos", f"{key}.png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    return path


def make_frame(key, name, group, accent, w=1920, h=1080):
    """The video frame each demo channel 'broadcasts'."""
    img = vertical_gradient((w, h), NAVY_TOP, NAVY_BOTTOM).convert("RGB")
    d = ImageDraw.Draw(img)

    # Faint concentric arcs, echoing the app icon.
    for r in (340, 520, 700, 880):
        d.arc([w // 2 - r, h // 2 - r, w // 2 + r, h // 2 + r],
              start=0, end=360, fill=(20, 32, 60), width=3)

    centered(d, h * 0.30, name, font(int(h * 0.13)), WHITE, w)
    centered(d, h * 0.47, group.upper(), font(int(h * 0.045)), accent, w)

    # An unmistakable marker that this is not real broadcast footage.
    badge = "SAMPLE CONTENT — NOT A BROADCAST"
    f = font(int(h * 0.030))
    bw = d.textbbox((0, 0), badge, font=f)[2]
    bx, by = (w - bw) / 2, h * 0.62
    pad = h * 0.018
    d.rounded_rectangle(
        [bx - pad * 1.6, by - pad, bx + bw + pad * 1.6, by + f.size + pad],
        radius=int(pad * 1.6), fill=(18, 22, 48), outline=(40, 52, 88), width=2,
    )
    d.text((bx, by), badge, font=f, fill=(140, 150, 175))

    centered(d, h * 0.80, "TS IPTV", font(int(h * 0.05)), (90, 100, 125), w)

    path = os.path.join(OUT, "frames", f"{key}.png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    return path


def make_stream(key, frame_path, seconds=900):
    """Loops one frame into a long HLS stream, so the player has live video."""
    out_dir = os.path.join(OUT, "streams", key)
    os.makedirs(out_dir, exist_ok=True)
    subprocess.run(
        [
            "ffmpeg", "-y", "-loglevel", "error",
            "-loop", "1", "-framerate", "5", "-i", frame_path,
            "-f", "lavfi", "-i", "anullsrc=channel_layout=stereo:sample_rate=44100",
            "-t", str(seconds),
            "-c:v", "libx264", "-preset", "ultrafast", "-tune", "stillimage",
            "-pix_fmt", "yuv420p", "-g", "10", "-b:v", "500k",
            "-c:a", "aac", "-b:a", "64k",
            "-f", "hls", "-hls_time", "6", "-hls_list_size", "0",
            "-hls_segment_filename", os.path.join(out_dir, "seg%04d.ts"),
            os.path.join(out_dir, "index.m3u8"),
        ],
        check=True,
    )
    return f"streams/{key}/index.m3u8"


def main():
    if shutil.which("ffmpeg") is None:
        print("ffmpeg not found on PATH", file=sys.stderr)
        return 1

    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    os.makedirs(OUT, exist_ok=True)

    base = "http://10.0.2.2:8099"
    lines = ['#EXTM3U', '#PLAYLIST:TS IPTV Demo']

    for key, name, group, accent in CHANNELS:
        make_logo(key, name, accent)
        frame = make_frame(key, name, group, accent)
        stream = make_stream(key, frame)
        lines.append(
            f'#EXTINF:-1 tvg-id="{key}" tvg-name="{name}" '
            f'tvg-logo="{base}/logos/{key}.png" group-title="{group}",{name}'
        )
        lines.append(f"{base}/{stream}")
        print("built", name)

    with open(os.path.join(OUT, "demo.m3u"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")

    print(f"\nWrote {OUT}")
    print(f"Serve:  python -m http.server 8099 --directory {OUT}")
    print(f"Import: {base}/demo.m3u")
    return 0



if __name__ == "__main__":
    sys.exit(main())
