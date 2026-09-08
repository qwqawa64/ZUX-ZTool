#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check_unused_strings.py -- Batch-check unused <string> resources in the :app module.

Why not plain grep / lint alone:
  - This repo loads some of its OWN strings via resources.getIdentifier() inside
    Hook code (BatchUninstall.kt, RecentTaskMemoryViewHook.kt) using name
    constants, so neither "R.string." nor "@string/" appears anywhere for them.
    Android Lint flags those as UnusedResources (false positive); a naive grep
    does the same. This script classifies every declared name into tiers and
    cross-checks against Android Lint (UnusedResources) when a lint report
    exists (app/build/reports/lint-results*.xml).

Reference forms detected (name counted as USED):
  1. R.string.<name>   in .kt/.java (incl. Compose stringResource(R.string.x))
  2. @string/<name>    in any scanned XML/manifest/text file
  3. exact quoted literal "<name>" in .kt/.java/.json/.sh/... -> DYNAMIC tier
     (covers getIdentifier constants such as STRING_BUTTON = "ztool_batch_uninstall_button")
  4. name built by string concatenation ("prefix_" + x or x + "_suffix")
     -> CONCAT_RISK tier

Tiers reported:
  USED          -- referenced via form 1/2, safe, nothing to do
  DYNAMIC       -- only reachable via quoted literal; keep + mark
                   tools:ignore="UnusedResources" when lint flags it
  CONCAT_RISK   -- name may be assembled from literal fragments; manual review
  UNUSED        -- zero occurrences in ANY scanned file; deletion candidate

Locale sync is also reported (default values/ vs values-en-rUS/ etc.), with
translatable="false" entries excluded from the "missing in locale" list.

Outputs (UTF-8):
  - console summary
  - app/build/reports/unused_strings_report.md
  - app/build/reports/unused_strings.json   (for later rename/cleanup tooling)

Usage:
  python tools/check_unused_strings.py [--no-lint] [--print-all]

Scan scope: app/src/** (kt/java/xml/json/sh/prop/list/txt/md/...), plus
app/build.gradle.kts and app/proguard-rules.pro. Generated build output and
.git are never scanned. Files are always decoded as UTF-8 (errors=replace).
"""

from __future__ import annotations

import argparse
import datetime
import json
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
APP_DIR = REPO_ROOT / "app"
RES_DIR = APP_DIR / "src" / "main" / "res"
REPORT_DIR = APP_DIR / "build" / "reports"

SCAN_EXTS = {
    ".kt", ".java", ".xml", ".json", ".sh", ".list", ".prop", ".properties",
    ".pro", ".txt", ".md", ".cfg", ".html", ".gradle",
}
EXTRA_FILES = [APP_DIR / "build.gradle.kts", APP_DIR / "proguard-rules.pro"]

# Resources consumed by external frameworks/build tooling by name, never via
# R.x / @x references in this repo. Never report them as unused.
FRAMEWORK_CONTRACT_NAMES = {
    # read by LSPosed Manager to declare the module's injection scope
    "xposed_scope",
}

# <string ...> opening tag attributes, self-closing allowed.
# The (?=[\s/>]) lookahead is required: plain \b would also match inside
# <string-array> ("string" followed by "-" is a word boundary), making the
# array body swallow following <string> declarations.
STRING_TAG_RE = re.compile(
    r'<string(?=[\s/>])([^>]*?)(/>|>(.*?)</string\s*>)', re.S | re.I)
STRING_ARRAY_TAG_RE = re.compile(r'<string-array\b([^>]*?)/?>', re.I)
PLURALS_TAG_RE = re.compile(r'<plurals\b([^>]*?)/?>', re.I)
NAME_ATTR_RE = re.compile(r'\bname="([^"]+)"')
TRANSLATABLE_FALSE_RE = re.compile(r'\btranslatable="false"')

RSTRING_RE = re.compile(r'(?<![\w.])R\.string\.([A-Za-z0-9_]+)')
ATSTRING_RE = re.compile(r'@string/([A-Za-z0-9_]+)')
RARRAY_RE = re.compile(r'\bR\.array\.([A-Za-z0-9_]+)')
ATARRAY_RE = re.compile(r'@array/([A-Za-z0-9_]+)')
RPLURALS_RE = re.compile(r'\bR\.plurals\.([A-Za-z0-9_]+)')
ATPLURALS_RE = re.compile(r'@plurals/([A-Za-z0-9_]+)')
# quoted literals, incl. simple escapes; long values pruned later
QUOTED_RE = re.compile(r'"((?:[^"\\\n]|\\.){1,160})"')
# "prefix_" + something / something + "_suffix" (identifier-like fragments)
PREFIX_CONCAT_RE = re.compile(r'"([A-Za-z0-9_]+)"\s*\+\s*[A-Za-z_$]')
SUFFIX_CONCAT_RE = re.compile(r'[A-Za-z0-9_$)]\s*\+\s*"([A-Za-z0-9_]+)"')


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def find_scan_files():
    files = []
    for p in (APP_DIR / "src").rglob("*"):
        if not p.is_file() or p.suffix.lower() not in SCAN_EXTS:
            continue
        if "build" in p.parts:  # generated output
            continue
        files.append(p)
    for p in EXTRA_FILES:
        if p.is_file():
            files.append(p)
    return sorted(set(files))


def parse_declared():
    """Parse every values*/strings.xml (and any values*/*.xml holding <string>)."""
    default = {}          # name -> {"line", "translatable", "preview", "file"}
    locales = {}          # dir name -> {name: line}
    plurals_default = []  # [(name, line)]
    arrays_default = []   # [(name, line)] string-arrays, referenced as R.array
    for res_file in sorted(RES_DIR.glob("values*/*.xml")):
        locale = "default" if res_file.parent.name == "values" else res_file.parent.name
        text = read_text(res_file)
        names = {}
        for m in STRING_TAG_RE.finditer(text):
            attrs, body = m.group(1), m.group(3) or ""
            name_m = NAME_ATTR_RE.search(attrs)
            if not name_m:
                continue
            name = name_m.group(1)
            line = text.count("\n", 0, m.start()) + 1
            entry = {
                "line": line,
                "translatable": bool(TRANSLATABLE_FALSE_RE.search(attrs)),
                "preview": re.sub(r"\s+", " ", body).strip()[:60],
                "file": str(res_file.relative_to(REPO_ROOT)),
            }
            names[name] = entry
            if locale == "default":
                default[name] = entry
        if locale == "default":
            for m in PLURALS_TAG_RE.finditer(text):
                name_m = NAME_ATTR_RE.search(m.group(1))
                if name_m:
                    plurals_default.append(
                        (name_m.group(1), text.count("\n", 0, m.start()) + 1))
            for m in STRING_ARRAY_TAG_RE.finditer(text):
                name_m = NAME_ATTR_RE.search(m.group(1))
                if name_m:
                    arrays_default.append(
                        (name_m.group(1), text.count("\n", 0, m.start()) + 1))
        elif names:
            # only dirs that actually declare strings count as locales
            # (values-night/themes.xml etc. are not string locales)
            locales[locale] = names
    return default, locales, plurals_default, arrays_default


def parse_lint_report():
    """Return set of string names Android Lint flagged UnusedResources, or None."""
    candidates = sorted(REPORT_DIR.glob("lint-results*.xml"),
                        key=lambda p: p.stat().st_mtime, reverse=True)
    if not candidates:
        return None
    try:
        import xml.etree.ElementTree as ET
        tree = ET.parse(candidates[0])
    except Exception as exc:  # noqa: BLE001 - report file may be truncated
        print(f"[warn] cannot parse lint report {candidates[0]}: {exc}")
        return None
    names = set()
    for issue in tree.iter("issue"):
        if issue.get("id") == "UnusedResources":
            m = re.search(r"\bR\.string\.([A-Za-z0-9_]+)", issue.get("message", ""))
            if m:
                names.add(m.group(1))
    return names


def locate_literal(name: str, texts, limit=3):
    hits = []
    needle = f'"{name}"'
    for path, text in texts:
        idx = 0
        while len(hits) < limit:
            idx = text.find(needle, idx)
            if idx < 0:
                break
            hits.append(f"{path.relative_to(REPO_ROOT)}:{text.count(chr(10), 0, idx) + 1}")
            idx += len(needle)
        if len(hits) >= limit:
            break
    return hits


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--no-lint", action="store_true",
                    help="skip cross-check against lint report")
    ap.add_argument("--print-all", action="store_true",
                    help="print full unused list to console, not only a preview")
    args = ap.parse_args()

    default, locales, plurals_default, arrays_default = parse_declared()
    texts = [(p, read_text(p)) for p in find_scan_files()]

    rstring, atstring, quoted = set(), set(), set()
    rarray, atarray = set(), set()
    prefix_frags, suffix_frags = [], []
    for path, text in texts:
        if path.suffix.lower() == ".xml":
            atstring.update(ATSTRING_RE.findall(text))
            atarray.update(ATARRAY_RE.findall(text))
        else:
            rstring.update(RSTRING_RE.findall(text))
            atstring.update(ATSTRING_RE.findall(text))
            rarray.update(RARRAY_RE.findall(text))
            atarray.update(ATARRAY_RE.findall(text))
            quoted.update(QUOTED_RE.findall(text))
            prefix_frags.extend(PREFIX_CONCAT_RE.findall(text))
            suffix_frags.extend(SUFFIX_CONCAT_RE.findall(text))

    concat_prefixes = {p for p in prefix_frags if p.endswith("_")}
    concat_suffixes = {s for s in suffix_frags if s.startswith("_")}

    tiers = {"USED": [], "DYNAMIC": [], "CONCAT_RISK": [], "UNUSED": []}
    for name in default:
        if name in rstring or name in atstring:
            tiers["USED"].append(name)
        elif name in quoted:
            tiers["DYNAMIC"].append(name)
        elif any(name.startswith(p) and len(name) > len(p) for p in concat_prefixes) or \
                any(name.endswith(s) and len(name) > len(s) for s in concat_suffixes):
            tiers["CONCAT_RISK"].append(name)
        else:
            tiers["UNUSED"].append(name)
    for names in tiers.values():
        names.sort()

    # locale sync (translatable="false" entries may legally be absent)
    sync = {}
    for locale, names in locales.items():
        expected = {n for n, e in default.items() if not e["translatable"]}
        missing = sorted(expected - set(names))
        extra = sorted(set(names) - set(default))
        sync[locale] = {"missing": missing, "extra": extra,
                        "declared": len(names)}

    plurals_used, plurals_unused = [], []
    for pname, _line in plurals_default:
        if pname in rstring or pname in atstring or pname in quoted:
            plurals_used.append(pname)
        else:
            plurals_unused.append(pname)

    arrays_used, arrays_unused = [], []
    for aname, _line in arrays_default:
        if aname in FRAMEWORK_CONTRACT_NAMES:
            arrays_used.append(aname)
        elif aname in rarray or aname in atarray or aname in quoted:
            arrays_used.append(aname)
        else:
            arrays_unused.append(aname)

    lint_names = None if args.no_lint else parse_lint_report()

    # ---------------- report ----------------
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    stamp = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    md = [f"# Unused string resources report ({stamp})", ""]
    md.append(f"- declared in `values/strings.xml`: **{len(default)}**")
    md.append(f"- USED (R.string / @string): **{len(tiers['USED'])}**")
    md.append(f"- DYNAMIC (quoted literal only): **{len(tiers['DYNAMIC'])}**")
    md.append(f"- CONCAT_RISK: **{len(tiers['CONCAT_RISK'])}**")
    md.append(f"- UNUSED (zero occurrences): **{len(tiers['UNUSED'])}**")
    md.append("")

    def section(title, names, note=""):
        md.append(f"## {title} ({len(names)})")
        if note:
            md.append(note)
        if not names:
            md.append("_none_")
        md.append("")
        md.append("| name | default line | preview |")
        md.append("|------|--------------|---------|")
        for n in names:
            e = default[n]
            md.append(f"| `{n}` | {e['line']} | {e['preview'] or '(empty)'} |")
        md.append("")

    section("UNUSED -- deletion candidates", tiers["UNUSED"],
            "Zero occurrences of the name in any scanned file. Cross-check the "
            "lint section below before deleting.")
    md.append("## DYNAMIC -- referenced only via quoted literal "
              f"({len(tiers['DYNAMIC'])})")
    md.append("Reachable through resources.getIdentifier(...) style loading; "
              "keep them and mark `tools:ignore=\"UnusedResources\"` so lint "
              "stops flagging them.")
    md.append("")
    if tiers["DYNAMIC"]:
        md.append("| name | literal found at |")
        md.append("|------|------------------|")
        for n in tiers["DYNAMIC"]:
            md.append(f"| `{n}` | {', '.join(locate_literal(n, texts)) or 'n/a'} |")
    else:
        md.append("_none_")
    md.append("")
    section("CONCAT_RISK -- name may be built by concatenation",
            tiers["CONCAT_RISK"], "Manual review required.")

    md.append(f"## Locale sync ({', '.join(locales) or 'no locale files'})")
    for locale, info in sync.items():
        md.append(f"- `{locale}`: declared {info['declared']}, "
                  f"missing vs default {len(info['missing'])}, "
                  f"extra vs default {len(info['extra'])}")
        for n in info["missing"]:
            md.append(f"  - missing: `{n}`")
        for n in info["extra"]:
            md.append(f"  - extra (not in default): `{n}`")
    md.append("")

    plurals_note = (f"used {len(plurals_used)} / unused "
                    f"{len(plurals_unused)}: {', '.join(plurals_unused)}"
                    if plurals_unused else f"all {len(plurals_used)} used")
    md.append(f"## Plurals in strings.xml -- {plurals_note}")
    md.append("")
    arrays_note = (f"used {len(arrays_used)} / unused "
                   f"{len(arrays_unused)}: {', '.join(arrays_unused)}"
                   if arrays_unused else f"all {len(arrays_used)} used")
    md.append(f"## String-arrays (all values*.xml) -- {arrays_note}")
    md.append("")

    if lint_names is None:
        md.append("## Lint cross-check")
        md.append("_lint report not found; run `:app:lintDebug` (or Android "
                  "Studio inspection 'UnusedResources') and re-run this script._")
        md.append("")
    else:
        both = sorted(set(tiers["UNUSED"]) & lint_names)
        lint_only = sorted(lint_names - set(tiers["UNUSED"]))
        script_only = sorted(set(tiers["UNUSED"]) - lint_names)
        md.append("## Lint cross-check (UnusedResources)")
        md.append(f"- lint flagged {len(lint_names)} module strings")
        md.append(f"- agreement (UNUSED & lint): **{len(both)}** -- safest to delete")
        md.append(f"- lint-only (script sees a reference, lint does not): "
                  f"{len(lint_only)} -> transitive or dynamic usage, review")
        md.append(f"- script-only (zero textual occurrences, lint keeps them): "
                  f"{len(script_only)} -> investigate before deleting")
        md.append("")
        for title, lst in (("lint-only", lint_only), ("script-only", script_only)):
            md.append(f"### {title} ({len(lst)})")
            md.append(", ".join(f"`{n}`" for n in lst) if lst else "_none_")
            md.append("")

    report_md = REPORT_DIR / "unused_strings_report.md"
    report_md.write_text("\n".join(md), encoding="utf-8")

    payload = {
        "generated": stamp,
        "default_declared": len(default),
        "tiers": {k: v for k, v in tiers.items()},
        "locale_sync": sync,
        "plurals_unused": plurals_unused,
        "string_arrays_unused": arrays_unused,
        "lint": {
            "found": lint_names is not None,
            "flagged": sorted(lint_names) if lint_names else [],
            "agreement": sorted(set(tiers["UNUSED"]) & (lint_names or set())),
        },
    }
    report_json = REPORT_DIR / "unused_strings.json"
    report_json.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")

    # ---------------- console ----------------
    print(f"declared: {len(default)}  USED: {len(tiers['USED'])}  "
          f"DYNAMIC: {len(tiers['DYNAMIC'])}  CONCAT_RISK: "
          f"{len(tiers['CONCAT_RISK'])}  UNUSED: {len(tiers['UNUSED'])}")
    for locale, info in sync.items():
        print(f"locale {locale}: missing {len(info['missing'])}, "
              f"extra {len(info['extra'])}")
    if lint_names is not None:
        print(f"lint cross-check: flagged {len(lint_names)}, "
              f"agreement {len(payload['lint']['agreement'])}")
    else:
        print("lint cross-check: no lint report found "
              "(run :app:lintDebug and re-run for verification)")
    if plurals_unused:
        print(f"plurals unused: {', '.join(plurals_unused)}")
    if arrays_unused:
        print(f"string-arrays unused: {', '.join(arrays_unused)}")

    unused = tiers["UNUSED"]
    if unused:
        shown = unused if args.print_all else unused[:40]
        print(f"\nunused preview ({len(shown)}/{len(unused)}):")
        for n in shown:
            print(f"  {n}")
        if not args.print_all:
            print("  ... see full list in the report file")
    print(f"\nreport: {report_md}")
    print(f"json:   {report_json}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
