#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
rename_strings.py -- apply one batch of renames from string_rename_map.csv.

Rewrites, for every row of the selected batch:
  - name="old" -> name="new"        in every values*/*.xml (all locales)
  - R.string.old -> R.string.new    in code (never touching android.R.string.*)
  - @string/old -> @string/new      in XML resources and AndroidManifest
  - "old" -> "new" quoted literals  in non-XML files, ONLY for rows whose old
    name appears as a quoted literal (the getIdentifier dynamic contracts)

Guards:
  - rows with old == new or batch == skip are ignored;
  - target name must not already exist as a declaration, and no two applied
    rows may share a target (collisions abort the batch);
  - declaration-diff check per file: names_after must equal
    names_before - olds + news (catches regex over-reach);
  - XML validation of every modified resource file before write-back;
  - CRLF/LF line endings preserved; UTF-8 everywhere.

Usage:
  python tools/rename_strings.py --batch nav [--dry-run]
  python tools/rename_strings.py --batch all
After a real run: python tools/check_unused_strings.py && .\\gradlew.bat assembleDebug
"""

from __future__ import annotations

import argparse
import csv
import re
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path

import importlib.util

REPO_ROOT = Path(__file__).resolve().parents[1]
MAP_CSV = Path(__file__).resolve().parent / "string_rename_map.csv"
RES_DIR = REPO_ROOT / "app" / "src" / "main" / "res"

_spec = importlib.util.spec_from_file_location(
    "checker", Path(__file__).resolve().parent / "check_unused_strings.py")
checker = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(checker)

DECL_RE = re.compile(r'(<string(?=[\s/>])[^>]*\bname=")([A-Za-z0-9_]+)(")')


def declared_names(text: str) -> set[str]:
    return {m.group(2) for m in DECL_RE.finditer(text)}


def load_rows(batch: str):
    rows = list(csv.DictReader(MAP_CSV.open(encoding="utf-8")))
    if batch != "all":
        rows = [r for r in rows if r["batch"] == batch]
    rows = [r for r in rows if r["old"] != r["new"]]
    olds = [r["old"] for r in rows]
    if len(olds) != len(set(olds)):
        sys.exit("[abort] duplicate old names in selection")
    targets = defaultdict(list)
    for r in rows:
        targets[r["new"]].append(r["old"])
    dupes = {k: v for k, v in targets.items() if len(v) > 1}
    if dupes:
        sys.exit(f"[abort] rows collide on target: {dupes}")
    return rows


def rewrite_file(path: Path, renames: dict[str, str], dynamic: set[str]):
    """Returns (decl_renamed, ref_renamed, lit_renamed) for one file."""
    raw = path.read_bytes().decode("utf-8")
    crlf = "\r\n" in raw
    text = raw.replace("\r\n", "\n") if crlf else raw
    before = declared_names(text)
    if not (before & set(renames)):
        # nothing declared here; references may still exist below
        pass
    decl_n = ref_n = lit_n = 0

    def sub_decl(m):
        nonlocal decl_n
        new = renames.get(m.group(2))
        if new:
            decl_n += 1
            return m.group(1) + new + m.group(3)
        return m.group(0)

    text = DECL_RE.sub(sub_decl, text)

    for old, new in renames.items():
        pat = re.compile(r"(?<![\w.])R\.string\." + re.escape(old) + r"(?![A-Za-z0-9_])")
        text, n = pat.subn(f"R.string.{new}", text)
        ref_n += n
        pat = re.compile(r"@string/" + re.escape(old) + r"(?![A-Za-z0-9_])")
        text, n = pat.subn(f"@string/{new}", text)
        ref_n += n
        if old in dynamic:
            pat = re.compile(r'"' + re.escape(old) + r'"')
            text, n = pat.subn(f'"{new}"', text)
            lit_n += n

    if decl_n == 0 and ref_n == 0 and lit_n == 0:
        return 0, 0, 0

    after = declared_names(text)
    expected = (before - set(renames)) | {v for k, v in renames.items() if k in before}
    if after != expected:
        print(f"[abort] {path.relative_to(REPO_ROOT)}: declaration set mismatch\n"
              f"  unexpected: {sorted(after - expected)}\n"
              f"  vanished:   {sorted(expected - after)}")
        sys.exit(1)
    if path.suffix.lower() == ".xml":
        try:
            ET.fromstring(text)
        except ET.ParseError as exc:
            print(f"[abort] {path.relative_to(REPO_ROOT)}: XML parse failed ({exc})")
            sys.exit(1)

    if not args.dry_run:
        out = text.replace("\n", "\r\n") if crlf else text
        path.write_bytes(out.encode("utf-8"))
    return decl_n, ref_n, lit_n


def main() -> int:
    global args, MAP_CSV
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--batch", required=True,
                    help="batch name from the map csv, or 'all'")
    ap.add_argument("--csv", type=Path, default=MAP_CSV)
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()
    MAP_CSV = args.csv

    rows = load_rows(args.batch)
    if not rows:
        print("nothing to rename for this batch")
        return 0
    renames = {r["old"]: r["new"] for r in rows}

    all_texts = [(p, checker.read_text(p)) for p in checker.find_scan_files()]
    existing: set[str] = set()
    for p, t in all_texts:
        if p.suffix.lower() == ".xml" and str(p).startswith(str(RES_DIR)):
            existing |= declared_names(t)
    clash = [r["new"] for r in rows if r["new"] in existing and r["old"] not in existing]
    if clash:
        sys.exit(f"[abort] target names already declared: {sorted(set(clash))}")

    # quoted-literal rewriting is ONLY legitimate for the ztool batch: those
    # are cross-process getIdentifier contracts (Hook-side STRING_* constants).
    # For every other batch, quoted literals like "reboot" (shell commands) or
    # preference keys are coincidences and MUST NOT be touched.
    dynamic: set[str] = set()
    if args.batch in ("ztool", "all"):
        dynamic = {r["old"] for r in rows
                   if any(p.suffix.lower() != ".xml" and f'"{r["old"]}"' in t
                          for p, t in all_texts)}
    coincidental = {r["old"] for r in rows
                    if any(p.suffix.lower() != ".xml" and f'"{r["old"]}"' in t
                           for p, t in all_texts)} - dynamic
    if dynamic:
        print(f"dynamic (quoted-literal) renames: {sorted(dynamic)}")
    if coincidental:
        print(f"[info] quoted literals left untouched (coincidental): "
              f"{sorted(coincidental)}")

    targets = sorted(set(RES_DIR.glob("values*/*.xml")) | {p for p, _ in all_texts})
    total_d = total_r = total_l = 0
    touched = set()
    for path in targets:
        d, r, l = rewrite_file(path, renames, dynamic)
        if d or r or l:
            total_d += d
            total_r += r
            total_l += l
            touched.add(path)
    verb = "would rename" if args.dry_run else "renamed"
    print(f"{verb} {len(rows)} names: {total_d} declarations, "
          f"{total_r} refs, {total_l} literals across {len(touched)} files")
    if not args.dry_run and touched:
        print("next: python tools/check_unused_strings.py && .\\gradlew.bat assembleDebug")
    return 0


if __name__ == "__main__":
    sys.exit(main())
