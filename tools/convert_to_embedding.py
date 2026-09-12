# -*- coding: utf-8 -*-
"""
Convert MIUI OneVision parallel-window XML rules (onevision/source/*.xml)
into ZUXOS embedding_config.json skeleton entries.

Only the "package X / activity Y needs embedding" semantics can be carried
over losslessly; everything else (view policies, orientation rules,
split-pair rules, projection variants) is out of scope for the embedding
config and is reported as dropped.

Outputs (onevision/output/):
  embedding_config_converted.json  - array of new package entries
  convert_report.md                - per-package status: converted / lossy / dropped

Usage:  python tools/convert_to_embedding.py   (expects onevision/source/*.xml)
"""
import json
import os
import re
import xml.etree.ElementTree as ET
from collections import OrderedDict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "onevision", "source")
OUT = os.path.join(ROOT, "onevision", "output")
EXISTING_CONFIG = os.path.join(
    ROOT, "app", "src", "main", "assets", "embedding", "zuxos_embedding",
    "embedding_config.json")

DEFAULTS = OrderedDict([
    ("forceFullscreenPages", []),
    ("transActivities", []),
    ("leftTransActivities", []),
    ("showEmbeddingDivider", "true"),
    ("skipLetterboxDisplayInfo", "false"),
    ("skipMultiWindowMode", "true"),
    ("showSurfaceViewBackground", "false"),
    ("shouldPausePrimaryActivity", "false"),
])

MAIN_HINTS = ("main", "home", "launcher", "splash", "index", "tab")


def guess_main_page(activities):
    """Pick a plausible main page from known activity class names, else ''."""
    for kw in MAIN_HINTS:
        cands = [a for a in activities if kw in a.rsplit(".", 1)[-1].lower()]
        if cands:
            # Prefer exact "...MainActivity" style matches first.
            exact = [a for a in cands if a.rsplit(".", 1)[-1].lower() in
                     ("mainactivity", "maintabactivity", "homeactivity",
                      "launcheractivity", "splashactivity", "indexactivity")]
            return (exact or cands)[0]
    return ""


def parse_activity_rule(rule):
    """Parse MIUI activityRule 'Act:mode:views;Act2:mode' into class names.

    Wildcard entries ('*:0') carry no activity names -> returns ([], True).
    """
    activities, wildcard = [], False
    for part in (rule or "").split(";"):
        part = part.strip()
        if not part:
            continue
        cls = part.split(":")[0].strip()
        if cls == "*":
            wildcard = True
        elif cls:
            activities.append(cls)
    return activities, wildcard


def load_packages(filename, root_tag):
    path = os.path.join(SRC, filename)
    if not os.path.exists(path):
        return []
    root = ET.parse(path).getroot()
    return root.findall(root_tag)


def main():
    os.makedirs(OUT, exist_ok=True)

    with open(EXISTING_CONFIG, encoding="utf-8") as f:
        existing = json.load(f)
    # Accept both the wrapped config {packages: [...]} and a bare entry list.
    if isinstance(existing, dict) and "packages" in existing:
        existing_names = {p["name"] for p in existing["packages"]}
    elif isinstance(existing, list):
        existing_names = {p["name"] for p in existing if isinstance(p, dict)}
    else:
        raise SystemExit(f"unexpected root structure in {EXISTING_CONFIG}; "
                         "refusing to dedupe against garbage input")

    entries = OrderedDict()   # pkg -> entry dict
    notes = OrderedDict()     # pkg -> [status, source, note]

    def record(pkg, source, status, note=""):
        if pkg not in notes:
            notes[pkg] = [status, source, note]
        elif notes[pkg][0] == "dropped" and status != "dropped":
            notes[pkg] = [status, source, note]

    # 1) embedded_rules_list.xml -- best source: divider/fullsize flags.
    for p in load_packages("embedded_rules_list.xml", "package"):
        pkg = p.get("name")
        if not pkg:
            continue
        activities = []
        if p.get("supportFullSize") == "true":
            # Package-level flag with no activity names; the shipped config
            # never uses wildcards in forceFullscreenPages, so leave empty.
            pass
        entries[pkg] = OrderedDict([
            ("name", pkg),
            ("mainPage", ""),
            ("activityPairs", []),
            ("forceFullscreenPages", []),
            ("transActivities", []),
            ("leftTransActivities", []),
            ("showEmbeddingDivider", p.get("isShowDivider", "false")),
            ("skipLetterboxDisplayInfo", "false"),
            ("skipMultiWindowMode", "true"),
            ("showSurfaceViewBackground", "false"),
            ("shouldPausePrimaryActivity", "false"),
        ])
        status = "converted"
        note = []
        if p.get("supportFullSize") == "true":
            note.append("supportFullSize dropped: no equivalent per-activity list")
        record(pkg, "embedded_rules_list.xml", status, "; ".join(note))

    # 2) autoui_list.xml -- activityRule -> activityPairs.
    for p in load_packages("autoui_list.xml", "package"):
        pkg = p.get("name")
        if not pkg:
            continue
        activities, wildcard = parse_activity_rule(p.get("activityRule"))
        if pkg not in entries:
            entries[pkg] = OrderedDict([("name", pkg), ("mainPage", ""),
                                    ("activityPairs", [])], **DEFAULTS)
        e = entries[pkg]
        known = {pair["from"] for pair in e["activityPairs"]}
        known |= {a for a in activities if a}
        # Replace wildcard placeholders from step 1 with real activities.
        e["activityPairs"] = [{"from": a, "to": "*"} for a in sorted(known)]
        if not e["mainPage"]:
            sorted_known = sorted(known)
            # Fall back to the first known activity when no Main/Home/Launcher
            # style name is available, so entries stay usable.
            e["mainPage"] = guess_main_page(sorted_known) or (
                sorted_known[0] if sorted_known else "")
        if wildcard:
            record(pkg, "autoui_list.xml", "lossy",
                   "wildcard activityRule (mode info dropped); activity list unknown")
        else:
            record(pkg, "autoui_list.xml", "converted",
                   "optimizeWebView and view ids dropped" if p.get("optimizeWebView") else "")

    # 3) embedded_setting_config.xml -- embeddedEnable skeleton entries.
    for s in load_packages("embedded_setting_config.xml", "setting"):
        pkg = s.get("name")
        if not pkg or s.get("embeddedEnable") != "true":
            continue
        if pkg in entries:
            record(pkg, "embedded_setting_config.xml", "converted",
                   "duplicate of earlier source; skipped")
            continue
        entries[pkg] = OrderedDict([("name", pkg), ("mainPage", ""),
                                    ("activityPairs", [])], **DEFAULTS)
        record(pkg, "embedded_setting_config.xml", "lossy",
               "no activity names available; skeleton only")

    # Explicitly out-of-scope files -> dropped packages.
    for filename, tag in (("autoui2_list.xml", "package"),
                          ("fixed_orientation_list.xml", "package"),
                          ("fixed_orientation_list_projection.xml", "package"),
                          ("embedded_rules_list_projection.xml", "package"),
                          ("generic_rules_list.xml", "package"),
                          ("generic_rules_list_projection.xml", "package")):
        for p in load_packages(filename, tag):
            pkg = p.get("name")
            if pkg and pkg not in notes:
                notes[pkg] = ["dropped", filename,
                              "view policies / orientation / split rules: no embedding equivalent"]

    # 4) Emit results, deduped against the shipped embedding_config.json.
    new_entries, dup = [], []
    for pkg, e in entries.items():
        if pkg in existing_names:
            dup.append(pkg)
            notes[pkg][2] = (notes[pkg][2] + "; " if notes[pkg][2] else "") + \
                            "already present in embedding_config.json"
            continue
        new_entries.append(e)

    with open(os.path.join(OUT, "embedding_config_converted.json"), "w",
              encoding="utf-8") as f:
        json.dump(new_entries, f, ensure_ascii=False, indent=2)

    stats = {"converted": 0, "lossy": 0, "dropped": 0}
    lines = ["# onevision -> embedding_config 转换报告", ""]
    for pkg, (status, src, note) in notes.items():
        stats[status] = stats.get(status, 0) + 1
        line = f"- **{pkg}** [{status}] ({src})"
        if note:
            line += f": {note}"
        lines.append(line)
    summary = ["", "## 统计", "",
               f"- converted（可并入）: {stats.get('converted', 0)}",
               f"- lossy（骨架/有损，需人工补 mainPage）: {stats.get('lossy', 0)}",
               f"- dropped（无对应语义，已丢弃）: {stats.get('dropped', 0)}",
               f"- 已存在于 embedding_config.json，未输出: {len(dup)}",
               f"- 实际输出新条目: {len(new_entries)}", ""]
    with open(os.path.join(OUT, "convert_report.md"), "w",
              encoding="utf-8") as f:
        f.write("\n".join(lines + summary))

    print(f"packages seen: {len(notes)}; new entries: {len(new_entries)}; "
          f"already in config: {len(dup)}")
    print("stats:", stats)
    print("wrote:", os.path.join(OUT, "embedding_config_converted.json"))
    print("wrote:", os.path.join(OUT, "convert_report.md"))


if __name__ == "__main__":
    main()
