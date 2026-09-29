#!/usr/bin/env python3
"""Checks for the website format guides (docs/prd-web-format-guides.md, section "Checks").

    python web/scripts/check_guides.py          # check only; exit 1 on any problem
    python web/scripts/check_guides.py --fix    # first run build_guides.py (regenerate pages from
                                                # web/guides-src and example files), then check

What is checked
  1. Every web/public/examples/*.tsiptv.json validates against schema/tsiptv-source-v1.json
     (JSON Schema draft 2020-12). Every other example/policy JSON file parses; XML examples are
     well-formed; the F4-0 fixture files exist.
  2. Every <pre data-example="/examples/..."> block equals the referenced file after HTML-entity
     decoding and line-ending normalisation (a trailing newline is not significant).
  3. Every format page has the eight anchors in both panes (vi: "what", en: "what-en", ...),
     and both panes are non-empty, have one <h1> and the "no channels" callout.
  4. Denylist terms are absent from web/public; every outbound link on a guide page is on the
     allowlist; every URL written in guide pages and example files is a permitted host.
  5. Every internal link (and #fragment) on the guide pages resolves to a file (and an id).
  6. <title>, description, canonical, hreflang (vi, en, x-default) and data-title-* are present;
     the MonPlayer page is noindex, has no outbound links and is not in the sitemap; the sitemap
     lists every indexable page; robots.txt points to it.
  7. The header nav and the footer of every page of the site link to /guides/.
  8. Every generated page equals what web/scripts/build_guides.py produces from web/guides-src.
  9. The browser validator passes web/scripts/test_validator.js (when Node is installed).
 10. Every page has the shared header nav and speculation rules of web/scripts/site_nav.py.

Requires Python 3.9+ and `jsonschema` (pip install jsonschema).
"""
from __future__ import annotations

import html
import json
import pathlib
import re
import sys
import xml.etree.ElementTree as ET
from html.parser import HTMLParser
from urllib.parse import unquote, urlsplit

WEB = pathlib.Path(__file__).resolve().parent.parent
PUBLIC = WEB / "public"
GUIDES = PUBLIC / "guides"
SITE = "https://tsiptv-8bdd6.web.app"

FORMAT_PAGES = ["m3u", "xmltv", "xspf", "json", "xtream-codes", "kodi", "stremio-addons", "tsiptv-source"]
ANCHORS = ["what", "syntax", "example", "create", "host", "add", "mistakes", "support"]
NOINDEX_GUIDES = {"monplayer", "tsiptv-source/validate"}

# F4-0 fixtures that must exist (PRD "Example files" + research/stremio-addons.md section 5.2).
REQUIRED_FILES = [
    "examples/playlist.m3u",
    "examples/guide.xml",
    "examples/playlist.xspf",
    "examples/playlist.json",
    "examples/iptv-org-streams.json",
    "examples/channel.strm",
    "examples/stremio-sampler/manifest.json",
    "examples/stremio-sampler/catalog/movie/tspd-movie.json",
    "examples/stremio-sampler/catalog/movie/tspd-movie/genre=Comedy.json",
    "examples/stremio-sampler/catalog/series/tspd-series.json",
    "examples/stremio-sampler/catalog/tv/tspd-tv.json",
    "examples/stremio-sampler/meta/movie/tspd_his_girl_friday.json",
    "examples/stremio-sampler/meta/series/tspd_superman.json",
    "examples/stremio-sampler/meta/tv/tspd_test_hls.json",
    "examples/stremio-sampler/stream/movie/tspd_his_girl_friday.json",
    "examples/stremio-sampler/stream/series/tspd_superman_s1e1.json",
    "examples/stremio-sampler/stream/tv/tspd_test_hls.json",
    "policy/addon-blocklist.json",
]

# Outbound link allowlist: (host, path prefix). Subdomains of a host are allowed.
LINK_ALLOW = [
    ("rfc-editor.org", ""),
    ("github.com", "/XMLTV/xmltv"),
    ("xspf.org", ""),
    ("github.com", "/kodi-pvr/pvr.iptvsimple"),
    ("github.com", "/xbmc/inputstream.adaptive/wiki"),
    ("github.com", "/Stremio/stremio-addon-sdk"),
    ("json-schema.org", ""),
    ("developer.android.com", ""),
    ("w3.org", ""),
    ("tsiptv-8bdd6.web.app", ""),
]

# Hosts that may appear as URLs in guide text and example files (content rule 2).
PLACEHOLDER_HOSTS = ["example.com", "example.org", "example.net", "example"]
MUX_URL = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
LOCAL_HOSTS = {"127.0.0.1", "localhost", "10.0.2.2"}
ARCHIVE_ITEMS =["his_girl_friday", "superman_the_mechanical_monsters"]
OWN_PREFIXES = ["/examples/", "/schema/", "/policy/", "/guides/", "/assets/", "/sitemap.xml"]
# XML namespace names are identifiers, not links.
NAMESPACE_URIS = {
    "http://xspf.org/ns/0/",
    "http://www.videolan.org/vlc/playlist/ns/0/",
    "http://www.videolan.org/vlc/playlist/0",
    "https://json-schema.org/draft/2020-12/schema",
    "http://www.sitemaps.org/schemas/sitemap/0.9",
    "http://www.w3.org/1999/xhtml",
}

DENY_TERMS = ["strem.io", "strem.fun", "stremio.net", "beamup", "addonscollection",
              "iptv-org.github.io", "monplayer.org", "org.monplayer", "xoilac", "xôi lạc"]
DENY_REGEXES = [
    re.compile(r"repository\.[a-z0-9]", re.I),
    re.compile(r"plugin\.video\.[a-z0-9]", re.I),
    re.compile(r"get\.php\?username=(?!U(?![A-Za-z0-9_]))", re.I),
]
TEXT_EXT = {".html", ".js", ".css", ".json", ".m3u", ".m3u8", ".xml", ".xspf", ".strm", ".txt", ".md"}

URL_RE = re.compile(r"""https?://[^\s"'<>`)\]]+""")
problems: list[str] = []


def urls_in(text: str):
    """URLs written in text. A URL followed by "<" is a shape with a placeholder (<user>)."""
    for m in URL_RE.finditer(text):
        tail = text[m.end():m.end() + 1]
        yield m.group(0) + ("<" if tail == "<" else "")


def fail(where, message):
    problems.append(f"{where}: {message}")


def rel(path: pathlib.Path) -> str:
    return path.relative_to(WEB.parent).as_posix()


def read(path: pathlib.Path) -> str:
    return path.read_text(encoding="utf-8")


def norm(text: str) -> str:
    return text.replace("\r\n", "\n").replace("\r", "\n").rstrip("\n")


def public_path(url_path: str) -> pathlib.Path:
    """Map a site path (/guides/m3u/) to the file Firebase Hosting would serve."""
    path = unquote(url_path)
    target = PUBLIC / path.lstrip("/")
    if path.endswith("/") or path == "":
        return target / "index.html"
    if target.is_file():
        return target
    if (target / "index.html").is_file():
        return target / "index.html"
    if target.with_suffix(".html").is_file():  # cleanUrls
        return target.with_suffix(".html")
    return target


def host_ok(host: str, allowed: str) -> bool:
    return host == allowed or host.endswith("." + allowed)


def link_allowed(url: str) -> bool:
    parts = urlsplit(url)
    host = (parts.hostname or "").lower()
    return any(host_ok(host, h) and parts.path.startswith(p) for h, p in LINK_ALLOW)


def content_url_allowed(url: str) -> bool:
    url = url.rstrip(".,;:")
    if url in NAMESPACE_URIS or "<" in url or "…" in url:
        return True
    parts = urlsplit(url)
    host = (parts.hostname or "").lower()
    if any(host_ok(host, h) for h in PLACEHOLDER_HOSTS):
        return True
    if url == MUX_URL:
        return True
    if host in LOCAL_HOSTS:   # loopback / emulator addresses in "test it locally" steps
        return True
    if host == "archive.org":
        return any(parts.path in (f"/download/{i}/{i}_512kb.mp4", f"/services/img/{i}", f"/details/{i}")
                   for i in ARCHIVE_ITEMS)
    if host == "tsiptv-8bdd6.web.app":
        return parts.path == "/" or any(parts.path.startswith(p) for p in OWN_PREFIXES)
    return link_allowed(url)


class Page(HTMLParser):
    """Collects what the checks need, per language pane."""

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.panes: dict[str, dict] = {}
        self.pane = None           # current pane language
        self.pane_tag = None
        self.pane_depth = 0
        self.ids_all: set[str] = set()
        self.links: list[tuple[str, str | None]] = []   # (url, pane)
        self.meta: dict[str, str] = {}
        self.alternates: dict[str, str] = {}
        self.canonical = None
        self.html_attrs: dict[str, str] = {}
        self.title = ""
        self.in_title = False
        self.pre = None            # (src, pane, chunks) while inside <pre>
        self.pres: list[tuple[str | None, str | None, str]] = []
        self.region = []           # stack of ("header"|"footer", tag depth)
        self.header_links: list[str] = []
        self.footer_links: list[str] = []
        self.in_header = 0
        self.in_footer = 0
        self.in_header_nav = 0
        self.refresh = False
        self.text_chunks: list[str] = []   # visible text of guide content (for URL checks)

    def _pane_data(self):
        return self.panes.setdefault(self.pane, {"ids": set(), "h1": 0, "text": 0, "no_channels": False})

    def handle_starttag(self, tag, attrs):
        a = {k: (v if v is not None else "") for k, v in attrs}
        if tag == "html":
            self.html_attrs = a
        if tag == "title":
            self.in_title = True
        if tag == "meta":
            if a.get("http-equiv", "").lower() == "refresh":
                self.refresh = True
            key = a.get("name") or a.get("property")
            if key:
                self.meta[key.lower()] = a.get("content", "")
        if tag == "link":
            relv = a.get("rel", "").lower()
            if relv == "canonical":
                self.canonical = a.get("href")
            elif relv == "alternate" and a.get("hreflang"):
                self.alternates[a["hreflang"].lower()] = a.get("href", "")
        if tag == "header" and "site" in a.get("class", "").split():
            self.in_header += 1
        elif tag == "header" and self.in_header:
            self.in_header += 1
        if tag == "footer" and "site" in a.get("class", "").split():
            self.in_footer += 1
        elif tag == "footer" and self.in_footer:
            self.in_footer += 1
        if tag == "nav" and self.in_header:
            self.in_header_nav += 1

        if "data-lang-pane" in a and self.pane is None:
            self.pane = a["data-lang-pane"]
            self.pane_tag = tag
            self.pane_depth = 1
            self._pane_data()
        elif self.pane is not None and tag == self.pane_tag:
            self.pane_depth += 1

        if "id" in a:
            self.ids_all.add(a["id"])
            if self.pane is not None:
                self._pane_data()["ids"].add(a["id"])
        if self.pane is not None:
            if tag == "h1":
                self._pane_data()["h1"] += 1
            if "data-no-channels" in a:
                self._pane_data()["no_channels"] = True
        for attr in ("href", "src"):
            if attr in a:
                self.links.append((a[attr], self.pane))
                if self.in_header_nav:
                    self.header_links.append(a[attr])
                if self.in_footer:
                    self.footer_links.append(a[attr])
        if tag == "pre":
            self.pre = (a.get("data-example"), self.pane, [])

    def handle_endtag(self, tag):
        if tag == "title":
            self.in_title = False
        if tag == "nav" and self.in_header_nav:
            self.in_header_nav -= 1
        if tag == "header" and self.in_header:
            self.in_header -= 1
        if tag == "footer" and self.in_footer:
            self.in_footer -= 1
        if tag == "pre" and self.pre is not None:
            src, pane, chunks = self.pre
            self.pres.append((src, pane, "".join(chunks)))
            self.pre = None
        if self.pane is not None and tag == self.pane_tag:
            self.pane_depth -= 1
            if self.pane_depth == 0:
                self.pane = None
                self.pane_tag = None

    def handle_data(self, data):
        if self.in_title:
            self.title += data
        if self.pre is not None:
            self.pre[2].append(data)
        if self.pane is not None:
            self._pane_data()["text"] += len(data.strip())
            self.text_chunks.append(data)


def parse(path: pathlib.Path) -> Page:
    page = Page()
    page.feed(read(path))
    page.close()
    return page


# ---------------------------------------------------------------------------------------------

def check_examples_and_schema():
    for name in REQUIRED_FILES:
        if not (PUBLIC / name).is_file():
            fail(f"web/public/{name}", "required fixture file is missing")

    try:
        import jsonschema
    except ImportError:
        fail("check_guides.py", "the jsonschema package is not installed (pip install jsonschema)")
        jsonschema = None

    schema_path = PUBLIC / "schema" / "tsiptv-source-v1.json"
    schema = None
    if schema_path.is_file():
        try:
            schema = json.loads(read(schema_path))
        except json.JSONDecodeError as e:
            fail(rel(schema_path), f"invalid JSON: {e}")
    else:
        fail(rel(schema_path), "missing")

    if jsonschema and schema:
        validator_cls = jsonschema.Draft202012Validator
        try:
            validator_cls.check_schema(schema)
        except jsonschema.SchemaError as e:
            fail(rel(schema_path), f"not a valid 2020-12 schema: {e.message}")
        validator = validator_cls(schema, format_checker=validator_cls.FORMAT_CHECKER)
        for path in sorted((PUBLIC / "examples").glob("*.tsiptv.json")):
            try:
                doc = json.loads(read(path))
            except json.JSONDecodeError as e:
                fail(rel(path), f"invalid JSON: {e}")
                continue
            for err in sorted(validator.iter_errors(doc), key=lambda e: list(e.path)):
                location = "/" + "/".join(str(p) for p in err.path)
                fail(rel(path), f"schema: {location} — {err.message}")

    for folder in ("examples", "policy", "schema"):
        for path in sorted((PUBLIC / folder).rglob("*")):
            if not path.is_file():
                continue
            if path.suffix == ".json":
                try:
                    json.loads(read(path))
                except json.JSONDecodeError as e:
                    fail(rel(path), f"invalid JSON: {e}")
            elif path.suffix in (".xml", ".xspf"):
                try:
                    ET.fromstring(path.read_bytes())
                except ET.ParseError as e:
                    fail(rel(path), f"XML not well-formed: {e}")
            if path.suffix in TEXT_EXT:
                for url in urls_in(read(path)):
                    if not content_url_allowed(url):
                        fail(rel(path), f"URL not allowed in examples: {url}")

    blocklist = PUBLIC / "policy" / "addon-blocklist.json"
    if blocklist.is_file():
        try:
            data = json.loads(read(blocklist))
            if not (isinstance(data, dict) and isinstance(data.get("ids"), list) and isinstance(data.get("hosts"), list)):
                fail(rel(blocklist), 'must be {"ids": [...], "hosts": [...]}')
        except json.JSONDecodeError:
            pass


def check_denylist():
    for path in sorted(PUBLIC.rglob("*")):
        if not path.is_file() or path.suffix.lower() not in TEXT_EXT:
            continue
        text = read(path)
        lower = text.lower()
        for term in DENY_TERMS:
            if term in lower:
                fail(rel(path), f"denylisted term: {term!r}")
        for rx in DENY_REGEXES:
            for m in rx.finditer(text):
                fail(rel(path), f"denylisted pattern {rx.pattern!r}: {m.group(0)!r}")


def guide_pages():
    return sorted(GUIDES.rglob("index.html"))


def slug_of(path: pathlib.Path) -> str:
    return path.parent.relative_to(GUIDES).as_posix().replace(".", "")


def check_guide_page(path: pathlib.Path, page: Page, pages: dict):
    where = rel(path)
    slug = slug_of(path)
    url_path = "/guides/" + (slug + "/" if slug else "")
    url = SITE + url_path

    # --- head -------------------------------------------------------------------------------
    if not page.title.strip():
        fail(where, "missing <title>")
    if not page.meta.get("description"):
        fail(where, "missing meta description")
    if page.canonical != url:
        fail(where, f"canonical should be {url}, is {page.canonical}")
    expected = {"vi": url, "en": url + "?lang=en", "x-default": url}
    for lang, href in expected.items():
        if page.alternates.get(lang) != href:
            fail(where, f'hreflang="{lang}" should be {href}, is {page.alternates.get(lang)}')
    for lang in ("vi", "en"):
        if not page.html_attrs.get(f"data-title-{lang}"):
            fail(where, f"<html> lacks data-title-{lang}")
    for key in ("og:title", "og:description", "og:image"):
        if not page.meta.get(key):
            fail(where, f"missing {key}")
    noindex = "noindex" in page.meta.get("robots", "")
    if (slug in NOINDEX_GUIDES) != noindex:
        fail(where, "noindex expected" if slug in NOINDEX_GUIDES else "unexpected noindex")

    # --- panes ------------------------------------------------------------------------------
    for lang in ("vi", "en"):
        pane = page.panes.get(lang)
        if not pane:
            fail(where, f"no {lang} pane")
            continue
        if pane["text"] < 200:
            fail(where, f"{lang} pane is (almost) empty")
        if pane["h1"] != 1:
            fail(where, f"{lang} pane has {pane['h1']} <h1> elements, expected 1")
        if not pane["no_channels"]:
            fail(where, f'{lang} pane lacks the "no channels" callout ([data-no-channels])')
    if slug in FORMAT_PAGES:
        for anchor in ANCHORS:
            if anchor not in page.panes.get("vi", {}).get("ids", set()):
                fail(where, f'vi pane lacks id="{anchor}"')
            if f"{anchor}-en" not in page.panes.get("en", {}).get("ids", set()):
                fail(where, f'en pane lacks id="{anchor}-en"')

    # --- example blocks ---------------------------------------------------------------------
    for src, pane, body in page.pres:
        if not src:
            continue
        file = PUBLIC / src.lstrip("/")
        if not src.startswith("/examples/") or not file.is_file():
            fail(where, f"data-example {src} does not point to a file under /examples/")
            continue
        if norm(body) != norm(read(file)):
            fail(where, f"[{pane}] example block differs from {src} (run with --fix)")

    # --- links ------------------------------------------------------------------------------
    for href, pane in page.links:
        if href.startswith(("mailto:", "tel:", "data:")):
            continue
        parts = urlsplit(href)
        if parts.scheme in ("http", "https"):
            if slug == "monplayer" and pane is not None:
                fail(where, f"MonPlayer page must have no outbound links: {href}")
            elif not link_allowed(href):
                fail(where, f"outbound link not on the allowlist: {href}")
            continue
        if parts.scheme:
            fail(where, f"unexpected link scheme: {href}")
            continue
        target_path = parts.path
        if not target_path:
            target = path
        elif target_path.startswith("/"):
            target = public_path(target_path)
        else:
            target = public_path((url_path + target_path))
        if target_path and not target.is_file():
            fail(where, f"internal link does not resolve: {href}")
            continue
        if target_path.startswith("/playlists") and pane is not None:
            # AC-W6: guide *content* (the language panes) must not link to the directory; the
            # shared site nav in the header is site chrome (web/scripts/site_nav.py).
            fail(where, "guide content must not link to /playlists/")
        if parts.fragment:
            ids = page.ids_all if target == path else pages.get(target, set())
            if target.suffix == ".html" and target not in pages and target != path:
                ids = parse(target).ids_all
            if target.suffix == ".html" and parts.fragment not in ids:
                fail(where, f"fragment #{parts.fragment} not found in {href}")

    # --- URLs written in the text (content rule 2) ------------------------------------------
    for chunk in page.text_chunks:
        for u in urls_in(chunk):
            if not content_url_allowed(u):
                fail(where, f"URL in text is not a permitted placeholder/test/spec URL: {u}")


def check_sitemap_and_robots(indexable: set[str], noindex: set[str]):
    sitemap = PUBLIC / "sitemap.xml"
    if not sitemap.is_file():
        fail("web/public/sitemap.xml", "missing")
        return
    try:
        root = ET.fromstring(sitemap.read_bytes())
    except ET.ParseError as e:
        fail(rel(sitemap), f"not well-formed: {e}")
        return
    locs = {el.text.strip() for el in root.iter() if el.tag.endswith("loc") and el.text}
    for u in sorted(indexable - locs):
        fail(rel(sitemap), f"indexable page missing: {u}")
    for u in sorted(noindex & locs):
        fail(rel(sitemap), f"noindex page must not be listed: {u}")
    for u in sorted(locs):
        if not u.startswith(SITE + "/") or not public_path(urlsplit(u).path).is_file():
            fail(rel(sitemap), f"listed URL does not resolve to a page: {u}")
    robots = PUBLIC / "robots.txt"
    if not robots.is_file():
        fail("web/public/robots.txt", "missing")
    else:
        text = read(robots)
        if f"Sitemap: {SITE}/sitemap.xml" not in text:
            fail(rel(robots), "does not point to the sitemap")
        if re.search(r"^Disallow:\s*/\s*$", text, re.M):
            fail(rel(robots), "must allow all")


def main():
    sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
    import build_guides

    if "--fix" in sys.argv[1:]:
        build_guides.main()   # regenerate pages from web/guides-src (fills example blocks too)

    for target in build_guides.stale_pages():
        fail(rel(target), "out of date vs web/guides-src (run python web/scripts/build_guides.py)")

    # One header nav + speculation rules on every page (web/scripts/site_nav.py).
    import site_nav
    for path in site_nav.pages():
        text = path.read_text(encoding="utf-8")
        if site_nav.is_redirect_stub(text):
            continue
        if site_nav.normalize(text, site_nav.url_path_of(path)) != text.replace("\r\n", "\n"):
            fail(rel(path), "header nav or speculation rules differ (run python web/scripts/site_nav.py, "
                            "or build_guides.py for guide pages)")

    check_examples_and_schema()
    check_denylist()

    all_pages = {}
    for path in sorted(PUBLIC.rglob("*.html")):
        all_pages[path] = parse(path)
    ids_by_page = {p: pg.ids_all for p, pg in all_pages.items()}

    indexable, noindex = set(), set()
    for path, page in all_pages.items():
        url_path = "/" + path.parent.relative_to(PUBLIC).as_posix() + "/"
        url_path = url_path.replace("/./", "/").replace("//", "/")
        full = SITE + url_path
        if page.refresh:
            continue   # redirect stubs have no chrome
        if "noindex" in page.meta.get("robots", ""):
            noindex.add(full)
        else:
            indexable.add(full)
        # AC-W9: Guides link in header nav and footer on every page
        if "/guides/" not in page.header_links:
            fail(rel(path), "header nav lacks the /guides/ link")
        if "/guides/" not in page.footer_links:
            fail(rel(path), "footer lacks the /guides/ link")

    for path in guide_pages():
        check_guide_page(path, all_pages[path], ids_by_page)

    check_sitemap_and_robots(indexable, noindex)

    # The browser validator (wave C): run its Node test when Node is installed.
    validator_test = WEB / "scripts" / "test_validator.js"
    if validator_test.is_file():
        import shutil
        import subprocess
        node = shutil.which("node")
        if node:
            r = subprocess.run([node, str(validator_test)], capture_output=True, text=True, encoding="utf-8")
            if r.returncode != 0:
                fail(rel(validator_test), "failed:\n" + (r.stdout + r.stderr).strip())
        else:
            print("check_guides: note — node not found, validator test skipped")

    pages_checked = len(guide_pages())
    if problems:
        print(f"check_guides: {len(problems)} problem(s)")
        for p in problems:
            print("  - " + p)
        return 1
    examples = len(list((PUBLIC / "examples").rglob("*.*")))
    print(f"check_guides: OK — {pages_checked} guide pages, {len(all_pages)} site pages, "
          f"{examples} example files, schema and links clean")
    return 0


if __name__ == "__main__":
    sys.exit(main())
