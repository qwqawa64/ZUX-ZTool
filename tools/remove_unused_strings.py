#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
remove_unused_strings.py -- Batch-remove UNUSED <string> entries from every
values*/*.xml, driven by the JSON produced by check_unused_strings.py.

Safety gates (a stale JSON can never delete a live resource):
  1. every name from the JSON is RE-VERIFIED against the current source tree
     (R.string.x / @string/x / quoted "x" literal) before deletion; names that
     gained any reference are skipped with a warning;
  2. removal targets whole <string ...>...</string> elements only (the
     (?=[\\s/>]) guard excludes <string-array>), spanning multi-line bodies;
  3. each modified file is XML-parsed BEFORE it is written back -- a parse
     failure aborts that file untouched;
  4. original line endings (CRLF/LF) are preserved.

Also removes comment lines whose section became empty (comment immediately
followed by another comment or </resources>) and collapses 3+ blank lines.

Usage:
  python tools/remove_unused_strings.py [--json PATH] [--dry-run]

Default JSON: app/build/reports/unused_strings.json (tiers.UNUSED).
After removal run: python tools/check_unused_strings.py  (expect UNUSED: 0)
                   .\\gradlew.bat assembleDebug
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

import importlib.util

REPO_ROOT = Path(__file__).resolve().parents[1]
APP_DIR = REPO_ROOT / "app"
DEFAULT_JSON = APP_DIR / "build" / "reports" / "unused_strings.json"
RES_DIR = APP_DIR / "src" / "main" / "res"

# import the checker module for file discovery + shared reading helpers
_spec = importlib.util.spec_from_file_location(
    "checker", Path(__file__).resolve().parent / "check_unused_strings.py")
checker = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(checker)

REF_RES = [
    re.compile(r"\bR\.string\.%s\b"),
    re.compile(r"@string/%s\b"),
]


def name_is_referenced(name: str, texts) -> tuple[bool, str]:
    """True if any live textual reference to the string resource exists."""
    esc = re.escape(name)
    base = [re.compile(r"\bR\.string\.%s\b" % esc),
            re.compile(r"@string/%s\b" % esc)]
    quoted = re.compile(r'"%s"' % esc)
    for path, text in texts:
        pats = base if path.suffix.lower() == ".xml" else base + [quoted]
        if any(p.search(text) for p in pats):
            return True, str(path.relative_to(REPO_ROOT))
    return False, ""


def element_re(name: str) -> re.Pattern:
    return re.compile(
        r'[ \t]*<string(?=[\s/>])[^>]*\bname="%s"[^>]*?'
        r'(?:/>|>.*?</string\s*>)[ \t]*\r?\n' % re.escape(name),
        re.S)


def strip_dangling_comments(text: str) -> tuple[str, int]:
    """Remove comment lines left with no content beneath them."""
    removed = 0
    pattern = re.compile(r'^[ \t]*<!--.*?-->[ \t]*\n', re.M)
    changed = True
    while changed:
        changed = False
        for m in list(pattern.finditer(text)):
            rest = text[m.end():]
            next_line = rest.split("\n", 1)[0].strip()
            if next_line == "" or next_line.startswith("<!--") \
                    or next_line.startswith("</resources>"):
                text = text[:m.start()] + text[m.end():]
                removed += 1
                changed = True
                break  # restart iteration, offsets shifted
    return text, removed


def declared_names(text: str) -> set[str]:
    return {m.group(1) for m in
            re.finditer(r'<string(?=[\s/>])[^>]*\bname="([^"]+)"', text)}


def process_file(path: Path, names: list[str]) -> tuple[int, list[str]]:
    """Remove elements for names present in this file. Returns (count, skipped)."""
    raw = path.read_bytes().decode("utf-8")
    crlf = "\r\n" in raw
    text = raw.replace("\r\n", "\n") if crlf else raw
    before = declared_names(text)
    removed, skipped = 0, []
    for name in names:
        new_text, n = element_re(name).subn("", text)
        if n:
            text, removed = new_text, removed + n
        elif re.search(r'<string(?=[\s/>])[^>]*\bname="%s"' % re.escape(name), text):
            skipped.append(name)  # present but pattern did not match -> review
    if not removed:
        return 0, skipped

    text, comments = strip_dangling_comments(text)
    text = re.sub(r"\n{3,}", "\n\n", text)

    # guard: no entry outside the removal list may have vanished (a malformed
    # body like "</string>>" can make the regex over-match into the next entry)
    vanished = (before - set(names)) - declared_names(text)
    if vanished:
        print(f"[abort] {path.relative_to(REPO_ROOT)}: removal would also drop "
              f"unrelated entries {sorted(vanished)} (malformed element body?); "
              f"file left untouched")
        sys.exit(1)

    try:  # validate BEFORE writing back
        ET.fromstring(text)
    except ET.ParseError as exc:
        print(f"[abort] {path.relative_to(REPO_ROOT)}: XML parse failed "
              f"after removal ({exc}); file left untouched")
        sys.exit(1)

    if not args.dry_run:
        out = text.replace("\n", "\r\n") if crlf else text
        path.write_bytes(out.encode("utf-8"))
    print(f"{path.relative_to(REPO_ROOT)}: removed {removed} entries"
          + (f", {comments} dangling comments" if comments else ""))
    return removed, skipped


def main() -> int:
    global args
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--json", type=Path, default=DEFAULT_JSON,
                    help="checker JSON report (default: %(default)s)")
    ap.add_argument("--dry-run", action="store_true",
                    help="report what would be removed, write nothing")
    args = ap.parse_args()

    if not args.json.is_file():
        print(f"[error] JSON report not found: {args.json}\n"
              f"        run: python tools/check_unused_strings.py")
        return 1
    data = json.loads(args.json.read_text(encoding="utf-8"))
    candidates = data["tiers"]["UNUSED"]
    print(f"JSON {args.json} ({data.get('generated', '?')}): "
          f"{len(candidates)} UNUSED candidates")

    texts = [(p, checker.read_text(p)) for p in checker.find_scan_files()]
    names = []
    for name in candidates:
        used, where = name_is_referenced(name, texts)
        if used:
            print(f"[skip] {name}: gained a reference at {where}")
        else:
            names.append(name)
    if len(names) != len(candidates):
        print(f"re-verification dropped {len(candidates) - len(names)} "
              f"stale candidates, removing {len(names)}")

    targets = sorted(p for p in RES_DIR.glob("values*/*.xml"))
    total, all_skipped = 0, []
    for path in targets:
        removed, skipped = process_file(path, names)
        total += removed
        all_skipped.extend(skipped)
    if all_skipped:
        print(f"[warn] unmatched-but-present entries needing manual review: "
              f"{', '.join(all_skipped)}")

    print(f"\n{'[dry-run] would remove' if args.dry_run else 'removed'} "
          f"{total} <string> elements across {len(targets)} files")
    if not args.dry_run:
        print("next: python tools/check_unused_strings.py && "
              ".\\gradlew.bat assembleDebug")
    return 0


if __name__ == "__main__":
    sys.exit(main())
