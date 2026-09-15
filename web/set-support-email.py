#!/usr/bin/env python3
"""
Replaces the {{SUPPORT_EMAIL}} placeholder across web/public.

    python web/set-support-email.py support@example.com

Run this once before the first `firebase deploy`. The placeholder is deliberate:
a plausible-looking dummy address could be published unnoticed, whereas this one
cannot be missed.
"""

import io
import os
import re
import sys

PLACEHOLDER = "{{SUPPORT_EMAIL}}"
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "public")
EMAIL_RE = re.compile(r"^[^@\s]+@[^@\s]+\.[^@\s]+$")


def main(argv):
    if len(argv) != 2 or not EMAIL_RE.match(argv[1]):
        print(__doc__.strip())
        return 2

    email = argv[1]
    changed = 0

    for dirpath, _dirnames, filenames in os.walk(ROOT):
        for name in filenames:
            if not name.endswith((".html", ".css", ".js", ".txt")):
                continue
            path = os.path.join(dirpath, name)
            text = io.open(path, encoding="utf-8").read()
            if PLACEHOLDER not in text:
                continue
            io.open(path, "w", encoding="utf-8", newline="\n").write(
                text.replace(PLACEHOLDER, email)
            )
            print("updated", os.path.relpath(path, os.path.dirname(ROOT)))
            changed += 1

    if changed == 0:
        print("No occurrences of", PLACEHOLDER, "- already set?")
    else:
        print(f"\nSet support email to {email} in {changed} file(s).")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
