#!/usr/bin/env python3
"""Builds the format guide pages (web/public/guides/**) from their sources.

    python web/scripts/build_guides.py          # write every page
    python web/scripts/build_guides.py --check  # exit 1 if a page on disk differs from its sources

Sources: web/guides-src/<name>.vi.html and <name>.en.html (the body of each language pane;
<name> is the page slug, "hub" for /guides/). Page metadata (titles, descriptions, noindex,
breadcrumb) is the PAGES table below. The shared <head>, header, footer and scripts are TEMPLATE.
Every <pre data-example="/examples/..."> block is filled from the referenced file, so edit the
example file, not the block. After building, run web/scripts/check_guides.py.
"""
import html
import pathlib
import re
import sys

WEB = pathlib.Path(__file__).resolve().parent.parent
SRC = WEB / "guides-src"
PUBLIC = WEB / "public"
OUT = PUBLIC / "guides"
BASE = "https://tsiptv-8bdd6.web.app"

PAGES = [
    # slug, crumb vi, crumb en, title vi, title en, desc vi, desc en, noindex
    ("", None, None,
     "Hướng dẫn định dạng — TS IPTV",
     "Format guides — TS IPTV",
     "Hướng dẫn các định dạng danh sách phát và lịch phát sóng mà TS IPTV đọc được: M3U, XMLTV, XSPF, JSON, đường dẫn kiểu Xtream Codes và định dạng của Kodi.",
     "Guides to the playlist and programme guide formats TS IPTV reads: M3U, XMLTV, XSPF, JSON, Xtream Codes style links and Kodi formats.",
     False),
    ("iptv", "IPTV là gì", "What is IPTV",
     "IPTV là gì — Hướng dẫn TS IPTV",
     "What is IPTV — TS IPTV guides",
     "IPTV là gì, luồng, danh sách phát và lịch phát sóng hoạt động ra sao, và TS IPTV làm gì (cũng như không làm gì).",
     "What IPTV is, how streams, playlists and programme guides fit together, and what TS IPTV does and does not do.",
     False),
    ("m3u", "M3U / M3U8", "M3U / M3U8",
     "M3U và M3U8 — Hướng dẫn TS IPTV",
     "M3U and M3U8 — TS IPTV guides",
     "Cú pháp danh sách phát M3U/M3U8 cho IPTV: thuộc tính, nhóm, header, cách tự viết, đưa lên mạng và thêm vào TS IPTV.",
     "The M3U/M3U8 IPTV playlist syntax: attributes, groups, headers, how to write one, host it and add it to TS IPTV.",
     False),
    ("xmltv", "XMLTV", "XMLTV",
     "Lịch phát sóng XMLTV — Hướng dẫn TS IPTV",
     "XMLTV programme guides — TS IPTV guides",
     "Cấu trúc tệp lịch phát sóng XMLTV, định dạng thời gian, cách liên kết với danh sách phát và cách dùng trong TS IPTV.",
     "The XMLTV programme guide structure, time format, how it links to a playlist and how TS IPTV uses it.",
     False),
    ("xspf", "XSPF", "XSPF",
     "Danh sách phát XSPF — Hướng dẫn TS IPTV",
     "XSPF playlists — TS IPTV guides",
     "Cú pháp danh sách phát XSPF (kể cả phần mở rộng của VLC), cách tạo, đưa lên mạng và thêm vào TS IPTV.",
     "The XSPF playlist syntax (including VLC's extension), how to create one, host it and add it to TS IPTV.",
     False),
    ("json", "JSON", "JSON",
     "Danh sách phát JSON — Hướng dẫn TS IPTV",
     "JSON playlists — TS IPTV guides",
     "Hai dạng danh sách phát JSON mà TS IPTV đọc được: JSON chung và dạng streams.json của iptv-org, kèm ví dụ đầy đủ.",
     "The two JSON playlist shapes TS IPTV reads: generic JSON and the iptv-org streams.json shape, with complete examples.",
     False),
    ("xtream-codes", "Xtream Codes", "Xtream Codes",
     "Đường dẫn kiểu Xtream Codes — Hướng dẫn TS IPTV",
     "Xtream Codes style links — TS IPTV guides",
     "Đường dẫn kiểu Xtream Codes là API của nhà cung cấp, không phải định dạng tệp. Cấu trúc đường dẫn và cách dùng trong TS IPTV.",
     "Xtream Codes style links are a provider API, not a file format. The URL shapes and how to use them in TS IPTV.",
     False),
    ("kodi", "Kodi", "Kodi",
     "Định dạng Kodi trong TS IPTV — Hướng dẫn TS IPTV",
     "Kodi formats in TS IPTV — TS IPTV guides",
     "TS IPTV đọc những gì từ danh sách phát kiểu Kodi PVR IPTV Simple (#KODIPROP, DRM, xem lại, .strm) và những gì không chạy được.",
     "What TS IPTV reads from Kodi PVR IPTV Simple style playlists (#KODIPROP, DRM, catch-up, .strm) and what it cannot run.",
     False),
    ("stremio-addons", "Addon tương thích Stremio", "Stremio-compatible addons",
     "Addon tương thích Stremio — Hướng dẫn TS IPTV",
     "Stremio-compatible addons — TS IPTV guides",
     "Giao thức addon mở: manifest, danh mục, chi tiết, luồng, cách tự tạo addon bằng tệp JSON tĩnh hoặc SDK Node.js, và cách thêm vào TS IPTV.",
     "The open addon protocol: manifest, catalogues, details, streams, how to build an addon as static JSON or with the Node.js SDK, and how to add it in TS IPTV.",
     False),
    ("tsiptv-source", "TS IPTV Source", "TS IPTV Source",
     "Định dạng TS IPTV Source — Hướng dẫn TS IPTV",
     "TS IPTV Source format — TS IPTV guides",
     "Định dạng mở của TS IPTV cho cả một bộ sưu tập: thông tin, giao diện, bố cục trang chủ, kênh, phim lẻ, phim bộ, lịch phát sóng và include. Tham khảo đầy đủ, ví dụ và cách tự tạo.",
     "TS IPTV's open format for a whole collection: metadata, appearance, home layout, channels, movies, series, guides and includes. Full reference, examples and a step-by-step guide.",
     False),
    ("tsiptv-source/validate", "Kiểm tra TS IPTV Source", "Validate a TS IPTV Source",
     "Kiểm tra TS IPTV Source — Hướng dẫn TS IPTV",
     "Validate a TS IPTV Source — TS IPTV guides",
     "Kiểm tra một tệp TS IPTV Source ngay trong trình duyệt, không tải tệp lên.",
     "Check a TS IPTV Source file in your browser, without uploading it.",
     True),
    ("monplayer", "MonPlayer", "MonPlayer",
     "Về định dạng của MonPlayer — Hướng dẫn TS IPTV",
     "About MonPlayer's format — TS IPTV guides",
     "Vì sao TS IPTV chưa nhập được nguồn JSON của MonPlayer và cần gì để hỗ trợ.",
     "Why TS IPTV cannot import MonPlayer's JSON sources yet and what it needs to add support.",
     True),
]

TEMPLATE = """<!doctype html>
<html lang="vi" data-title-vi="{tvi}" data-title-en="{ten}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>{tvi}</title>
<meta name="description" content="{dvi}">
{robots}<link rel="canonical" href="{url}">
<link rel="alternate" hreflang="vi" href="{url}">
<link rel="alternate" hreflang="en" href="{url}?lang=en">
<link rel="alternate" hreflang="x-default" href="{url}">
<meta property="og:title" content="{tvi}">
<meta property="og:description" content="{dvi}">
<meta property="og:image" content="{base}/assets/og-image.png">
<meta property="og:type" content="article">
<meta property="og:url" content="{url}">
<link rel="icon" href="/assets/favicon-32.png" sizes="32x32">
<link rel="apple-touch-icon" href="/assets/apple-touch-icon.png">
<link rel="stylesheet" href="/assets/site.css">
</head>
<body>

<header class="site">
  <div class="wrap">
    <img src="/assets/app-icon.png" alt="">
    <span class="name">TS IPTV</span>
    <nav>
      <a href="/guides/" aria-current="page">Hướng dẫn / Guides</a>
      <a href="/">Trang chủ / Home</a>
      <a href="/privacy/">Quyền riêng tư / Privacy</a>
    </nav>
  </div>
</header>

<main class="wrap">

  <ul class="langbar">
    <li><button type="button" data-lang="vi" aria-pressed="true">Tiếng Việt</button></li>
    <li><button type="button" data-lang="en" aria-pressed="false">English</button></li>
  </ul>

  <!-- ==================== VIETNAMESE ==================== -->
  <article data-lang-pane="vi">
{crumb_vi}{body_vi}
  </article>

  <!-- ==================== ENGLISH ==================== -->
  <article data-lang-pane="en" hidden>
{crumb_en}{body_en}
  </article>

</main>

<footer class="site">
  <div class="wrap">
    TS IPTV · <a href="/guides/">Hướng dẫn / Guides</a> · <a href="/">Trang chủ / Home</a> · <a href="/privacy/">Quyền riêng tư / Privacy</a> · <a href="/terms/">Điều khoản / Terms</a>
  </div>
</footer>

<script src="/assets/lang.js"></script>
<script src="/assets/guides.js"></script>
</body>
</html>
"""


def crumb(label, home, aria):
    if label is None:
        return ""
    return (f'    <nav class="breadcrumb" aria-label="{aria}"><a href="/guides/">{home}</a> › '
            f'<span aria-current="page">{label}</span></nav>\n')


PRE_EXAMPLE_RE = re.compile(
    r'(<pre\b[^>]*\bdata-example="(?P<src>[^"]+)"[^>]*>\s*<code[^>]*>)(?P<body>.*?)(</code>\s*</pre>)',
    re.S)


def _norm(text):
    return text.replace("\r\n", "\n").replace("\r", "\n").rstrip("\n")


def fill_examples(page):
    """Replace the body of every <pre data-example> block with the escaped referenced file."""
    def repl(m):
        file = PUBLIC / m.group("src").lstrip("/")
        if not file.is_file():
            return m.group(0)   # check_guides.py reports the missing file
        body = html.escape(_norm(file.read_text(encoding="utf-8")), quote=False)
        return m.group(1) + body + m.group(4)
    return PRE_EXAMPLE_RE.sub(repl, page)


def render_all():
    """{output path: page text} for every guide page."""
    pages = {}
    for slug, cvi, cen, tvi, ten, dvi, den, noindex in PAGES:
        name = slug.replace("/", "-") if slug else "hub"   # tsiptv-source/validate → tsiptv-source-validate
        body_vi = (SRC / f"{name}.vi.html").read_text(encoding="utf-8").rstrip()
        body_en = (SRC / f"{name}.en.html").read_text(encoding="utf-8").rstrip()
        url = f"{BASE}/guides/{slug + '/' if slug else ''}"
        page = TEMPLATE.format(
            tvi=html.escape(tvi), ten=html.escape(ten), dvi=html.escape(dvi),
            robots='<meta name="robots" content="noindex">\n' if noindex else "",
            url=url, base=BASE,
            crumb_vi=crumb(cvi, "Hướng dẫn", "Vị trí trang"),
            crumb_en=crumb(cen, "Guides", "Breadcrumb"),
            body_vi=body_vi, body_en=body_en,
        )
        # The English description (den) is kept for reviewers; the <head> carries the
        # Vietnamese description because Vietnamese is the default pane.
        target = OUT / slug / "index.html" if slug else OUT / "index.html"
        pages[target] = fill_examples(page)
    return pages


def stale_pages():
    """Output pages that are missing or differ from what the sources produce."""
    stale = []
    for target, text in render_all().items():
        if not target.is_file() or target.read_bytes() != text.encode("utf-8"):
            stale.append(target)
    return stale


def main():
    if "--check" in sys.argv[1:]:
        stale = stale_pages()
        for p in stale:
            print(f"out of date: {p.relative_to(WEB.parent).as_posix()}")
        return 1 if stale else 0
    for target, text in render_all().items():
        target.parent.mkdir(parents=True, exist_ok=True)
        old = target.read_bytes() if target.is_file() else None
        new = text.encode("utf-8")
        if old != new:
            target.write_bytes(new)
            print(f"wrote {target.relative_to(WEB.parent).as_posix()}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
