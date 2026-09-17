#!/usr/bin/env python3
"""Scan Kotlin/Java sources for section-divider comments and long KDoc/JavaDoc blocks.

Reported categories:
  1. Divider comments using dashes as separators (e.g. // ---- Foo ----)
  2. Divider comments using ─ box-drawing chars (e.g. // ── Foo ───────)
  3. KDoc/JavaDoc blocks longer than 5 lines
"""
import os
import re
import sys

ROOT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "app", "src")
EXTS = {".kt", ".java"}

DASH_DIVIDER = re.compile(r"//\s*-{3,}")
BOX_DIVIDER = re.compile(r"//\s*─{2,}")


def scan_file(path):
    with open(path, encoding="utf-8") as f:
        lines = f.readlines()
    out = []
    i = 0
    n = len(lines)
    while i < n:
        line = lines[i]
        if DASH_DIVIDER.search(line):
            out.append((i + 1, "dash-divider", line.rstrip()))
        if BOX_DIVIDER.search(line):
            out.append((i + 1, "box-divider", line.rstrip()))
        s = line.strip()
        if s.startswith("/**"):
            j = i
            while j < n and "*/" not in lines[j]:
                j += 1
            if j >= n:
                i += 1
                continue
            length = j - i + 1
            if length > 5:
                snippet = lines[i].rstrip() if length <= 8 else lines[i].rstrip() + " ... " + lines[j].rstrip()
                out.append((i + 1, f"long-doc({length} lines)", snippet))
            i = j
        i += 1
    return out


def main():
    count = 0
    for dirpath, _, files in os.walk(ROOT):
        if "build" in dirpath:
            continue
        for name in files:
            if os.path.splitext(name)[1] not in EXTS:
                continue
            path = os.path.join(dirpath, name)
            for lineno, kind, text in scan_file(path):
                rel = os.path.relpath(path, os.path.dirname(ROOT))
                print(f"{rel}:{lineno} [{kind}] {text}")
                count += 1
    print(f"\nTotal: {count}")


if __name__ == "__main__":
    main()
